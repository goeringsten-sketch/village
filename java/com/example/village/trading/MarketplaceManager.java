package com.example.village.trading;

import org.bukkit.Location;

import com.example.village.currency.GlobalCurrencyManager;
import com.example.village.currency.LocalCurrencyManager;

import java.util.*;
import java.util.logging.Logger;

/**
 * Manager für registrierte Marktplätze und Shop-Instanzen.
 *
 * Erlaubt später persistente Shops, Shop-Lookup und Shop-Registrierung.
 */
public class MarketplaceManager {

    private final Map<String, MarketplaceUI> shopsByOwner = Collections.synchronizedMap(new HashMap<>());
    private final Logger logger;

    public MarketplaceManager(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null");
    }

    public MarketplaceUI createShop(String ownerUUID, String shopName, Location location,
                                   GlobalCurrencyManager globalCurrencyManager,
                                   LocalCurrencyManager localCurrencyManager) {
        Objects.requireNonNull(ownerUUID, "Owner UUID cannot be null");
        Objects.requireNonNull(shopName, "Shop name cannot be null");
        Objects.requireNonNull(location, "Location cannot be null");

        MarketplaceUI existing = shopsByOwner.get(ownerUUID);
        if (existing != null) {
            return existing;
        }

        MarketplaceUI marketplace = new MarketplaceUI(ownerUUID, shopName, location,
            globalCurrencyManager, localCurrencyManager, logger);
        shopsByOwner.put(ownerUUID, marketplace);
        logger.info("Registered marketplace: " + shopName + " for owner " + ownerUUID);
        return marketplace;
    }

    public Optional<MarketplaceUI> getShop(String ownerUUID) {
        return Optional.ofNullable(shopsByOwner.get(ownerUUID));
    }

    public Collection<MarketplaceUI> getAllShops() {
        return Collections.unmodifiableCollection(shopsByOwner.values());
    }

    public boolean removeShop(String ownerUUID) {
        if (shopsByOwner.containsKey(ownerUUID)) {
            shopsByOwner.remove(ownerUUID);
            logger.info("Removed marketplace for owner " + ownerUUID);
            return true;
        }
        return false;
    }

    public List<String> listShopNames() {
        List<String> shopNames = new ArrayList<>();
        for (MarketplaceUI shop : shopsByOwner.values()) {
            shopNames.add(shop.getShopName());
        }
        return shopNames;
    }
}
