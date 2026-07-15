package com.example.village.gui;

import com.example.village.VillagePlugin;
import com.example.village.model.Quest;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Quest-Menü GUI mit Annehmen- und Abschließen-Aktionen.
 */
public class QuestMenuGui {
    public static final String QUEST_ID_KEY = "quest-id";
    public static final String QUEST_ACTION_KEY = "quest-action";

    private final Player player;
    private final Collection<Quest> availableQuests;
    private final Collection<Quest> activeQuests;
    private final VillagePlugin plugin;

    public QuestMenuGui(Player player, Collection<Quest> availableQuests,
                        Collection<Quest> activeQuests, VillagePlugin plugin) {
        this.player = player;
        this.availableQuests = availableQuests;
        this.activeQuests = activeQuests;
        this.plugin = plugin;
    }

    public static boolean isQuestMenuTitle(String title) {
        String expected = MessageUtil.text("ui.quests.title", "§b§lQuests");
        return title != null && title.equals(expected);
    }

    public static String getQuestId(ItemStack item, VillagePlugin plugin) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(
                questIdKey(plugin), PersistentDataType.STRING);
    }

    public static String getQuestAction(ItemStack item, VillagePlugin plugin) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(
                questActionKey(plugin), PersistentDataType.STRING);
    }

    private static NamespacedKey questIdKey(VillagePlugin plugin) {
        return new NamespacedKey(plugin, QUEST_ID_KEY);
    }

    private static NamespacedKey questActionKey(VillagePlugin plugin) {
        return new NamespacedKey(plugin, QUEST_ACTION_KEY);
    }

    public void open() {
        int total = availableQuests.size() + activeQuests.size();
        int rows = Math.max(2, (int) Math.ceil(Math.max(total, 1) / 9.0) + 1);
        int invSize = Math.min(rows * 9, 54);

        Inventory inv = Bukkit.createInventory(null, invSize,
                MessageUtil.text("ui.quests.title", "§b§lQuests"));

        int slot = 0;
        for (Quest quest : availableQuests) {
            if (slot >= invSize) break;
            inv.setItem(slot++, createQuestItem(quest, "accept"));
        }
        for (Quest quest : activeQuests) {
            if (slot >= invSize) break;
            inv.setItem(slot++, createQuestItem(quest, "complete"));
        }

        player.openInventory(inv);
    }

    private ItemStack createQuestItem(Quest quest, String action) {
        Material material = switch (quest.getPriority()) {
            case 1, 2 -> Material.RED_CONCRETE;
            case 3 -> Material.GOLD_BLOCK;
            case 4, 5 -> Material.LIME_CONCRETE;
            default -> Material.WHITE_CONCRETE;
        };
        if ("complete".equals(action)) {
            material = Material.EMERALD_BLOCK;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.quests.title-prefix", "§e") + quest.getTitle());
            List<String> lore = new ArrayList<>();
            lore.add(MessageUtil.text("ui.quests.description-prefix", "§7") + quest.getDescription());
            lore.add("");
            lore.add(MessageUtil.text("ui.quests.objective", "§e§lZiel: §7") + quest.getObjective());
            lore.add("");
            lore.add(MessageUtil.text("ui.quests.rewards", "§a§lBelohnungen:"));
            lore.add(MessageUtil.text("ui.quests.points", "§7  + ") + quest.getRewardVillagePoints()
                    + MessageUtil.text("ui.quests.points-suffix", " Dorfpunkte"));
            if (quest.getRewardMoney() > 0) {
                lore.add(MessageUtil.text("ui.quests.money", "§7  + ")
                        + String.format("%.0f", quest.getRewardMoney())
                        + MessageUtil.text("ui.quests.money-suffix", " Geld"));
            }
            if (quest.getRewardVillagerXp() > 0) {
                lore.add(MessageUtil.text("ui.quests.xp", "§7  + ") + quest.getRewardVillagerXp()
                        + MessageUtil.text("ui.quests.xp-suffix", " NPC-XP"));
            }
            if (!quest.getRewardItems().isEmpty()) {
                lore.add(MessageUtil.text("ui.quests.items", "§7  + Items"));
            }
            lore.add("");
            if (quest.isExpired()) {
                lore.add(MessageUtil.text("ui.quests.expired", "§c§lABGELAUFEN"));
            } else if ("complete".equals(action)) {
                lore.add(MessageUtil.text("ui.quests.complete", "§aKlick: Abschließen"));
            } else {
                lore.add(MessageUtil.text("ui.quests.accept", "§aKlick: Annehmen"));
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(questIdKey(plugin), PersistentDataType.STRING, quest.getQuestId());
            meta.getPersistentDataContainer().set(questActionKey(plugin), PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        return item;
    }
}
