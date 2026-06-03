package com.example.village.service;

import com.example.village.config.CurrencyConfigManager;
import com.example.village.model.Village;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CurrencyService {
    private final Plugin plugin;
    private final CurrencyConfigManager config;
    private final Map<UUID, Map<String, Double>> balances = new ConcurrentHashMap<>();
    private final Set<UUID> joinBonusGranted = ConcurrentHashMap.newKeySet();
    private final File dataFile;
    private org.bukkit.configuration.file.FileConfiguration data;

    public CurrencyService(Plugin plugin, CurrencyConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.dataFile = new File(plugin.getDataFolder(), "currency-balances.yml");
        load();
    }

    public String getGlobalCurrencyId() {
        return config.getGlobalCurrencyId();
    }

    public String getVillageCurrencyId(Village village) {
        return config.getVillageCurrencyId(village.getName());
    }

    public String getVillageCurrencyDisplayName(Village village) {
        return config.getVillageCurrencyName(village.getName());
    }

    public synchronized double getBalance(UUID playerId, String currencyId) {
        return balances.getOrDefault(playerId, Collections.emptyMap()).getOrDefault(currencyId, 0.0);
    }

    public synchronized void addBalance(UUID playerId, String currencyId, double amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be >= 0");
        balances.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).merge(currencyId, amount, Double::sum);
    }

    public synchronized boolean removeBalance(UUID playerId, String currencyId, double amount) {
        if (amount < 0) return false;
        double current = getBalance(playerId, currencyId);
        if (current < amount) return false;
        balances.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(currencyId, current - amount);
        return true;
    }

    public synchronized boolean transfer(UUID from, UUID to, String currencyId, double amount) {
        if (amount <= 0) return false;
        if (!removeBalance(from, currencyId, amount)) return false;
        addBalance(to, currencyId, amount);
        return true;
    }

    public synchronized void ensureVillageCurrency(Village village) {
        String currencyId = getVillageCurrencyId(village);
        for (UUID member : village.getMembers().keySet()) {
            balances.computeIfAbsent(member, k -> new ConcurrentHashMap<>()).putIfAbsent(currencyId, 0.0);
        }
    }

    public synchronized void deleteVillageCurrency(Village village) {
        String currencyId = getVillageCurrencyId(village);
        for (Map<String, Double> account : balances.values()) {
            account.remove(currencyId);
        }
    }

    public synchronized void ensureJoinBonus(UUID playerId) {
        if (!joinBonusGranted.add(playerId)) return;
        addBalance(playerId, getGlobalCurrencyId(), config.getGlobalStartingAmount());
    }

    public synchronized Map<UUID, Double> getBalancesForCurrency(String currencyId) {
        Map<UUID, Double> out = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Double>> e : balances.entrySet()) {
            double v = e.getValue().getOrDefault(currencyId, 0.0);
            if (v != 0.0) out.put(e.getKey(), v);
        }
        return out;
    }

    public synchronized Map<UUID, Double> getVillageMemberBalances(Village village) {
        String currencyId = getVillageCurrencyId(village);
        Map<UUID, Double> out = new LinkedHashMap<>();
        for (UUID id : village.getMembers().keySet()) {
            out.put(id, getBalance(id, currencyId));
        }
        return out;
    }

    public synchronized void save() {
        data.set("joinBonusGranted", joinBonusGranted.stream().map(UUID::toString).toList());
        data.set("balances", null);
        for (Map.Entry<UUID, Map<String, Double>> e : balances.entrySet()) {
            String base = "balances." + e.getKey();
            for (Map.Entry<String, Double> c : e.getValue().entrySet()) {
                data.set(base + "." + c.getKey(), c.getValue());
            }
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte currency-balances.yml nicht speichern: " + e.getMessage());
        }
    }

    private void load() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
        }
        data = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);
        List<String> granted = data.getStringList("joinBonusGranted");
        for (String g : granted) {
            try { joinBonusGranted.add(UUID.fromString(g)); } catch (IllegalArgumentException ignored) {}
        }
        org.bukkit.configuration.ConfigurationSection sec = data.getConfigurationSection("balances");
        if (sec == null) return;
        for (String playerKey : sec.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(playerKey);
            } catch (IllegalArgumentException e) {
                continue;
            }
            org.bukkit.configuration.ConfigurationSection psec = sec.getConfigurationSection(playerKey);
            if (psec == null) continue;
            Map<String, Double> account = new ConcurrentHashMap<>();
            for (String currencyId : psec.getKeys(false)) {
                account.put(currencyId, psec.getDouble(currencyId, 0.0));
            }
            balances.put(id, account);
        }
    }
}
