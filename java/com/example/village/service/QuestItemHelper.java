package com.example.village.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Hilfsmethoden für Item-Zählung und -Abzug bei Sammelquests.
 */
public final class QuestItemHelper {

    private QuestItemHelper() {}

    public static int countMaterial(PlayerInventory inventory, Material material) {
        if (inventory == null || material == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            count += amountOf(item, material);
        }
        for (ItemStack item : inventory.getArmorContents()) {
            count += amountOf(item, material);
        }
        count += amountOf(inventory.getItemInOffHand(), material);
        return count;
    }

    public static boolean removeMaterial(PlayerInventory inventory, Material material, int amount) {
        if (inventory == null || material == null || amount <= 0) {
            return true;
        }
        int remaining = amount;

        ItemStack[] storage = inventory.getStorageContents();
        remaining = removeFromArray(storage, material, remaining);
        inventory.setStorageContents(storage);
        if (remaining <= 0) {
            return true;
        }

        ItemStack[] armor = inventory.getArmorContents();
        remaining = removeFromArray(armor, material, remaining);
        inventory.setArmorContents(armor);
        if (remaining <= 0) {
            return true;
        }

        ItemStack offhand = inventory.getItemInOffHand();
        if (offhand != null && offhand.getType() == material) {
            if (offhand.getAmount() > remaining) {
                offhand.setAmount(offhand.getAmount() - remaining);
                return true;
            }
            remaining -= offhand.getAmount();
            inventory.setItemInOffHand(null);
        }
        return remaining <= 0;
    }

    private static int removeFromArray(ItemStack[] contents, Material material, int remaining) {
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) {
                continue;
            }
            if (item.getAmount() > remaining) {
                item.setAmount(item.getAmount() - remaining);
                return 0;
            }
            remaining -= item.getAmount();
            contents[i] = null;
            if (remaining <= 0) {
                return 0;
            }
        }
        return remaining;
    }

    private static int amountOf(ItemStack item, Material material) {
        if (item == null || item.getType() != material) {
            return 0;
        }
        return item.getAmount();
    }
}
