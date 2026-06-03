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
 * Trading UI für Dorf-Spieler mit Dorf-Villager (Lokale Currency)
 * 
 * Szenario:
 * - Spieler aus Dorf A kauft/verkauft an Villager aus Dorf A
 * - Nur lokale Währung
 */
public class VillagerLocalTradeUI implements TradingUI {
    
    private final String villagerUUID;
    private final String villagerName;
    private final String villageUUID;
    private final VillagerInventory villagerInventory;
    private final LocalCurrencyManager currencyManager;
    private final Logger logger;
    private final Map<Integer, TradeOffer> slotOffers = new HashMap<>();
    
    public VillagerLocalTradeUI(String villagerUUID, String villagerName, String villageUUID,
                               VillagerInventory inventory, LocalCurrencyManager currencyManager,
                               Logger logger) {
        this.villagerUUID = Objects.requireNonNull(villagerUUID);
        this.villagerName = Objects.requireNonNull(villagerName);
        this.villageUUID = Objects.requireNonNull(villageUUID);
        this.villagerInventory = Objects.requireNonNull(inventory);
        this.currencyManager = Objects.requireNonNull(currencyManager);
        this.logger = Objects.requireNonNull(logger);
    }
    
    /**
     * Öffne Handel-Interface für Spieler
     */
    @Override
    public void openTradingUI(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, 
            ChatColor.GOLD + "Handeln mit " + villagerName);
        
        // Hole alle Verkaufs-Angebote
        List<TradeOffer> offers = villagerInventory.getForSale()
            .stream()
            .filter(offer -> offer.getCurrency() == CurrencyType.LOCAL)
            .filter(offer -> offer.hasStock(1))  // Nur mit Bestand
            .collect(Collectors.toList());
        
        // Füge als Items hinzu
        slotOffers.clear();
        int slot = 0;
        for (TradeOffer offer : offers) {
            ItemStack item = createTradeItem(offer);
            inventory.setItem(slot, item);
            slotOffers.put(slot, offer);
            slot++;
        }
        
        // TODO: Verkaufs-Seite (rechte Hälfte)
        
        player.openInventory(inventory);
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
                     currencyManager.formatCurrency(offer.getPrice()));
            lore.add(ChatColor.AQUA + "Bestand: " + offer.getStock());
            lore.add(ChatColor.GRAY + "Menge: " + offer.getQuantityPerTrade());
            lore.add("");
            lore.add(ChatColor.GOLD + "Klicken zum kaufen");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Verarbeite einen Trade
     * @param buyer Der Spieler der kauft
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
            
            // 2. Prüfe Spieler-Guthaben
            double playerBalance = currencyManager.getBalance(buyer.getUniqueId());
            double totalCost = offer.getPrice() * quantity;
            
            if (playerBalance < totalCost) {
                result.addError("Du hast nicht genug " + currencyManager.getCurrencyName() + 
                               ". Benötigt: " + currencyManager.formatCurrency(totalCost) + 
                               ", Du hast: " + currencyManager.formatCurrency(playerBalance));
                return result;
            }
            
            // 3. Prüfe Villager-Stock
            if (!offer.hasStock(quantity)) {
                result.addError("Der Villager hat nicht genug auf Lager. " +
                               "Verfügbar: " + offer.getStock() + ", Du möchtest: " + quantity);
                return result;
            }
            
            // 4. Transferiere Währung: Spieler → Villager
            currencyManager.transferToVillager(buyer.getUniqueId(), villagerUUID, totalCost);
            
            // 5. Gebe Items zum Spieler
            ItemStack itemStack = new ItemStack(
                getMaterialFromItemId(offer.getItemId()),
                offer.getQuantityPerTrade() * quantity
            );
            
            Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(itemStack);
            
            // Falls Spieler nicht genug Platz: Items auf Boden für Rollback nicht nötig
            // (die Transaktionen sind schon durchgeführt)
            if (!leftover.isEmpty()) {
                // Items spawnen auf dem Boden
                leftover.forEach((slot, item) -> {
                    buyer.getWorld().dropItemNaturally(buyer.getLocation(), item);
                });
            }
            
            // 6. Update Villager-Stock
            offer.reduceStock(quantity);
            
            // Erfolg!
            result.setSuccess(true);
            result.setMessage(ChatColor.GREEN + "Trade erfolgreich abgeschlossen!");
            result.setNewLocalBalance(currencyManager.getBalance(buyer.getUniqueId()));
            
            logger.info("Trade completed: " + buyer.getName() + " bought from " + 
                       villagerName + " for " + totalCost + " " + 
                       currencyManager.getCurrencyName());
            
        } catch (InsufficientBalanceException | InvalidOperationException e) {
            result.addError(ChatColor.RED + "Fehler: " + e.getMessage());
            logger.warning("Trade failed: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Validiere Käufer
     */
    private boolean validateBuyer(Player buyer, TradeResult result) {
        if (buyer == null || !buyer.isOnline()) {
            result.addError("Spieler ist nicht online");
            return false;
        }
        return true;
    }
    
    /**
     * Validiere Angebot
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
        
        if (offer.getCurrency() != CurrencyType.LOCAL) {
            result.addError("Dieses Angebot ist nur in globaler Währung verfügbar");
            return false;
        }
        
        return true;
    }
    
    /**
     * Convert Item-ID String zu Bukkit Material
     * TODO: Proper Item-System implementieren
     */
    private Material getMaterialFromItemId(String itemId) {
        try {
            return Material.valueOf(itemId.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown material: " + itemId + ", using STONE");
            return Material.STONE;
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
