package com.example.village.gui;

import com.example.village.model.CustomVillager;
import com.example.village.model.VillagerJob;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * GUI zur Auswahl eines neuen Jobs für einen Villager.
 */
public class JobSelectionGui {

    public static final String TITLE_PREFIX = "§6Job wählen: ";

    /** Slot → VillagerJob Mapping, um Klicks auszuwerten */
    private static final Map<Integer, VillagerJob> JOB_SLOTS = new HashMap<>();

    static {
        VillagerJob[] jobs = VillagerJob.values();
        for (int i = 0; i < jobs.length; i++) {
            JOB_SLOTS.put(i + 10, jobs[i]); // Slots 10+ (mittige Zeile)
        }
    }

    private final CustomVillager villager;
    private final Player player;

    public JobSelectionGui(CustomVillager villager, Player player) {
        this.villager = villager;
        this.player = player;
    }

    public void open() {
        int rows = 3;
        Inventory inv = Bukkit.createInventory(null, rows * 9, TITLE_PREFIX + villager.getName());

        // Füge Glasscheiben als Rahmen ein
        ItemStack filler = createFiller();
        for (int i = 0; i < rows * 9; i++) {
            inv.setItem(i, filler);
        }

        // Jobs in der mittleren Reihe platzieren
        for (Map.Entry<Integer, VillagerJob> entry : JOB_SLOTS.entrySet()) {
            inv.setItem(entry.getKey(), createJobItem(entry.getValue()));
        }

        // Zurück-Button
        inv.setItem(0, createBackItem());

        player.openInventory(inv);
    }

    private ItemStack createJobItem(VillagerJob job) {
        Material icon = getIconForJob(job);
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(job.getDisplayName());
            boolean isCurrent = job == villager.getJob();
            meta.setLore(Arrays.asList(
                    isCurrent ? "§a● Aktueller Job" : "§7Klicken zum Zuweisen",
                    "§8Gebäude: §7" + job.getWorkBuildingKey(),
                    "§8Skills: §7" + String.join(", ", job.getSkillTrees())
            ));
            if (isCurrent) {
                meta.addEnchant(
                    org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§r");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBackItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§cZurück");
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material getIconForJob(VillagerJob job) {
        return switch (job) {
            case FARMER -> Material.WHEAT;
            case MERCHANT -> Material.GOLD_INGOT;
            case GUARD -> Material.IRON_SWORD;
            case LIBRARIAN -> Material.BOOK;
            case MILLER -> Material.STONE_BRICKS;
            case PRIEST -> Material.NETHER_STAR;
            case LABORER -> Material.OAK_LOG;
        };
    }

    /**
     * Gibt den Job zurück, der sich in einem bestimmten Slot befindet (null = kein Job-Slot).
     */
    public static VillagerJob getJobForSlot(int slot) {
        return JOB_SLOTS.get(slot);
    }

    public static boolean isJobSlot(int slot) {
        return JOB_SLOTS.containsKey(slot);
    }

    public static boolean isBackSlot(int slot) {
        return slot == 0;
    }
}
