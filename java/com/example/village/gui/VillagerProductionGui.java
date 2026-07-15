package com.example.village.gui;

import com.example.village.config.VillageConfigManager;
import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.model.VillagerProfession;
import com.example.village.service.VillagerService;
import com.example.village.util.ItemBuilder;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * GUI für das "Produktion"-Menü eines Berufs.
 *
 * Erlaubt laut Ausbauplan: Produktion starten/pausieren, Zielprodukt auswählen
 * und Produktionsrate/Laufzeit einsehen. Die Slot-Positionen der Produkt-Buttons
 * sind deterministisch aus {@link VillagerProfession#getProduces()} abgeleitet,
 * damit der Klick-Handler ohne zusätzlichen State weiß, welches Material gemeint ist.
 */
public final class VillagerProductionGui {

    public static final int SLOT_INFO = 4;
    public static final int PRODUCT_SLOT_START = 19;
    public static final int SLOT_ALL_PRODUCTS = 31;
    public static final int SLOT_PAUSE_TOGGLE = 40;
    public static final int SLOT_BACK = 49;

    private final VillageConfigManager configManager;
    private final VillagerService villagerService;
    private final Village village;
    private final CustomVillager villager;
    private final Player player;

    public VillagerProductionGui(VillageConfigManager configManager,
                                  VillagerService villagerService,
                                  Village village,
                                  CustomVillager villager,
                                  Player player) {
        this.configManager = configManager;
        this.villagerService = villagerService;
        this.village = village;
        this.villager = villager;
        this.player = player;
    }

    public void open() {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.VILLAGER_PRODUCTION,
                village.getId().toString() + ":" + villager.getId().toString());
        Inventory inv = Bukkit.createInventory(holder, 54,
                MessageUtil.text("ui.villager-production.title-prefix", "&6Produktion: ") + villager.getName());

        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler());
        }

        VillagerProfession profession = configManager.getProfession(villager.getProfessionKey());
        List<Material> produces = profession != null ? profession.getProduces() : List.of();

        inv.setItem(SLOT_INFO, buildInfoItem(profession, produces));

        for (int i = 0; i < produces.size() && PRODUCT_SLOT_START + i < SLOT_ALL_PRODUCTS; i++) {
            Material material = produces.get(i);
            boolean selected = material.equals(villager.getPreferredProduct());
            inv.setItem(PRODUCT_SLOT_START + i, new ItemBuilder(material)
                    .name((selected ? "&a&l" : "&f") + material.name())
                    .lore(
                            selected
                                    ? MessageUtil.text("ui.villager-production.selected", "&aAktuell ausgewählt")
                                    : MessageUtil.text("ui.villager-production.select-hint", "&7Linksklick: als Zielprodukt wählen")
                    )
                    .glow(selected)
                    .build());
        }

        boolean allMode = villager.getPreferredProduct() == null;
        inv.setItem(SLOT_ALL_PRODUCTS, new ItemBuilder(Material.HOPPER)
                .name(allMode ? "&a&lAlle Produkte" : "&fAlle Produkte")
                .lore(
                        MessageUtil.text("ui.villager-production.all-lore-1", "&7Produziert bei jedem Zyklus"),
                        MessageUtil.text("ui.villager-production.all-lore-2", "&7alle möglichen Güter dieses Berufs."),
                        allMode
                                ? MessageUtil.text("ui.villager-production.selected", "&aAktuell ausgewählt")
                                : MessageUtil.text("ui.villager-production.select-hint", "&7Linksklick: auswählen")
                )
                .glow(allMode)
                .build());

        boolean paused = villager.isProductionPaused();
        inv.setItem(SLOT_PAUSE_TOGGLE, new ItemBuilder(paused ? Material.LIME_DYE : Material.REDSTONE_TORCH)
                .name(paused
                        ? MessageUtil.text("ui.villager-production.resume", "&aProduktion fortsetzen")
                        : MessageUtil.text("ui.villager-production.pause", "&cProduktion pausieren"))
                .lore(
                        paused
                                ? MessageUtil.text("ui.villager-production.resume-lore", "&7Produktion ist aktuell pausiert.")
                                : MessageUtil.text("ui.villager-production.pause-lore", "&7Produktion läuft aktuell.")
                )
                .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name(MessageUtil.text("ui.generic.back", "&7Zurück"))
                .build());

        player.openInventory(inv);
    }

    private ItemStack buildInfoItem(VillagerProfession profession, List<Material> produces) {
        if (profession == null || produces.isEmpty()) {
            return new ItemBuilder(Material.BARRIER)
                    .name(MessageUtil.text("ui.villager-production.none", "&cDieser Beruf produziert nichts"))
                    .lore(MessageUtil.text("ui.villager-production.none-lore", "&7Dieser Beruf ist eine Dienstleistung ohne eigene Produktion."))
                    .build();
        }

        double intervalSeconds = villagerService != null
                ? villagerService.getEffectiveProductionIntervalSeconds(village, profession)
                : profession.getProductionIntervalSeconds();
        int amountPerCycle = Math.max(1, 1 + (villager.getLevel() / 3));
        int usedSlots = villager.getInventory().size();
        int maxSlots = configManager.getJobStorageCapacity(village, villager);

        return new ItemBuilder(Material.CLOCK)
                .name(MessageUtil.text("ui.villager-production.info", "&6Produktions-Übersicht"))
                .lore(
                        MessageUtil.text("ui.villager-production.rate", "&7Produktionsrate: &fjede(n) ")
                                + String.format("%.0f", intervalSeconds)
                                + MessageUtil.text("ui.villager-production.seconds", " Sekunden"),
                        MessageUtil.text("ui.villager-production.cost", "&7Kosten: &fkeine (zeitbasiert)"),
                        MessageUtil.text("ui.villager-production.per-cycle", "&7Menge pro Zyklus: &f~") + amountPerCycle,
                        MessageUtil.text("ui.villager-production.storage", "&7Job-Lager: &f") + usedSlots + "/" + maxSlots,
                        MessageUtil.text("ui.generic.empty", " "),
                        villager.isProductionPaused()
                                ? MessageUtil.text("ui.villager-production.status-paused", "&cStatus: Pausiert")
                                : MessageUtil.text("ui.villager-production.status-active", "&aStatus: Aktiv")
                )
                .build();
    }

    private ItemStack filler() {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(MessageUtil.text("ui.generic.empty", " "))
                .build();
    }
}
