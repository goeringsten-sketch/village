package com.example.village.gui;

import com.example.village.model.CustomVillager;
import com.example.village.model.VillagerJob;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI zur Auswahl eines neuen Jobs für einen Villager (mit Pagination).
 */
public class JobSelectionGui {

    public static final String TITLE_PREFIX = "§6Job wählen: ";
    private static final int JOBS_PER_PAGE = 21;
    private static final int[] JOB_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final Map<UUID, PageContext> PAGE_CONTEXT = new HashMap<>();

    private final CustomVillager villager;
    private final Player player;

    public JobSelectionGui(CustomVillager villager, Player player) {
        this.villager = villager;
        this.player = player;
    }

    public void open() {
        open(0);
    }

    public void open(int page) {
        List<VillagerJob> jobs = new ArrayList<>(VillagerJob.selectableJobs());
        int totalPages = Math.max(1, (int) Math.ceil(jobs.size() / (double) JOBS_PER_PAGE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        PAGE_CONTEXT.put(player.getUniqueId(), new PageContext(villager.getId(), safePage, totalPages));

        String title = TITLE_PREFIX + villager.getName();
        if (totalPages > 1) {
            title += " §7(" + (safePage + 1) + "/" + totalPages + ")";
        }

        Inventory inv = Bukkit.createInventory(null, 54, title);

        ItemStack filler = createFiller();
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        int start = safePage * JOBS_PER_PAGE;
        for (int i = 0; i < JOB_SLOTS.length; i++) {
            int jobIndex = start + i;
            if (jobIndex >= jobs.size()) {
                break;
            }
            inv.setItem(JOB_SLOTS[i], createJobItem(jobs.get(jobIndex)));
        }

        if (safePage > 0) {
            inv.setItem(45, createNavItem(Material.ARROW, MessageUtil.text("ui.generic.prev-page", "§eVorherige Seite")));
        }
        if (safePage < totalPages - 1) {
            inv.setItem(53, createNavItem(Material.ARROW, MessageUtil.text("ui.generic.next-page", "§eNächste Seite")));
        }

        inv.setItem(49, createBackItem());
        player.openInventory(inv);
    }

    private ItemStack createJobItem(VillagerJob job) {
        Material icon = job.getIcon() != null ? job.getIcon() : Material.PAPER;
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(job.getDisplayName());
            boolean isCurrent = job == villager.getJob();
            List<String> lore = new ArrayList<>();
            lore.add(isCurrent
                    ? MessageUtil.text("ui.job-selection.current", "§a● Aktueller Job")
                    : MessageUtil.text("ui.job-selection.assign", "§7Klicken zum Zuweisen"));
            lore.add(MessageUtil.text("ui.job-selection.profession", "§8Profession: §7")
                    + job.getProfessionKey());
            lore.add(MessageUtil.text("ui.job-selection.building", "§8Gebäude: §7")
                    + job.getWorkBuildingKey());
            lore.add(MessageUtil.text("ui.job-selection.skills", "§8Skills: §7")
                    + String.join(", ", job.getSkillTrees()));
            meta.setLore(lore);
            if (isCurrent) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
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
            meta.setDisplayName(MessageUtil.text("ui.generic.empty", "§r"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBackItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.generic.back", "§cZurück"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNavItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static VillagerJob getJobForSlot(Player player, int slot) {
        PageContext ctx = PAGE_CONTEXT.get(player.getUniqueId());
        if (ctx == null) {
            return null;
        }
        int slotIndex = -1;
        for (int i = 0; i < JOB_SLOTS.length; i++) {
            if (JOB_SLOTS[i] == slot) {
                slotIndex = i;
                break;
            }
        }
        if (slotIndex < 0) {
            return null;
        }
        List<VillagerJob> jobs = new ArrayList<>(VillagerJob.selectableJobs());
        int jobIndex = ctx.page * JOBS_PER_PAGE + slotIndex;
        if (jobIndex >= jobs.size()) {
            return null;
        }
        return jobs.get(jobIndex);
    }

    public static boolean isJobSlot(int slot) {
        for (int jobSlot : JOB_SLOTS) {
            if (jobSlot == slot) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBackSlot(int slot) {
        return slot == 49;
    }

    public static boolean isPrevPageSlot(int slot) {
        return slot == 45;
    }

    public static boolean isNextPageSlot(int slot) {
        return slot == 53;
    }

    public static void clearContext(Player player) {
        PAGE_CONTEXT.remove(player.getUniqueId());
    }

    public static PageContext getContext(Player player) {
        return PAGE_CONTEXT.get(player.getUniqueId());
    }

    public record PageContext(UUID villagerId, int page, int totalPages) {}
}
