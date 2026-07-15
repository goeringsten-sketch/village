package com.example.village.listener;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.model.*;
import com.example.village.service.*;
import com.example.village.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

import java.util.*;

/**
 * Verarbeitet Rechtsklicks auf WB-Blöcke des neuen Gebäude-Systems:
 *   - Truhe / Barrel → Gebäude-Truhe öffnen (Access-Check)
 *   - Glocke, spezielle WBs → Gebäude-Info-Menü
 *   - Inventar schließen → Cache syncen
 *   - Blockabbau-Schutz für registrierte WBs
 */
public final class WorkstationInteractListener implements Listener {

    private final VillagePlugin plugin;
    private final VillageManager villageManager;
    private final BuildingConfigLoader configLoader;
    private final com.example.village.listener.GuiClickListener guiClickListener;
    private final BuildingChestManager chestManager;
    private final ProductionService productionService;
    private final PathService pathService;
    private final String prefix;
    private final VillageConfigManager configManager;

    /** Spieler → offenes Gebäude-Truhen-ID (für Cache-Sync beim Schließen) */
    private final Map<UUID, UUID> openChestSessions = new HashMap<>();

    public WorkstationInteractListener(VillagePlugin plugin, VillageManager villageManager,
                                       BuildingConfigLoader configLoader,
                                       BuildingChestManager chestManager,
                                       ProductionService productionService,
                                       PathService pathService,
                                       com.example.village.listener.GuiClickListener guiClickListener) {
        this.plugin            = plugin;
        this.villageManager    = villageManager;
        this.configLoader      = configLoader;
        this.chestManager      = chestManager;
        this.productionService = productionService;
        this.pathService       = pathService;
        this.guiClickListener  = guiClickListener;
        this.prefix            = plugin.getConfig().getString("prefix", "§6[Dorf] ");
        this.configManager     = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();

        // If the player is currently selecting a villager to assign to a workstation,
        // don't handle the click here so JobAssignmentListener can process it.
        try {
            if (guiClickListener != null && guiClickListener.getPendingJobAssignments().containsKey(player.getUniqueId())) {
                return;
            }
        } catch (Exception ignored) {}

        // Dorf ermitteln
        Village village = villageManager.getPlayerVillage(player.getUniqueId())
            .or(() -> villageManager.getVillageAtLocation(block.getLocation()))
            .orElse(null);
        if (village == null) return;

        // Gebäude über WB-Block suchen
        VillageBuilding building = findByWorkstation(village, block);
        if (building == null) return;

        BuildingDefinition def = configLoader.getDefinition(building.getTypeKey());
        if (def == null) return;

        // Permission
        if (!player.hasPermission(def.getPermission())
            && !player.hasPermission("village.building.*")
            && !player.hasPermission("village.admin")) {
            event.setCancelled(true);
            MessageUtil.send(player, prefix, configManager.text("messages.workstation-no-permission", "&cFehlende Berechtigung: &e%permission%").replace("%permission%", def.getPermission()));
            return;
        }

        // Noch im Bau?
        if (!building.isCompleted()) {
            event.setCancelled(true);
            MessageUtil.send(player, prefix, configManager.text("messages.workstation-under-construction", "&cDieses Gebäude ist noch im Bau."));
            return;
        }

        Material mat = block.getType();
        event.setCancelled(true);

        if (isChestBlock(mat)) {
            if (chestManager.openForPlayer(player, building, def, village))
                openChestSessions.put(player.getUniqueId(), building.getId());

        } else if (mat == Material.BELL || isInfoBlock(mat)) {
            showBuildingInfo(player, building, def);

        } else if (mat == Material.LECTERN) {
            showSkillInfo(player, building, def, village);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID bid = openChestSessions.remove(player.getUniqueId());
        if (bid == null) return;
        chestManager.syncFromInventory(bid, event.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().hasPermission("village.admin")) return;
        Block block = event.getBlock();
        Village village = villageManager.getVillageAtLocation(block.getLocation()).orElse(null);
        if (village == null) return;
        if (findByWorkstation(village, block) != null) {
            event.setCancelled(true);
            MessageUtil.send(event.getPlayer(), prefix, configManager.text("messages.workstation-protected", "&cWorkstation-Block – kann nicht zerstört werden!"));
        }
    }

    // ── Info-Ausgabe ──────────────────────────────────────────────

    private void showBuildingInfo(Player player, VillageBuilding building, BuildingDefinition def) {
        player.sendMessage(MessageUtil.color(configManager.text("messages.workstation-info-title", "§6§l%name% §8(Tier %level%/%max%)")
                .replace("%name%", def.getName())
                .replace("%level%", String.valueOf(building.getLevel()))
                .replace("%max%", String.valueOf(def.getMaxUpgradeTier()))));
        player.sendMessage(MessageUtil.color(configManager.text("messages.workstation-description", "§7%description%").replace("%description%", def.getDescription())));

        // Aktive Produktionsjobs
        List<ProductionService.ActiveJob> jobs = productionService.getActiveJobs(building.getId());
        if (!jobs.isEmpty()) {
            player.sendMessage(MessageUtil.color(configManager.text("messages.workstation-productions", "§aProduktionen:")));
            jobs.forEach(j -> player.sendMessage(MessageUtil.color(configManager.text("messages.workstation-production-line", "  §e%recipe% §8– noch §e%seconds%s")
                .replace("%recipe%", j.recipe.getId())
                .replace("%seconds%", String.valueOf(j.getRemainingSeconds())))));
        }

        // Truhen-Info
        if (def.getChestConfig() != null) {
            int slots = chestManager.getSlots(building.getId());
            player.sendMessage(MessageUtil.color(configManager.text("messages.workstation-storage", "§7Lager: §e%rows%x9 §7(%slots% Slots)")
                    .replace("%rows%", String.valueOf(ChestConfig.slotsToRows(slots)))
                    .replace("%slots%", String.valueOf(slots))));
        }

        // Nächstes Upgrade
        BuildingDefinition.UpgradeTier next = def.getUpgradeTier(building.getLevel() + 1);
        if (next != null) {
            player.sendMessage(MessageUtil.color(configManager.text("messages.workstation-next-upgrade", "§eNächstes Upgrade: §6%name%").replace("%name%", next.getName())));
            player.sendMessage(MessageUtil.color(configManager.text("messages.workstation-cost", "§7Kosten: %cost%").replace("%cost%", formatCost(next))));
        }
    }

    private void showSkillInfo(Player player, VillageBuilding building, BuildingDefinition def, Village village) {
        player.sendMessage(MessageUtil.color(configManager.text("messages.workstation-skilltree-title", "§6§l%name% – Skilltrees").replace("%name%", def.getName())));
        player.sendMessage(MessageUtil.color(configManager.text("messages.workstation-skilltree-desc", "§7Hier werden Job-Skilltrees freigeschaltet.")));
    }

    // ── Suche ─────────────────────────────────────────────────────

    private VillageBuilding findByWorkstation(Village village, Block block) {
        for (VillageBuilding b : village.getBuildings()) {
            BuildingDefinition def = configLoader.getDefinition(b.getTypeKey());
            if (def == null) continue;
            String key = WorkstationMatcher.resolveWorkstationKey(
                    plugin, b, def, block.getLocation(), block.getType());
            if (key != null) return b;
        }
        return null;
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────

    private boolean isChestBlock(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL;
    }

    private boolean isInfoBlock(Material m) {
        return m == Material.CARTOGRAPHY_TABLE || m == Material.BOOKSHELF ||
               m == Material.ANVIL || m == Material.SMITHING_TABLE ||
               m == Material.BLAST_FURNACE || m == Material.SMOKER ||
               m == Material.FURNACE || m == Material.STONECUTTER ||
               m == Material.BEEHIVE || m == Material.BEE_NEST;
    }

    private String formatCost(BuildingDefinition.UpgradeTier tier) {
        StringBuilder sb = new StringBuilder();
        tier.getBuildItems().forEach((m, a) -> sb.append("§e").append(a).append("× §7").append(m.name()).append(" "));
        if (tier.getBuildMoneyGlobal() > 0) sb.append("§e").append((int) tier.getBuildMoneyGlobal()).append("$ ");
        return sb.toString().trim();
    }
}
