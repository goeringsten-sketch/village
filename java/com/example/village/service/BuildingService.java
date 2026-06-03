package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.hook.BlueMapHook;
import com.example.village.hook.WorldEditHook;
import com.example.village.hook.WorldGuardHook;
import com.example.village.model.BuildingDefinition;
import com.example.village.model.BuildingType;
import com.example.village.model.PendingBlockSelection;
import com.example.village.model.Village;
import com.example.village.model.VillageBorder;
import com.example.village.model.VillageBuilding;
import com.example.village.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import net.kyori.adventure.text.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BuildingService {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VillageManager villageManager;
    private final EconomyService economyService;
    private final CurrencyService currencyService;
    private final WorldEditHook worldEditHook;
    private final WorldGuardHook worldGuardHook;
    private final BlueMapHook blueMapHook;

    private final Map<UUID, BuildingSession> activeSessions = new HashMap<>();
    private final Map<UUID, PendingBuildingConfirmation> pendingConfirmations = new java.util.concurrent.ConcurrentHashMap<>();
    // Diagnostic messages for last placement attempt per player
    private final Map<UUID, String> lastPlacementDiagnostics = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, BorderExpansionConfirmation> borderExpansionConfirmations = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, PendingSignRevealConfirmation> pendingSignRevealConfirmations = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, PendingBlockSelection> pendingBlockSelections = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, PendingPlacementMode> pendingPlacementModes = new java.util.concurrent.ConcurrentHashMap<>();
    private final int placementTimeoutSeconds;
    private final Map<UUID, Scoreboard> originalScoreboards = new java.util.concurrent.ConcurrentHashMap<>();
    // Aktive Konstruktionen: BuildingId -> Task
    private final Map<UUID, ConstructionTask> activeConstructions = new java.util.concurrent.ConcurrentHashMap<>();

    private BuildingDefinition getDefinition(String typeKey) {
        BuildingConfigLoader loader = plugin.getBuildingConfigLoader();
        return loader != null ? loader.getDefinition(typeKey) : null;
    }

    private BuildingType getLegacyType(String typeKey) {
        return configManager.getBuildingType(typeKey);
    }

    public boolean isPathBuilding(String typeKey) {
        BuildingDefinition def = getDefinition(typeKey);
        return def != null && def.isPath();
    }

    public boolean isBlockCheckBuilding(String typeKey) {
        BuildingDefinition def = getDefinition(typeKey);
        return def != null && def.isBlockCheckBased();
    }

    public boolean startPathPlacement(Player player, Village village, String typeKey, Location origin, String direction) {
        BuildingDefinition def = getDefinition(typeKey);
        if (def == null || !def.isPath()) return false;

        PathService pathService = plugin.getPathService();
        if (pathService == null) {
            MessageUtil.send(player, configManager.getPrefix(), "&cPfad-Service nicht verfügbar.");
            return false;
        }
        if (pathService.isInPathSession(player.getUniqueId())) {
            MessageUtil.send(player, configManager.getPrefix(), "&cDu hast bereits eine aktive Pfad-Session.");
            return false;
        }

        villageManager.ensureBuildingTypeOrdinals(village);
        int nextOrdinal = village.getBuildings().stream()
                .filter(b -> b.getTypeKey().equals(typeKey))
                .mapToInt(VillageBuilding::getTypeOrdinal)
                .max()
                .orElse(0) + 1;

        VillageBuilding building = new VillageBuilding(UUID.randomUUID(), typeKey, origin);
        building.setDirection(direction != null ? direction : "N");
        building.setOwnerId(player.getUniqueId());
        building.setTypeOrdinal(nextOrdinal);

        if (!pathService.startPathSession(player, village, building, def)) {
            return false;
        }

        MessageUtil.send(player, configManager.getPrefix(),
                "&aPfad-Aufzeichnung gestartet. Laufe die gewünschte Strecke und nutze &e/village path done &7oder &e/village path cancel&7.");
        return true;
    }

    private String getDisplayName(String typeKey) {
        BuildingDefinition def = getDefinition(typeKey);
        if (def != null) return def.getName();
        BuildingType type = getLegacyType(typeKey);
        return type != null ? type.getDisplayName() : typeKey;
    }

    public String getDisplayNameForType(String typeKey) {
        return getDisplayName(typeKey);
    }

    private int getRequiredVillageLevel(String typeKey) {
        BuildingDefinition def = getDefinition(typeKey);
        if (def != null) return def.getRequiredVillageLevel();
        BuildingType type = getLegacyType(typeKey);
        return type != null ? type.getRequiredLevel() : Integer.MAX_VALUE;
    }

    private String getRequiredUpgradeKey(String typeKey) {
        BuildingType type = getLegacyType(typeKey);
        return type != null ? type.getRequiredUpgrade() : null;
    }

    private String getSchematicName(String typeKey) {
        BuildingDefinition def = getDefinition(typeKey);
        String baseName = null;
        if (def != null && def.getSchematic() != null && !def.getSchematic().isBlank()) {
            baseName = def.getSchematic();
        } else {
            BuildingType type = getLegacyType(typeKey);
            baseName = type != null ? type.getSchematic() : null;
        }
        
        if (baseName == null || baseName.isBlank()) return null;
        
        // Try base schematic first
        File baseFile = new File(plugin.getDataFolder(), "schematics/" + baseName);
        if (baseFile.exists()) return baseName;
        
        // Try variations (_001, _002, etc.)
        String basePath = baseName.replaceAll("\\.schem$", "");
        File baseDir = new File(plugin.getDataFolder(), "schematics");
        if (baseDir.isDirectory()) {
            for (int i = 1; i <= 100; i++) {
                String variant = basePath + String.format("_%03d.schem", i);
                File variantFile = new File(baseDir, variant);
                if (variantFile.exists()) {
                    return variant; // Return first found variation
                }
            }
        }
        
        return baseName; // Return original name even if not found (will fail later with proper error)
    }

    /**
     * Find all schematic variations for a building type (e.g., house_001.schem, house_002.schem, ...)
     * Returns list of found variations, or empty list if none found.
     */
    public java.util.List<String> findSchematicVariations(String typeKey) {
        java.util.List<String> variations = new java.util.ArrayList<>();
        BuildingDefinition def = getDefinition(typeKey);
        String baseName = null;
        if (def != null && def.getSchematic() != null && !def.getSchematic().isBlank()) {
            baseName = def.getSchematic();
        } else {
            BuildingType type = getLegacyType(typeKey);
            baseName = type != null ? type.getSchematic() : null;
        }
        
        if (baseName == null || baseName.isBlank()) return variations;
        
        String basePath = baseName.replaceAll("\\.schem$", "");
        File baseDir = new File(plugin.getDataFolder(), "schematics");
        
        // Add base schematic if exists
        File baseFile = new File(baseDir, baseName);
        if (baseFile.exists()) {
            variations.add(baseName);
        }
        
        // Add variations (_001, _002, etc.)
        if (baseDir.isDirectory()) {
            for (int i = 1; i <= 100; i++) {
                String variant = basePath + String.format("_%03d.schem", i);
                File variantFile = new File(baseDir, variant);
                if (variantFile.exists()) {
                    variations.add(variant);
                }
            }
        }
        
        return variations;
    }

    private String getSchematicName(String typeKey, String overrideName) {
        if (overrideName != null && !overrideName.isBlank()) {
            File overrideFile = new File(plugin.getDataFolder(), "schematics/" + overrideName);
            if (overrideFile.exists()) {
                return overrideName;
            }
        }
        return getSchematicName(typeKey);
    }

    public String getSchematicNameForType(String typeKey) {
        return getSchematicName(typeKey);
    }

    public String resolvePlacementDirection(Player player, String explicitDirection) {
        if (explicitDirection != null && !explicitDirection.isBlank()) {
            return explicitDirection;
        }

        float yaw = player.getLocation().getYaw();
        yaw = (yaw % 360 + 360) % 360; // normalize to 0-360

        if (yaw < 45 || yaw >= 315) {
            return "S";
        }
        if (yaw < 135) {
            return "W";
        }
        if (yaw < 225) {
            return "N";
        }
        return "E";
    }

    private double getGlobalBuildCost(String typeKey) {
        BuildingDefinition def = getDefinition(typeKey);
        if (def != null) return def.getBuildMoneyGlobal();
        BuildingType type = getLegacyType(typeKey);
        return type != null ? type.getGlobalCost() : 0.0;
    }

    private double getLocalBuildCost(String typeKey) {
        BuildingDefinition def = getDefinition(typeKey);
        if (def != null) return def.getBuildMoneyLocal();
        BuildingType type = getLegacyType(typeKey);
        return type != null ? type.getLocalCost() : 0.0;
    }

    public static class SchematicSelection {
        private Location pos1;
        private Location pos2;
        private Location origin;

        public Location getPos1() { return pos1; }
        public void setPos1(Location pos1) { this.pos1 = pos1; }
        public Location getPos2() { return pos2; }
        public void setPos2(Location pos2) { this.pos2 = pos2; }
        public Location getOrigin() { return origin; }
        public void setOrigin(Location origin) { this.origin = origin; }
    }

    private final Map<UUID, SchematicSelection> schematicSelections = new java.util.concurrent.ConcurrentHashMap<>();

    public SchematicSelection getOrCreateSchematicSelection(UUID playerId) {
        return schematicSelections.computeIfAbsent(playerId, k -> new SchematicSelection());
    }

    public void clearSchematicSelection(UUID playerId) {
        schematicSelections.remove(playerId);
    }

    public WorldEditHook getWorldEditHook() {
        return worldEditHook;
    }


    public BuildingService(VillagePlugin plugin, VillageConfigManager configManager,
                           VillageManager villageManager, EconomyService economyService,
                           CurrencyService currencyService,
                           WorldEditHook worldEditHook, WorldGuardHook worldGuardHook,
                           BlueMapHook blueMapHook) {
        this.plugin = plugin;
        this.placementTimeoutSeconds = plugin.getConfig().getInt("placement.timeout-seconds", 60);
        this.configManager = configManager;
        this.villageManager = villageManager;
        this.economyService = economyService;
        this.currencyService = currencyService;
        this.worldEditHook = worldEditHook;
        this.worldGuardHook = worldGuardHook;
        this.blueMapHook = blueMapHook;
    }

    public enum PlaceResult {
        SUCCESS,
        NOT_ENOUGH_MONEY,
        LEVEL_TOO_LOW,
        UPGRADE_REQUIRED,
        OUTSIDE_BORDER,
        BUILDING_TOO_FAR,
        BORDER_EXPANSION_NEEDED,
        INSIDE_DEFAULT_BORDER,
        ON_START_BORDER,
        OVERLAPS_BUILDING,
        PARTIAL_OUTSIDE_BORDER,
        BLOCK_CHECK_VALIDATION_FAILED,
        UNKNOWN_TYPE,
        ALREADY_BUILDING,
        SCHEMATIC_NOT_FOUND
    }

    // --- Two-step building placement flow ---

    /**
     * Step 1: Load schematic, show as fake blocks (client-side preview).
     * Does NOT register the building in the village yet.
     * Returns true if preview was shown successfully.
     */
    public PlaceResult previewBuilding(Player player, Village village, String typeKey,
                                    Location location, String direction, String schematicName) {
        if (pendingConfirmations.containsKey(player.getUniqueId()) || activeSessions.containsKey(player.getUniqueId())) {
            return PlaceResult.ALREADY_BUILDING;
        }

        BuildingDefinition def = getDefinition(typeKey);
        if (def != null && def.isBlockCheckBased()) {
            int villageLevel = village.getLevel();
            int requiredLevel = getRequiredVillageLevel(typeKey);
            if (villageLevel < requiredLevel) {
                return PlaceResult.LEVEL_TOO_LOW;
            }

            PlaceResult boundaryCheck = validateBlockCheckBoundary(village, def, location);
            if (boundaryCheck != PlaceResult.SUCCESS) {
                plugin.getLogger().warning("previewBuilding: BlockCheck-Grenzprüfung fehlgeschlagen: " + boundaryCheck);
                if (boundaryCheck == PlaceResult.OVERLAPS_BUILDING) {
                    String diag = findOverlapDiagnostic(village, collectBlockCheckFootprint(def, location), location);
                    setLastPlacementDiagnostic(player.getUniqueId(), diag);
                } else {
                    clearLastPlacementDiagnostic(player.getUniqueId());
                }
                return boundaryCheck;
            }

            PlaceResult validation = validateBlockCheck(def, location, player);
            if (validation != PlaceResult.SUCCESS) {
                plugin.getLogger().warning("previewBuilding: BlockCheck-Validierung fehlgeschlagen: " + validation);
                return validation;
            }

            pendingConfirmations.put(player.getUniqueId(),
                    new PendingBuildingConfirmation(village, typeKey, direction, location,
                            null, Collections.emptyList(), schematicName));
            return PlaceResult.SUCCESS;
        }

        int villageLevel = village.getLevel();
        int requiredLevel = getRequiredVillageLevel(typeKey);
        if (villageLevel < requiredLevel) {
            return PlaceResult.LEVEL_TOO_LOW;
        }

        String resolvedDirection = resolvePlacementDirection(player, direction);
        schematicName = getSchematicName(typeKey, schematicName);
        if (schematicName == null || schematicName.isBlank()) {
            plugin.getLogger().warning("previewBuilding: Gebäude ohne Schematic oder unbekannter Typ: " + typeKey);
            return PlaceResult.SCHEMATIC_NOT_FOUND;
        }

        if (!worldEditHook.isAvailable()) {
            plugin.getLogger().warning("previewBuilding: WorldEdit ist nicht installiert oder aktiviert.");
            return PlaceResult.SCHEMATIC_NOT_FOUND;
        }

        File schematicFile = new File(plugin.getDataFolder(), "schematics/" + schematicName);
        if (!schematicFile.exists()) {
            plugin.getLogger().warning("previewBuilding: Schematic-Datei nicht gefunden: " + schematicFile.getAbsolutePath());
            return PlaceResult.SCHEMATIC_NOT_FOUND;
        }

        WorldEditHook.SchematicData schematicData = worldEditHook.loadAndRotateSchematicData(schematicFile, resolvedDirection);
        if (schematicData == null) {
            plugin.getLogger().warning("previewBuilding: loadAndRotateSchematicData() gab null zurück");
            return PlaceResult.SCHEMATIC_NOT_FOUND;
        }

        if (schematicData.getBlocks().isEmpty()) {
            plugin.getLogger().warning("previewBuilding: Schematic ist leer: " + schematicName);
            return PlaceResult.SCHEMATIC_NOT_FOUND;
        }

        PlaceResult previewCheck = validateBuildingBoundary(village, location, schematicData);
        if (previewCheck != PlaceResult.SUCCESS) {
            plugin.getLogger().warning("previewBuilding: validateBuildingBoundary = " + previewCheck);
            if (previewCheck == PlaceResult.OVERLAPS_BUILDING) {
                String diag = findOverlapDiagnostic(village, collectSchematicFootprint(location, schematicData), location);
                setLastPlacementDiagnostic(player.getUniqueId(), diag);
            } else {
                clearLastPlacementDiagnostic(player.getUniqueId());
            }
            return previewCheck;
        }

        // Send fake blocks to the player
        List<Location> previewLocations = new ArrayList<>();
        for (WorldEditHook.SchematicBlock sb : schematicData.getBlocks()) {
            Location blockLoc = location.clone().add(sb.dx(), sb.dy(), sb.dz());
            player.sendBlockChange(blockLoc, sb.blockData());
            previewLocations.add(blockLoc);
        }

        pendingConfirmations.put(player.getUniqueId(),
                new PendingBuildingConfirmation(village, typeKey, resolvedDirection, location,
                        schematicData, previewLocations, schematicName));

        return PlaceResult.SUCCESS;
    }

    /**
     * Step 2a: Check if building can be placed and if border expansion is needed.
     * If border expansion IS needed: Shows warning, saves state, returns BORDER_EXPANSION_NEEDED
     * If border expansion NOT needed: Places building and returns SUCCESS
     * Otherwise: Returns error code
     */
    public PlaceResult confirmBuilding(Player player) {
        plugin.getLogger().info("=== confirmBuilding STARTED ===");
        PendingBuildingConfirmation pending = pendingConfirmations.get(player.getUniqueId());
        plugin.getLogger().info("confirmBuilding: getPendingConfirmation = " + (pending != null ? "FOUND" : "NULL"));
        
        if (pending == null) {
            plugin.getLogger().info("confirmBuilding: Keine Confirmation gefunden - return UNKNOWN_TYPE");
            return PlaceResult.UNKNOWN_TYPE;
        }

        Village village = pending.getVillage();
        String typeKey = pending.getTypeKey();
        plugin.getLogger().info("confirmBuilding: BuildingType=" + getDisplayName(typeKey));

        BuildingDefinition def = getDefinition(typeKey);
        PlaceResult boundaryCheck;
        if (def != null && def.isBlockCheckBased()) {
            boundaryCheck = validateBlockCheckBoundary(village, def, pending.getLocation());
        } else {
            boundaryCheck = validateBuildingBoundary(village, pending.getLocation(), pending.getSchematicData());
        }
        plugin.getLogger().info("confirmBuilding: validateBuildingBoundary = " + boundaryCheck);
        
        if (boundaryCheck != PlaceResult.SUCCESS) {
            plugin.getLogger().info("confirmBuilding: Boundary-Check FAILED - cleaning up");
            // store diagnostic for player when overlap occurred
            if (boundaryCheck == PlaceResult.OVERLAPS_BUILDING) {
                String diag;
                if (def != null && def.isBlockCheckBased()) {
                    diag = findOverlapDiagnostic(village, collectBlockCheckFootprint(def, pending.getLocation()), pending.getLocation());
                } else {
                    diag = findOverlapDiagnostic(village, collectSchematicFootprint(pending.getLocation(), pending.getSchematicData()), pending.getLocation());
                }
                setLastPlacementDiagnostic(player.getUniqueId(), diag);
            } else {
                clearLastPlacementDiagnostic(player.getUniqueId());
            }
            pendingConfirmations.remove(player.getUniqueId());
            if (pending.getSchematicData() != null) {
                double globalCost = getGlobalBuildCost(typeKey);
                if (globalCost > 0 && economyService.isAvailable()) {
                    economyService.deposit(player, globalCost);
                }
                clearPendingPreview(player, pending);
            }
            return boundaryCheck;
        }

        boolean expansionNeeded = def != null && def.isBlockCheckBased()
                ? isBorderExpansionNeededForBlockCheck(village, def, pending.getLocation())
                : isBorderExpansionNeeded(village, pending.getLocation(), pending.getSchematicData());
        plugin.getLogger().info("confirmBuilding: isBorderExpansionNeeded = " + expansionNeeded);
        
        if (expansionNeeded) {
            plugin.getLogger().info("confirmBuilding: Border expansion needed - showing dialog");
            // Show warning and ask for confirmation (clickable)
            BuildingType type2 = configManager.getBuildingType(pending.getTypeKey());

            MessageUtil.sendYesNoRunCommand(
                    player,
                    configManager.getPrefix(),
                    "&cAchtung: Die Dorfgrenze muss erweitert werden. Fortfahren?",
                    "/village buildexpansionconfirm ja",
                    "/village buildexpansionconfirm nein"
            );
            
            // Create a temporary building for state tracking (not yet registered in village)
            UUID tempBuildingId = UUID.randomUUID();
            VillageBuilding tempBuilding = new VillageBuilding(tempBuildingId, pending.getTypeKey(), pending.getLocation());
            
            // Save expansion state for later confirmation
            BuildingSession tempSession = new BuildingSession(village, tempBuilding, new HashMap<>(), pending.getSchematicData());
            BorderExpansionConfirmation expansionState = new BorderExpansionConfirmation(
                    village, tempBuilding, tempSession, type2, pending.getLocation(), pending.getSchematicData());
            borderExpansionConfirmations.put(player.getUniqueId(), expansionState);
            plugin.getLogger().info("confirmBuilding: BorderExpansionConfirmation saved, returning BORDER_EXPANSION_NEEDED");
            
            return PlaceResult.BORDER_EXPANSION_NEEDED;
        }

        plugin.getLogger().info("confirmBuilding: No expansion needed - calling finalizeBuildingPlacement");
        return finalizeBuildingPlacement(player, pending);
    }

    /**
     * Step 2b: User confirmed border expansion with "ja" - finalize placement
     */
    public PlaceResult confirmBorderExpansion(Player player) {
        PendingBuildingConfirmation pending = pendingConfirmations.get(player.getUniqueId());
        if (pending == null) return PlaceResult.UNKNOWN_TYPE;

        BuildingDefinition def = getDefinition(pending.getTypeKey());
        if (def != null && def.isBlockCheckBased()) {
            PlaceResult boundaryCheck = validateBlockCheckBoundary(pending.getVillage(), def, pending.getLocation());
            if (boundaryCheck != PlaceResult.SUCCESS) {
                return boundaryCheck;
            }
            PlaceResult validation = validateBlockCheck(def, pending.getLocation(), player);
            if (validation != PlaceResult.SUCCESS) {
                return validation;
            }
            return finalizeBuildingPlacement(player, pending);
        }

        PlaceResult boundaryCheck = validateBuildingBoundary(pending.getVillage(), pending.getLocation(), pending.getSchematicData());
        if (boundaryCheck != PlaceResult.SUCCESS) {
            return boundaryCheck;
        }

        return finalizeBuildingPlacement(player, pending);
    }

    /**
     * Step 2c: User declined border expansion with "nein" - go back to placement
     */
    public void declineBorderExpansion(Player player) {
        pendingConfirmations.remove(player.getUniqueId());
        // Preview will be re-shown by ChatInputListener
    }

    /**
     * Internal: Handles the actual building placement and border extension
     */
    private PlaceResult finalizeBuildingPlacement(Player player, PendingBuildingConfirmation pending) {
        if (pending == null) return PlaceResult.UNKNOWN_TYPE;
        BuildingDefinition def = getDefinition(pending.getTypeKey());
        if (def != null && def.isBlockCheckBased() && pending.getSchematicData() == null) {
            return finalizeBlockCheckBuildingPlacement(player, pending, def);
        }

        plugin.getLogger().info("=== finalizeBuildingPlacement STARTED ===");
        Village village = pending.getVillage();

        // Remove from pending (we're finalizing)
        pendingConfirmations.remove(player.getUniqueId());
        plugin.getLogger().info("finalizeBuildingPlacement: Entfernt aus pendingConfirmations");

        // Clear the fake block preview - restore original blocks
        clearPendingPreview(player, pending);
        plugin.getLogger().info("finalizeBuildingPlacement: Preview gelöscht");

        // Check money again (in case it changed)
        double globalCost = getGlobalBuildCost(pending.getTypeKey());
        if (globalCost > 0 && economyService.isAvailable()) {
            if (!economyService.canAfford(player, globalCost)) {
                plugin.getLogger().info("finalizeBuildingPlacement: Nicht genug Geld - return NOT_ENOUGH_MONEY");
                return PlaceResult.NOT_ENOUGH_MONEY;
            }
            if (!economyService.withdraw(player, globalCost)) {
                plugin.getLogger().info("finalizeBuildingPlacement: Withdrawal failed - return NOT_ENOUGH_MONEY");
                return PlaceResult.NOT_ENOUGH_MONEY;
            }
            plugin.getLogger().info("finalizeBuildingPlacement: " + globalCost + " withdrawn");
        }

        String direction = pending.getDirection() != null && !pending.getDirection().isBlank()
                ? pending.getDirection()
                : resolvePlacementDirection(player, null);
        plugin.getLogger().info("finalizeBuildingPlacement: Direction = " + direction);

        villageManager.ensureBuildingTypeOrdinals(village);
        int nextOrdinal = village.getBuildings().stream()
                .filter(b -> pending.getTypeKey().equals(b.getTypeKey()))
                .mapToInt(VillageBuilding::getTypeOrdinal)
                .max()
                .orElse(0) + 1;

        UUID buildingId = UUID.randomUUID();
        VillageBuilding building = new VillageBuilding(buildingId, pending.getTypeKey(), pending.getLocation());
        building.setDirection(direction);
        building.setSchematicName(pending.getSchematicName());
        building.setOwnerId(player.getUniqueId());
        building.setTypeOrdinal(nextOrdinal);
        village.addBuilding(building);
        plugin.getLogger().info("finalizeBuildingPlacement: Building registriert mit ID " + buildingId);

        // Load schematic for validation (already rotated by WorldEdit)
        Map<int[], Material> schematic = new HashMap<>();
        for (WorldEditHook.SchematicBlock sb : pending.getSchematicData().getBlocks()) {
            schematic.put(new int[]{sb.dx(), sb.dy(), sb.dz()}, sb.blockData().getMaterial());
        }
        plugin.getLogger().info("finalizeBuildingPlacement: Schematic geladen mit " + schematic.size() + " Blöcken");

        updateBuildingProtectionAndMarker(village, building, pending.getSchematicData());
        plugin.getLogger().info("finalizeBuildingPlacement: WorldGuard/BlueMap aktualisiert");

        BuildingSession session = new BuildingSession(village, building, schematic, pending.getSchematicData());
        activeSessions.put(player.getUniqueId(), session);
        plugin.getLogger().info("finalizeBuildingPlacement: Active session erstellt");
        
        // EXTEND VILLAGE BORDER if needed (now for real)
        extendVillageBorderIfNeeded(village, pending.getLocation(), pending.getSchematicData());
        plugin.getLogger().info("finalizeBuildingPlacement: Border erweitert (falls nötig)");
        
        villageManager.saveVillage(village);
        plugin.getLogger().info("finalizeBuildingPlacement: Village gespeichert");

        // Place the build menu sign next to the building and remember its location.
        Location signLoc = placeBuildSign(building.getLocation(), direction, getDisplayName(pending.getTypeKey()));
        session.setSignLocation(signLoc);
        building.setSignLocation(signLoc);
        plugin.getLogger().info("finalizeBuildingPlacement: Build-Sign platziert");

        // Show the building boundary with GOLD_ORE using actual block positions
        showBuildingBoundary(player);
        session.setPreviewVisible(false);
        clearPlacementMode(player.getUniqueId());
        plugin.getLogger().info("=== finalizeBuildingPlacement SUCCESS ===");

        return PlaceResult.SUCCESS;
    }

    private PlaceResult finalizeBlockCheckBuildingPlacement(Player player,
                                                            PendingBuildingConfirmation pending,
                                                            BuildingDefinition def) {
        plugin.getLogger().info("=== finalizeBlockCheckBuildingPlacement STARTED ===");
        Village village = pending.getVillage();
        pendingConfirmations.remove(player.getUniqueId());

        double globalCost = getGlobalBuildCost(pending.getTypeKey());
        double localCost = getLocalBuildCost(pending.getTypeKey());
        if (globalCost > 0) {
            if (!economyService.canAfford(player, globalCost)) {
                plugin.getLogger().info("finalizeBlockCheckBuildingPlacement: Nicht genug globales Geld");
                return PlaceResult.NOT_ENOUGH_MONEY;
            }
            if (!economyService.withdraw(player, globalCost)) {
                plugin.getLogger().info("finalizeBlockCheckBuildingPlacement: Globaler Abzug fehlgeschlagen");
                return PlaceResult.NOT_ENOUGH_MONEY;
            }
        }

        if (localCost > 0) {
            String localCurrencyId = currencyService.getVillageCurrencyId(village);
            if (currencyService.getBalance(player.getUniqueId(), localCurrencyId) < localCost) {
                if (globalCost > 0) economyService.deposit(player, globalCost);
                plugin.getLogger().info("finalizeBlockCheckBuildingPlacement: Nicht genug lokales Geld");
                return PlaceResult.NOT_ENOUGH_MONEY;
            }
            if (!currencyService.removeBalance(player.getUniqueId(), localCurrencyId, localCost)) {
                if (globalCost > 0) economyService.deposit(player, globalCost);
                plugin.getLogger().info("finalizeBlockCheckBuildingPlacement: Lokaler Abzug fehlgeschlagen");
                return PlaceResult.NOT_ENOUGH_MONEY;
            }
        }

        villageManager.ensureBuildingTypeOrdinals(village);
        int nextOrdinal = village.getBuildings().stream()
                .filter(b -> pending.getTypeKey().equals(b.getTypeKey()))
                .mapToInt(VillageBuilding::getTypeOrdinal)
                .max()
                .orElse(0) + 1;

        UUID buildingId = UUID.randomUUID();
        VillageBuilding building = new VillageBuilding(buildingId, pending.getTypeKey(), pending.getLocation());
        building.setDirection(pending.getDirection() != null && !pending.getDirection().isBlank()
                ? pending.getDirection()
                : resolvePlacementDirection(player, null));
        building.setOwnerId(player.getUniqueId());
        building.setTypeOrdinal(nextOrdinal);
        building.setCompleted(true);
        village.addBuilding(building);

        if (worldGuardHook.isAvailable()) {
            createBlockCheckBuildingRegion(village, building, def);
        }

        Location signLoc = placeBuildingInfoSign(building.getLocation(), getDisplayName(pending.getTypeKey()), pending.getDirection());
        building.setSignLocation(signLoc);

        villageManager.saveVillage(village);
        clearPlacementMode(player.getUniqueId());
        plugin.getLogger().info("finalizeBlockCheckBuildingPlacement: Building registered and marked completed");

        return PlaceResult.SUCCESS;
    }

    private void createBlockCheckBuildingRegion(Village village, VillageBuilding building, BuildingDefinition def) {
        int radius = BlockCheckValidator.radiusFromDefinition(def);
        if (radius <= 0) radius = 8;

        Location origin = building.getLocation();
        int minY = Math.max(origin.getBlockY() - 5, origin.getWorld().getMinHeight());
        int maxY = Math.min(origin.getBlockY() + 15, origin.getWorld().getMaxHeight() - 1);

        Location min = origin.clone().add(-radius, minY - origin.getBlockY(), -radius);
        Location max = origin.clone().add(radius, maxY - origin.getBlockY(), radius);
        worldGuardHook.createBuildingRegion(village, building.getId().toString(), min, max);
    }

    private boolean isWithinStartBoundary(Village village, int x, int z) {
        if (village == null) {
            return false;
        }

        BuildingDefinition dorfbrunnen = getDefinition("dorfbrunnen");
        if (dorfbrunnen != null && dorfbrunnen.getArea() != null) {
            Location bellLocation = village.getBellLocation();
            if (bellLocation != null) {
                int radius = switch (dorfbrunnen.getArea().shape()) {
                    case "circle" -> dorfbrunnen.getArea().maxRadius();
                    case "rectangle" -> Math.max(dorfbrunnen.getArea().maxWidth(), dorfbrunnen.getArea().maxDepth()) / 2;
                    default -> 0;
                };
                if (radius > 0) {
                    int dx = x - bellLocation.getBlockX();
                    int dz = z - bellLocation.getBlockZ();
                    return dx * dx + dz * dz <= radius * radius;
                }
            }
        }

        VillageBorder border0 = village.getBorderById(0);
        return border0 != null && (border0.contains(x, z) || border0.isOnBorder(x, z));
    }

    private Set<Long> collectBlockCheckFootprint(BuildingDefinition def, Location origin) {
        Set<Long> coords = new HashSet<>();
        if (def == null || origin == null) {
            return coords;
        }

        int radius = BlockCheckValidator.radiusFromDefinition(def);
        if (radius <= 0) {
            radius = 8;
        }

        for (int x = origin.getBlockX() - radius; x <= origin.getBlockX() + radius; x++) {
            for (int z = origin.getBlockZ() - radius; z <= origin.getBlockZ() + radius; z++) {
                coords.add(footprintKey(x, z));
            }
        }
        return coords;
    }

    private Set<Long> collectSchematicFootprint(Location origin, WorldEditHook.SchematicData schematicData) {
        Set<Long> coords = new HashSet<>();
        if (origin == null || schematicData == null || schematicData.getBlocks().isEmpty()) {
            return coords;
        }

        for (WorldEditHook.SchematicBlock sb : schematicData.getBlocks()) {
            coords.add(footprintKey(origin.getBlockX() + sb.dx(), origin.getBlockZ() + sb.dz()));
        }
        return coords;
    }

    private Set<Long> collectExistingBuildingFootprint(VillageBuilding building) {
        if (building == null || building.getLocation() == null) {
            return new HashSet<>();
        }

        BuildingDefinition def = getDefinition(building.getTypeKey());
        if (def == null) {
            return new HashSet<>();
        }

        if (def.isBlockCheckBased()) {
            return collectBlockCheckFootprint(def, building.getLocation());
        }

        String schematicName = getSchematicName(building.getTypeKey(), building.getSchematicName());
        if (schematicName == null || schematicName.isBlank() || !worldEditHook.isAvailable()) {
            return new HashSet<>();
        }

        File schematicFile = new File(plugin.getDataFolder(), "schematics/" + schematicName);
        if (!schematicFile.exists()) {
            return new HashSet<>();
        }

        WorldEditHook.SchematicData cached = worldEditHook.loadAndRotateSchematicData(schematicFile, building.getDirection());
        if (cached == null || cached.getBlocks().isEmpty()) {
            return new HashSet<>();
        }

        return collectSchematicFootprint(building.getLocation(), cached);
    }

    private boolean intersectsExistingBuilding(Village village, Set<Long> proposedFootprint, Location origin) {
        if (village == null || origin == null || origin.getWorld() == null || proposedFootprint == null || proposedFootprint.isEmpty()) {
            return false;
        }

        for (VillageBuilding existing : village.getBuildings()) {
            if (existing == null || existing.getLocation() == null || existing.getLocation().getWorld() == null) {
                continue;
            }
            if (!existing.getLocation().getWorld().equals(origin.getWorld())) {
                continue;
            }

            Set<Long> existingFootprint = collectExistingBuildingFootprint(existing);
            if (existingFootprint.isEmpty()) {
                continue;
            }

            for (Long coordinate : proposedFootprint) {
                if (existingFootprint.contains(coordinate)) {
                    // record diagnostic for the current pending player (if any) will be set by caller
                    String type = existing.getTypeKey();
                    String ownerName = "unbekannt";
                    if (existing.getOwnerId() != null) {
                        org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(existing.getOwnerId());
                        if (off != null && off.getName() != null) ownerName = off.getName();
                    }
                    return true;
                }
            }
        }

        return false;
    }

    private long footprintKey(int x, int z) {
        return (((long) x) << 32) | (z & 0xffffffffL);
    }

    private PlaceResult validateBlockCheckBoundary(Village village, BuildingDefinition def, Location origin) {
        if (def == null || origin == null || village == null) return PlaceResult.SUCCESS;

        Location bellLocation = village.getBellLocation();
        int maxRadius = village.getMaxBuildRadius(configManager.getMaxBuildRadius(), configManager.getUpgradeRadiusPerLevel());
        int maxRadiusSquared = maxRadius * maxRadius;
        int radius = BlockCheckValidator.radiusFromDefinition(def);
        if (radius <= 0) radius = 8;

        Set<Long> proposedFootprint = collectBlockCheckFootprint(def, origin);
        if (intersectsExistingBuilding(village, proposedFootprint, origin)) {
            return PlaceResult.OVERLAPS_BUILDING;
        }

        int inside = 0;
        int outside = 0;
        int outsideRadius = 0;

        for (int x = origin.getBlockX() - radius; x <= origin.getBlockX() + radius; x++) {
            for (int z = origin.getBlockZ() - radius; z <= origin.getBlockZ() + radius; z++) {
                if (isWithinStartBoundary(village, x, z)) {
                    return PlaceResult.ON_START_BORDER;
                }
                // NEU: Prüfen ob Block auf einer bestehenden Dorfgrenze liegt (Teilüberschneidung)
                if (village.isOnAnyBorder(x, z)) {
                    return PlaceResult.PARTIAL_OUTSIDE_BORDER;
                }

                int dx = x - bellLocation.getBlockX();
                int dz = z - bellLocation.getBlockZ();
                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > maxRadiusSquared) {
                    outsideRadius++;
                }

                if (village.containsLocation(x, z)) {
                    inside++;
                } else {
                    outside++;
                }
            }
        }

        if (inside == 0) {
            return PlaceResult.OUTSIDE_BORDER;
        }
        if (outsideRadius > 0) {
            return PlaceResult.BUILDING_TOO_FAR;
        }

        PlaceResult defaultBorder = validateNotEntirelyInsideDefaultBorderOnlyForBlockCheck(village, origin, def, radius);
        if (defaultBorder != PlaceResult.SUCCESS) {
            return defaultBorder;
        }

        return PlaceResult.SUCCESS;
    }

    private PlaceResult validateBlockCheck(BuildingDefinition def, Location origin, Player player) {
        if (def == null || origin == null) return PlaceResult.SUCCESS;
        int radius = BlockCheckValidator.radiusFromDefinition(def);
        if (radius <= 0) radius = 8;

        BlockCheckValidator.ValidationResult validation = BlockCheckValidator.validate(def, origin, radius);
        if (!validation.valid()) {
            if (player != null) {
                com.example.village.util.MessageUtil.send(player, configManager.getPrefix(), "&c" + validation.failReason());
            }
            return PlaceResult.BLOCK_CHECK_VALIDATION_FAILED;
        }
        return PlaceResult.SUCCESS;
    }

    private boolean isBorderExpansionNeededForBlockCheck(Village village, BuildingDefinition def, Location origin) {
        if (def == null || village == null || origin == null) return false;
        int radius = BlockCheckValidator.radiusFromDefinition(def);
        if (radius <= 0) radius = 8;

        int inside = 0;
        int outside = 0;
        for (int x = origin.getBlockX() - radius; x <= origin.getBlockX() + radius; x++) {
            for (int z = origin.getBlockZ() - radius; z <= origin.getBlockZ() + radius; z++) {
                if (village.containsLocation(x, z)) {
                    inside++;
                } else {
                    outside++;
                }
                if (inside > 0 && outside > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private PlaceResult validateNotEntirelyInsideDefaultBorderOnlyForBlockCheck(Village village, Location origin,
                                                                                BuildingDefinition def, int radius) {
        VillageBorder border0 = village.getBorderById(0);
        if (border0 == null) {
            return PlaceResult.SUCCESS;
        }
        boolean hasAnnex = village.getBorders().stream().anyMatch(b -> b.getId() != 0);
        if (!hasAnnex) {
            return PlaceResult.SUCCESS;
        }

        for (int x = origin.getBlockX() - radius; x <= origin.getBlockX() + radius; x++) {
            for (int z = origin.getBlockZ() - radius; z <= origin.getBlockZ() + radius; z++) {
                boolean in0 = border0.contains(x, z) || border0.isOnBorder(x, z);
                if (!in0) {
                    return PlaceResult.SUCCESS;
                }
                boolean inOther = false;
                for (VillageBorder b : village.getBorders()) {
                    if (b.getId() == 0) continue;
                    if (b.contains(x, z) || b.isOnBorder(x, z)) {
                        inOther = true;
                        break;
                    }
                }
                if (inOther) {
                    return PlaceResult.SUCCESS;
                }
            }
        }

        return PlaceResult.INSIDE_DEFAULT_BORDER;
    }

    /**
     * Check if a building would require border expansion WITHOUT actually extending it
     */
    private boolean isBorderExpansionNeeded(Village village, Location origin, WorldEditHook.SchematicData schematicData) {
        if (schematicData == null || schematicData.getBlocks().isEmpty()) {
            return false;
        }

        // Count blocks outside existing border
        for (WorldEditHook.SchematicBlock sb : schematicData.getBlocks()) {
            int worldX = origin.getBlockX() + sb.dx();
            int worldZ = origin.getBlockZ() + sb.dz();
            
            if (!village.containsLocation(worldX, worldZ)) {
                return true; // At least one block outside = expansion needed
            }
        }
        return false; // All blocks inside = no expansion needed
    }

    /**
     * Cancel the pending building preview - clear fake blocks.
     */
    public void cancelBuildingPreview(Player player) {
        PendingBuildingConfirmation pending = pendingConfirmations.remove(player.getUniqueId());
        if (pending != null) {
            clearPendingPreview(player, pending);
        }
    }

    public boolean hasPendingConfirmation(UUID playerId) {
        return pendingConfirmations.containsKey(playerId);
    }

    public PendingBuildingConfirmation getPendingConfirmation(UUID playerId) {
        return pendingConfirmations.get(playerId);
    }

    public void setPendingBlockSelection(UUID playerId, PendingBlockSelection selection) {
        pendingBlockSelections.put(playerId, selection);
    }

    public PendingBlockSelection getPendingBlockSelection(UUID playerId) {
        return pendingBlockSelections.get(playerId);
    }

    public void clearPendingBlockSelection(UUID playerId) {
        pendingBlockSelections.remove(playerId);
    }

    public void setLastPlacementDiagnostic(UUID playerId, String message) {
        if (playerId == null) return;
        if (message == null) lastPlacementDiagnostics.remove(playerId);
        else lastPlacementDiagnostics.put(playerId, message);
    }

    public String getLastPlacementDiagnostic(UUID playerId) {
        return playerId == null ? null : lastPlacementDiagnostics.get(playerId);
    }

    public void clearLastPlacementDiagnostic(UUID playerId) {
        if (playerId == null) return;
        lastPlacementDiagnostics.remove(playerId);
    }

    private String findOverlapDiagnostic(Village village, Set<Long> proposedFootprint, Location origin) {
        if (village == null || proposedFootprint == null || proposedFootprint.isEmpty()) return null;
        for (VillageBuilding existing : village.getBuildings()) {
            if (existing == null || existing.getLocation() == null || existing.getLocation().getWorld() == null) continue;
            if (!existing.getLocation().getWorld().equals(origin.getWorld())) continue;
            Set<Long> existingFootprint = collectExistingBuildingFootprint(existing);
            if (existingFootprint == null || existingFootprint.isEmpty()) continue;
            for (Long coord : proposedFootprint) {
                if (existingFootprint.contains(coord)) {
                    String type = existing.getTypeKey();
                    String owner = "unbekannt";
                    if (existing.getOwnerId() != null) {
                        org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(existing.getOwnerId());
                        if (off != null && off.getName() != null) owner = off.getName();
                    }
                    // Try to find border id at building location
                    int borderId = -1;
                    VillageBorder b = village.getBorderAt(existing.getLocation().getBlockX(), existing.getLocation().getBlockZ());
                    if (b != null) borderId = b.getId();
                    return "Überschneidung mit '" + type + "' (ID=" + existing.getId() + ") von " + owner + (borderId >= 0 ? " in Grenze ID=" + borderId : "");
                }
            }
        }
        return null;
    }

    private void clearPendingPreview(Player player, PendingBuildingConfirmation pending) {
        if (pending == null || pending.getPreviewLocations() == null) return;
        World world = player.getWorld();
        for (Location loc : pending.getPreviewLocations()) {
            player.sendBlockChange(loc, world.getBlockAt(loc).getBlockData());
        }
    }

    /**
     * Places a build menu sign in front of the building based on direction.
     */
    private Location placeBuildSign(Location buildingOrigin, String direction, String buildingName) {
        BlockFace signFace;
        int signOffsetX = 0;
        int signOffsetZ = 0;
        switch (direction) {
            case "N" -> { signOffsetZ = -1; signFace = BlockFace.NORTH; }
            case "S" -> { signOffsetZ = 1; signFace = BlockFace.SOUTH; }
            case "E" -> { signOffsetX = 1; signFace = BlockFace.EAST; }
            case "W" -> { signOffsetX = -1; signFace = BlockFace.WEST; }
            default -> { signOffsetZ = -1; signFace = BlockFace.NORTH; }
        }

        Location signLoc = buildingOrigin.clone().add(signOffsetX, 0, signOffsetZ);
        if (!signLoc.getBlock().isPassable()) {
            signLoc.add(0, 1, 0);
        }

        Block signBlock = signLoc.getBlock();
        signBlock.setType(Material.OAK_WALL_SIGN);

        BlockData signData = signBlock.getBlockData();
        if (signData instanceof WallSign wallSign) {
            wallSign.setFacing(signFace);
            signBlock.setBlockData(wallSign);
        }

        if (signBlock.getState() instanceof Sign sign) {
            sign.getSide(Side.FRONT).line(0,
                    net.kyori.adventure.text.Component.text("§6Baustelle"));
            sign.getSide(Side.FRONT).line(1,
                    net.kyori.adventure.text.Component.text(buildingName));
            sign.getSide(Side.FRONT).line(2,
                    net.kyori.adventure.text.Component.text(""));
            sign.getSide(Side.FRONT).line(3,
                    net.kyori.adventure.text.Component.text("[Rechtsklick]"));
            sign.setWaxed(true);
            sign.update();
        }

        return signLoc;
    }

    /**
     * Places a building info sign at a specific location for completed buildings.
     */
    public Location placeBuildingInfoSign(Location location, String buildingName, String direction) {
        Block signBlock = location.getBlock();
        signBlock.setType(Material.OAK_WALL_SIGN);

        BlockFace facing = switch (direction != null ? direction : "N") {
            case "S" -> BlockFace.SOUTH;
            case "E" -> BlockFace.EAST;
            case "W" -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
        BlockData signData = signBlock.getBlockData();
        if (signData instanceof WallSign wallSign) {
            wallSign.setFacing(facing);
            signBlock.setBlockData(wallSign);
        }

        if (signBlock.getState() instanceof Sign sign) {
            sign.getSide(Side.FRONT).line(0,
                    net.kyori.adventure.text.Component.text("§6" + buildingName));
            sign.getSide(Side.FRONT).line(1,
                    net.kyori.adventure.text.Component.text("§aFertig"));
            sign.getSide(Side.FRONT).line(2,
                    net.kyori.adventure.text.Component.text(""));
            sign.getSide(Side.FRONT).line(3,
                    net.kyori.adventure.text.Component.text("[Rechtsklick]"));
            sign.setWaxed(true);
            sign.update();
        }

        return location;
    }

    public Location placeBuildingInfoSignAtFace(Block clickedBlock, BlockFace clickedFace, String buildingName) {
        if (clickedBlock == null || clickedFace == null) return null;
        Block signBlock;
        if (clickedFace == BlockFace.UP) {
            signBlock = clickedBlock.getRelative(BlockFace.UP);
            signBlock.setType(Material.OAK_SIGN);
        } else {
            signBlock = clickedBlock.getRelative(clickedFace);
            signBlock.setType(Material.OAK_WALL_SIGN);
            BlockData signData = signBlock.getBlockData();
            if (signData instanceof WallSign wallSign) {
                wallSign.setFacing(clickedFace);
                signBlock.setBlockData(wallSign);
            }
        }

        if (signBlock.getState() instanceof Sign sign) {
            sign.getSide(Side.FRONT).line(0, net.kyori.adventure.text.Component.text("§6" + buildingName));
            sign.getSide(Side.FRONT).line(1, net.kyori.adventure.text.Component.text("§aFertig"));
            sign.getSide(Side.FRONT).line(2, net.kyori.adventure.text.Component.text(""));
            sign.getSide(Side.FRONT).line(3, net.kyori.adventure.text.Component.text("[Rechtsklick]"));
            sign.setWaxed(true);
            sign.update();
        }
        return signBlock.getLocation();
    }

    private BlockFace determineSignFacing(Block signBlock) {
        // Simple logic: face towards the block with most air space
        Location loc = signBlock.getLocation();
        World world = signBlock.getWorld();

        // Check each direction
        int northSpace = getAirSpace(world, loc.clone().add(0, 0, -1));
        int southSpace = getAirSpace(world, loc.clone().add(0, 0, 1));
        int eastSpace = getAirSpace(world, loc.clone().add(1, 0, 0));
        int westSpace = getAirSpace(world, loc.clone().add(-1, 0, 0));

        // Return direction with most space
        if (northSpace >= southSpace && northSpace >= eastSpace && northSpace >= westSpace) {
            return BlockFace.NORTH;
        } else if (southSpace >= eastSpace && southSpace >= westSpace) {
            return BlockFace.SOUTH;
        } else if (eastSpace >= westSpace) {
            return BlockFace.EAST;
        } else {
            return BlockFace.WEST;
        }
    }

    private int getAirSpace(World world, Location loc) {
        int space = 0;
        for (int y = 0; y < 3; y++) {
            if (world.getBlockAt(loc.clone().add(0, y, 0)).isPassable()) {
                space++;
            }
        }
        return space;
    }

    public VillageConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Removes the build sign from a stored location in the session.
     */
    private void removeBuildSign(BuildingSession session) {
        if (session == null) return;
        
        Location signLoc = session.getSignLocation();
        if (signLoc == null) return;
        
        Block signBlock = signLoc.getBlock();
        if (signBlock.getType().toString().contains("SIGN")) {
            signBlock.setType(Material.AIR);
        }
    }

    /**
     * Removes the build sign from a building location using the direction.
     * DEPRECATED - Use removeBuildSign(session) instead
     */
    @Deprecated
    private void removeBuildSignWithDirection(Location buildingOrigin, String direction) {
        int signOffsetX = 0;
        int signOffsetZ = 0;
        
        switch (direction) {
            case "N" -> signOffsetZ = -1;
            case "S" -> signOffsetZ = 1;
            case "E" -> signOffsetX = 1;
            case "W" -> signOffsetX = -1;
            default -> signOffsetZ = -1;
        }

        Location signLoc = buildingOrigin.clone().add(signOffsetX, 0, signOffsetZ);
        Block signBlock = signLoc.getBlock();
        
        if (signBlock.getType().toString().contains("SIGN")) {
            signBlock.setType(Material.AIR);
        }
    }

    private Location calculateSignLocation(VillageBuilding building) {
        if (building.getSignLocation() != null) {
            return building.getSignLocation().clone();
        }
        int offsetX = 0;
        int offsetZ = 0;
        String direction = building.getDirection();

        switch (direction) {
            case "N" -> offsetZ = -1;
            case "S" -> offsetZ = 1;
            case "E" -> offsetX = 1;
            case "W" -> offsetX = -1;
        }

        Location signLoc = building.getLocation().clone().add(offsetX, 0, offsetZ);
        if (!signLoc.getBlock().isPassable()) {
            signLoc.add(0, 1, 0);
        }
        return signLoc;
    }

    public void removeSignAt(Location signLocation) {
        if (signLocation == null) return;
        Block signBlock = signLocation.getBlock();
        if (signBlock.getType().toString().contains("SIGN")) {
            signBlock.setType(Material.AIR);
        }
    }

    private static final Pattern SIGN_PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_-]+)%");

    public void applyBuildingSignTemplate(Player viewer, VillageBuilding building, VillageConfigManager configManager) {
        if (building == null || building.getSignLocation() == null) return;
        Block block = building.getSignLocation().getBlock();
        if (!(block.getState() instanceof Sign sign)) return;

        String template = building.getSignTemplate();
        if (template == null || template.isBlank()) {
            template = "%building_type%|%owner%|Level %level%|[Rechtsklick]";
        }

        String buildingTypeName = getDisplayName(building.getTypeKey());
        String buildingName = building.hasCustomName() ? building.getCustomName() : buildingTypeName;
        String ownerName = "Unbekannt";
        if (building.getOwnerId() != null) {
            var off = Bukkit.getOfflinePlayer(building.getOwnerId());
            if (off != null && off.getName() != null) ownerName = off.getName();
        }

        String normalized = template.replace("%%", "%");
        StringBuffer buf = new StringBuffer();
        Matcher m = SIGN_PLACEHOLDER.matcher(normalized);
        while (m.find()) {
            String key = m.group(1);
            String value = switch (key) {
                case "building_type" -> buildingTypeName;
                case "building_name" -> buildingName;
                case "owner" -> ownerName;
                case "level" -> String.valueOf(building.getLevel());
                default -> "%" + key + "%";
            };
            m.appendReplacement(buf, Matcher.quoteReplacement(value));
        }
        m.appendTail(buf);
        String resolved = buf.toString();

        if (viewer != null) {
            try {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                resolved = (String) papi.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class)
                        .invoke(null, viewer, resolved);
            } catch (Throwable ignored) {
            }
        }

        String[] lines = resolved.split("\\|", -1);
        for (int i = 0; i < 4; i++) {
            String line = i < lines.length ? lines[i] : "";
            sign.getSide(Side.FRONT).line(i, net.kyori.adventure.text.Component.text(MessageUtil.translateColorCodes(line)));
        }
        sign.setWaxed(true);
        sign.update();
    }

    /**
     * Shows the building sign again after it was hidden. Warns if a non-air block would be replaced.
     */
    public void revealBuildingSign(Player player, Village village, VillageBuilding building) {
        revealBuildingSign(player, village, building, true);
    }

    public boolean wouldReplaceBlockWhenRevealingSign(VillageBuilding building) {
        if (building == null) return false;
        Location target = building.getSignLocation() != null ? building.getSignLocation() : calculateSignLocation(building);
        Material mat = target.getBlock().getType();
        return !mat.isAir() && !mat.name().contains("SIGN");
    }

    public void revealBuildingSign(Player player, Village village, VillageBuilding building, boolean allowReplace) {
        if (building == null || village == null || player == null) return;
        if (!building.isSignHidden()) return;

        String displayName = getDisplayName(building.getTypeKey());
        String direction = building.getDirection() != null ? building.getDirection() : "N";

        Location target = building.getSignLocation() != null ? building.getSignLocation() : calculateSignLocation(building);
        Block block = target.getBlock();
        Material mat = block.getType();
        if (!mat.isAir() && !mat.name().contains("SIGN")) {
            if (!allowReplace) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&eEin Block blockiert das Schild bei &7" + target.getBlockX() + " " + target.getBlockY()
                                + " " + target.getBlockZ() + "&e.");
                return;
            }
            MessageUtil.send(player, configManager.getPrefix(),
                    "&eWarnung: Am Schild-Platz (&7" + target.getBlockX() + " " + target.getBlockY() + " "
                            + target.getBlockZ() + "&e) steht &7" + mat + "&e — wird ersetzt.");
        }

        Location signLoc = target.clone();
        Block signBlock = signLoc.getBlock();
        Block below = signBlock.getRelative(BlockFace.DOWN);
        // Richtung immer bestimmen, unabhängig ob Boden- oder Wandzeichen
        BlockFace signFace = switch (direction) {
            case "S" -> BlockFace.SOUTH;
            case "E" -> BlockFace.EAST;
            case "W" -> BlockFace.WEST;
            default  -> BlockFace.NORTH;
        };
        if (!below.getType().isAir() && below.getType().isSolid()) {
            // Bodenzeichen: Rotation über Sign-BlockData setzen
            signBlock.setType(Material.OAK_SIGN);
            BlockData floorData = signBlock.getBlockData();
            if (floorData instanceof org.bukkit.block.data.type.Sign floorSign) {
                floorSign.setRotation(signFace);
                signBlock.setBlockData(floorSign);
            }
        } else {
            // Wandzeichen: facing setzen
            signBlock.setType(Material.OAK_WALL_SIGN);
            BlockData wallData = signBlock.getBlockData();
            if (wallData instanceof WallSign wallSign) {
                wallSign.setFacing(signFace);
                signBlock.setBlockData(wallSign);
            }
        }
        if (signBlock.getState() instanceof Sign sign) {
            sign.getSide(Side.FRONT).line(0, net.kyori.adventure.text.Component.text("§6Baustelle"));
            sign.getSide(Side.FRONT).line(1, net.kyori.adventure.text.Component.text(displayName));
            sign.getSide(Side.FRONT).line(2, net.kyori.adventure.text.Component.text(""));
            sign.getSide(Side.FRONT).line(3, net.kyori.adventure.text.Component.text("[Rechtsklick]"));
            sign.setWaxed(true);
            sign.update();
        }
        building.setSignHidden(false);
        building.setSignLocation(signLoc);
        refreshBuildingProtectionAndMarker(village, building);
        villageManager.saveVillage(village);
        applyBuildingSignTemplate(player, building, configManager);
        MessageUtil.send(player, configManager.getPrefix(), "&aSchild wieder eingeblendet.");
    }

    public PendingSignRevealConfirmation createPendingSignRevealConfirmation(Player player, Village village, VillageBuilding building) {
        if (player == null || village == null || building == null) return null;
        Location target = calculateSignLocation(building);
        PendingSignRevealConfirmation pending = new PendingSignRevealConfirmation(village.getId(), building.getId(), target);
        pendingSignRevealConfirmations.put(player.getUniqueId(), pending);
        return pending;
    }

    public PendingSignRevealConfirmation getPendingSignRevealConfirmation(UUID playerId) {
        return pendingSignRevealConfirmations.get(playerId);
    }

    public boolean resolvePendingSignReveal(Player player, boolean approve) {
        PendingSignRevealConfirmation pending = pendingSignRevealConfirmations.remove(player.getUniqueId());
        if (pending == null) return false;
        if (!approve) return true;
        Village village = villageManager.getVillage(pending.villageId()).orElse(null);
        if (village == null) return false;
        VillageBuilding building = village.getBuildings().stream()
                .filter(b -> b.getId().equals(pending.buildingId()))
                .findFirst().orElse(null);
        if (building == null) return false;
        revealBuildingSign(player, village, building, true);
        return true;
    }

    private VillageBuilding findBuilding(Village village, UUID buildingId) {
        return village.getBuildings().stream()
                .filter(b -> b.getId().equals(buildingId))
                .findFirst().orElse(null);
    }

    public boolean removeCompletedBuilding(Player player, Village village, UUID buildingId) {
        VillageBuilding building = findBuilding(village, buildingId);
        if (building == null) {
            return false;
        }

        removeSignAt(building.getSignLocation());
        if (worldGuardHook.isAvailable()) {
            villageManager.ensureBuildingTypeOrdinals(village);
            worldGuardHook.removeBuildingRegion(village, building);
        }
        if (blueMapHook != null && blueMapHook.isAvailable()) {
            blueMapHook.removeBuildingMarker(village, building);
        }
        village.removeBuilding(buildingId);
        double refundBase = getGlobalBuildCost(building.getTypeKey());
        if (refundBase > 0 && economyService.isAvailable()) {
            double refund = refundBase * 0.5;
            if (refund > 0) {
                economyService.deposit(player, refund);
            }
        }
        villageManager.saveVillage(village);
        return true;
    }

    public boolean moveBuildingSign(Player player, Village village, UUID buildingId) {
        VillageBuilding building = findBuilding(village, buildingId);
        if (building == null) {
            return false;
        }

        // Remove old sign
        if (building.getSignLocation() != null) {
            removeSignAt(building.getSignLocation());
            building.setSignLocation(null);
            building.setSignHidden(false);
            villageManager.saveVillage(village);
        }

        // Set pending block selection for interactive placement
        PendingBlockSelection selection = new PendingBlockSelection(
                player.getUniqueId(),
                PendingBlockSelection.SelectionType.MOVE_BUILDING_SIGN,
                buildingId
        );
        setPendingBlockSelection(player.getUniqueId(), selection);

        // Drop the sign item
        dropBuildingSignItem(player.getLocation(), building, getDisplayName(building.getTypeKey()));
        
        MessageUtil.send(player, configManager.getPrefix(), 
                "&aDorf-Schild wurde gedroppt! &7Platziere es dort, wo du möchtest.");
        return true;
    }

    /**
     * Drops a buildable sign item for the player to place.
     */
    private void dropBuildingSignItem(Location dropLocation, VillageBuilding building, String displayName) {
        ItemStack signItem = new ItemStack(Material.OAK_WALL_SIGN);
        ItemMeta meta = signItem.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&6&l" + displayName + " (Marker)"));
            List<String> lore = new ArrayList<>();
            lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7Gebäude: &e" + displayName));
            lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7Klicke zum Platzieren"));
            meta.setLore(lore);
            signItem.setItemMeta(meta);
        }
        
        org.bukkit.entity.Item droppedItem = dropLocation.getWorld().dropItem(dropLocation, signItem);
        droppedItem.setPickupDelay(0); // Spieler kann sofort aufsammeln
    }

    public boolean upgradeBuilding(Player player, Village village, UUID buildingId) {
        VillageBuilding building = findBuilding(village, buildingId);
        if (building == null || !building.isCompleted()) {
            return false;
        }

        BuildingDefinition def = getDefinition(building.getTypeKey());
        BuildingType type = getLegacyType(building.getTypeKey());
        int maxLevel = def != null ? def.getMaxUpgradeTier() : (type != null ? type.getMaxLevel() : 1);
        if (building.getLevel() >= maxLevel) {
            return false;
        }
        double cost;
        double localCost;
        int pointsCost;
        if (def != null) {
            BuildingDefinition.UpgradeTier next = def.getUpgradeTier(building.getLevel() + 1);
            if (next == null) return false;
            if (village.getLevel() < next.getRequiredVillageLevel()) return false;
            if (!player.hasPermission(next.getPermission())) return false;
            cost = next.getBuildMoneyGlobal();
            localCost = next.getBuildMoneyLocal();
            pointsCost = 0;
        } else if (type != null) {
            if (village.getLevel() < type.getUpgradeRequiredVillageLevel()) {
                return false;
            }
            String unlockKey = type.getUpgradeRequiredUnlockKey();
            if (unlockKey != null && !unlockKey.isBlank() && village.getUpgradeLevel(unlockKey) <= 0) {
                return false;
            }
            cost = type.getUpgradeCostPerLevel() * (building.getLevel());
            localCost = type.getUpgradeLocalCostPerLevel() * (building.getLevel());
            pointsCost = type.getUpgradePointsPerLevel() * (building.getLevel());
        } else {
            return false;
        }

        if (village.getPoints() < pointsCost) {
            return false;
        }

        if (cost > 0) {
            if (!economyService.canAfford(player, cost)) {
                return false;
            }
            economyService.withdraw(player, cost);
        }
        if (localCost > 0) {
            String localCurrencyId = currencyService.getVillageCurrencyId(village);
            if (currencyService.getBalance(player.getUniqueId(), localCurrencyId) < localCost) {
                if (cost > 0) economyService.deposit(player, cost);
                return false;
            }
            if (!currencyService.removeBalance(player.getUniqueId(), localCurrencyId, localCost)) {
                if (cost > 0) economyService.deposit(player, cost);
                return false;
            }
        }

        village.setPoints(village.getPoints() - pointsCost);
        building.setLevel(building.getLevel() + 1);
        // Zuerst speichern – Upgrade ist damit dauerhaft gesichert,
        // unabhängig davon ob der Marker-Update danach fehlschlägt.
        villageManager.saveVillage(village);
        try {
            refreshBuildingProtectionAndMarker(village, building);
            if (def != null && def.isPath()) {
                PathService pathService = plugin.getPathService();
                if (pathService != null) {
                    pathService.refreshZoneForBuilding(building, def);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[BuildingService] Marker-Update nach Upgrade fehlgeschlagen " +
                    "(Schematic evtl. nicht vorhanden): " + e.getMessage());
        }
        executeUpgradeTierEffects(player, village, building, def != null ? def.getUpgradeTier(building.getLevel()) : null);
        return true;
    }

    private void executeUpgradeTierEffects(Player player, Village village, VillageBuilding building,
                                           BuildingDefinition.UpgradeTier tier) {
        if (tier == null) return;

        String achievement = tier.getChange("achievement", (String) null);
        if (achievement != null && !achievement.isBlank()) {
            MessageUtil.send(player, configManager.getPrefix(), achievement);
        }

        Object rawCommands = tier.getChange("commands", (Object) null);
        if (rawCommands == null) return;

        List<String> commands = parseUpgradeCommands(rawCommands);
        for (String command : commands) {
            if (command == null || command.isBlank()) continue;
            command = applyUpgradePlaceholders(command.trim(), player, village, building, tier);
            if (command.isBlank()) continue;
            if (command.startsWith("player:")) {
                Bukkit.dispatchCommand(player, command.substring("player:".length()).trim());
            } else if (command.startsWith("console:")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.substring("console:".length()).trim());
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }
    }

    private List<String> parseUpgradeCommands(Object rawCommands) {
        if (rawCommands instanceof String) {
            return List.of((String) rawCommands);
        }
        if (rawCommands instanceof List<?>) {
            List<String> commands = new ArrayList<>();
            for (Object item : (List<?>) rawCommands) {
                if (item instanceof String s) commands.add(s);
            }
            return commands;
        }
        return List.of();
    }

    private String applyUpgradePlaceholders(String command, Player player, Village village,
                                            VillageBuilding building, BuildingDefinition.UpgradeTier tier) {
        return command
            .replace("%player%", player.getName())
            .replace("%village%", village.getName())
            .replace("%building%", getDisplayName(building.getTypeKey()))
            .replace("%level%", String.valueOf(building.getLevel()))
            .replace("%old_level%", String.valueOf(building.getLevel() - 1))
            .replace("%new_level%", String.valueOf(building.getLevel()))
            .replace("%world%", player.getWorld().getName())
            .replace("%x%", String.valueOf(building.getLocation().getBlockX()))
            .replace("%y%", String.valueOf(building.getLocation().getBlockY()))
            .replace("%z%", String.valueOf(building.getLocation().getBlockZ()));
    }

    public void refreshBuildingProtectionAndMarker(Village village, VillageBuilding building) {
        WorldEditHook.SchematicData data = loadSchematicDataForBuilding(building);
        updateBuildingProtectionAndMarker(village, building, data);
    }

    /** Startet einen einfachen Konstruktion-Flow für ein bestehenden, nicht fertigen Gebäude. */
    public boolean startConstruction(Player player, Village village, UUID buildingId) {
        VillageBuilding building = findBuilding(village, buildingId);
        if (building == null || building.isCompleted() || activeConstructions.containsKey(buildingId)) return false;

        BuildingDefinition def = getDefinition(building.getTypeKey());
        // Kosten prüfen (vereinfachte Variante: nur Geld + items aus buildItems)
        double globalCost = def != null ? def.getBuildMoneyGlobal() : getGlobalBuildCost(building.getTypeKey());
        double localCost = def != null ? def.getBuildMoneyLocal() : getLocalBuildCost(building.getTypeKey());

        if (globalCost > 0 && !economyService.canAfford(player, globalCost)) return false;
        if (localCost > 0) {
            String localCurrency = currencyService.getVillageCurrencyId(village);
            if (currencyService.getBalance(player.getUniqueId(), localCurrency) < localCost) return false;
        }

        // Items check
        Map<org.bukkit.Material, Integer> items = def != null ? def.getBuildItems() : java.util.Collections.emptyMap();
        for (var e : items.entrySet()) {
            int need = e.getValue();
            if (!hasPlayerOrVillageItems(player, village, e.getKey(), need)) {
                return false;
            }
        }

        // Abbuchungen
        if (globalCost > 0) economyService.withdraw(player, globalCost);
        if (localCost > 0) currencyService.removeBalance(player.getUniqueId(), currencyService.getVillageCurrencyId(village), localCost);
        for (var e : items.entrySet()) removePlayerOrVillageItems(player, village, e.getKey(), e.getValue());

        // Task erstellen
        ConstructionTask task = new ConstructionTask(building, village, player, 20 * 10); // 10s
        activeConstructions.put(building.getId(), task);
        task.start();
        MessageUtil.send(player, configManager.getPrefix(), "&aKonstruktion gestartet: &e" + getDisplayName(building.getTypeKey()));
        return true;
    }

    public boolean isUnderConstruction(UUID buildingId) {
        return activeConstructions.containsKey(buildingId);
    }

    public boolean cancelConstruction(UUID buildingId) {
        ConstructionTask t = activeConstructions.remove(buildingId);
        if (t == null) return false;
        t.cancel();
        return true;
    }

    private boolean hasPlayerOrVillageItems(Player player, Village village, org.bukkit.Material mat, int need) {
        // Vereinfachung: nur im Inventar des Spielers prüfen
        int found = 0;
        for (ItemStack is : player.getInventory().getContents()) if (is != null && is.getType() == mat) found += is.getAmount();
        return found >= need;
    }

    private void removePlayerOrVillageItems(Player player, Village village, org.bukkit.Material mat, int amount) {
        int rem = amount;
        for (int i = 0; i < player.getInventory().getSize() && rem > 0; i++) {
            ItemStack is = player.getInventory().getItem(i);
            if (is == null) continue;
            if (is.getType() != mat) continue;
            int take = Math.min(is.getAmount(), rem);
            is.setAmount(is.getAmount() - take);
            if (is.getAmount() <= 0) player.getInventory().setItem(i, null);
            rem -= take;
        }
        player.updateInventory();
    }

    private final class ConstructionTask {
        private final VillageBuilding building;
        private final Village village;
        private final Player player;
        private final int totalTicks;
        private org.bukkit.scheduler.BukkitTask task;

        ConstructionTask(VillageBuilding building, Village village, Player player, int totalTicks) {
            this.building = building;
            this.village = village;
            this.player = player;
            this.totalTicks = totalTicks;
        }

        void start() {
            org.bukkit.scheduler.BukkitRunnable r = new org.bukkit.scheduler.BukkitRunnable() {
                int elapsed = 0;
                @Override public void run() {
                    elapsed += 20;
                    if (elapsed >= totalTicks) {
                        try {
                            building.setCompleted(true);
                            BuildingService.this.villageManager.saveVillage(village);
                            BuildingService.this.refreshBuildingProtectionAndMarker(village, building);
                            BuildingService.this.activeConstructions.remove(building.getId());
                            MessageUtil.send(player, configManager.getPrefix(), "&aKonstruktion abgeschlossen: &e" + BuildingService.this.getDisplayName(building.getTypeKey()));
                            plugin.getLogger().info("[BuildingService] Konstruktion fertig: " + building.getId());
                        } catch (Exception e) {
                            plugin.getLogger().warning("Construction finish failed: " + e.getMessage());
                        }
                        cancel();
                    }
                }
            };
            this.task = r.runTaskTimer(plugin, 0L, 20L);
        }

        void cancel() {
            if (this.task != null) this.task.cancel();
            BuildingService.this.activeConstructions.remove(building.getId());
            MessageUtil.send(player, configManager.getPrefix(), "&cKonstruktion abgebrochen: &e" + BuildingService.this.getDisplayName(building.getTypeKey()));
        }
    }

    private void updateBuildingProtectionAndMarker(Village village, VillageBuilding building,
                                                   WorldEditHook.SchematicData schematicData) {
        if (worldGuardHook.isAvailable() && schematicData != null) {
            worldGuardHook.createBuildingRegion(village, building, building.getLocation(), schematicData);
            worldGuardHook.updateBuildingRegionAccess(village, building);
        }
        // schematicData-Check verhindert NPE in BlueMapHook wenn keine .schem-Datei vorhanden
        if (blueMapHook != null && blueMapHook.isAvailable() && schematicData != null) {
            blueMapHook.syncBuildingMarker(village, building, getLegacyType(building.getTypeKey()), schematicData);
        }
    }

    private WorldEditHook.SchematicData loadSchematicDataForBuilding(VillageBuilding building) {
        String schematicName = getSchematicName(building.getTypeKey(), building.getSchematicName());
        if (schematicName == null || schematicName.isBlank()) return null;
        File schematicFile = new File(plugin.getDataFolder(), "schematics/" + schematicName);
        if (!schematicFile.exists()) return null;
        return worldEditHook.loadAndRotateSchematicData(schematicFile, building.getDirection());
    }

    public VillageBorder getPreviewBorderForBuilding(VillageBuilding building) {
        if (building == null || building.getLocation() == null) return null;

        WorldEditHook.SchematicData schematicData = loadSchematicDataForBuilding(building);
        if (schematicData == null || schematicData.getBlocks().isEmpty()) return null;

        List<int[]> corners = calculateBuildingBoundary(building.getLocation(), schematicData);
        if (corners.isEmpty()) return null;

        int minX = corners.get(0)[0] - 1;
        int maxX = corners.get(2)[0] + 1;
        int minZ = corners.get(0)[1] - 1;
        int maxZ = corners.get(2)[1] + 1;

        return new VillageBorder(List.of(
                new int[]{minX, minZ},
                new int[]{maxX, minZ},
                new int[]{maxX, maxZ},
                new int[]{minX, maxZ}
        ), 0, 256);
    }

    /**
     * Calculates the direction from the building to the player.
     * Returns N, S, E, or W based on the relative position.
     */
    private String calculateDirectionTowardsPlayer(Player player, Location buildingLoc) {
        double playerX = player.getLocation().getX();
        double playerZ = player.getLocation().getZ();
        double buildingX = buildingLoc.getX();
        double buildingZ = buildingLoc.getZ();

        double dx = playerX - buildingX;
        double dz = playerZ - buildingZ;

        // Determine which direction the player is from the building
        double absDx = Math.abs(dx);
        double absDz = Math.abs(dz);

        if (absDz > absDx) {
            // Player is more north or south
            return dz < 0 ? "N" : "S";
        } else {
            // Player is more east or west
            return dx < 0 ? "W" : "E";
        }
    }

    /**
     * Extends the village border if any building blocks fall outside the current boundary.
     * Creates a new expanded border to include all building blocks (expanded by 1 block).
     */
    /**
     * Extends the village border if the building extends OUTSIDE the current border.
     * - If building is COMPLETELY OUTSIDE: Creates a new bounding box that encompasses the building
     * - If building has AT LEAST 1 BLOCK INSIDE: Only adds the building boundary to the existing border
     * 
     * Returns true if border was extended, false if building was completely within existing border.
     */
    private boolean extendVillageBorderIfNeeded(Village village, Location origin, WorldEditHook.SchematicData schematicData) {
        if (schematicData == null || schematicData.getBlocks().isEmpty()) {
            return false;
        }

        // Expand ONLY enough to include the blocks that are currently outside the territory.
        // For non-rectangular (walk-drawn) borders, a rectangle can easily overlap existing territory.
        // So we build a "patch polygon" around the outside blocks and trace its boundary.

        Set<Long> filled = new HashSet<>();
        int outsideCount = 0;

        for (WorldEditHook.SchematicBlock sb : schematicData.getBlocks()) {
            int worldX = origin.getBlockX() + sb.dx();
            int worldZ = origin.getBlockZ() + sb.dz();
            if (village.containsLocation(worldX, worldZ)) {
                continue;
            }

            outsideCount++;

            // Add a 1-block padding around outside blocks so the new border fully contains the building
            // while staying strictly outside existing territory.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = worldX + dx;
                    int z = worldZ + dz;
                    if (village.containsLocation(x, z) || village.isOnAnyBorder(x, z)) {
                        continue;
                    }
                    filled.add(packXZ(x, z));
                }
            }
        }

        if (outsideCount == 0 || filled.isEmpty()) {
            return false;
        }

        List<int[]> boundaryLoop = traceOuterBoundary(filled, 50000);
        if (boundaryLoop == null || boundaryLoop.size() < 3) {
            return false;
        }

        VillageBorder base = village.getBorder();
        VillageBorder newBorder = new VillageBorder(boundaryLoop, base.getMinY(), base.getMaxY());

        // Ensure the new border is connected to existing territory (touching is enough).
        if (!isConnectedToExistingTerritory(village, newBorder)) {
            return false;
        }

        village.addBorder(newBorder);
        return true;
    }

    private boolean isConnectedToExistingTerritory(Village village, VillageBorder border) {
        for (int[] edge : border.getEdgeBlocks()) {
            int x = edge[0];
            int z = edge[1];
            if (village.containsLocation(x, z)
                    || village.containsLocation(x + 1, z)
                    || village.containsLocation(x - 1, z)
                    || village.containsLocation(x, z + 1)
                    || village.containsLocation(x, z - 1)
                    || village.isOnAnyBorder(x, z)
                    || village.isOnAnyBorder(x + 1, z)
                    || village.isOnAnyBorder(x - 1, z)
                    || village.isOnAnyBorder(x, z + 1)
                    || village.isOnAnyBorder(x, z - 1)) {
                return true;
            }
        }
        return false;
    }

    private long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private List<int[]> traceOuterBoundary(Set<Long> filled, int maxSteps) {
        if (filled == null || filled.isEmpty()) return null;

        // Pick a boundary start cell (min x, then min z) that has an outside neighbor.
        boolean found = false;
        int startX = 0, startZ = 0;
        for (long k : filled) {
            int x = (int) (k >> 32);
            int z = (int) k;
            if (!isBoundaryCell(filled, x, z)) continue;
            if (!found || x < startX || (x == startX && z < startZ)) {
                startX = x;
                startZ = z;
                found = true;
            }
        }
        if (!found) return null;

        int[][] dirs = new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        int dir = 0; // east

        List<int[]> loop = new ArrayList<>();
        int cx = startX;
        int cz = startZ;
        loop.add(new int[]{cx, cz});

        int steps = 0;
        while (steps++ < maxSteps) {
            boolean moved = false;
            for (int i = 0; i < 4; i++) {
                int ndir = (dir + 3 + i) % 4; // right, straight, left, back
                int nx = cx + dirs[ndir][0];
                int nz = cz + dirs[ndir][1];
                if (filled.contains(packXZ(nx, nz)) && isBoundaryCell(filled, nx, nz)) {
                    cx = nx;
                    cz = nz;
                    dir = ndir;
                    loop.add(new int[]{cx, cz});
                    moved = true;
                    break;
                }
            }
            if (!moved) return null;
            if (cx == startX && cz == startZ && loop.size() > 4) {
                return loop;
            }
        }
        return null;
    }

    private boolean isBoundaryCell(Set<Long> filled, int x, int z) {
        if (!filled.contains(packXZ(x, z))) return false;
        return !filled.contains(packXZ(x + 1, z))
                || !filled.contains(packXZ(x - 1, z))
                || !filled.contains(packXZ(x, z + 1))
                || !filled.contains(packXZ(x, z - 1));
    }

    /**
     * Calculates the actual boundary of a building based on its schematic blocks.
     * Finds min/max X and Z coordinates from ALL blocks in the schematic.
     * Returns corner points: [minX, minZ], [maxX, minZ], [maxX, maxZ], [minX, maxZ]
     */
    private List<int[]> calculateBuildingBoundary(Location origin, WorldEditHook.SchematicData schematicData) {
        if (schematicData == null || schematicData.getBlocks().isEmpty()) {
            return new ArrayList<>();
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        // Find min/max from all schematic blocks
        for (WorldEditHook.SchematicBlock sb : schematicData.getBlocks()) {
            int worldX = origin.getBlockX() + sb.dx();
            int worldZ = origin.getBlockZ() + sb.dz();
            
            minX = Math.min(minX, worldX);
            maxX = Math.max(maxX, worldX);
            minZ = Math.min(minZ, worldZ);
            maxZ = Math.max(maxZ, worldZ);
        }

        // Create corner points
        List<int[]> corners = new ArrayList<>();
        corners.add(new int[]{minX, minZ});
        corners.add(new int[]{maxX, minZ});
        corners.add(new int[]{maxX, maxZ});
        corners.add(new int[]{minX, maxZ});
        
        return corners;
    }

    /**
     * Validates if building placement is within village boundary.
     * Returns error if completely outside, shows warning if partially outside.
     */
    private PlaceResult validateBuildingBoundary(Village village, Location origin, WorldEditHook.SchematicData schematicData) {
        if (schematicData == null || schematicData.getBlocks().isEmpty()) {
            return PlaceResult.SUCCESS;
        }

        Location bellLocation = village.getBellLocation();
        int maxRadius = village.getMaxBuildRadius(configManager.getMaxBuildRadius(), configManager.getUpgradeRadiusPerLevel());
        int maxRadiusSquared = maxRadius * maxRadius;
        Set<Long> proposedFootprint = collectSchematicFootprint(origin, schematicData);

        if (intersectsExistingBuilding(village, proposedFootprint, origin)) {
            return PlaceResult.OVERLAPS_BUILDING;
        }

        int blocksInside = 0;
        int blocksOutside = 0;
        int blocksOutsideRadius = 0;

        for (WorldEditHook.SchematicBlock sb : schematicData.getBlocks()) {
            int worldX = origin.getBlockX() + sb.dx();
            int worldZ = origin.getBlockZ() + sb.dz();

            if (isWithinStartBoundary(village, worldX, worldZ)) {
                return PlaceResult.ON_START_BORDER;
            }
            // NEU: Prüfen ob Schematic-Block auf einer Dorfgrenze liegt
            if (village.isOnAnyBorder(worldX, worldZ)) {
                return PlaceResult.PARTIAL_OUTSIDE_BORDER;
            }

            double distanceSquared = Math.pow(worldX - bellLocation.getBlockX(), 2) + Math.pow(worldZ - bellLocation.getBlockZ(), 2);
            if (distanceSquared > maxRadiusSquared) {
                blocksOutsideRadius++;
            }

            if (village.containsLocation(worldX, worldZ)) {
                blocksInside++;
            } else {
                blocksOutside++;
            }
        }

        if (blocksInside == 0) {
            return PlaceResult.OUTSIDE_BORDER;
        }

        if (blocksOutsideRadius > 0) {
            return PlaceResult.BUILDING_TOO_FAR;
        }

        PlaceResult defaultBorder = validateNotEntirelyInsideDefaultBorderOnly(village, origin, schematicData);
        if (defaultBorder != PlaceResult.SUCCESS) {
            return defaultBorder;
        }

        return PlaceResult.SUCCESS;
    }

    /**
     * Rejects placement if the footprint lies entirely inside border id 0 while the village
     * already has annex borders (id != 0): new buildings must use expanded territory.
     */
    private PlaceResult validateNotEntirelyInsideDefaultBorderOnly(Village village, Location origin,
                                                                   WorldEditHook.SchematicData schematicData) {
        VillageBorder border0 = village.getBorderById(0);
        if (border0 == null) {
            return PlaceResult.SUCCESS;
        }
        boolean hasAnnex = village.getBorders().stream().anyMatch(b -> b.getId() != 0);
        if (!hasAnnex) {
            return PlaceResult.SUCCESS;
        }

        for (WorldEditHook.SchematicBlock sb : schematicData.getBlocks()) {
            int worldX = origin.getBlockX() + sb.dx();
            int worldZ = origin.getBlockZ() + sb.dz();

            boolean in0 = border0.contains(worldX, worldZ) || border0.isOnBorder(worldX, worldZ);
            if (!in0) {
                return PlaceResult.SUCCESS;
            }
            boolean inOther = false;
            for (VillageBorder b : village.getBorders()) {
                if (b.getId() == 0) continue;
                if (b.contains(worldX, worldZ) || b.isOnBorder(worldX, worldZ)) {
                    inOther = true;
                    break;
                }
            }
            if (inOther) {
                return PlaceResult.SUCCESS;
            }
        }

        return PlaceResult.INSIDE_DEFAULT_BORDER;
    }

    /**
     * Shows the building boundary using Gold Ore at the edge blocks.
     * - Boundary is OUTSIDE the building (expanded by 1 block in X/Z)
     * - Y height is determined by the highest block at each X/Z coordinate (like village border)
     */
    public void showBuildingBoundary(Player player) {
        BuildingSession session = activeSessions.get(player.getUniqueId());
        if (session == null || session.getSchematicData() == null) return;

        Location origin = session.getBuilding().getLocation();
        World world = player.getWorld();

        // Clear old border
        for (Location oldLoc : session.getBorderVisualizationBlocks()) {
            if (oldLoc.getWorld().equals(world)) {
                player.sendBlockChange(oldLoc, world.getBlockAt(oldLoc).getBlockData());
            }
        }
        session.getBorderVisualizationBlocks().clear();

        // Calculate boundary corners (min/max of actual blocks)
        List<int[]> corners = calculateBuildingBoundary(origin, session.getSchematicData());
        if (corners.isEmpty()) return;

        // Expand boundary by 1 block in X/Z directions (boundary OUTSIDE the building)
        int minX = corners.get(0)[0] - 1;
        int maxX = corners.get(2)[0] + 1;
        int minZ = corners.get(0)[1] - 1;
        int maxZ = corners.get(2)[1] + 1;

        Material borderMaterial = Material.GOLD_ORE;
        BlockData borderData = borderMaterial.createBlockData();

        Set<String> seen = new HashSet<>();
        
        // Draw the boundary outline at the highest block Y for each XZ coordinate
        // Top edge (minZ)
        for (int x = minX; x <= maxX; x++) {
            int y = world.getHighestBlockYAt(x, minZ);
            String key = x + "," + y + "," + minZ;
            if (!seen.contains(key)) {
                seen.add(key);
                Location loc = new Location(world, x, y, minZ);
                player.sendBlockChange(loc, borderData);
                session.getBorderVisualizationBlocks().add(loc);
            }
        }

        // Bottom edge (maxZ)
        for (int x = minX; x <= maxX; x++) {
            int y = world.getHighestBlockYAt(x, maxZ);
            String key = x + "," + y + "," + maxZ;
            if (!seen.contains(key)) {
                seen.add(key);
                Location loc = new Location(world, x, y, maxZ);
                player.sendBlockChange(loc, borderData);
                session.getBorderVisualizationBlocks().add(loc);
            }
        }

        // Left edge (minX)
        for (int z = minZ; z <= maxZ; z++) {
            int y = world.getHighestBlockYAt(minX, z);
            String key = minX + "," + y + "," + z;
            if (!seen.contains(key)) {
                seen.add(key);
                Location loc = new Location(world, minX, y, z);
                player.sendBlockChange(loc, borderData);
                session.getBorderVisualizationBlocks().add(loc);
            }
        }

        // Right edge (maxX)
        for (int z = minZ; z <= maxZ; z++) {
            int y = world.getHighestBlockYAt(maxX, z);
            String key = maxX + "," + y + "," + z;
            if (!seen.contains(key)) {
                seen.add(key);
                Location loc = new Location(world, maxX, y, z);
                player.sendBlockChange(loc, borderData);
                session.getBorderVisualizationBlocks().add(loc);
            }
        }
    }

    // --- Existing building session methods ---

    public PlaceResult startBuildingPlacement(Player player, Village village, String buildingTypeKey,
                                               Location location) {
        return startBuildingPlacement(player, village, buildingTypeKey, location, false);
    }

    public PlaceResult startBuildingPlacement(Player player, Village village, String buildingTypeKey,
                                               Location location, boolean skipBorderCheck) {
        String schematicName = getSchematicName(buildingTypeKey);
        if (schematicName == null || schematicName.isBlank()) return PlaceResult.UNKNOWN_TYPE;

        if (village.getLevel() < getRequiredVillageLevel(buildingTypeKey)) return PlaceResult.LEVEL_TOO_LOW;

        String requiredUpgrade = getRequiredUpgradeKey(buildingTypeKey);
        if (requiredUpgrade != null && !village.getUpgrades().containsKey(requiredUpgrade)) {
            return PlaceResult.UPGRADE_REQUIRED;
        }

        // ALWAYS check border - at least one block must be inside
        if (!skipBorderCheck && !village.containsLocation(location)) return PlaceResult.OUTSIDE_BORDER;

        // Check if an ACTIVE (non-paused) building already exists for this player
        if (hasActiveBuilding(player)) return PlaceResult.ALREADY_BUILDING;

        double globalCost = getGlobalBuildCost(buildingTypeKey);
        if (globalCost > 0) {
            if (!economyService.canAfford(player, globalCost)) return PlaceResult.NOT_ENOUGH_MONEY;
            economyService.withdraw(player, globalCost);
        }
        String localCurrencyId = currencyService.getVillageCurrencyId(village);
        double localCost = getLocalBuildCost(buildingTypeKey);
        if (localCost > 0) {
            if (currencyService.getBalance(player.getUniqueId(), localCurrencyId) < localCost) {
                return PlaceResult.NOT_ENOUGH_MONEY;
            }
            if (!currencyService.removeBalance(player.getUniqueId(), localCurrencyId, localCost)) {
                return PlaceResult.NOT_ENOUGH_MONEY;
            }
        }

        villageManager.ensureBuildingTypeOrdinals(village);
        int nextOrdinal = village.getBuildings().stream()
                .filter(b -> buildingTypeKey.equals(b.getTypeKey()))
                .mapToInt(VillageBuilding::getTypeOrdinal)
                .max()
                .orElse(0) + 1;

        UUID buildingId = UUID.randomUUID();
        VillageBuilding building = new VillageBuilding(buildingId, buildingTypeKey, location);
        building.setTypeOrdinal(nextOrdinal);
        village.addBuilding(building);

        // Load schematic for validation
        Map<int[], Material> schematic = new HashMap<>();
        WorldEditHook.SchematicData schematicData = null;
        if (worldEditHook.isAvailable()) {
            File schematicFile = new File(plugin.getDataFolder(), "schematics/" + schematicName);
            schematic = worldEditHook.loadSchematic(schematicFile);
            schematicData = worldEditHook.loadAndRotateSchematicData(schematicFile, "N");
        }
        
        if (schematicData != null) {
            PlaceResult boundary = validateBuildingBoundary(village, location, schematicData);
            if (boundary != PlaceResult.SUCCESS) {
                if (globalCost > 0) economyService.deposit(player, globalCost);
                if (localCost > 0) currencyService.addBalance(player.getUniqueId(), localCurrencyId, localCost);
                village.removeBuilding(building.getId());
                return boundary;
            }
        }

        updateBuildingProtectionAndMarker(village, building, schematicData);

        // Create preview locations
        List<Location> previewLocations = new ArrayList<>();
        if (schematicData != null) {
            for (WorldEditHook.SchematicBlock sb : schematicData.getBlocks()) {
                Location blockLoc = location.clone().add(sb.dx(), sb.dy(), sb.dz());
                previewLocations.add(blockLoc);
            }
        }

        // Set pending confirmation for placement
        PendingBuildingConfirmation confirmation = new PendingBuildingConfirmation(village, buildingTypeKey, "N", location, schematicData, previewLocations, schematicName);
        pendingConfirmations.put(player.getUniqueId(), confirmation);

        // Show preview
        showBuildingPreviewForConfirmation(player, confirmation);

        villageManager.saveVillage(village);

        return PlaceResult.SUCCESS;
    }

    public void showBuildingPreview(Player player) {
        BuildingSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        Location origin = session.getBuilding().getLocation();

        // Clear the tracking set for new preview
        session.getVisualizedBlockLocations().clear();

        // Use SchematicData if available (has correct rotated BlockData)
        if (session.getSchematicData() != null) {
            for (WorldEditHook.SchematicBlock sb : session.getSchematicData().getBlocks()) {
                Location loc = origin.clone().add(sb.dx(), sb.dy(), sb.dz());
                // Only visualize if the actual block is AIR (don't overwrite built blocks)
                if (loc.getBlock().getType() == Material.AIR) {
                    player.sendBlockChange(loc, sb.blockData());
                    // Track this location as visualized
                    session.getVisualizedBlockLocations().add(locToString(loc));
                }
            }
        } else {
            // Fallback: use material schematic map
            if (session.getSchematic().isEmpty()) return;
            for (Map.Entry<int[], Material> entry : session.getSchematic().entrySet()) {
                int[] offset = entry.getKey();
                Material mat = entry.getValue();
                if (mat == Material.AIR) continue;

                Location loc = origin.clone().add(offset[0], offset[1], offset[2]);
                // Only visualize if the actual block is AIR
                if (loc.getBlock().getType() == Material.AIR) {
                    BlockData blockData = mat.createBlockData();
                    player.sendBlockChange(loc, blockData);
                    session.getVisualizedBlockLocations().add(locToString(loc));
                }
            }
        }
    }

    public void showBuildingPreviewForConfirmation(Player player, PendingBuildingConfirmation confirmation) {
        Location origin = confirmation.getLocation();

        // Use SchematicData if available
        if (confirmation.getSchematicData() != null) {
            for (WorldEditHook.SchematicBlock sb : confirmation.getSchematicData().getBlocks()) {
                Location loc = origin.clone().add(sb.dx(), sb.dy(), sb.dz());
                // Only visualize if the actual block is AIR
                if (loc.getBlock().getType() == Material.AIR) {
                    player.sendBlockChange(loc, sb.blockData());
                }
            }
        }
    }

    /**
     * DISABLED: Border visualization is no longer shown.
     * Commenting out the entire method implementation.
     */
    /*
    public void showBuildingBorder(Player player) {
        BuildingSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        if (session.getSchematicData() == null) return;

        Location origin = session.getBuilding().getLocation();
        String direction = session.getBuilding().getDirection();
        World world = player.getWorld();
        
        int width = session.getSchematicData().getWidth();
        int depth = session.getSchematicData().getDepth();
        
        // Get the origin offset (where the player saved the schematic from)
        int originOffsetX = session.getSchematicData().getOriginOffsetX();
        int originOffsetZ = session.getSchematicData().getOriginOffsetZ();

        // Clear old border visualization first
        for (Location oldLoc : session.getBorderVisualizationBlocks()) {
            if (oldLoc.getWorld().equals(world)) {
                player.sendBlockChange(oldLoc, world.getBlockAt(oldLoc).getBlockData());
            }
        }
        session.getBorderVisualizationBlocks().clear();

        // Define border points in unrotated schematic space (perimeter)
        // Adjusted for the origin offset
        List<int[]> borderPoints = new ArrayList<>();
        borderPoints.add(new int[]{-originOffsetX, -originOffsetZ});
        borderPoints.add(new int[]{width - 1 - originOffsetX, -originOffsetZ});
        borderPoints.add(new int[]{width - 1 - originOffsetX, depth - 1 - originOffsetZ});
        borderPoints.add(new int[]{-originOffsetX, depth - 1 - originOffsetZ});

        // Rotate border points according to building direction
        List<int[]> rotatedBorderPoints = new ArrayList<>();
        for (int[] point : borderPoints) {
            int[] rotated = rotateSchematicCoordinates(point[0], 0, point[1], width, depth, direction);
            rotatedBorderPoints.add(new int[]{rotated[0], rotated[2]}); // Use X and Z only
        }

        // Create VillageBorder with rotated points
        VillageBorder border = new VillageBorder(rotatedBorderPoints, 0, 256);

        // Get border edge blocks using the existing VillageBorder line algorithm
        List<int[]> edgeBlocks = border.getEdgeBlocks();
        Material borderMaterial = configManager.getPreviewBlock();
        BlockData borderData = borderMaterial.createBlockData();

        List<Location> shownBlocks = new ArrayList<>();
        
        // Show border blocks at the highest block Y for each XZ coordinate
        for (int[] edge : edgeBlocks) {
            int x = edge[0];
            int z = edge[1];
            
            // Get world coordinates from schematic offsets
            int worldX = origin.getBlockX() + x;
            int worldZ = origin.getBlockZ() + z;
            
            // Get the highest block Y at this XZ position
            int y = world.getHighestBlockYAt(worldX, worldZ);
            
            Location borderLoc = new Location(world, worldX, y, worldZ);
            player.sendBlockChange(borderLoc, borderData);
            shownBlocks.add(borderLoc);
        }

        // Store shown blocks for cleanup
        session.getBorderVisualizationBlocks().addAll(shownBlocks);
    }
    */

    /**
     * DISABLED: Border visualization is no longer shown.
     */
    /*
    public void hideBuildingBorder(Player player) {
        BuildingSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        World world = player.getWorld();

        for (Location loc : session.getBorderVisualizationBlocks()) {
            if (loc.getWorld().equals(world)) {
                player.sendBlockChange(loc, world.getBlockAt(loc).getBlockData());
            }
        }

        session.getBorderVisualizationBlocks().clear();
    }
    */

    /**
     * Rotates schematic coordinates based on building direction.
     * Direction: N=0°, E=90°, S=180°, W=270°
     * 
     * Rotations are CLOCKWISE (IM Uhrzeigersinn):
     * - N: No rotation
     * - E: 90° clockwise
     * - S: 180°
     * - W: 270° clockwise (= -90° = 90° counter-clockwise)
     */
    private int[] rotateSchematicCoordinates(int x, int y, int z, int width, int depth, String direction) {
        if (direction == null || direction.equals("N")) {
            return new int[]{x, y, z};
        }

        return switch (direction) {
            case "E" -> new int[]{depth - 1 - z, y, x};            // 90° clockwise: swap + invert z
            case "S" -> new int[]{width - 1 - x, y, depth - 1 - z}; // 180°: invert both
            case "W" -> new int[]{z, y, width - 1 - x};            // 270° clockwise: swap (inverse of E) + invert x
            default -> new int[]{x, y, z};                         // N (no rotation)
        };
    }

    public void clearBuildingPreview(Player player) {
        BuildingSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        Location origin = session.getBuilding().getLocation();
        World world = player.getWorld();

        // Only clear blocks that are in our visualization tracking set
        for (String locStr : session.getVisualizedBlockLocations()) {
            Location loc = stringToLoc(locStr, world);
            if (loc != null) {
                // Restore the actual block from the world
                player.sendBlockChange(loc, world.getBlockAt(loc).getBlockData());
            }
        }

        // Clear the tracking set
        session.getVisualizedBlockLocations().clear();
    }

    /**
     * Helper to convert Location to String for tracking.
     */
    private String locToString(Location loc) {
        return loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ() + ":" + loc.getWorld().getName();
    }

    /**
     * Helper to convert String back to Location.
     */
    private Location stringToLoc(String locStr, World world) {
        try {
            String[] parts = locStr.split(":");
            if (parts.length == 4) {
                return new Location(world, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Fehler beim Parsen von Location-String: " + locStr);
        }
        return null;
    }

    public boolean validateBuilding(Player player) {
        BuildingSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return false;

        if (session.getSchematic().isEmpty()) {
            // No schematic = auto-complete
            completeBuilding(player);
            return true;
        }

        Location origin = session.getBuilding().getLocation();
        World world = player.getWorld();

        boolean valid = worldEditHook.validateBuilding(world, origin, session.getSchematic());
        if (valid) {
            completeBuilding(player);
        }
        return valid;
    }

    /** Admin helper: places all preview blocks and marks building completed. */
    public boolean forceCompleteBuilding(Player player) {
        BuildingSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return false;
        Location origin = session.getBuilding().getLocation();
        if (session.getSchematicData() != null) {
            for (WorldEditHook.SchematicBlock sb : session.getSchematicData().getBlocks()) {
                Location loc = origin.clone().add(sb.dx(), sb.dy(), sb.dz());
                loc.getBlock().setBlockData(sb.blockData(), false);
            }
        } else {
            for (Map.Entry<int[], Material> entry : session.getSchematic().entrySet()) {
                int[] offset = entry.getKey();
                Material mat = entry.getValue();
                if (mat == Material.AIR) continue;
                origin.clone().add(offset[0], offset[1], offset[2]).getBlock().setType(mat, false);
            }
        }
        completeBuilding(player);
        return true;
    }

    public void completeBuilding(Player player) {
        BuildingSession session = activeSessions.get(player.getUniqueId());  // Get but don't remove yet
        if (session == null) return;

        // Clear preview if visible (needs session to be present)
        if (session.isPreviewVisible()) {
            clearBuildingPreview(player);
        }

        // Hide the building border (needs session to be present)
        // Border visualization disabled - commented out
        // hideBuildingBorder(player);

        session.getBuilding().setCompleted(true);
        session.getBuilding().setSignHidden(false);

        // Update the construction sign to a completed building sign if present
        if (session.getSignLocation() != null) {
            Block signBlock = session.getSignLocation().getBlock();
            if (signBlock.getState() instanceof Sign sign) {
                if (signBlock.getBlockData() instanceof WallSign wallSign) {
                    String direction = session.getBuilding().getDirection();
                    BlockFace signFace = switch (direction) {
                        case "S" -> BlockFace.SOUTH;
                        case "E" -> BlockFace.EAST;
                        case "W" -> BlockFace.WEST;
                        default -> BlockFace.NORTH;
                    };
                    wallSign.setFacing(signFace);
                    signBlock.setBlockData(wallSign);
                }
                String buildingName = getDisplayName(session.getBuilding().getTypeKey());
                sign.getSide(Side.FRONT).line(0, net.kyori.adventure.text.Component.text("§6" + buildingName));
                sign.getSide(Side.FRONT).line(1, net.kyori.adventure.text.Component.text("§aFertig"));
                sign.getSide(Side.FRONT).line(2, net.kyori.adventure.text.Component.text(""));
                sign.getSide(Side.FRONT).line(3, net.kyori.adventure.text.Component.text("[Rechtsklick]"));
                sign.setWaxed(true);
                sign.update();
                applyBuildingSignTemplate(player, session.getBuilding(), configManager);
            }
        }

        villageManager.saveVillage(session.getVillage());
        
        com.example.village.util.MessageUtil.send(player, configManager.getPrefix(),
                "&aGebäude fertiggestellt!");

        // NOW remove the session after all cleanup is done
        activeSessions.remove(player.getUniqueId());
    }

    public void cancelBuilding(Player player) {
        BuildingSession session = activeSessions.get(player.getUniqueId());  // Get but don't remove yet
        if (session == null) return;

        // Clear preview if visible (needs session to be present)
        if (session.isPreviewVisible()) {
            clearBuildingPreview(player);
        }

        // Hide the building border (needs session to be present)
        // Border visualization disabled - commented out
        // hideBuildingBorder(player);

        // Hide building boundary visualization (GOLD_ORE client-side)
        World world = player.getWorld();
        for (Location loc : session.getBorderVisualizationBlocks()) {
            if (loc != null && loc.getWorld() != null && loc.getWorld().equals(world)) {
                player.sendBlockChange(loc, world.getBlockAt(loc).getBlockData());
            }
        }
        session.getBorderVisualizationBlocks().clear();

        // Remove the build sign from where it was placed (at player's location when confirming)
        if (session.getSignLocation() != null) {
            Block signBlock = session.getSignLocation().getBlock();
            if (signBlock.getType() == Material.OAK_WALL_SIGN) {
                signBlock.setType(Material.AIR);
            }
        }

        // Remove from village
        session.getVillage().removeBuilding(session.getBuilding().getId());

        // Refund cost
        double globalRefund = getGlobalBuildCost(session.getBuilding().getTypeKey());
        double localRefund = getLocalBuildCost(session.getBuilding().getTypeKey());
        if (globalRefund > 0 || localRefund > 0) {
            if (globalRefund > 0) economyService.deposit(player, globalRefund);
            if (localRefund > 0) {
                String localCurrencyId = currencyService.getVillageCurrencyId(session.getVillage());
                currencyService.addBalance(player.getUniqueId(), localCurrencyId, localRefund);
            }
        }

        com.example.village.util.MessageUtil.send(player, configManager.getPrefix(),
                "&cBau abgebrochen. Geld erstattet.");
        villageManager.saveVillage(session.getVillage());

        // NOW remove the session after all cleanup is done
        activeSessions.remove(player.getUniqueId());
    }

    public boolean isInBuildingSession(UUID playerId) {
        return activeSessions.containsKey(playerId) || pendingConfirmations.containsKey(playerId);
    }

    public BuildingSession getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }

    public boolean isBuildingPaused(UUID buildingId) {
        if (buildingId == null) {
            return false;
        }
        return activeSessions.values().stream()
                .anyMatch(session -> buildingId.equals(session.getBuilding().getId()) && session.isPaused());
    }

    /**
     * Opens the building menu GUI for a player.
     */
    public void openBuildingMenu(Player player) {
        BuildingSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        com.example.village.gui.BuildingMenuGui menuGui = 
                new com.example.village.gui.BuildingMenuGui(player, player.getUniqueId(), session.isPaused());
        player.openInventory(menuGui.getInventory());
    }

    /**
     * Toggles preview visibility for a building.
     */
    public void toggleBuildingPreview(Player player) {
        BuildingSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        if (session.isPreviewVisible()) {
            clearBuildingPreview(player);
            session.setPreviewVisible(false);
            com.example.village.util.MessageUtil.send(player, configManager.getPrefix(),
                    "&aVorschau versteckt.");
        } else {
            showBuildingPreview(player);
            session.setPreviewVisible(true);
            com.example.village.util.MessageUtil.send(player, configManager.getPrefix(),
                    "&aVorschau angezeigt.");
        }
    }

    /**
     * Pauses the building construction.
     */
    /**
     * Checks if a player already has an ACTIVE (non-paused) building in progress.
     * A paused building does NOT count as active.
     */
    private boolean hasActiveBuilding(Player player) {
        return activeSessions.containsKey(player.getUniqueId()) || pendingConfirmations.containsKey(player.getUniqueId());
    }

    /**
     * Checks if a player has a PAUSED building that can be resumed.
     */
    private boolean hasPausedBuilding(Player player) {
        BuildingSession session = activeSessions.get(player.getUniqueId());
        return session != null && session.isPaused();
    }

    public void pauseBuilding(Player player) {
        BuildingSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        if (!session.isPaused()) {
            // Only pause if currently ACTIVE (not already paused)
            session.setPaused(true);
            clearBuildingPreview(player);
            session.setPreviewVisible(false);
            com.example.village.util.MessageUtil.send(player, configManager.getPrefix(),
                    "&eBau unterbrochen. Du kannst: &7Fortsetzen &eoder &7Abbrechen&e.");
        } else {
            // Already paused
            com.example.village.util.MessageUtil.send(player, configManager.getPrefix(),
                    "&cDieser Bau ist bereits unterbrochen.");
        }
    }

    /**
     * Resumes a paused building construction.
     * Only allows resume if THIS specific building is paused (prevents resuming wrong building).
     */
    public void resumeBuilding(Player player) {
        BuildingSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        // Ensure we're resuming the paused building, not accidentally activating another
        if (session.isPaused()) {
            session.setPaused(false);
            showBuildingPreview(player);
            session.setPreviewVisible(true);
            com.example.village.util.MessageUtil.send(player, configManager.getPrefix(),
                    "&eBau fortgesetzt.");
        } else {
            // This building is not paused, so something is wrong
            com.example.village.util.MessageUtil.send(player, configManager.getPrefix(),
                    "&cDieser Bau ist nicht unterbrochen.");
        }
    }

    /**
     * Starts a building session for an existing incomplete building.
     * This allows resuming construction of a building that was previously started but not completed.
     */
    public boolean startBuildingSessionForExistingBuilding(Player player, Village village, UUID buildingId) {
        VillageBuilding building = findBuilding(village, buildingId);
        if (building == null || building.isCompleted()) {
            return false;
        }

        // Check if player already has an active session
        if (isInBuildingSession(player.getUniqueId())) {
            com.example.village.util.MessageUtil.send(player, configManager.getPrefix(),
                    "&cDu baust bereits ein Gebaeude!");
            return false;
        }

        String schematicName2 = getSchematicName(building.getTypeKey());
        if (schematicName2 == null || schematicName2.isBlank()) return false;
        // Load schematic file
        java.io.File schematicFile = new java.io.File(plugin.getDataFolder(), "schematics/" + schematicName2);
        if (!schematicFile.exists()) {
            com.example.village.util.MessageUtil.send(player, configManager.getPrefix(),
                    "&cSchematic-Datei nicht gefunden: &e" + schematicName2);
            return false;
        }

        // Load and rotate schematic data
        WorldEditHook.SchematicData schematicData = worldEditHook.loadAndRotateSchematicData(schematicFile, building.getDirection());
        if (schematicData == null) {
            com.example.village.util.MessageUtil.send(player, configManager.getPrefix(),
                    "&cFehler beim Laden der Schematic-Datei.");
            return false;
        }

        // Create schematic map
        Map<int[], Material> schematic = new HashMap<>();
        for (WorldEditHook.SchematicBlock sb : schematicData.getBlocks()) {
            schematic.put(new int[]{sb.dx(), sb.dy(), sb.dz()}, sb.blockData().getMaterial());
        }

        // Create and start session
        BuildingSession session = new BuildingSession(village, building, schematic, schematicData);
        activeSessions.put(player.getUniqueId(), session);

        // Show preview
        showBuildingPreview(player);
        session.setPreviewVisible(true);

        com.example.village.util.MessageUtil.send(player, configManager.getPrefix(),
                "&aBau-Session für &e" + getDisplayName(building.getTypeKey()) + " &agestartet.");
        return true;
    }

    // --- Public accessors for Chat Input Listener ---
    public Map<UUID, BorderExpansionConfirmation> getBorderExpansionConfirmations() {
        return borderExpansionConfirmations;
    }

    public static final class BuildingSession {
        private final Village village;
        private final VillageBuilding building;
        private final Map<int[], Material> schematic;
        private final WorldEditHook.SchematicData schematicData;
        private final java.util.Set<String> visualizedBlockLocations;  // Track which blocks are visualized
        private final List<Location> borderVisualizationBlocks;  // Track border visualization blocks
        private Location signLocation;  // Track where the sign was placed
        private boolean paused;
        private boolean previewVisible;

        public BuildingSession(Village village, VillageBuilding building,
                               Map<int[], Material> schematic, WorldEditHook.SchematicData schematicData) {
            this.village = village;
            this.building = building;
            this.schematic = schematic;
            this.schematicData = schematicData;
            this.visualizedBlockLocations = new java.util.HashSet<>();
            this.borderVisualizationBlocks = new ArrayList<>();
            this.signLocation = null;
            this.paused = false;
            this.previewVisible = false;
        }

        public Village getVillage() { return village; }
        public VillageBuilding getBuilding() { return building; }
        public Map<int[], Material> getSchematic() { return schematic; }
        public WorldEditHook.SchematicData getSchematicData() { return schematicData; }
        public java.util.Set<String> getVisualizedBlockLocations() { return visualizedBlockLocations; }
        public List<Location> getBorderVisualizationBlocks() { return borderVisualizationBlocks; }
        public Location getSignLocation() { return signLocation; }
        public void setSignLocation(Location loc) { this.signLocation = loc; }
        public boolean isPaused() { return paused; }
        public void setPaused(boolean paused) { this.paused = paused; }
        public boolean isPreviewVisible() { return previewVisible; }
        public void setPreviewVisible(boolean visible) { this.previewVisible = visible; }
    }

    /**
     * Holds data for a building that has been previewed but not yet confirmed.
     */
    public static final class PendingBuildingConfirmation {
        private final Village village;
        private final String typeKey;
        private final String direction;
        private final String schematicName;
        private final Location location;
        private final WorldEditHook.SchematicData schematicData;
        private final List<Location> previewLocations;

        public PendingBuildingConfirmation(Village village, String typeKey, String direction,
                                           Location location,
                                           WorldEditHook.SchematicData schematicData,
                                           List<Location> previewLocations,
                                           String schematicName) {
            this.village = village;
            this.typeKey = typeKey;
            this.direction = direction;
            this.schematicName = schematicName;
            this.location = location;
            this.schematicData = schematicData;
            this.previewLocations = previewLocations;
        }

        public Village getVillage() { return village; }
        public String getTypeKey() { return typeKey; }
        public String getDirection() { return direction; }
        public String getSchematicName() { return schematicName; }
        public Location getLocation() { return location; }
        public WorldEditHook.SchematicData getSchematicData() { return schematicData; }
        public List<Location> getPreviewLocations() { return previewLocations; }
    }

    public static final class BorderExpansionConfirmation {
        private final Village village;
        private final VillageBuilding building;
        private final BuildingSession session;
        private final BuildingType buildingType;
        private final Location buildingLocation;
        private final WorldEditHook.SchematicData schematicData;

        public BorderExpansionConfirmation(Village village, VillageBuilding building, 
                                           BuildingSession session, BuildingType buildingType,
                                           Location buildingLocation, WorldEditHook.SchematicData schematicData) {
            this.village = village;
            this.building = building;
            this.session = session;
            this.buildingType = buildingType;
            this.buildingLocation = buildingLocation;
            this.schematicData = schematicData;
        }

        public Village getVillage() { return village; }
        public VillageBuilding getBuilding() { return building; }
        public BuildingSession getSession() { return session; }
        public BuildingType getBuildingType() { return buildingType; }
        public Location getBuildingLocation() { return buildingLocation; }
        public WorldEditHook.SchematicData getSchematicData() { return schematicData; }
    }

    public record PendingSignRevealConfirmation(UUID villageId, UUID buildingId, Location targetLocation) {}

    /**
     * Completely removes a building, including blocks and sign.
     */
    public void removeBuildingCompletely(VillageBuilding building) {
        Village village = villageManager.getAllVillages().stream()
                .filter(v -> v.getBuildings().stream().anyMatch(b -> b.getId().equals(building.getId())))
                .findFirst().orElse(null);
        if (village != null) {
            if (worldGuardHook.isAvailable()) {
                villageManager.ensureBuildingTypeOrdinals(village);
                worldGuardHook.removeBuildingRegion(village, building);
            }
            if (blueMapHook != null && blueMapHook.isAvailable()) {
                blueMapHook.removeBuildingMarker(village, building);
            }
        }
        // Remove sign
        if (building.getSignLocation() != null) {
            removeSignAt(building.getSignLocation());
        }
        // Remove blocks by setting them to air
        String schematicName = getSchematicName(building.getTypeKey(), building.getSchematicName());
        if (schematicName != null && !schematicName.isBlank()) {
            java.io.File schematicFile = new java.io.File(plugin.getDataFolder(), "schematics/" + schematicName);
            WorldEditHook.SchematicData schematicData = worldEditHook.loadSchematicData(schematicFile);
            if (schematicData != null) {
                // Calculate the area and set to air
                Location origin = building.getLocation();
                int width = schematicData.getWidth();
                int height = schematicData.getHeight();
                int length = schematicData.getDepth();
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        for (int z = 0; z < length; z++) {
                            Location blockLoc = origin.clone().add(x, y, z);
                            blockLoc.getBlock().setType(Material.AIR);
                        }
                    }
                }
            }
        }
    }

    /**
     * Removes all active building sessions for a village.
     */
    public void removeSessionsForVillage(UUID villageId) {
        activeSessions.entrySet().removeIf(entry -> entry.getValue().getVillage().getId().equals(villageId));
    }

    /**
     * Startet den Platzierungsmodus für einen Spieler.
     * Der Spieler muss nun Rechtsklick auf einen Block machen.
     */
    public void startPlacementMode(Player player, Village village, String buildingTypeKey, String direction) {
        startPlacementMode(player, village, buildingTypeKey, direction, null);
    }

    public void startPlacementMode(Player player, Village village, String buildingTypeKey,
                                   String direction, String schematicName) {
        // Immer neu anlegen – setzt den Timeout zurück, auch wenn derselbe Typ erneut platziert wird
        PendingPlacementMode mode = new PendingPlacementMode(player.getUniqueId(), village, buildingTypeKey,
                                                             direction, schematicName, placementTimeoutSeconds);
        pendingPlacementModes.put(player.getUniqueId(), mode);

        MessageUtil.sendClickableCommand(
                player,
                plugin.getConfig().getString("prefix", "&6[Dorf] "),
                "&aRechtsklick auf einen Block, um das Gebäude zu platzieren.",
                "/village cancel");

        updatePlacementTimer(player, mode);
    }

    /**
     * Prüft und entfernt abgelaufene Platzierungen.
     */
    public void checkPlacementTimeouts() {
        pendingPlacementModes.entrySet().removeIf(entry -> {
            PendingPlacementMode mode = entry.getValue();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) {
                return false;
            }
            if (mode.isExpired()) {
                cancelBuildingPreview(player);
                clearPlacementScoreboard(player);
                MessageUtil.send(player, "&6[Dorf] ",
                        "&cGebäude-Platzierung abgebrochen. Zeit abgelaufen! (Timeout: " +
                                mode.getTimeoutSeconds() + "s)");
                return true;
            }
            updatePlacementTimer(player, mode);
            return false;
        });
    }

    public PendingPlacementMode getPendingPlacementMode(UUID playerId) {
        return pendingPlacementModes.get(playerId);
    }

    public void cancelPlacement(UUID playerId) {
        PendingPlacementMode mode = pendingPlacementModes.remove(playerId);
        if (mode != null) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                clearPlacementScoreboard(player);
                MessageUtil.send(player, "&6[Dorf] ", "&cGebäude-Platzierung abgebrochen.");
            }
        }
    }

    private void updatePlacementTimer(Player player, PendingPlacementMode mode) {
        if (player == null || mode == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        long elapsedSeconds = (System.currentTimeMillis() - mode.getCreatedAt()) / 1000L;
        int remaining = Math.max(0, mode.getTimeoutSeconds() - (int) elapsedSeconds);

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        originalScoreboards.putIfAbsent(uuid, player.getScoreboard());
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("village_place", "dummy", Component.text("§6Bauplatzierung"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.getScore("§7Verbleibend: §e" + remaining + "s").setScore(1);
        player.setScoreboard(board);
    }

    private void clearPlacementScoreboard(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        Scoreboard original = originalScoreboards.remove(uuid);
        if (original != null) {
            player.setScoreboard(original);
        }
    }

    /**
     * Removes a pending placement mode without sending messages.
     * Used when the player has successfully selected a block.
     */
    public void clearPlacementMode(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            clearPlacementScoreboard(player);
        }
        pendingPlacementModes.remove(playerId);
    }

    /**
     * Holds data for a player waiting to place a building by right-clicking.
     */
    public static final class PendingPlacementMode {
        private final UUID playerId;
        private final Village village;
        private final String buildingTypeKey;
        private final String direction;
        private final String schematicName;
        private final long createdAt;
        private final int timeoutSeconds;
        private Location previewLocation;

        public PendingPlacementMode(UUID playerId, Village village, String buildingTypeKey, 
                                    String direction, String schematicName, int timeoutSeconds) {
            this.playerId = playerId;
            this.village = village;
            this.buildingTypeKey = buildingTypeKey;
            this.direction = direction;
            this.schematicName = schematicName;
            this.createdAt = System.currentTimeMillis();
            this.timeoutSeconds = timeoutSeconds;
        }

        public UUID getPlayerId() { return playerId; }
        public Village getVillage() { return village; }
        public String getBuildingTypeKey() { return buildingTypeKey; }
        public String getDirection() { return direction; }
        public String getSchematicName() { return schematicName; }
        public long getCreatedAt() { return createdAt; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > timeoutSeconds * 1000L;
        }
        public Location getPreviewLocation() { return previewLocation; }
        public void setPreviewLocation(Location loc) { this.previewLocation = loc; }
    }
}
