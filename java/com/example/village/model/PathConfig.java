package com.example.village.model;

import org.bukkit.Material;
import java.util.*;

/** Konfiguration für Pfad- und Straßen-Gebäude. */
public final class PathConfig {

    private final int width;
    private final boolean widthUpgradeable;
    private final int maxWidth;
    private final Material visualizeBorderBlock;
    private final String surfaceCheckMode;     // "skylight" | "solid_under_air"
    private final int minSkylight;
    private final Material requiredSurfaceBlock;
    private final int requiredSurfacePercentage;
    private final Set<Material> validSurfaceBlocks;
    private final Set<Material> validBaseBlocks;
    private final int speedAmplifier;          // 0 = Speed I, 1 = Speed II …
    private final int maxSpeedAmplifier;
    private final int speedAmplifierPerLevel;
    private final int durationAfterLeaveTicks;
    private final int durationAfterLeaveTicksPerLevel;
    private final int widthIncrementPerLevel;

    public PathConfig(int width, boolean widthUpgradeable, int maxWidth,
                      Material visualizeBorderBlock, String surfaceCheckMode, int minSkylight,
                      Material requiredSurfaceBlock, int requiredSurfacePercentage,
                      Set<Material> validSurfaceBlocks, Set<Material> validBaseBlocks,
                      int speedAmplifier, int maxSpeedAmplifier, int speedAmplifierPerLevel,
                      int durationAfterLeaveTicks, int durationAfterLeaveTicksPerLevel,
                      int widthIncrementPerLevel) {
        this.width                    = Math.max(1, width);
        this.widthUpgradeable         = widthUpgradeable;
        this.maxWidth                 = Math.max(width, maxWidth);
        this.visualizeBorderBlock     = visualizeBorderBlock != null ? visualizeBorderBlock : Material.YELLOW_CONCRETE_POWDER;
        this.surfaceCheckMode         = surfaceCheckMode != null ? surfaceCheckMode : "skylight";
        this.minSkylight              = minSkylight;
        this.requiredSurfaceBlock     = requiredSurfaceBlock != null ? requiredSurfaceBlock : Material.DIRT_PATH;
        this.requiredSurfacePercentage = Math.max(0, Math.min(100, requiredSurfacePercentage));
        this.validSurfaceBlocks       = Collections.unmodifiableSet(validSurfaceBlocks != null ? new HashSet<>(validSurfaceBlocks) : Set.of());
        this.validBaseBlocks          = Collections.unmodifiableSet(validBaseBlocks != null ? new HashSet<>(validBaseBlocks) : Set.of());
        this.speedAmplifier           = Math.max(0, speedAmplifier);
        this.maxSpeedAmplifier        = Math.max(this.speedAmplifier, maxSpeedAmplifier);
        this.speedAmplifierPerLevel   = Math.max(0, speedAmplifierPerLevel);
        this.durationAfterLeaveTicks  = Math.max(0, durationAfterLeaveTicks);
        this.durationAfterLeaveTicksPerLevel = Math.max(0, durationAfterLeaveTicksPerLevel);
        this.widthIncrementPerLevel   = Math.max(0, widthIncrementPerLevel);
    }

    public int getWidth()                         { return width; }
    public boolean isWidthUpgradeable()           { return widthUpgradeable; }
    public int getMaxWidth()                      { return maxWidth; }
    public Material getVisualizeBorderBlock()     { return visualizeBorderBlock; }
    public String getSurfaceCheckMode()           { return surfaceCheckMode; }
    public int getMinSkylight()                   { return minSkylight; }
    public Material getRequiredSurfaceBlock()     { return requiredSurfaceBlock; }
    public int getRequiredSurfacePercentage()     { return requiredSurfacePercentage; }
    public Set<Material> getValidSurfaceBlocks()  { return validSurfaceBlocks; }
    public Set<Material> getValidBaseBlocks()     { return validBaseBlocks; }
    public int getSpeedAmplifier()                { return speedAmplifier; }
    public int getMaxSpeedAmplifier()             { return maxSpeedAmplifier; }
    public int getSpeedAmplifierPerLevel()        { return speedAmplifierPerLevel; }
    public int getDurationAfterLeaveTicks()       { return durationAfterLeaveTicks; }
    public int getDurationAfterLeaveTicksPerLevel(){ return durationAfterLeaveTicksPerLevel; }
    public int getWidthIncrementPerLevel()        { return widthIncrementPerLevel; }

    public static PathConfig defaultPath() {
        return new PathConfig(3, false, 3,
            Material.YELLOW_CONCRETE_POWDER, "skylight", 1,
            Material.DIRT_PATH, 60,
            Set.of(Material.DIRT_PATH),
            Set.of(Material.DIRT, Material.GRASS_BLOCK, Material.DIRT_PATH, Material.COARSE_DIRT),
            0, 4, 0,
            60, 20,
            1);
    }

    public static PathConfig defaultStreet() {
        return new PathConfig(3, true, 5,
            Material.WHITE_CONCRETE_POWDER, "skylight", 1,
            Material.COBBLESTONE, 70,
            Set.of(Material.COBBLESTONE, Material.STONE_BRICKS, Material.GRAVEL),
            Set.of(Material.COBBLESTONE, Material.STONE, Material.GRAVEL),
            1, 4, 1,
            120, 40,
            1);
    }
}
