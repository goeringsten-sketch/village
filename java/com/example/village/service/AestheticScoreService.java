package com.example.village.service;

import com.example.village.model.BuildingDefinition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;

/**
 * Berechnet einen Ästhetik-Score (0–100) für BLOCK_CHECK-Gebäude.
 */
public final class AestheticScoreService {

    public record ScoreBreakdown(
            int total,
            int materialVariety,
            int structuralIntegrity,
            int symmetry,
            int decoration
    ) {}

    private static final Set<Material> DECORATION_MATERIALS = Set.of(
            Material.TORCH, Material.WALL_TORCH, Material.LANTERN, Material.SOUL_LANTERN,
            Material.FLOWER_POT, Material.PAINTING, Material.ITEM_FRAME, Material.GLOW_ITEM_FRAME,
            Material.CANDLE, Material.WHITE_CARPET, Material.RED_CARPET, Material.BLUE_CARPET,
            Material.OAK_STAIRS, Material.SPRUCE_STAIRS, Material.BIRCH_STAIRS,
            Material.POPPY, Material.DANDELION, Material.BLUE_ORCHID, Material.ALLIUM,
            Material.AZURE_BLUET, Material.RED_TULIP, Material.ORANGE_TULIP,
            Material.WHITE_TULIP, Material.PINK_TULIP, Material.OXEYE_DAISY,
            Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.SUNFLOWER,
            Material.LILAC, Material.ROSE_BUSH, Material.PEONY
    );

    private static final Map<String, Set<Material>> MATERIAL_CATEGORIES = Map.of(
            "wood", Set.of(Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
                    Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.DARK_OAK_PLANKS),
            "stone", Set.of(Material.COBBLESTONE, Material.STONE, Material.STONE_BRICKS,
                    Material.BRICKS, Material.DEEPSLATE_BRICKS, Material.MOSSY_COBBLESTONE),
            "glass", Set.of(Material.GLASS, Material.GLASS_PANE, Material.TINTED_GLASS),
            "earth", Set.of(Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.PODZOL)
    );

    private final BuildingConfigLoader buildingConfigLoader;

    public AestheticScoreService(BuildingConfigLoader buildingConfigLoader) {
        this.buildingConfigLoader = buildingConfigLoader;
    }

    public ScoreBreakdown compute(BuildingDefinition def, Location origin) {
        if (def == null || origin == null || origin.getWorld() == null) {
            return new ScoreBreakdown(0, 0, 0, 0, 0);
        }

        int radius = BlockCheckValidator.radiusFromDefinition(def);
        if (radius <= 0) {
            radius = 8;
        }

        World world = origin.getWorld();
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        int minY = Math.max(world.getMinHeight(), oy - 2);
        int maxY = Math.min(world.getMaxHeight() - 1, oy + 12);

        Set<Material> distinctMaterials = new HashSet<>();
        int decorationCount = 0;
        int solidCount = 0;
        Map<Integer, Integer> leftBlocks = new HashMap<>();
        Map<Integer, Integer> rightBlocks = new HashMap<>();
        int minX = ox - radius;
        int maxX = ox + radius;
        int minZ = oz - radius;
        int maxZ = oz + radius;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type.isAir()) {
                        continue;
                    }
                    distinctMaterials.add(type);
                    solidCount++;
                    if (DECORATION_MATERIALS.contains(type)) {
                        decorationCount++;
                    }
                    if (x < ox) {
                        leftBlocks.merge(y * 1000 + z, 1, Integer::sum);
                    } else if (x > ox) {
                        rightBlocks.merge(y * 1000 + z, 1, Integer::sum);
                    }
                }
            }
        }

        int materialScore = scoreMaterialVariety(distinctMaterials);
        int structuralScore = scoreStructuralIntegrity(solidCount, def);
        int symmetryScore = scoreSymmetry(leftBlocks, rightBlocks, maxX - minX + 1, maxZ - minZ + 1);
        int decorationScore = scoreDecoration(decorationCount);

        int total = Math.min(100, materialScore + structuralScore + symmetryScore + decorationScore);
        return new ScoreBreakdown(total, materialScore, structuralScore, symmetryScore, decorationScore);
    }

    public double getProductionMultiplier(int aestheticScore) {
        if (!buildingConfigLoader.isAestheticScoringEnabled()) {
            return 1.0;
        }
        int bonusThreshold = buildingConfigLoader.getAestheticMinScoreForBonus();
        int penaltyThreshold = buildingConfigLoader.getAestheticPenaltyThreshold();

        if (aestheticScore >= 81) {
            return 1.0 + buildingConfigLoader.getAestheticHighBonus();
        }
        if (aestheticScore >= bonusThreshold) {
            return 1.0 + buildingConfigLoader.getAestheticMidBonus();
        }
        if (aestheticScore <= penaltyThreshold) {
            return 1.0 - buildingConfigLoader.getAestheticLowPenalty();
        }
        return 1.0;
    }

    private int scoreMaterialVariety(Set<Material> materials) {
        if (materials.isEmpty()) {
            return 0;
        }
        int categories = 0;
        for (Set<Material> category : MATERIAL_CATEGORIES.values()) {
            for (Material mat : materials) {
                if (category.contains(mat)) {
                    categories++;
                    break;
                }
            }
        }
        int score = Math.min(25, categories * 5);
        if (materials.size() == 1) {
            score = Math.max(0, score - 10);
        } else if (materials.size() > 8) {
            score = Math.max(0, score - 5);
        }
        score += scoreMaterialPairs(materials);
        return Math.min(25, score);
    }

    private int scoreMaterialPairs(Set<Material> materials) {
        int bonus = 0;
        for (List<String> pair : buildingConfigLoader.getAestheticMaterialPairs()) {
            if (pair.size() < 2) {
                continue;
            }
            Material a = Material.matchMaterial(pair.get(0).toUpperCase(Locale.ROOT));
            Material b = Material.matchMaterial(pair.get(1).toUpperCase(Locale.ROOT));
            if (a != null && b != null && materials.contains(a) && materials.contains(b)) {
                bonus += 3;
            }
        }
        return Math.min(10, bonus);
    }

    private int scoreStructuralIntegrity(int solidCount, BuildingDefinition def) {
        int score = 20;
        if (solidCount >= 50) {
            score += 10;
        } else if (solidCount >= 20) {
            score += 5;
        }
        if (def.getArea() != null && "hollow_structure".equals(def.getArea().shape())) {
            score += 5;
        }
        return Math.min(35, score);
    }

    private int scoreSymmetry(Map<Integer, Integer> left, Map<Integer, Integer> right,
                              int width, int depth) {
        double ratio = width > 0 ? (double) depth / width : 1.0;
        int proportionScore = (ratio >= 0.5 && ratio <= 2.0) ? 10 : 5;

        int matching = 0;
        int compared = 0;
        for (Map.Entry<Integer, Integer> entry : left.entrySet()) {
            compared++;
            int diff = Math.abs(entry.getValue() - right.getOrDefault(entry.getKey(), 0));
            if (diff <= 1) {
                matching++;
            }
        }
        int symmetryScore = compared > 0 ? (int) Math.round(10.0 * matching / compared) : 5;
        return Math.min(20, proportionScore + symmetryScore);
    }

    private int scoreDecoration(int decorationCount) {
        if (decorationCount < 5) {
            return 0;
        }
        if (decorationCount >= 15) {
            return 20;
        }
        return (decorationCount - 5) * 2;
    }
}
