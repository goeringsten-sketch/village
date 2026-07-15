package com.example.village.gui;

import com.example.village.config.VillageConfigManager;
import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.model.VillagerJob;
import com.example.village.util.ItemBuilder;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * "Berufsmenu" - zentraler Einstiegspunkt für jobbezogene Aktionen (siehe Ausbauplan
 * Abschnitt 2): verlinkt auf Produktion, Aufträge, Lager und Arbeitsstätte.
 */
public final class VillagerProfessionGui {

    private final VillageConfigManager configManager;
    private final Village village;
    private final CustomVillager villager;
    private final Player player;

    public VillagerProfessionGui(Village village, CustomVillager villager, Player player, VillageConfigManager configManager) {
        this.configManager = configManager;
        this.village = village;
        this.villager = villager;
        this.player = player;
    }

    public void open() {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.VILLAGER_PROFESSION,
                village.getId().toString() + ":" + villager.getId().toString());
        Inventory inv = Bukkit.createInventory(holder, 54,
                MessageUtil.text("ui.villager-profession.title", "&6Beruf: ") + villager.getName());

        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler());
        }

        VillagerJob job = villager.getJob();
        int maxSlots = configManager.getJobStorageCapacity(village, villager);

        inv.setItem(4, new ItemBuilder(job != null ? job.getIcon() : Material.VILLAGER_SPAWN_EGG)
                .name("&a" + villager.getName())
                .lore(
                        MessageUtil.text("ui.villager-profession.job", "&7Beruf: &e") + (job != null ? job.getDisplayName() : MessageUtil.text("ui.villager-profession.no-job", "Keiner")),
                        MessageUtil.text("ui.villager-profession.profession-key", "&7Profession-Key: &f") + villager.getProfessionKey(),
                        MessageUtil.text("ui.villager-profession.work-location", "&7Arbeitsort: &f")
                                + (villager.getWorkLocation() != null
                                ? villager.getWorkLocation().getBlockX() + ", " + villager.getWorkLocation().getBlockY() + ", " + villager.getWorkLocation().getBlockZ()
                                : MessageUtil.text("ui.villager-profession.unknown", "Unbekannt")),
                        MessageUtil.text("ui.villager-profession.description-1", "&7Dieses Menü zeigt jobbezogene"),
                        MessageUtil.text("ui.villager-profession.description-2", "&7Aktionen und Lagergrenzen an.")
                )
                .build());

        inv.setItem(19, new ItemBuilder(Material.CLOCK)
                .name(MessageUtil.text("ui.villager-profession.production", "&6Produktion"))
                .lore(
                        MessageUtil.text("ui.villager-profession.production-lore-1", "&7Produktion starten/pausieren,"),
                        MessageUtil.text("ui.villager-profession.production-lore-2", "&7Zielprodukt wählen, Rate ansehen.")
                )
                .build());

        inv.setItem(20, new ItemBuilder(Material.CHEST)
                .name(MessageUtil.text("ui.villager-profession.storage", "&aLager öffnen"))
                .lore(
                        MessageUtil.text("ui.villager-profession.storage-lore-1", "&7Öffnet das jobbezogene Lager"),
                        MessageUtil.text("ui.villager-profession.storage-lore-2", "&7für diesen Dorfbewohner."),
                        MessageUtil.text("ui.villager-profession.storage-lore-3", "&7Max. Slots: &e") + maxSlots
                )
                .build());

        inv.setItem(22, new ItemBuilder(Material.PAPER)
                .name(MessageUtil.text("ui.villager-profession.contracts", "&eAufträge"))
                .lore(
                        MessageUtil.text("ui.villager-profession.contracts-lore-1", "&7Aufträge ansehen, annehmen,"),
                        MessageUtil.text("ui.villager-profession.contracts-lore-2", "&7abbrechen oder neu erstellen.")
                )
                .build());

        inv.setItem(24, new ItemBuilder(Material.LECTERN)
                .name(MessageUtil.text("ui.villager-profession.worksite", "&bArbeitsstätte"))
                .lore(
                        MessageUtil.text("ui.villager-profession.worksite-lore-1", "&7Job und Workstation neu zuweisen."),
                        MessageUtil.text("ui.villager-profession.worksite-lore-2", "&7Passt auch Workstation-Aliases an.")
                )
                .build());

        inv.setItem(28, new ItemBuilder(Material.BOOK)
                .name(MessageUtil.text("ui.villager-profession.details", "&6Job-Übersicht"))
                .lore(
                        MessageUtil.text("ui.villager-profession.details-lore-1", "&7Zeigt Level, Produktion und"),
                        MessageUtil.text("ui.villager-profession.details-lore-2", "&7vorhandene Job-Optionen.")
                )
                .build());

        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name(MessageUtil.text("ui.generic.back", "&7Zurueck"))
                .build());

        player.openInventory(inv);
    }

    private ItemStack filler() {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(MessageUtil.text("ui.generic.empty", " "))
                .build();
    }
}
