package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.hook.CitizensHook;
import com.example.village.model.CustomVillager;
import com.example.village.model.VillagerActivity;
import com.example.village.model.VillagerNeed;
import com.example.village.model.Village;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Zentrale Logik für Villager-Hunger, Nährstoffe, Fütterung, Decay, Effekte und Warnungen.
 */
public final class VillagerNutritionService {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private BuildingChestManager chestManager;
    private QuestManager questManager;

    /** Villager → Effekt-Key → Ablaufzeit (ms) */
    private final Map<UUID, Map<String, Long>> activeEffects = new HashMap<>();
    /** Villager → letzte Hunger-Warnung (ms) */
    private final Map<UUID, Long> lastWarningTime = new HashMap<>();
    /** Villager → Tick-Zähler für Schaden */
    private final Map<UUID, Integer> damageTickCounters = new HashMap<>();
    /** Villager → letzte Fütterungszeit (ms) */
    private final Map<UUID, Long> lastFeedTime = new HashMap<>();
    /** Villager → ausstehende zeitliche Nährstoff-Wiederherstellung */
    private final Map<UUID, List<PendingRecovery>> pendingRecoveries = new HashMap<>();
    /** Villager → Tick-Zähler für periodische Recovery */
    private final Map<UUID, Integer> recoveryTickCounters = new HashMap<>();

