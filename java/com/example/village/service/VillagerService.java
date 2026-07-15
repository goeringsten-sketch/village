package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.hook.CitizensHook;
import com.example.village.model.BuildingDefinition;

import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.model.VillageBuilding;
import com.example.village.model.VillagerJob;
import com.example.village.model.VillagerNeed;
import com.example.village.model.VillagerProfession;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VillagerService {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VillageManager villageManager;
    private final CitizensHook citizensHook;
    private final VillagerScheduleManager scheduleManager;
    private VillagerNutritionService nutritionService;
    private VillagerContractService contractService;
    private int tickTaskId = -1;

    public VillagerService(VillagePlugin plugin, VillageConfigManager configManager,
                           VillageManager villageManager, CitizensHook citizensHook) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.villageManager = villageManager;
        this.citizensHook = citizensHook;
        this.scheduleManager = new VillagerScheduleManager(this, citizensHook);
    }

    public void setNutritionService(VillagerNutritionService nutritionService) {
        this.nutritionService = nutritionService;
        scheduleManager.setNutritionService(nutritionService);
    }

    public void setContractService(VillagerContractService contractService) {
        this.contractService = contractService;
    }

    /**
     * Converts all villagers of the given village back to plain vanilla villagers.
     * Called when a village is being deleted. Removes:
     * - Citizens NPCs (if Citizens is active)
     * - Custom name and name visibility
     * - Glowing status
     * - The plugin's PersistentData marker key ("village-villager-id")
     */
    public void releaseVillagersOnDelete(Village village) {
        if (village == null) return;
        org.bukkit.NamespacedKey markerKey = new org.bukkit.NamespacedKey(plugin, "village-villager-id");

        for (com.example.village.model.CustomVillager cv : new java.util.ArrayList<>(village.getVillagers())) {
            // Citizens NPC: just remove the NPC
            if (cv.getNpcId() >= 0 && citizensHook != null && citizensHook.isAvailable()) {
                citizensHook.removeNpc(cv.getId());
                continue;
            }

            // Vanilla villager: find the entity and strip all plugin-specific state
            World world = village.getWorld();
            if (world == null) continue;
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (!(entity instanceof org.bukkit.entity.Villager v)) continue;
                String stored = v.getPersistentDataContainer().get(
                        markerKey, org.bukkit.persistence.PersistentDataType.STRING);
                if (!cv.getId().toString().equals(stored)) continue;

                // Strip custom name
                v.customName(null);
                v.setCustomNameVisible(false);
                // Remove glow
                v.setGlowing(false);
                // Remove plugin marker so it's no longer tracked
                v.getPersistentDataContainer().remove(markerKey);
                break;
            }
        }
    }

    public void startVillagerTasks() {
        // Dorfweite Vertragsverarbeitung alle 20 Ticks (1 Sekunde)
        tickTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Village village : villageManager.getAllVillages()) {
                if (contractService != null) {
                    contractService.processVillage(village);
                }
            }
        }, 20L, 20L).getTaskId();
    }

    public void stopVillagerTasks() {
        if (tickTaskId != -1) {
            Bukkit.getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }
    }

    /**
     * Produktion und Tagesplan – wird vom VillagerTickService über die Warteschlange aufgerufen.
     */
    public void tickProductionAndSchedule(Village village, CustomVillager villager) {
        tickVillager(village, villager);
    }

    private void tickVillager(Village village, CustomVillager villager) {
        if (nutritionService != null) {
            nutritionService.ensureNutrientsInitialized(villager);
        }

        // Check production
        VillagerProfession profession = configManager.getProfession(villager.getProfessionKey());
        if (profession == null) return;

        if (profession.getProductionIntervalSeconds() <= 0) return;

        long now = System.currentTimeMillis();
        long elapsed = now - villager.getLastProductionTime();

        // Calculate speed multiplier from upgrades
        int speedLevel = village.getUpgradeLevel("production-speed");
        double speedMultiplier = 1.0 + (speedLevel * configManager.getUpgradeSpeedMultiplierPerLevel());
        double effectiveInterval = profession.getProductionIntervalSeconds() / speedMultiplier;

        if (elapsed >= effectiveInterval * 1000) {
            produce(village, villager, profession);
            villager.setLastProductionTime(now);
        }

        // Handle schedule-based behavior
        if (villager.getProfessionKey() != null && !villager.getProfessionKey().equals("none")) {
            // Beruf zugewiesen - verwende neues Schedule-System
            scheduleManager.startNextActivity(village, villager);
        } else {
            // Kein Beruf - verwende altes einfaches System
            handleSimpleSchedule(village, villager);
        }
    }

    /**
     * Effektives Produktionsintervall (in Sekunden) unter Beruecksichtigung des
     * Dorf-Upgrades "production-speed". Wird vom Produktions-Menue zur Anzeige genutzt.
     */
    public double getEffectiveProductionIntervalSeconds(Village village, VillagerProfession profession) {
        if (profession == null || village == null) return 0;
        int speedLevel = village.getUpgradeLevel("production-speed");
        double speedMultiplier = 1.0 + (speedLevel * configManager.getUpgradeSpeedMultiplierPerLevel());
        return profession.getProductionIntervalSeconds() / speedMultiplier;
    }

    private void produce(Village village, CustomVillager villager, VillagerProfession profession) {
        if (villager.isProductionPaused()) {
            return;
        }
        if (nutritionService != null && nutritionService.getProductionMultiplier(villager) <= 0) {
            return;
        }
        if (nutritionService != null ? nutritionService.isHungerCritical(villager) : villager.isNeedCritical(VillagerNeed.HUNGER)) {
            return;
        }

        List<Material> produces = profession.getProduces();
        if (produces.isEmpty()) return;

        List<Material> targets;
        if (villager.getPreferredProduct() != null && produces.contains(villager.getPreferredProduct())) {
            targets = List.of(villager.getPreferredProduct());
        } else {
            targets = produces;
        }

        double nutritionMod = nutritionService != null ? nutritionService.getProductionMultiplier(villager) : 1.0;
        nutritionMod *= getAestheticProductionMultiplier(village, villager);
        int amount = Math.max(1, (int) Math.round((1 + (villager.getLevel() / 3)) * nutritionMod));

        boolean producedAny = false;
        for (Material material : targets) {
            if (!configManager.hasJobStorageCapacity(village, villager, material)) {
                continue;
            }
            villager.addItem(material, amount);
            producedAny = true;
        }
        if (!producedAny) {
            // Job-Lager voll: kein XP-Gewinn ohne tatsaechlichen Output.
            return;
        }

        // Add XP
        double xpGain = (1.0 + (villager.getLevel() * 0.5))
                * (nutritionService != null ? nutritionService.getXpGainMultiplier(villager) : 1.0);
        villager.addXp(xpGain);
        checkVillagerLevelUp(villager);
    }

    private void checkVillagerLevelUp(CustomVillager villager) {
        int maxLevel = configManager.getSkillTreeMaxLevel();
        int xpPerLevel = configManager.getSkillTreeXpPerLevel();

        while (villager.getLevel() < maxLevel) {
            double required = xpPerLevel * villager.getLevel();
            if (villager.getXp() < required) break;

            villager.setXp(villager.getXp() - required);
            villager.setLevel(villager.getLevel() + 1);
        }
    }

    private void initializeNutrientState(CustomVillager villager) {
        if (nutritionService != null) {
            nutritionService.ensureNutrientsInitialized(villager);
            return;
        }
        ConfigurationSection nutrients = configManager.getVillagerNutrientsSection();
        if (nutrients == null || nutrients.getKeys(false).isEmpty()) {
            return;
        }
        int capacity = configManager.getVillagerNutrientStorageCapacity();
        for (String nutrientKey : nutrients.getKeys(false)) {
            villager.setNutrientCapacity(nutrientKey, capacity);
            villager.setNutrientLevel(nutrientKey, capacity);
        }
    }

    private void handleSimpleSchedule(Village village, CustomVillager villager) {
        if (village.getWorld() == null) return;
        long worldTime = village.getWorld().getTime();

        int wakeUp = configManager.getScheduleWakeUp();
        int workStart = configManager.getScheduleWorkStart();
        int lunch = configManager.getScheduleLunch();
        int workEnd = configManager.getScheduleWorkEnd();
        int sleep = configManager.getScheduleSleep();

        if (citizensHook.isAvailable()) {
            Location target = null;

            if (worldTime >= sleep || worldTime < wakeUp) {
                // Sleeping - go home
                target = villager.getHomeLocation();
            } else if (worldTime >= workStart && worldTime < lunch) {
                // Working morning
                target = villager.getWorkLocation();
            } else if (worldTime >= lunch && worldTime < lunch + 1000) {
                // Lunch break - go home
                target = villager.getHomeLocation();
            } else if (worldTime >= lunch + 1000 && worldTime < workEnd) {
                // Working afternoon
                target = villager.getWorkLocation();
            } else {
                // Free time
                target = villager.getHomeLocation();
            }

            if (target != null) {
                citizensHook.navigateTo(villager.getNpcId(), target);
            }
        }
    }

    /**
     * Returns the total number of beds available in completed buildings of the village.
     */
    public int getTotalBeds(Village village) {
        int beds = 0;
        for (VillageBuilding building : village.getBuildings()) {
            if (!building.isCompleted()) continue;
            BuildingDefinition def = plugin.getBuildingConfigLoader() != null
                    ? plugin.getBuildingConfigLoader().getDefinition(building.getTypeKey())
                    : null;
            if (def != null) {
                beds += Math.max(0, def.getVillagerSlots());
            }
        }
        return beds;
    }

    /**
     * Returns the number of beds currently assigned to villagers.
     */
    public int getUsedBeds(Village village) {
        int used = 0;
        for (CustomVillager v : village.getVillagers()) {
            if (v.getAssignedBedBuildingId() != null) {
                used++;
            }
        }
        return used;
    }

    public int getVillagerCapacityForBuilding(VillageBuilding building) {
        if (building == null) return 0;
        BuildingDefinition def = plugin.getBuildingConfigLoader() != null
                ? plugin.getBuildingConfigLoader().getDefinition(building.getTypeKey())
                : null;
        if (def != null) return Math.max(0, def.getVillagerSlots());
        return 0;
    }

    /**
     * Spawns a new villager as a normal villager without profession.
     * Requires an available bed in a completed residential building.
     * First checks for vanilla villagers within village bounds and replaces them.
     * If no vanilla villager is found, spawns a new one.
     */
    public CustomVillager spawnVillager(Village village, Location location) {
        int maxVillagers = village.getMaxVillagers(
                configManager.getBaseMaxVillagers(),
                configManager.getUpgradeVillagersPerLevel());

        if (village.getVillagers().size() >= maxVillagers) {
            plugin.getLogger().info("Villager-Limit erreicht!");
            return null;
        }

        // Check bed availability
        int totalBeds = getTotalBeds(village);
        int usedBeds = getUsedBeds(village);
        if (usedBeds >= totalBeds) {
            plugin.getLogger().info("Keine freien Betten verfügbar!");
            return null;
        }

        // PRÜFE: Gibt es einen Vanilla-Villager in der Dorfgrenze?
        Villager vanillaVillager = findVanillaVillagerInBounds(village);
        if (vanillaVillager == null) {
            plugin.getLogger().info("Kein Vanilla-Villager in Dorfgrenze gefunden!");
            return null; // KEIN Spawn ohne Vanilla-Villager
        }

        UUID id = UUID.randomUUID();
        String name = generateVillagerName();
        // Spawn as "none" - no profession assigned initially
        CustomVillager villager = new CustomVillager(id, name, VillagerJob.LABORER);
        initializeNutrientState(villager);
        villager.setHomeLocation(location);
        villager.setWorkLocation(determineInitialWorkLocation(village, villager, location));

        // Auto-assign to first available bed building
        for (VillageBuilding building : village.getBuildings()) {
            if (!building.isCompleted()) continue;
            int capacity = 0;
            BuildingDefinition def = plugin.getBuildingConfigLoader() != null
                    ? plugin.getBuildingConfigLoader().getDefinition(building.getTypeKey())
                    : null;
            if (def != null) {
                capacity = Math.max(0, def.getVillagerSlots());
            }
            if (capacity <= 0) continue;
            long assignedCount = village.getVillagers().stream()
                    .filter(v -> building.getId().equals(v.getAssignedBedBuildingId()))
                    .count();
            if (assignedCount < capacity) {
                villager.setAssignedBedBuildingId(building.getId());
                break;
            }
        }

        village.addVillager(villager);

        // Ersetze den gefundenen Vanilla-Villager
        plugin.getLogger().info("Vanilla Villager gefunden! Ersetze ihn mit: " + name);
        replaceVanillaVillager(villager, vanillaVillager);

        villageManager.saveVillage(village);
        return villager;
    }

    /**
     * Legacy method for backward compatibility - spawns with profession key.
     * @deprecated Use {@link #spawnVillager(Village, Location)} instead. The professionKey parameter is ignored.
     */
    @Deprecated
    public CustomVillager spawnVillager(Village village, String professionKey, Location location) {
        // Redirect to spawn without profession - professionKey is ignored
        return spawnVillager(village, location);
    }

    /**
     * Spawns a Bukkit Villager entity at the given location for the custom villager.
     */
    private void spawnBukkitVillager(CustomVillager customVillager, Location location) {
        if (location.getWorld() == null) return;

        // If Citizens is available, use Citizens for NPC
        if (citizensHook.isAvailable()) {
            citizensHook.spawnNpc(customVillager, location);
            return;
        }

        // Spawn a real Bukkit Villager entity
        location.getWorld().spawn(location, Villager.class, villagerEntity -> {
            villagerEntity.customName(net.kyori.adventure.text.Component.text(customVillager.getName()));
            villagerEntity.setCustomNameVisible(true);
            villagerEntity.setProfession(Villager.Profession.NONE);
            villagerEntity.setVillagerType(Villager.Type.PLAINS);
            villagerEntity.setAI(true);
            // Store the custom villager ID in PersistentDataContainer for identification
            villagerEntity.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "village-villager-id"),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    customVillager.getId().toString());
        });
    }

    private void spawnVanillaVillager(CustomVillager customVillager, Location location) {
        if (location.getWorld() == null) return;

        location.getWorld().spawn(location, Villager.class, villagerEntity -> {
            villagerEntity.customName(net.kyori.adventure.text.Component.text(customVillager.getName()));
            villagerEntity.setCustomNameVisible(true);
            villagerEntity.setProfession(Villager.Profession.NONE);
            villagerEntity.setVillagerType(Villager.Type.PLAINS);
            villagerEntity.setAI(true);
            villagerEntity.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "village-villager-id"),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    customVillager.getId().toString());
        });
    }

    /**
     * Finds a vanilla (unmodified) Villager within the village bounds.
     * Returns null if no vanilla villager is found.
     */
    private Villager findVanillaVillagerInBounds(Village village) {
        World world = village.getWorld();
        if (world == null) return null;

        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "village-villager-id");
        
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (!(entity instanceof Villager villager)) continue;
            
            // Skip if this is already a custom village villager
            if (villager.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
                continue;
            }
            
            // Check if the villager is within village bounds
            if (village.containsLocation(villager.getLocation())) {
                return villager;
            }
        }
        
        return null;
    }

    /**
     * Replaces a vanilla villager with a custom village villager.
     * This converts the vanilla villager's entity to represent the custom villager.
     */
    private void replaceVanillaVillager(CustomVillager customVillager, Villager vanillaVillager) {
        // Update the vanilla villager's display to represent the custom villager
        vanillaVillager.customName(net.kyori.adventure.text.Component.text(customVillager.getName()));
        vanillaVillager.setCustomNameVisible(true);
        vanillaVillager.setProfession(Villager.Profession.NONE);
        vanillaVillager.setVillagerType(Villager.Type.PLAINS);
        vanillaVillager.setAI(true);
        
        // Mark this entity as a custom village villager
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "village-villager-id");
        vanillaVillager.getPersistentDataContainer().set(
                key, org.bukkit.persistence.PersistentDataType.STRING,
                customVillager.getId().toString());
        
        // Update the custom villager to reference this entity's location
        customVillager.setHomeLocation(vanillaVillager.getLocation());
        customVillager.setNpcId(-1); // Not a Citizens NPC
    }

    /**
     * Spawnt einen Vanilla-Villager an einer bestimmten Location.
     */
    public void spawnVanillaVillagerAt(CustomVillager customVillager, Location location) {
        spawnVanillaVillager(customVillager, location);
    }

    private Location determineReviveLocation(Village village, CustomVillager villager) {
        if (villager != null && villager.getHomeLocation() != null && villager.getHomeLocation().getWorld() != null) {
            return villager.getHomeLocation().clone();
        }
        if (village.getBellLocation() != null && village.getBellLocation().getWorld() != null) {
            return village.getBellLocation().clone();
        }
        if (village.getLocation() != null && village.getLocation().getWorld() != null) {
            return village.getLocation().clone();
        }
        return null;
    }

    public void spawnCustomVillager(CustomVillager customVillager, Location location) {
        if (customVillager == null || location == null || location.getWorld() == null) {
            return;
        }
        if (citizensHook.isAvailable()) {
            int npcId = citizensHook.spawnNpc(customVillager, location);
            if (npcId >= 0) {
                customVillager.setNpcId(npcId);
                return;
            }
        } else {
            spawnVanillaVillagerAt(customVillager, location);
            customVillager.setNpcId(-1);
            return;
        }

        spawnVanillaVillagerAt(customVillager, location);
        customVillager.setNpcId(-1);
    }

    public boolean isRevivalAvailable(Village village) {
        return village != null && village.getLastDeadVillager() != null;
    }

    public long getRevivalCooldownMillis(Village village) {
        if (village == null) return 0L;
        long base = configManager.getVillagerRevivalBaseCooldownSeconds();
        long increase = configManager.getVillagerRevivalCooldownIncreasePerUseSeconds();
        long max = configManager.getVillagerRevivalMaxCooldownSeconds();
        long seconds = base + Math.max(0, village.getReviveUses()) * increase;
        if (max > 0) {
            seconds = Math.min(seconds, max);
        }
        return Math.max(0L, seconds) * 1000L;
    }

    public double getRevivalCost(Village village) {
        if (village == null) return 0.0;
        double base = configManager.getVillagerRevivalBaseCost();
        double increase = configManager.getVillagerRevivalCostIncreasePerUse();
        double max = configManager.getVillagerRevivalMaxCost();
        double cost = base + Math.max(0, village.getReviveUses()) * increase;
        if (max > 0) {
            cost = Math.min(cost, max);
        }
        return Math.max(0.0, cost);
    }

    public long getRevivalCooldownRemainingMillis(Village village) {
        if (village == null) return 0L;
        long last = village.getLastReviveAt();
        if (last <= 0L) return 0L;
        long cooldown = getRevivalCooldownMillis(village);
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0L, cooldown - elapsed);
    }

    public boolean canReviveNow(Village village) {
        return getRevivalCooldownRemainingMillis(village) <= 0L;
    }

    public boolean reviveLastDeadVillager(Village village) {
        if (village == null || village.getLastDeadVillager() == null) {
            return false;
        }

        CustomVillager deadVillager = village.getLastDeadVillager();
        Location reviveLocation = determineReviveLocation(village, deadVillager);
        if (reviveLocation == null || reviveLocation.getWorld() == null) {
            return false;
        }

        if (!village.getVillagers().contains(deadVillager)) {
            village.addVillager(deadVillager);
        }

        deadVillager.setNpcId(-1);
        spawnCustomVillager(deadVillager, reviveLocation);
        village.setLastDeadVillager(null);
        village.incrementReviveUses();
        village.setLastReviveAt(System.currentTimeMillis());
        villageManager.saveVillage(village);
        return true;
    }

    /**
     * Entfernt die Vanilla-Villager Entity.
     */
    /**
     * Holt die aktuelle Location des Villagers.
     */
    public Location getVillagerLocation(Village village, CustomVillager villager) {
        if (villager.getNpcId() >= 0 && citizensHook.isAvailable()) {
            // NPC Position
            return citizensHook.getNpcLocation(villager.getNpcId());
        } else {
            // Vanilla-Villager Position suchen
            return findVanillaVillagerLocation(village, villager);
        }
    }
    /**
     * Findet die Position eines Vanilla-Villagers.
     */
    private Location findVanillaVillagerLocation(Village village, CustomVillager villager) {
        if (villager.getVanillaEntityId() != null) {
            for (Villager entity : village.getLocation().getWorld().getEntitiesByClass(Villager.class)) {
                if (entity.getUniqueId().equals(villager.getVanillaEntityId())) {
                    return entity.getLocation();
                }
            }
        }
        // Fallback: Home-Location
        return villager.getHomeLocation();
    }
    /**
     * Assigns a job (profession) to a villager, linking to a specific building.
     */
    public boolean assignJob(Village village, CustomVillager villager, String professionKey, UUID buildingId) {
        VillagerProfession profession = configManager.getProfession(professionKey);
        if (profession == null) return false;
        if (village.getLevel() < profession.getRequiredLevel()) return false;

        // Setze Beruf
        villager.setProfessionKey(professionKey);
        villager.setAssignedJobBuildingId(buildingId);

        // Find work location from building
        for (VillageBuilding building : village.getBuildings()) {
            if (building.getId().equals(buildingId)) {
                villager.setWorkLocation(building.getLocation());
                break;
            }
        }

        // BEI BERUFSZUWEISUNG: Konvertiere zu NPC
        plugin.getLogger().info("Beruf zugewiesen: " + professionKey + " - konvertiere zu NPC");
        scheduleManager.ensureNpcVillager(village, villager);

        // Setze Skin falls verfügbar
        if (citizensHook.isAvailable() && villager.getNpcId() >= 0) {
            citizensHook.setNpcSkin(villager.getNpcId(), profession.getSkinName());
        }

        villageManager.saveVillage(village);
        return true;
    }

    /**
     * Fires a villager from their current job.
     * @param resetProgress if true, resets XP and level to defaults
     */
    public void fireFromJob(Village village, CustomVillager villager, boolean resetProgress) {
        villager.setProfessionKey("none");
        villager.setAssignedJobBuildingId(null);
        villager.setWorkLocation(villager.getHomeLocation());

        if (resetProgress) {
            villager.setLevel(1);
            villager.setXp(0);
            villager.getSkills().clear();
        }

        // BEI BERUFSENTLASSUNG: Konvertiere zurück zu Vanilla-Villager
        plugin.getLogger().info("Beruf entlassen - konvertiere zurück zu Vanilla-Villager");
        scheduleManager.ensureVanillaVillager(village, villager);

        villageManager.saveVillage(village);
    }

    /**
     * Whether job progress should be reset on firing (from config).
     */
    public boolean shouldResetProgressOnFire() {
        return configManager.getConfig().getBoolean("villagers.reset-progress-on-fire", false);
    }

    public void removeVillager(Village village, UUID villagerId) {
        CustomVillager customVillager = village.getVillagers().stream()
                .filter(v -> v.getId().equals(villagerId))
                .findFirst()
                .orElse(null);
        
        if (customVillager == null) {
            plugin.getLogger().warning("Villager mit ID " + villagerId + " nicht gefunden!");
            return;
        }
        
        plugin.getLogger().info("Entferne Villager: " + customVillager.getName() + " (ID: " + villagerId + ", NPC: " + customVillager.getNpcId() + ")");
        
        // If it's a Citizens NPC, remove it through the hook
        if (customVillager.getNpcId() >= 0 && citizensHook.isAvailable()) {
            citizensHook.removeNpc(villagerId);
            plugin.getLogger().info("Citizens NPC entfernt.");
        } else if (customVillager.getNpcId() < 0) {
            // Remove the Bukkit Villager entity with custom village-villager-id marker
            World world = village.getWorld();
            if (world != null) {
                org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "village-villager-id");
                // Make a copy of the entities list to avoid concurrent modification
                java.util.List<org.bukkit.entity.Entity> entitiesToCheck = new java.util.ArrayList<>(world.getEntities());
                boolean found = false;
                for (org.bukkit.entity.Entity entity : entitiesToCheck) {
                    if (entity instanceof Villager v) {
                        String idStr = v.getPersistentDataContainer().get(
                                key, org.bukkit.persistence.PersistentDataType.STRING);
                        if (villagerId.toString().equals(idStr)) {
                            v.setHealth(0.0); // Kill the entity first to ensure removal
                            entity.remove(); // Then remove it
                            found = true;
                            plugin.getLogger().info("Bukkit Villager-Entity entfernt.");
                            break;
                        }
                    }
                }
                if (!found) {
                    plugin.getLogger().warning("Keine übereinstimmende Villager-Entity gefunden!");
                }
            }
        }
        
        // Remove from village data
        village.removeVillager(villagerId);
        villageManager.saveVillage(village);
        plugin.getLogger().info("Villager erfolgreich aus der Datenbank entfernt.");
    }

    public void feedVillager(CustomVillager villager, double amount) {
        if (nutritionService != null && !villager.getNutrientLevels().isEmpty()) {
            for (String key : villager.getNutrientLevels().keySet()) {
                villager.addNutrientLevel(key, amount / villager.getNutrientLevels().size());
            }
            return;
        }
        villager.setNeedValue(VillagerNeed.HUNGER,
                villager.getNeedValue(VillagerNeed.HUNGER) + amount);
    }

    public boolean feedVillager(Player player, CustomVillager villager, Material foodMaterial) {
        if (foodMaterial == null || foodMaterial == Material.AIR) {
            return false;
        }
        if (nutritionService != null) {
            return nutritionService.feedWithMaterial(player, villager, foodMaterial);
        }
        Map<String, Map<String, Double>> recoveryMap = configManager.getVillagerFoodRecoveryByItem();
        Map<String, Double> nutrientRecovery = recoveryMap.get(foodMaterial.name().toUpperCase(java.util.Locale.ROOT));
        if (nutrientRecovery == null || nutrientRecovery.isEmpty()) {
            return false;
        }

        boolean creative = player != null && player.getGameMode() == GameMode.CREATIVE;
        if (!creative && player != null) {
            if (!player.getInventory().contains(foodMaterial, 1)) {
                return false;
            }
            player.getInventory().removeItem(new ItemStack(foodMaterial, 1));
        }

        double defaultCapacity = configManager.getVillagerNutrientStorageCapacity();
        for (Map.Entry<String, Double> entry : nutrientRecovery.entrySet()) {
            if ("hunger".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            String nutrientKey = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            villager.setNutrientCapacity(nutrientKey, defaultCapacity);
            villager.addNutrientLevel(nutrientKey, entry.getValue());
        }
        return true;
    }

    public boolean feedVillager(CustomVillager villager, Material foodMaterial) {
        if (nutritionService != null) {
            return nutritionService.feedFromInventory(villager, foodMaterial);
        }
        Map<String, Map<String, Double>> recoveryMap = configManager.getVillagerFoodRecoveryByItem();
        Map<String, Double> nutrientRecovery = recoveryMap.get(foodMaterial.name().toUpperCase(java.util.Locale.ROOT));
        if (nutrientRecovery == null || nutrientRecovery.isEmpty()) {
            return false;
        }
        if (!villager.getInventory().containsKey(foodMaterial) || villager.getInventory().get(foodMaterial) <= 0) {
            return false;
        }
        villager.removeItem(foodMaterial, 1);
        double defaultCapacity = configManager.getVillagerNutrientStorageCapacity();
        for (Map.Entry<String, Double> entry : nutrientRecovery.entrySet()) {
            if ("hunger".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            String nutrientKey = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            villager.setNutrientCapacity(nutrientKey, defaultCapacity);
            villager.addNutrientLevel(nutrientKey, entry.getValue());
        }
        return true;
    }

    private String generateVillagerName() {
        String[] names = {
                "Hans", "Fritz", "Karl", "Anna", "Maria", "Otto",
                "Heinrich", "Wilhelm", "Emma", "Frieda", "Gustav",
                "Ludwig", "Helga", "Gertrude", "Hermann", "Bertha",
                "Dieter", "Ingrid", "Walter", "Greta"
        };
        return names[(int) (Math.random() * names.length)];
    }
    // Utility methods
    private Location determineInitialWorkLocation(Village village, CustomVillager villager, Location spawnLocation) {
        if (village != null && villager != null && villager.getJob() != null) {
            java.util.Set<String> preferredKeys = villager.getJob().getPreferredBuildingKeys();
            for (VillageBuilding building : village.getBuildings()) {
                if (!building.isCompleted()) continue;
                if (preferredKeys.contains(building.getTypeKey())) {
                    return building.getLocation();
                }
            }
        }

        Location target = spawnLocation.clone();
        int dx = 2 + (int) (Math.random() * 3);
        int dz = 2 + (int) (Math.random() * 3);
        if (Math.random() < 0.5) dx = -dx;
        if (Math.random() < 0.5) dz = -dz;
        target.add(dx, 0, dz);
        target.setY(spawnLocation.getY());
        return target;
    }

    private VillagerJob parseVillagerJob(String jobStr) {
        return VillagerJob.fromString(jobStr);
    }

    public VillageConfigManager getConfigManager() {
        return configManager;
    }

    public int getSkillTreeMaxLevel() {
        return configManager.getSkillTreeMaxLevel();
    }

    public int getSkillTreeXpPerLevel() {
        return configManager.getSkillTreeXpPerLevel();
    }

    public double getUpgradeSpeedMultiplierPerLevel() {
        return configManager.getUpgradeSpeedMultiplierPerLevel();
    }

    public Location resolveActivityLocation(VillageBuilding building, String locationKey) {
        if (building == null || building.getLocation() == null) return null;
        return building.getLocation();
    }

    /**
     * Entfernt die Vanilla-Villager Entity.
     */
    public void removeVanillaVillagerEntity(Village village, CustomVillager villager) {
        if (villager.getVanillaEntityId() != null) {
            for (Villager entity : village.getLocation().getWorld().getEntitiesByClass(Villager.class)) {
                if (entity.getUniqueId().equals(villager.getVanillaEntityId())) {
                    entity.remove();
                    break;
                }
            }
            villager.setVanillaEntityId(null);
        }
    }

    private double getAestheticProductionMultiplier(Village village, CustomVillager villager) {
        if (villager.getAssignedJobBuildingId() == null || village == null) {
            return 1.0;
        }
        VillageBuilding building = village.getBuildings().stream()
                .filter(b -> b.getId().equals(villager.getAssignedJobBuildingId()))
                .findFirst()
                .orElse(null);
        if (building == null || !building.hasAestheticScore()) {
            return 1.0;
        }
        AestheticScoreService aestheticService = plugin.getAestheticScoreService();
        if (aestheticService == null) {
            return 1.0;
        }
        return aestheticService.getProductionMultiplier(building.getAestheticScore());
    }

    /**
     * Gibt den VillagerScheduleManager zurück.
     */
    public VillagerScheduleManager getScheduleManager() {
        return scheduleManager;
    }

    /**
     * Ersetzt alle Citizens-NPCs mit Minecraft-Villagern.
     */
    public void replaceCitizensVillagersWithMinecraftVillagers(Village village) {
        org.bukkit.NamespacedKey villagerKey = new org.bukkit.NamespacedKey(plugin, "village-villager-id");
        for (CustomVillager villager : village.getVillagers()) {
            if (villager.getNpcId() >= 0) {
                // Citizens-NPC entfernen und sauberen Vanilla-Villager ohne Namen spawnen
                Location currentLocation = getVillagerLocation(village, villager);
                citizensHook.removeNpc(villager.getId());
                villager.setNpcId(-1);
                if (currentLocation != null) {
                    spawnCleanVanillaVillager(currentLocation);
                }
            } else {
                // Bereits als Vanilla-Entität getrackten Villager aufräumen: Namen + Tag entfernen
                if (village.getLocation() != null && village.getLocation().getWorld() != null) {
                    for (org.bukkit.entity.Villager entity :
                            village.getLocation().getWorld().getEntitiesByClass(org.bukkit.entity.Villager.class)) {
                        String tag = entity.getPersistentDataContainer()
                                .get(villagerKey, org.bukkit.persistence.PersistentDataType.STRING);
                        if (tag != null && tag.equals(villager.getId().toString())) {
                            entity.customName(null);
                            entity.setCustomNameVisible(false);
                            entity.getPersistentDataContainer().remove(villagerKey);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Spawnt einen vollständig namenlosen Vanilla-Villager (für Dorf-Auflösung).
     * Kein PersistentData-Tag, kein Custom-Name – vollständiger Vanilla-Zustand.
     */
    private void spawnCleanVanillaVillager(Location location) {
        if (location.getWorld() == null) return;
        location.getWorld().spawn(location, org.bukkit.entity.Villager.class, entity -> {
            entity.customName(null);
            entity.setCustomNameVisible(false);
            entity.setProfession(org.bukkit.entity.Villager.Profession.NONE);
            entity.setVillagerType(org.bukkit.entity.Villager.Type.PLAINS);
            entity.setAI(true);
        });
    }
}
