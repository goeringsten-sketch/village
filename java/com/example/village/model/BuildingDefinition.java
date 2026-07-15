package com.example.village.model;

import org.bukkit.Material;
import java.util.*;

/**
 * Beschreibt ein Gebäude/Gebiet ohne Schematic – nur über Workstation-Blöcke und Bereich.
 * Wird aus config/buildings.yml (categories-Sektion) geladen.
 */
public final class BuildingDefinition {

    public enum BuildingKind { STRUCTURE, PATH, AREA, OUTPOST }

    /**
     * Bestimmt wie das Gebäude validiert und abgeschlossen wird.
     *
     * SCHEMATIC   – WorldEdit-Schematic muss gebaut werden (bestehender BuildingService).
     *               Erfordert ein .schem-File. Das Feld 'schematic' muss gesetzt sein.
     * BLOCK_CHECK – Keine Schematic. Stattdessen wird geprüft ob bestimmte Blöcke im
     *               registrierten Bereich vorhanden sind (required_blocks in der Config).
     *               Pfad-Gebäude (type: path) erzwingen immer BLOCK_CHECK via Walk-Session.
     */
    public enum ValidationMode { SCHEMATIC, BLOCK_CHECK, HYBRID }

    // ── Identität ─────────────────────────────────────────────────
    private final String id;
    private final String categoryId;
    private final String name;
    private final String description;
    private final String permission;
    private final Material icon;
    private final int requiredVillageLevel;
    private final String parentId;
    private final BuildingKind kind;
    private final ValidationMode validationMode;   // SCHEMATIC oder BLOCK_CHECK

    // ── Schematic (nur bei validationMode == SCHEMATIC) ───────────
    private final String schematic;                // Dateiname z.B. "dorfzentrum.schem"

    // ── Block-Check (nur bei validationMode == BLOCK_CHECK) ───────
    /** Blöcke die im Bereich vorhanden sein müssen: Material → Mindestanzahl */
    private final Map<Material, Integer> requiredBlocks;
    /** Anteil der Bereichsfläche der mit bestimmtem Block belegt sein muss (0–100) */
    private final int requiredBlockPercentage;
    private final Material requiredBlockForPercentage;

    // ── Workstation-Blöcke ────────────────────────────────────────
    private final List<Material> primaryWorkstationBlocks;
    private final List<WorkstationSlot> additionalWorkstations;

    // ── Bereich ───────────────────────────────────────────────────
    private final AreaConfig area;

    // ── Truhen ────────────────────────────────────────────────────
    private final ChestConfig chestConfig;

    // ── Villager & Kosten ─────────────────────────────────────────
    private final int villagerSlots;
    private final Map<Material, Integer> buildItems;
    private final double buildMoneyGlobal;
    private final double buildMoneyLocal;

    // ── Upgrades & Produktion ─────────────────────────────────────
    private final Map<Integer, UpgradeTier> upgrades;
    private final List<ProductionRecipe> recipes;

    // ── Pfad & Effekte ────────────────────────────────────────────
    private final PathConfig pathConfig;
    private final Map<String, Object> passiveEffects;
    private final boolean requiresOutsideAllVillages;
    private final String upgradesTo;
    private final boolean showInMenu;
    private final boolean requiresSchematic;
    /** Maximale Anzahl dieses Gebäude-Typs pro Dorf. -1 = unbegrenzt. */
    private final int maxInstances;

