package com.example.village.gui;

import com.example.village.model.CustomVillager;
import com.example.village.model.VillagerRelation;
import com.example.village.model.VillagerState;
import com.example.village.service.SkillTreeManager;
import com.example.village.service.VillagerNutritionService;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GUI für Villager-Interaktionen (Linksklick).
 */
public class VillagerMenuGui {
    private final CustomVillager villager;
    private final Player player;
    private final SkillTreeManager skillTreeManager;
    private final VillagerNutritionService nutritionService;

    public VillagerMenuGui(CustomVillager villager, Player player, SkillTreeManager skillTreeManager,
                           VillagerNutritionService nutritionService) {
        this.villager = villager;
        this.player = player;
        this.skillTreeManager = skillTreeManager;
        this.nutritionService = nutritionService;
    }

    /**
     * Öffnet das Villager-Hauptmenü
     */
    public void open() {
        Inventory inv = Bukkit.createInventory(null, 54,
                MessageUtil.text("ui.villager-menu.title-prefix", "§b") + villager.getName()
                        + MessageUtil.text("ui.villager-menu.title-suffix", " - Menü"));

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
            meta.setDisplayName(MessageUtil.text("ui.villager-menu.name-prefix", "§e") + villager.getName());
            meta.setLore(Arrays.asList(
                MessageUtil.text("ui.villager-menu.job", "§7Job: ") + villager.getJob().getDisplayName(),
                MessageUtil.text("ui.villager-menu.level", "§7Level: ") + villager.getLevel(),
                MessageUtil.text("ui.villager-menu.state", "§7Staat: ") + villager.getCurrentState().getDisplayName()
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createStatusItem() {
        ItemStack item = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(MessageUtil.text("ui.villager-menu.hunger", "§7Hunger: ") + String.format("%.0f", villager.getNeedValue(com.example.village.model.VillagerNeed.HUNGER)) + "%");
            lore.add(MessageUtil.text("ui.villager-menu.happiness", "§7Zufriedenheit: ") + String.format("%.0f", villager.getNeedValue(com.example.village.model.VillagerNeed.HAPPINESS)) + "%");
            lore.add(MessageUtil.text("ui.villager-menu.morale", "§7Moral: ") + String.format("%.0f", villager.getMorale()) + "%");
            lore.add(MessageUtil.text("ui.generic.empty", " "));
            lore.add(MessageUtil.text("ui.villager-menu.nutrients", "§6Nährstoff-Status"));
            lore.addAll(buildNutrientLines());
            meta.setDisplayName("§cStatus anzeigen");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> buildNutrientLines() {
        if (villager.getNutrientLevels().isEmpty()) {
            return Arrays.asList(MessageUtil.text("ui.villager-menu.no-nutrients", "§7Keine Nährstoffdaten aktiv."));
        }

        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Double> entry : villager.getNutrientLevels().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            double capacity = villager.getNutrientCapacity(entry.getKey());
            double percent = capacity > 0 ? (entry.getValue() / capacity) * 100.0 : 0.0;
            String label = nutritionService != null
                    ? nutritionService.getNutrientDisplayName(key)
                    : formatNutrientLabel(key);
            String bar = nutritionService != null
                    ? nutritionService.formatNutrientBar(percent)
                    : "§e" + String.format("%.0f", percent) + "%";
        lines.add(MessageUtil.text("ui.villager-menu.nutrient-line-prefix", "§8■ §7") + label + MessageUtil.text("ui.villager-menu.nutrient-line-middle", ": ") + bar);
        }

        double hunger = villager.getNeedValue(com.example.village.model.VillagerNeed.HUNGER);
        String mood = hunger >= 75 ? "§afrisch" : hunger >= 40 ? "§eaktiv" : "§cbedarfsgesteuert";
        lines.add(MessageUtil.text("ui.generic.empty", " "));
        lines.add(MessageUtil.text("ui.villager-menu.overall", "§7Gesamtzustand: ") + mood);
        return lines;
    }

    private String formatNutrientLabel(String key) {
            return switch (key) {
            case "protein" -> MessageUtil.text("ui.villager-menu.nutrient-protein", "Protein");
            case "fett" -> MessageUtil.text("ui.villager-menu.nutrient-fat", "Fett");
            case "kohlenhydrate" -> MessageUtil.text("ui.villager-menu.nutrient-carbs", "Kohlenhydrate");
            case "vitamine" -> MessageUtil.text("ui.villager-menu.nutrient-vitamins", "Vitamine");
            case "mineralien" -> MessageUtil.text("ui.villager-menu.nutrient-minerals", "Mineralien");
            default -> key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
        };
    }

    private ItemStack createRelationItem() {
        ItemStack item = new ItemStack(Material.COPPER_BLOCK);
        ItemMeta meta = item.getItemMeta();
        VillagerRelation relation = villager.getRelation(player.getUniqueId());
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.villager-menu.relation", "§6Beziehung"));
            meta.setLore(Arrays.asList(
                MessageUtil.text("ui.villager-menu.relation-status", "§7Status: ") + relation.getRelationshipStatus(),
                MessageUtil.text("ui.villager-menu.reputation", "§7Reputation: ") + relation.getReputation() + "/100",
                MessageUtil.text("ui.villager-menu.trust", "§7Vertrauen: ") + relation.getTrustLevel() + "/10",
                MessageUtil.text("ui.villager-menu.quests", "§7Quests: ") + relation.getQuestsCompleted()
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createJobItem() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.villager-menu.job-prefix", "§eJob: ") + villager.getJob().getDisplayName());
            meta.setLore(Arrays.asList(
                MessageUtil.text("ui.villager-menu.workbuilding", "§7Arbeitsgebäude: ") + (villager.getAssignedJobBuildingId() != null ? MessageUtil.text("ui.generic.yes", "Ja") : MessageUtil.text("ui.generic.no", "Nein")),
                MessageUtil.text("ui.villager-menu.wallet", "§7Geld: ") + String.format("%.0f", villager.getWallet())
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRenameItem() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.villager-menu.rename", "§aUmbenennen"));
            meta.setLore(Arrays.asList(
                MessageUtil.text("ui.villager-menu.rename-hint", "§7Klick: Geben Sie einen neuen Namen ein")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createReassignJobItem() {
        ItemStack item = new ItemStack(Material.LECTERN);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.villager-menu.change-job", "§aJob ändern"));
            meta.setLore(Arrays.asList(
                MessageUtil.text("ui.villager-menu.current-job", "§7Aktueller Job: ") + villager.getJob().getDisplayName(),
                MessageUtil.text("ui.villager-menu.choose-job", "§7Klick: Anderen Job wählen")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMoveItem() {
        ItemStack item = new ItemStack(Material.RED_BED);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.villager-menu.move", "§aUmziehen"));
            meta.setLore(Arrays.asList(
                MessageUtil.text("ui.villager-menu.move-hint", "§7Klick: Freies Bett wählen")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createViewInventoryItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.villager-menu.inventory", "§aInventar anzeigen"));
            meta.setLore(Arrays.asList(
                MessageUtil.text("ui.villager-menu.items", "§7Items: ") + villager.getInventory().size()
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSkillTreeItem() {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.villager-menu.skill-tree", "§c§lSkill-Baum"));
            meta.setLore(Arrays.asList(
                MessageUtil.text("ui.villager-menu.level", "§7Level: ") + villager.getLevel(),
                MessageUtil.text("ui.villager-menu.xp", "§7XP: ") + String.format("%.0f", villager.getXp()),
                MessageUtil.text("ui.villager-menu.skill-tree-hint", "§7Klick: Skill-Baum öffnen")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createQuestItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.villager-menu.quest", "§d§lQuest"));
            meta.setLore(Arrays.asList(
                MessageUtil.text("ui.villager-menu.quest-hint", "§7Klick: Quest-Dialog starten")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTradeItem() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.villager-menu.trade", "§6§lHandeln"));
            meta.setLore(Arrays.asList(
                MessageUtil.text("ui.villager-menu.trade-hint", "§7Klick: Mit NPC handeln")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTransferItem() {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.villager-menu.transfer", "§5Transfer"));
            meta.setLore(Arrays.asList(
                MessageUtil.text("ui.villager-menu.transfer-hint", "§7Klick: In anderes Dorf übertragen")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createHealItem() {
        ItemStack item = new ItemStack(Material.POTION);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.villager-menu.heal", "§a§lHeilen"));
            meta.setLore(Arrays.asList(
                MessageUtil.text("ui.villager-menu.heal-hint", "§7Klick: Villager heilen")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBanishItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.villager-menu.banish", "§c§lVerjagen"));
            meta.setLore(Arrays.asList(
                MessageUtil.text("ui.villager-menu.banish-hint", "§cKlick: Villager permanent löschen")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }
}
