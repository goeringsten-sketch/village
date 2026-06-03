package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.model.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Walk-Session-Management für Pfad/Straße/Schiene-Gebäude.
 *
 * Ablauf:
 * 1. startPathSession()  – Spieler beginnt Strecke abzulaufen
 * 2. handleMove()        – erfasst 3×N-Streifen, visualisiert Ränder
 * 3. finalizePathSession()– Oberflächencheck → PathZone registrieren
 * 4. cancelPathSession() – Session abbrechen
 */
public final class PathService {

    private final VillagePlugin plugin;
    private final VillageManager villageManager;
    private final BuildingConfigLoader configLoader;

    private final Map<UUID, PathWalkSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, PathZone> pathZones = new ConcurrentHashMap<>();  // buildingId → zone

    public PathService(VillagePlugin plugin, VillageManager villageManager, BuildingConfigLoader configLoader) {
        this.plugin        = plugin;
        this.villageManager = villageManager;
        this.configLoader  = configLoader;
    }

    // ── Session starten ───────────────────────────────────────────

    public boolean startPathSession(Player player, Village village, VillageBuilding building, BuildingDefinition def) {
        if (activeSessions.containsKey(player.getUniqueId())) {
            player.sendMessage("§cDu hast bereits eine aktive Pfad-Session. §e/village path cancel");
            return false;
        }
        PathConfig cfg = getEffectivePathConfig(building, def);
        activeSessions.put(player.getUniqueId(), new PathWalkSession(village, building, def, cfg, player.getLocation()));

        player.sendMessage("§a✓ Pfad-Aufzeichnung gestartet!");
        player.sendMessage("§7Breite: §e" + cfg.getWidth() + " §7Blöcke | Oberfläche: §e"
                + cfg.getRequiredSurfaceBlock().name() + " §7(min §e" + cfg.getRequiredSurfacePercentage() + "%§7)");
        player.sendMessage("§7§e/village path done §7→ Abschließen | §e/village path cancel §7→ Abbrechen");
        return true;
    }

    // ── Bewegungs-Update ──────────────────────────────────────────

    public void handleMove(Player player, Location from, Location to) {
        PathWalkSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;

        int cx = to.getBlockX(), cz = to.getBlockZ();
        World world = to.getWorld();
        int halfW = session.getConfig().getWidth() / 2;
        int dx = to.getBlockX() - from.getBlockX();
        int dz = to.getBlockZ() - from.getBlockZ();

        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps == 0) steps = 1;

