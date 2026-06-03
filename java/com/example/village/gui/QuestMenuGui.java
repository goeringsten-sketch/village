package com.example.village.gui;

import com.example.village.model.Quest;
import com.example.village.service.QuestManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Quest-Menü GUI.
 */
public class QuestMenuGui {
    private final Player player;
    private final Collection<Quest> quests;

    public QuestMenuGui(Player player, Collection<Quest> quests) {
        this.player = player;
        this.quests = quests;
    }

    public void open() {
        int questCount = quests.size();
        int rows = (int) Math.ceil(questCount / 9.0) + 1;
        int invSize = Math.min(rows * 9, 54);

        Inventory inv = Bukkit.createInventory(null, invSize, "§b§lQuests");

        int slot = 0;
        for (Quest quest : quests) {
            if (slot >= invSize) break;
            inv.setItem(slot, createQuestItem(quest));
            slot++;
        }

        player.openInventory(inv);
    }

    private ItemStack createQuestItem(Quest quest) {
        Material material = switch (quest.getPriority()) {
            case 1, 2 -> Material.RED_CONCRETE;
            case 3 -> Material.GOLD_BLOCK;
            case 4, 5 -> Material.LIME_CONCRETE;
            default -> Material.WHITE_CONCRETE;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + quest.getTitle());
            List<String> lore = new ArrayList<>();
            lore.add("§7" + quest.getDescription());
            lore.add("");
            lore.add("§e§lZiel: §7" + quest.getObjective());
            lore.add("");
            lore.add("§a§lBelohnungen:");
            lore.add("§7  + " + quest.getRewardVillagePoints() + " Dorfpunkte");
            if (quest.getRewardMoney() > 0) {
                lore.add("§7  + " + String.format("%.0f", quest.getRewardMoney()) + " Geld");
            }
            if (quest.getRewardVillagerXp() > 0) {
                lore.add("§7  + " + quest.getRewardVillagerXp() + " NPC-XP");
            }
            if (!quest.getRewardItems().isEmpty()) {
                lore.add("§7  + Items");
            }
            lore.add("");
            if (quest.isExpired()) {
                lore.add("§c§lABGELAUFEN");
            } else {
                lore.add("§aKlick: Annehmen");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
