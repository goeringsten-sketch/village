package com.example.village.listener;

import com.example.village.VillagePlugin;
import com.example.village.gui.JobSelectionGui;
import com.example.village.gui.QuestMenuGui;
import com.example.village.gui.SkillTreeGui;
import com.example.village.gui.VillagerMenuGui;
import com.example.village.model.CustomVillager;
import com.example.village.model.Quest;
import com.example.village.model.Village;
import com.example.village.model.VillagerJob;
import com.example.village.service.QuestManager;
import com.example.village.service.SkillTreeManager;
import com.example.village.service.VillageManager;
import com.example.village.service.VillagerManager;
import com.example.village.trading.VillagerLocalTradeUI;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Listener für GUI-Interaktionen in Villager-Menüs.
 * Implementiert alle vorher fehlenden TODO-Handler.
 */
public class VillagerGuiClickListener implements Listener {

    private final VillagePlugin plugin;
    private final VillagerManager villagerManager;
    private final SkillTreeManager skillTreeManager;
    private final QuestManager questManager;
    private final VillageManager villageManager;

    /** Spieler → aktuell geöffneter Villager */
    private final Map<UUID, UUID> playerToVillager = new HashMap<>();

    /** Spieler → Villager-Kontext im Quest-Menü */
    private final Map<UUID, UUID> playerToQuestVillager = new HashMap<>();

    /** Spieler → wartet auf Villager-Umbenennungs-Input */
    private final Map<UUID, UUID> pendingVillagerRenames = new HashMap<>();

    /** Spieler → wartet auf Job-Selection (GUI-Slot-Auswahl) */
    private final Map<UUID, UUID> pendingJobSelections = new HashMap<>();

    public VillagerGuiClickListener(VillagePlugin plugin,
                                    VillagerManager villagerManager,
                                    SkillTreeManager skillTreeManager,
                                    QuestManager questManager,
                                    VillageManager villageManager) {
        this.plugin = plugin;
        this.villagerManager = villagerManager;
        this.skillTreeManager = skillTreeManager;
        this.questManager = questManager;
        this.villageManager = villageManager;
    }

    private String t(String path, String fallback) {
        return plugin.getConfigManager().text(path, fallback);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        String title = event.getView().title().toString();

        // VillagerInventory (Ressourcen-Ansicht)
        if (inv.getHolder() instanceof com.example.village.gui.VillagerInventoryGui villagerInventory) {
            event.setCancelled(true);
            return;
        }

        // Job-Auswahl GUI
        if (title.startsWith(JobSelectionGui.TITLE_PREFIX)) {
            event.setCancelled(true);
            handleJobSelectionClick(player, event.getRawSlot());
            return;
        }

        // Quest-Menü
        if (QuestMenuGui.isQuestMenuTitle(title)) {
            event.setCancelled(true);
            handleQuestMenuClick(player, event.getCurrentItem());
            return;
        }

        // Haupt-Villager-Menü
        if (title.contains("Menü") && playerToVillager.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            UUID villagerId = playerToVillager.get(player.getUniqueId());
            CustomVillager villager = villagerManager.getVillager(villagerId);
            if (villager != null) {
                handleVillagerMenuClick(player, villager, event.getRawSlot());
            }
        }
    }

    private void handleVillagerMenuClick(Player player, CustomVillager villager, int slot) {
        switch (slot) {
            case 9  -> handleRename(player, villager);
            case 10 -> handleReassignJob(player, villager);
            case 11 -> handleMoveHome(player, villager);
            case 12 -> handleViewInventory(player, villager);
            case 18 -> handleSkillTree(player, villager);
            case 19 -> handleQuestMenu(player, villager);
            case 20 -> handleTrade(player, villager);
            case 21 -> handleTransfer(player, villager);
            case 27 -> handleHeal(player, villager);
            case 28 -> handleBanish(player, villager);
        }
    }

    // ===================== HANDLER =====================