    public BuildingDefinition(Builder b) {
        this.id                         = b.id;
        this.categoryId                 = b.categoryId;
        this.name                       = b.name;
        this.description                = b.description;
        this.permission                 = b.permission;
        this.icon                       = b.icon;
        this.requiredVillageLevel       = b.requiredVillageLevel;
        this.parentId                   = b.parentId;
        this.kind                       = b.kind;
        this.validationMode             = b.kind == BuildingKind.PATH
                                            ? ValidationMode.BLOCK_CHECK  // Pfade immer BLOCK_CHECK
                                            : b.validationMode;
        this.schematic                  = b.schematic;
        this.requiredBlocks             = Collections.unmodifiableMap(new LinkedHashMap<>(b.requiredBlocks));
        this.requiredBlockPercentage    = b.requiredBlockPercentage;
        this.requiredBlockForPercentage = b.requiredBlockForPercentage;
        this.primaryWorkstationBlocks   = List.copyOf(b.primaryWorkstationBlocks);
        this.additionalWorkstations     = List.copyOf(b.additionalWorkstations);
        this.area                       = b.area;
        this.chestConfig                = b.chestConfig;
        this.villagerSlots              = b.villagerSlots;
        this.buildItems                 = Collections.unmodifiableMap(new LinkedHashMap<>(b.buildItems));
        this.buildMoneyGlobal           = b.buildMoneyGlobal;
        this.buildMoneyLocal            = b.buildMoneyLocal;
        this.upgrades                   = Collections.unmodifiableMap(new LinkedHashMap<>(b.upgrades));
        this.recipes                    = List.copyOf(b.recipes);
        this.pathConfig                 = b.pathConfig;
        this.passiveEffects             = Collections.unmodifiableMap(new LinkedHashMap<>(b.passiveEffects));
        this.requiresOutsideAllVillages = b.requiresOutsideAllVillages;
        this.upgradesTo                 = b.upgradesTo;
        this.showInMenu                 = b.showInMenu;
        this.requiresSchematic          = b.kind == BuildingKind.PATH ? false : b.requiresSchematic;
        this.maxInstances               = b.maxInstances;
    }

    // ── Getters ───────────────────────────────────────────────────
    public String getId()                               { return id; }
    public String getCategoryId()                       { return categoryId; }
    public String getName()                             { return name; }
    public String getDescription()                      { return description; }
    public String getPermission()                       { return permission; }
    public Material getIcon()                           { return icon; }
    public int getRequiredVillageLevel()                { return requiredVillageLevel; }
    public String getParentId()                         { return parentId; }
    public BuildingKind getKind()                       { return kind; }
    public ValidationMode getValidationMode()           { return validationMode; }
    public String getSchematic()                        { return schematic; }
    public Map<Material, Integer> getRequiredBlocks()   { return requiredBlocks; }
    public int getRequiredBlockPercentage()             { return requiredBlockPercentage; }
    public Material getRequiredBlockForPercentage()     { return requiredBlockForPercentage; }
    public boolean isSchematicBased()                   { return validationMode == ValidationMode.SCHEMATIC || validationMode == ValidationMode.HYBRID; }
    public boolean isBlockCheckBased()                  { return validationMode == ValidationMode.BLOCK_CHECK; }
    public boolean isHybridBased()                      { return validationMode == ValidationMode.HYBRID; }
    public List<Material> getPrimaryWorkstationBlocks() { return primaryWorkstationBlocks; }
    public List<WorkstationSlot> getAdditionalWorkstations() { return additionalWorkstations; }
    public AreaConfig getArea()                         { return area; }
    public ChestConfig getChestConfig()                 { return chestConfig; }
    public int getVillagerSlots()                       { return villagerSlots; }
    public Map<Material, Integer> getBuildItems()       { return buildItems; }
    public double getBuildMoneyGlobal()                 { return buildMoneyGlobal; }
    public double getBuildMoneyLocal()                  { return buildMoneyLocal; }
    public Map<Integer, UpgradeTier> getUpgrades()      { return upgrades; }
    public List<ProductionRecipe> getRecipes()          { return recipes; }
    public PathConfig getPathConfig()                   { return pathConfig; }
    public Map<String, Object> getPassiveEffects()      { return passiveEffects; }
    public boolean isRequiresOutsideAllVillages()       { return requiresOutsideAllVillages; }
    public String getUpgradesTo()                       { return upgradesTo; }
    public boolean isPath()                             { return kind == BuildingKind.PATH; }
    public boolean isOutpost()                          { return kind == BuildingKind.OUTPOST; }
    public int getMaxUpgradeTier()                      { return upgrades.isEmpty() ? 1 : Collections.max(upgrades.keySet()); }
    public UpgradeTier getUpgradeTier(int tier)         { return upgrades.get(tier); }
    public boolean isShowInMenu()                       { return showInMenu; }
    public boolean isRequiresSchematic()                { return requiresSchematic; }
    public int getMaxInstances()                        { return maxInstances; }