    public VillagerNutritionService(VillagePlugin plugin, VillageConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void setChestManager(BuildingChestManager chestManager) {
        this.chestManager = chestManager;
    }

    public void setQuestManager(QuestManager questManager) {
        this.questManager = questManager;
    }

    // ===================== TICK =====================

    public void tick(Village village, CustomVillager villager, double minutesElapsed) {
        ensureNutrientsInitialized(villager);
        applyNutrientCapacityFromUpgrades(village, villager);

        String activity = villager.getActivityDecayKey();
        decayNutrients(villager, minutesElapsed, activity, village);

        applyHungerDamage(village, villager);
        maybeWarnVillage(village, villager);
        refreshActiveEffects(villager);
        applyPendingRecoveries(villager);
        applyMovementSpeedEffect(villager);
        maybeAutoFeedFromChest(village, villager);
    }

    private void applyPendingRecoveries(CustomVillager villager) {
        if (!configManager.isPeriodicRecoveryEnabled()) {
            return;
        }
        List<PendingRecovery> pending = pendingRecoveries.get(villager.getId());
        if (pending == null || pending.isEmpty()) {
            return;
        }

        int intervalSeconds = configManager.getPeriodicRecoveryTickIntervalSeconds();
        int counter = recoveryTickCounters.merge(villager.getId(), 20, Integer::sum);
        if (counter < intervalSeconds * 20) {
            return;
        }
        recoveryTickCounters.put(villager.getId(), 0);

        long now = System.currentTimeMillis();
        Iterator<PendingRecovery> it = pending.iterator();
        while (it.hasNext()) {
            PendingRecovery recovery = it.next();
            if (now >= recovery.endTimeMs()) {
                it.remove();
                continue;
            }
            double chunk = recovery.remainingAmount() / Math.max(1, recovery.ticksRemaining(now, intervalSeconds));
            villager.addNutrientLevel(recovery.nutrientKey(), chunk);
            recovery.consume(chunk);
        }
        if (pending.isEmpty()) {
            pendingRecoveries.remove(villager.getId());
        }
    }

    private void applyMovementSpeedEffect(CustomVillager villager) {
        double multiplier = getNutrientEffectMultiplier(villager, "movement-speed");
        CitizensHook citizensHook = plugin.getCitizensHook();
        if (citizensHook == null || !citizensHook.isAvailable() || villager.getNpcId() < 0) {
            return;
        }
        net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(villager.getNpcId());
        if (npc == null || !npc.isSpawned()) {
            return;
        }
        npc.getNavigator().getLocalParameters().speedModifier((float) multiplier);
    }

    private void maybeAutoFeedFromChest(Village village, CustomVillager villager) {
        if (!configManager.isAutoFeedFromChestEnabled() || !configManager.isAllowSelfFeeding()) {
            return;
        }
        if (!isHungerCritical(villager) && villager.getNeedValue(VillagerNeed.HUNGER) > getCriticalThreshold() * 1.5) {
            return;
        }
        if (hasEdibleFood(villager)) {
            if (isHungerCritical(villager)) {
                eatBestFromInventory(villager);
            }
            return;
        }
        if (getFoodFromVillageStorage(village, villager)) {
            eatBestFromInventory(villager);
        }
    }

    // ===================== DECAY =====================

    public void decayNutrients(CustomVillager villager, double minutes, String activityKey, Village village) {
        if (villager.getNutrientLevels().isEmpty()) {
            return;
        }

        double activityMultiplier = configManager.getNutrientDecayByActivity(activityKey);
        double seasonalMultiplier = configManager.getSeasonalDecayMultiplier(
                village != null && village.getWorld() != null ? village.getWorld().getTime() : 6000L);
        double jobMultiplier = configManager.getJobDecayMultiplier(villager.getProfessionKey());

        for (Map.Entry<String, Double> entry : new HashMap<>(villager.getNutrientLevels()).entrySet()) {
            String key = entry.getKey();
            double capacity = villager.getNutrientCapacity(key);
            double decayPerMinute = configManager.getNutrientDecayPerMinute(key);
            double nutrientActivityMultiplier = configManager.getNutrientActivityMultiplier(activityKey, key);
            double decay = decayPerMinute * minutes * activityMultiplier * nutrientActivityMultiplier * seasonalMultiplier * jobMultiplier;
            decay *= (capacity / 100.0);
            villager.setNutrientLevel(key, entry.getValue() - decay);
        }
    }

    public void applyWorkNutrientCost(CustomVillager villager, Village village, double extraMinutes) {
        decayNutrients(villager, extraMinutes, "WORK", village);
    }

    // ===================== THRESHOLDS =====================

    public double getCriticalThreshold() {
        return configManager.getNeedCritical("hunger");
    }

    public boolean isHungerCritical(CustomVillager villager) {
        return villager.getNeedValue(VillagerNeed.HUNGER) <= getCriticalThreshold();
    }

    public boolean isHungerDamaging(CustomVillager villager) {
        return villager.getNeedValue(VillagerNeed.HUNGER) < configManager.getVillagerHungerDamageThreshold();
    }

    public void applyActivityEndNutrientCost(CustomVillager villager, Village village, VillagerActivity activity) {
        if (!configManager.isActivityEndCostEnabled() || activity == null) {
            return;
        }
        String activityKey = switch (activity.getType()) {
            case WORK, PREPARE, GATHER -> "WORK";
            case SLEEP -> "SLEEP";
            case TRAVEL_TO -> "WAKE_UP";
            case GET_FOOD -> "GET_FOOD";
            case EAT_FOOD -> "EAT_FOOD";
            default -> "IDLE";
        };
        double minutes = activity.getDurationTicks() / 20.0 / 60.0;
        double endMultiplier = configManager.getActivityEndCostMultiplier(activityKey);
        decayNutrients(villager, minutes * endMultiplier, activityKey, village);
    }

    // ===================== FEEDING =====================

    public boolean canFeedNow(CustomVillager villager) {
        int intervalSeconds = configManager.getFeedIntervalSeconds();
        if (intervalSeconds <= 0) {
            return true;
        }
        Long last = lastFeedTime.get(villager.getId());
        if (last == null) {
            return true;
        }
        return System.currentTimeMillis() - last >= intervalSeconds * 1000L;
    }

    public boolean feedFromPlayer(Player player, CustomVillager villager) {
        if (!configManager.isAllowPlayerFeeding()) {
            return false;
        }
        if (!canFeedNow(villager)) {
            return false;
        }
        Material food = findBestFoodForPlayer(player, villager);
        if (food == null) {
            return false;
        }
        return feedWithMaterial(player, villager, food);
    }

    public Material findBestFoodForPlayer(Player player, CustomVillager villager) {
        if (player != null && player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            Material best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (String itemKey : configManager.getVillagerFoodRecoveryByItem().keySet()) {
                Material mat = Material.matchMaterial(itemKey);
                if (mat == null) {
                    continue;
                }
                double score = scoreFood(mat, villager);
                if (score > bestScore) {
                    bestScore = score;
                    best = mat;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return findBestFood(player != null ? player.getInventory().getContents() : null, villager, false);
    }

    public boolean feedWithMaterial(Player player, CustomVillager villager, Material foodMaterial) {
        Map<String, Double> recovery = getRecovery(foodMaterial);
        if (recovery.isEmpty() && configManager.getFeedHungerValue(foodMaterial.name()) <= 0) {
            return false;
        }
        if (player != null && !configManager.isAllowPlayerFeeding()) {
            return false;
        }
        if (!canFeedNow(villager)) {
            return false;
        }

        boolean creative = player != null && player.getGameMode() == org.bukkit.GameMode.CREATIVE;
        if (!creative && player != null) {
            if (!player.getInventory().contains(foodMaterial, 1)) {
                return false;
            }
            player.getInventory().removeItem(new ItemStack(foodMaterial, 1));
        }

        applyRecovery(villager, recovery, foodMaterial);
        lastFeedTime.put(villager.getId(), System.currentTimeMillis());
        onFed(villager, player);
        return true;
    }

    public boolean feedFromInventory(CustomVillager villager, Material foodMaterial) {
        if (!configManager.isAllowSelfFeeding()) {
            return false;
        }
        if (!canFeedNow(villager)) {
            return false;
        }
        Map<String, Double> recovery = getRecovery(foodMaterial);
        if (recovery.isEmpty() && configManager.getFeedHungerValue(foodMaterial.name()) <= 0) {
            return false;
        }
        if (villager.getInventory().getOrDefault(foodMaterial, 0) <= 0) {
            return false;
        }
        villager.removeItem(foodMaterial, 1);
        applyRecovery(villager, recovery, foodMaterial);
        lastFeedTime.put(villager.getId(), System.currentTimeMillis());
        onFed(villager, null);
        return true;
    }

    public boolean eatBestFromInventory(CustomVillager villager) {
        Material best = findBestFoodFromMap(villager.getInventory(), villager);
        if (best == null) {
            return false;
        }
        return feedFromInventory(villager, best);
    }

    /**
     * Holt Nahrung aus dem Dorf-Lager (öffentliche Truhen) und legt sie ins Villager-Inventar.
     */
    public boolean getFoodFromVillageStorage(Village village, CustomVillager villager) {
        if (chestManager == null || !configManager.isAutoFeedFromChestEnabled()) {
            return false;
        }

        Map<Material, Integer> pool = chestManager.getPublicPool(village);
        if (pool.isEmpty()) {
            return false;
        }

        Material best = selectBestFromPool(pool, villager);
        if (best == null) {
            return false;
        }

        Map<Material, Integer> required = Map.of(best, 1);
        if (!chestManager.consumeFromPublic(village, required)) {
            return false;
        }
        villager.addItem(best, 1);
        return true;
    }

    private void applyRecovery(CustomVillager villager, Map<String, Double> recovery, Material foodMaterial) {
        ensureNutrientsInitialized(villager);
        double defaultCapacity = resolveNutrientCapacity(villager);
        String itemKey = foodMaterial.name().toUpperCase(Locale.ROOT);

        double hungerValue = configManager.getFeedHungerValue(itemKey);
        if (recovery.containsKey("hunger")) {
            hungerValue = recovery.get("hunger");
        }

        Map<String, Double> nutrientRecovery = new LinkedHashMap<>(recovery);
        nutrientRecovery.remove("hunger");

        if (hungerValue > 0 && nutrientRecovery.isEmpty()) {
            distributeHungerToNutrients(villager, hungerValue, defaultCapacity);
        } else if (hungerValue > 0) {
            distributeHungerProportionally(villager, hungerValue, nutrientRecovery, defaultCapacity);
        }

        double instantPercent = configManager.getFeedInstantPercent(itemKey) / 100.0;
        double recoverySeconds = configManager.getFeedRecoverySeconds(itemKey);

        for (Map.Entry<String, Double> entry : nutrientRecovery.entrySet()) {
            String nutrientKey = entry.getKey().toLowerCase(Locale.ROOT);
            villager.setNutrientCapacity(nutrientKey, defaultCapacity);
            double total = entry.getValue();
            double instant = total * instantPercent;
            double delayed = total - instant;
            if (instant > 0) {
                villager.addNutrientLevel(nutrientKey, instant);
            }
            if (delayed > 0 && recoverySeconds > 0 && configManager.isPeriodicRecoveryEnabled()) {
                scheduleDelayedRecovery(villager, nutrientKey, delayed, recoverySeconds);
            } else if (delayed > 0) {
                villager.addNutrientLevel(nutrientKey, delayed);
            }
        }

        applyNutrientEffectsOnConsume(villager, foodMaterial);
        villager.adjustMorale(2);
    }

    private void distributeHungerToNutrients(CustomVillager villager, double hungerValue, double defaultCapacity) {
        ConfigurationSection nutrients = configManager.getVillagerNutrientsSection();
        if (nutrients == null || nutrients.getKeys(false).isEmpty()) {
            villager.setNeedValue(VillagerNeed.HUNGER,
                    Math.min(100, villager.getNeedValue(VillagerNeed.HUNGER) + hungerValue));
            return;
        }
        double perNutrient = hungerValue / nutrients.getKeys(false).size();
        for (String nutrientKey : nutrients.getKeys(false)) {
            String normalized = nutrientKey.toLowerCase(Locale.ROOT);
            villager.setNutrientCapacity(normalized, defaultCapacity);
            villager.addNutrientLevel(normalized, perNutrient);
        }
    }

    private void distributeHungerProportionally(CustomVillager villager, double hungerValue,
                                                Map<String, Double> nutrientRecovery, double defaultCapacity) {
        double sum = nutrientRecovery.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum <= 0) {
            distributeHungerToNutrients(villager, hungerValue, defaultCapacity);
            return;
        }
        for (Map.Entry<String, Double> entry : nutrientRecovery.entrySet()) {
            String nutrientKey = entry.getKey().toLowerCase(Locale.ROOT);
            villager.setNutrientCapacity(nutrientKey, defaultCapacity);
            villager.addNutrientLevel(nutrientKey, hungerValue * (entry.getValue() / sum));
        }
    }

    private void scheduleDelayedRecovery(CustomVillager villager, String nutrientKey,
                                         double amount, double recoverySeconds) {
        long endTime = System.currentTimeMillis() + (long) (recoverySeconds * 1000L);
        pendingRecoveries.computeIfAbsent(villager.getId(), id -> new ArrayList<>())
                .add(new PendingRecovery(nutrientKey, amount, endTime));
    }

    private void onFed(CustomVillager villager, Player player) {
        if (player != null) {
            double relationBonus = getNutrientEffectMultiplier(villager, "relationship")
                    + getNutrientEffectMultiplier(villager, "communication") - 1.0;
            if (relationBonus > 0) {
                int repGain = Math.max(1, (int) Math.round(relationBonus * 2));
                villager.getRelation(player.getUniqueId()).addReputation(repGain);
            }
        }
        if (questManager != null && player != null) {
            questManager.recordVillagerFeed(player, villager);
        }
    }

    // ===================== FOOD SELECTION =====================

    public Material findBestFood(ItemStack[] contents, CustomVillager villager, boolean villagerInventory) {
        if (villagerInventory) {
            return findBestFoodFromMap(villager.getInventory(), villager);
        }
        if (contents == null) {
            return null;
        }
        Material best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                continue;
            }
            double score = scoreFood(stack.getType(), villager);
            if (score > bestScore) {
                bestScore = score;
                best = stack.getType();
            }
        }
        return best;
    }

    public Material findBestFoodFromMap(Map<Material, Integer> items, CustomVillager villager) {
        Material best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Map.Entry<Material, Integer> entry : items.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            double score = scoreFood(entry.getKey(), villager);
            if (score > bestScore) {
                bestScore = score;
                best = entry.getKey();
            }
        }
        return best;
    }

