package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.model.*;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lädt die 'categories'-Sektion aus config/buildings.yml und erzeugt BuildingDefinition-Objekte.
 * Läuft parallel zum bestehenden VillageConfigManager (der schematic-basierte BuildingTypes lädt).
 */
public final class BuildingConfigLoader {

    public static final class CategoryInfo {
        private final String id;
        private final String name;
        private final String permission;
        private final Material icon;

        public CategoryInfo(String id, String name, String permission, Material icon) {
            this.id = id;
            this.name = name;
            this.permission = permission;
            this.icon = icon;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getPermission() { return permission; }
        public Material getIcon() { return icon; }
    }

    private final VillagePlugin plugin;
    private final Map<String, BuildingDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, String> categoryPermissions = new LinkedHashMap<>();
    private final Map<String, CategoryInfo> categories = new LinkedHashMap<>();

    public BuildingConfigLoader(VillagePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        definitions.clear();
        categoryPermissions.clear();
        categories.clear();

        File file = new File(plugin.getDataFolder(), "config/buildings.yml");
        YamlConfiguration cfg;
        if (file.exists()) {
            cfg = YamlConfiguration.loadConfiguration(file);
        } else if (plugin.shouldUseBundledResources()) {
            if (plugin.getResource("config/buildings.yml") != null) {
                plugin.saveResource("config/buildings.yml", false);
                cfg = YamlConfiguration.loadConfiguration(file);
            } else {
                plugin.getLogger().warning("[BuildingConfigLoader] config/buildings.yml fehlt.");
                return;
            }
        } else {
            cfg = loadBundledConfig("config/buildings.yml");
            if (cfg == null) {
                plugin.getLogger().warning("[BuildingConfigLoader] config/buildings.yml fehlt.");
                return;
            }
        }

        ConfigurationSection cats = cfg.getConfigurationSection("categories");
        if (cats == null) { plugin.getLogger().warning("[BuildingConfigLoader] Keine 'categories'-Sektion."); return; }

        for (String catId : cats.getKeys(false)) {
            ConfigurationSection cat = cats.getConfigurationSection(catId);
            if (cat == null) continue;
            String perm = cat.getString("permission", "village.building." + catId);
            categoryPermissions.put(catId, perm);
            Material icon = mat(cat.getString("icon"), Material.CHEST);
            categories.put(catId, new CategoryInfo(catId, cat.getString("name", catId), perm, icon));

            ConfigurationSection buildings = cat.getConfigurationSection("buildings");
            if (buildings == null) continue;
            for (String bid : buildings.getKeys(false)) {
                ConfigurationSection b = buildings.getConfigurationSection(bid);
                if (b == null) continue;
                try { definitions.put(bid, parse(catId, bid, b)); }
                catch (Exception e) { plugin.getLogger().warning("[BuildingConfigLoader] Fehler bei " + catId + "." + bid + ": " + e.getMessage()); }
            }
        }
        plugin.getLogger().info("[BuildingConfigLoader] " + definitions.size() + " Definitionen geladen, " + categories.size() + " Kategorien.");
    }