        // Interpoliere zwischen from -> to, um Lücken bei schneller Bewegung zu vermeiden
        for (int s = 0; s <= steps; s++) {
            double t = steps == 0 ? 0.0 : (s / (double) steps);
            int baseX = from.getBlockX() + (int) Math.round(dx * t);
            int baseZ = from.getBlockZ() + (int) Math.round(dz * t);

            boolean nsMovement = Math.abs(dz) >= Math.abs(dx);
            for (int i = -halfW; i <= halfW; i++) {
                int bx = nsMovement ? baseX + i : baseX;
                int bz = nsMovement ? baseZ     : baseZ + i;

                long key = packXZ(bx, bz);
                if (session.blockSet.contains(key)) continue; // schneller Lookup
                session.blockSet.add(key);
                session.recordedXZ.add(key);

                // Surface Y mit einfachem per-session Cache
                Integer cachedY = session.surfaceYCache.get(key);
                int sy = cachedY != null ? cachedY : getSurfaceY(world, bx, bz, session.getConfig());
                session.surfaceYCache.putIfAbsent(key, sy);

                session.positions.add(new int[]{bx, sy, bz});

                // Randblöcke (i == -halfW oder +halfW) visualisieren
                boolean isEdge = session.getConfig().getWidth() > 1 && (i == -halfW || i == halfW);
                if (isEdge) {
                    Location borderLoc = new Location(world, bx, sy, bz);
                    player.sendBlockChange(borderLoc, session.getConfig().getVisualizeBorderBlock().createBlockData());
                    session.borderBlocks.add(borderLoc);
                }
            }
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text(
            "§6Pfad §7| §e" + session.positions.size() + " §7Blöcke erfasst"));
    }

    // ── Abschließen ───────────────────────────────────────────────

    public boolean finalizePathSession(Player player) {
        PathWalkSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) { player.sendMessage("§cKeine aktive Pfad-Session."); return false; }
        clearVis(player, session);

        if (session.positions.size() < 3) {
            player.sendMessage("§cPfad zu kurz (min. 3 Blöcke).");
            return false;
        }
        // -> Komponentenfilter: wähle die größte zusammenhängende Komponente
        Set<Long> allBlocks = new HashSet<>(session.blockSet);
        if (allBlocks.isEmpty()) { player.sendMessage("§cKeine Blöcke erfasst."); return false; }

        Set<Long> visited = new HashSet<>();
        Set<Long> largestComp = new HashSet<>();
        ArrayDeque<Long> dq = new ArrayDeque<>();

        for (Long node : allBlocks) {
            if (visited.contains(node)) continue;
            Set<Long> comp = new HashSet<>();
            visited.add(node);
            dq.clear(); dq.add(node);
            while (!dq.isEmpty()) {
                long cur = dq.removeFirst();
                comp.add(cur);
                int cx2 = (int) (cur >> 32);
                int cz2 = (int) cur;
                // 4-neighbors (N,S,E,W)
                long n1 = packXZ(cx2 + 1, cz2);
                long n2 = packXZ(cx2 - 1, cz2);
                long n3 = packXZ(cx2, cz2 + 1);
                long n4 = packXZ(cx2, cz2 - 1);
                if (allBlocks.contains(n1) && !visited.contains(n1)) { visited.add(n1); dq.add(n1); }
                if (allBlocks.contains(n2) && !visited.contains(n2)) { visited.add(n2); dq.add(n2); }
                if (allBlocks.contains(n3) && !visited.contains(n3)) { visited.add(n3); dq.add(n3); }
                if (allBlocks.contains(n4) && !visited.contains(n4)) { visited.add(n4); dq.add(n4); }
            }
            if (comp.size() > largestComp.size()) largestComp = comp;
        }

        if (largestComp.isEmpty()) { player.sendMessage("§cKeine gültige Komponente gefunden."); return false; }

        // Filter positions nach größter Komponente (bewahrt Reihenfolge)
        List<int[]> filtered = new ArrayList<>();
        for (int[] pos : session.positions) {
            if (largestComp.contains(packXZ(pos[0], pos[2]))) filtered.add(pos);
        }

        if (filtered.size() < 3) { player.sendMessage("§cPfad nach Filterung zu kurz."); return false; }

        // Oberflächencheck auf gefilterter Komponente
        World world = player.getWorld();
        PathConfig cfg = session.getConfig();
        int total = 0, matching = 0;
        for (int[] pos : filtered) {
            int sy = session.surfaceYCache.getOrDefault(packXZ(pos[0], pos[2]), getSurfaceY(world, pos[0], pos[2], cfg));
            if (!isSkyVisible(world, pos[0], sy, pos[2], cfg)) continue;
            total++;
            if (world.getBlockAt(pos[0], sy, pos[2]).getType() == cfg.getRequiredSurfaceBlock()) matching++;
        }

        if (total == 0) { player.sendMessage("§cKeine Oberflächenblöcke mit Himmelsicht gefunden."); return false; }

        int pct = (int) ((matching * 100.0) / total);
        if (pct < cfg.getRequiredSurfacePercentage()) {
            player.sendMessage("§cZu wenig §e" + cfg.getRequiredSurfaceBlock().name()
                + "§c: §e" + pct + "% §c(benötigt §e" + cfg.getRequiredSurfacePercentage() + "%§c)");
            return false;
        }

        PathZone zone = new PathZone(session.building.getId(), session.village.getId(),
            session.def.getId(), new ArrayList<>(filtered), cfg);
        pathZones.put(session.building.getId(), zone);

        boolean alreadyPresent = session.village.getBuildings().stream()
                .anyMatch(b -> b.getId().equals(session.building.getId()));
        if (!alreadyPresent) {
            session.village.addBuilding(session.building);
        }

        session.building.setCompleted(true);
        villageManager.saveVillage(session.village);

        player.sendMessage("§a✓ Pfad registriert! §e" + filtered.size()
            + " §aBlöcke – Oberfläche §e" + pct + "%");
        player.sendMessage("§7Speed-Bonus: §eSpeed " + (cfg.getSpeedAmplifier() + 1)
            + " §7(§e" + cfg.getDurationAfterLeaveTicks() / 20 + "s §7Nachlauf)");
        return true;
    }

    public void cancelPathSession(Player player) {
        PathWalkSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) { player.sendMessage("§cKeine aktive Pfad-Session."); return; }
        clearVis(player, session);
        player.sendMessage("§cPfad-Aufzeichnung abgebrochen.");
    }

    public PathConfig getEffectivePathConfig(VillageBuilding building, BuildingDefinition def) {
        PathConfig base = def != null ? def.getPathConfig() : null;
        if (base == null) base = PathConfig.defaultPath();
        if (!base.isWidthUpgradeable()) return base;
        int upgradedWidth = Math.min(base.getMaxWidth(), base.getWidth() + base.getWidthIncrementPerLevel() * Math.max(0, building.getLevel() - 1));

        int baseAmp = Math.max(0, base.getSpeedAmplifier());
        int upgradedAmp = Math.min(base.getMaxSpeedAmplifier(),
                baseAmp + base.getSpeedAmplifierPerLevel() * Math.max(0, building.getLevel() - 1));

        int baseDur = Math.max(0, base.getDurationAfterLeaveTicks());
        int upgradedDur = baseDur + base.getDurationAfterLeaveTicksPerLevel() * Math.max(0, building.getLevel() - 1);

        // If nothing changed, return base
        if (upgradedWidth == base.getWidth() && upgradedAmp == baseAmp && upgradedDur == baseDur) return base;

        return new PathConfig(
            upgradedWidth,
            base.isWidthUpgradeable(),
            base.getMaxWidth(),
            base.getVisualizeBorderBlock(),
            base.getSurfaceCheckMode(),
            base.getMinSkylight(),
            base.getRequiredSurfaceBlock(),
            base.getRequiredSurfacePercentage(),
            base.getValidSurfaceBlocks(),
            base.getValidBaseBlocks(),
            upgradedAmp,
            base.getMaxSpeedAmplifier(),
            base.getSpeedAmplifierPerLevel(),
            upgradedDur,
            base.getDurationAfterLeaveTicksPerLevel(),
            base.getWidthIncrementPerLevel()
        );
    }

    public void refreshZoneForBuilding(VillageBuilding building, BuildingDefinition def) {
        if (building == null || def == null || !def.isPath()) return;
        PathZone existing = pathZones.get(building.getId());
        if (existing == null) return;
        PathConfig updatedConfig = getEffectivePathConfig(building, def);
        PathZone refreshed = new PathZone(existing.getBuildingId(), existing.getVillageId(), existing.getDefinitionId(),
                new ArrayList<>(existing.getPositions()), updatedConfig);
        pathZones.put(building.getId(), refreshed);
    }

    // ── Zonen-API ─────────────────────────────────────────────────

    public PathZone getZoneAt(Location loc) {
        int x = loc.getBlockX(), z = loc.getBlockZ();
        for (PathZone zone : pathZones.values()) if (zone.contains(x, z)) return zone;
        return null;
    }

    public boolean isInPathSession(UUID id)             { return activeSessions.containsKey(id); }
    public void registerZone(PathZone zone)             { pathZones.put(zone.buildingId, zone); }
    public void removeZone(UUID buildingId)             { pathZones.remove(buildingId); }
    public Collection<PathZone> getAllZones()           { return Collections.unmodifiableCollection(pathZones.values()); }

    // ── Hilfsmethoden ─────────────────────────────────────────────

    private int getSurfaceY(World w, int x, int z, PathConfig cfg) {
        if ("skylight".equals(cfg.getSurfaceCheckMode())) return w.getHighestBlockYAt(x, z);
        for (int y = w.getMaxHeight() - 1; y >= w.getMinHeight(); y--) {
            Block b = w.getBlockAt(x, y, z), above = w.getBlockAt(x, y + 1, z);
            if (b.getType().isSolid() && !above.getType().isSolid()) return y;
        }
        return w.getHighestBlockYAt(x, z);
    }

    private boolean isSkyVisible(World w, int x, int y, int z, PathConfig cfg) {
        return "skylight".equals(cfg.getSurfaceCheckMode())
            ? w.getBlockAt(x, y, z).getLightFromSky() >= cfg.getMinSkylight()
            : true;
    }

    private void clearVis(Player player, PathWalkSession session) {
        World w = player.getWorld();
        session.borderBlocks.forEach(loc -> {
            if (w.equals(loc.getWorld())) player.sendBlockChange(loc, w.getBlockAt(loc).getBlockData());
        });
    }

    private long packXZ(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }

    // ── Nested: PathWalkSession ───────────────────────────────────

    public static final class PathWalkSession {
        final Village village;
        final VillageBuilding building;
        final BuildingDefinition def;
        final PathConfig config;
        final Location startLocation;
        final Set<Long> recordedXZ      = new LinkedHashSet<>();
        final List<int[]> positions     = new ArrayList<>();
        final List<Location> borderBlocks = new ArrayList<>();
        final Set<Long> blockSet = new HashSet<>();
        final Map<Long, Integer> surfaceYCache = new HashMap<>();

        PathWalkSession(Village v, VillageBuilding b, BuildingDefinition d, PathConfig c, Location s) {
            village = v; building = b; def = d; config = c; startLocation = s;
        }
        public PathConfig getConfig() { return config; }
    }

    // ── Nested: PathZone ──────────────────────────────────────────

    public static final class PathZone {
        final UUID buildingId, villageId;
        final String definitionId;
        final List<int[]> positions;
        final PathConfig config;
        final Set<Long> blockSet;

        public PathZone(UUID bid, UUID vid, String defId, List<int[]> pos, PathConfig cfg) {
            buildingId = bid; villageId = vid; definitionId = defId;
            positions  = Collections.unmodifiableList(pos); config = cfg;
            Set<Long> s = new HashSet<>();
            pos.forEach(p -> s.add(((long) p[0] << 32) | (p[2] & 0xFFFFFFFFL)));
            blockSet = Collections.unmodifiableSet(s);
        }

        public boolean contains(int x, int z)   { return blockSet.contains(((long) x << 32) | (z & 0xFFFFFFFFL)); }
        public UUID getBuildingId()              { return buildingId; }
        public UUID getVillageId()               { return villageId; }
        public String getDefinitionId()          { return definitionId; }
        public List<int[]> getPositions()        { return positions; }
        public PathConfig getConfig()            { return config; }
    }
}
