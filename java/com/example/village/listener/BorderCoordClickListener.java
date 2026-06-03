package com.example.village.listener;

import com.example.village.service.BorderService;
import com.example.village.service.BorderPreviewService;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.Location;

public final class BorderCoordClickListener implements Listener {
    private final BorderService borderService;
    private final BorderPreviewService previewService;

    public BorderCoordClickListener(BorderService borderService, BorderPreviewService previewService) {
        this.borderService = borderService;
        this.previewService = previewService;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (!(action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK)) return;
        Player player = event.getPlayer();
        if (!borderService.isInCoordSession(player.getUniqueId())) return;
        event.setCancelled(true);

        Location loc = event.getClickedBlock() == null ? null : event.getClickedBlock().getLocation();
        if (loc == null) return;

        BorderService.CoordSession session = borderService.getCoordSession(player.getUniqueId());
        if (session == null) return;

        if (session.getPoint1() == null) {
            session.setPoint1(new int[]{loc.getBlockX(), loc.getBlockZ()});
            MessageUtil.send(player, null, "&aPunkt 1 gesetzt: &e" + loc.getBlockX() + " &e" + loc.getBlockZ());
            MessageUtil.send(player, null, "&7Klicke den zweiten Block oder tippe 'x z' in den Chat.");
            return;
        }
        if (session.getPoint2() == null) {
            session.setPoint2(new int[]{loc.getBlockX(), loc.getBlockZ()});
            MessageUtil.send(player, null, "&aPunkt 2 gesetzt: &e" + loc.getBlockX() + " &e" + loc.getBlockZ());

            // finish selection on main thread (mirror ChatInputListener behavior)
            Bukkit.getScheduler().runTask(com.example.village.VillagePlugin.getProvidingPlugin(com.example.village.VillagePlugin.class), () -> {
                // Session VOR finishCoordSelection holen (wird darin entfernt)
                var session2 = borderService.getCoordSession(player.getUniqueId());
                var border = borderService.finishCoordSelection(player);
                if (border != null && session2 != null) {
                    if (previewService != null) previewService.showPreview(player, border, player.getWorld());
                    MessageUtil.send(player, null, "&7Vorschau der ausgewaehlten Region:");
                    borderService.addPendingBorderConfirmation(player.getUniqueId(), session2.getVillage(), border);
                    MessageUtil.sendYesNoRunCommand(player, null, "&7Grenze setzen?", "/village borderconfirm ja", "/village borderconfirm nein");
                } else {
                    // Fehlerursache aus der Session lesen (session2 existiert noch, da sie vorher geholt wurde)
                    String err = (session2 != null && session2.getLastError() != null)
                            ? session2.getLastError()
                            : "&cUngültige Auswahl – prüfe ob die Grenze verbunden und nicht zu groß ist.";
                    MessageUtil.send(player, null, err);
                }
            });
        }
    }
}
