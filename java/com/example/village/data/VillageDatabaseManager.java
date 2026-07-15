package com.example.village.data;

import com.example.village.VillagePlugin;
import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.model.VillageBorder;
import com.example.village.model.VillageBuilding;
import com.example.village.model.VillageJoinRequest;
import com.example.village.model.VillageRelation;
import com.example.village.model.VillageRelationState;
import com.example.village.model.VillageRelationType;
import com.example.village.model.VillageMember;
import com.example.village.model.VillageRole;
import com.example.village.model.VillagerContract;
import com.example.village.model.VillagerJob;
import com.example.village.model.VillagerNeed;
import com.example.village.model.VillagerSkill;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class VillageDatabaseManager {

    private final VillagePlugin plugin;
    private final File villageDir;
    private final Map<UUID, Village> villages = new ConcurrentHashMap<>();

    public VillageDatabaseManager(VillagePlugin plugin) {
        this.plugin = plugin;
        this.villageDir = new File(plugin.getDataFolder(), "villages");
    }

    public void initialize() {
        if (!villageDir.exists()) villageDir.mkdirs();
        loadAllVillages();
    }

    public void shutdown() {
        saveAllVillages();
    }

    public Map<UUID, Village> getVillages() {
        return villages;
    }

    public void addVillage(Village village) {
        villages.put(village.getId(), village);
        saveVillage(village);
    }

    public void removeVillage(UUID villageId) {
        villages.remove(villageId);
        File file = new File(villageDir, villageId.toString() + ".yml");
        if (file.exists()) file.delete();
    }

    public Village getVillage(UUID villageId) {
        return villages.get(villageId);
    }

    private void loadAllVillages() {
        File[] files = villageDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            try {
                Village village = loadVillage(file);
                if (village != null) {
                    villages.put(village.getId(), village);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Fehler beim Laden von Dorf: " + file.getName(), e);
            }
        }
        plugin.getLogger().info(villages.size() + " Doerfer geladen.");
    }

    private Village loadVillage(File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        UUID id = UUID.fromString(cfg.getString("id"));
        String name = cfg.getString("name", "Unbenannt");
        UUID founderId = UUID.fromString(cfg.getString("founder"));
        String worldName = cfg.getString("world", "world");

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Welt '" + worldName + "' nicht gefunden fuer Dorf " + name);
            return null;
        }

        int bellX = cfg.getInt("bell.x");
        int bellY = cfg.getInt("bell.y");
        int bellZ = cfg.getInt("bell.z");
        Location bellLoc = new Location(world, bellX, bellY, bellZ);

        Village village = new Village(id, name, founderId, bellLoc);
        village.setWorldName(worldName);
        village.setLevel(cfg.getInt("level", 1));
        village.setPoints(cfg.getInt("points", 0));
        village.setFoundedAt(cfg.getLong("founded-at", System.currentTimeMillis()));
        village.setReviveUses(cfg.getInt("revival.uses", 0));
        village.setLastReviveAt(cfg.getLong("revival.last-at", 0L));

        // Borders (supports multiple border regions)
        ConfigurationSection bordersSection = cfg.getConfigurationSection("borders");
        if (bordersSection != null) {
            boolean first = true;
            for (String borderKey : bordersSection.getKeys(false)) {
                ConfigurationSection bs = bordersSection.getConfigurationSection(borderKey);
                if (bs == null) continue;
                VillageBorder loadedBorder = loadSingleBorder(bs);
                if (loadedBorder == null) continue;
                if (first) {
                    village.setBorder(loadedBorder);
                    first = false;
                } else {
                    village.addBorder(loadedBorder);
                }
            }
        } else {
            // Legacy: single "border" section
            ConfigurationSection borderSection = cfg.getConfigurationSection("border");
            if (borderSection != null) {
                VillageBorder loadedBorder = loadSingleBorder(borderSection);
                if (loadedBorder != null) {
                    village.setBorder(loadedBorder);
                }
            }
        }
        village.ensureBorderIds();

        // Members
        ConfigurationSection membersSection = cfg.getConfigurationSection("members");
        if (membersSection != null) {
            for (String key : membersSection.getKeys(false)) {
                UUID playerId = UUID.fromString(key);
                VillageRole role = VillageRole.valueOf(
                        membersSection.getString(key + ".role", "MEMBER"));
                VillageMember member = new VillageMember(playerId, role);
                java.util.List<String> roleNames = membersSection.getStringList(key + ".roles");
                if (roleNames != null && !roleNames.isEmpty()) {
                    java.util.Set<VillageRole> roles = java.util.EnumSet.noneOf(VillageRole.class);
                    for (String rn : roleNames) {
                        try {
                            roles.add(VillageRole.valueOf(rn.toUpperCase(java.util.Locale.ROOT)));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    if (!roles.isEmpty()) {
                        member.setRoles(roles);
                    }
                }
                member.setJoinedAt(membersSection.getLong(key + ".joined-at", System.currentTimeMillis()));
                village.getMembers().put(playerId, member);
            }
        }

        ConfigurationSection requestsSection = cfg.getConfigurationSection("join-requests");
        if (requestsSection != null) {
            for (String key : requestsSection.getKeys(false)) {
                UUID playerId = UUID.fromString(key);
                VillageJoinRequest request = new VillageJoinRequest(playerId);
                request.setRequestedAt(requestsSection.getLong(key + ".requested-at", System.currentTimeMillis()));
                village.getJoinRequests().put(playerId, request);
            }
        }

        // Buildings
        ConfigurationSection buildingsSection = cfg.getConfigurationSection("buildings");
        if (buildingsSection != null) {
            for (String key : buildingsSection.getKeys(false)) {
                ConfigurationSection bs = buildingsSection.getConfigurationSection(key);
                if (bs == null) continue;
                UUID bId = UUID.fromString(key);
                String typeKey = bs.getString("type", "house");
                int bx = bs.getInt("x");
                int by = bs.getInt("y");
                int bz = bs.getInt("z");
                Location bLoc = new Location(world, bx, by, bz);
                VillageBuilding building = new VillageBuilding(bId, typeKey, bLoc);
                building.setCompleted(bs.getBoolean("completed", false));
                building.setPlacedAt(bs.getLong("placed-at", System.currentTimeMillis()));
                building.setLevel(bs.getInt("level", 1));
                if (bs.contains("owner")) {
                    try {
                        building.setOwnerId(UUID.fromString(bs.getString("owner")));
                    } catch (IllegalArgumentException ignored) {}
                }
                building.setSignHidden(bs.getBoolean("sign.hidden", false));
                building.setAccessAllMembers(bs.getBoolean("access.all-members", true));
                List<String> accessList = bs.getStringList("access.list");
                if (accessList != null && !accessList.isEmpty()) {
                    java.util.Set<UUID> set = new java.util.HashSet<>();
                    for (String s : accessList) {
                        try { set.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
                    }
                    building.setAccessList(set);
                }
                if (bs.contains("sign.template")) {
                    building.setSignTemplate(bs.getString("sign.template"));
                }
                if (bs.contains("custom-name")) {
                    building.setCustomName(bs.getString("custom-name"));
                }
                if (bs.contains("sign.x") && bs.contains("sign.y") && bs.contains("sign.z")) {
                    building.setSignLocation(new Location(world,
                            bs.getInt("sign.x"), bs.getInt("sign.y"), bs.getInt("sign.z")));
                }
                building.setTypeOrdinal(bs.getInt("type-ordinal", 0));
                building.setDirection(bs.getString("direction", "N"));
                if (bs.contains("schematic")) {
                    building.setSchematicName(bs.getString("schematic"));
                }
                if (bs.contains("aesthetic-score")) {
                    building.setAestheticScore(bs.getInt("aesthetic-score", -1));
                }
                village.addBuilding(building);
            }
            fixBuildingTypeOrdinals(village);
        }

        // Villagers
        ConfigurationSection villagersSection = cfg.getConfigurationSection("villagers");
        if (villagersSection != null) {
            for (String key : villagersSection.getKeys(false)) {
                ConfigurationSection vs = villagersSection.getConfigurationSection(key);
                if (vs == null) continue;
                UUID vId = UUID.fromString(key);
                CustomVillager villager = new CustomVillager(vId,
                        vs.getString("name", "Dorfbewohner"),
                        parseVillagerJob(vs.getString("profession", "storage")));
                villager.setLevel(vs.getInt("level", 1));
                villager.setXp(vs.getDouble("xp", 0));
                villager.setWallet(vs.getDouble("wallet", 0));
                villager.setLastProductionTime(vs.getLong("last-production", System.currentTimeMillis()));
                villager.setProductionPaused(vs.getBoolean("production-paused", false));
                if (vs.contains("preferred-product")) {
                    Material preferred = Material.matchMaterial(vs.getString("preferred-product", ""));
                    if (preferred != null) {
                        villager.setPreferredProduct(preferred);
                    }
                }

                // Inventory
                ConfigurationSection invSection = vs.getConfigurationSection("inventory");
                if (invSection != null) {
                    for (String matKey : invSection.getKeys(false)) {
                        Material mat = Material.matchMaterial(matKey);
                        if (mat != null) {
                            villager.addItem(mat, invSection.getInt(matKey));
                        }
                    }
                }

                // Needs
                ConfigurationSection needsSection = vs.getConfigurationSection("needs");
                if (needsSection != null) {
                    for (VillagerNeed need : VillagerNeed.values()) {
                        villager.setNeedValue(need,
                                needsSection.getDouble(need.name().toLowerCase(), 100));
                    }
                }

                ConfigurationSection nutrientsSection = vs.getConfigurationSection("nutrients");
                if (nutrientsSection != null) {
                    ConfigurationSection capacitiesSection = vs.getConfigurationSection("nutrient-capacities");
                    for (String nutrientKey : nutrientsSection.getKeys(false)) {
                        villager.setNutrientLevel(nutrientKey, nutrientsSection.getDouble(nutrientKey));
                        if (capacitiesSection != null && capacitiesSection.contains(nutrientKey)) {
                            villager.setNutrientCapacity(nutrientKey, capacitiesSection.getDouble(nutrientKey));
                        }
                    }
                }

                // Skills
                ConfigurationSection skillsSection = vs.getConfigurationSection("skills");
                if (skillsSection != null) {
                    for (String skillKey : skillsSection.getKeys(false)) {
                        int level = skillsSection.getInt(skillKey);
                        for (int i = 0; i < level; i++) {
                            // Add XP for each level
                            villager.addSkillXp(skillKey, 100);
                        }
                    }
                }

                // Locations
                if (vs.contains("home")) {
                    villager.setHomeLocation(new Location(world,
                            vs.getInt("home.x"), vs.getInt("home.y"), vs.getInt("home.z")));
                }
                if (vs.contains("work")) {
                    villager.setWorkLocation(new Location(world,
                            vs.getInt("work.x"), vs.getInt("work.y"), vs.getInt("work.z")));
                }

                village.addVillager(villager);
            }
        }

        ConfigurationSection contractsSection = cfg.getConfigurationSection("contracts");
        if (contractsSection != null) {
            for (String key : contractsSection.getKeys(false)) {
                ConfigurationSection cs = contractsSection.getConfigurationSection(key);
                if (cs == null) continue;
                try {
                    VillagerContract contract = new VillagerContract(
                            UUID.fromString(key),
                            parseContractType(cs.getString("type", "PRODUCTION")),
                            uuidOrNull(cs.getString("requester")),
                            uuidOrNull(cs.getString("supplier")),
                            cs.getString("material"),
                            cs.getInt("amount", 1),
                            cs.getLong("created-at", System.currentTimeMillis()),
                            cs.getLong("deadline-at", 0L),
                            cs.getString("note", ""),
                            parseContractStatus(cs.getString("status", "OPEN"))
                    );
                    village.addContract(contract);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        ConfigurationSection deadSection = cfg.getConfigurationSection("last-dead-villager");
        if (deadSection != null && deadSection.contains("id")) {
            try {
                UUID deadId = UUID.fromString(deadSection.getString("id"));
                CustomVillager deadVillager = new CustomVillager(deadId,
                        deadSection.getString("name", "Dorfbewohner"),
                        parseVillagerJob(deadSection.getString("profession", "storage")));
                deadVillager.setLevel(deadSection.getInt("level", 1));
                village.setLastDeadVillager(deadVillager);
            } catch (IllegalArgumentException ignored) {
            }
        }

        // Upgrades
        ConfigurationSection upgradesSection = cfg.getConfigurationSection("upgrades");
        if (upgradesSection != null) {
            for (String key : upgradesSection.getKeys(false)) {
                village.setUpgradeLevel(key, upgradesSection.getInt(key));
            }
        }

        // Relations
        ConfigurationSection relationsSection = cfg.getConfigurationSection("relations");
        if (relationsSection != null) {
            for (String key : relationsSection.getKeys(false)) {
                ConfigurationSection relationSection = relationsSection.getConfigurationSection(key);
                if (relationSection == null) continue;
                try {
                    UUID otherVillageId = UUID.fromString(key);
                    VillageRelationType type = VillageRelationType.fromString(
                            relationSection.getString("type"));
                    VillageRelationState state = VillageRelationState.valueOf(
                            relationSection.getString("state", "ACTIVE"));
                    UUID initiator = relationSection.contains("initiator")
                            ? UUID.fromString(relationSection.getString("initiator"))
                            : null;
                    if (type != null) {
                        village.addRelation(new VillageRelation(otherVillageId, type, state, initiator));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        List<String> knownVillageIds = cfg.getStringList("known-villages");
        if (knownVillageIds != null) {
            java.util.Set<UUID> parsedKnown = new java.util.HashSet<>();
            for (String idString : knownVillageIds) {
                try {
                    parsedKnown.add(UUID.fromString(idString));
                } catch (IllegalArgumentException ignored) {
                }
            }
            village.setKnownVillageIds(parsedKnown);
        }

        return village;
    }

    /**
     * Ensures each building has a stable positive typeOrdinal (for WorldGuard ids).
     * Legacy saves without ordinal get sequential numbers per typeKey by placedAt.
     */
    /** Ensures stable positive type ordinals per building type (WorldGuard region ids). */
    public void ensureBuildingTypeOrdinals(Village village) {
        fixBuildingTypeOrdinals(village);
    }

    private void fixBuildingTypeOrdinals(Village village) {
        java.util.Map<String, java.util.List<VillageBuilding>> byType = new java.util.HashMap<>();
        for (VillageBuilding b : village.getBuildings()) {
            byType.computeIfAbsent(b.getTypeKey(), k -> new java.util.ArrayList<>()).add(b);
        }
        for (java.util.List<VillageBuilding> list : byType.values()) {
            java.util.Set<Integer> used = new java.util.HashSet<>();
            for (VillageBuilding b : list) {
                if (b.getTypeOrdinal() > 0) {
                    used.add(b.getTypeOrdinal());
                }
            }
            java.util.List<VillageBuilding> missing = new java.util.ArrayList<>();
            for (VillageBuilding b : list) {
                if (b.getTypeOrdinal() <= 0) {
                    missing.add(b);
                }
            }
            missing.sort(java.util.Comparator.comparingLong(VillageBuilding::getPlacedAt));
            int candidate = 1;
            for (VillageBuilding b : missing) {
                while (used.contains(candidate)) {
                    candidate++;
                }
                b.setTypeOrdinal(candidate);
                used.add(candidate);
                candidate++;
            }
        }
    }

    private VillageBorder loadSingleBorder(ConfigurationSection section) {
        List<int[]> points = new ArrayList<>();
        List<?> pointsList = section.getList("points");
        if (pointsList != null) {
            for (Object obj : pointsList) {
                if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) obj;
                    int x = ((Number) map.get("x")).intValue();
                    int z = ((Number) map.get("z")).intValue();
                    points.add(new int[]{x, z});
                }
            }
        }
        if (points.isEmpty()) return null;
        int minY = section.getInt("min-y", 0);
        int maxY = section.getInt("max-y", 256);
        VillageBorder border = new VillageBorder(points, minY, maxY);
        border.setId(section.getInt("id", 0));
        List<String> owners = section.getStringList("owners");
        if (owners != null && !owners.isEmpty()) {
            java.util.Set<java.util.UUID> set = new java.util.HashSet<>();
            for (String s : owners) {
                try {
                    set.add(java.util.UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {}
            }
            border.setOwners(set);
        }
        return border;
    }

    public void saveVillage(Village village) {
        File file = new File(villageDir, village.getId().toString() + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();

        cfg.set("id", village.getId().toString());
        cfg.set("name", village.getName());
        cfg.set("founder", village.getFounderId().toString());
        cfg.set("world", village.getWorldName());
        cfg.set("level", village.getLevel());
        cfg.set("points", village.getPoints());
        cfg.set("founded-at", village.getFoundedAt());

        // Bell
        Location bell = village.getBellLocation();
        cfg.set("bell.x", bell.getBlockX());
        cfg.set("bell.y", bell.getBlockY());
        cfg.set("bell.z", bell.getBlockZ());

        // Borders (multiple border regions)
        int borderIndex = 0;
        for (VillageBorder border : village.getBorders()) {
            String path = "borders." + borderIndex;
            cfg.set(path + ".id", border.getId());
            List<Map<String, Integer>> borderPoints = new ArrayList<>();
            for (int[] point : border.getBorderPoints()) {
                Map<String, Integer> map = new java.util.LinkedHashMap<>();
                map.put("x", point[0]);
                map.put("z", point[1]);
                borderPoints.add(map);
            }
            cfg.set(path + ".points", borderPoints);
            cfg.set(path + ".min-y", border.getMinY());
            cfg.set(path + ".max-y", border.getMaxY());
            java.util.List<String> owners = new java.util.ArrayList<>();
            for (java.util.UUID u : border.getOwners()) owners.add(u.toString());
            cfg.set(path + ".owners", owners);
            borderIndex++;
        }

        // Members
        for (Map.Entry<UUID, VillageMember> entry : village.getMembers().entrySet()) {
            String path = "members." + entry.getKey().toString();
            cfg.set(path + ".role", entry.getValue().getRole().name());
            java.util.List<String> roleList = entry.getValue().getRoles().stream()
                    .map(Enum::name)
                    .toList();
            cfg.set(path + ".roles", roleList);
            cfg.set(path + ".joined-at", entry.getValue().getJoinedAt());
        }

        for (Map.Entry<UUID, VillageJoinRequest> entry : village.getJoinRequests().entrySet()) {
            String path = "join-requests." + entry.getKey();
            cfg.set(path + ".requested-at", entry.getValue().getRequestedAt());
        }

        // Buildings
        for (VillageBuilding building : village.getBuildings()) {
            String path = "buildings." + building.getId().toString();
            cfg.set(path + ".type", building.getTypeKey());
            cfg.set(path + ".x", building.getLocation().getBlockX());
            cfg.set(path + ".y", building.getLocation().getBlockY());
            cfg.set(path + ".z", building.getLocation().getBlockZ());
            cfg.set(path + ".completed", building.isCompleted());
            cfg.set(path + ".placed-at", building.getPlacedAt());
            cfg.set(path + ".level", building.getLevel());
            cfg.set(path + ".type-ordinal", building.getTypeOrdinal());
            cfg.set(path + ".direction", building.getDirection());
            if (building.getSchematicName() != null) {
                cfg.set(path + ".schematic", building.getSchematicName());
            }
            if (building.getOwnerId() != null) {
                cfg.set(path + ".owner", building.getOwnerId().toString());
            }
            cfg.set(path + ".sign.hidden", building.isSignHidden());
            cfg.set(path + ".access.all-members", building.isAccessAllMembers());
            java.util.List<String> accessList = new java.util.ArrayList<>();
            for (UUID u : building.getAccessList()) accessList.add(u.toString());
            cfg.set(path + ".access.list", accessList);
            if (building.getSignTemplate() != null) {
                cfg.set(path + ".sign.template", building.getSignTemplate());
            }
            if (building.getCustomName() != null) {
                cfg.set(path + ".custom-name", building.getCustomName());
            }
            if (building.getSignLocation() != null) {
                cfg.set(path + ".sign.x", building.getSignLocation().getBlockX());
                cfg.set(path + ".sign.y", building.getSignLocation().getBlockY());
                cfg.set(path + ".sign.z", building.getSignLocation().getBlockZ());
            }
            if (building.hasAestheticScore()) {
                cfg.set(path + ".aesthetic-score", building.getAestheticScore());
            }
        }

        // Villagers
        for (CustomVillager villager : village.getVillagers()) {
            String path = "villagers." + villager.getId().toString();
            cfg.set(path + ".name", villager.getName());
            cfg.set(path + ".profession", villager.getProfessionKey());
            cfg.set(path + ".level", villager.getLevel());
            cfg.set(path + ".xp", villager.getXp());
            cfg.set(path + ".wallet", villager.getWallet());
            cfg.set(path + ".last-production", villager.getLastProductionTime());
            cfg.set(path + ".production-paused", villager.isProductionPaused());
            if (villager.getPreferredProduct() != null) {
                cfg.set(path + ".preferred-product", villager.getPreferredProduct().name());
            }

            for (Map.Entry<Material, Integer> invEntry : villager.getInventory().entrySet()) {
                cfg.set(path + ".inventory." + invEntry.getKey().name(), invEntry.getValue());
            }

            for (VillagerNeed need : VillagerNeed.values()) {
                cfg.set(path + ".needs." + need.name().toLowerCase(), villager.getNeedValue(need));
            }

            for (Map.Entry<String, Double> nutrientEntry : villager.getNutrientLevels().entrySet()) {
                cfg.set(path + ".nutrients." + nutrientEntry.getKey(), nutrientEntry.getValue());
                cfg.set(path + ".nutrient-capacities." + nutrientEntry.getKey(),
                        villager.getNutrientCapacity(nutrientEntry.getKey()));
            }

            for (Map.Entry<String, VillagerSkill> skillEntry : villager.getSkills().entrySet()) {
                cfg.set(path + ".skills." + skillEntry.getKey(), (int) skillEntry.getValue().getLevel());
            }

            if (villager.getHomeLocation() != null) {
                cfg.set(path + ".home.x", villager.getHomeLocation().getBlockX());
                cfg.set(path + ".home.y", villager.getHomeLocation().getBlockY());
                cfg.set(path + ".home.z", villager.getHomeLocation().getBlockZ());
            }

            if (villager.getWorkLocation() != null) {
                cfg.set(path + ".work.x", villager.getWorkLocation().getBlockX());
                cfg.set(path + ".work.y", villager.getWorkLocation().getBlockY());
                cfg.set(path + ".work.z", villager.getWorkLocation().getBlockZ());
            }
        }

        // Upgrades
        for (Map.Entry<String, Integer> entry : village.getUpgrades().entrySet()) {
            cfg.set("upgrades." + entry.getKey(), entry.getValue());
        }

        // Last Dead Villager (für Wiederbelebung)
        if (village.getLastDeadVillager() != null) {
            CustomVillager dead = village.getLastDeadVillager();
            cfg.set("last-dead-villager.id", dead.getId().toString());
            cfg.set("last-dead-villager.name", dead.getName());
            cfg.set("last-dead-villager.profession", dead.getProfessionKey());
            cfg.set("last-dead-villager.level", dead.getLevel());
        }

        // Contracts
        for (VillagerContract contract : village.getContracts()) {
            String path = "contracts." + contract.getId();
            cfg.set(path + ".type", contract.getType().name());
            if (contract.getRequesterVillagerId() != null) {
                cfg.set(path + ".requester", contract.getRequesterVillagerId().toString());
            }
            if (contract.getSupplierVillagerId() != null) {
                cfg.set(path + ".supplier", contract.getSupplierVillagerId().toString());
            }
            cfg.set(path + ".material", contract.getMaterialKey());
            cfg.set(path + ".amount", contract.getAmount());
            cfg.set(path + ".created-at", contract.getCreatedAt());
            cfg.set(path + ".deadline-at", contract.getDeadlineAt());
            cfg.set(path + ".note", contract.getNote());
            cfg.set(path + ".status", contract.getStatus().name());
        }

        cfg.set("revival.uses", village.getReviveUses());
        cfg.set("revival.last-at", village.getLastReviveAt());

        // Relations
        for (VillageRelation relation : village.getRelations().values()) {
            String path = "relations." + relation.getOtherVillageId().toString();
            cfg.set(path + ".type", relation.getType().name());
            cfg.set(path + ".state", relation.getState().name());
            if (relation.getInitiatorVillageId() != null) {
                cfg.set(path + ".initiator", relation.getInitiatorVillageId().toString());
            }
        }

        java.util.List<String> knownVillageIds = new java.util.ArrayList<>();
        for (UUID knownId : village.getKnownVillageIds()) {
            knownVillageIds.add(knownId.toString());
        }
        cfg.set("known-villages", knownVillageIds);

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Speichern von Dorf: " + village.getName(), e);
        }
    }

    public void saveAllVillages() {
        for (Village village : villages.values()) {
            saveVillage(village);
        }
    }

    // Utility methods
    private VillagerJob parseVillagerJob(String jobStr) {
        return VillagerJob.fromString(jobStr);
    }

    private VillagerContract.Type parseContractType(String value) {
        try {
            return VillagerContract.Type.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            return VillagerContract.Type.PRODUCTION;
        }
    }

    private VillagerContract.Status parseContractStatus(String value) {
        try {
            return VillagerContract.Status.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            return VillagerContract.Status.OPEN;
        }
    }

    private UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
