package com.example.village.listener;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.service.BorderPreviewService;
import com.example.village.service.BorderService;
import com.example.village.model.VillageBorder;
import com.example.village.service.VillageManager;
import com.example.village.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

/**
 * Handles the walk-based border selection mode.
 * Players walk block-by-block (no diagonal) to define the village border.
 */
public final class BorderWalkListener implements Listener {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final BorderService borderService;
    private final BorderPreviewService previewService;
    private final VillageManager villageManager;

    public BorderWalkListener(VillagePlugin plugin,
                              VillageConfigManager configManager,
                              BorderService borderService,
                              BorderPreviewService previewService,
                              VillageManager villageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.borderService = borderService;
        this.previewService = previewService;
        this.villageManager = villageManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!borderService.isInWalkSession(player.getUniqueId())) return;

        // Only trigger on actual block changes
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        BorderService.BorderWalkSession session = borderService.getWalkSession(player.getUniqueId());
        if (session == null) return;

        BorderService.WalkStepResult result = borderService.handleWalkStep(player, event.getTo());
        if (configManager.isWalkBorderDebug()) {
            int toX = event.getTo().getBlockX();
            int toZ = event.getTo().getBlockZ();
            plugin.getLogger().info("[WalkBorder] step player=" + player.getName()
                    + " to=(" + toX + "," + toZ + ") result=" + result
                    + " points=" + session.getPoints().size()
                    + " paused=" + session.isPausedDuplicate());
        }

