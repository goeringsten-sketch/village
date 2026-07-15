package com.example.village.service;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * Lädt .schem.meta-Dateien für Hybrid-Validierung (STRUCTURE vs. DECORATION).
 */
public final class SchematicMetaLoader {

    public enum BlockRole { STRUCTURE, DECORATION }

    public record SchematicMeta(
            Map<String, BlockRole> blockRoles,
            Set<Material> decorationWhitelist,
            boolean persistentRecheck,
            Map<String, String> anchors
    ) {
        public static SchematicMeta empty() {
            return new SchematicMeta(Collections.emptyMap(), Collections.emptySet(), false, Collections.emptyMap());
        }

        public BlockRole roleFor(int dx, int dy, int dz) {
            return blockRoles.getOrDefault(key(dx, dy, dz), BlockRole.STRUCTURE);
        }

        public String anchor(String name) {
            if (name == null || name.isBlank()) return null;
            return anchors.get(name.trim().toLowerCase(Locale.ROOT));
        }

        public static String key(int dx, int dy, int dz) {
            return dx + "," + dy + "," + dz;
        }
    }

    private SchematicMetaLoader() {}

    public static SchematicMeta load(File schematicFile, ConfigurationSection buildingSection) {
        boolean persistentRecheck = buildingSection != null
                && buildingSection.getBoolean("hybrid_recheck_on_use", false);

        Set<Material> whitelist = new HashSet<>();
        if (buildingSection != null) {
            for (String matName : buildingSection.getStringList("decoration_whitelist")) {
                Material mat = Material.matchMaterial(matName.toUpperCase(Locale.ROOT));
                if (mat != null) {
                    whitelist.add(mat);
                }
            }
        }

        File metaFile = new File(schematicFile.getAbsolutePath() + ".meta");
        if (!metaFile.exists()) {
            return new SchematicMeta(Collections.emptyMap(), whitelist, persistentRecheck, Collections.emptyMap());
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(metaFile);
        Map<String, BlockRole> roles = new HashMap<>();
        Map<String, String> anchors = new HashMap<>();
        ConfigurationSection blocks = yaml.getConfigurationSection("blocks");
        if (blocks != null) {
            for (String coord : blocks.getKeys(false)) {
                String roleStr = blocks.getString(coord, "STRUCTURE").toUpperCase(Locale.ROOT);
                BlockRole role = "DECORATION".equals(roleStr) ? BlockRole.DECORATION : BlockRole.STRUCTURE;
                roles.put(coord, role);
            }
        }

        for (String matName : yaml.getStringList("decoration-whitelist")) {
            Material mat = Material.matchMaterial(matName.toUpperCase(Locale.ROOT));
            if (mat != null) {
                whitelist.add(mat);
            }
        }

        ConfigurationSection markers = yaml.getConfigurationSection("markers");
        if (markers == null) {
            markers = yaml.getConfigurationSection("anchors");
        }
        if (markers != null) {
            for (String anchorKey : markers.getKeys(false)) {
                String anchorValue = markers.getString(anchorKey);
                if (anchorValue != null && !anchorValue.isBlank()) {
                    anchors.put(anchorKey.trim().toLowerCase(Locale.ROOT), anchorValue.trim());
                }
            }
        }

        if (yaml.contains("persistent-recheck")) {
            persistentRecheck = yaml.getBoolean("persistent-recheck", persistentRecheck);
        }

        return new SchematicMeta(Collections.unmodifiableMap(roles), Set.copyOf(whitelist), persistentRecheck, Collections.unmodifiableMap(anchors));
    }
}
