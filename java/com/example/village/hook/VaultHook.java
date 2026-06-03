package com.example.village.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class VaultHook {

    private Object economy;
    private boolean available;
    private Class<?> economyClass;

    public void setup(Logger logger) {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                logger.info("Vault nicht gefunden - Wirtschaftssystem deaktiviert.");
                available = false;
                return;
            }

            economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (rsp == null) {
                logger.warning("Vault gefunden, aber kein Economy-Provider registriert.");
                available = false;
                return;
            }

            economy = rsp.getProvider();
            available = economy != null;
            if (available) {
                Method nameMethod = economyClass.getMethod("getName");
                Object name = nameMethod.invoke(economy);
                logger.info("Vault-Integration aktiviert (" + name + ").");
            }
        } catch (ClassNotFoundException e) {
            logger.info("Vault API nicht verfügbar zur Compilezeit; Integration bleibt deaktiviert.");
            available = false;
        } catch (Exception e) {
            logger.warning("Fehler beim Initialisieren der Vault-Integration: " + e.getMessage());
            available = false;
        }
    }

    public boolean isAvailable() {
        return available && economy != null;
    }

    public double getBalance(Player player) {
        if (!isAvailable()) return 0;
        try {
            Method method = economyClass.getMethod("getBalance", Player.class);
            return (double) method.invoke(economy, player);
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean has(Player player, double amount) {
        if (!isAvailable()) return true;
        try {
            Method method = economyClass.getMethod("has", Player.class, double.class);
            return (boolean) method.invoke(economy, player, amount);
        } catch (Exception e) {
            return true;
        }
    }

    public boolean withdraw(Player player, double amount) {
        if (!isAvailable()) return true;
        try {
            Method method = economyClass.getMethod("withdrawPlayer", Player.class, double.class);
            Object response = method.invoke(economy, player, amount);
            Method successMethod = response.getClass().getMethod("transactionSuccess");
            return (boolean) successMethod.invoke(response);
        } catch (Exception e) {
            return true;
        }
    }

    public boolean deposit(Player player, double amount) {
        if (!isAvailable()) return true;
        try {
            Method method = economyClass.getMethod("depositPlayer", Player.class, double.class);
            Object response = method.invoke(economy, player, amount);
            Method successMethod = response.getClass().getMethod("transactionSuccess");
            return (boolean) successMethod.invoke(response);
        } catch (Exception e) {
            return true;
        }
    }

    public String format(double amount) {
        if (!isAvailable()) return String.format("%.2f", amount);
        try {
            Method method = economyClass.getMethod("format", double.class);
            return (String) method.invoke(economy, amount);
        } catch (Exception e) {
            return String.format("%.2f", amount);
        }
    }
}
