package com.example.village.hook;

import com.example.village.currency.PlayerCurrencyAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.logging.Logger;

public final class OpenEcoCurrencyHook implements PlayerCurrencyAPI {
    private final Logger logger;
    private Object api;
    private boolean available;

    public OpenEcoCurrencyHook(Logger logger) {
        this.logger = logger;
        setup();
    }

    private void setup() {
        try {
            Class<?> apiClass = Class.forName("dev.alexisbinh.openeco.api.OpenEcoApi");
            RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration((Class) apiClass);
            if (reg == null) {
                available = false;
                return;
            }
            api = reg.getProvider();
            available = api != null;
            if (available) {
                logger.info("OpenEco API erkannt: " + api.getClass().getName());
            }
        } catch (ClassNotFoundException e) {
            available = false;
        } catch (Exception e) {
            available = false;
            logger.warning("OpenEco API setup fehlgeschlagen: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available && api != null;
    }

    @Override
    public double getBalance(UUID player, String currencyName) {
        try {
            Object result = call("getBalance", new Class[]{UUID.class, String.class}, player, currencyName);
            if (result instanceof BigDecimal bd) {
                return bd.doubleValue();
            }
            if (result instanceof Number n) {
                return n.doubleValue();
            }
        } catch (Exception e) {
            logger.warning("OpenEco getBalance fehlgeschlagen: " + e.getMessage());
        }
        return 0.0;
    }

    @Override
    public void addBalance(UUID player, String currencyName, double amount) {
        Object result = call("deposit",
                new Class[]{UUID.class, String.class, BigDecimal.class},
                player, currencyName, BigDecimal.valueOf(amount));
        ensureSuccessResult(result, "deposit");
    }

    @Override
    public void removeBalance(UUID player, String currencyName, double amount) {
        Object result = call("withdraw",
                new Class[]{UUID.class, String.class, BigDecimal.class},
                player, currencyName, BigDecimal.valueOf(amount));
        ensureSuccessResult(result, "withdraw");
    }

    @Override
    public boolean hasCurrency(String currencyId) {
        try {
            Object result = call("hasCurrency", new Class[]{String.class}, currencyId);
            return result instanceof Boolean b && b;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void ensureAccount(UUID playerId, String playerName) {
        call("ensureAccount", new Class[]{UUID.class, String.class}, playerId, playerName);
    }

    private Object call(String name, Class<?>[] types, Object... args) {
        if (!isAvailable()) {
            throw new IllegalStateException("OpenEco API nicht verfügbar");
        }
        try {
            Method method = api.getClass().getMethod(name, types);
            return method.invoke(api, args);
        } catch (Exception e) {
            throw new IllegalStateException("OpenEco call failed (" + name + "): " + e.getMessage(), e);
        }
    }

    private void ensureSuccessResult(Object result, String op) {
        if (result == null) {
            return;
        }
        try {
            Method isSuccess = result.getClass().getMethod("isSuccess");
            Object ok = isSuccess.invoke(result);
            if (ok instanceof Boolean b && b) {
                return;
            }
            throw new IllegalStateException("OpenEco " + op + " rejected: " + result);
        } catch (NoSuchMethodException ignored) {
            // If result object has no isSuccess, assume no exception means success.
        } catch (Exception e) {
            throw new IllegalStateException("OpenEco " + op + " status check failed: " + e.getMessage(), e);
        }
    }
}
