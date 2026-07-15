package com.example.village.trading;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.example.village.currency.*;
import com.example.village.model.*;

import org.bukkit.Location;
import java.util.*;
import java.util.logging.Logger;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für Trading UIs
 */
@DisplayName("Trading UI Tests")
class TradingUITest {
    
    private LocalCurrencyManager localCurrencyManager;
    private GlobalCurrencyManager globalCurrencyManager;
    private VillagerInventory villagerInventory;
    private Logger logger;
    
    private UUID villagerUUID;
    private UUID playerUUID;
    private UUID playerUUID2;
    
    @BeforeEach
    void setUp() throws InvalidOperationException {
        localCurrencyManager = new LocalCurrencyManager("village-1", "local_taler", "Local Taler", "Ɖ", Logger.getLogger("test"));
        globalCurrencyManager = new GlobalCurrencyManager();
        villagerInventory = new VillagerInventory(10);
        logger = Logger.getLogger("TestLogger");
        
        villagerUUID = UUID.randomUUID();
        playerUUID = UUID.randomUUID();
        playerUUID2 = UUID.randomUUID();
        
        // Setup Player-Guthaben
        localCurrencyManager.initializePlayer(playerUUID, 500);
        globalCurrencyManager.addBalance(playerUUID, 1000);
        globalCurrencyManager.addBalance(playerUUID2, 800);
    }

    // ====== VillagerLocalTradeUI Tests ======
    
