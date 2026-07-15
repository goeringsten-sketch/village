package com.example.village.trading;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.ChatColor;

import com.example.village.currency.*;
import com.example.village.model.*;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Trading UI für externe Dorf-Spieler mit Dorf-Villager (Globale Currency)
 * 
 * Szenario:
 * - Spieler aus Dorf B kauft von Villager aus Dorf A
 * - Nur globale Währung
 * - Villager kann nur verkaufen, nicht kaufen
 */
public class VillagerExternalTradeUI implements TradingUI {
    
    private final String villagerUUID;
    private final String villagerName;
    private final String villageUUID;
    private final VillagerInventory villagerInventory;
    private final GlobalCurrencyManager globalCurrencyManager;
    private final LocalCurrencyManager localCurrencyManager;
    private final Logger logger;
    private final Map<Integer, TradeOffer> slotOffers = new HashMap<>();
    
    public VillagerExternalTradeUI(String villagerUUID, String villagerName, String villageUUID,
                                   VillagerInventory inventory,
                                   GlobalCurrencyManager globalCurrencyManager,
                                   LocalCurrencyManager localCurrencyManager,
                                   Logger logger) {
        this.villagerUUID = Objects.requireNonNull(villagerUUID);
        this.villagerName = Objects.requireNonNull(villagerName);
        this.villageUUID = Objects.requireNonNull(villageUUID);
        this.villagerInventory = Objects.requireNonNull(inventory);
        this.globalCurrencyManager = Objects.requireNonNull(globalCurrencyManager);
        this.localCurrencyManager = Objects.requireNonNull(localCurrencyManager);
        this.logger = Objects.requireNonNull(logger);
    }
    
    /**
     * Öffne Handel-Interface für externen Spieler (nur Kauf-Seite)
     */
    @Override
    public void openTradingUI(Player buyer) {
        Inventory inventory = Bukkit.createInventory(null, 27,
            ChatColor.GOLD + "Handeln mit " + villagerName + ChatColor.GRAY + " (extern)");
        
        // Hole alle Verkaufs-Angebote in globaler Währung
        List<TradeOffer> offers = villagerInventory.getForSale()
            .stream()
            .filter(offer -> offer.getCurrency() == CurrencyType.GLOBAL)
            .filter(offer -> offer.hasStock(1))
            .collect(Collectors.toList());
        
        // Füge als Items hinzu
        slotOffers.clear();
        int slot = 0;
        for (TradeOffer offer : offers) {
            ItemStack item = createTradeItem(offer);
            inventory.setItem(slot, item);
            slotOffers.put(slot, offer);
            slot++;
            if (slot >= 27) break;  // Max 27 Slots
        }
        
        buyer.openInventory(inventory);
    }
    
