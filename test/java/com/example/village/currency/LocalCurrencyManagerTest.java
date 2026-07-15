package com.example.village.currency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Tests für LocalCurrencyManager
 */
public class LocalCurrencyManagerTest {
    
    private LocalCurrencyManager manager;
    private UUID player1;
    private UUID player2;
    private String villager1;
    
    @BeforeEach
    void setUp() {
        manager = new LocalCurrencyManager(
            "village-123",
            "gold_taler",
            "Gold Taler",
            "Ɖ",
            Logger.getLogger("test")
        );
        
        player1 = UUID.randomUUID();
        player2 = UUID.randomUUID();
        villager1 = "villager-001";
    }
    
    @Test
    void testInitializePlayer() throws InvalidOperationException {
        manager.initializePlayer(player1, 100);
        assertEquals(100, manager.getBalance(player1));
    }
    
    @Test
    void testAddBalance() throws InvalidOperationException {
        manager.initializePlayer(player1, 50);
        manager.addBalance(player1, 30);
        assertEquals(80, manager.getBalance(player1));
    }
    
    @Test
    void testRemoveBalance() throws InvalidOperationException, InsufficientBalanceException {
        manager.initializePlayer(player1, 100);
        manager.removeBalance(player1, 30);
        assertEquals(70, manager.getBalance(player1));
    }
    
    @Test
    void testRemoveBalanceInsufficientFunds() throws InvalidOperationException {
        manager.initializePlayer(player1, 50);
        assertThrows(InsufficientBalanceException.class, () -> {
            manager.removeBalance(player1, 100);
        });
    }
    
    @Test
    void testTransferBetweenPlayers() throws InvalidOperationException, InsufficientBalanceException {
        manager.initializePlayer(player1, 100);
        manager.initializePlayer(player2, 50);
        
        manager.transfer(player1, player2, 30);
        
        assertEquals(70, manager.getBalance(player1));
        assertEquals(80, manager.getBalance(player2));
    }
    
    @Test
    void testVillagerBalance() throws InvalidOperationException {
        manager.initializeVillager(villager1, 200);
        assertEquals(200, manager.getVillagerBalance(villager1));
    }
    
    @Test
    void testTransferToVillager() throws InvalidOperationException, InsufficientBalanceException {
        manager.initializePlayer(player1, 100);
        manager.initializeVillager(villager1, 0);
        
        manager.transferToVillager(player1, villager1, 25);
        
        assertEquals(75, manager.getBalance(player1));
        assertEquals(25, manager.getVillagerBalance(villager1));
    }
    
    @Test
    void testTransferFromVillager() throws InvalidOperationException, InsufficientBalanceException {
        manager.initializePlayer(player1, 100);
        manager.initializeVillager(villager1, 50);
        
        manager.transferFromVillager(villager1, player1, 20);
        
        assertEquals(120, manager.getBalance(player1));
        assertEquals(30, manager.getVillagerBalance(villager1));
    }
    
    @Test
    void testFormatCurrency() {
        String formatted = manager.formatCurrency(150);
        assertTrue(formatted.contains("Ɖ") || formatted.contains("150"));
    }
    
    @Test
    void testTotalInCirculation() throws InvalidOperationException {
        manager.initializePlayer(player1, 100);
        manager.initializePlayer(player2, 150);
        manager.initializeVillager(villager1, 50);
        
        assertEquals(300, manager.getTotalInCirculation());
    }
    
    @Test
    void testNegativeAmountThrowsException() throws InvalidOperationException {
        manager.initializePlayer(player1, 100);
        
        assertThrows(InvalidOperationException.class, () -> {
            manager.addBalance(player1, -50);
        });
    }
}
