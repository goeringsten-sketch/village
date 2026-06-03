package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.config.VillageLightConfigManager;
import com.example.village.hook.ProtocolLibHook;
import com.example.village.hook.WorldGuardHook;
import com.example.village.model.LightPathSource;
import com.example.village.model.LightPointSource;
import com.example.village.model.LightRegionSource;
import com.example.village.model.Village;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class VillageLightService {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VillageLightConfigManager lightConfigManager;
    private final VillageManager villageManager;
    private final ReflectionLightPacketAdapter lightPacketAdapter;
    private final ProtocolLibHook protocolLibHook;
    private final WorldGuardHook worldGuardHook;
    private final Map<UUID, PlayerLightState> playerStates = new ConcurrentHashMap<>();
    private final Map<UUID, ChunkRefreshTask> pendingRefreshes = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, Integer>> chunkLightCaches = new ConcurrentHashMap<>();

    private BukkitTask updateTask;
    private ProtocolLibLightInterceptor packetInterceptor;
    private boolean interceptingOutgoingPackets;

    public VillageLightService(VillagePlugin plugin,
                               VillageConfigManager configManager,
                               VillageLightConfigManager lightConfigManager,
                               VillageManager villageManager,
                               ProtocolLibHook protocolLibHook,
                               WorldGuardHook worldGuardHook) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.lightConfigManager = lightConfigManager;
        this.villageManager = villageManager;
        this.protocolLibHook = protocolLibHook;
        this.worldGuardHook = worldGuardHook;
        this.lightPacketAdapter = new ReflectionLightPacketAdapter(plugin);
    }

    public void start() {
        if (!lightConfigManager.isEnabled()) {
            plugin.getLogger().info("Village-Lichtsystem ist in light-limits.yml deaktiviert.");
            return;
        }

        if (!lightPacketAdapter.isAvailable()) {
            plugin.getLogger().warning("Village-Lichtsystem konnte nicht aktiviert werden, weil der NMS-Light-Adapter nicht verfuegbar ist.");
            return;
        }

        interceptingOutgoingPackets = false;
        if (protocolLibHook.isAvailable()) {
            packetInterceptor = new ProtocolLibLightInterceptor(plugin, this, lightPacketAdapter);
            interceptingOutgoingPackets = packetInterceptor.start();
        }

        int interval = lightConfigManager.getUpdateIntervalTicks();
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
        plugin.getDebugConfigManager().debug("light", "Village light service enabled with interval " + interval + " ticks");
        plugin.getLogger().info("Village-Lichtsystem aktiviert.");
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        if (packetInterceptor != null) {
            packetInterceptor.stop();
            packetInterceptor = null;
        }
        interceptingOutgoingPackets = false;
        cancelAllPendingRefreshes();

        for (Player player : Bukkit.getOnlinePlayers()) {
            resetPlayerLight(player, true);
        }
        playerStates.clear();
        clearLightCaches();
    }

    public void clearLightCaches() {
        chunkLightCaches.clear();
    }

    public void reloadConfiguration() {
        lightConfigManager.load();
        clearLightCaches();
        plugin.getDebugConfigManager().debug("light", "Light configuration reloaded");
    }

    public boolean isActive() {
        return lightConfigManager.isEnabled() && lightPacketAdapter.isAvailable();
    }

    public void refreshPlayer(Player player) {
        if (!isActive()) {
            return;
        }
        updatePlayer(player, true);
    }

    public void checkPlayer(Player player) {
        if (!isActive()) {
            return;
        }
        updatePlayer(player, false);
    }

    public void forgetPlayer(Player player) {
        playerStates.remove(player.getUniqueId());
        ChunkRefreshTask task = pendingRefreshes.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public void refreshChunk(Player player, Chunk chunk) {
        if (!isActive()) {
            return;
        }
        if (interceptingOutgoingPackets) {
            return;
        }

        int maxLightLevel = getTargetLightLevelForChunk(player, chunk.getX(), chunk.getZ());
        if (maxLightLevel >= 15) {
            return;
        }
        lightPacketAdapter.sendClampedLight(player, chunk, maxLightLevel);
    }

    public void refreshChunkForNearbyTrackedPlayers(World world, int chunkX, int chunkZ) {
        if (!isActive()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(world)) {
                continue;
            }
            PlayerLightState state = playerStates.get(player.getUniqueId());
            if (state == null) {
                continue;
            }

            int radius = lightConfigManager.getRefreshRadiusChunks();
            Chunk playerChunk = player.getLocation().getChunk();
            if (Math.abs(playerChunk.getX() - chunkX) > radius || Math.abs(playerChunk.getZ() - chunkZ) > radius) {
                continue;
            }

            if (world.isChunkLoaded(chunkX, chunkZ)) {
                int maxLightLevel = getTargetLightLevelForChunk(player, chunkX, chunkZ);
                if (maxLightLevel < 15) {
                    lightPacketAdapter.sendClampedLight(player, world.getChunkAt(chunkX, chunkZ), maxLightLevel);
                }
            }
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player, false);
        }
    }

    private void updatePlayer(Player player, boolean forceRefresh) {
        if (!player.isOnline()) {
            return;
        }

        Location location = player.getLocation();
        UUID playerId = player.getUniqueId();
        PlayerLightState previous = playerStates.get(playerId);

        int maxLightLevel = getTargetLightLevel(player);
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "";
        int blockX = location.getBlockX();
        int blockY = location.getBlockY();
        int blockZ = location.getBlockZ();

        boolean changedPosition = previous == null
                || previous.blockX != blockX
                || previous.blockY != blockY
                || previous.blockZ != blockZ
                || !previous.worldName.equals(worldName);
        int chunkX = location.getChunk().getX();
        int chunkZ = location.getChunk().getZ();
        boolean changedChunk = previous == null
                || previous.chunkX != chunkX
                || previous.chunkZ != chunkZ
                || !previous.worldName.equals(worldName);

        if (!forceRefresh && !changedPosition && previous != null && previous.maxLightLevel == maxLightLevel) {
            return;
        }

        playerStates.put(playerId, new PlayerLightState(worldName, blockX, blockY, blockZ, chunkX, chunkZ, maxLightLevel));

        if (previous == null || previous.maxLightLevel != maxLightLevel || forceRefresh) {
            int radius = (previous != null && previous.maxLightLevel != maxLightLevel)
                    ? lightConfigManager.getStageChangeRefreshRadiusChunks()
                    : lightConfigManager.getRefreshRadiusChunks();
            resendNearbyChunks(player, -1, radius);
            return;
        }

        if (changedChunk && maxLightLevel < 15) {
            int radius = lightConfigManager.getChunkMoveRefreshRadiusChunks();
            if (radius > 0) {
                resendNearbyChunks(player, -1, radius);
            }
        }
    }

    public int getTargetLightLevel(Player player) {
        Location location = player.getLocation();
        return getTargetLightLevelAt(player, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public int getTargetLightLevelForChunk(Player player, int chunkX, int chunkZ) {
        String cacheKey = buildLightCacheKey(player);
        Map<Long, Integer> cache = chunkLightCaches.computeIfAbsent(cacheKey, k -> new ConcurrentHashMap<>());
        long packed = packChunk(chunkX, chunkZ);
        return cache.computeIfAbsent(packed, key -> computeChunkLightLevel(player, chunkX, chunkZ));
    }

    private int computeChunkLightLevel(Player player, int chunkX, int chunkZ) {
        int minBlockX = chunkX << 4;
        int minBlockZ = chunkZ << 4;
        int maxBlockX = minBlockX + 15;
        int maxBlockZ = minBlockZ + 15;
        int playerY = player.getLocation().getBlockY();
        int[] sampleX = new int[]{minBlockX, minBlockX + 5, minBlockX + 10, maxBlockX};
        int[] sampleZ = new int[]{minBlockZ, minBlockZ + 5, minBlockZ + 10, maxBlockZ};

        int maxLightLevel = 0;
        for (int x : sampleX) {
            for (int z : sampleZ) {
                maxLightLevel = Math.max(maxLightLevel, getTargetLightLevelAt(player, x, playerY, z));
                if (maxLightLevel >= 15) {
                    return 15;
                }
            }
        }
        return maxLightLevel;
    }

    private int getTargetLightLevelAt(Player player, int blockX, int blockY, int blockZ) {
        String worldName = player.getWorld().getName();
        if (!lightConfigManager.isWorldEnabled(worldName)) {
            return 15;
        }

        int maxLightLevel = lightConfigManager.getDefaultMaxLightLevel();
        List<Village> lightGroup = buildLightGroup(player);
        maxLightLevel = Math.max(maxLightLevel, getTargetLightLevelForGroup(worldName, blockX, blockZ, lightGroup));

        for (LightPointSource source : lightConfigManager.getPointSources()) {
            if (!worldName.equals(source.worldName())) continue;
            maxLightLevel = Math.max(maxLightLevel, getPointSourceLightLevel(source, blockX, blockY, blockZ));
        }

        for (LightRegionSource source : lightConfigManager.getRegionSources()) {
            if (!worldName.equals(source.worldName())) continue;
            maxLightLevel = Math.max(maxLightLevel, getRegionSourceLightLevel(player.getWorld(), source, blockX, blockZ));
        }

        for (LightPathSource source : lightConfigManager.getPathSources()) {
            if (!worldName.equals(source.worldName())) continue;
            maxLightLevel = Math.max(maxLightLevel, getPathSourceLightLevel(source, blockX, blockZ));
        }

        return Math.max(0, Math.min(15, maxLightLevel));
    }

    private int getVillageLightLevel(Village village, int blockX, int blockZ) {
        double distanceToBorder = village.getDistanceToBorder(blockX, blockZ);
        int outsideDistance = (int) Math.max(0, Math.ceil(distanceToBorder));
        return lightConfigManager.getMaxLightLevelForDistance(outsideDistance);
    }

    private int getTargetLightLevelForGroup(String worldName, int blockX, int blockZ, List<Village> lightGroup) {
        int maxLightLevel = lightConfigManager.getDefaultMaxLightLevel();
        for (Village village : lightGroup) {
            maxLightLevel = Math.max(maxLightLevel, getVillageLightLevel(village, blockX, blockZ));
            maxLightLevel = Math.max(maxLightLevel, getVillageBellPointLightLevel(village, blockX, blockZ));
        }
        return maxLightLevel;
    }

    private List<Village> buildLightGroup(Player player) {
        Optional<Village> playerVillage = villageManager.getPlayerVillage(player.getUniqueId());
        if (playerVillage.isEmpty()) {
            return List.of();
        }

        Village village = playerVillage.get();
        if (village.getWorldName() == null || !village.getWorldName().equals(player.getWorld().getName())) {
            return List.of();
        }

        List<Village> group = new ArrayList<>();
        group.add(village);
        for (Village other : villageManager.getAllVillages()) {
            if (other.equals(village) || !player.getWorld().getName().equals(other.getWorldName())) {
                continue;
            }
            if (villageManager.areVillagesFriendly(village, other)) {
                group.add(other);
            }
        }
        return List.copyOf(group);
    }

    private String buildLightCacheKey(Player player) {
        String worldName = player.getWorld().getName();
        Optional<Village> playerVillage = villageManager.getPlayerVillage(player.getUniqueId());
        if (playerVillage.isEmpty()) {
            return worldName + "|static";
        }

        Set<String> ids = new HashSet<>();
        ids.add(playerVillage.get().getId().toString());
        for (Village other : villageManager.getAllVillages()) {
            if (other.equals(playerVillage.get()) || !worldName.equals(other.getWorldName())) {
                continue;
            }
            if (villageManager.areVillagesFriendly(playerVillage.get(), other)) {
                ids.add(other.getId().toString());
            }
        }

        String joinedIds = ids.stream().sorted().collect(Collectors.joining(","));
        return worldName + "|villages:" + joinedIds;
    }

    private int getVillageBellPointLightLevel(Village village, int blockX, int blockZ) {
        Location bellLocation = village.getBellLocation();
        if (bellLocation == null) {
            return 0;
        }
        return getCubeSourceLightLevel(
                blockX,
                bellLocation.getBlockY(),
                blockZ,
                bellLocation.getX(),
                bellLocation.getY(),
                bellLocation.getZ(),
                village.getBellRadius(),
                lightConfigManager.getSteps(),
                lightConfigManager.isCircularStages()
        );
    }

    private int getPointSourceLightLevel(LightPointSource source, int blockX, int blockY, int blockZ) {
        return getCubeSourceLightLevel(
                blockX,
                blockY,
                blockZ,
                source.x(),
                source.y(),
                source.z(),
                source.radius(),
                source.stages(),
                lightConfigManager.isCircularStages()
        );
    }

    private int getCubeSourceLightLevel(int blockX, int blockY, int blockZ,
                                        double sourceX, double sourceY, double sourceZ,
                                        double radius,
                                        List<com.example.village.model.LightDistanceStep> stages,
                                        boolean circular) {
        double dx = Math.abs(blockX - sourceX);
        double dy = Double.isNaN(sourceY) ? 0 : Math.abs(blockY - sourceY);
        double dz = Math.abs(blockZ - sourceZ);
        double distance = circular
                ? Math.sqrt(dx * dx + dy * dy + dz * dz)
                : Math.max(dx, Math.max(dy, dz));
        int outsideDistance = (int) Math.max(0, Math.ceil(Math.max(0, distance - radius)));
        return lightConfigManager.getMaxLightLevelForDistance(stages, outsideDistance);
    }

    private int getRegionSourceLightLevel(World world, LightRegionSource source, int blockX, int blockZ) {
        double distance = worldGuardHook.distanceToRegionBoundary(world, source.regionId(), blockX, blockZ);
        if (Double.isInfinite(distance)) {
            return lightConfigManager.getDefaultMaxLightLevel();
        }
        int outsideDistance = (int) Math.max(0, Math.ceil(distance - source.baseRadius()));
        return lightConfigManager.getMaxLightLevelForDistance(outsideDistance);
    }

    private int getPathSourceLightLevel(LightPathSource source, int blockX, int blockZ) {
        double distance = distancePointToSegment(
                blockX, blockZ,
                source.fromX(), source.fromZ(),
                source.toX(), source.toZ());
        int outsideDistance = (int) Math.max(0, Math.ceil(distance - source.radius()));
        return lightConfigManager.getMaxLightLevelForDistance(outsideDistance);
    }

    private double distancePointToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared == 0) {
            double pointDx = px - ax;
            double pointDz = pz - az;
            return Math.sqrt(pointDx * pointDx + pointDz * pointDz);
        }

        double t = ((px - ax) * dx + (pz - az) * dz) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        double projX = ax + t * dx;
        double projZ = az + t * dz;
        double distX = px - projX;
        double distZ = pz - projZ;
        return Math.sqrt(distX * distX + distZ * distZ);
    }

    private void resendNearbyChunks(Player player, int maxLightLevel, int radius) {
        World world = player.getWorld();
        Chunk center = player.getLocation().getChunk();
        Queue<ChunkCoordinate> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();

        for (int cx = center.getX() - radius; cx <= center.getX() + radius; cx++) {
            for (int cz = center.getZ() - radius; cz <= center.getZ() + radius; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                long packed = packChunk(cx, cz);
                if (seen.add(packed)) {
                    queue.add(new ChunkCoordinate(cx, cz));
                }
            }
        }

        scheduleChunkRefresh(player, world, maxLightLevel, queue);
    }

    private void resetPlayerLight(Player player) {
        resetPlayerLight(player, false);
    }

    private void resetPlayerLight(Player player, boolean immediate) {
        if (!player.isOnline()) {
            return;
        }
        if (immediate) {
            resendNearbyChunksImmediately(player, 15, lightConfigManager.getStageChangeRefreshRadiusChunks());
            return;
        }
        resendNearbyChunks(player, 15, lightConfigManager.getStageChangeRefreshRadiusChunks());
    }

    private void resendNearbyChunksImmediately(Player player, int maxLightLevel, int radius) {
        World world = player.getWorld();
        Chunk center = player.getLocation().getChunk();

        for (int cx = center.getX() - radius; cx <= center.getX() + radius; cx++) {
            for (int cz = center.getZ() - radius; cz <= center.getZ() + radius; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                int chunkLightLevel = maxLightLevel >= 0
                        ? maxLightLevel
                        : getTargetLightLevelForChunk(player, cx, cz);
                if (maxLightLevel >= 0 || chunkLightLevel < 15 || maxLightLevel < 0) {
                    lightPacketAdapter.sendClampedLight(player, world.getChunkAt(cx, cz), chunkLightLevel);
                }
            }
        }
    }

    private void scheduleChunkRefresh(Player player, World world, int maxLightLevel, Queue<ChunkCoordinate> queue) {
        ChunkRefreshTask oldTask = pendingRefreshes.remove(player.getUniqueId());
        if (oldTask != null) {
            oldTask.cancel();
        }

        if (queue.isEmpty()) {
            return;
        }

        int batchSize = lightConfigManager.getRefreshBatchSize();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !player.getWorld().equals(world)) {
                    ChunkRefreshTask removed = pendingRefreshes.remove(player.getUniqueId());
                    if (removed != null) {
                        removed.cancel();
                    }
                    return;
                }

                int processed = 0;
                while (processed < batchSize && !queue.isEmpty()) {
                    ChunkCoordinate coordinate = queue.poll();
                    if (coordinate != null && world.isChunkLoaded(coordinate.x, coordinate.z)) {
                        int chunkLightLevel = maxLightLevel >= 0
                                ? maxLightLevel
                                : getTargetLightLevelForChunk(player, coordinate.x, coordinate.z);
                        if (maxLightLevel >= 0 || chunkLightLevel < 15 || maxLightLevel < 0) {
                            lightPacketAdapter.sendClampedLight(player, world.getChunkAt(coordinate.x, coordinate.z), chunkLightLevel);
                        }
                    }
                    processed++;
                }

                if (queue.isEmpty()) {
                    ChunkRefreshTask removed = pendingRefreshes.remove(player.getUniqueId());
                    if (removed != null) {
                        removed.cancel();
                    }
                }
            }
        }, 0L, 1L);

        pendingRefreshes.put(player.getUniqueId(), new ChunkRefreshTask(task));
    }

    private void cancelAllPendingRefreshes() {
        for (ChunkRefreshTask task : pendingRefreshes.values()) {
            task.cancel();
        }
        pendingRefreshes.clear();
    }

    private static long packChunk(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private record PlayerLightState(String worldName, int blockX, int blockY, int blockZ,
                                    int chunkX, int chunkZ, int maxLightLevel) {
    }

    private record ChunkCoordinate(int x, int z) {
    }

    private record ChunkRefreshTask(BukkitTask task) {
        private void cancel() {
            task.cancel();
        }
    }
}
