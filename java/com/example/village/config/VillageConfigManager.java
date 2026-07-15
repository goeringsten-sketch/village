package com.example.village.config;

import com.example.village.VillagePlugin;
import com.example.village.model.CustomVillager;
import com.example.village.model.UpgradeType;
import com.example.village.model.Village;
import com.example.village.model.VillagerActivity;
import com.example.village.model.VillagerProfession;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VillageConfigManager {

    private final VillagePlugin plugin;
    private FileConfiguration config;
    private FileConfiguration villageConfig;
    private FileConfiguration playersConfig;
    private FileConfiguration buildingsConfig;
    private FileConfiguration villagersConfig;
    private FileConfiguration languageConfig;
    private FileConfiguration questsConfig;
    private File villageConfigFile;
    private String languageCode = "de";

    private final Map<String, UpgradeType> upgradeTypes = new HashMap<>();
    private final Map<String, VillagerProfession> professions = new HashMap<>();

    public VillageConfigManager(VillagePlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        // Ensure configs are up-to-date before loading
        plugin.reloadConfig();
        checkAndMigrateConfigs();
        config = plugin.getConfig();
        languageCode = config.getString("language", "de");
        villageConfig = loadSubConfig("config/village.yml");
        villageConfigFile = new File(plugin.getDataFolder(), "config/village.yml");
        playersConfig = loadSubConfig("config/players.yml");
        buildingsConfig = loadSubConfig("config/buildings.yml");
        villagersConfig = loadSubConfig("config/villagers.yml");
        questsConfig = loadSubConfig("config/quests-and-villagers.yml");
        languageConfig = loadLanguageConfig(languageCode);

        loadUpgradeTypes();
        loadProfessions();
    }

    private static final int CURRENT_CONFIG_VERSION = 1;

    private void checkAndMigrateConfigs() {
        String[] paths = new String[]{"config.yml", "config/village.yml", "config/buildings.yml", "config/players.yml", "config/villagers.yml", "config/currencies.yml", "config/light-limits.yml", "config/quests-and-villagers.yml"};

        if (shouldBackupConfigs()) {
            backupConfigs(paths);
        }

        for (String rel : paths) {
            File f = new File(plugin.getDataFolder(), rel);
            if (!f.exists()) {
                if (plugin.shouldUseBundledResources()) {
                    try {
                        plugin.saveResource(rel, false);
                        plugin.getLogger().info("Konfiguration " + rel + " aus Default-Ressource erstellt.");
                    } catch (IllegalArgumentException ignored) {
                        plugin.getLogger().fine("Keine Default-Ressource fuer " + rel + " gefunden.");
                    }
                }
                continue;
            }

            int fileVersion = 0;
            try {
                FileConfiguration fc = YamlConfiguration.loadConfiguration(f);
                fileVersion = fc.getInt("config-version", 0);
            } catch (Exception e) {
                plugin.getLogger().warning("Fehler beim Einlesen von " + rel + ": " + e.getMessage());
            }

            if (fileVersion < CURRENT_CONFIG_VERSION) {
                ensureConfigVersion(f, CURRENT_CONFIG_VERSION);
                plugin.getLogger().info("Konfiguration " + rel + " auf Version " + CURRENT_CONFIG_VERSION + " aktualisiert.");
            }
        }
    }

    private boolean shouldBackupConfigs() {
        DebugConfigManager debugConfigManager = plugin.getDebugConfigManager();
        return debugConfigManager != null && debugConfigManager.isBackupConfigsEnabled();
    }

    private void backupConfigs(String[] paths) {
        File backupRoot = new File(plugin.getDataFolder(), "config_backups");
        String ts = String.valueOf(System.currentTimeMillis());
        File thisBackup = new File(backupRoot, ts);
        if (!thisBackup.exists() && !thisBackup.mkdirs()) {
            plugin.getLogger().warning("Konnte Backup-Verzeichnis nicht erstellen: " + thisBackup.getAbsolutePath());
            return;
        }

        for (String rel : paths) {
            File source = new File(plugin.getDataFolder(), rel);
            if (!source.exists()) {
                continue;
            }
            File target = new File(thisBackup, rel.replace('/', '_'));
            try {
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Backup erstellt: " + target.getAbsolutePath());
            } catch (Exception e) {
                plugin.getLogger().warning("Fehler beim Erstellen des Backups fuer " + rel + ": " + e.getMessage());
            }
        }
    }

    private void ensureConfigVersion(File file, int version) {
        try {
            FileConfiguration fc = YamlConfiguration.loadConfiguration(file);
            if (fc.getInt("config-version", 0) < version) {
                fc.set("config-version", version);
                fc.save(file);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Aktualisieren der Config-Version fuer " + file.getName() + ": " + e.getMessage());
        }
    }

    private FileConfiguration loadSubConfig(String relativePath) {
        File file = new File(plugin.getDataFolder(), relativePath);
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }

        if (plugin.shouldUseBundledResources()) {
            try {
                plugin.saveResource(relativePath, false);
            } catch (IllegalArgumentException ignored) {
                // no bundled resource found -> fallback to empty config
            }
            if (file.exists()) {
                return YamlConfiguration.loadConfiguration(file);
            }
        }

        FileConfiguration bundled = loadBundledConfig(relativePath);
        return bundled != null ? bundled : new YamlConfiguration();
    }

    private FileConfiguration loadBundledConfig(String relativePath) {
        try (InputStream input = plugin.getResource(relativePath)) {
            if (input == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Laden der Default-Config " + relativePath + ": " + e.getMessage());
            return null;
        }
    }

    private FileConfiguration cfgForRoot(String root) {
        return switch (root) {
            case "founding", "territory", "levels", "upgrades" -> villageConfig;
            case "role-upgrades" -> playersConfig;
            case "buildings" -> buildingsConfig;
            case "villagers" -> villagersConfig;
            default -> config;
        };
    }

    private String rootOf(String path) {
        int i = path.indexOf('.');
        return i < 0 ? path : path.substring(0, i);
    }

    private int getInt(String path, int def) {
        FileConfiguration primary = cfgForRoot(rootOf(path));
        if (primary != null && primary.contains(path)) return primary.getInt(path, def);
        return config.getInt(path, def);
    }

    private double getDouble(String path, double def) {
        FileConfiguration primary = cfgForRoot(rootOf(path));
        if (primary != null && primary.contains(path)) return primary.getDouble(path, def);
        return config.getDouble(path, def);
    }

    private boolean getBoolean(String path, boolean def) {
        FileConfiguration primary = cfgForRoot(rootOf(path));
        if (primary != null && primary.contains(path)) return primary.getBoolean(path, def);
        return config.getBoolean(path, def);
    }

    private String getString(String path, String def) {
        FileConfiguration primary = cfgForRoot(rootOf(path));
        if (primary != null && primary.contains(path)) return primary.getString(path, def);
        return config.getString(path, def);
    }

    private List<Map<?, ?>> getMapList(String path) {
        FileConfiguration primary = cfgForRoot(rootOf(path));
        if (primary != null && primary.contains(path)) return primary.getMapList(path);
        return config.getMapList(path);
    }

    private List<?> getList(String path) {
        FileConfiguration primary = cfgForRoot(rootOf(path));
        if (primary != null && primary.contains(path)) return primary.getList(path);
        return config.getList(path);
    }

    private double getDualAmount(ConfigurationSection section, String keyPrefix, String branch, double fallback) {
        if (section == null) return fallback;
        String nested = keyPrefix + "." + branch;
        if (section.contains(nested)) return section.getDouble(nested, fallback);
        if (section.contains(keyPrefix)) return section.getDouble(keyPrefix, fallback);
        return fallback;
    }

    private ConfigurationSection getSection(String path) {
        FileConfiguration primary = cfgForRoot(rootOf(path));
        ConfigurationSection section = primary != null ? primary.getConfigurationSection(path) : null;
        return section != null ? section : config.getConfigurationSection(path);
    }


    private void loadUpgradeTypes() {
        upgradeTypes.clear();
        ConfigurationSection section = getSection("upgrades");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) continue;
            Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
            if (icon == null) icon = Material.PAPER;
            upgradeTypes.put(key, new UpgradeType(
                    key,
                    s.getString("display-name", key),
                    s.getString("description", ""),
                    icon,
                    s.getInt("max-level", 10),
                    getDualAmount(s, "cost-per-level", "global", 100),
                    getDualAmount(s, "cost-per-level", "local", 0),
                    s.getInt("points-per-level", 50),
                    s.getInt("required-village-level", 1)
            ));
        }
    }

    private void loadProfessions() {
        professions.clear();
        ConfigurationSection section = getSection("villagers.professions");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) continue;
            Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
            if (icon == null) icon = Material.PAPER;
            List<Material> produces = new ArrayList<>();
            for (String matName : s.getStringList("produces")) {
                Material m = Material.matchMaterial(matName);
                if (m != null) produces.add(m);
            }

            List<VillagerActivity> activitySequence = new ArrayList<>();
            ConfigurationSection activitiesSection = s.getConfigurationSection("activity-sequence");
            if (activitiesSection != null) {
                for (String activityKey : activitiesSection.getKeys(false)) {
                    ConfigurationSection act = activitiesSection.getConfigurationSection(activityKey);
                    if (act == null) continue;
                    VillagerActivity.ActivityType type;
                    try {
                        type = VillagerActivity.ActivityType.valueOf(act.getString("type", "IDLE").toUpperCase());
                    } catch (IllegalArgumentException e) {
                        type = VillagerActivity.ActivityType.IDLE;
                    }
                    List<String> actions = new ArrayList<>();
                    if (act.contains("actions")) {
                        actions.addAll(act.getStringList("actions"));
                    } else if (act.isString("action")) {
                        actions.add(act.getString("action"));
                    }

                    activitySequence.add(new VillagerActivity(
                            type,
                            act.getString("location-key", ""),
                            act.getInt("duration-ticks", 200),
                            act.getBoolean("interruptable", true),
                            act.getString("description", ""),
                            act.getInt("delay-ticks", 0),
                            actions,
                            act.getInt("required-village-level", 0),
                            act.getString("required-upgrade", null)
                    ));
                }
            }

            professions.put(key, new VillagerProfession(
                    key,
                    s.getString("display-name", key),
                    icon,
                    produces,
                    s.getInt("production-interval-seconds", 300),
                    s.getInt("required-level", 1),
                    s.getString("skin-name", "villager"),
                    activitySequence
            ));
        }
    }

    public String message(String path) {
        String langMessage = languageConfig != null ? languageConfig.getString("messages." + path) : null;
        if (langMessage != null) return langMessage;
        return config.getString("messages." + path, path);
    }

    public String text(String path, String fallback) {
        String langText = languageConfig != null ? languageConfig.getString(path) : null;
        if (langText != null && !langText.isBlank()) {
            return langText;
        }
        String configText = config.getString(path);
        if (configText != null && !configText.isBlank()) {
            return configText;
        }
        return fallback != null ? fallback : path;
    }

    public String getPrefix() {
        String langPrefix = languageConfig != null ? languageConfig.getString("messages.prefix") : null;
        if (langPrefix != null) return langPrefix;
        return config.getString("messages.prefix", "&8[&6Dorf&8] &7");
    }

    public boolean isVillagerRevivalEnabled() {
        return getBoolean("villagers.revival.enabled", false);
    }

    public String getVillagerRevivalCurrencyType() {
        return getString("villagers.revival.currency-type", "global");
    }

    public double getVillagerRevivalBaseCost() {
        return getDouble("villagers.revival.base-cost", 0.0);
    }

    public double getVillagerRevivalCostIncreasePerUse() {
        return getDouble("villagers.revival.cost-increase-per-use", 0.0);
    }

    public double getVillagerRevivalMaxCost() {
        return getDouble("villagers.revival.max-cost", 0.0);
    }

    public long getVillagerRevivalBaseCooldownSeconds() {
        return Math.max(0L, Math.round(getDouble("villagers.revival.base-cooldown-seconds", 0.0)));
    }

    public long getVillagerRevivalCooldownIncreasePerUseSeconds() {
        return Math.max(0L, Math.round(getDouble("villagers.revival.cooldown-increase-per-use-seconds", 0.0)));
    }

    public long getVillagerRevivalMaxCooldownSeconds() {
        return Math.max(0L, Math.round(getDouble("villagers.revival.max-cooldown-seconds", 0.0)));
    }

    public int getWellMaxAirBlocks() { return getInt("founding.well.max-air-blocks", 10); }
    public String getFoundingPermission() { return getString("founding.requirements.permission", "village.create"); }
    public double getFoundingMoneyCost() { return getFoundingGlobalCost(); }
    public double getFoundingGlobalCost() { return getDouble("founding.requirements.money.global", getDouble("founding.requirements.money", 0)); }
    public double getFoundingLocalCost() { return getDouble("founding.requirements.money.local", 0); }

    public List<Map<String, Object>> getFoundingItemRequirements() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<?> items = getList("founding.requirements.items");
        if (items == null) return result;
        for (Object obj : items) {
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) obj;
                result.add(map);
            }
        }
        return result;
    }

    public String getRenamePermission() { return getString("founding.rename.permission", "village.rename"); }
    public double getRenameGlobalCost() { return getDouble("founding.rename.money.global", getDouble("founding.rename.money", 0)); }
    public double getRenameLocalCost() { return getDouble("founding.rename.money.local", 0); }

    public List<Map<String, Object>> getRenameItemRequirements() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<?> items = getList("founding.rename.items");
        if (items == null) return result;
        for (Object obj : items) {
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) obj;
                result.add(map);
            }
        }
        return result;
    }

    public String getJoinPermission() { return getString("joining.permission", "village.join"); }
    public double getJoinGlobalCost() { return getDouble("joining.money.global", getDouble("joining.money", 0)); }
    public double getJoinLocalCost() { return getDouble("joining.money.local", 0); }

    public List<Map<String, Object>> getJoinItemRequirements() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<?> items = getList("joining.items");
        if (items == null) return result;
        for (Object obj : items) {
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) obj;
                result.add(map);
            }
        }
        return result;
    }

    public int getMaxArea() { return getInt("territory.max-area", 10000); }
    public int getHeightAbove() { return getInt("territory.height-above", 64); }
    public int getHeightBelow() { return getInt("territory.height-below", 64); }

    public int getInitialSize() {
        int size = getInt("territory.initial-size", 21);
        if (size % 2 == 0) {
            size++;
            plugin.getLogger().warning("territory.initial-size war gerade (" + (size - 1) + "), wurde auf " + size + " erhoeht (muss ungerade sein).");
        }
        return size;
    }

    public int getMinSquareSize() { return getInt("territory.min-square-size", 3); }
    public int getMaxBuildRadius() { return getInt("territory.max-build-radius", 50); }

    public Material getPreviewBlock() {
        Material m = Material.matchMaterial(getString("territory.preview-block", "REDSTONE_ORE"));
        return m != null ? m : Material.REDSTONE_ORE;
    }

    public Material getPreviewSelectionBlock() {
        Material m = Material.matchMaterial(getString("territory.preview-selected-block", "EMERALD_ORE"));
        return m != null ? m : Material.EMERALD_ORE;
    }

    public Material getBuildingPreviewBorderBlock() {
        Material m = Material.matchMaterial(getString("territory.building-preview-border-block", "GOLD_ORE"));
        return m != null ? m : Material.GOLD_ORE;
    }

    public Material getBuildingBorderBlock() {
        Material m = Material.matchMaterial(getString("territory.building-border-block", "LAPIS_ORE"));
        return m != null ? m : Material.LAPIS_ORE;
    }

    public int getPreviewDuration() { return getInt("territory.preview-duration", 30); }
    public String getSelectionMethod() { return getString("territory.selection-method", "BOTH"); }
    public boolean isWalkBorderDebug() { return getBoolean("territory.debug-walk-border", false); }
    public boolean isBorderEntryDebug() { return getBoolean("territory.debug-border-entry", false); }

    public void setBorderEntryDebug(boolean enabled) {
        ensureVillageConfigFile();
        villageConfig.set("territory.debug-border-entry", enabled);
        try {
            villageConfig.save(villageConfigFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte territory.debug-border-entry nicht speichern: " + e.getMessage());
        }
    }

    private FileConfiguration loadLanguageConfig(String code) {
        String normalized = (code == null || code.isBlank()) ? "de" : code.trim().toLowerCase(Locale.ROOT);
        File file = new File(plugin.getDataFolder(), "lang/" + normalized + ".yml");
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }

        String resourcePath = "lang/" + normalized + ".yml";
        if (plugin.getResource(resourcePath) != null) {
            plugin.saveResource(resourcePath, false);
            if (file.exists()) {
                return YamlConfiguration.loadConfiguration(file);
            }
        }

        if (!"de".equals(normalized)) {
            return loadLanguageConfig("de");
        }

        return new YamlConfiguration();
    }

    public int getMaxLevel() { return getInt("levels.max-level", 50); }

    public int getPointsForLevel(int level) {
        List<Map<?, ?>> list = getMapList("levels.points-per-level");
        int lastPoints = 100;
        for (Map<?, ?> entry : list) {
            Object lvlObj = entry.get("level");
            Object ptsObj = entry.get("points");
            if (lvlObj == null || ptsObj == null) continue;
            int lvl = ((Number) lvlObj).intValue();
            int pts = ((Number) ptsObj).intValue();
            if (lvl <= level) lastPoints = pts;
        }
        return lastPoints;
    }

    public int getPointsForSource(String source) { return getInt("levels.point-sources." + source, 0); }

    // Levelup prerequisites
    public List<String> getPrerequisiteBuildings(int level) {
        ConfigurationSection prq = getSection("levels.prerequisites.level_" + level);
        if (prq == null || !prq.contains("buildings")) return new ArrayList<>();
        List<?> buildings = prq.getList("buildings");
        List<String> result = new ArrayList<>();
        if (buildings != null) {
            for (Object building : buildings) {
                if (building instanceof String str) {
                    result.add(str);
                }
            }
        }
        return result;
    }

    public int getPrerequisiteMinVillagers(int level) {
        ConfigurationSection prq = getSection("levels.prerequisites.level_" + level);
        if (prq == null) return 0;
        return prq.getInt("min-villagers", 0);
    }

    public String getPrerequisitePermission(int level) {
        ConfigurationSection prq = getSection("levels.prerequisites.level_" + level);
        if (prq == null) return null;
        return prq.getString("permission", null);
    }

    public List<String> getPrerequisiteParticles(int level) {
        ConfigurationSection prq = getSection("levels.prerequisites.level_" + level);
        if (prq == null || !prq.contains("particles")) return new ArrayList<>();

        Object particlesValue = prq.get("particles");
        List<String> result = new ArrayList<>();
        if (particlesValue instanceof String str) {
            for (String particle : str.split("\\s+")) {
                if (!particle.isBlank()) {
                    result.add(particle.trim());
                }
            }
            return result;
        }

        if (particlesValue instanceof List<?> particles) {
            for (Object particle : particles) {
                if (particle instanceof String str) {
                    result.add(str);
                }
            }
        }
        return result;
    }

    public boolean isParticleEffectEnabled() { return getBoolean("levels.particle-config.enabled", false); }
    public int getParticleCheckIntervalTicks() { return getInt("levels.particle-config.check-interval-ticks", 20); }
    public double getParticleHeightAboveBell() { return getDouble("levels.particle-config.height-above-bell", 1.5); }
    public double getParticleRadiusAroundBell() { return getDouble("levels.particle-config.radius-around-bell", 3.0); }

    // Villager Glow / Display config
    public int getVillagerGlowTempDurationTicks() { return getInt("villager.glow-config.temp-duration-ticks", 300); }

    public int getJobStorageBaseSlots(String professionKey) {
        if (professionKey == null || professionKey.isBlank()) {
            return 9;
        }
        return getInt("villagers.job-storage.base-slots." + professionKey, 9);
    }

    public int getJobStorageSlotsPerUpgradeLevel(String professionKey) {
        if (professionKey == null || professionKey.isBlank()) {
            return 9;
        }
        return getInt("villagers.job-storage.slots-per-level." + professionKey, 9);
    }

    public int getJobStorageMaxSlots(String professionKey) {
        if (professionKey == null || professionKey.isBlank()) {
            return 54;
        }
        return getInt("villagers.job-storage.max-slots." + professionKey, 54);
    }

    /**
     * Kombinierte Job-Lager-Kapazitaet (Basis + Dorf-Upgrade-Stufen, gekappt auf das Maximum).
     * Zentrale Quelle der Wahrheit, damit GUIs und Listener nicht mehr eigene Kopien
     * dieser Berechnung pflegen (und dabei auseinanderlaufen).
     */
    public int getJobStorageCapacity(Village village, CustomVillager villager) {
        String professionKey = villager != null ? villager.getProfessionKey() : null;
        int level = village != null ? Math.max(0, village.getUpgradeLevel("villager-storage")) : 0;
        int base = getJobStorageBaseSlots(professionKey);
        int perLevel = getJobStorageSlotsPerUpgradeLevel(professionKey);
        int max = getJobStorageMaxSlots(professionKey);
        return Math.min(max, base + (level * perLevel));
    }

    /**
     * Prueft, ob im Job-Lager noch Platz fuer ein (ggf. neues) Material ist.
     * Bereits vorhandene Materialtypen duerfen immer weiter aufgestockt werden,
     * nur fuer neue Materialtypen greift das Slot-Limit.
     */
    public boolean hasJobStorageCapacity(Village village, CustomVillager villager, Material material) {
        if (villager == null || material == null) return false;
        if (villager.getInventory().containsKey(material)) {
            return true;
        }
        return villager.getInventory().size() < getJobStorageCapacity(village, villager);
    }

    public Map<String, UpgradeType> getUpgradeTypes() { return upgradeTypes; }
    public UpgradeType getUpgradeType(String key) { return upgradeTypes.get(key); }
    public Map<String, VillagerProfession> getProfessions() { return professions; }
    public VillagerProfession getProfession(String key) { return professions.get(key); }

    public int getBaseMaxVillagers() { return getInt("villagers.base-max", 5); }
    public int getScheduleWakeUp() { return getInt("villagers.schedule.wake-up", 0); }
    public int getScheduleWorkStart() { return getInt("villagers.schedule.work-start", 2000); }
    public int getScheduleLunch() { return getInt("villagers.schedule.lunch", 6000); }
    public int getScheduleWorkEnd() { return getInt("villagers.schedule.work-end", 10000); }
    public int getScheduleSleep() { return getInt("villagers.schedule.sleep", 13000); }
    public double getNeedDecay(String need) { return getDouble("villagers.needs." + need + ".decay-per-minute", 1.0); }
    public double getNeedCritical(String need) { return getDouble("villagers.needs." + need + ".critical-threshold", 20.0); }
    public int getSkillTreeMaxLevel() { return getInt("villagers.skill-tree.max-skill-level", 10); }
    public int getSkillTreeXpPerLevel() { return getInt("villagers.skill-tree.xp-per-level", 100); }
    public int getVillagerNutrientStorageCapacity() { return getInt("villagers.nutrient-storage.default-capacity-per-nutrient", 100); }
    public ConfigurationSection getVillagerNutrientStorageSection() { return getSection("villagers.nutrient-storage"); }
    public int getVillagerHungerCriticalThreshold() {
        return (int) getNeedCritical("hunger");
    }
    public int getVillagerHungerDamageThreshold() {
        return getInt("villagers.needs.hunger.damage-threshold",
                getInt("villagers.hunger.damage-threshold", 15));
    }
    public int getVillagerHungerDamageIntervalTicks() {
        return getInt("villagers.needs.hunger.damage-interval-ticks",
                getInt("villagers.hunger.damage-interval-ticks", 400));
    }
    public double getVillagerHungerDamagePerInterval() {
        return getDouble("villagers.needs.hunger.damage-per-interval",
                getDouble("villagers.hunger.damage-per-interval", 0.5));
    }
    public double getNutrientDecayPerMinute(String nutrientKey) {
        ConfigurationSection section = getVillagerNutrientsSection();
        if (section != null) {
            ConfigurationSection nutrient = section.getConfigurationSection(nutrientKey);
            if (nutrient != null && nutrient.contains("decay-per-minute")) {
                return nutrient.getDouble("decay-per-minute");
            }
        }
        ConfigurationSection storage = getVillagerNutrientStorageSection();
        if (storage != null && storage.contains("decay-per-minute")) {
            return storage.getDouble("decay-per-minute");
        }
        return getNeedDecay("hunger");
    }
    public double getNutrientActivityMultiplier(String activityKey, String nutrientKey) {
        if (activityKey == null || nutrientKey == null) {
            return 1.0;
        }

        ConfigurationSection section = getSection("villagers.nutrition.activity-nutrient-multipliers." + activityKey);
        if (section != null && section.contains(nutrientKey)) {
            return section.getDouble(nutrientKey, 1.0);
        }
        return 1.0;
    }
    public double getNutrientDecayByActivity(String activityKey) {
        ConfigurationSection storage = getVillagerNutrientStorageSection();
        if (storage != null) {
            ConfigurationSection byActivity = storage.getConfigurationSection("decay-by-activity");
            if (byActivity != null && byActivity.contains(activityKey)) {
                return byActivity.getDouble(activityKey);
            }
        }
        ConfigurationSection schedule = getSection("villagers.schedule.nutrient-decay-by-activity");
        if (schedule != null && schedule.contains(activityKey)) {
            return schedule.getDouble(activityKey);
        }
        return 1.0;
    }
    public boolean isBalancedDietEnabled() {
        return getBoolean("villagers.nutrition.balanced-diet.enabled", true);
    }
    public double getBalancedDietLowNutrientThreshold() {
        return getDouble("villagers.nutrition.balanced-diet.low-nutrient-threshold", 20.0);
    }
    public double getBalancedDietProductionPenalty() {
        return getDouble("villagers.nutrition.balanced-diet.production-penalty", 0.10);
    }
    public double getBalancedDietExcessSugarThreshold() {
        return getDouble("villagers.nutrition.balanced-diet.excess-sugar-threshold", 80.0);
    }
    public double getSeasonalDecayMultiplier(long worldTime) {
        if (!getBoolean("villagers.nutrition.seasonal.enabled", true)) {
            return 1.0;
        }
        long winterStart = getInt("villagers.nutrition.seasonal.winter-start", 12000);
        long winterEnd = getInt("villagers.nutrition.seasonal.winter-end", 6000);
        boolean winter = worldTime >= winterStart || worldTime < winterEnd;
        return winter
                ? getDouble("villagers.nutrition.seasonal.winter-decay-multiplier", 1.25)
                : getDouble("villagers.nutrition.seasonal.summer-decay-multiplier", 0.9);
    }
    public double getJobDecayMultiplier(String professionKey) {
        if (professionKey == null) {
            return 1.0;
        }
        return getDouble("villagers.nutrition.job-decay-multipliers." + professionKey, 1.0);
    }
    public boolean isHungerWarningsEnabled() {
        return getBoolean("villagers.nutrition.warnings.enabled", true);
    }
    public double getHungerWarningThreshold() {
        return getDouble("villagers.nutrition.warnings.threshold", 25.0);
    }
    public int getHungerWarningCooldownSeconds() {
        return getInt("villagers.nutrition.warnings.cooldown-seconds", 300);
    }
    public String getNutrientCapacityUpgradeKey() {
        return getString("villagers.nutrition.upgrades.nutrient-capacity-level-key", "nutrient-capacity");
    }
    public double getNutrientCapacityPerUpgradeLevel() {
        return getDouble("villagers.nutrition.upgrades.capacity-per-level", 15.0);
    }
    public List<String> getBakerPriorityFoods() {
        List<?> list = getList("villagers.nutrition.baker-priority-foods");
        if (list == null || list.isEmpty()) {
            return List.of("BREAD", "CAKE", "COOKIE");
        }
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            result.add(String.valueOf(entry));
        }
        return result;
    }
    public boolean isAutoFeedFromChestEnabled() {
        return getBoolean("villagers.nutrition.auto-feed-from-chest", true);
    }
    public int getScheduleGetFood() {
        return getInt("villagers.schedule.get-food", 3000);
    }
    public Map<String, Double> getVillagerFoodRecovery() {
        Map<String, Double> values = new LinkedHashMap<>();
        ConfigurationSection section = getSection("villagers.feeding.recovery");
        if (section == null) {
            section = getSection("villagers.food.recovery");
        }
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection itemSection = section.getConfigurationSection(key);
                if (itemSection != null) {
                    values.put(key.toUpperCase(Locale.ROOT), itemSection.getDouble("hunger", 0.0));
                } else {
                    values.put(key.toUpperCase(Locale.ROOT), section.getDouble(key, 0.0));
                }
            }
        }
        return values;
    }

    public Map<String, Map<String, Double>> getVillagerFoodRecoveryByItem() {
        Map<String, Map<String, Double>> result = new LinkedHashMap<>();
        ConfigurationSection section = getSection("villagers.feeding.recovery");
        if (section == null) {
            section = getSection("villagers.food.recovery");
        }
        if (section == null) {
            return result;
        }
        for (String itemKey : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(itemKey);
            if (itemSection == null) {
                result.put(itemKey.toUpperCase(Locale.ROOT), Map.of("hunger", section.getDouble(itemKey, 0.0)));
                continue;
            }
            Map<String, Double> nutrientValues = new LinkedHashMap<>();
            for (String nutrientKey : itemSection.getKeys(false)) {
                if (isFoodRecoveryMetaKey(nutrientKey)) {
                    continue;
                }
                nutrientValues.put(nutrientKey.toLowerCase(Locale.ROOT), itemSection.getDouble(nutrientKey, 0.0));
            }
            result.put(itemKey.toUpperCase(Locale.ROOT), nutrientValues);
        }
        return result;
    }

    public static boolean isFoodRecoveryMetaKey(String key) {
        if (key == null) {
            return true;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.equals("instant-percent") || normalized.equals("instant_percent")
                || normalized.equals("recovery-seconds") || normalized.equals("recovery_duration_seconds")
                || normalized.equals("recovery-duration-seconds");
    }

    public boolean isAllowPlayerFeeding() {
        return getBoolean("villagers.feeding.allow-player-feeding", true);
    }

    public boolean isAllowSelfFeeding() {
        return getBoolean("villagers.feeding.allow-self-feeding", true);
    }

    public int getFeedIntervalSeconds() {
        return getInt("villagers.feeding.feed-interval-seconds", 5);
    }

    public double getDefaultFeedInstantPercent() {
        return getDouble("villagers.feeding.default-instant-percent", 100.0);
    }

    public double getDefaultFeedRecoverySeconds() {
        return getDouble("villagers.feeding.default-recovery-seconds", 0.0);
    }

    public boolean isPeriodicRecoveryEnabled() {
        return getBoolean("villagers.feeding.periodic-recovery.enabled", true);
    }

    public int getPeriodicRecoveryTickIntervalSeconds() {
        return getInt("villagers.feeding.periodic-recovery.tick-interval-seconds", 1);
    }

    public double getFeedInstantPercent(String itemKey) {
        ConfigurationSection section = getSection("villagers.feeding.recovery." + itemKey);
        if (section != null && section.contains("instant-percent")) {
            return section.getDouble("instant-percent");
        }
        return getDefaultFeedInstantPercent();
    }

    public double getFeedRecoverySeconds(String itemKey) {
        ConfigurationSection section = getSection("villagers.feeding.recovery." + itemKey);
        if (section != null) {
            if (section.contains("recovery-seconds")) {
                return section.getDouble("recovery-seconds");
            }
            if (section.contains("recovery-duration-seconds")) {
                return section.getDouble("recovery-duration-seconds");
            }
        }
        return getDefaultFeedRecoverySeconds();
    }

    public double getFeedHungerValue(String itemKey) {
        ConfigurationSection section = getSection("villagers.feeding.recovery." + itemKey);
        if (section != null && section.contains("hunger")) {
            return section.getDouble("hunger");
        }
        return 0.0;
    }

    public boolean isHungerEntityDamageEnabled() {
        return getBoolean("villagers.needs.hunger.entity-damage.enabled", false);
    }

    public double getHungerEntityDamageAmount() {
        return getDouble("villagers.needs.hunger.entity-damage.amount", 1.0);
    }

    public int getNutrientMaxDurationSeconds(String nutrientKey) {
        ConfigurationSection section = getVillagerNutrientsSection();
        if (section == null) {
            return 300;
        }
        ConfigurationSection nutrient = section.getConfigurationSection(nutrientKey);
        if (nutrient == null) {
            return 300;
        }
        return nutrient.getInt("max-duration-seconds", nutrient.getInt("duration-seconds", 60) * 3);
    }

    public boolean isNutrientStackDuration(String nutrientKey) {
        ConfigurationSection section = getVillagerNutrientsSection();
        if (section == null) {
            return true;
        }
        ConfigurationSection nutrient = section.getConfigurationSection(nutrientKey);
        if (nutrient == null) {
            return true;
        }
        return nutrient.getBoolean("stack-duration", true);
    }

    public boolean isActivityEndCostEnabled() {
        return getBoolean("villagers.nutrition.activity-end-cost.enabled", true);
    }

    public double getActivityEndCostMultiplier(String activityType) {
        if (activityType == null) {
            return 1.0;
        }
        return getDouble("villagers.nutrition.activity-end-cost.multipliers." + activityType.toUpperCase(Locale.ROOT), 1.0);
    }

    public ConfigurationSection getVillagerFeedingSection() { return getSection("villagers.feeding"); }
    public ConfigurationSection getVillagerNutrientsSection() { return getSection("villagers.nutrients"); }

    public int getUpgradeAreaPerLevel() { return getInt("upgrades.border-expansion.area-per-level", 2000); }
    public int getUpgradeVillagersPerLevel() { return getInt("upgrades.max-villagers.villagers-per-level", 2); }
    public double getUpgradeSpeedMultiplierPerLevel() { return getDouble("upgrades.production-speed.speed-multiplier-per-level", 0.1); }
    public double getUpgradeTaxRatePerLevel() { return getDouble("upgrades.taxes.tax-rate-per-level", 0.05); }
    public int getUpgradeRadiusPerLevel() { return getInt("upgrades.build-radius-expansion.radius-per-level", 5); }
    public int getUpgradeMembersPerLevel() { return getInt("upgrades.max-members.members-per-level", 3); }

    public ConfigurationSection getRoleUpgradesSection() {
        ConfigurationSection s = playersConfig.getConfigurationSection("role-upgrades");
        return s != null ? s : config.getConfigurationSection("role-upgrades");
    }

    /**
     * Gibt alle Voraussetzungen für ein Level als Map zurück.
     */
    public Map<String, Object> getLevelPrerequisites(int level) {
        ConfigurationSection prq = getSection("levels.prerequisites.level_" + level);
        if (prq == null) return null;
        
        Map<String, Object> result = new HashMap<>();
        for (String key : prq.getKeys(false)) {
            if (key.equals("particles")) continue; // Skip particles
            result.put(key, prq.get(key));
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Gibt die Kosten für ein Levelup zurück.
     * Standardmäßig: Dorfpunkte (keine globalen/lokalen Kosten konfiguriert)
     */
    public double getLevelupCost(int level, String currencyType) {
        // This is for future expansion - currently costs are handled via points
        // Can be extended to add global/local currency costs per level
        switch (currencyType.toLowerCase()) {
            case "global":
                return getDouble("levels.levelup-costs.level_" + level + ".global", 0.0);
            case "local":
                return getDouble("levels.levelup-costs.level_" + level + ".local", 0.0);
            default:
                return 0.0;
        }
    }

    public FileConfiguration getConfig() { return config; }

    private void ensureVillageConfigFile() {
        if (villageConfigFile == null) {
            villageConfigFile = new File(plugin.getDataFolder(), "config/village.yml");
        }
    }

    public FileConfiguration getQuestsConfig() {
        return questsConfig;
    }

    public boolean isChunkOptimizationEnabled() {
        if (questsConfig == null) return true;
        return questsConfig.getBoolean("performance.chunk-optimization", true);
    }

    public int getMaxUpdatesPerTick() {
        if (questsConfig == null) return 10;
        return questsConfig.getInt("performance.max-updates-per-tick", 10);
    }

    public int getGlobalMaxUpdatesPerTick() {
        if (questsConfig == null) return getMaxUpdatesPerTick();
        return Math.max(1, questsConfig.getInt("performance.global-max-updates-per-tick", getMaxUpdatesPerTick()));
    }

    public int getBatchUpdateInterval() {
        if (questsConfig == null) return 20;
        return questsConfig.getInt("performance.batch-update-interval", 20);
    }
}
