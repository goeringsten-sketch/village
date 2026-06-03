package com.example.village.service;

import com.example.village.VillagePlugin;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.logging.Level;

final class ReflectionLightPacketAdapter {

    private final VillagePlugin plugin;
    private final boolean available;

    private final Constructor<?> chunkPosConstructor;
    private final Constructor<?> lightPacketConstructor;
    private final Method serverLevelGetLightEngine;
    private final Method packetDataGetSkyUpdates;
    private final Method packetDataGetBlockUpdates;
    private final Method lightUpdatePacketGetLightData;
    private final Method levelChunkWithLightPacketGetLightData;
    private final Class<?> lightPacketClass;
    private final Class<?> levelChunkWithLightPacketClass;
    private final Method connectionSendMethod;
    private final Field serverPlayerConnectionField;

    ReflectionLightPacketAdapter(VillagePlugin plugin) {
        this.plugin = plugin;

        Constructor<?> resolvedChunkPosConstructor = null;
        Constructor<?> resolvedLightPacketConstructor = null;
        Method resolvedServerLevelGetLightEngine = null;
        Method resolvedPacketDataGetSkyUpdates = null;
        Method resolvedPacketDataGetBlockUpdates = null;
        Method resolvedLightUpdatePacketGetLightData = null;
        Method resolvedLevelChunkWithLightPacketGetLightData = null;
        Class<?> resolvedLightPacketClass = null;
        Class<?> resolvedLevelChunkWithLightPacketClass = null;
        Method resolvedConnectionSendMethod = null;
        Field resolvedServerPlayerConnectionField = null;
        boolean resolvedAvailable = false;

        try {
            Class<?> chunkPosClass = Class.forName("net.minecraft.world.level.ChunkPos");
            Class<?> lightEngineClass = Class.forName("net.minecraft.world.level.lighting.LevelLightEngine");
            Class<?> levelChunkClass = Class.forName("net.minecraft.world.level.chunk.LevelChunk");
            Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> packetInterface = Class.forName("net.minecraft.network.protocol.Packet");
            Class<?> lightPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundLightUpdatePacket");
            Class<?> levelChunkWithLightPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket");
            Class<?> lightPacketDataClass = Class.forName("net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> connectionClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");

            resolvedChunkPosConstructor = chunkPosClass.getConstructor(int.class, int.class);
            resolvedLightPacketConstructor = lightPacketClass.getConstructor(chunkPosClass, lightEngineClass, BitSet.class, BitSet.class);
            resolvedLightPacketClass = lightPacketClass;
            resolvedLevelChunkWithLightPacketClass = levelChunkWithLightPacketClass;

            resolvedServerLevelGetLightEngine = findMethod(serverLevelClass, "getLightEngine");

            resolvedLightUpdatePacketGetLightData = findMethod(lightPacketClass, "getLightData");
            if (resolvedLightUpdatePacketGetLightData == null) {
                resolvedLightUpdatePacketGetLightData = findSingleMethodByReturnType(lightPacketClass, lightPacketDataClass);
            }

            resolvedLevelChunkWithLightPacketGetLightData = findMethod(levelChunkWithLightPacketClass, "getLightData");
            if (resolvedLevelChunkWithLightPacketGetLightData == null) {
                resolvedLevelChunkWithLightPacketGetLightData = findSingleMethodByReturnType(levelChunkWithLightPacketClass, lightPacketDataClass);
            }

            resolvedPacketDataGetSkyUpdates = findMethod(lightPacketDataClass, "getSkyUpdates");
            if (resolvedPacketDataGetSkyUpdates == null) {
                resolvedPacketDataGetSkyUpdates = findListMethod(lightPacketDataClass, 0);
            }

            resolvedPacketDataGetBlockUpdates = findMethod(lightPacketDataClass, "getBlockUpdates");
            if (resolvedPacketDataGetBlockUpdates == null) {
                resolvedPacketDataGetBlockUpdates = findListMethod(lightPacketDataClass, 1);
            }

            resolvedServerPlayerConnectionField = findField(serverPlayerClass, "connection", connectionClass);
            if (resolvedServerPlayerConnectionField == null) {
                resolvedServerPlayerConnectionField = findFieldByType(serverPlayerClass, connectionClass);
            }

            resolvedConnectionSendMethod = findPacketSendMethod(connectionClass, packetInterface);

            if (resolvedServerLevelGetLightEngine != null
                    && resolvedLightUpdatePacketGetLightData != null
                    && resolvedLevelChunkWithLightPacketGetLightData != null
                    && resolvedPacketDataGetSkyUpdates != null
                    && resolvedPacketDataGetBlockUpdates != null
                    && resolvedServerPlayerConnectionField != null
                    && resolvedConnectionSendMethod != null) {
                resolvedAvailable = true;
            }
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Village-Lichtsystem konnte NMS-Light-Adapter nicht initialisieren.", ex);
        }

        this.chunkPosConstructor = resolvedChunkPosConstructor;
        this.lightPacketConstructor = resolvedLightPacketConstructor;
        this.serverLevelGetLightEngine = resolvedServerLevelGetLightEngine;
        this.packetDataGetSkyUpdates = resolvedPacketDataGetSkyUpdates;
        this.packetDataGetBlockUpdates = resolvedPacketDataGetBlockUpdates;
        this.lightUpdatePacketGetLightData = resolvedLightUpdatePacketGetLightData;
        this.levelChunkWithLightPacketGetLightData = resolvedLevelChunkWithLightPacketGetLightData;
        this.lightPacketClass = resolvedLightPacketClass;
        this.levelChunkWithLightPacketClass = resolvedLevelChunkWithLightPacketClass;
        this.connectionSendMethod = resolvedConnectionSendMethod;
        this.serverPlayerConnectionField = resolvedServerPlayerConnectionField;
        this.available = resolvedAvailable;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean sendClampedLight(Player player, Chunk chunk, int maxLightLevel) {
        if (!available || !player.isOnline()) {
            return false;
        }

        try {
            Object serverLevel = invokeNoArg(chunk.getWorld(), "getHandle");
            Object lightEngine = serverLevelGetLightEngine.invoke(serverLevel);
            Object chunkPos = chunkPosConstructor.newInstance(chunk.getX(), chunk.getZ());
            Object packet = lightPacketConstructor.newInstance(chunkPos, lightEngine, null, null);
            clampPacket(packet, maxLightLevel);

            Object serverPlayer = invokeNoArg(player, "getHandle");
            Object connection = serverPlayerConnectionField.get(serverPlayer);
            connectionSendMethod.invoke(connection, packet);
            return true;
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Village-Lichtpaket konnte fuer Spieler " + player.getName() + " nicht gesendet werden.", ex);
            return false;
        }
    }

    public void clampPacket(Object packet, int maxLightLevel) {
        if (!available || packet == null || maxLightLevel >= 15) {
            return;
        }

        Class<?> packetClass = packet.getClass();
        if (!lightPacketClass.isAssignableFrom(packetClass)
                && !levelChunkWithLightPacketClass.isAssignableFrom(packetClass)) {
            return;
        }

        try {
            Method accessor = lightPacketClass.isAssignableFrom(packetClass)
                    ? lightUpdatePacketGetLightData
                    : levelChunkWithLightPacketGetLightData;
            Object packetData = accessor.invoke(packet);
            clampNibbleLists(packetDataGetSkyUpdates.invoke(packetData), maxLightLevel);
            clampNibbleLists(packetDataGetBlockUpdates.invoke(packetData), maxLightLevel);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.WARNING, "Lichtdaten eines ausgehenden Pakets konnten nicht angepasst werden.", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private void clampNibbleLists(Object value, int maxLightLevel) {
        if (!(value instanceof List<?> rawList)) {
            return;
        }

        List<Object> copies = new ArrayList<>(rawList.size());
        for (Object entry : rawList) {
            if (entry instanceof byte[] bytes) {
                copies.add(clampNibbleArray(bytes, maxLightLevel));
            }
        }

        rawList.clear();
        ((List<Object>) rawList).addAll(copies);
    }

    private byte[] clampNibbleArray(byte[] source, int maxLightLevel) {
        byte[] copy = source.clone();
        for (int i = 0; i < copy.length; i++) {
            int lower = Math.min(copy[i] & 0x0F, maxLightLevel);
            int upper = Math.min((copy[i] >> 4) & 0x0F, maxLightLevel);
            copy[i] = (byte) ((upper << 4) | lower);
        }
        return copy;
    }

    private static Method findMethod(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method findSingleMethodByReturnType(Class<?> type, Class<?> returnType) {
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() == 0 && returnType.equals(method.getReturnType())) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Method findListMethod(Class<?> type, int index) {
        int current = 0;
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() == 0 && List.class.equals(method.getReturnType())) {
                if (current == index) {
                    method.setAccessible(true);
                    return method;
                }
                current++;
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name, Class<?> expectedType) {
        try {
            Field field = type.getDeclaredField(name);
            if (!expectedType.isAssignableFrom(field.getType())) {
                return null;
            }
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Field findFieldByType(Class<?> type, Class<?> expectedType) {
        for (Field field : type.getDeclaredFields()) {
            if (expectedType.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static Method findPacketSendMethod(Class<?> connectionClass, Class<?> packetClass) {
        Method fallback = null;
        for (Method method : connectionClass.getMethods()) {
            if (method.getParameterCount() != 1) {
                continue;
            }
            if (!void.class.equals(method.getReturnType())) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (!packetClass.isAssignableFrom(parameterType)) {
                continue;
            }

            if (!"send".equals(method.getName())) {
                continue;
            }

            method.setAccessible(true);
            if (packetClass.equals(parameterType)) {
                return method;
            }
            if (fallback == null) {
                fallback = method;
            }
        }
        return fallback;
    }

    private static Object invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }
}
