package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.hook.VaultHook;
import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

public final class EconomyService {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VaultHook vaultHook;

    public EconomyService(VillagePlugin plugin, VillageConfigManager configManager,
                          VaultHook vaultHook) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.vaultHook = vaultHook;
    }

    public boolean isAvailable() {
        return vaultHook.isAvailable();
    }

    public double getBalance(Player player) {
        return vaultHook.getBalance(player);
    }

    public boolean canAfford(Player player, double amount) {
        return vaultHook.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        return vaultHook.withdraw(player, amount);
    }

    public boolean deposit(Player player, double amount) {
        return vaultHook.deposit(player, amount);
    }

    public String format(double amount) {
        return vaultHook.format(amount);
    }

    /**
     * Calculates the tax revenue for a village based on the tax upgrade level.
     */
    public double calculateTaxRevenue(Village village) {
        int taxLevel = village.getUpgradeLevel("taxes");
        if (taxLevel <= 0) return 0;

        double taxRate = taxLevel * configManager.getUpgradeTaxRatePerLevel();
        // Tax is based on number of members and villager productivity
        int memberCount = village.getMembers().size();
        return memberCount * taxRate * 100;
    }

    /**
     * Handles trading between a player and a custom villager.
     * Returns true if the trade was successful.
     */
    public boolean tradeWithVillager(Player player, CustomVillager villager,
                                      Material buyMaterial, int buyAmount, double price) {
        if (!isAvailable()) return false;

        int available = villager.getInventory().getOrDefault(buyMaterial, 0);
        if (available < buyAmount) return false;

        if (!canAfford(player, price)) return false;

        if (!withdraw(player, price)) return false;
        villager.removeItem(buyMaterial, buyAmount);
        villager.setWallet(villager.getWallet() + price);

        // Give items to player
        org.bukkit.inventory.ItemStack items = new org.bukkit.inventory.ItemStack(buyMaterial, buyAmount);
        player.getInventory().addItem(items);

        return true;
    }

    /**
     * Handles inter-villager trading within a village.
     */
    public boolean villagerTrade(CustomVillager seller, CustomVillager buyer,
                                  Material material, int amount, double price) {
        int available = seller.getInventory().getOrDefault(material, 0);
        if (available < amount) return false;

        if (buyer.getWallet() < price) return false;

        seller.removeItem(material, amount);
        seller.setWallet(seller.getWallet() + price);

        buyer.addItem(material, amount);
        buyer.setWallet(buyer.getWallet() - price);

        return true;
    }
}
