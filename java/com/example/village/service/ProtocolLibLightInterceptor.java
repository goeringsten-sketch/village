package com.example.village.service;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.example.village.VillagePlugin;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

final class ProtocolLibLightInterceptor {

    private final VillagePlugin plugin;
    private final VillageLightService lightService;
    private final ReflectionLightPacketAdapter lightPacketAdapter;

    private PacketAdapter listener;

    ProtocolLibLightInterceptor(VillagePlugin plugin,
                                VillageLightService lightService,
                                ReflectionLightPacketAdapter lightPacketAdapter) {
        this.plugin = plugin;
        this.lightService = lightService;
        this.lightPacketAdapter = lightPacketAdapter;
    }

    public boolean start() {
        try {
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
            PacketType[] types = resolvePacketTypes();
            if (types.length == 0) {
                plugin.getLogger().warning("ProtocolLib-Lichtabfangung konnte keine passenden PacketTypes finden.");
                return false;
            }

            listener = new PacketAdapter(plugin, ListenerPriority.NORMAL, types) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (player == null) {
                        return;
                    }

                    PacketContainer packet = event.getPacket();
                    Integer chunkX = packet.getIntegers().readSafely(0);
                    Integer chunkZ = packet.getIntegers().readSafely(1);
                    if (chunkX == null || chunkZ == null) {
                        return;
                    }

                    int maxLightLevel = lightService.getTargetLightLevelForChunk(player, chunkX, chunkZ);
                    if (maxLightLevel >= 15) {
                        return;
                    }

                    Object handle = packet.getHandle();
                    lightPacketAdapter.clampPacket(handle, maxLightLevel);
                }
            };

            protocolManager.addPacketListener(listener);
            plugin.getLogger().info("Village-Lichtpakete werden jetzt direkt ueber ProtocolLib beim Versand angepasst.");
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING,
                    "ProtocolLib-Lichtabfangung konnte nicht gestartet werden.", ex);
            listener = null;
            return false;
        }
    }

    public void stop() {
        if (listener == null) {
            return;
        }
        try {
            ProtocolLibrary.getProtocolManager().removePacketListener(listener);
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.FINE,
                    "ProtocolLib-Lichtlistener konnte nicht sauber entfernt werden.", ex);
        }
        listener = null;
    }

    private PacketType[] resolvePacketTypes() {
        List<PacketType> types = new ArrayList<>();
        addIfPresent(types, "MAP_CHUNK");
        addIfPresent(types, "LIGHT_UPDATE");
        addIfPresent(types, "LEVEL_CHUNK_WITH_LIGHT");
        return types.toArray(PacketType[]::new);
    }

    private void addIfPresent(List<PacketType> result, String fieldName) {
        try {
            Field field = PacketType.Play.Server.class.getField(fieldName);
            Object value = field.get(null);
            if (value instanceof PacketType packetType) {
                result.add(packetType);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