    @SuppressWarnings("unchecked")
    public <T> T getPassiveEffect(String key, T defaultValue) {
        Object v = passiveEffects.get(key);
        if (v == null) return defaultValue;
        try { return (T) v; } catch (ClassCastException e) { return defaultValue; }
    }

    // ── Nested Records ────────────────────────────────────────────

    public record WorkstationSlot(
        Material material, String label, boolean required,
        int countMin, int countMax, boolean publicAccess, boolean villagerAccessible) {}

    public record AreaConfig(
        String shape,
        int minWidth, int maxWidth, int minDepth, int maxDepth,
        int minRadius, int maxRadius, boolean radiusUpgradeable,
        int minInteriorVolume, int maxInteriorVolume) {

        public static AreaConfig rectangle(int minW, int maxW, int minD, int maxD) {
            return new AreaConfig("rectangle", minW, maxW, minD, maxD, 0, 0, false, 0, 0);
        }
        public static AreaConfig circle(int minR, int maxR, boolean upgradeable) {
            return new AreaConfig("circle", 0, 0, 0, 0, minR, maxR, upgradeable, 0, 0);
        }
        public static AreaConfig hollow(int minVol, int maxVol) {
            return new AreaConfig("hollow_structure", 0, 0, 0, 0, 0, 0, false, minVol, maxVol);
        }
    }

    public static final class UpgradeTier {
        private final String name;
        private final String permission;
        private final int requiredVillageLevel;
        private final Map<Material, Integer> buildItems;
        private final double buildMoneyGlobal;
        private final double buildMoneyLocal;
        private final Map<String, Object> changes;
        private final String upgradesTo;

        public UpgradeTier(String name, String permission, int requiredVillageLevel,
                           Map<Material, Integer> buildItems, double buildMoneyGlobal, double buildMoneyLocal,
                           Map<String, Object> changes, String upgradesTo) {
            this.name                 = name;
            this.permission           = permission;
            this.requiredVillageLevel = requiredVillageLevel;
            this.buildItems           = Collections.unmodifiableMap(new LinkedHashMap<>(buildItems));
            this.buildMoneyGlobal     = buildMoneyGlobal;
            this.buildMoneyLocal      = buildMoneyLocal;
            this.changes              = Collections.unmodifiableMap(new LinkedHashMap<>(changes));
            this.upgradesTo           = upgradesTo;
        }

        public String getName()                      { return name; }
        public String getPermission()                { return permission; }
        public int getRequiredVillageLevel()         { return requiredVillageLevel; }
        public Map<Material, Integer> getBuildItems() { return buildItems; }
        public double getBuildMoneyGlobal()          { return buildMoneyGlobal; }
        public double getBuildMoneyLocal()           { return buildMoneyLocal; }
        public Map<String, Object> getChanges()      { return changes; }
        public String getUpgradesTo()                { return upgradesTo; }

        public Map<String, Object> getModularExtensions() {
            Object raw = changes.get("modular_extensions");
            if (raw instanceof org.bukkit.configuration.ConfigurationSection section) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (String key : section.getKeys(false)) {
                    result.put(key, section.getConfigurationSection(key) != null
                        ? section.getConfigurationSection(key)
                        : section.get(key));
                }
                return Collections.unmodifiableMap(result);
            }
            if (raw instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((k, v) -> result.put(String.valueOf(k), v));
                return Collections.unmodifiableMap(result);
            }
            return Collections.emptyMap();
        }

