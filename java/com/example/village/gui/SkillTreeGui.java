package com.example.village.gui;

import com.example.village.model.CustomVillager;
import com.example.village.model.VillagerSkill;
import com.example.village.service.SkillTreeManager;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill-Baum GUI für Villager-Progression.
 */
public class SkillTreeGui {
    private final CustomVillager villager;
    private final Player player;
    private final SkillTreeManager skillTreeManager;
    private static final int ROWS = 5;

    public SkillTreeGui(CustomVillager villager, Player player, SkillTreeManager skillTreeManager) {
        this.villager = villager;
        this.player = player;
        this.skillTreeManager = skillTreeManager;
    }

    public void open() {
        Inventory inv = Bukkit.createInventory(null, ROWS * 9,
                MessageUtil.text("ui.skill-tree.title", "§b") + villager.getName() + MessageUtil.text("ui.skill-tree.suffix", " - Skills"));

        // Villager-Stats oben
        inv.setItem(0, createLevelItem());
        inv.setItem(1, createXpItem());
        inv.setItem(2, createMoneyItem());

        // Trenner
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, createSeparator());
        }

        // Skills anzeigen
        int slot = 18;
        for (VillagerSkill skill : villager.getSkills().values()) {
            if (slot >= ROWS * 9) break;
            inv.setItem(slot, createSkillItem(skill));
            slot++;
        }

        player.openInventory(inv);
    }

    private ItemStack createLevelItem() {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lLevel " + villager.getLevel());
            meta.setLore(List.of(
                MessageUtil.text("ui.skill-tree.xp", "§7XP: ") + String.format("%.0f", villager.getXp())
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createXpItem() {
        ItemStack item = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.skill-tree.experience", "§eErfahrung"));
            meta.setLore(List.of(
                MessageUtil.text("ui.skill-tree.total", "§7Gesamt: ") + String.format("%.0f", villager.getXp())
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMoneyItem() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.skill-tree.money", "§6Geld"));
            meta.setLore(List.of(
                MessageUtil.text("ui.skill-tree.budget", "§7Budget: ") + String.format("%.0f", villager.getWallet())
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSeparator() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.generic.empty", " "));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSkillItem(VillagerSkill skill) {
        Material material = switch (skill.getSkillName()) {
            case "Anbau", "Ernte" -> Material.WHEAT;
            case "Effizienz" -> Material.COMPARATOR;
            case "Verhandlung", "Preise" -> Material.EMERALD;
            case "Bestand" -> Material.BARREL;
            case "Kampf", "Reichweite", "Reflexe" -> Material.IRON_SWORD;
            case "Wissen", "Forschung" -> Material.ENCHANTING_TABLE;
            case "Speicherung" -> Material.CHEST;
            default -> Material.BOOK;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.skill-tree.skill-prefix", "§6") + skill.getSkillName() + MessageUtil.text("ui.skill-tree.skill-lvl", " §7Lv.") + skill.getLevel());
            List<String> lore = new ArrayList<>();
            lore.add(MessageUtil.text("ui.skill-tree.description-prefix", "§7") + skillTreeManager.getSkillDescription(skill.getSkillName()));
            lore.add("");
            lore.add(MessageUtil.text("ui.skill-tree.progress", "§eProgress: ") + String.format("%.1f", skill.getXpProgress() * 100) + "%");
            lore.add(MessageUtil.text("ui.skill-tree.xp-to-level", "§eXP bis Level: ") + skill.getXpToNextLevel());
            lore.add("");
            lore.add(MessageUtil.text("ui.skill-tree.upgrade-hint", "§aRechtsklick: Upgrade"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
