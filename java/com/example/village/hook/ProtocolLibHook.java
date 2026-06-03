package com.example.village.hook;

import org.bukkit.Bukkit;

import java.util.logging.Logger;

public final class ProtocolLibHook {

    private boolean available;

    public void setup(Logger logger) {
        available = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
        if (available) {
            logger.info("ProtocolLib-Integration aktiviert.");
        } else {
            logger.info("ProtocolLib nicht gefunden - Lichtpakete werden nicht direkt beim Versand abgefangen.");
        }
    }

    public boolean isAvailable() {
        return available;
    }
}