        @SuppressWarnings("unchecked")
        public <T> T getChange(String key, T def) {
            Object v = changes.get(key);
            if (v == null) return def;
            try { return (T) v; } catch (ClassCastException e) { return def; }
        }
    }

    // ── Builder ───────────────────────────────────────────────────
    public static final class Builder {
        String id = "", categoryId = "", name = "", description = "", permission = "";
        Material icon = Material.STONE;
        int requiredVillageLevel = 1;
        String parentId = null;
        BuildingKind kind = BuildingKind.STRUCTURE;
        ValidationMode validationMode = ValidationMode.SCHEMATIC; // Default: Schematic
        String schematic = null;
        final Map<Material, Integer> requiredBlocks = new LinkedHashMap<>();
        int requiredBlockPercentage = 0;
        Material requiredBlockForPercentage = null;
        final List<Material> primaryWorkstationBlocks = new ArrayList<>();
        final List<WorkstationSlot> additionalWorkstations = new ArrayList<>();
        AreaConfig area = null;
        ChestConfig chestConfig = null;
        int villagerSlots = 0;
        final Map<Material, Integer> buildItems = new LinkedHashMap<>();
        double buildMoneyGlobal = 0, buildMoneyLocal = 0;
        final Map<Integer, UpgradeTier> upgrades = new LinkedHashMap<>();
        final List<ProductionRecipe> recipes = new ArrayList<>();
        PathConfig pathConfig = null;
        final Map<String, Object> passiveEffects = new LinkedHashMap<>();
        boolean requiresOutsideAllVillages = false;
        String upgradesTo = null;
        boolean showInMenu = true;
        boolean requiresSchematic = true;
        int maxInstances = -1;

        public Builder id(String v)                        { id = v; return this; }
        public Builder categoryId(String v)                { categoryId = v; return this; }
        public Builder name(String v)                      { name = v; return this; }
        public Builder description(String v)               { description = v; return this; }
        public Builder permission(String v)                { permission = v; return this; }
        public Builder icon(Material v)                    { icon = v; return this; }
        public Builder requiredVillageLevel(int v)         { requiredVillageLevel = v; return this; }
        public Builder parentId(String v)                  { parentId = v; return this; }
        public Builder kind(BuildingKind v)                { kind = v; return this; }
        public Builder validationMode(ValidationMode v)    { validationMode = v; return this; }
        public Builder schematic(String v)                 { schematic = v; return this; }
        public Builder requiredBlock(Material m, int min)  { requiredBlocks.put(m, min); return this; }
        public Builder requiredBlockPercentage(int pct, Material m) {
            requiredBlockPercentage = pct; requiredBlockForPercentage = m; return this;
        }
        public Builder primaryWorkstationBlock(Material v) { primaryWorkstationBlocks.add(v); return this; }
        public Builder additionalWorkstation(WorkstationSlot v) { additionalWorkstations.add(v); return this; }
        public Builder area(AreaConfig v)                  { area = v; return this; }
        public Builder chestConfig(ChestConfig v)          { chestConfig = v; return this; }
        public Builder villagerSlots(int v)                { villagerSlots = v; return this; }
        public Builder buildItem(Material m, int a)        { buildItems.put(m, a); return this; }
        public Builder buildMoneyGlobal(double v)          { buildMoneyGlobal = v; return this; }
        public Builder buildMoneyLocal(double v)           { buildMoneyLocal = v; return this; }
        public Builder upgrade(int tier, UpgradeTier v)   { upgrades.put(tier, v); return this; }
        public Builder recipe(ProductionRecipe v)          { recipes.add(v); return this; }
        public Builder pathConfig(PathConfig v)            { pathConfig = v; return this; }
        public Builder passiveEffect(String k, Object v)   { passiveEffects.put(k, v); return this; }
        public Builder requiresOutsideAllVillages(boolean v) { requiresOutsideAllVillages = v; return this; }
        public Builder upgradesTo(String v)                { upgradesTo = v; return this; }
        public Builder showInMenu(boolean v)               { showInMenu = v; return this; }
        public Builder requiresSchematic(boolean v)        { requiresSchematic = v; return this; }
        public Builder maxInstances(int v)                 { maxInstances = v; return this; }
        public BuildingDefinition build()                  { return new BuildingDefinition(this); }
    }
}
