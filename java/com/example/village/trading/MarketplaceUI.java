package com.example.village.trading;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.Location;

import com.example.village.currency.*;
import com.example.village.model.*;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Trading UI für Marktplatz / Spieler-betriebene Läden
 * 
 * Szenario:
 * - Spieler A hat einen Laden (gegebeben durch Dorf oder freistehend)
 * - Spieler B kauft von Laden A
 * - Spieler A kann in BEIDEN Währungen verkaufen (lokal + global)
 * - Spieler B zahlt mit verfügbarer Währung
 */
public class MarketplaceUI implements TradingUI {
    
    private final String shopOwnerUUID;
    private final String shopName;
    private final Location shopLocation;
    private final List<TradeOffer> shopInventory;
    private final GlobalCurrencyManager globalCurrencyManager;
    private final LocalCurrencyManager localCurrencyManager;
    private final Logger logger;
    private final Map<Integer, TradeOffer> slotOffers = new HashMap<>();
    
    // Statistiken
    private long totalSales = 0;
    private double totalRevenue = 0;
    
    public MarketplaceUI(String shopOwnerUUID, String shopName, Location shopLocation,
                        GlobalCurrencyManager globalCurrencyManager,
                        LocalCurrencyManager localCurrencyManager,
                        Logger logger) {
        this.shopOwnerUUID = Objects.requireNonNull(shopOwnerUUID);
        this.shopName = Objects.requireNonNull(shopName);
        this.shopLocation = Objects.requireNonNull(shopLocation);
        this.shopInventory = Collections.synchronizedList(new ArrayList<>());
        this.globalCurrencyManager = Objects.requireNonNull(globalCurrencyManager);
        this.localCurrencyManager = Objects.requireNonNull(localCurrencyManager);
        this.logger = Objects.requireNonNull(logger);
    }
    
    /**
     * Füge Angebot zum Shop hinzu
     */
    public void addOffer(TradeOffer offer) {
        shopInventory.add(Objects.requireNonNull(offer));
        logger.info("Added offer to " + shopName + ": " + offer.getDisplayName());
    }
    
    /**
     * Entferne Angebot vom Shop
     */
    public void removeOffer(String itemId) {
        shopInventory.removeIf(offer -> offer.getItemId().equals(itemId));
    }
    
    /**
     * Öffne Shop für Käufer
     */
    @Override
    public void openTradingUI(Player buyer) {
        List<TradeOffer> availableOffers = shopInventory.stream()
            .filter(offer -> offer.hasStock(1))
            .collect(Collectors.toList());
        
        if (availableOffers.isEmpty()) {
            buyer.sendMessage(ChatColor.YELLOW + "Der Shop " + shopName + 
                            " hat keine Artikel auf Lager");
            return;
        }
        
        // Erstelle GUI mit 54 Slots (6 Reihen)
        Inventory inventory = Bukkit.createInventory(null, 54,
            ChatColor.GOLD + shopName + ChatColor.GRAY + " - Marktplatz");
        
        // Füge Items hinzu
        slotOffers.clear();
        int slot = 0;
        for (TradeOffer offer : availableOffers) {
            ItemStack item = createMarketplaceItem(offer);
            inventory.setItem(slot, item);
            slotOffers.put(slot, offer);
            slot++;
            if (slot >= 54) break;  // Limit auf 54 Slots
        }
        
        // Statistik-Info am Ende
        ItemStack infoItem = createInfoItem();
        if (inventory.getItem(53) == null) {
            inventory.setItem(53, infoItem);
        }
        
        buyer.openInventory(inventory);
    }
    
    /**
     * Erstelle Item für Marktplatz-Anzeige
     */
    private ItemStack createMarketplaceItem(TradeOffer offer) {
        Material material = getMaterialFromItemId(offer.getItemId());
        ItemStack item = new ItemStack(material, offer.getQuantityPerTrade());
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + offer.getDisplayName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "─────────────────");
            lore.add(ChatColor.AQUA + "Verkäufer: " + ChatColor.WHITE + shopName);
            lore.add("");
            
            // Zeige Preis mit Währung
            String priceDisplay = formatOfferPrice(offer);
            lore.add(ChatColor.GREEN + "Preis: " + priceDisplay);
            lore.add(ChatColor.GRAY + "Menge: " + offer.getQuantityPerTrade());
            lore.add(ChatColor.AQUA + "Verfügbar: " + offer.getStock());
            
