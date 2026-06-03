package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.hook.CitizensHook;
import com.example.village.model.BuildingDefinition;
import com.example.village.model.BuildingType;
import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.model.VillageBuilding;
import com.example.village.model.VillagerJob;
import com.example.village.model.VillagerNeed;
import com.example.village.model.VillagerProfession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VillagerService {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VillageManager villageManager;
    private final CitizensHook citizensHook;
    private final VillagerScheduleManager scheduleManager;
    private int tickTaskId = -1;

    public VillagerService(VillagePlugin plugin, VillageConfigManager configManager,
                           VillageManager villageManager, CitizensHook citizensHook) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.villageManager = villageManager;
        this.citizensHook = citizensHook;
        this.scheduleManager = new VillagerScheduleManager(this, citizensHook);
    }

    public void startVillagerTasks() {
        // Run villager tick every 20 ticks (1 second)
        tickTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAllVillagers, 20L, 20L).getTaskId();
    }

    public void stopVillagerTasks() {
        if (tickTaskId != -1) {
            Bukkit.getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }
    }

    private void tickAllVillagers() {
        for (Village village : villageManager.getAllVillages()) {
            for (CustomVillager villager : village.getVillagers()) {
                tickVillager(village, villager);
            }
        }
    }

    private void tickVillager(Village village, CustomVillager villager) {
        // Decay needs (1 second = 1/60 minute)
        villager.decayNeeds(1.0 / 60.0);

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

    private void produce(Village village, CustomVillager villager, VillagerProfession profession) {
        // Check if villager's needs are critical - reduced production if unhappy/hungry
        if (villager.isNeedCritical(VillagerNeed.HUNGER)) return;

        List<Material> produces = profession.getProduces();
        if (produces.isEmpty()) return;

        // Level affects production amount
        int amount = 1 + (villager.getLevel() / 3);

        for (Material material : produces) {
            villager.addItem(material, amount);
        }

        // Add XP
        double xpGain = 1.0 + (villager.getLevel() * 0.5);
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
                continue;
            }
            BuildingType type = configManager.getBuildingType(building.getTypeKey());
            if (type != null) {
                beds += type.getEffectiveVillagerCapacity(building.getLevel());
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
        BuildingType type = configManager.getBuildingType(building.getTypeKey());
        return type != null ? type.getEffectiveVillagerCapacity(building.getLevel()) : 0;
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
            } else {
                BuildingType type = configManager.getBuildingType(building.getTypeKey());
                if (type != null) capacity = type.getEffectiveVillagerCapacity(building.getLevel());
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
        villager.setNeedValue(VillagerNeed.HUNGER,
                villager.getNeedValue(VillagerNeed.HUNGER) + amount);
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
            String workBuildingKey = villager.getJob().getWorkBuildingKey();
            for (VillageBuilding building : village.getBuildings()) {
                if (!building.isCompleted()) continue;
                if (building.getTypeKey().equals(workBuildingKey)) {
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
        if (jobStr == null) return VillagerJob.LABORER;
        for (VillagerJob job : VillagerJob.values()) {
            if (job.getWorkBuildingKey().equals(jobStr)) {
                return job;
            }
        }
        return VillagerJob.LABORER; // Default fallback
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
        BuildingType legacy = configManager.getBuildingType(building.getTypeKey());
        if (legacy != null) {
            Location absolute = legacy.getAbsoluteLocation(locationKey, building.getLocation());
            if (absolute != null) return absolute;
        }
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