        switch (result) {
            case ALREADY_VISITED -> {
                // Block already walked on - skip silently
            }
            case INSIDE_BORDER -> {
                // Player is still inside the existing border - no fake block, no recording
            }
            case RECORDED -> {
                // Show only the OUTER outline of walked blocks (thin visualization)
                session.renderOutline(player, Material.EMERALD_ORE, false);

                // Show area info on action bar
                showBorderActionBar(player, session);
            }
            case PAUSED_DUPLICATE -> {
                // Highlight the last point and keep the outline visible
                session.renderOutline(player, Material.EMERALD_ORE, true);
                if (configManager.isWalkBorderDebug()) {
                    int[] last = session.getLastPoint();
                    plugin.getLogger().info("[WalkBorder] paused-duplicate player=" + player.getName()
                            + " last=" + (last == null ? "null" : ("(" + last[0] + "," + last[1] + ")")));
                }
                MessageUtil.send(player, configManager.getPrefix(),
                        "&eDu hast einen Block doppelt betreten. &7Gehe zur &6goldenen Markierung &7zurueck, dann wird die Grenze weiter gezogen.");
            }
            case RESUMED -> {
                // Remove the gold highlight by re-rendering the outline
                session.renderOutline(player, Material.EMERALD_ORE, false);
                if (configManager.isWalkBorderDebug()) {
                    int[] last = session.getLastPoint();
                    plugin.getLogger().info("[WalkBorder] resumed player=" + player.getName()
                            + " at=" + (last == null ? "null" : ("(" + last[0] + "," + last[1] + ")")));
                }
            }
            case FINISH -> {
                // Player touched the existing territory/border again -> finish automatically
                if (configManager.isWalkBorderDebug()) {
                    int[] last = session.getLastPoint();
                    int[] endAttach = session.getEndAttach();
                    plugin.getLogger().info("[WalkBorder] auto-finish player=" + player.getName()
                            + " last=" + (last == null ? "null" : ("(" + last[0] + "," + last[1] + ")"))
                            + " endAttach=" + (endAttach == null ? "null" : ("(" + endAttach[0] + "," + endAttach[1] + ")")));
                }
                session.clearFakeBlocks(player);
                finishAndApplyBorder(player, session);
            }
        }
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!borderService.isInWalkSession(player.getUniqueId())) return;

        BorderService.BorderWalkSession session = borderService.getWalkSession(player.getUniqueId());
        if (session == null || session.getPoints().size() < 4) return;

        // Right-click to finish: player must be adjacent to the existing village border
        if (!borderService.isAdjacentToExistingBorder(player)) {
            MessageUtil.send(player, configManager.getPrefix(),
                    "&cDu musst an der bestehenden Grenze stehen, um die Grenzziehung zu beenden!");
            return;
        }

        // Set end-attachment so the border can be closed along the existing border safely.
        int[] endAttach = findAdjacentBorderBlock(session.getVillage(),
                session.getLastPoint()[0], session.getLastPoint()[1]);
        session.setEndAttach(endAttach);
        if (configManager.isWalkBorderDebug()) {
            int[] last = session.getLastPoint();
            plugin.getLogger().info("[WalkBorder] right-click-finish player=" + player.getName()
                    + " last=(" + last[0] + "," + last[1] + ")"
                    + " endAttach=" + (endAttach == null ? "null" : ("(" + endAttach[0] + "," + endAttach[1] + ")"))
                    + " points=" + session.getPoints().size());
        }

        session.clearFakeBlocks(player);
        finishAndApplyBorder(player, session);
    }

    private int[] findAdjacentBorderBlock(com.example.village.model.Village village, int x, int z) {
        if (village.isOnAnyBorder(x, z)) return new int[]{x, z};
        if (village.isOnAnyBorder(x + 1, z)) return new int[]{x + 1, z};
        if (village.isOnAnyBorder(x - 1, z)) return new int[]{x - 1, z};
        if (village.isOnAnyBorder(x, z + 1)) return new int[]{x, z + 1};
        if (village.isOnAnyBorder(x, z - 1)) return new int[]{x, z - 1};
        return null;
    }

    private void finishAndApplyBorder(Player player, BorderService.BorderWalkSession session) {
        if (configManager.isWalkBorderDebug()) {
            int[] startAttach = session.getStartAttach();
            int[] endAttach = session.getEndAttach();
            plugin.getLogger().info("[WalkBorder] finish-start player=" + player.getName()
                    + " points=" + session.getPoints().size()
                    + " startAttach=" + (startAttach == null ? "null" : ("(" + startAttach[0] + "," + startAttach[1] + ")"))
                    + " endAttach=" + (endAttach == null ? "null" : ("(" + endAttach[0] + "," + endAttach[1] + ")")));
        }

        if (session.getAction() == BorderService.BorderWalkAction.NORMAL) {
            VillageBorder border = borderService.finishWalkSelection(player);
            if (border != null) {
                if (configManager.isWalkBorderDebug()) {
                    plugin.getLogger().info("[WalkBorder] finish-success player=" + player.getName()
                            + " borderPoints=" + border.getBorderPoints().size()
                            + " edgeBlocks=" + border.getEdgeBlocks().size()
                            + " area=" + border.calculateArea());
                }
                // Show preview with emerald_ore for new borders
                previewService.showPreviewWithMaterial(player, border, player.getWorld(), Material.EMERALD_ORE);
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("border-preview-start"));

                // Store border as pending - player must confirm via chat
                borderService.addPendingBorderConfirmation(
                        player.getUniqueId(), session.getVillage(), border);
                // No village prefix for border drawing flow
                MessageUtil.sendYesNoRunCommand(
                        player,
                        null,
                        "&7Grenze setzen?",
                        "/village borderconfirm ja",
                        "/village borderconfirm nein"
                );
            } else {
                if (configManager.isWalkBorderDebug()) {
                    plugin.getLogger().info("[WalkBorder] finish-failed player=" + player.getName());
                }
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("border-invalid-walk"));
            }
            return;
        }

        // Special cut/enlarge operations rely on a different finish flow.
        borderService.endWalkSession(player.getUniqueId());
        java.util.List<VillageBorder> resultBorders = borderService.finishSpecialWalkSelection(session);
        if (resultBorders != null && !resultBorders.isEmpty()) {
            if (configManager.isWalkBorderDebug()) {
                plugin.getLogger().info("[WalkBorder] finish-special-success player=" + player.getName()
                        + " action=" + session.getAction()
                        + " borders=" + resultBorders.size());
            }
            previewService.showPreviewMultiple(player, resultBorders, player.getWorld());
            MessageUtil.send(player, configManager.getPrefix(),
                    configManager.message("border-preview-start"));
            borderService.addPendingBorderActionConfirmation(
                    player.getUniqueId(), session.getVillage(), session.getAction(), session.getTargetBorderId(), resultBorders);
            MessageUtil.sendYesNoRunCommand(
                    player,
                    null,
                    "&7Grenze setzen?",
                    "/village borderconfirm ja",
                    "/village borderconfirm nein"
            );
        } else {
            if (configManager.isWalkBorderDebug()) {
                plugin.getLogger().info("[WalkBorder] finish-special-failed player=" + player.getName()
                        + " action=" + session.getAction());
            }
            MessageUtil.send(player, configManager.getPrefix(),
                    configManager.message("border-invalid-walk"));
        }
    }

    private void showBorderActionBar(Player player, BorderService.BorderWalkSession session) {
        int currentBlocks = session.getPoints().size();
        int maxArea = session.getVillage().getMaxArea(
                configManager.getMaxArea(), configManager.getUpgradeAreaPerLevel());
        int usedArea = session.getVillage().getTotalArea();
        int available = maxArea - usedArea;

        String raw = "&aGezogene Bloecke: &e" + currentBlocks + " &8| &aVerfuegbar: &e" + available + " Bloecke";
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
        player.sendActionBar(component);
    }
}
