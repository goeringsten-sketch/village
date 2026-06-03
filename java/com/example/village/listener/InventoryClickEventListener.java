package com.example.village.listener;

import com.example.village.currency.CurrencyType;
import com.example.village.integration.CurrencyIntegrationManager;
import com.example.village.model.TradeOffer;
import com.example.village.model.VillagerInventory;
import com.example.village.trading.MarketplaceUI;
import com.example.village.trading.PlayerToPlayerTradeUI;
import com.example.village.trading.TradeResult;
import com.example.village.trading.TradingUI;
import com.example.village.trading.VillagerExternalTradeUI;
import com.example.village.trading.VillagerLocalTradeUI;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class InventoryClickEventListener implements Listener {

    private final CurrencyIntegrationManager currencySystem;
    private final Logger logger;
    private final Map<UUID, TradingUI> activeTradings = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, VillagerInventory> villagerInventories = Collections.synchronizedMap(new HashMap<>());

    public InventoryClickEventListener(CurrencyIntegrationManager currencySystem, Logger logger) {
        this.currencySystem = Objects.requireNonNull(currencySystem);
        this.logger = Objects.requireNonNull(logger);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        try {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            String title = event.getView().getTitle();
            if (!isTradeGUI(title)) {
                return;
            }

            TradingUI tradingUI = activeTradings.get(player.getUniqueId());
            if (tradingUI == null) {
                player.sendMessage(ChatColor.RED + "Keine aktive Trading-Session");
                return;
            }

            if (tradingUI instanceof PlayerToPlayerTradeUI p2pUi) {
                handlePlayerToPlayerClick(event, player, p2pUi);
                return;
            }

            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
                return;
            }
            handleClassicClick(player, title, slot, tradingUI);
        } catch (Exception e) {
            logger.severe("Error handling inventory click: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        String title = event.getView().getTitle();
        if (!isTradeGUI(title)) {
            return;
        }

        TradingUI ui = activeTradings.get(player.getUniqueId());
        if (ui instanceof PlayerToPlayerTradeUI) {
            return;
        }
        activeTradings.remove(player.getUniqueId());
    }

    private boolean isTradeGUI(String title) {
        return title.contains("Handeln mit") ||
                title.contains("Handel mit") ||
                title.contains("Marktplatz") ||
                title.contains("Markt");
    }

    private void handleClassicClick(Player player, String title, int slot, TradingUI tradingUI) {
        if (title.contains("Handeln mit") && !title.contains("extern")) {
            handleLocalTradeClick(player, slot, (VillagerLocalTradeUI) tradingUI);
        } else if (title.contains("Handeln mit") && title.contains("extern")) {
            handleExternalTradeClick(player, slot, (VillagerExternalTradeUI) tradingUI);
        } else if (title.contains("Marktplatz") || title.contains("Markt")) {
            handleMarketplaceClick(player, slot, (MarketplaceUI) tradingUI);
        }
    }

    private void handleLocalTradeClick(Player player, int slot, VillagerLocalTradeUI ui) {
        Optional<TradeOffer> offer = ui.getOfferForSlot(slot);
        if (offer.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Kein Angebot auf diesem Slot.");
            return;
        }
        TradeResult result = ui.processTrade(player, offer.get(), 1);
        if (result.isSuccess()) {
            player.sendMessage(result.getMessage());
        } else {
            result.getErrors().forEach(error -> player.sendMessage(ChatColor.RED + error));
        }
    }

    private void handleExternalTradeClick(Player player, int slot, VillagerExternalTradeUI ui) {
        Optional<TradeOffer> offer = ui.getOfferForSlot(slot);
        if (offer.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Kein Angebot auf diesem Slot.");
            return;
        }
        TradeResult result = ui.processTrade(player, offer.get(), 1);
        if (result.isSuccess()) {
            player.sendMessage(result.getMessage());
        } else {
            result.getErrors().forEach(error -> player.sendMessage(ChatColor.RED + error));
        }
    }

    private void handleMarketplaceClick(Player player, int slot, MarketplaceUI marketplace) {
        if (slot == 53) {
            player.sendMessage(ChatColor.GOLD + "=== " + marketplace.getShopName() + " ===");
            player.sendMessage(ChatColor.YELLOW + "Verkäufe: " + marketplace.getTotalSales());
            player.sendMessage(ChatColor.GREEN + "Umsatz: " + marketplace.getTotalRevenue());
            return;
        }
        Optional<TradeOffer> offer = marketplace.getOfferForSlot(slot);
        if (offer.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Kein Angebot auf diesem Slot.");
            return;
        }
        TradeResult result = marketplace.processTrade(player, offer.get(), 1);
        if (result.isSuccess()) {
            player.sendMessage(result.getMessage());
        } else {
            result.getErrors().forEach(error -> player.sendMessage(ChatColor.RED + error));
        }
    }

    private void handlePlayerToPlayerClick(InventoryClickEvent event, Player player, PlayerToPlayerTradeUI ui) {
        event.setCancelled(true);
        InventoryView view = event.getView();
        int topSize = view.getTopInventory().getSize();
        int rawSlot = event.getRawSlot();

        if (rawSlot >= topSize) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) {
                return;
            }
            boolean fullStack = event.isRightClick();
            int playerSlot = event.getSlot();
            if (ui.addItemToOffer(player, playerSlot, fullStack)) {
                ui.refreshBothViews();
            }
            return;
        }

        if (rawSlot >= PlayerToPlayerTradeUI.OWN_START && rawSlot <= PlayerToPlayerTradeUI.OWN_END) {
            boolean fullStack = event.isRightClick();
            if (ui.removeItemFromOfferBySlot(player, rawSlot, fullStack)) {
                ui.refreshBothViews();
                player.sendMessage(ChatColor.YELLOW + (fullStack ? "Eintrag aus Angebot entfernt." : "1 Item aus Angebot entfernt."));
            }
            return;
        }

        if (rawSlot >= PlayerToPlayerTradeUI.ACCEPT_START && rawSlot <= PlayerToPlayerTradeUI.ACCEPT_END) {
            boolean completed = ui.acceptTrade(player);
            if (!completed) {
                player.sendMessage(ChatColor.GREEN + "Trade akzeptiert. Warte auf die andere Seite.");
            } else {
                unregisterTrading(player.getUniqueId());
                Player other = ui.getOther(player);
                if (other != null) {
                    unregisterTrading(other.getUniqueId());
                }
                ui.clearSession();
            }
            return;
        }

        if (rawSlot >= PlayerToPlayerTradeUI.CANCEL_START && rawSlot <= PlayerToPlayerTradeUI.CANCEL_END) {
            ui.cancelTrade(player);
            unregisterTrading(player.getUniqueId());
            Player other = ui.getOther(player);
            if (other != null) {
                unregisterTrading(other.getUniqueId());
            }
            ui.clearSession();
        }
    }

    public void registerTrading(UUID playerUUID, TradingUI tradingUI) {
        activeTradings.put(playerUUID, tradingUI);
    }

    public void unregisterTrading(UUID playerUUID) {
        activeTradings.remove(playerUUID);
    }

    public void cacheVillagerInventory(UUID villagerUUID, VillagerInventory inventory) {
        villagerInventories.put(villagerUUID, inventory);
    }

    public VillagerInventory getCachedInventory(UUID villagerUUID) {
        return villagerInventories.get(villagerUUID);
    }
}