    /**
     * Erstelle Item zum Anzeigen eines Trade-Angebots
     */
    private ItemStack createTradeItem(TradeOffer offer) {
        Material material = getMaterialFromItemId(offer.getItemId());
        ItemStack item = new ItemStack(material, offer.getQuantityPerTrade());
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + offer.getDisplayName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "─────────────────");
            lore.add(ChatColor.GREEN + "Preis: " + 
                     globalCurrencyManager.formatCurrency(offer.getPrice()));
            lore.add(ChatColor.AQUA + "Bestand: " + offer.getStock());
            lore.add(ChatColor.GRAY + "Menge: " + offer.getQuantityPerTrade());
            lore.add("");
            lore.add(ChatColor.GOLD + "Klicken zum kaufen");
            lore.add(ChatColor.RED + "Globale Währung");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Verarbeite einen Trade (Externes Kaufen)
     * @param buyer Der externe Spieler
     * @param offer Das Angebot
     * @param quantity Wie viel Stacks
     */
    @Override
    public TradeResult processTrade(Player buyer, TradeOffer offer, int quantity) {
        TradeResult result = new TradeResult();
        
        try {
            // 1. Validierung
            if (!validateBuyer(buyer, result)) {
                return result;
            }
            
            if (!validateOffer(offer, quantity, result)) {
                return result;
            }
            
            // 2. Prüfe globales Guthaben des Spielers
            double buyerGlobalBalance = globalCurrencyManager.getBalance(buyer.getUniqueId());
            double totalCost = offer.getPrice() * quantity;
            
            if (buyerGlobalBalance < totalCost) {
                result.addError("Du hast nicht genug globale Währung. " +
                               "Benötigt: " + globalCurrencyManager.formatCurrency(totalCost) +
                               ", Du hast: " + globalCurrencyManager.formatCurrency(buyerGlobalBalance));
                return result;
            }
            
            // 3. Prüfe Villager-Stock
            if (!offer.hasStock(quantity)) {
                result.addError("Der Villager hat nicht genug auf Lager. " +
                               "Verfügbar: " + offer.getStock() + ", Du möchtest: " + quantity);
                return result;
            }
            
            // 4. Transferiere Währung: Spieler → Villager (global)
            // Der Villager bekommt globale Währung
            globalCurrencyManager.transfer(buyer.getUniqueId(), UUID.fromString(villagerUUID), totalCost);
            
            // 5. Gebe Items zum Spieler
            ItemStack itemStack = new ItemStack(
                getMaterialFromItemId(offer.getItemId()),
                offer.getQuantityPerTrade() * quantity
            );
            
            Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(itemStack);
            
            // Items spawnen auf dem Boden wenn Inventar voll
            if (!leftover.isEmpty()) {
                leftover.forEach((slot, item) -> {
                    buyer.getWorld().dropItemNaturally(buyer.getLocation(), item);
                });
            }
            
            // 6. Update Villager-Stock
            offer.reduceStock(quantity);
            
            // Erfolg!
            result.setSuccess(true);
            result.setMessage(ChatColor.GREEN + "Trade erfolgreich abgeschlossen!");
            result.setNewGlobalBalance(globalCurrencyManager.getBalance(buyer.getUniqueId()));
            
            logger.info("External trade completed: " + buyer.getName() + 
                       " bought from " + villagerName + " (Village: " + villageUUID + 
                       ") for " + totalCost + " global currency");
            
        } catch (InsufficientBalanceException | InvalidOperationException e) {
            result.addError(ChatColor.RED + "Fehler: " + e.getMessage());
            logger.warning("External trade failed: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Validiere externen Käufer
     */
    private boolean validateBuyer(Player buyer, TradeResult result) {
        if (buyer == null || !buyer.isOnline()) {
            result.addError("Spieler ist nicht online");
            return false;
        }
        
        // Prüfe ob der Käufer aus dem gleichen Dorf ist (externe Spieler dürfen nicht Mitglieder sein)
        try {
            com.example.village.VillagePlugin plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(com.example.village.VillagePlugin.class);
            if (plugin.getVillageManager() != null) {
                com.example.village.model.Village village = plugin.getVillageManager()
                        .getVillage(UUID.fromString(villageUUID)).orElse(null);
                if (village != null && village.isMember(buyer.getUniqueId())) {
                    result.addError("Dorfmitglieder müssen die lokale Währung nutzen.");
                    return false;
                }
            }
        } catch (Exception e) {
            // Ignore / fallback
        }
        
        return true;
    }
    
    /**
     * Validiere Angebot für extern
     */
    private boolean validateOffer(TradeOffer offer, int quantity, TradeResult result) {
        if (offer == null) {
            result.addError("Angebot existiert nicht");
            return false;
        }
        
        if (quantity <= 0) {
            result.addError("Menge muss positiv sein");
            return false;
        }
        
        // Nur GLOBALE Angebote können extern gekauft werden
        if (offer.getCurrency() != CurrencyType.GLOBAL) {
            result.addError("Dieses Angebot ist nur für Dorf-Spieler verfügbar");
            return false;
        }
        
        return true;
    }
    
    /**
     * Convert Item-ID zu Bukkit Material
     */
    private Material getMaterialFromItemId(String itemId) {
        try {
            return Material.valueOf(itemId.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown material: " + itemId + ", using DIAMOND");
            return Material.DIAMOND;
        }
    }
    
    @Override
    public Optional<TradeOffer> getOfferForSlot(int slot) {
        return Optional.ofNullable(slotOffers.get(slot));
    }

    @Override
    public String formatPrice(double price, String symbol) {
        return String.format("%s %.0f", symbol, price);
    }
}
