package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Score;

public final class PlayerNutritionService {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final Map<UUID, PlayerNutritionState> states = new LinkedHashMap<>();
    private int taskId = -1;
    private final Set<UUID> scoreboardVisible = new HashSet<>();
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final Map<UUID, Integer> refreshIntervalSeconds = new HashMap<>();
    private final Map<UUID, Long> lastRefreshMillis = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedHotbars = new HashMap<>();
    private final Set<UUID> hotbarVisible = new HashSet<>();

    public PlayerNutritionService(VillagePlugin plugin, VillageConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void start() {
        if (taskId != -1) return;
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("player-nutrition.enabled", false);
    }

    public boolean isEnabledFor(Player player) {
        if (!isEnabled() || player == null) return false;
        String permissionNode = plugin.getConfig().getString("player-nutrition.permission-node", "village.player.eating.%s");
        String permissionSuffix = plugin.getConfig().getString("player-nutrition.permission-suffix", "default");
        return player.hasPermission(String.format(Locale.ROOT, permissionNode, permissionSuffix));
    }

    public void ensureState(Player player) {
        if (!isEnabledFor(player)) return;
        PlayerNutritionState state = states.computeIfAbsent(player.getUniqueId(), id -> new PlayerNutritionState());
        if (!state.initialized) {
            initializeState(state, player);
        }
    }

    public void initializeState(Player player) {
        if (!isEnabledFor(player)) return;
        PlayerNutritionState state = states.computeIfAbsent(player.getUniqueId(), id -> new PlayerNutritionState());
        initializeState(state, player);
    }

    private void initializeState(PlayerNutritionState state, Player player) {
        ConfigurationSection nutrients = configManager.getVillagerNutrientsSection();
        if (nutrients == null || nutrients.getKeys(false).isEmpty()) {
            return;
        }
        int capacity = configManager.getVillagerNutrientStorageCapacity();
        for (String nutrientKey : nutrients.getKeys(false)) {
            String key = nutrientKey.toLowerCase(Locale.ROOT);
            state.nutrientLevels.put(key, (double) capacity);
            state.capacity.put(key, (double) capacity);
        }
        state.initialized = true;
        updateFoodLevel(player);
    }

    public Map<String, Double> getNutrientPercentages(Player player) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (player == null) return result;
        ensureState(player);
        PlayerNutritionState state = states.get(player.getUniqueId());
        if (state == null || !state.initialized) return result;
        for (Map.Entry<String, Double> entry : state.nutrientLevels.entrySet()) {
            double capacity = state.capacity.getOrDefault(entry.getKey(), (double) configManager.getVillagerNutrientStorageCapacity());
            double percent = capacity > 0 ? (entry.getValue() / capacity) * 100.0 : 0.0;
            result.put(entry.getKey(), Math.max(0.0, Math.min(100.0, percent)));
        }
        return result;
    }

    public Map<String, Object> getNutrientDetail(Player player, String nutrientKey) {
        if (player == null || nutrientKey == null) return null;
        PlayerNutritionState state = states.get(player.getUniqueId());
        if (state == null || !state.initialized) return null;

        Map<String, Object> detail = new LinkedHashMap<>();
        double curr = state.nutrientLevels.getOrDefault(nutrientKey, 0.0);
        double cap = state.capacity.getOrDefault(nutrientKey, (double) configManager.getVillagerNutrientStorageCapacity());
        double percent = cap > 0 ? (curr / cap) * 100.0 : 0.0;
        long now = System.currentTimeMillis();
        boolean boosted = state.activeEffects.containsKey(nutrientKey.toLowerCase(Locale.ROOT))
                && state.activeEffects.get(nutrientKey.toLowerCase(Locale.ROOT)) > now;

        detail.put("current", curr);
        detail.put("capacity", cap);
        detail.put("percent", percent);
        detail.put("boosted", boosted);

        return detail;
    }