    /**
     * Umbenennen: Chat-Input über pendingVillagerRenames Map.
     * ChatInputListener verarbeitet dann den Text.
     */
    private void handleRename(Player player, CustomVillager villager) {
        player.closeInventory();
        pendingVillagerRenames.put(player.getUniqueId(), villager.getId());
        player.sendMessage(MessageUtil.color(t("messages.villager-rename-prompt", "§eGib den neuen Namen ein §7(oder §cabbrechen§7):")));
    }

    /**
     * Job neu zuweisen: JobSelectionGui öffnen.
     */
    private void handleReassignJob(Player player, CustomVillager villager) {
        player.closeInventory();
        pendingJobSelections.put(player.getUniqueId(), villager.getId());
        new JobSelectionGui(villager, player).open();
    }

    /**
     * Wohnort setzen: Spieler-Position als neuen Heimatort des Villagers übernehmen.
     */
    private void handleMoveHome(Player player, CustomVillager villager) {
        player.closeInventory();
        org.bukkit.Location loc = player.getLocation();
        villager.setHomeLocation(loc);
        // Persistenz via VillageManager
        if (villager.getParentVillageId() != null) {
            villageManager.getVillage(villager.getParentVillageId())
                    .ifPresent(villageManager::saveVillage);
        }
        player.sendMessage(MessageUtil.color(t("messages.villager-home-set", "§a✓ Heimatort von §e%name% §awurde auf deine Position gesetzt §7(%x%, %y%, %z%)§a.")
                .replace("%name%", villager.getName())
                .replace("%x%", String.valueOf(loc.getBlockX()))
                .replace("%y%", String.valueOf(loc.getBlockY()))
                .replace("%z%", String.valueOf(loc.getBlockZ()))));
    }

    /**
     * Inventar anzeigen: Ressourcen des Villagers in einer Chest-GUI.
     */
    private void handleViewInventory(Player player, CustomVillager villager) {
        player.closeInventory();
        Inventory villagerInv = Bukkit.createInventory(null, 27,
                MessageUtil.color(t("ui.villager-inventory.title-prefix", "§b") + villager.getName() + t("ui.villager-inventory.title-suffix", " - Inventar")));

        int slot = 0;
        for (Map.Entry<org.bukkit.Material, Integer> entry : villager.getInventory().entrySet()) {
            if (slot >= 27) break;
            villagerInv.setItem(slot, new ItemStack(entry.getKey(), entry.getValue()));
            slot++;
        }
        player.openInventory(villagerInv);
    }

    /**
     * Skill-Tree GUI.
     */
    private void handleSkillTree(Player player, CustomVillager villager) {
        player.closeInventory();
        new SkillTreeGui(villager, player, skillTreeManager).open();
    }

    /**
     * Quest-Menü: Verfügbare Quests des Villagers laden.
     */
    private void handleQuestMenu(Player player, CustomVillager villager) {
        player.closeInventory();

        Village village = null;
        if (villager.getParentVillageId() != null) {
            village = villageManager.getVillage(villager.getParentVillageId()).orElse(null);
        }
        if (village == null) {
            village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        }

        if (village == null) {
            player.sendMessage(MessageUtil.color(t("messages.village-not-found", "§cKein Dorf gefunden.")));
            return;
        }

        final Village finalVillage = village;
        List<Quest> available = questManager.getAvailableQuests(player, villager, finalVillage);
        List<Quest> active = questManager.getActiveQuestsForVillager(player.getUniqueId(), villager.getId());

        try {
            playerToQuestVillager.put(player.getUniqueId(), villager.getId());
            new QuestMenuGui(player, available, active, plugin).open();
        } catch (Exception e) {
            player.sendMessage(MessageUtil.color(t("messages.quest-load-failed", "§cFehler beim Laden der Quests: %error%").replace("%error%", e.getMessage())));
        }
    }

    private void handleQuestMenuClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        UUID villagerId = playerToQuestVillager.get(player.getUniqueId());
        if (villagerId == null) {
            player.sendMessage(MessageUtil.color(t("messages.villager-missing", "§cVillager nicht mehr vorhanden.")));
            return;
        }

