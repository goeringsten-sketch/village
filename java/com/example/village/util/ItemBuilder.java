package com.example.village.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        meta.displayName(MessageUtil.color(name));
        return this;
    }

    public ItemBuilder name(Component name) {
        meta.displayName(name);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(MessageUtil.color(line));
        }
        meta.lore(lore);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(MessageUtil.color(line));
        }
        meta.lore(lore);
        return this;
    }

    public ItemBuilder addLore(String line) {
        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        lore.add(MessageUtil.color(line));
        meta.lore(lore);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (glow) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
