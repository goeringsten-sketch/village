package com.example.village.config;

import com.example.village.VillagePlugin;
import com.example.village.model.LightDistanceStep;
import com.example.village.model.LightPathSource;
import com.example.village.model.LightPointSource;
import com.example.village.model.LightRegionSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VillageLightConfigManager {

    private final VillagePlugin plugin;
    private FileConfiguration config;
    private boolean circularStages = false;
    private List<LightDistanceStep> steps = List.of();
    private Set<String> enabledWorlds = Set.of();
    private List<LightPointSource> pointSources = List.of();
    private List<LightRegionSource> regionSources = List.of();
    private List<LightPathSource> pathSources = List.of();

    public VillageLightConfigManager(VillagePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "config/light-limits.yml");
        if (!file.exists()) {
            // Fallback: Prüfe ob die Datei im alten Pfad noch existiert
            File oldFile = new File(plugin.getDataFolder(), "light-limits.yml");
            if (oldFile.exists()) {
                file = oldFile;
            }
        }

        config = YamlConfiguration.loadConfiguration(file);
        loadStageShape();
        loadSteps();
        loadWorlds();
        loadPointSources();
        loadRegionSources();
        loadPathSources();
    }

    private void loadStageShape() {
        String shape = config.getString("light-control.stage-shape", "square");
        circularStages = "circle".equalsIgnoreCase(shape) || "circular".equalsIgnoreCase(shape);
    }

    private void loadSteps() {
        List<LightDistanceStep> loaded = loadDistanceSteps(config.getConfigurationSection("light-control"), "stages");

        if (loaded.isEmpty()) {
            loaded.add(new LightDistanceStep(0, 15));
            loaded.add(new LightDistanceStep(120, 3));
        }

        loaded.sort(Comparator.comparingInt(LightDistanceStep::distance));
        steps = List.copyOf(loaded);
    }

    private void loadWorlds() {
        Set<String> worlds = new HashSet<>();
        for (String world : config.getStringList("light-control.enabled-worlds")) {
            if (world != null && !world.isBlank()) {
                worlds.add(world);
            }
        }
        enabledWorlds = Set.copyOf(worlds);
    }

    private void loadPointSources() {
        List<LightPointSource> loaded = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("light-control.points-of-interest");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection source = section.getConfigurationSection(key);
                if (source == null) continue;
                String world = source.getString("world", "");
                if (world.isBlank()) continue;
                double radius = Math.max(0, source.getDouble("radius", source.getDouble("base-radius", 0)));
                double y = source.contains("y") ? source.getDouble("y") : Double.NaN;
                List<LightDistanceStep> sourceStages = loadDistanceSteps(source, "stages");
                loaded.add(new LightPointSource(
                        key,
                        world,
                        source.getDouble("x"),
                        y,
                        source.getDouble("z"),
                        radius,
                        List.copyOf(sourceStages)
                ));
            }
        }
        pointSources = List.copyOf(loaded);
    }

    private List<LightDistanceStep> loadDistanceSteps(ConfigurationSection section, String path) {
        List<LightDistanceStep> loaded = new ArrayList<>();
        if (section == null) {
            return loaded;
        }

        for (var map : section.getMapList(path)) {
            Object distanceObj = map.get("distance");
            Object maxLightObj = map.get("max-light-level");
            if (distanceObj instanceof Number && maxLightObj instanceof Number) {
                Number distance = (Number) distanceObj;
                Number maxLight = (Number) maxLightObj;
                int clampedLight = Math.max(0, Math.min(15, maxLight.intValue()));
                loaded.add(new LightDistanceStep(Math.max(0, distance.intValue()), clampedLight));
            }
        }

        loaded.sort(Comparator.comparingInt(LightDistanceStep::distance));
        return loaded;
    }

    private void loadRegionSources() {
        List<LightRegionSource> loaded = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("light-control.worldguard-regions");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection source = section.getConfigurationSection(key);
                if (source == null) continue;
                String world = source.getString("world", "");
                String regionId = source.getString("region-id", "");
                if (world.isBlank() || regionId.isBlank()) continue;
                loaded.add(new LightRegionSource(
                        key,
                        world,
                        regionId,
                        Math.max(0, source.getDouble("base-radius", 0))
                ));
            }
        }
        regionSources = List.copyOf(loaded);
    }

    private void loadPathSources() {
        List<LightPathSource> loaded = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("light-control.paths");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection source = section.getConfigurationSection(key);
                if (source == null) continue;
                String world = source.getString("world", "");
                ConfigurationSection from = source.getConfigurationSection("from");
                ConfigurationSection to = source.getConfigurationSection("to");
                if (world.isBlank() || from == null || to == null) continue;
                loaded.add(new LightPathSource(
                        key,
                        world,
                        from.getDouble("x"),
                        from.getDouble("z"),
                        to.getDouble("x"),
                        to.getDouble("z"),
                        Math.max(0, source.getDouble("radius", 0))
                ));
            }
        }
        pathSources = List.copyOf(loaded);
    }

    public boolean isEnabled() {
        return config.getBoolean("light-control.enabled", false);
    }

    public int getUpdateIntervalTicks() {
        return Math.max(1, config.getInt("light-control.update-interval-ticks", 10));
    }

    public int getRefreshRadiusChunks() {
        return Math.max(0, config.getInt("light-control.refresh-radius-chunks", 3));
    }

    public int getStageChangeRefreshRadiusChunks() {
        return Math.max(getRefreshRadiusChunks(),
                config.getInt("light-control.stage-change-refresh-radius-chunks", 5));
    }

    public int getRefreshBatchSize() {
        return Math.max(1, config.getInt("light-control.refresh-batch-size-chunks", 8));
    }

    public int getChunkMoveRefreshRadiusChunks() {
        return Math.max(0,
                config.getInt("light-control.chunk-move-refresh-radius-chunks", 2));
    }

    public int getDefaultMaxLightLevel() {
        return Math.max(0, Math.min(15, config.getInt("light-control.default-max-light-level", 15)));
    }

    public List<LightDistanceStep> getSteps() {
        return steps;
    }

    public boolean isCircularStages() {
        return circularStages;
    }

    public boolean isWorldEnabled(String worldName) {
        return enabledWorlds.isEmpty() || enabledWorlds.contains(worldName);
    }

    public List<LightPointSource> getPointSources() {
        return pointSources;
    }

    public List<LightRegionSource> getRegionSources() {
        return regionSources;
    }

    public List<LightPathSource> getPathSources() {
        return pathSources;
    }

    public int getMaxLightLevelForDistance(int distance) {
        return getMaxLightLevelForDistance(steps, distance);
    }

    public int getMaxLightLevelForDistance(List<LightDistanceStep> overrideSteps, int distance) {
        int result = 15;
        for (LightDistanceStep step : overrideSteps.isEmpty() ? steps : overrideSteps) {
            if (distance < step.distance()) {
                break;
            }
            result = step.maxLightLevel();
        }
        return result;
    }
}