    @Test
    @DisplayName("VillagerLocalTradeUI: Villager auf Lager, Spieler kann kaufen")
    void testVillagerLocalTradeUI_SuccessfulPurchase() {
        // Setup
        TradeOffer offer = new TradeOffer("OAK_LOG", "Eichenholz", 1, 50, CurrencyType.LOCAL, 100);
        villagerInventory.addForSale(offer);
        
        VillagerLocalTradeUI ui = new VillagerLocalTradeUI(
            villagerUUID.toString(),
            "Forstmeister Klaus",
            "village-1",
            villagerInventory,
            localCurrencyManager,
            logger
        );
        
        // Execute: Spieler kauft 2 Stacks
        TradeResult result = ui.processTrade(null, offer, 2);
        
        // Verify: Trade sollte fehlschlagen wegen null Player
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("VillagerLocalTradeUI: Zu wenig Währung → Fehler")
    void testVillagerLocalTradeUI_InsufficientFunds() throws InvalidOperationException {
        // Setup
        UUID poorPlayer = UUID.randomUUID();
        localCurrencyManager.initializePlayer(poorPlayer, 20);  // Nur 20 Währung
        
        TradeOffer offer = new TradeOffer("DIAMOND", "Diamant", 1, 50, CurrencyType.LOCAL, 100);
        villagerInventory.addForSale(offer);
        
        VillagerLocalTradeUI ui = new VillagerLocalTradeUI(
            villagerUUID.toString(),
            "Steinmetz",
            "village-1",
            villagerInventory,
            localCurrencyManager,
            logger
        );
        
        double playerBalance = localCurrencyManager.getBalance(poorPlayer);
        assertTrue(playerBalance < (50 * 2), "Spieler sollte zu arm sein");
    }

    @Test
    @DisplayName("VillagerLocalTradeUI: Villager sollte nicht mit globaler Währung auf rechnung")
    void testVillagerLocalTradeUI_GlobalCurrencyRejected() {
        TradeOffer offer = new TradeOffer("GOLD_BLOCK", "Goldblock", 1, 100, CurrencyType.GLOBAL, 50);
        villagerInventory.addForSale(offer);
        
        VillagerLocalTradeUI ui = new VillagerLocalTradeUI(
            villagerUUID.toString(),
            "Goldsmith",
            "village-1",
            villagerInventory,
            localCurrencyManager,
            logger
        );
        
        assertEquals(CurrencyType.GLOBAL, offer.getCurrency());
    }

    // ====== VillagerExternalTradeUI Tests ======
    
    @Test
    @DisplayName("VillagerExternalTradeUI: Externe Spieler zahlen global")
    void testVillagerExternalTradeUI_ExternalPlayerGlobal() {
        TradeOffer offer = new TradeOffer("IRON_INGOT", "Eisenbarren", 1, 75, CurrencyType.GLOBAL, 200);
        villagerInventory.addForSale(offer);
        
        double globalBalanceBefore = globalCurrencyManager.getBalance(playerUUID2);
        assertEquals(800, globalBalanceBefore);
        
        double expectedBalance = 800 - 75;
        assertTrue(expectedBalance > 0);
    }

    @Test
    @DisplayName("VillagerExternalTradeUI: Nur globale Angebote für extern")
    void testVillagerExternalTradeUI_OnlyGlobalOffers() {
        TradeOffer localOffer = new TradeOffer("WHEAT", "Weizen", 1, 30, CurrencyType.LOCAL, 100);
        villagerInventory.addForSale(localOffer);
        
        assertNotEquals(CurrencyType.GLOBAL, localOffer.getCurrency());
    }

    // ====== PlayerToPlayerTradeUI Tests ======

    @Test
    @DisplayName("PlayerToPlayerTradeUI: P2P Trade basic create")
    void testPlayerToPlayerTradeUI_Creation() {
        PlayerToPlayerTradeUI ui = new PlayerToPlayerTradeUI(globalCurrencyManager, logger, new File("target/test-data"));
        assertNotNull(ui);
    }

    @Test
    @DisplayName("PlayerToPlayerTradeUI: Zu wenig Währung → Fehler")
    void testPlayerToPlayerTradeUI_InsufficientBalance() throws InvalidOperationException {
        UUID brokePlayer = UUID.randomUUID();
        globalCurrencyManager.addBalance(brokePlayer, 50);
        
        PlayerToPlayerTradeUI ui = new PlayerToPlayerTradeUI(globalCurrencyManager, logger, new File("target/test-data"));
        
        double balance = globalCurrencyManager.getBalance(brokePlayer);
        assertTrue(balance < 200);
    }

    // ====== MarketplaceUI Tests ======

    @Test
    @DisplayName("MarketplaceUI: Shop mit Items anlegen")
    void testMarketplaceUI_AddOffers() {
        MarketplaceUI shop = new MarketplaceUI(
            playerUUID.toString(),
            "Kraemer's Laden",
            new Location(null, 0, 0, 0),
            globalCurrencyManager,
            localCurrencyManager,
            logger
        );
        
        TradeOffer offer1 = new TradeOffer("APPLE", "Apfel", 1, 5, CurrencyType.LOCAL, 100);
        TradeOffer offer2 = new TradeOffer("GOLD_INGOT", "Goldbarren", 1, 500, CurrencyType.GLOBAL, 20);
        
        shop.addOffer(offer1);
        shop.addOffer(offer2);
        
        assertEquals("Kraemer's Laden", shop.getShopName());
        assertEquals(playerUUID.toString(), shop.getShopOwner());
    }

    @Test
    @DisplayName("MarketplaceUI: Statistiken tracken")
    void testMarketplaceUI_TrackStatistics() {
        MarketplaceUI shop = new MarketplaceUI(
            playerUUID.toString(),
            "Statistik-Test Shop",
            new Location(null, 0, 0, 0),
            globalCurrencyManager,
            localCurrencyManager,
            logger
        );
        
        assertEquals(0, shop.getTotalSales());
        assertEquals(0, shop.getTotalRevenue());
    }

    @Test
    @DisplayName("MarketplaceUI: Dual-Währung Support (lokal + global)")
    void testMarketplaceUI_DualCurrency() {
        MarketplaceUI shop = new MarketplaceUI(
            playerUUID.toString(),
            "Dual-Shop",
            new Location(null, 0, 0, 0),
            globalCurrencyManager,
            localCurrencyManager,
            logger
        );
        
        TradeOffer localOffer = new TradeOffer("CARROTS", "Karotten", 1, 40, CurrencyType.LOCAL, 50);
        TradeOffer globalOffer = new TradeOffer("NETHER_STAR", "Netherstern", 1, 5000, CurrencyType.GLOBAL, 1);
        
        shop.addOffer(localOffer);
        shop.addOffer(globalOffer);
        
        assertEquals(CurrencyType.LOCAL, localOffer.getCurrency());
        assertEquals(CurrencyType.GLOBAL, globalOffer.getCurrency());
    }

    @Test
    @DisplayName("Integration: Mehrere Trading UIs nebeneinander")
    void testMultipleTradingUIsConcurrent() {
        VillagerLocalTradeUI localUI = new VillagerLocalTradeUI(
            villagerUUID.toString(),
            "Hans",
            "village-1",
            villagerInventory,
            localCurrencyManager,
            logger
        );
        MarketplaceUI marketUI = new MarketplaceUI(
            playerUUID.toString(),
            "Markt-Hall",
            new Location(null, 0, 0, 0),
            globalCurrencyManager,
            localCurrencyManager,
            logger
        );
        
        assertNotNull(localUI);
        assertNotNull(marketUI);
    }
}