        CustomVillager villager = villagerManager.getVillager(villagerId);
        if (villager == null) {
            player.sendMessage(MessageUtil.color(t("messages.villager-missing", "§cVillager nicht mehr vorhanden.")));
            playerToQuestVillager.remove(player.getUniqueId());
            return;
        }

        String questId = QuestMenuGui.getQuestId(clicked, plugin);
        String action = QuestMenuGui.getQuestAction(clicked, plugin);
        if (questId == null || action == null) {
            return;
        }

        Village village = resolveVillage(villager, player);
        if (village == null) {
            player.sendMessage(MessageUtil.color(t("messages.village-not-found", "§cKein Dorf gefunden.")));
            return;
        }

        try {
            if ("accept".equals(action)) {
                questManager.assignQuestToPlayer(player, villager, questId);
                MessageUtil.send(player, plugin.getConfigManager().getPrefix(),
                        t("messages.quest-accepted", "&aQuest angenommen: &e%title%")
                                .replace("%title%", questManager.getQuestTemplate(questId).getTitle()));
            } else if ("complete".equals(action)) {
                Quest quest = questManager.getActiveQuest(player.getUniqueId(), questId);
                if (quest == null) {
                    MessageUtil.send(player, plugin.getConfigManager().getPrefix(),
                            t("messages.quest-not-active", "&cDiese Quest ist nicht aktiv."));
                    return;
                }
                questManager.completeQuest(player, quest, villager, village);
                MessageUtil.send(player, plugin.getConfigManager().getPrefix(),
                        t("messages.quest-completed", "&aQuest abgeschlossen: &e%title%")
                                .replace("%title%", quest.getTitle()));
            }
        } catch (QuestManager.QuestException e) {
            MessageUtil.send(player, plugin.getConfigManager().getPrefix(),
                    t("messages.quest-action-failed", "&c%error%").replace("%error%", e.getMessage()));
        }

        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> handleQuestMenu(player, villager));
    }

    private Village resolveVillage(CustomVillager villager, Player player) {
        if (villager.getParentVillageId() != null) {
            return villageManager.getVillage(villager.getParentVillageId()).orElse(null);
        }
        return villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
    }

    /**
     * Handel: VillagerLocalTradeUI öffnen (lokale Währung).
     */
    private void handleTrade(Player player, CustomVillager villager) {
        player.closeInventory();

        if (villager.getParentVillageId() == null) {
            player.sendMessage(MessageUtil.color(t("messages.villager-no-village", "§cDieser Villager gehört keinem Dorf an.")));
            return;
        }

        // Prüfe ob Spieler Mitglied des Dorfes ist
        Village village = villageManager.getVillage(villager.getParentVillageId()).orElse(null);
        if (village == null) {
            player.sendMessage(MessageUtil.color(t("messages.village-not-found", "§cDorf nicht gefunden.")));
            return;
        }
        if (!village.isMember(player.getUniqueId())) {
            player.sendMessage(MessageUtil.color(t("messages.not-member", "§cDu bist kein Mitglied dieses Dorfes.")));
            return;
        }

        // Trade-UI öffnen
        try {
            com.example.village.model.VillagerInventory villagerInventory =
                    new com.example.village.model.VillagerInventory(27);

            // Items aus dem Villager-Inventar als Verkaufs-Angebote hinzufügen
            for (Map.Entry<org.bukkit.Material, Integer> entry : villager.getInventory().entrySet()) {
                com.example.village.model.TradeOffer offer = new com.example.village.model.TradeOffer(
                        entry.getKey().name(),
                        entry.getKey().name().charAt(0) + entry.getKey().name().substring(1).toLowerCase().replace('_', ' '),
                        1,
                        1.0,
                        com.example.village.currency.CurrencyType.LOCAL,
                        entry.getValue()
                );
                villagerInventory.addForSale(offer);
            }

            // CurrencyManager-Stub (kein persistenter State für diesen Prototyp)
            com.example.village.currency.LocalCurrencyManager currencyManager =
                    new com.example.village.currency.LocalCurrencyManager(
                            village.getId().toString(),
                            "local_" + village.getName().toLowerCase().replace(" ", "_"),
                            village.getName() + "-Taler",
                            "Ƭ",
                            plugin.getLogger()
                    );

            VillagerLocalTradeUI tradeUI = new VillagerLocalTradeUI(
                    villager.getId().toString(),
                    villager.getName(),
                    village.getId().toString(),
                    villagerInventory,
                    currencyManager,
                    plugin.getLogger()
            );
            plugin.getInventoryClickEventListener().registerTrading(player.getUniqueId(), tradeUI);
            tradeUI.openTradingUI(player);
        } catch (Exception e) {
            player.sendMessage(MessageUtil.color(t("messages.trade-open-failed", "§cFehler beim Öffnen des Handels: %error%").replace("%error%", e.getMessage())));
            plugin.getLogger().warning("Trade UI Fehler: " + e.getMessage());
        }
    }

    /**
     * Transfer: Villager in ein anderes Dorf verschieben.
     * Zeigt alle verfügbaren Dörfer als Chat-Liste.
     */
    private void handleTransfer(Player player, CustomVillager villager) {
        player.closeInventory();

        List<Village> allVillages = new ArrayList<>(villageManager.getAllVillages());
        if (allVillages.isEmpty()) {
            player.sendMessage(MessageUtil.color(t("messages.no-other-villages", "§cKeine anderen Dörfer vorhanden.")));
            return;
        }

        player.sendMessage(MessageUtil.color(t("messages.villager-transfer-title", "§6═══ Dorf-Transfer: %name% ═══").replace("%name%", villager.getName())));
        player.sendMessage(MessageUtil.color(t("messages.current-village", "§7Aktuelles Dorf: §e") +
                (villager.getParentVillageId() != null
                        ? villageManager.getVillage(villager.getParentVillageId())
                        .map(Village::getName).orElse("Unbekannt")
                        : "Keins")));
        player.sendMessage(MessageUtil.color(t("messages.select-target-village", "§7Wähle ein Zieldorf (Klicke auf Namen):")));

        for (Village v : allVillages) {
            if (villager.getParentVillageId() != null &&
                    v.getId().equals(villager.getParentVillageId())) continue; // skip current

            net.md_5.bungee.api.chat.TextComponent component =
                    new net.md_5.bungee.api.chat.TextComponent(
                            MessageUtil.translateColorCodes(t("messages.target-village-line", "§a► §e%name% §7(Level %level%)").replace("%name%", v.getName()).replace("%level%", String.valueOf(v.getLevel()))));
            component.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                    "/village villager transfer " + villager.getId() + " " + v.getId()
            ));
            component.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.BaseComponent[]{
                            new net.md_5.bungee.api.chat.TextComponent(MessageUtil.translateColorCodes(t("messages.transfer-hover", "§7Klicken um %villager% nach §e%village% §7zu transferieren").replace("%villager%", villager.getName()).replace("%village%", v.getName())))
                    }
            ));
            player.spigot().sendMessage(component);
        }
    }

    /**
     * Heilen: Alle Bedürfnisse auf 100 setzen.
     */
    private void handleHeal(Player player, CustomVillager villager) {
        for (com.example.village.model.VillagerNeed need : com.example.village.model.VillagerNeed.values()) {
            villager.setNeedValue(need, 100);
        }
        villager.adjustMorale(50);
        player.sendMessage(MessageUtil.color(t("messages.villager-healed", "§a✓ %name% wurde vollständig geheilt!").replace("%name%", villager.getName())));

        // Persistenz
        if (villager.getParentVillageId() != null) {
            villageManager.getVillage(villager.getParentVillageId())
                    .ifPresent(villageManager::saveVillage);
        }
    }

    /**
     * Verbannen: Villager aus dem Dorf entfernen.
     */
    private void handleBanish(Player player, CustomVillager villager) {
        player.closeInventory();
        String name = villager.getName();
        if (villagerManager.removeVillager(villager)) {
            player.sendMessage(MessageUtil.color(t("messages.villager-banished", "§c✓ %name% wurde aus dem Dorf verjagt!").replace("%name%", name)));
            playerToVillager.remove(player.getUniqueId());
        } else {
            player.sendMessage(MessageUtil.color(t("messages.villager-banish-failed", "§cFehler: %name% konnte nicht entfernt werden.").replace("%name%", name)));
        }
    }

    // ===================== JOB SELECTION =====================

    private void handleJobSelectionClick(Player player, int slot) {
        UUID villagerId = pendingJobSelections.get(player.getUniqueId());
        if (villagerId == null) return;

        if (JobSelectionGui.isBackSlot(slot)) {
            JobSelectionGui.clearContext(player);
            pendingJobSelections.remove(player.getUniqueId());
            CustomVillager villager = villagerManager.getVillager(villagerId);
            if (villager != null) {
                trackVillagerMenu(player, villager);
                Bukkit.getScheduler().runTask(plugin, () ->
                        new VillagerMenuGui(villager, player, skillTreeManager,
                                plugin.getVillagerNutritionService()).open());
            }
            return;
        }

        if (JobSelectionGui.isPrevPageSlot(slot)) {
            JobSelectionGui.PageContext ctx = JobSelectionGui.getContext(player);
            CustomVillager villager = villagerManager.getVillager(villagerId);
            if (ctx != null && villager != null && ctx.page() > 0) {
                Bukkit.getScheduler().runTask(plugin, () -> new JobSelectionGui(villager, player).open(ctx.page() - 1));
            }
            return;
        }

        if (JobSelectionGui.isNextPageSlot(slot)) {
            JobSelectionGui.PageContext ctx = JobSelectionGui.getContext(player);
            CustomVillager villager = villagerManager.getVillager(villagerId);
            if (ctx != null && villager != null && ctx.page() + 1 < ctx.totalPages()) {
                Bukkit.getScheduler().runTask(plugin, () -> new JobSelectionGui(villager, player).open(ctx.page() + 1));
            }
            return;
        }

        if (!JobSelectionGui.isJobSlot(slot)) return;

        VillagerJob newJob = JobSelectionGui.getJobForSlot(player, slot);
        if (newJob == null) return;

        CustomVillager villager = villagerManager.getVillager(villagerId);
        if (villager == null) {
            player.sendMessage(MessageUtil.color(t("messages.villager-missing", "§cVillager nicht mehr vorhanden.")));
            pendingJobSelections.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        VillagerJob oldJob = villager.getJob();
        villager.setJob(newJob);
        pendingJobSelections.remove(player.getUniqueId());
        JobSelectionGui.clearContext(player);

        // Persistenz
        if (villager.getParentVillageId() != null) {
            villageManager.getVillage(villager.getParentVillageId())
                    .ifPresent(villageManager::saveVillage);
        }

        player.sendMessage(MessageUtil.color(t("messages.job-changed", "§a✓ Job geändert: §e%old% §a→ §e%new%")
                .replace("%old%", oldJob != null ? oldJob.getDisplayName() : "Keiner")
                .replace("%new%", newJob.getDisplayName())));

        // Zurück zum Villager-Menü
        Bukkit.getScheduler().runTask(plugin, () -> {
            trackVillagerMenu(player, villager);
            new VillagerMenuGui(villager, player, skillTreeManager,
                    plugin.getVillagerNutritionService()).open();
        });
    }

    // ===================== TRACKING =====================

    public void trackVillagerMenu(Player player, CustomVillager villager) {
        playerToVillager.put(player.getUniqueId(), villager.getId());
    }

    public void untrackVillagerMenu(Player player) {
        playerToVillager.remove(player.getUniqueId());
        playerToQuestVillager.remove(player.getUniqueId());
        pendingVillagerRenames.remove(player.getUniqueId());
        pendingJobSelections.remove(player.getUniqueId());
    }

    public UUID getLastInteractedVillager(UUID playerId) {
        return playerToVillager.get(playerId);
    }

    public Map<UUID, UUID> getPendingVillagerRenames() {
        return pendingVillagerRenames;
    }

    public Map<UUID, UUID> getPendingJobSelections() {
        return pendingJobSelections;
    }
}