    public boolean togglePlayerScoreboard(Player player) {
        if (player == null) return false;
        UUID id = player.getUniqueId();
        boolean enabled = !scoreboardVisible.contains(id);
        if (enabled) {
            scoreboardVisible.add(id);
            createEmptyScoreboardFor(player);
            updatePlayerScoreboardIfVisible(player);
        } else {
            scoreboardVisible.remove(id);
            // restore default scoreboard
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            playerScoreboards.remove(id);
        }
        return enabled;
    }

    public boolean isScoreboardVisible(Player player) {
        if (player == null) return false;
        return scoreboardVisible.contains(player.getUniqueId());
    }

    private void createEmptyScoreboardFor(Player player) {
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;
        Scoreboard board = mgr.getNewScoreboard();
        playerScoreboards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
    }

    public void updatePlayerScoreboardIfVisible(Player player) {
        if (player == null) return;
        if (!isScoreboardVisible(player)) return;
        updatePlayerScoreboard(player);
        lastRefreshMillis.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void setPlayerRefreshInterval(Player player, int seconds) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        if (seconds <= 0) {
            refreshIntervalSeconds.put(id, 0);
            lastRefreshMillis.put(id, 0L);
        } else {
            refreshIntervalSeconds.put(id, seconds);
            lastRefreshMillis.put(id, System.currentTimeMillis());
        }
    }

    // Hotbar visualization
    public boolean isHotbarVisible(Player player) {
        if (player == null) return false;
        return hotbarVisible.contains(player.getUniqueId());
    }

    public void togglePlayerHotbar(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        if (hotbarVisible.contains(id)) {
            disableHotbar(player);
        } else {
            enableHotbar(player);
        }
    }