    private YamlConfiguration loadBundledConfig(String relativePath) {
        try (InputStream input = plugin.getResource(relativePath)) {
            if (input == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("[BuildingConfigLoader] Fehler beim Laden der Default-Config " + relativePath + ": " + e.getMessage());
            return null;
        }
    }

    private BuildingDefinition parse(String catId, String id, ConfigurationSection s) {
        BuildingDefinition.Builder b = new BuildingDefinition.Builder()
            .id(id).categoryId(catId)
            .name(s.getString("name", id))
            .description(s.getString("description", ""))
            .permission(s.getString("permission", "village.building." + catId + "." + id))
            .icon(mat(s.getString("icon"), Material.STONE))
            .requiredVillageLevel(s.getInt("requires_village_level", 1))
            .parentId(s.getString("parent"))
            .villagerSlots(s.getInt("villager_slots", 0))
            .upgradesTo(s.getString("upgrades_to"))
            .requiresOutsideAllVillages(s.getBoolean("outpost.requires_outside_all_villages", false));
        // UI flags
        b.showInMenu(s.getBoolean("show_in_menu", true));

        // Kind
        BuildingDefinition.BuildingKind kind = switch (s.getString("type", "structure").toLowerCase()) {
            case "path"    -> BuildingDefinition.BuildingKind.PATH;
            case "area"    -> BuildingDefinition.BuildingKind.AREA;
            case "outpost" -> BuildingDefinition.BuildingKind.OUTPOST;
            default        -> BuildingDefinition.BuildingKind.STRUCTURE;
        };
        b.kind(kind);
        
        // ValidationMode:
        //   - Pfade (type: path) → immer BLOCK_CHECK (erzwungen im Konstruktor)
        //   - Explizit "block_check" → BLOCK_CHECK
        //   - Hat ein schematic-Feld → SCHEMATIC
        //   - Default: SCHEMATIC (Rückwärtskompatibilität)
        String schematicFile = s.getString("schematic", null);
        String modeStr = s.getString("validation_mode",
            schematicFile != null ? "schematic" : "block_check").toLowerCase();
        BuildingDefinition.ValidationMode mode = switch (modeStr) {
            case "block_check", "blockcheck" -> BuildingDefinition.ValidationMode.BLOCK_CHECK;
            default                          -> BuildingDefinition.ValidationMode.SCHEMATIC;
        };
        b.validationMode(mode);
        
        // requiresSchematic: Pfade und BLOCK_CHECK-Gebäude benötigen kein Schematic
        b.requiresSchematic(
            kind == BuildingDefinition.BuildingKind.PATH || mode == BuildingDefinition.ValidationMode.BLOCK_CHECK
                ? false
                : s.getBoolean("requires_schematic", true)
        );
        
        if (schematicFile != null) b.schematic(schematicFile);

        // required_blocks (für BLOCK_CHECK-Modus)
        ConfigurationSection reqBlocks = s.getConfigurationSection("required_blocks");
        if (reqBlocks != null) {
            for (String matStr : reqBlocks.getKeys(false)) {
                Material m = mat(matStr, null);
                if (m != null) b.requiredBlock(m, reqBlocks.getInt(matStr, 1));
            }
        }
        // Prozentualer Block-Check (z.B. "60% der Fläche muss DIRT_PATH sein")
        ConfigurationSection reqPct = s.getConfigurationSection("required_block_percentage");
        if (reqPct != null) {
            Material pctMat = mat(reqPct.getString("block"), null);
            if (pctMat != null) b.requiredBlockPercentage(reqPct.getInt("percentage", 50), pctMat);
        }

        // Primäre WB-Blöcke
        s.getStringList("workstation_blocks").forEach(ms -> { Material m = mat(ms, null); if (m != null) b.primaryWorkstationBlock(m); });

        // Zusätzliche WBs
        ConfigurationSection addWb = s.getConfigurationSection("additional_workstation_blocks");
        if (addWb != null) for (String k : addWb.getKeys(false)) {
            ConfigurationSection ws = addWb.getConfigurationSection(k);
            if (ws == null) continue;
            b.additionalWorkstation(new BuildingDefinition.WorkstationSlot(
                mat(ws.getString("type"), Material.CHEST), ws.getString("label", k),
                ws.getBoolean("required", false), ws.getInt("count_min", 1), ws.getInt("count_max", 1),
                ws.getBoolean("public", false), ws.getBoolean("villager_accessible", true)));
        }

        // Bereich
        ConfigurationSection area = s.getConfigurationSection("area");
        if (area != null) b.area(parseArea(area));

        // Truhe
        ConfigurationSection chest = s.getConfigurationSection("chest");
        if (chest != null) {
            String accStr = chest.getBoolean("public", true) ? "PUBLIC"
                : chest.getString("access", "PUBLIC").toUpperCase();
            ChestConfig.Access acc;
            try { acc = ChestConfig.Access.valueOf(accStr); } catch (Exception e) { acc = ChestConfig.Access.PUBLIC; }
            b.chestConfig(new ChestConfig(chest.getInt("initial_slots", 9),
                chest.getInt("max_upgradeable_slots", 54), acc, chest.getBoolean("villager_accessible", true)));
        }

        // Baukosten
        ConfigurationSection cost = s.getConfigurationSection("build_cost");
        if (cost != null) {
            parseMaterialMap(cost.getConfigurationSection("items")).forEach(b::buildItem);
            b.buildMoneyGlobal(cost.getDouble("money", 0));
            b.buildMoneyLocal(cost.getDouble("money_local", 0));
        }

        // Pfad
        ConfigurationSection path = s.getConfigurationSection("path");
        if (path != null) b.pathConfig(parsePath(path));

        // Passive Effekte
        ConfigurationSection passive = s.getConfigurationSection("passive_effects");
        if (passive != null) passive.getKeys(false).forEach(k -> b.passiveEffect(k, passive.get(k)));

        // Rezepte
        ConfigurationSection recipes = s.getConfigurationSection("recipes");
        if (recipes != null) for (String rid : recipes.getKeys(false)) {
            ConfigurationSection r = recipes.getConfigurationSection(rid);
            if (r == null) continue;
            b.recipe(new ProductionRecipe(rid,
                parseMaterialMap(r.getConfigurationSection("inputs")),
                parseMaterialMap(r.getConfigurationSection("outputs")),
                r.getInt("duration_seconds", 60),
                r.getString("required_villager_job"),
                r.getString("villager_skill_bonus"),
                r.getInt("required_building_level", 1)));
        }

        // Upgrades
        ConfigurationSection upgrades = s.getConfigurationSection("upgrades");
        if (upgrades != null) for (String tk : upgrades.getKeys(false)) {
            ConfigurationSection u = upgrades.getConfigurationSection(tk);
            if (u == null) continue;
            int tier = Integer.parseInt(tk.replaceAll("[^0-9]", "").isEmpty() ? "2" : tk.replaceAll("[^0-9]", ""));
            ConfigurationSection uCost = u.getConfigurationSection("build_cost");
            Map<Material, Integer> uItems = uCost != null ? parseMaterialMap(uCost.getConfigurationSection("items")) : new LinkedHashMap<>();
            Map<String, Object> changes = new LinkedHashMap<>();
            for (String ck : u.getKeys(false)) {
                if (!Set.of("name","permission","requires_village_level","build_cost").contains(ck))
                    changes.put(ck, u.get(ck));
            }
            b.upgrade(tier, new BuildingDefinition.UpgradeTier(
                u.getString("name", "Tier " + tier),
                u.getString("permission", s.getString("permission","") + ".upgrade." + tier),
                u.getInt("requires_village_level", 1), uItems,
                uCost != null ? uCost.getDouble("money", 0) : 0,
                uCost != null ? uCost.getDouble("money_local", 0) : 0,
                changes, u.getString("upgrades_to")));
        }

        return b.build();
    }

    private BuildingDefinition.AreaConfig parseArea(ConfigurationSection area) {
        return switch (area.getString("shape", "rectangle")) {
            case "circle" -> BuildingDefinition.AreaConfig.circle(
                area.getInt("min_radius", 3), area.getInt("max_radius", 10),
                area.getBoolean("upgradeable_max_radius", false));
            case "hollow_structure" -> BuildingDefinition.AreaConfig.hollow(
                area.getInt("min_interior_volume", 8), area.getInt("max_interior_volume", 200));
            default -> {
                int minW = 3, minD = 3, maxW = 10, maxD = 10;
                String mn = area.getString("min_size"), mx = area.getString("max_size");
                if (mn != null && mn.contains("x")) { var p = mn.split("x"); minW = Integer.parseInt(p[0].trim()); minD = Integer.parseInt(p[1].trim()); }
                if (mx != null && mx.contains("x")) { var p = mx.split("x"); maxW = Integer.parseInt(p[0].trim()); maxD = Integer.parseInt(p[1].trim()); }
                yield BuildingDefinition.AreaConfig.rectangle(minW, maxW, minD, maxD);
            }
        };
    }

    private PathConfig parsePath(ConfigurationSection p) {
        Set<Material> vs = new HashSet<>(), vb = new HashSet<>();
        p.getStringList("valid_surface_blocks").forEach(ms -> { Material m = mat(ms, null); if (m != null) vs.add(m); });
        p.getStringList("valid_base_blocks").forEach(ms -> { Material m = mat(ms, null); if (m != null) vb.add(m); });
        Material req = mat(p.getString("required_surface_block"), Material.DIRT_PATH);
        if (vs.isEmpty()) vs.add(req);
        ConfigurationSection sp = p.getConfigurationSection("speed_effect");
        return new PathConfig(
            p.getInt("width", 3), p.getBoolean("width_upgradeable", false), p.getInt("max_width", 3),
            mat(p.getString("visualize_border_block"), Material.YELLOW_CONCRETE_POWDER),
            p.getString("surface_check_mode", "skylight"), p.getInt("surface_min_skylight", 1),
            req, p.getInt("required_surface_percentage", 60), vs, vb,
            sp != null ? sp.getInt("amplifier", 0) : 0,
            sp != null ? sp.getInt("max_amplifier", 4) : 4,
            sp != null ? sp.getInt("amplifier_per_level", 1) : 1,
            sp != null ? sp.getInt("duration_after_leave_ticks", 60) : 60,
            sp != null ? sp.getInt("duration_after_leave_ticks_per_level", 40) : 40,
            p.getInt("width_increment_per_level", 1));
    }

    private Map<Material, Integer> parseMaterialMap(ConfigurationSection s) {
        Map<Material, Integer> map = new LinkedHashMap<>();
        if (s == null) return map;
        s.getKeys(false).forEach(k -> { Material m = mat(k, null); if (m != null) map.put(m, s.getInt(k, 1)); });
        return map;
    }

    private Material mat(String name, Material fallback) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name.toUpperCase());
        return m != null ? m : fallback;
    }

    // ── Öffentliche API ───────────────────────────────────────────
    public BuildingDefinition getDefinition(String id)        { return definitions.get(id); }
    public boolean hasDefinition(String id)                   { return definitions.containsKey(id); }
    public Collection<BuildingDefinition> getAll()            { return Collections.unmodifiableCollection(definitions.values()); }
    public String getCategoryPermission(String cat)           { return categoryPermissions.getOrDefault(cat, "village.building." + cat); }
    public Map<String, String> getCategoryPermissions()       { return Collections.unmodifiableMap(categoryPermissions); }
    public Collection<CategoryInfo> getCategories()           { return Collections.unmodifiableCollection(categories.values()); }
    public CategoryInfo getCategory(String id)                { return categories.get(id); }
    public List<BuildingDefinition> getByCategory(String cat) {
        List<BuildingDefinition> r = new ArrayList<>();
        definitions.values().stream().filter(d -> d.getCategoryId().equals(cat)).forEach(r::add);
        return r;
    }
}
