package com.example.village.currency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.logging.Logger;

/**
 * Tests für CurrencyConverter
 */
public class CurrencyConverterTest {
    
    private CurrencyConverter converter;
    
    @BeforeEach
    void setUp() {
        // Conv Rate 1.25: 100 Local = 80 Global (20% Verlust)
        converter = new CurrencyConverter(1.25, Logger.getLogger("test"));
    }
    
    @Test
    void testConvertLocalToGlobal() {
        // 100 Local -> 80 Global
        double result = converter.convertLocalToGlobal(100);
        assertEquals(80, result, 0.01);
    }
    
    @Test
    void testConvertLocalToGlobal_Small() {
        // 50 Local -> 40 Global
        double result = converter.convertLocalToGlobal(50);
        assertEquals(40, result, 0.01);
    }
    
    @Test
    void testConvertGlobalToLocal() {
        // 80 Global -> 100 Local (kein Verlust in diese Richtung)
        double result = converter.convertGlobalToLocal(80);
        assertEquals(100, result, 0.01);
    }
    
    @Test
    void testCalculateFee() {
        // 100 Local -> 80 Global, Gebühr = 20
        double fee = converter.calculateFee(100);
        assertEquals(20, fee, 0.01);
    }
    
    @Test
    void testConversionLossPercent() {
        // 20% Verlust
        double loss = converter.getConversionLossPercent();
        assertEquals(20, loss, 0.01);
    }
    
    @Test
    void testRoundTrip() {
        // 100 Local -> 80 Global -> 100 Local
        double global = converter.convertLocalToGlobal(100);
        double localAgain = converter.convertGlobalToLocal(global);
        assertEquals(100, localAgain, 0.01);
    }
    
    @Test
    void testZeroAmount() {
        assertEquals(0, converter.convertLocalToGlobal(0));
        assertEquals(0, converter.convertGlobalToLocal(0));
    }
}