    private void enableHotbar(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        if (hotbarVisible.contains(id)) return;
        // save current hotbar (slots 0-8)
        ItemStack[] save = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack it = player.getInventory().getItem(i);
            save[i] = (it == null) ? null : it.clone();
        }
        savedHotbars.put(id, save);
        hotbarVisible.add(id);
        updatePlayerHotbar(player);
    }

    private void disableHotbar(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        if (!hotbarVisible.contains(id)) return;
        ItemStack[] save = savedHotbars.remove(id);
        if (save != null) {
            for (int i = 0; i < 9; i++) {
                player.getInventory().setItem(i, save[i]);
            }
        }
        hotbarVisible.remove(id);
        player.updateInventory();
    }

    private void updatePlayerHotbar(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        if (!hotbarVisible.contains(id)) return;

        PlayerNutritionState state = states.get(id);
        Map<String, Double> percents = getNutrientPercentages(player);
        // build list of nutrient keys in insertion order
        java.util.List<String> keys = new java.util.ArrayList<>(percents.keySet());

        // Prepare 9 slots: slots 0-7 for nutrients, slot 8 for effects/overflow
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item;
            if (slot < 8 && slot < keys.size()) {
                String key = keys.get(slot);
                double percent = percents.getOrDefault(key, 0.0);
                double curr = (state != null) ? state.nutrientLevels.getOrDefault(key, 0.0) : 0.0;
                double cap = (state != null) ? state.capacity.getOrDefault(key, (double) configManager.getVillagerNutrientStorageCapacity()) : (double) configManager.getVillagerNutrientStorageCapacity();
                int amount = Math.max(1, Math.min(64, (int) Math.round(percent * 64.0 / 100.0)));
                item = new ItemStack(Material.PAPER, amount);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§e" + capitalize(key));
                    java.util.List<String> lore = new java.util.ArrayList<>();
                    lore.add("§7" + String.format("%d / %d (%.0f%%)", (int) Math.round(curr), (int) Math.round(cap), percent));
                    lore.add("§7Hotbar: Scroll zum Anzeigen");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            } else {
                // effects / overflow slot
                item = new ItemStack(Material.FEATHER, 1);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§6Effekte");
                    meta.setLore(java.util.List.of("§7Keine aktiven Effekte"));
                    item.setItemMeta(meta);
                }
            }
            player.getInventory().setItem(slot, item);
        }
        player.updateInventory();
    }

    public int getPlayerRefreshInterval(Player player) {
        if (player == null) return 0;
        return refreshIntervalSeconds.getOrDefault(player.getUniqueId(), plugin.getConfig().getInt("player-nutrition.scoreboard-refresh-default-seconds", 30));
    }

    public int getDefaultRefreshIntervalSeconds() {
        return plugin.getConfig().getInt("player-nutrition.scoreboard-refresh-default-seconds", 30);
    }

    public void clearPlayerRefreshInterval(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        refreshIntervalSeconds.remove(id);
        lastRefreshMillis.remove(id);
    }

    private void updatePlayerScoreboard(Player player) {
        Scoreboard board = playerScoreboards.get(player.getUniqueId());
        if (board == null) {
            createEmptyScoreboardFor(player);
            board = playerScoreboards.get(player.getUniqueId());
            if (board == null) return;
        }

        // Clear old objectives
        for (Objective obj : board.getObjectives()) {
            obj.unregister();
        }

        Objective obj = board.registerNewObjective("village_hunger", "dummy", "§6§lNährwerte");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        PlayerNutritionState state = states.get(player.getUniqueId());
        Map<String, Double> percents = getNutrientPercentages(player);
        if (percents.isEmpty() || state == null) {
            String label = "Hunger: " + String.format("%.0f%%", getHungerPercent(player));
            Score s = obj.getScore(label);
            s.setScore(1);
        } else {
            for (Map.Entry<String, Double> entry : percents.entrySet()) {
                String key = entry.getKey();
                double percent = entry.getValue();
                double curr = 0.0;
                double cap = (double) configManager.getVillagerNutrientStorageCapacity();
                curr = state.nutrientLevels.getOrDefault(key, 0.0);
                cap = state.capacity.getOrDefault(key, (double) configManager.getVillagerNutrientStorageCapacity());
                String label = String.format("%s (%d/%d - %.0f%%)", capitalize(key), (int) Math.round(curr), (int) Math.round(cap), percent);
                if (label.length() > 40) {
                    label = label.substring(0, 37) + "...";
                }
                Score s = obj.getScore(label);
                s.setScore(1);
            }
        }

        List<String> activeEffectLines = getActiveEffectLabels(player);
        Score effectsHeader = obj.getScore("--- Effekte ---");
        effectsHeader.setScore(1);
        if (activeEffectLines.isEmpty()) {
            Score info = obj.getScore("Keine aktiven Effekte");
            info.setScore(1);
        } else {
            int idx = 1;
            for (String line : activeEffectLines) {
                if (line.length() > 40) {
                    line = line.substring(0, 37) + "...";
                }
                Score info = obj.getScore(line);
                info.setScore(idx++);
            }
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }

    public void applyFood(Player player, Material foodMaterial) {
        if (!isEnabledFor(player)) return;
        ensureState(player);
        PlayerNutritionState state = states.get(player.getUniqueId());
        if (state == null || !state.initialized) return;

        Map<String, Map<String, Double>> recoveryMap = configManager.getVillagerFoodRecoveryByItem();
        Map<String, Double> recovery = recoveryMap.get(foodMaterial.name().toUpperCase(Locale.ROOT));
        if (recovery == null || recovery.isEmpty()) return;

        for (Map.Entry<String, Double> entry : recovery.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if ("hunger".equals(key)) continue;
            double current = state.nutrientLevels.getOrDefault(key, 0.0);
            double capacity = state.capacity.getOrDefault(key, (double) configManager.getVillagerNutrientStorageCapacity());
            state.nutrientLevels.put(key, Math.max(0.0, Math.min(capacity, current + entry.getValue())));
        }

        applyFoodEffects(player, recovery);
        updateFoodLevel(player);
    }

    public double getHungerPercent(Player player) {
        PlayerNutritionState state = states.get(player.getUniqueId());
        if (state == null || !state.initialized || state.nutrientLevels.isEmpty()) {
            return player.getFoodLevel() * 100.0 / 20.0;
        }
        double sum = 0.0;
        int count = 0;
        for (Map.Entry<String, Double> entry : state.nutrientLevels.entrySet()) {
            double capacity = state.capacity.getOrDefault(entry.getKey(), (double) configManager.getVillagerNutrientStorageCapacity());
            double percent = capacity > 0 ? (entry.getValue() / capacity) * 100.0 : 0.0;
            sum += percent;
            count++;
        }
        return count == 0 ? 100.0 : Math.max(0.0, Math.min(100.0, sum / count));
    }

    private void tick() {
        if (!isEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isEnabledFor(player)) continue;
            ensureState(player);
            PlayerNutritionState state = states.get(player.getUniqueId());
            if (state == null || !state.initialized) continue;

            refreshActiveEffects(player);

            double decayPerMinute = plugin.getConfig().getDouble("player-nutrition.decay-per-minute", 1.0);
            double decayPerSecond = decayPerMinute / 60.0;
            for (Map.Entry<String, Double> entry : new LinkedHashMap<>(state.nutrientLevels).entrySet()) {
                double capacity = state.capacity.getOrDefault(entry.getKey(), (double) configManager.getVillagerNutrientStorageCapacity());
                double decay = decayPerSecond * capacity / 100.0;
                state.nutrientLevels.put(entry.getKey(), Math.max(0.0, entry.getValue() - decay));
            }

            double hunger = getHungerPercent(player);
            updateFoodLevel(player, hunger);

            // Auto-refresh scoreboard per-player according to configured interval
            if (isScoreboardVisible(player)) {
                UUID id = player.getUniqueId();
                int interval = refreshIntervalSeconds.getOrDefault(id,
                        plugin.getConfig().getInt("player-nutrition.scoreboard-refresh-default-seconds", 30));
                if (interval > 0) {
                    long last = lastRefreshMillis.getOrDefault(id, 0L);
                    long now = System.currentTimeMillis();
                    if (now - last >= interval * 1000L) {
                        updatePlayerScoreboardIfVisible(player);
                    }
                }
            }

            int damageThreshold = plugin.getConfig().getInt("player-nutrition.damage-threshold", 15);
            int damageIntervalTicks = plugin.getConfig().getInt("player-nutrition.damage-interval-ticks", 400);
            double damagePerInterval = plugin.getConfig().getDouble("player-nutrition.damage-per-interval", 0.5);
            if (hunger < damageThreshold) {
                long now = System.currentTimeMillis();
                long last = state.lastDamageTime;
                if (now - last >= damageIntervalTicks * 50L) {
                    player.damage(damagePerInterval);
                    state.lastDamageTime = now;
                }
            }
            // Hotbar visualization update (once per tick for visible players)
            if (hotbarVisible.contains(player.getUniqueId())) {
                updatePlayerHotbar(player);
            }
        }
    }

    private void updateFoodLevel(Player player) {
        updateFoodLevel(player, getHungerPercent(player));
    }

    private void updateFoodLevel(Player player, double hunger) {
        int food = (int) Math.round(Math.max(0.0, Math.min(20.0, hunger / 5.0)));
        player.setFoodLevel(food);
        player.setSaturation(Math.min(5.0f, (float) (hunger / 20.0)));
    }

    public List<String> getActiveEffectLabels(Player player) {
        if (player == null) return List.of();
        PlayerNutritionState state = states.get(player.getUniqueId());
        if (state == null || !state.initialized) return List.of();
        refreshActiveEffects(player);

        ConfigurationSection nutrients = configManager.getVillagerNutrientsSection();
        if (nutrients == null || nutrients.getKeys(false).isEmpty()) return List.of();

        List<String> effects = new ArrayList<>();
        for (Map.Entry<String, Long> entry : new ArrayList<>(state.activeEffects.entrySet())) {
            String nutrientKey = entry.getKey();
            long expiry = entry.getValue();
            if (expiry <= System.currentTimeMillis()) {
                state.activeEffects.remove(nutrientKey);
                continue;
            }

            ConfigurationSection nutrientSection = nutrients.getConfigurationSection(nutrientKey);
            if (nutrientSection == null || !nutrientSection.contains("effects")) {
                continue;
            }
            ConfigurationSection effectSection = nutrientSection.getConfigurationSection("effects");
            if (effectSection == null) continue;

            for (String effectKey : effectSection.getKeys(false)) {
                double effectValue = effectSection.getDouble(effectKey, 0.0);
                if (effectValue == 0.0) continue;
                long secondsLeft = Math.max(1L, (expiry - System.currentTimeMillis()) / 1000L);
                effects.add(String.format("%s %s (%ds)",
                        formatEffectValue(effectValue),
                        describeEffectKey(effectKey),
                        secondsLeft));
            }
        }
        return effects;
    }

    private void applyFoodEffects(Player player, Map<String, Double> recovery) {
        if (player == null || recovery == null || recovery.isEmpty()) return;
        PlayerNutritionState state = states.get(player.getUniqueId());
        if (state == null || !state.initialized) return;

        ConfigurationSection nutrients = configManager.getVillagerNutrientsSection();
        if (nutrients == null) return;

        long now = System.currentTimeMillis();
        for (String nutrientKey : recovery.keySet()) {
            String normalizedKey = nutrientKey.toLowerCase(Locale.ROOT);
            if ("hunger".equals(normalizedKey)) continue;
            ConfigurationSection nutrientSection = nutrients.getConfigurationSection(normalizedKey);
            if (nutrientSection == null) continue;
            if (!nutrientSection.contains("effects")) continue;

            int durationSeconds = nutrientSection.getInt("duration-seconds", 0);
            if (durationSeconds <= 0) continue;
            int maxDurationSeconds = Math.max(durationSeconds, nutrientSection.getInt("max-duration-seconds", durationSeconds));
            boolean stackDuration = nutrientSection.getBoolean("stack-duration", false);

            long expiry = now + durationSeconds * 1000L;
            Long currentExpiry = state.activeEffects.get(normalizedKey);
            if (currentExpiry != null && currentExpiry > now && stackDuration) {
                expiry = Math.min(now + maxDurationSeconds * 1000L, currentExpiry + durationSeconds * 1000L);
            }
            state.activeEffects.put(normalizedKey, expiry);
        }
    }

    private void refreshActiveEffects(Player player) {
        if (player == null) return;
        PlayerNutritionState state = states.get(player.getUniqueId());
        if (state == null || state.activeEffects.isEmpty()) return;
        long now = System.currentTimeMillis();
        state.activeEffects.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private String formatEffectValue(double value) {
        if (value >= 0.0) {
            return String.format("+%.0f%%", value * 100.0);
        }
        return String.format("%.0f%%", value * 100.0);
    }

    private String describeEffectKey(String effectKey) {
        return switch (effectKey.toLowerCase(Locale.ROOT)) {
            case "production-speed" -> "Produktionsgeschwindigkeit";
            case "movement-speed" -> "Bewegung";
            case "xp-gain" -> "XP-Gewinn";
            case "build-speed" -> "Baugeschwindigkeit";
            case "relationship" -> "Beziehungsbonus";
            case "communication" -> "Kommunikation";
            default -> effectKey;
        };
    }

    private static final class PlayerNutritionState {
        private final Map<String, Double> nutrientLevels = new LinkedHashMap<>();
        private final Map<String, Double> capacity = new LinkedHashMap<>();
        private final Map<String, Long> activeEffects = new LinkedHashMap<>();
        private boolean initialized = false;
        private long lastDamageTime = 0L;
    }
}
