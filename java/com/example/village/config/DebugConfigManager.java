package com.example.village.config;

import com.example.village.VillagePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public final class DebugConfigManager {

    private final VillagePlugin plugin;
    private FileConfiguration config;

    public DebugConfigManager(VillagePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "config/debug.yml");
        if (!file.exists()) {
            plugin.saveResource("config/debug.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled(String channel) {
        if (config == null) {
            return false;
        }
        return config.getBoolean("debug." + channel, config.getBoolean("debug.enabled", false));
    }

    public boolean isBackupConfigsEnabled() {
        if (config == null) {
            return false;
        }
        return config.getBoolean("debug.backup-configs", false);
    }

    public void debug(String channel, String message) {
        if (isEnabled(channel)) {
            plugin.getLogger().info("[Debug:" + channel + "] " + message);
        }
    }
}