            lore.add("");
            lore.add(ChatColor.GOLD + "Klicken zum kaufen");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Erstelle Info-Item mit Shop-Statistiken
     */
    private ItemStack createInfoItem() {
        ItemStack info = new ItemStack(Material.BEACON);
        ItemMeta meta = info.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + shopName);
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "─────────────────");
            lore.add(ChatColor.YELLOW + "Verkäufe: " + ChatColor.WHITE + totalSales);
            lore.add(ChatColor.GREEN + "Umsatz: " + totalRevenue);
            lore.add(ChatColor.GRAY + "Ort: " + formatLocation(shopLocation));
            
            meta.setLore(lore);
            info.setItemMeta(meta);
        }
        
        return info;
    }
    
    /**
     * Verarbeite einen Kauf
     * @param buyer Der Käufer
     * @param offer Das Angebot
     * @param quantity Anzahl Stacks
     */
    @Override
    public TradeResult processTrade(Player buyer, TradeOffer offer, int quantity) {
        TradeResult result = new TradeResult();
        
        try {
            // 1. Validierung
            if (!validateBuyer(buyer, result)) {
                return result;
            }
            
            if (!validateMarketplaceOffer(offer, quantity, result)) {
                return result;
            }
            
            double totalCost = offer.getPrice() * quantity;
            
            // 2. Versuche Zahlung zu verarbeiten
            // Icon: Bevorzuge lokale Währung wenn verfügbar
            CurrencyType preferredCurrency = offer.getCurrency();
            
            if (preferredCurrency == CurrencyType.LOCAL) {
                // Nur Käufer aus gleichem Dorf können mit lokal zahlen
                // TODO: Check if buyer is in same village
                
                double balance = localCurrencyManager.getBalance(buyer.getUniqueId());
                if (balance < totalCost) {
                    result.addError("Du hast nicht genug lokale Währung. " +
                                   "Benötigt: " + totalCost + 
                                   ", Du hast: " + balance);
                    return result;
                }
                
                // Transfer zu Shop-Besitzer
                localCurrencyManager.transferToVillager(
                    buyer.getUniqueId(), 
                    shopOwnerUUID, 
                    totalCost
                );
                
                result.setNewLocalBalance(localCurrencyManager.getBalance(buyer.getUniqueId()));
                
            } else {
                // Globale Währung
                double balance = globalCurrencyManager.getBalance(buyer.getUniqueId());
                if (balance < totalCost) {
                    result.addError("Du hast nicht genug globale Währung. " +
                                   "Benötigt: " + globalCurrencyManager.formatCurrency(totalCost) +
                                   ", Du hast: " + globalCurrencyManager.formatCurrency(balance));
                    return result;
                }
                
                // Transfer zu Shop-Besitzer
                globalCurrencyManager.transfer(
                    buyer.getUniqueId(),
                    UUID.fromString(shopOwnerUUID),
                    totalCost
                );
                
                result.setNewGlobalBalance(globalCurrencyManager.getBalance(buyer.getUniqueId()));
            }
            
            // 3. Gebe Items
            ItemStack itemStack = new ItemStack(
                getMaterialFromItemId(offer.getItemId()),
                offer.getQuantityPerTrade() * quantity
            );
            
            Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(itemStack);
            
            // Überschüssige Items spawnen
            if (!leftover.isEmpty()) {
                leftover.forEach((slot, item) -> {
                    buyer.getWorld().dropItemNaturally(buyer.getLocation(), item);
                });
            }
            
            // 4. Update Stock
            offer.reduceStock(quantity);
            
            // 5. Update Statistiken
            totalSales++;
            totalRevenue += totalCost;
            
            result.setSuccess(true);
            result.setMessage(ChatColor.GREEN + "Kauf erfolgreich!");
            
            // Benachrichtige Shop-Besitzer (wenn online)
            Player owner = Bukkit.getPlayer(UUID.fromString(shopOwnerUUID));
            if (owner != null && owner.isOnline()) {
                owner.sendMessage(ChatColor.GOLD + buyer.getName() + ChatColor.RESET + 
                                 " hat von deinem Shop " + ChatColor.GOLD + shopName + 
                                 ChatColor.RESET + " gekauft!");
            }
            
            logger.info("Marketplace purchase: " + buyer.getName() + " bought from " + 
                       shopName + " for " + totalCost);
            
        } catch (Exception e) {
            result.addError(ChatColor.RED + "Fehler: " + e.getMessage());
            logger.severe("Marketplace purchase failed: " + e.getMessage());
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
     * Validiere Marktplatz-Angebot
     */
    private boolean validateMarketplaceOffer(TradeOffer offer, int quantity, TradeResult result) {
        if (offer == null) {
            result.addError("Angebot existiert nicht");
            return false;
        }
        
        if (!shopInventory.contains(offer)) {
            result.addError("Angebot ist nicht mehr in diesem Shop");
            return false;
        }
        
        if (quantity <= 0) {
            result.addError("Menge muss positiv sein");
            return false;
        }
        
        if (!offer.hasStock(quantity)) {
            result.addError("Nicht genug auf Lager");
            return false;
        }
        
        return true;
    }
    
    /**
     * Format Angebots-Preis mit Währungs-Sym bol
     */
    private String formatOfferPrice(TradeOffer offer) {
        if (offer.getCurrency() == CurrencyType.LOCAL) {
            return offer.getPrice() + " Ɖ";
        } else {
            return globalCurrencyManager.formatCurrency(offer.getPrice());
        }
    }
    
    /**
     * Format Location für Anzeige
     */
    private String formatLocation(Location loc) {
        if (loc == null) return "?";
        return String.format("%s (%d, %d, %d)",
            loc.getWorld() != null ? loc.getWorld().getName() : "?",
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
    
    /**
     * Convert Item-ID zu Bukkit Material
     */
    private Material getMaterialFromItemId(String itemId) {
        try {
            return Material.valueOf(itemId.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown material: " + itemId + ", using CHEST");
            return Material.CHEST;
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
    
    // Getter für Statistiken
    public long getTotalSales() {
        return totalSales;
    }
    
    public double getTotalRevenue() {
        return totalRevenue;
    }
    
    public String getShopName() {
        return shopName;
    }
    
    public String getShopOwner() {
        return shopOwnerUUID;
    }
}
