package com.example.village.gui;

import com.example.village.config.VillageConfigManager;
import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.model.VillagerContract;
import com.example.village.model.VillagerJob;
import com.example.village.util.ItemBuilder;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * "Job-Übersicht" aus dem Ausbauplan: zeigt aktuellen Beruf, Level/XP, Produktionsstatus,
 * aktuelle Arbeitsstätte und Anzahl offener Aufträge, und verlinkt auf die jeweiligen
 * Untermenüs (Produktion, Aufträge, Lager, Arbeitsstätte).
 */
public final class VillagerJobDetailsGui {

    private final VillageConfigManager configManager;
    private final Village village;
    private final CustomVillager villager;
    private final Player player;

    public VillagerJobDetailsGui(VillageConfigManager configManager, Village village, CustomVillager villager, Player player) {
        this.configManager = configManager;
        this.village = village;
        this.villager = villager;
        this.player = player;
    }

    public void open() {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.VILLAGER_JOB_DETAILS,
                village.getId().toString() + ":" + villager.getId().toString());
        Inventory inv = Bukkit.createInventory(holder, 54,
                MessageUtil.text("ui.villager-job-details.title", "&6Job-Details: ") + villager.getName());

        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler());
        }

        VillagerJob job = villager.getJob();
        String jobName = job != null ? job.getDisplayName() : MessageUtil.text("ui.villager-job-details.no-job", "&7Kein Job");
        String workBuilding = job != null ? job.getWorkBuildingKey() : "-";
        int storageSlots = configManager.getJobStorageCapacity(village, villager);
        int openContracts = countOpenContracts();
        String productionStatus = villager.isProductionPaused()
                ? MessageUtil.text("ui.villager-job-details.production-paused", "&cPausiert")
                : (villager.getPreferredProduct() != null
                        ? MessageUtil.text("ui.villager-job-details.production-target", "&aAktiv (Ziel: &f")
                                + villager.getPreferredProduct().name() + "&a)"
                        : MessageUtil.text("ui.villager-job-details.production-all", "&aAktiv (alle Produkte)"));

        inv.setItem(4, new ItemBuilder(job != null ? job.getIcon() : Material.VILLAGER_SPAWN_EGG)
                .name("&a" + villager.getName())
                .lore(
                        MessageUtil.text("ui.villager-job-details.job", "&7Beruf: &e") + jobName,
                        MessageUtil.text("ui.villager-job-details.level", "&7Level/XP: &e") + villager.getLevel()
                                + " &7/ &e" + String.format("%.1f", villager.getXp()),
                        MessageUtil.text("ui.villager-job-details.production", "&7Produktion: ") + productionStatus,
                        MessageUtil.text("ui.villager-job-details.building", "&7Arbeitsgebäude: &f") + workBuilding,
                        MessageUtil.text("ui.villager-job-details.contracts-open", "&7Offene/aktive Aufträge: &e") + openContracts,
                        MessageUtil.text("ui.villager-job-details.storage", "&7Job-Lager: &e") + storageSlots + " Slots",
                        MessageUtil.text("ui.villager-job-details.skills", "&7Skilltrees: &f")
                                + (job != null ? String.join(", ", job.getSkillTrees()) : "-")
                )
                .build());

        inv.setItem(19, new ItemBuilder(Material.CLOCK)
                .name(MessageUtil.text("ui.villager-job-details.production-button", "&6Produktion"))
                .lore(
                        MessageUtil.text("ui.villager-job-details.production-lore-1", "&7Produktion starten/pausieren"),
                        MessageUtil.text("ui.villager-job-details.production-lore-2", "&7und Zielprodukt auswählen.")
                )
                .build());

        inv.setItem(20, new ItemBuilder(Material.CHEST)
                .name(MessageUtil.text("ui.villager-job-details.inventory", "&aLager öffnen"))
                .lore(
                        MessageUtil.text("ui.villager-job-details.inventory-lore-1", "&7Öffnet das berufsbezogene Lager."),
                        MessageUtil.text("ui.villager-job-details.inventory-lore-2", "&7Kapazität: &e") + storageSlots + " Slots"
                )
                .build());

        inv.setItem(22, new ItemBuilder(Material.PAPER)
                .name(MessageUtil.text("ui.villager-job-details.contracts", "&eAufträge"))
                .lore(
                        MessageUtil.text("ui.villager-job-details.contracts-lore-1", "&7Aufträge ansehen, annehmen,"),
                        MessageUtil.text("ui.villager-job-details.contracts-lore-2", "&7abbrechen oder neu erstellen."),
                        MessageUtil.text("ui.villager-job-details.contracts-lore-3", "&7Offen/aktiv: &e") + openContracts
                )
                .build());

        inv.setItem(24, new ItemBuilder(Material.LECTERN)
                .name(MessageUtil.text("ui.villager-job-details.worksite", "&bArbeitsstätte"))
                .lore(
                        MessageUtil.text("ui.villager-job-details.worksite-lore-1", "&7Job und Workstation neu zuweisen."),
                        MessageUtil.text("ui.villager-job-details.worksite-lore-2", "&7Öffnet das Konfigurationsmenü.")
                )
                .build());

        inv.setItem(28, new ItemBuilder(Material.ARROW)
                .name(MessageUtil.text("ui.villager-job-details.back", "&7Zurück"))
                .lore(MessageUtil.text("ui.villager-job-details.back-lore", "&7Zurück zum Berufsmenü."))
                .build());

        player.openInventory(inv);
    }

    private int countOpenContracts() {
        int count = 0;
        for (VillagerContract contract : village.getContracts()) {
            boolean involved = villager.getId().equals(contract.getRequesterVillagerId())
                    || villager.getId().equals(contract.getSupplierVillagerId());
            if (!involved) continue;
            if (contract.getStatus() == VillagerContract.Status.OPEN
                    || contract.getStatus() == VillagerContract.Status.ACTIVE) {
                count++;
            }
        }
        return count;
    }

    private ItemStack filler() {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(MessageUtil.text("ui.generic.empty", " "))
                .build();
    }
}
