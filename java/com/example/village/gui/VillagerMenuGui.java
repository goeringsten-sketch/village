package com.example.village.gui;

import com.example.village.model.CustomVillager;
import com.example.village.model.VillagerRelation;
import com.example.village.model.VillagerState;
import com.example.village.service.SkillTreeManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * GUI für Villager-Interaktionen (Linksklick).
 */
public class VillagerMenuGui {
    private final CustomVillager villager;
    private final Player player;
    private final SkillTreeManager skillTreeManager;

    public VillagerMenuGui(CustomVillager villager, Player player, SkillTreeManager skillTreeManager) {
        this.villager = villager;
        this.player = player;
        this.skillTreeManager = skillTreeManager;
    }

    /**
     * Öffnet das Villager-Hauptmenü
     */
    public void open() {
        Inventory inv = Bukkit.createInventory(null, 45, "§b" + villager.getName() + " - Menü");

        // Info-Items (erste Reihe)
        inv.setItem(0, createInfoItem());
        inv.setItem(1, createStatusItem());
        inv.setItem(2, createRelationItem());
        inv.setItem(3, createJobItem());

        // Aktions-Items
        inv.setItem(9, createRenameItem());
        inv.setItem(10, createReassignJobItem());
        inv.setItem(11, createMoveItem());
        inv.setItem(12, createViewInventoryItem());

        inv.setItem(18, createSkillTreeItem());
        inv.setItem(19, createQuestItem());
        inv.setItem(20, createTradeItem());
        inv.setItem(21, createTransferItem());

        inv.setItem(27, createHealItem());
        inv.setItem(28, createBanishItem());

        player.openInventory(inv);
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + villager.getName());
            meta.setLore(Arrays.asList(
                "§7Job: " + villager.getJob().getDisplayName(),
                "§7Level: " + villager.getLevel(),
                "§7Staat: " + villager.getCurrentState().getDisplayName()
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createStatusItem() {
        ItemStack item = new ItemStack(Material.APPLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§cStatus anzeigen");
            meta.setLore(Arrays.asList(
                "§7Hunger: " + String.format("%.0f", villager.getNeedValue(com.example.village.model.VillagerNeed.HUNGER)) + "%",
                "§7Zufriedenheit: " + String.format("%.0f", villager.getNeedValue(com.example.village.model.VillagerNeed.HAPPINESS)) + "%",
                "§7Moral: " + String.format("%.0f", villager.getMorale()) + "%"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRelationItem() {
        ItemStack item = new ItemStack(Material.COPPER_BLOCK);
        ItemMeta meta = item.getItemMeta();
        VillagerRelation relation = villager.getRelation(player.getUniqueId());
        if (meta != null) {
            meta.setDisplayName("§6Beziehung");
            meta.setLore(Arrays.asList(
                "§7Status: " + relation.getRelationshipStatus(),
                "§7Reputation: " + relation.getReputation() + "/100",
                "§7Vertrauen: " + relation.getTrustLevel() + "/10",
                "§7Quests: " + relation.getQuestsCompleted()
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createJobItem() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§eJob: " + villager.getJob().getDisplayName());
            meta.setLore(Arrays.asList(
                "§7Arbeitsgebäude: " + (villager.getAssignedJobBuildingId() != null ? "Ja" : "Nein"),
                "§7Geld: " + String.format("%.0f", villager.getWallet())
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRenameItem() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aUmbenennen");
            meta.setLore(Arrays.asList(
                "§7Klick: Geben Sie einen neuen Namen ein"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createReassignJobItem() {
        ItemStack item = new ItemStack(Material.LECTERN);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aJob ändern");
            meta.setLore(Arrays.asList(
                "§7Aktueller Job: " + villager.getJob().getDisplayName(),
                "§7Klick: Anderen Job wählen"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMoveItem() {
        ItemStack item = new ItemStack(Material.RED_BED);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aUmziehen");
            meta.setLore(Arrays.asList(
                "§7Klick: Freies Bett wählen"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createViewInventoryItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aInventar anzeigen");
            meta.setLore(Arrays.asList(
                "§7Items: " + villager.getInventory().size()
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSkillTreeItem() {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lSkill-Baum");
            meta.setLore(Arrays.asList(
                "§7Level: " + villager.getLevel(),
                "§7XP: " + String.format("%.0f", villager.getXp()),
                "§7Klick: Skill-Baum öffnen"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createQuestItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§lQuest");
            meta.setLore(Arrays.asList(
                "§7Klick: Quest-Dialog starten"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTradeItem() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lHandeln");
            meta.setLore(Arrays.asList(
                "§7Klick: Mit NPC handeln"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTransferItem() {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5Transfer");
            meta.setLore(Arrays.asList(
                "§7Klick: In anderes Dorf übertragen"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createHealItem() {
        ItemStack item = new ItemStack(Material.POTION);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§lHeilen");
            meta.setLore(Arrays.asList(
                "§7Klick: Villager heilen"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBanishItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lVerjagen");
            meta.setLore(Arrays.asList(
                "§cKlick: Villager permanent löschen"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }
}
