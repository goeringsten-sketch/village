package com.example.village.listener;

import com.example.village.config.VillageConfigManager;
import com.example.village.model.Village;
import com.example.village.model.VillageBorder;
import com.example.village.service.BorderPreviewService;
import com.example.village.service.BorderService;
import com.example.village.service.VillageManager;
import com.example.village.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;

public final class BorderManageSelectionListener implements Listener {

    private final VillageConfigManager configManager;
    private final VillageManager villageManager;
    private final BorderService borderService;
    private final BorderPreviewService previewService;

    public BorderManageSelectionListener(VillageConfigManager configManager,
                                         VillageManager villageManager,
                                         BorderService borderService,
                                         BorderPreviewService previewService) {
        this.configManager = configManager;
        this.villageManager = villageManager;
        this.borderService = borderService;
        this.previewService = previewService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSelectBorderByClick(PlayerInteractEvent event) {
        // Prevent double-trigger (main-hand + off-hand) which would toggle selection twice.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        BorderService.PendingBorderSelection pending = borderService.getPendingBorderSelection(player.getUniqueId());
        if (pending == null) {
            return;
        }

        Village village = pending.getVillage();
        if (village == null) {
            borderService.removePendingBorderSelection(player.getUniqueId());
            return;
        }
        if (!villageManager.canManageVillage(village, player.getUniqueId()) && !player.hasPermission("village.admin")) {
            borderService.removePendingBorderSelection(player.getUniqueId());
            previewService.clearPreview(player);
            MessageUtil.send(player, configManager.getPrefix(), configManager.message("no-permission"));
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }

        event.setCancelled(true);

        VillageBorder clickedBorder = village.getBorderAt(event.getClickedBlock().getX(), event.getClickedBlock().getZ());
        if (clickedBorder == null) {
            MessageUtil.send(player, configManager.getPrefix(), "&cHier wurde keine Grenzflaeche gefunden.");
            return;
        }
        int borderId = clickedBorder.getId();
        if (configManager.isWalkBorderDebug()) {
            org.bukkit.Bukkit.getLogger().info("[Village][Selection] player=" + player.getName() + " clicked at=(" + event.getClickedBlock().getX() + "," + event.getClickedBlock().getZ() + ") borderId=" + borderId);
        }

        if (pending.getMode() == BorderService.BorderSelectionMode.DELETE) {
            if (borderId == 0) {
                MessageUtil.send(player, configManager.getPrefix(), "&cDie Default-Grenzflaeche (ID 0) kann nicht geloescht werden.");
                return;
            }
            previewService.showPreview(player, clickedBorder, player.getWorld());
            // Do not ask for a first confirmation here - the delete command shows the warning/confirm once.
            player.performCommand("village manage border delete " + borderId);
            borderService.removePendingBorderSelection(player.getUniqueId());
            return;
        }

        if (pending.getMode() == BorderService.BorderSelectionMode.SPLIT) {
            // Directly perform split on clicked border
            player.performCommand("village manage border split " + borderId);
            borderService.removePendingBorderSelection(player.getUniqueId());
            return;
        }

        if (pending.getMode() == BorderService.BorderSelectionMode.ENLARGE) {
            player.performCommand("village manage border enlarge " + borderId);
            borderService.removePendingBorderSelection(player.getUniqueId());
            return;
        }

        if (pending.getMode() == BorderService.BorderSelectionMode.SHRINK) {
            player.performCommand("village manage border shrink " + borderId);
            borderService.removePendingBorderSelection(player.getUniqueId());
            return;
        }

        var selected = pending.getSelectedBorderIds();
        if (!selected.add(borderId)) {
            selected.remove(borderId);
        }

        List<VillageBorder> selectedBorders = new ArrayList<>();
        for (Integer id : selected) {
            VillageBorder b = village.getBorderById(id);
            if (b != null) {
                selectedBorders.add(b);
            }
        }
        if (selectedBorders.isEmpty()) {
            previewService.showPreviewMultiple(player, village.getBorders(), player.getWorld());
        } else {
            previewService.showPreviewMultiple(player, village.getBorders(), selectedBorders, player.getWorld());
        }

        String ids = selected.isEmpty()
                ? "(keine)"
                : selected.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
        MessageUtil.send(player, configManager.getPrefix(), "&7Ausgewaehlt: &e" + ids);
        MessageUtil.sendYesNoAbortRunCommand(
                player,
                configManager.getPrefix(),
                "&7Fertig mit Auswaehlen?",
                "/village manage border fusionfinish ja",
                "/village manage border fusionfinish nein",
                "/village manage border cancel"
        );
    }
}
