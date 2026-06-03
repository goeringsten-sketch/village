package com.example.village.listener;

import com.example.village.service.PathService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gibt Geschwindigkeitsbonus beim Betreten registrierter Pfad-Zonen.
 * Nachlauf-Dauer (configurable) nach Verlassen.
 */
public final class PathEffectListener implements Listener {

    private final PathService pathService;

    private final Map<UUID, Long>    lastOnPath           = new HashMap<>();
    private final Map<UUID, Integer> activeAfterLeaveTicks = new HashMap<>();
    private final Map<UUID, Integer> activeAmplifier       = new HashMap<>();

    public PathEffectListener(PathService pathService) {
        this.pathService = pathService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Nur auswerten wenn Block gewechselt
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
         && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
         && event.getFrom().getBlockY() == event.getTo().getBlockY()) return;

        Player player = event.getPlayer();
        UUID id       = player.getUniqueId();

        // Walk-Session-Bewegung weiterleiten
        if (pathService.isInPathSession(id))
            pathService.handleMove(player, event.getFrom(), event.getTo());

        // Pfad-Effekt
        PathService.PathZone zone = pathService.getZoneAt(event.getTo());
        long now = System.currentTimeMillis();

        if (zone != null) {
            int amp   = zone.getConfig().getSpeedAmplifier();
            int after = zone.getConfig().getDurationAfterLeaveTicks();
            lastOnPath.put(id, now);
            activeAfterLeaveTicks.put(id, after);
            activeAmplifier.put(id, amp);
            applySpeed(player, amp, 40); // 2s – wird laufend erneuert
        } else {
            Long lastTime = lastOnPath.get(id);
            if (lastTime == null) return;
            int afterTicks = activeAfterLeaveTicks.getOrDefault(id, 0);
            long remainMs  = afterTicks * 50L - (now - lastTime);
            if (remainMs > 0) {
                applySpeed(player, activeAmplifier.getOrDefault(id, 0), (int) (remainMs / 50));
            } else {
                removeSpeed(player);
                lastOnPath.remove(id);
                activeAfterLeaveTicks.remove(id);
                activeAmplifier.remove(id);
            }
        }
    }

    private void applySpeed(Player player, int amplifier, int ticks) {
        PotionEffect cur = player.getPotionEffect(PotionEffectType.SPEED);
        if (cur != null && cur.getAmplifier() > amplifier) return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, amplifier, true, false, false), true);
    }

    private void removeSpeed(Player player) {
        PotionEffect cur = player.getPotionEffect(PotionEffectType.SPEED);
        if (cur != null && cur.getAmplifier() <= 1) player.removePotionEffect(PotionEffectType.SPEED);
    }
}
