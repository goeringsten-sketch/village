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
        
        // Hole alle Verkaufs-Angebote (Linke Seite - Spieler kauft)
        List<TradeOffer> sellOffers = villagerInventory.getForSale()
            .stream()
            .filter(offer -> offer.getCurrency() == CurrencyType.LOCAL)
            .filter(offer -> offer.hasStock(1))  // Nur mit Bestand
            .collect(Collectors.toList());
        
        // Hole alle Kauf-Angebote (Rechte Seite - Spieler verkauft)
        List<TradeOffer> buyOffers = villagerInventory.getWantToBuy()
            .stream()
            .filter(offer -> offer.getCurrency() == CurrencyType.LOCAL)
            .filter(offer -> offer.hasStock(1))
            .collect(Collectors.toList());
        
        slotOffers.clear();

        // 1. Trenner setzen
        ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta sepMeta = separator.getItemMeta();
        if (sepMeta != null) {
            sepMeta.setDisplayName(" ");
            separator.setItemMeta(sepMeta);
        }
        inventory.setItem(4, separator);
        inventory.setItem(13, separator);
        inventory.setItem(22, separator);

        // 2. Linke Seite (Verkauf vom Villager an Spieler)
        int[] leftSlots = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21};
        int leftIdx = 0;
        for (TradeOffer offer : sellOffers) {
            if (leftIdx >= leftSlots.length) break;
            int slot = leftSlots[leftIdx++];
            ItemStack item = createTradeItem(offer, true);
            inventory.setItem(slot, item);
            slotOffers.put(slot, offer);
        }

        // 3. Rechte Seite (Ankauf vom Villager von Spieler)
        int[] rightSlots = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26};
        int rightIdx = 0;
        for (TradeOffer offer : buyOffers) {
            if (rightIdx >= rightSlots.length) break;
            int slot = rightSlots[rightIdx++];
            ItemStack item = createTradeItem(offer, false);
            inventory.setItem(slot, item);
            slotOffers.put(slot, offer);
        }
        
        player.openInventory(inventory);
    }
    
    /**
     * Erstelle Item zum Anzeigen eines Trade-Angebots
     */
    private ItemStack createTradeItem(TradeOffer offer, boolean isBuy) {
        Material material = getMaterialFromItemId(offer.getItemId());
        ItemStack item = new ItemStack(material, offer.getQuantityPerTrade());
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + offer.getDisplayName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "─────────────────");
            if (isBuy) {
                lore.add(ChatColor.GREEN + "Preis (Kauf): " + 
                         currencyManager.formatCurrency(offer.getPrice()));
                lore.add(ChatColor.AQUA + "Bestand: " + offer.getStock());
                lore.add(ChatColor.GRAY + "Menge: " + offer.getQuantityPerTrade());
                lore.add("");
                lore.add(ChatColor.GOLD + "Klicken zum Kaufen");
            } else {
                lore.add(ChatColor.GREEN + "Erlös (Verkauf): " + 
                         currencyManager.formatCurrency(offer.getPrice()));
                lore.add(ChatColor.AQUA + "Nachfrage: " + offer.getStock());
                lore.add(ChatColor.GRAY + "Menge benötigt: " + offer.getQuantityPerTrade());
                lore.add("");
                lore.add(ChatColor.GOLD + "Klicken zum Verkaufen");
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    private ItemStack createTradeItem(TradeOffer offer) {
        return createTradeItem(offer, true);
    }
    
    /**
     * Verarbeite einen Trade
     * @param buyer Der Spieler der kauft/verkauft
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

            boolean isPlayerSelling = villagerInventory.getWantToBuy().contains(offer);

            if (isPlayerSelling) {
                // Spieler verkauft an Villager
                Material material = getMaterialFromItemId(offer.getItemId());
                int requiredQuantity = offer.getQuantityPerTrade() * quantity;
                if (!buyer.getInventory().containsAtLeast(new ItemStack(material), requiredQuantity)) {
                    result.addError("Du hast nicht genug " + offer.getDisplayName() + 
                                   " im Inventar. Benötigt: " + requiredQuantity);
                    return result;
                }

                double totalEarnings = offer.getPrice() * quantity;
                double villagerBalance = currencyManager.getVillagerBalance(villagerUUID);
                if (villagerBalance < totalEarnings) {
                    result.addError("Der Villager hat nicht genug " + currencyManager.getCurrencyName() + 
                                   " für diesen Ankauf. Benötigt: " + currencyManager.formatCurrency(totalEarnings) + 
                                   ", Villager hat: " + currencyManager.formatCurrency(villagerBalance));
                    return result;
                }

                if (!offer.hasStock(quantity)) {
                    result.addError("Der Villager möchte nicht mehr so viel ankaufen. " +
                                   "Gewünscht: " + offer.getStock() + ", Du bietest: " + quantity);
                    return result;
                }

                if (villagerInventory.getResource(offer.getItemId()) == 0 && !villagerInventory.hasSpace()) {
                    result.addError("Das Lager des Villagers ist voll.");
                    return result;
                }

                // Geld transferieren: Villager -> Spieler
                currencyManager.transferFromVillager(villagerUUID, buyer.getUniqueId(), totalEarnings);

                // Items aus dem Spieler-Inventar entfernen
                ItemStack toRemove = new ItemStack(material, requiredQuantity);
                buyer.getInventory().removeItem(toRemove);

                // Items dem Villager-Lager hinzufügen
                villagerInventory.addResource(offer.getItemId(), requiredQuantity);

                // Nachfrage reduzieren
                offer.reduceStock(quantity);

                result.setSuccess(true);
                result.setMessage(ChatColor.GREEN + "Erfolgreich verkauft!");
                result.setNewLocalBalance(currencyManager.getBalance(buyer.getUniqueId()));

                logger.info("Trade completed (Player Sell): " + buyer.getName() + " sold to " + 
                           villagerName + " for " + totalEarnings + " " + 
                           currencyManager.getCurrencyName());

            } else {
                // Spieler kauft von Villager
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
                
                if (!leftover.isEmpty()) {
                    // Items spawnen auf dem Boden
                    leftover.forEach((slot, item) -> {
                        buyer.getWorld().dropItemNaturally(buyer.getLocation(), item);
                    });
                }
                
                // 6. Update Villager-Stock & Ressourcen
                int itemAmount = offer.getQuantityPerTrade() * quantity;
                if (villagerInventory.getResource(offer.getItemId()) >= itemAmount) {
                    villagerInventory.removeResource(offer.getItemId(), itemAmount);
                }
                offer.reduceStock(quantity);
                
                // Erfolg!
                result.setSuccess(true);
                result.setMessage(ChatColor.GREEN + "Trade erfolgreich abgeschlossen!");
                result.setNewLocalBalance(currencyManager.getBalance(buyer.getUniqueId()));
                
                logger.info("Trade completed: " + buyer.getName() + " bought from " + 
                           villagerName + " for " + totalCost + " " + 
                           currencyManager.getCurrencyName());
            }
            
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
     * Convert Item-ID String zu Bukkit Material.
     * Unterstützt Namen wie MATERIAL und Minecraft-Item-IDs mit Fallback auf Material.matchMaterial().
     */
    private Material getMaterialFromItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            logger.warning("Empty itemId for local villager trade; using STONE");
            return Material.STONE;
        }
        String normalized = itemId.trim().toUpperCase(Locale.ROOT);
        try {
            return Material.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            Material resolved = Material.matchMaterial(normalized);
            if (resolved != null) {
                return resolved;
            }
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