    private Material selectBestFromPool(Map<Material, Integer> pool, CustomVillager villager) {
        List<String> bakerPriority = configManager.getBakerPriorityFoods();
        for (String matName : bakerPriority) {
            Material mat = Material.matchMaterial(matName);
            if (mat != null && pool.getOrDefault(mat, 0) > 0 && !getRecovery(mat).isEmpty()) {
                return mat;
            }
        }
        return findBestFoodFromMap(pool.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), villager);
    }

    public boolean hasEdibleFood(CustomVillager villager) {
        for (Map.Entry<Material, Integer> entry : villager.getInventory().entrySet()) {
            if (entry.getValue() > 0 && !getRecovery(entry.getKey()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public double scoreFood(Material material, CustomVillager villager) {
        Map<String, Double> recovery = getRecovery(material);
        if (recovery.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }

        double score = 0.0;
        double hunger = villager.getNeedValue(VillagerNeed.HUNGER);
        if (recovery.containsKey("hunger")) {
            score += recovery.get("hunger") * (1.0 + Math.max(0.0, (40.0 - hunger) / 40.0));
        }

        ConfigurationSection nutrients = configManager.getVillagerNutrientsSection();
        if (nutrients != null) {
            for (String nutrientKey : nutrients.getKeys(false)) {
                String normalized = nutrientKey.toLowerCase(Locale.ROOT);
                double current = villager.getNutrientLevel(normalized);
                double capacity = villager.getNutrientCapacity(normalized);
                double percent = capacity > 0 ? (current / capacity) * 100.0 : 0.0;
                double missing = Math.max(0.0, 100.0 - percent);
                if (recovery.containsKey(normalized)) {
                    score += recovery.get(normalized) * (1.0 + missing / 100.0);
                }
            }
        }
        return score;
    }

    private Map<String, Double> getRecovery(Material material) {
        return configManager.getVillagerFoodRecoveryByItem()
                .getOrDefault(material.name().toUpperCase(Locale.ROOT), Collections.emptyMap());
    }

    // ===================== PRODUCTION / BALANCE =====================

    public double getProductionMultiplier(CustomVillager villager) {
        if (!configManager.isBalancedDietEnabled()) {
            return getNutrientEffectMultiplier(villager, "production-speed");
        }

        double multiplier = getNutrientEffectMultiplier(villager, "production-speed");
        double hunger = villager.getNeedValue(VillagerNeed.HUNGER);
        double critical = getCriticalThreshold();

        if (hunger <= critical) {
            return 0.0;
        }
        if (hunger < critical * 2) {
            multiplier *= (1.0 - configManager.getBalancedDietProductionPenalty());
        }

        if (hasLowNutrient(villager)) {
            multiplier *= (1.0 - configManager.getBalancedDietProductionPenalty());
        }

        if (hasExcessSugar(villager)) {
            multiplier *= 0.95;
        }

        return Math.max(0.0, multiplier);
    }

    public boolean hasLowNutrient(CustomVillager villager) {
        double threshold = configManager.getBalancedDietLowNutrientThreshold();
        for (Map.Entry<String, Double> entry : villager.getNutrientLevels().entrySet()) {
            double capacity = villager.getNutrientCapacity(entry.getKey());
            double percent = capacity > 0 ? (entry.getValue() / capacity) * 100.0 : 0.0;
            if (percent < threshold) {
                return true;
            }
        }
        return false;
    }

    public boolean hasExcessSugar(CustomVillager villager) {
        double threshold = configManager.getBalancedDietExcessSugarThreshold();
        double capacity = villager.getNutrientCapacity("sugar");
        if (capacity <= 0) {
            return false;
        }
        return (villager.getNutrientLevel("sugar") / capacity) * 100.0 > threshold;
    }

    // ===================== EFFECTS =====================

    private void applyNutrientEffectsOnConsume(CustomVillager villager, Material foodMaterial) {
        ConfigurationSection nutrients = configManager.getVillagerNutrientsSection();
        if (nutrients == null) {
            return;
        }

        Map<String, Double> recovery = getRecovery(foodMaterial);
        long now = System.currentTimeMillis();
        Map<String, Long> effects = activeEffects.computeIfAbsent(villager.getId(), k -> new HashMap<>());

        for (String nutrientKey : recovery.keySet()) {
            if ("hunger".equalsIgnoreCase(nutrientKey)) {
                continue;
            }
            ConfigurationSection nutrientSection = nutrients.getConfigurationSection(nutrientKey);
            if (nutrientSection == null) {
                continue;
            }

            int durationSeconds = nutrientSection.getInt("duration-seconds", 0);
            if (durationSeconds <= 0) {
                continue;
            }

            String normalized = nutrientKey.toLowerCase(Locale.ROOT);
            int maxDurationSeconds = configManager.getNutrientMaxDurationSeconds(normalized);
            long maxExpiry = now + maxDurationSeconds * 1000L;
            long newExpiry = now + durationSeconds * 1000L;

            if (configManager.isNutrientStackDuration(normalized)) {
                Long current = effects.get(normalized);
                if (current != null && current > now) {
                    newExpiry = Math.min(maxExpiry, current + durationSeconds * 1000L);
                }
            }
            effects.put(normalized, newExpiry);

            String command = nutrientSection.getString("command-on-consume");
            if (command != null && !command.isBlank()) {
                String cmd = command.replace("{villager}", villager.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }
    }

    public double getXpGainMultiplier(CustomVillager villager) {
        return getNutrientEffectMultiplier(villager, "xp-gain");
    }

    public double getBuildSpeedMultiplier(CustomVillager villager) {
        return getNutrientEffectMultiplier(villager, "build-speed");
    }

    public double getRelationshipMultiplier(CustomVillager villager) {
        double relationship = getNutrientEffectMultiplier(villager, "relationship");
        double communication = getNutrientEffectMultiplier(villager, "communication");
        return (relationship + communication) / 2.0;
    }

    public double getNutrientEffectMultiplier(CustomVillager villager, String effectKey) {
        ConfigurationSection nutrients = configManager.getVillagerNutrientsSection();
        if (nutrients == null) {
            return 1.0;
        }

        Map<String, Long> effects = activeEffects.get(villager.getId());
        if (effects == null || effects.isEmpty()) {
            return 1.0;
        }

        double bonus = 0.0;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : effects.entrySet()) {
            if (entry.getValue() <= now) {
                continue;
            }
            ConfigurationSection section = nutrients.getConfigurationSection(entry.getKey());
            if (section == null) {
                continue;
            }
            bonus += section.getDouble("effects." + effectKey, 0.0);
        }
        return 1.0 + bonus;
    }

    private void refreshActiveEffects(CustomVillager villager) {
        Map<String, Long> effects = activeEffects.get(villager.getId());
        if (effects == null) {
            return;
        }
        long now = System.currentTimeMillis();
        effects.entrySet().removeIf(e -> e.getValue() <= now);
        if (effects.isEmpty()) {
            activeEffects.remove(villager.getId());
        }
    }

    // ===================== DAMAGE & WARNINGS =====================

    private void applyHungerDamage(Village village, CustomVillager villager) {
        if (!isHungerDamaging(villager)) {
            damageTickCounters.remove(villager.getId());
            return;
        }

        int interval = configManager.getVillagerHungerDamageIntervalTicks();
        int counter = damageTickCounters.merge(villager.getId(), 20, Integer::sum);
        if (counter < interval) {
            return;
        }
        damageTickCounters.put(villager.getId(), 0);

        double damage = configManager.getVillagerHungerDamagePerInterval();
        villager.adjustMorale(-damage * 2);
        villager.setNeedValue(VillagerNeed.HAPPINESS,
                Math.max(0, villager.getNeedValue(VillagerNeed.HAPPINESS) - damage));

        if (configManager.isHungerEntityDamageEnabled()) {
            applyEntityHungerDamage(villager);
        }

        if (villager.getNutrientLevels().isEmpty()) {
            return;
        }
        for (String key : new ArrayList<>(villager.getNutrientLevels().keySet())) {
            villager.setNutrientLevel(key, villager.getNutrientLevel(key) - damage);
        }
    }

    private void maybeWarnVillage(Village village, CustomVillager villager) {
        if (!configManager.isHungerWarningsEnabled()) {
            return;
        }
        double threshold = configManager.getHungerWarningThreshold();
        if (villager.getNeedValue(VillagerNeed.HUNGER) > threshold) {
            return;
        }

        long cooldownMs = configManager.getHungerWarningCooldownSeconds() * 1000L;
        long now = System.currentTimeMillis();
        Long last = lastWarningTime.get(villager.getId());
        if (last != null && now - last < cooldownMs) {
            return;
        }
        lastWarningTime.put(villager.getId(), now);

        String msg = configManager.getPrefix() + "§e" + villager.getName()
                + " §7hat nur noch §c" + String.format("%.0f", villager.getNeedValue(VillagerNeed.HUNGER))
                + "% §7Hunger!";
        for (UUID memberId : village.getMembers().keySet()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null && p.isOnline()) {
                p.sendMessage(msg);
            }
        }
    }

    // ===================== INIT & CAPACITY =====================

    public void ensureNutrientsInitialized(CustomVillager villager) {
        if (!villager.getNutrientLevels().isEmpty()) {
            return;
        }
        ConfigurationSection nutrients = configManager.getVillagerNutrientsSection();
        if (nutrients == null || nutrients.getKeys(false).isEmpty()) {
            return;
        }
        double capacity = configManager.getVillagerNutrientStorageCapacity();
        for (String nutrientKey : nutrients.getKeys(false)) {
            villager.setNutrientCapacity(nutrientKey, capacity);
            villager.setNutrientLevel(nutrientKey, capacity);
        }
    }

    public void applyNutrientCapacityFromUpgrades(Village village, CustomVillager villager) {
        if (village == null || villager.getNutrientLevels().isEmpty()) {
            return;
        }
        int level = village.getUpgradeLevel(configManager.getNutrientCapacityUpgradeKey());
        double bonus = level * configManager.getNutrientCapacityPerUpgradeLevel();
        double base = configManager.getVillagerNutrientStorageCapacity();
        double capacity = base + bonus;

        for (String key : villager.getNutrientLevels().keySet()) {
            double current = villager.getNutrientLevel(key);
            double oldCap = villager.getNutrientCapacity(key);
            villager.setNutrientCapacity(key, capacity);
            if (oldCap > 0 && capacity > oldCap) {
                villager.setNutrientLevel(key, current * (capacity / oldCap));
            }
        }
    }

    public double resolveNutrientCapacity(CustomVillager villager) {
        if (!villager.getNutrientCapacities().isEmpty()) {
            return villager.getNutrientCapacities().values().iterator().next();
        }
        return configManager.getVillagerNutrientStorageCapacity();
    }

    public String getNutrientDisplayName(String nutrientKey) {
        ConfigurationSection section = configManager.getVillagerNutrientsSection();
        if (section != null) {
            ConfigurationSection nutrient = section.getConfigurationSection(nutrientKey);
            if (nutrient != null && nutrient.contains("display-name")) {
                return nutrient.getString("display-name", nutrientKey);
            }
        }
        return nutrientKey.substring(0, 1).toUpperCase(Locale.ROOT) + nutrientKey.substring(1);
    }

    public String formatNutrientBar(double percent) {
        int filled = (int) Math.round(percent / 10.0);
        String color = percent >= 60 ? "§a" : percent >= 30 ? "§e" : "§c";
        return color + "█".repeat(Math.max(0, filled)) + "§8" + "░".repeat(Math.max(0, 10 - filled))
                + " §7" + String.format("%.0f", percent) + "%";
    }

    private void applyEntityHungerDamage(CustomVillager villager) {
        double amount = configManager.getHungerEntityDamageAmount();
        CitizensHook citizensHook = plugin.getCitizensHook();
        if (citizensHook != null && villager.getNpcId() >= 0) {
            org.bukkit.entity.Entity entity = citizensHook.getNpcEntity(villager.getNpcId());
            if (entity instanceof LivingEntity living) {
                living.damage(amount);
                return;
            }
        }
        if (villager.getVanillaEntityId() != null) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(villager.getVanillaEntityId());
            if (entity instanceof LivingEntity living) {
                living.damage(amount);
            }
        }
    }

    private static final class PendingRecovery {
        private final String nutrientKey;
        private double remainingAmount;
        private final long endTimeMs;

        PendingRecovery(String nutrientKey, double amount, long endTimeMs) {
            this.nutrientKey = nutrientKey;
            this.remainingAmount = amount;
            this.endTimeMs = endTimeMs;
        }

        String nutrientKey() { return nutrientKey; }
        double remainingAmount() { return remainingAmount; }
        long endTimeMs() { return endTimeMs; }

        void consume(double amount) {
            remainingAmount = Math.max(0, remainingAmount - amount);
        }

        int ticksRemaining(long now, int intervalSeconds) {
            long msLeft = Math.max(0, endTimeMs - now);
            return Math.max(1, (int) (msLeft / (intervalSeconds * 1000L)));
        }
    }
}
