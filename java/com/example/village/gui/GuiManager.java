package com.example.village.gui;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.hook.VaultHook;
import com.example.village.service.BorderPreviewService;
import com.example.village.service.BuildingConfigLoader;
import com.example.village.service.BuildingService;
import com.example.village.service.VillageManager;
import com.example.village.model.BuildingDefinition;

import com.example.village.model.CustomVillager;
import com.example.village.model.UpgradeType;
import com.example.village.model.Village;
import com.example.village.model.VillageBuilding;
import com.example.village.model.VillageJoinRequest;
import com.example.village.model.VillageMember;
import com.example.village.model.VillageRelation;
import com.example.village.model.VillageRelationState;
import com.example.village.model.VillageRelationType;
import com.example.village.model.VillageRole;
import com.example.village.model.VillagerProfession;
import com.example.village.util.ItemBuilder;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiManager {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VaultHook vaultHook;
    private final VillageManager villageManager;
    private final Map<UUID, String> buildingSearchQueries = new ConcurrentHashMap<>();
    /** Tracks building-type keys / "typeKey:tier" upgrade keys not yet acknowledged by a player. */
    private final java.util.concurrent.ConcurrentHashMap<UUID, java.util.Set<String>> newlyUnlocked =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Integer> villagerGlowModes = new ConcurrentHashMap<>();  // 0=OFF, 1=TEMP, 2=ON
    private BorderPreviewService previewService;

    private String ui(String path, String fallback) {
        return configManager.text(path, fallback);
    }

    public GuiManager(VillagePlugin plugin, VillageConfigManager configManager,
                      VaultHook vaultHook, VillageManager villageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.vaultHook = vaultHook;
        this.villageManager = villageManager;
    }

    public void setPreviewService(BorderPreviewService previewService) {
        this.previewService = previewService;
    }

    private List<BuildingDefinition> getDefinitionList() {
        BuildingConfigLoader loader = plugin.getBuildingConfigLoader();
        if (loader == null) return List.of();
        return loader.getAll().stream()
                .filter(BuildingDefinition::isShowInMenu)
                .sorted(Comparator.comparing(BuildingDefinition::getCategoryId)
                        .thenComparing(BuildingDefinition::getId))
                .toList();
    }

    private BuildingDefinition getDefinition(String typeKey) {
        BuildingConfigLoader loader = plugin.getBuildingConfigLoader();
        return loader != null ? loader.getDefinition(typeKey) : null;
    }

    public void setBuildingSearchQuery(Player player, String query) {
        if (player == null) return;
        if (query == null || query.isBlank()) {
            buildingSearchQueries.remove(player.getUniqueId());
            return;
        }
        buildingSearchQueries.put(player.getUniqueId(), query.trim());
    }

    public String getBuildingSearchQuery(Player player) {
        if (player == null) return "";
        return buildingSearchQueries.getOrDefault(player.getUniqueId(), "");
    }

    // ── Newly-unlocked highlight API ─────────────────────────────────────────

    public boolean isNewlyUnlocked(UUID playerId, String key) {
        var set = newlyUnlocked.get(playerId);
        return set != null && set.contains(key);
    }

    public void acknowledgeItem(UUID playerId, String key) {
        var set = newlyUnlocked.get(playerId);
        if (set != null) set.remove(key);
    }

    public void markNewlyUnlockedForVillage(Village village, int newLevel) {
        BuildingConfigLoader loader = plugin.getBuildingConfigLoader();
        if (loader == null) return;
        for (BuildingDefinition def : loader.getAll()) {
            if (def.getRequiredVillageLevel() == newLevel) {
                notifyMembers(village, def.getId(), def.getPermission());
            }
            for (var entry : def.getUpgrades().entrySet()) {
                BuildingDefinition.UpgradeTier tier = entry.getValue();
                if (tier.getRequiredVillageLevel() == newLevel) {
                    notifyMembers(village, def.getId() + ":" + entry.getKey(), tier.getPermission());
                }
            }
        }
    }

    private void notifyMembers(Village village, String key, String permission) {
        for (UUID memberId : village.getMembers().keySet()) {
            org.bukkit.entity.Player online = Bukkit.getPlayer(memberId);
            if (online == null) continue;
            if (permission == null || permission.isBlank()
                    || online.hasPermission(permission)
                    || online.hasPermission("village.admin")) {
                newlyUnlocked.computeIfAbsent(memberId,
                        k -> java.util.Collections.synchronizedSet(new java.util.HashSet<>())).add(key);
            }
        }
    }

    public boolean hasAccessToBuilding(Player player, BuildingDefinition def) {
        if (player.hasPermission("village.admin")) return true;
        String perm = def.getPermission();
        return perm == null || perm.isBlank() || player.hasPermission(perm);
    }

    public boolean hasAccessToUpgrade(Player player, BuildingDefinition.UpgradeTier tier) {
        if (player.hasPermission("village.admin")) return true;
        String perm = tier.getPermission();
        return perm == null || perm.isBlank() || player.hasPermission(perm);
    }

    /**
     * Scans all buildings/upgrades this player can now access and marks any that are
     * newly accessible (permission was granted since last check) as newly-unlocked.
     * Called on login to catch offline permission grants.
     */
    public void checkAndMarkNewlyAccessible(Player player, Village village) {
        BuildingConfigLoader loader = plugin.getBuildingConfigLoader();
        if (loader == null) return;
        UUID pid = player.getUniqueId();
        for (BuildingDefinition def : loader.getAll()) {
            if (!def.isShowInMenu()) continue;
            if (!hasAccessToBuilding(player, def)) continue;
            if (village.getLevel() >= def.getRequiredVillageLevel()) {
                // Building itself: mark if not yet acknowledged
                String key = def.getId();
                newlyUnlocked.computeIfAbsent(pid,
                        k -> java.util.Collections.synchronizedSet(new java.util.HashSet<>())).add(key);
            }
            // Upgrade tiers
            for (var entry : def.getUpgrades().entrySet()) {
                BuildingDefinition.UpgradeTier tier = entry.getValue();
                if (!hasAccessToUpgrade(player, tier)) continue;
                if (village.getLevel() >= tier.getRequiredVillageLevel()) {
                    String key = def.getId() + ":" + entry.getKey();
                    newlyUnlocked.computeIfAbsent(pid,
                            k -> java.util.Collections.synchronizedSet(new java.util.HashSet<>())).add(key);
                }
            }
        }
    }

    private boolean matchesBuildingSearch(Player player, BuildingDefinition definition, VillageBuilding building) {
        String query = getBuildingSearchQuery(player);
        if (query.isBlank()) {
            return true;
        }

        String haystack = "";
        if (definition != null) {
            haystack += " " + definition.getName() + " " + definition.getId() + " " + definition.getDescription();
        }
        if (building != null) {
            haystack += " " + (building.hasCustomName() ? building.getCustomName() : "")
                    + " " + building.getTypeKey();
        }

        return haystack.toLowerCase().contains(query.toLowerCase());
    }

    public List<VillageBuilding> getVisibleBuildingsForManageType(Player player, Village village, String typeKey) {
        BuildingDefinition def = getDefinition(typeKey);
        if (village == null || def == null) {
            return List.of();
        }

        return village.getBuildings().stream()
                .filter(building -> building.getTypeKey().equals(typeKey))
                .filter(building -> matchesBuildingSearch(player, def, building))
                .toList();
    }

    private String getBuildingDisplayName(VillageBuilding building, BuildingDefinition definition) {
        if (building != null && building.hasCustomName()) {
            return building.getCustomName();
        }
        if (definition != null) {
            return definition.getName();
        }
        return building != null ? building.getTypeKey() : "Gebäude";
    }

    // --- Founding GUI ---

    public void openFoundingGui(Player player) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.FOUNDING);
        Inventory inv = Bukkit.createInventory(holder, 27, MessageUtil.color(ui("ui.gui.founding.title", "&6&lDorf gruenden")));

        // Info item
        inv.setItem(4, new ItemBuilder(Material.BELL)
                .name(ui("ui.gui.founding.name", "&6&lDorf gruenden"))
                .lore(ui("ui.gui.founding.lore-1", "&7Gruende dein eigenes Dorf!"),
                        ui("ui.gui.founding.empty", "&7"),
                        ui("ui.gui.founding.requirements", "&eVoraussetzungen:"),
                        ui("ui.gui.founding.money", "&7Geld: &a") + vaultHook.format(configManager.getFoundingMoneyCost()),
                        ui("ui.gui.founding.permission", "&7Permission: &a") + configManager.getFoundingPermission())
                .build());

        // Confirm
        inv.setItem(11, new ItemBuilder(Material.LIME_WOOL)
                .name(ui("ui.gui.founding.confirm", "&a&lBestaetigen"))
                .lore(ui("ui.gui.founding.confirm-lore-1", "&7Klicke um ein Dorf zu gruenden."),
                        ui("ui.gui.founding.confirm-lore-2", "&7Du wirst nach einem Namen gefragt."))
                .build());

        // Cancel
        inv.setItem(15, new ItemBuilder(Material.RED_WOOL)
                .name(ui("ui.gui.founding.cancel", "&c&lAbbrechen"))
                .lore(ui("ui.gui.founding.cancel-lore", "&7Abbrechen und Menue schliessen."))
                .build());

        // Item requirements
        int slot = 18;
        for (Map<String, Object> req : configManager.getFoundingItemRequirements()) {
            String matName = String.valueOf(req.get("material"));
            Object amountObj = req.get("amount");
            if (amountObj == null) continue;
            int amount = ((Number) amountObj).intValue();
            if (amount <= 0) amount = 1; // Validiere dass amount >= 1
            Material mat = Material.matchMaterial(matName);
            if (mat == null) mat = Material.BARRIER;

            inv.setItem(slot++, new ItemBuilder(mat, Math.min(amount, 64))
                    .name(ui("ui.gui.founding.required-item", "&eBenoetigtes Item"))
                    .lore(ui("ui.gui.founding.required-item-lore", "&7") + matName + " x" + amount)
                    .build());
        }

        player.openInventory(inv);
    }

    // --- Main Village GUI ---

    public void openMainGui(Player player, Village village) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.MAIN, village.getId().toString());
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color(ui("ui.gui.main.title-prefix", "&6&l") + village.getName()));

        // Village info - Bell
        inv.setItem(4, createBellItemForMain(player, village));

        // Upgrades
        inv.setItem(19, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name(ui("ui.gui.main.upgrades", "&a&lUpgrades"))
                .lore(ui("ui.gui.main.upgrades-lore", "&7Verbessere dein Dorf."))
                .glow(true)
                .build());

        // Buildings (nur Gebäude, nicht "Meine Gebäude")
        inv.setItem(21, new ItemBuilder(Material.BRICKS)
                .name(ui("ui.gui.main.buildings", "&a&lGebaeude"))
                .lore(ui("ui.gui.main.buildings-lore", "&7Baue neue Gebaeude oder verwalte bestehende."))
                .glow(true)
                .build());

        // Villagers
        inv.setItem(23, new ItemBuilder(Material.VILLAGER_SPAWN_EGG)
                .name(ui("ui.gui.main.villagers", "&a&lDorfbewohner"))
                .lore(ui("ui.gui.main.villagers-lore", "&7Verwalte deine Dorfbewohner."))
                .glow(true)
                .build());

        // Members
        inv.setItem(25, new ItemBuilder(Material.PLAYER_HEAD)
                .name(ui("ui.gui.main.members", "&a&lMitglieder"))
                .lore(village.getJoinRequests().isEmpty()
                        ? List.of(ui("ui.gui.main.members-lore", "&7Verwalte Dorfmitglieder."))
                        : List.of("&7Verwalte Dorfmitglieder.",
                                ui("ui.gui.main.open-requests", "&7Offene Anfragen: &e") + village.getJoinRequests().size()))
                .glow(true)
                .build());

        // Relations
        inv.setItem(27, new ItemBuilder(Material.PAPER)
                .name(ui("ui.gui.main.relations", "&a&lRelationen"))
                .lore(ui("ui.gui.main.relations-lore-1", "&7Verwalte Beziehungen zu anderen Dörfern."),
                        ui("ui.gui.main.relations-lore-2", "&7Freundschaft, Handel, Krieg oder Durchgangssperre."))
                .glow(true)
                .build());

        // Border
        inv.setItem(37, new ItemBuilder(Material.MAP)
                .name(ui("ui.gui.main.borders", "&a&lGrenzen"))
                .lore(ui("ui.gui.main.borders-lore", "&7Zeige oder aendere die Dorfgrenzen."))
                .build());

        // Level info
        inv.setItem(39, new ItemBuilder(Material.NETHER_STAR)
                .name(ui("ui.gui.main.quests", "&a&lQuests"))
                .lore(ui("ui.gui.main.quests-lore-1", "&7Alle Dorf-Quests fuer Mitglieder"),
                        ui("ui.gui.main.quests-lore-2", "&7(6x9 Uebersicht)."))
                .build());

        // Border preview toggle removed from main GUI (only available in Border menu)

        // Rename (slot 43)
        inv.setItem(43, new ItemBuilder(Material.NAME_TAG)
                .name(ui("ui.gui.main.rename", "&e&lDorf umbenennen"))
                .lore(ui("ui.gui.main.rename-lore-1", "&7Aendere den Namen deines Dorfes."),
                        ui("ui.gui.main.rename-lore-2", "&7"),
                        ui("ui.gui.main.rename-lore-3", "&eKlicke zum Umbenennen."))
                .build());

        VillageMember viewer = village.getMember(player.getUniqueId());
        boolean canDelete = viewer != null && viewer.getRole().canDeleteVillage();
        inv.setItem(53, new ItemBuilder(canDelete ? Material.TNT : Material.GRAY_DYE)
                .name(canDelete ? ui("ui.gui.main.delete", "&c&lDorf loeschen") : ui("ui.gui.main.delete-locked", "&8&lDorf loeschen"))
                .lore(canDelete
                        ? List.of(ui("ui.gui.main.delete-lore-1", "&7Loesche dein Dorf unwiderruflich."), ui("ui.gui.main.delete-lore-2", "&7"), ui("ui.gui.main.delete-lore-3", "&cKlicke zum Loeschen."))
                        : List.of(ui("ui.gui.main.delete-locked-lore", "&7Nur der Gruender kann das Dorf loeschen.")))
                .build());

        if (viewer != null && !village.isFounder(player.getUniqueId())) {
            inv.setItem(51, new ItemBuilder(Material.OAK_DOOR)
                    .name(ui("ui.gui.main.leave", "&c&lDorf verlassen"))
                    .lore(ui("ui.gui.main.leave-lore-1", "&7Verlasse dieses Dorf."),
                            ui("ui.gui.main.leave-lore-2", "&7Du kannst danach einem anderen Dorf beitreten."))
                    .build());
        }

        if (player.hasPermission("village.admin")) {
            inv.setItem(45, new ItemBuilder(Material.COMMAND_BLOCK)
                    .name(ui("ui.gui.main.schematic-tools", "&c&lSchematic Tools"))
                    .lore(ui("ui.gui.main.schematic-tools-lore-1", "&7Oeffnet den Admin-Schematic-Workflow."),
                            ui("ui.gui.main.schematic-tools-lore-2", "&7"),
                            ui("ui.gui.main.schematic-tools-lore-3", "&eKlicke fuer Befehls-Hilfe."))
                    .build());
        }

        // Close
        inv.setItem(49, new ItemBuilder(Material.BARRIER)
                .name(ui("ui.gui.main.close", "&c&lSchliessen"))
                .build());

        player.openInventory(inv);
    }

    public void openVillageQuestsGui(Player player, Village village) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.QUESTS, village.getId().toString());
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color("&6&lQuests"));
        int slot = 0;
        if (plugin.getQuestManager() != null) {
            for (com.example.village.model.Quest quest : plugin.getQuestManager().getQuestTemplates()) {
                if (slot >= 45) break;
                inv.setItem(slot++, new ItemBuilder(Material.BOOK)
                        .name("&e" + quest.getTitle())
                        .lore("&7" + quest.getDescription(),
                                "&7Ziel: &f" + quest.getObjective(),
                                "&7Mindest-Level: &e" + quest.getRequiredVillageLevel(),
                                "&7Belohnung Punkte: &a" + quest.getRewardVillagePoints())
                        .build());
            }
        }
        inv.setItem(49, new ItemBuilder(Material.ARROW).name("&7Zurueck").build());
        player.openInventory(inv);
    }

    public void openRelationsGui(Player player, Village village) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.RELATIONS,
                village.getId().toString());
        Inventory inv = Bukkit.createInventory(holder, 27,
                MessageUtil.color("&6&lRelationen: " + village.getName()));

        inv.setItem(4, new ItemBuilder(Material.PAPER)
                .name("&6&lRelationen verwalten")
                .lore("&7Wähle einen Beziehungsmodus.",
                        "&7Freundschaft, Handel, Krieg oder Durchgangssperre.")
                .build());

        inv.setItem(19, new ItemBuilder(Material.GREEN_WOOL)
                .name("&a&lFreundschaft")
                .lore("&7Fordere eine Freundschaft zwischen zwei Dörfern an.",
                        "&7Akzeptiere oder breche bestehende Beziehungen.")
                .build());

        inv.setItem(21, new ItemBuilder(Material.BLUE_WOOL)
                .name("&b&lHandel")
                .lore("&7Eröffne einen Handelsmodus mit einem anderen Dorf.",
                        "&7Andere Dörfer können den Handel annehmen oder ablehnen.")
                .build());

        inv.setItem(23, new ItemBuilder(Material.RED_WOOL)
                .name("&c&lKrieg")
                .lore("&7Erkläre einem anderen Dorf den Krieg.",
                        "&7Beende den Krieg durch erneutes Klicken.")
                .build());

        inv.setItem(25, new ItemBuilder(Material.GRAY_WOOL)
                .name("&8&lDurchgangssperre")
                .lore("&7Setze eine Durchgangssperre gegen ein anderes Dorf.",
                        "&7Breche die Sperre durch erneutes Klicken.")
                .build());

        inv.setItem(22, new ItemBuilder(Material.ARROW)
                .name("&7Zurück")
                .build());

        player.openInventory(inv);
    }

    public void openRelationTargetsGui(Player player, Village village, VillageRelationType type) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.RELATION_TARGETS,
                village.getId().toString() + ":" + type.name());
        Inventory inv = Bukkit.createInventory(holder, 54,
                MessageUtil.color("&6&l" + type.getDisplayName() + ": " + village.getName()));

        inv.setItem(4, new ItemBuilder(Material.PAPER)
                .name("&6&l" + type.getDisplayName())
                .lore("&7Wähle ein Dorf aus, um diese Beziehung zu verwalten.",
                        "&7Aktive Beziehungen werden angezeigt.")
                .build());

        int slot = 10;
        for (Village target : villageManager.getAllVillages()) {
            if (target.getId().equals(village.getId())) continue;
            if (slot >= 49) break;

            VillageRelation relation = village.getRelation(target.getId());
            String relationStatus = relation == null ? "&7Keine Beziehung"
                    : "&7" + relation.getType().getDisplayName() + ": &e" + relation.getState().getDisplayName();
            String actionHint = relation == null
                    ? "&7Klicke, um eine Anfrage zu senden."
                    : relation.getState() == VillageRelationState.REQUESTED
                    ? (relation.getInitiatorVillageId() != null && relation.getInitiatorVillageId().equals(village.getId())
                        ? "&7Klicke, um die Anfrage abzubrechen."
                        : "&7Klicke, um die Anfrage anzunehmen.")
                    : relation.getState() == VillageRelationState.ACTIVE
                    ? "&7Klicke, um die Beziehung zu beenden."
                    : "&7Klicke, um die Anfrage zu beenden.";

            inv.setItem(slot++, new ItemBuilder(Material.PLAYER_HEAD)
                    .name("&a" + target.getName())
                    .lore("&7Dorf: &e" + target.getName(),
                            relationStatus,
                            actionHint)
                    .build());
        }

        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name("&7Zurück")
                .build());

        player.openInventory(inv);
    }

    // --- Confirm Delete GUI ---

    public void openConfirmDeleteGui(Player player, Village village) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.CONFIRM,
                "delete:" + village.getId().toString());
        Inventory inv = Bukkit.createInventory(holder, 27,
                MessageUtil.color("&c&lDorf loeschen?"));

        inv.setItem(4, new ItemBuilder(Material.TNT)
                .name("&c&lDorf " + village.getName() + " loeschen?")
                .lore("&7Diese Aktion kann nicht rueckgaengig",
                        "&7gemacht werden!",
                        "&7",
                        "&7Alle Gebaeude, Dorfbewohner und",
                        "&7Upgrades gehen verloren!")
                .build());

        inv.setItem(11, new ItemBuilder(Material.LIME_WOOL)
                .name("&a&lJa, loeschen!")
                .lore("&7Dorf unwiderruflich loeschen.")
                .build());

        inv.setItem(15, new ItemBuilder(Material.RED_WOOL)
                .name("&c&lAbbrechen")
                .lore("&7Zurueck zum Dorfmenue.")
                .build());

        player.openInventory(inv);
    }

    // --- Border Selection GUI ---

    public void openBorderSelectionGui(Player player, Village village) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.BORDER_SELECTION,
                village.getId().toString());
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color("&6&lGrenzauswahl"));

        // Calculate remaining available blocks
        int maxArea = village.getMaxArea(
                configManager.getMaxArea(), configManager.getUpgradeAreaPerLevel());
        int usedArea = village.getTotalArea();
        int remaining = maxArea - usedArea;

        // Info item showing available blocks (slot 4)
        inv.setItem(4, new ItemBuilder(Material.FILLED_MAP)
                .name("&6&lGebietsinformation")
                .lore("&7Aktuelle Flaeche: &e" + usedArea + " Bloecke",
                        "&7Maximale Flaeche: &e" + maxArea + " Bloecke",
                        "&7Verfuegbar: &a" + remaining + " Bloecke",
                        "&7Gebiete: &e" + village.getBorders().size())
                .build());

        String method = configManager.getSelectionMethod();

        if ("BOTH".equals(method) || "COORDINATES".equals(method)) {
            inv.setItem(19, new ItemBuilder(Material.COMPASS)
                    .name("&a&lKoordinaten-Eingabe")
                    .lore("&7Gib zwei Eckpunkte (x z) im Chat ein.",
                            "&7Daraus entsteht ein Rechteck.",
                            "&7",
                            "&7Verfuegbar: &a" + remaining + " Bloecke")
                    .build());
        }

        if ("BOTH".equals(method) || "WALK".equals(method)) {
            inv.setItem(28, new ItemBuilder(Material.LEATHER_BOOTS)
                    .name("&a&lFlaeche ablaufen")
                    .lore("&7Laufe die Grenze Block fuer Block ab.",
                            "&7Nicht diagonal!",
                            "&7Stoppt bei bereits besuchtem Block",
                            "&7oder bestehender Dorfgrenze.",
                            "&7",
                            "&7Verfuegbar: &a" + remaining + " Bloecke")
                    .build());
        }

        // Show borders (slot 13)
        boolean active = previewService != null && previewService.hasActivePreview(player);
        inv.setItem(13, new ItemBuilder(active ? Material.ENDER_EYE : Material.ENDER_PEARL)
                .name("&a&lGrenzen anzeigen")
                .lore(active
                        ? List.of("&7Status: &aAN",
                        "&7",
                        "&eLinksklick: &7Ausblenden (permanent)",
                        "&eRechtsklick: &730s anzeigen")
                        : List.of("&7Status: &cAUS",
                        "&7",
                        "&eLinksklick: &7Permanent anzeigen",
                        "&eRechtsklick: &730s anzeigen"))
                .build());

        // Release border (slot 53)
        inv.setItem(53, new ItemBuilder(Material.SHEARS)
                .name("&e&lGrenzen zuruecksetzen")
                .lore("&7Setzt die Dorfgrenze auf das",
                        "&7urspruengliche Quadrat um die",
                        "&7Glocke zurueck.",
                        "&c&lAchtung: Alle Gebaeude werden entfernt!",
                        "&7",
                        "&eKlicke zum Zuruecksetzen.")
                .build());

        inv.setItem(21, new ItemBuilder(Material.SHEARS)
                .name("&b&lRegion teilen")
                .lore("&7Teilt eine bestehende Grenzflaeche in zwei Bereiche.",
                        "&7Klicke eine Grenzregion und folge den Anweisungen.",
                        "&7Diese Funktion ist aktuell experimentell.")
                .build());

        inv.setItem(22, new ItemBuilder(Material.GREEN_STAINED_GLASS_PANE)
                .name("&a&lRegion vergroessern")
                .lore("&7Erweitert eine bestehende Grenzflaeche.",
                        "&7Klicke eine Grenzregion und waehle einen neuen Bereich.",
                        "&7Diese Funktion ist aktuell noch in Arbeit.")
                .build());

        inv.setItem(23, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name("&c&lRegion verkleinern")
                .lore("&7Verkleinert eine bestehende Grenzflaeche.",
                        "&7Klicke eine Grenzregion und waehle einen kleineren Bereich.",
                        "&7Diese Funktion ist aktuell noch in Arbeit.")
                .build());

        inv.setItem(30, new ItemBuilder(Material.BARRIER)
                .name("&c&lGrenze loeschen")
                .lore("&7Klicke danach auf einen Block",
                        "&7der zu loeschenden Grenzflaeche.",
                        "&7",
                        "&eDanach kommt eine Bestaetigung.")
                .build());

        inv.setItem(32, new ItemBuilder(Material.ANVIL)
                .name("&b&lGrenzen vereinen")
                .lore("&7Klicke danach auf 2+ Grenzflaechen,",
                        "&7um sie zu vereinen.",
                        "&7",
                        "&eNach jeder Auswahl: [JA]/[NEIN] in Chat.")
                .build());

        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name("&7Zurueck")
                .build());

        player.openInventory(inv);
    }

    // --- Buildings GUI ---

    public void openBuildingsGui(Player player, Village village) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.BUILDINGS,
                village.getId().toString());
        Inventory inv = Bukkit.createInventory(holder, 27, MessageUtil.color("&6&lGebaeude"));

        // Gebäude bauen
        inv.setItem(11, new ItemBuilder(Material.GRASS_BLOCK)
                .name("&a&lGebäude bauen")
                .lore("&7Freigeschaltete Gebäude anzeigen",
                        "&7und neue Gebäude platzieren.")
                .build());

        // Gebäude verwalten
        inv.setItem(15, new ItemBuilder(Material.CHEST)
                .name("&e&lGebäude verwalten")
                .lore("&7Gebäudearten anzeigen und",
                        "&7gebaute Gebäude verwalten.")
                .build());

        inv.setItem(22, new ItemBuilder(Material.ARROW)
                .name("&7Zurueck")
                .build());

        player.openInventory(inv);
    }

    // --- Building Build GUI ---

    public void openBuildingBuildGui(Player player, Village village) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.BUILDINGS,
                village.getId().toString() + ":build");
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color("&6&lGebäude bauen"));

        int slot = 0;
        for (BuildingDefinition def : getDefinitionList()) {
            // Only show buildings the player has the required permission for
            if (!hasAccessToBuilding(player, def)) continue;
            if (!matchesBuildingSearch(player, def, null)) continue;
            boolean unlocked = village.getLevel() >= def.getRequiredVillageLevel();

            if (!unlocked) continue;

            long builtCount = village.getBuildings().stream()
                    .filter(b -> b.getTypeKey().equals(def.getId()))
                    .count();
            boolean inProgress = village.getBuildings().stream()
                    .anyMatch(b -> b.getTypeKey().equals(def.getId()) && !b.isCompleted());
            Material icon = inProgress ? Material.CLOCK : def.getIcon();
            String statusLine = inProgress ? "&7Status: &eAktuell im Bau" : "&aKlicke zum Platzieren!";

            inv.setItem(slot++, new ItemBuilder(icon)
                    .name(def.getName())
                    .lore(def.getDescription(),
                            "&7",
                            "&7Kosten: &e" + vaultHook.format(def.getBuildMoneyGlobal()),
                            "&7Kapazität: &e" + def.getVillagerSlots() + " Bewohner",
                            "&7Gebaut: &e" + builtCount,
                            "&7",
                            statusLine)
                    .glow(true)
                    .build());

            if (slot >= 45) break;
        }

        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name("&7Zurueck")
                .build());

        player.openInventory(inv);
    }

    // --- Building Manage GUI ---

    public void openBuildingManageGui(Player player, Village village) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.BUILDINGS,
                village.getId().toString() + ":manage");
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color("&6&lGebäude verwalten"));

        int slot = 0;
        for (BuildingDefinition def : getDefinitionList()) {
            if (!matchesBuildingSearch(player, def, null)) continue;

            long builtCount = village.getBuildings().stream()
                    .filter(b -> b.getTypeKey().equals(def.getId()))
                    .count();

            if (builtCount == 0) continue;

            inv.setItem(slot++, new ItemBuilder(def.getIcon())
                    .name(def.getName())
                    .lore("&7Gebaut: &e" + builtCount,
                            "&7",
                            "&aKlicke zum Verwalten!")
                    .build());

            if (slot >= 45) break;
        }

        inv.setItem(53, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&e&lSuche")
                .lore("&7Klicke, um nach einem",
                        "&7Gebäudename im Chat zu suchen.",
                        getBuildingSearchQuery(player).isBlank()
                                ? "&8Aktuell: keine Filter"
                                : "&7Aktuell: &e" + getBuildingSearchQuery(player))
                .build());

        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name("&7Zurueck")
                .build());

        player.openInventory(inv);
    }

    // --- My Buildings GUI ---

    public void openMyBuildingsGui(Player player, Village village) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.BUILDINGS,
                village.getId().toString() + ":my");
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color("&6&lMeine Gebäude"));

        BuildingService buildingService = plugin.getBuildingService();
        int slot = 0;
        for (VillageBuilding building : village.getBuildings()) {
            BuildingDefinition def = getDefinition(building.getTypeKey());
            if (def == null) continue;

            boolean underConstruction = buildingService != null && buildingService.isUnderConstruction(building.getId());
            String status = building.isCompleted() ? "&aFertig" : underConstruction ? "&eAktive Baustelle" : "&cIm Bau";
            int level = building.getLevel();
            String title = def.getName();
            String desc = def.getDescription();
            Material icon = def.getIcon();
            int maxLevel = def.getMaxUpgradeTier();
            int capacity = def.getVillagerSlots();

            inv.setItem(slot++, new ItemBuilder(icon)
                    .name(title)
                    .lore(desc,
                            "&7",
                            "&7Status: " + status,
                            "&7Level: &e" + level + "/" + maxLevel,
                            "&7Kapazität: &e" + capacity + " Bewohner",
                            underConstruction ? "&7Aktive Baustelle: &eFortschritt läuft" : "",
                            "&7",
                            "&aKlicke für Optionen!")
                    .glow(building.isCompleted() || underConstruction)
                    .build());

            if (slot >= 45) break;
        }

        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name("&7Zurück")
                .build());

        player.openInventory(inv);
    }

    // --- Building Detail GUI (completed buildings) ---

    public void openBuildingDetailGui(Player player, Village village, UUID buildingId) {
        if (player == null || village == null || buildingId == null) return;

        VillageBuilding building = village.getBuildings().stream()
                .filter(b -> buildingId.equals(b.getId()))
                .findFirst()
                .orElse(null);
        if (building == null) return;

        BuildingDefinition def = getDefinition(building.getTypeKey());
        String title = getBuildingDisplayName(building, def);
        Material icon = def != null ? def.getIcon() : Material.BRICKS;

        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.BUILDING_DETAIL,
                village.getId().toString() + ":" + buildingId.toString());
        Inventory inv = Bukkit.createInventory(holder, 54,
                MessageUtil.color("&6&lGebäude: " + title));

        inv.setItem(4, new ItemBuilder(icon)
                .name("&6&l" + title)
                .lore("&7Level: &e" + building.getLevel(),
                        "&7Status: &aFertig",
                        building.hasCustomName() ? "&7Name: &e" + building.getCustomName() : "",
                        "&7",
                        "&7Wähle eine Option.")
                .build());

        // Options
        inv.setItem(15, new ItemBuilder(Material.NAME_TAG)
                .name("&e&lGebäude umbenennen")
                .lore("&7Tippe im Chat einen neuen",
                        "&7Namen für dieses Gebäude ein.")
                .build());

        inv.setItem(19, new ItemBuilder(Material.OAK_SIGN)
                .name("&e&lSchild beschriften")
                .lore("&7Bearbeite die Schild-Beschriftung",
                        "&7mit Platzhaltern (&e%building_type%&7, &e%owner%&7, &e%level%&7).")
                .build());

        // Upgrades button: reflect permission/level state and glow if newly unlocked
        boolean upgradeNewlyUnlocked = false;
        boolean upgradeAvailable = false;
        boolean upgradeLocked = false;
        String upgradeLockedReason = "";
        if (def != null) {
            int nextTier = building.getLevel() + 1;
            BuildingDefinition.UpgradeTier nextUpgrade = def.getUpgradeTier(nextTier);
            if (nextUpgrade != null) {
                String upgradeKey = def.getId() + ":" + nextTier;
                upgradeNewlyUnlocked = isNewlyUnlocked(player.getUniqueId(), upgradeKey);
                // Check level requirement
                if (village.getLevel() < nextUpgrade.getRequiredVillageLevel()) {
                    upgradeLocked = true;
                    upgradeLockedReason = "&cDorflevel " + nextUpgrade.getRequiredVillageLevel() + " benötigt";
                }
                // Check permission
                String upgPerm = nextUpgrade.getPermission();
                if (!upgradeLocked && upgPerm != null && !upgPerm.isBlank()
                        && !player.hasPermission(upgPerm)
                        && !player.hasPermission("village.admin")) {
                    upgradeLocked = true;
                    upgradeLockedReason = "&cFehlende Berechtigung";
                }
                upgradeAvailable = !upgradeLocked;
            } else {
                upgradeLocked = true;
                upgradeLockedReason = "&7Maximales Level erreicht";
            }
        }
        Material upgradeMat = upgradeLocked ? Material.GRAY_STAINED_GLASS_PANE : Material.ANVIL;
        inv.setItem(21, new ItemBuilder(upgradeMat)
                .name((upgradeNewlyUnlocked ? "&e&l✦ " : (upgradeAvailable ? "&a&l" : "&7")) + "Upgrades")
                .lore(upgradeNewlyUnlocked ? "&e&lNEU freigeschaltet!" : "",
                        upgradeLocked ? upgradeLockedReason : "&7Gebäude-Upgrades kaufen.",
                        upgradeAvailable ? "&7Klicke zum Upgraden." : "")
                .glow(upgradeNewlyUnlocked && upgradeAvailable)
                .build());

        inv.setItem(23, new ItemBuilder(Material.PLAYER_HEAD)
                .name("&b&lWhitelist")
                .lore("&7Linksklick: Mitglieder-Untermenue oeffnen.",
                        "&7Rechtsklick: Modus toggeln (" + (building.isAccessAllMembers() ? "&aAlle" : "&eWhitelist") + "&7).")
                .build());

        inv.setItem(25, new ItemBuilder(Material.OAK_HANGING_SIGN)
                .name("&6&lSchild verschieben")
                .lore("&7Wähle einen Block per Rechtsklick,",
                        "&7um das Schild dort zu platzieren.")
                .build());

        if (building.isSignHidden()) {
            inv.setItem(37, new ItemBuilder(Material.LIME_DYE)
                    .name("&a&lSchild einblenden")
                    .lore("&7Platziert das Schild wieder (toggle).",
                            "&eWarnung&7, falls dort ein Block steht (Koordinate im Chat).")
                    .build());
        } else {
            inv.setItem(37, new ItemBuilder(Material.GRAY_DYE)
                    .name("&7&lSchild ausblenden")
                    .lore("&7Entfernt das Schild aus der Welt (toggle).",
                            "&7Menü bleibt über das Dorfmenü erreichbar.")
                    .build());
        }

        // Dorfzentrum is the village's foundation — no demolish button
        if (!"dorfzentrum".equals(building.getTypeKey())) {
            inv.setItem(43, new ItemBuilder(Material.TNT)
                    .name("&c&lAbreißen")
                    .lore("&7Linksklick: Nur Datensatz/Region/Schild entfernen.",
                            "&7Rechtsklick: Gebäude-Blöcke ebenfalls abbauen.")
                    .build());
        }

        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name("&7Zurück")
                .build());

        player.openInventory(inv);
    }

    public void openBuildingWhitelistGui(Player player, Village village, UUID buildingId) {
        VillageBuilding building = village.getBuildings().stream()
                .filter(b -> buildingId.equals(b.getId()))
                .findFirst().orElse(null);
        if (building == null) return;
        BuildingDefinition def = getDefinition(building.getTypeKey());
        String title = def != null ? def.getName() : building.getTypeKey();
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.BUILDING_WHITELIST,
                village.getId() + ":" + buildingId);
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color("&6&lWhitelist: " + title));

        List<UUID> memberIds = new ArrayList<>(village.getMembers().keySet());
        memberIds.sort(Comparator.comparing(id -> {
            String n = Bukkit.getOfflinePlayer(id).getName();
            return n != null ? n.toLowerCase() : id.toString();
        }));
        int slot = 0;
        for (UUID memberId : memberIds) {
            if (slot >= 45) break;
            String name = Bukkit.getOfflinePlayer(memberId).getName();
            if (name == null) name = memberId.toString().substring(0, 8);
            boolean allowed = building.isAccessAllMembers() || building.getAccessList().contains(memberId);
            inv.setItem(slot++, new ItemBuilder(Material.PLAYER_HEAD)
                    .name((allowed ? "&a" : "&7") + name)
                    .lore("&7Status: " + (allowed ? "&aErlaubt" : "&cBlockiert"),
                            "&7Klick: in Whitelist toggeln.")
                    .build());
        }
        inv.setItem(49, new ItemBuilder(Material.ARROW).name("&7Zurueck").build());
        player.openInventory(inv);
    }

    // --- Upgrades GUI ---

    public void openUpgradesGui(Player player, Village village) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.UPGRADES,
                village.getId().toString());
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color("&6&lUpgrades"));

        int slot = 0;
        for (Map.Entry<String, UpgradeType> entry : configManager.getUpgradeTypes().entrySet()) {
            UpgradeType type = entry.getValue();
            int currentLevel = village.getUpgradeLevel(entry.getKey());
            boolean maxed = currentLevel >= type.getMaxLevel();

            double globalCost = type.getGlobalCostPerLevel() * (currentLevel + 1);
            double localCost = type.getLocalCostPerLevel() * (currentLevel + 1);
            int pointsCost = type.getPointsPerLevel() * (currentLevel + 1);

            boolean levelLocked = village.getLevel() < type.getRequiredVillageLevel();
            String levelRequireLine = levelLocked
                    ? "&c&lDorflevel " + type.getRequiredVillageLevel() + " erforderlich!"
                    : "&aDorflevel " + type.getRequiredVillageLevel() + " ✔";

            inv.setItem(slot++, new ItemBuilder(levelLocked ? Material.BARRIER : type.getIcon())
                    .name(type.getDisplayName() + " &7[&e" + currentLevel + "&7/&e" + type.getMaxLevel() + "&7]")
                    .lore(type.getDescription(),
                            "&7",
                            levelRequireLine,
                            "&7",
                            maxed ? "&aMax Level erreicht!" : "&7Kosten global: &e" + vaultHook.format(globalCost),
                            maxed ? "" : "&7Kosten lokal: &e" + vaultHook.format(localCost),
                            maxed ? "" : "&7Punkte: &e" + pointsCost,
                            "&7",
                            levelLocked ? "&cNoch nicht verfügbar." : (maxed ? "" : "&aKlicke zum Kaufen!"))
                    .glow(currentLevel > 0 && !levelLocked)
                    .build());

            if (slot >= 45) break;
        }

        inv.setItem(45, new ItemBuilder(Material.NAME_TAG)
                .name("&d&lRollen freischalten")
                .lore("&7Schaltet HR, Baumeister, Builder,", "&7Haendler und Trainer als Rollen frei.")
                .build());

        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name("&7Zurueck")
                .build());

        player.openInventory(inv);
    }

    public void openRoleUpgradesGui(Player player, Village village, com.example.village.service.UpgradeService upgradeService) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.ROLE_UPGRADES, village.getId().toString());
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color("&6&lRollen-Upgrades"));
        int slot = 0;
        for (com.example.village.service.UpgradeService.RoleUnlock unlock : upgradeService.getRoleUnlocks()) {
            boolean unlocked = village.getUpgradeLevel(unlock.key()) > 0;
            inv.setItem(slot++, new ItemBuilder(unlock.icon())
                    .name((unlocked ? "&a" : "&e") + unlock.displayName())
                    .lore("&7Upgrade-Key: &f" + unlock.key(),
                            unlocked ? "&aFreigeschaltet" : "&7Kosten global: &e" + vaultHook.format(unlock.globalMoneyCost()),
                            unlocked ? "" : "&7Kosten lokal: &e" + String.format(java.util.Locale.US, "%.2f", unlock.localMoneyCost()),
                            unlocked ? "" : "&7Punkte: &e" + unlock.pointsCost(),
                            unlocked ? "" : "&aKlick zum Freischalten")
                    .build());
        }
        inv.setItem(49, new ItemBuilder(Material.ARROW).name("&7Zurueck").build());
        player.openInventory(inv);
    }

    public void openMemberRolesGui(Player player, Village village, UUID memberId, com.example.village.service.UpgradeService upgradeService) {
        VillageMember member = village.getMember(memberId);
        if (member == null) return;
        String name = Bukkit.getOfflinePlayer(memberId).getName();
        if (name == null) name = memberId.toString().substring(0, 8);
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.MEMBER_ROLES,
                village.getId() + ":" + memberId);
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color("&6&lRollen: " + name));

        VillageRole[] configurable = {
                VillageRole.HR, VillageRole.BAUMEISTER, VillageRole.BUILDER, VillageRole.HAENDLER, VillageRole.TRAINER
        };
        int slot = 10;
        for (VillageRole role : configurable) {
            boolean enabled = member.hasRole(role);
            String upg = role.upgradeKey();
            boolean unlocked = upg == null || village.getUpgradeLevel(upg) > 0;
            Material mat = unlocked ? (enabled ? Material.LIME_DYE : Material.GRAY_DYE) : Material.BARRIER;
            com.example.village.service.UpgradeService.RoleUnlock unlock = null;
            if (upgradeService != null && upg != null) {
                unlock = upgradeService.getRoleUnlocks().stream()
                        .filter(r -> r.key().equalsIgnoreCase(upg))
                        .findFirst().orElse(null);
            }
            ItemBuilder builder = new ItemBuilder(mat)
                    .name((enabled ? "&a" : "&7") + role.getDisplayName());
            if (unlocked) {
                builder.lore("&7Klick: Rolle toggeln.");
            } else {
                builder.lore("&cNicht freigeschaltet.",
                        unlock != null ? "&7Kosten global: &e" + vaultHook.format(unlock.globalMoneyCost()) : "&7Kosten global: &e?",
                        unlock != null ? "&7Kosten lokal: &e" + String.format(java.util.Locale.US, "%.2f", unlock.localMoneyCost()) : "&7Kosten lokal: &e?",
                        unlock != null ? "&7Punkte: &e" + unlock.pointsCost() : "&7Punkte: &e?",
                        "&aKlicke zum Freischalten.");
            }
            inv.setItem(slot, builder.build());
            slot += 2;
        }
        if (!village.isFounder(memberId)) {
            inv.setItem(40, new ItemBuilder(Material.NETHER_STAR)
                    .name("&6&lGruender uebergeben")
                    .lore("&7Nur der aktuelle Gruender kann", "&7die Gruender-Rolle uebergeben.")
                    .build());
        }
        inv.setItem(49, new ItemBuilder(Material.ARROW).name("&7Zurueck").build());
        player.openInventory(inv);
    }

    // --- Member Manage GUI ---

    public void openMemberManageGui(Player player, Village village, UUID memberId) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.MEMBERS,
                village.getId().toString() + ":" + memberId.toString());
        Inventory inv = Bukkit.createInventory(holder, 27, MessageUtil.color("&6&lMitglied verwalten"));

        VillageMember member = village.getMembers().get(memberId);
        if (member == null) { player.closeInventory(); return; }

        String name = Bukkit.getOfflinePlayer(memberId).getName();
        if (name == null) name = memberId.toString().substring(0, 8);

        // Info
        inv.setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
                .name("&a" + name)
                .lore("&7Rolle: &e" + member.getRole().getDisplayName())
                .build());

        // Entfernen
        inv.setItem(11, new ItemBuilder(Material.RED_WOOL)
                .name("&c&lEntfernen")
                .lore("&7Mitglied aus dem Dorf entfernen.")
                .build());

        // Rolle ändern - nur wenn nicht Founder
        if (!village.isFounder(memberId)) {
            inv.setItem(13, new ItemBuilder(Material.BOOK)
                    .name("&e&lRollen")
                    .lore("&7Oeffnet Rollen-Untermenue.")
                    .build());
        }

        inv.setItem(22, new ItemBuilder(Material.ARROW)
                .name("&7Zurueck")
                .build());

        player.openInventory(inv);
    }

    // --- Villagers GUI ---

    public void openVillagersGui(Player player, Village village) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.VILLAGERS,
                village.getId().toString());
        int maxVillagers = village.getMaxVillagers(
                configManager.getBaseMaxVillagers(),
                configManager.getUpgradeVillagersPerLevel());
        Inventory inv = Bukkit.createInventory(holder, 54,
                MessageUtil.color("&6&lDorfbewohner &7(" + village.getVillagers().size()
                        + "/" + maxVillagers + ")"));

        int slot = 0;
        for (CustomVillager villager : village.getVillagers()) {
            VillagerProfession profession = configManager.getProfession(villager.getProfessionKey());
            Material icon = profession != null ? profession.getIcon() : Material.VILLAGER_SPAWN_EGG;
            String profName = profession != null ? profession.getDisplayName() : villager.getProfessionKey();

            inv.setItem(slot++, new ItemBuilder(icon)
                    .name("&a" + villager.getName())
                    .lore("&7Beruf: " + profName,
                            "&7Level: &e" + villager.getLevel(),
                            "&7Hunger: &e" + String.format("%.0f", villager.getNeedValue(
                                    com.example.village.model.VillagerNeed.HUNGER)) + "%",
                            "&7Zufriedenheit: &e" + String.format("%.0f", villager.getNeedValue(
                                    com.example.village.model.VillagerNeed.HAPPINESS)) + "%",
                            "&7Geld: &e" + String.format("%.2f", villager.getWallet()),
                            "&7",
                            "&aLinksklick: Details",
                            "&6Rechtsklick: Temporär anzeigen")
                    .build());

            if (slot >= 45) break;
        }

        // Spawn new villager
        if (village.getVillagers().size() < maxVillagers) {
            inv.setItem(45, new ItemBuilder(Material.LIME_DYE)
                    .name("&a&lNeuen Dorfbewohner einladen")
                    .lore("&7Klicke um einen neuen Dorfbewohner",
                            "&7mit einem Beruf einzuladen.")
                    .build());
        }

        // Villager display / glow button (slot 48)
        int glowMode = villagerGlowModes.getOrDefault(player.getUniqueId(), 0);
        if (player.hasPermission("village.villager.show.temp") || player.hasPermission("village.villager.show.on")) {
            Material glowMaterial = switch (glowMode) {
                case 1 -> Material.LIGHT_BLUE_WOOL;        // TEMP
                case 2 -> Material.LIME_WOOL;              // ON
                default -> Material.GRAY_WOOL;             // OFF
            };
            String glowState = switch (glowMode) {
                case 1 -> "&6Temporär (15s)";
                case 2 -> "&2Dauerhaft";
                default -> "&7Aus";
            };
            inv.setItem(48, new ItemBuilder(glowMaterial)
                    .name("&a&lDorfbewohner anzeigen")
                    .lore("&7Status: " + glowState,
                            "&7Klick: Zwischen Modi umschalten",
                            "&7",
                            "&7Leuchteffekt mit Glowing")
                    .build());
        }

        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name("&7Zurueck")
                .build());

        player.openInventory(inv);
    }

    // --- Members GUI ---

    public void openMembersGui(Player player, Village village) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.MEMBERS,
                village.getId().toString());
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color("&6&lMitglieder"));

        VillageMember viewer = village.getMember(player.getUniqueId());
        boolean canManage = viewer != null && viewer.canManageMembers();

        int slot = 0;
        int maxMemberSlotExclusive = canManage ? 36 : 45;
        List<Map.Entry<java.util.UUID, VillageMember>> members = new ArrayList<>(village.getMembers().entrySet());
        members.sort(Comparator.comparing(entry -> {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            return name != null ? name.toLowerCase() : entry.getKey().toString();
        }));

        for (Map.Entry<java.util.UUID, VillageMember> entry : members) {
            VillageMember member = entry.getValue();
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = entry.getKey().toString().substring(0, 8);

            inv.setItem(slot++, new ItemBuilder(Material.PLAYER_HEAD)
                    .name("&a" + name)
                    .lore("&7Rolle: &e" + member.getRole().getDisplayName(),
                            "&7Beigetreten: &e" + new java.text.SimpleDateFormat("dd.MM.yyyy")
                                    .format(new java.util.Date(member.getJoinedAt())))
                    .build());

            if (slot >= maxMemberSlotExclusive) break;
        }

        if (canManage) {
            int requestSlot = 36;
            List<Map.Entry<java.util.UUID, VillageJoinRequest>> requests = new ArrayList<>(village.getJoinRequests().entrySet());
            requests.sort(Comparator.comparingLong(entry -> entry.getValue().getRequestedAt()));

            for (Map.Entry<java.util.UUID, VillageJoinRequest> entry : requests) {
                if (requestSlot > 44) break;
                String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (name == null) name = entry.getKey().toString().substring(0, 8);

                inv.setItem(requestSlot++, new ItemBuilder(Material.WRITABLE_BOOK)
                        .name("&eAnfrage: &f" + name)
                        .lore("&7Beitrittsanfrage zum Dorf",
                                "&7",
                                "&aLinksklick: Annehmen",
                                "&cRechtsklick: Ablehnen")
                        .glow(true)
                        .build());
            }

            inv.setItem(45, new ItemBuilder(Material.BOOK)
                    .name("&6&lOffene Anfragen")
                    .lore("&7Aktuell offen: &e" + village.getJoinRequests().size(),
                            "&7Aelteste und Gruender koennen",
                            "&7hier Anfragen bearbeiten.")
                    .build());
        }

        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name("&7Zurueck")
                .build());

        player.openInventory(inv);
    }

    // --- Villager Detail GUI ---

    public void openVillagerDetailGui(Player player, Village village, CustomVillager villager) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.VILLAGER_DETAIL,
                village.getId().toString() + ":" + villager.getId().toString());
        VillagerProfession profession = configManager.getProfession(villager.getProfessionKey());
        Inventory inv = Bukkit.createInventory(holder, 54,
                MessageUtil.color("&6&l" + villager.getName()));

        if (villager.getNutrientLevels().isEmpty()) {
            ConfigurationSection nutrients = configManager.getVillagerNutrientsSection();
            double hungerFallback = Math.max(0.0, Math.min(100.0, villager.getNeedValue(com.example.village.model.VillagerNeed.HUNGER)));
            if (nutrients != null) {
                int capacity = configManager.getVillagerNutrientStorageCapacity();
                for (String nutrientKey : nutrients.getKeys(false)) {
                    villager.setNutrientCapacity(nutrientKey, capacity);
                    villager.setNutrientLevel(nutrientKey, capacity * (hungerFallback / 100.0));
                }
            }
        }

        // Info
        StringBuilder nutrientLore = new StringBuilder();
        var nutritionService = plugin.getVillagerNutritionService();
        if (!villager.getNutrientLevels().isEmpty()) {
            for (Map.Entry<String, Double> entry : villager.getNutrientLevels().entrySet()) {
                double capacity = villager.getNutrientCapacity(entry.getKey());
                double percent = capacity > 0 ? (entry.getValue() / capacity) * 100.0 : 0.0;
                String label = nutritionService != null
                        ? nutritionService.getNutrientDisplayName(entry.getKey())
                        : entry.getKey();
                String bar = nutritionService != null
                        ? nutritionService.formatNutrientBar(percent)
                        : String.format("%.0f%%", percent);
                nutrientLore.append("&7").append(label).append(": ").append(bar).append("&r\n");
            }
        } else {
            nutrientLore.append("&7Keine Nährstoffwerte gespeichert.&r\n");
        }
        String[] nutrientLines = java.util.Arrays.stream(nutrientLore.toString().split("\\n"))
                .filter(line -> !line.isBlank())
                .toArray(String[]::new);
        String[] loreLines = new String[]{
                "&7Beruf: " + (profession != null ? profession.getDisplayName() : "Unbekannt"),
                "&7Level: &e" + villager.getLevel(),
                "&7XP: &e" + String.format("%.1f", villager.getXp()),
                "&7Hunger (Nährstoffe): &e" + String.format("%.0f", villager.getNeedValue(com.example.village.model.VillagerNeed.HUNGER)) + "%",
                "",
        };
        String[] finalLore = java.util.stream.Stream.concat(java.util.Arrays.stream(loreLines), java.util.Arrays.stream(nutrientLines))
                .toArray(String[]::new);
        inv.setItem(4, new ItemBuilder(profession != null ? profession.getIcon() : Material.VILLAGER_SPAWN_EGG)
                .name("&a" + villager.getName())
                .lore(finalLore)
                .build());

        // Inventory
        inv.setItem(10, new ItemBuilder(Material.CHEST)
                .name("&aInventar")
                .lore("&7Items: &e" + villager.getInventory().size() + " Typen")
                .build());

        // Feed
        inv.setItem(12, new ItemBuilder(Material.BREAD)
                .name("&aFuettern")
                .lore("&7Hunger (Nährstoffe): &e" + String.format("%.0f",
                        villager.getNeedValue(com.example.village.model.VillagerNeed.HUNGER)) + "%",
                        "&7Nährstoffe werden aus dem Inventar des Spielers verwendet.",
                        "&7Klicke zum Fuettern.")
                .build());

        // Assign bed
        inv.setItem(20, new ItemBuilder(Material.RED_BED)
                .name("&aBett zuweisen")
                .lore("&7Linksklick auf ein Bett in einem",
                        "&7fertigen Wohnhaus.",
                        "&7",
                        villager.getAssignedBedBuildingId() != null
                                ? "&aAktuell zugewiesen"
                                : "&cKein Bett zugewiesen")
                .build());

        // Assign job
        inv.setItem(24, new ItemBuilder(Material.IRON_PICKAXE)
                .name("&aJob zuweisen")
                .lore("&7Linksklick auf einen Arbeitsblock",
                        "&7eines fertigen Gebaeudes.",
                        "&7",
                        villager.getAssignedJobBuildingId() != null
                                ? "&aAktuell ist ein Job zugewiesen."
                                : "&cNoch kein Job zugewiesen.")
                .build());

        // Profession menu
        inv.setItem(22, new ItemBuilder(Material.BOOK)
                .name(MessageUtil.text("ui.villager-menu.profession-menu", "&6Berufsmenu"))
                .lore(MessageUtil.text("ui.villager-menu.profession-menu-lore-1", "&7Job-spezifische Aktionen,"),
                        MessageUtil.text("ui.villager-menu.profession-menu-lore-2", "&7Lager und Auftraege."))
                .build());

        // Contracts
        inv.setItem(23, new ItemBuilder(Material.PAPER)
                .name(MessageUtil.text("ui.villager-menu.contracts", "&eAuftraege"))
                .lore(MessageUtil.text("ui.villager-menu.contracts-lore-1", "&7Einen Standardauftrag"),
                        MessageUtil.text("ui.villager-menu.contracts-lore-2", "&7zwischen Dorfbewohnern anlegen."))
                .build());

        // Job storage
        inv.setItem(25, new ItemBuilder(Material.CHEST)
                .name(MessageUtil.text("ui.villager-menu.job-storage", "&aJob-Lager"))
                .lore(MessageUtil.text("ui.villager-menu.job-storage-lore-1", "&7Zeigt das berufsbezogene Lager"),
                        MessageUtil.text("ui.villager-menu.job-storage-lore-2", "&7und die aktuelle Kapazitaet."))
                .build());

        // Fire from job
        inv.setItem(28, new ItemBuilder(Material.BARRIER)
                .name("&cJob kuendigen")
                .lore("&7Entlasse den Dorfbewohner",
                        "&7von seinem aktuellen Job.",
                        "&7",
                        !"none".equals(villager.getProfessionKey())
                                ? "&eKlicke zum Kuendigen."
                                : "&8Kein Job vorhanden.")
                .build());

        // Trade
        inv.setItem(14, new ItemBuilder(Material.EMERALD)
                .name("&aHandeln")
                .lore("&7Kaufe Items vom Dorfbewohner.")
                .build());

        // Remove
        inv.setItem(16, new ItemBuilder(Material.RED_DYE)
                .name("&cEntlassen")
                .lore("&7Entferne diesen Dorfbewohner.")
                .build());

        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name("&7Zurueck")
                .build());

        player.openInventory(inv);
    }

    // --- Villager Config GUI (legacy / alternate entry) ---

    public void openVillagerConfigGui(Player player, Village village, CustomVillager villager) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.VILLAGER_CONFIG,
                village.getId().toString() + ":" + villager.getId().toString());
        Inventory inv = Bukkit.createInventory(holder, 54,
                MessageUtil.color("&6&lZuweisungen: " + villager.getName()));

        // Info
        VillagerProfession profession = configManager.getProfession(villager.getProfessionKey());
        String profName = profession != null ? profession.getDisplayName() : "Keiner";
        inv.setItem(4, new ItemBuilder(Material.VILLAGER_SPAWN_EGG)
                .name("&a" + villager.getName())
                .lore("&7Beruf: " + profName,
                        "&7Level: &e" + villager.getLevel(),
                        "&7Bett: " + (villager.getAssignedBedBuildingId() != null ? "&aZugewiesen" : "&cKein Bett"),
                        "&7Job: " + (villager.getAssignedJobBuildingId() != null ? "&aZugewiesen" : "&cKein Job"))
                .build());

        // Assign bed
        inv.setItem(20, new ItemBuilder(Material.RED_BED)
                .name("&a&lBett zuweisen")
                .lore("&7Weise diesem Dorfbewohner",
                        "&7ein freies Bett zu.",
                        "&7",
                        villager.getAssignedBedBuildingId() != null
                                ? "&aAktuell zugewiesen"
                                : "&cKein Bett zugewiesen")
                .build());

        // Choose job
        inv.setItem(24, new ItemBuilder(Material.IRON_PICKAXE)
                .name("&a&lJob zuweisen")
                .lore("&7Weise diesem Dorfbewohner",
                        "&7einen Job zu.",
                        "&7",
                        "&7Aktueller Job: " + profName,
                        "&7",
                        "&eLinksklick auf Arbeitsblock im Gebaeude.")
                .build());

        // Fire from job
        inv.setItem(28, new ItemBuilder(Material.BARRIER)
                .name("&c&lJob kuendigen")
                .lore("&7Entlasse den Dorfbewohner",
                        "&7von seinem aktuellen Job.",
                        "&7",
                        !"none".equals(villager.getProfessionKey())
                                ? "&eKlicke zum Kuendigen."
                                : "&8Kein Job vorhanden.")
                .build());

        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name("&7Schliessen")
                .build());

        player.openInventory(inv);
    }

    // --- Building Direction Selection GUI ---

    public void openBuildingDirectionGui(Player player, Village village, String typeKey) {
        BuildingDefinition def = getDefinition(typeKey);
        if (def == null) return;

        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.BUILDING_DIRECTION,
                village.getId().toString() + ":" + typeKey);
        Inventory inv = Bukkit.createInventory(holder, 54,
                MessageUtil.color("&6&lRichtung: " + def.getName()));

        // Info
        inv.setItem(13, new ItemBuilder(def.getIcon())
                .name(def.getName())
                .lore("&7Waehle die Richtung der Eingangstuer.",
                        "&7",
                        "&7Kosten: &e" + vaultHook.format(def.getBuildMoneyGlobal()),
                        "&7Schematic: &e" + (def.getSchematic() != null ? def.getSchematic() : ""))
                .build());

        // North (slot 4)
        inv.setItem(4, new ItemBuilder(Material.BLUE_WOOL)
                .name("&b&lNorden (N)")
                .lore("&7Tuer zeigt nach Norden.")
                .build());

        // South (slot 22)
        inv.setItem(22, new ItemBuilder(Material.RED_WOOL)
                .name("&c&lSueden (S)")
                .lore("&7Tuer zeigt nach Sueden.")
                .build());

        // West (slot 12)
        inv.setItem(12, new ItemBuilder(Material.GREEN_WOOL)
                .name("&a&lWesten (W)")
                .lore("&7Tuer zeigt nach Westen.")
                .build());

        // East (slot 14)
        inv.setItem(14, new ItemBuilder(Material.YELLOW_WOOL)
                .name("&e&lOsten (E)")
                .lore("&7Tuer zeigt nach Osten.")
                .build());

        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name("&7Zurueck")
                .build());

        player.openInventory(inv);
    }

    // --- Building Categories GUI (Hierarchical) ---

    public void openBuildingCategoriesGui(Player player, Village village) {
        BuildingConfigLoader loader = plugin.getBuildingConfigLoader();
        List<BuildingConfigLoader.CategoryInfo> cats = loader != null ? loader.getCategories().stream().toList() : List.of();
        
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.BUILDING_CATEGORIES,
                village.getId().toString());
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color("&6&lGebäude"));

        int slot = 0;
        for (BuildingConfigLoader.CategoryInfo cat : cats) {
            if (slot >= 45) break;
            String catId = cat.getId();
            // Only show categories that have at least one building accessible to this player
            List<BuildingDefinition> accessible = loader.getByCategory(catId).stream()
                    .filter(d -> d.isShowInMenu() && hasAccessToBuilding(player, d))
                    .toList();
            if (accessible.isEmpty()) continue;
            long count = accessible.size();
            // Glow if any building in this category is newly unlocked for this player
            boolean hasNew = accessible.stream()
                    .anyMatch(d -> isNewlyUnlocked(player.getUniqueId(), d.getId()));
            inv.setItem(slot++, new ItemBuilder(cat.getIcon())
                    .name((hasNew ? "&e&l✦ " : "") + cat.getName())
                    .lore("&7Kategorie: &e" + cat.getName(),
                          "&7Verfügbare Gebäude: &e" + count,
                          hasNew ? "&e&lNEU Freigeschaltet!" : "")
                    .glow(hasNew)
                    .build());
        }

        // Back / Close button
        inv.setItem(49, new ItemBuilder(Material.ARROW).name("&7Zurueck").build());
        player.openInventory(inv);
    }

    public void openBuildingCategoryContentGui(Player player, Village village, String categoryId) {
        BuildingConfigLoader loader = plugin.getBuildingConfigLoader();
        if (loader == null) return;

        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.BUILDING_CATEGORIES,
                village.getId().toString() + ":" + categoryId);
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color("&6&l" + loader.getCategory(categoryId).getName()));

        int slot = 0;
        for (BuildingDefinition def : loader.getByCategory(categoryId)) {
            if (!def.isShowInMenu()) {
                // Show non-menu buildings (e.g. dorfzentrum) only if the village already has one
                VillageBuilding existing = village.getBuildings().stream()
                        .filter(b -> b.getTypeKey().equals(def.getId()) && b.isCompleted())
                        .findFirst().orElse(null);
                if (existing == null) continue;
                if (!matchesBuildingSearch(player, def, null)) continue;
                inv.setItem(slot++, new ItemBuilder(def.getIcon())
                        .name("&6&l" + def.getName())
                        .lore(def.getDescription(),
                                "&7",
                                "&7Level: &e" + existing.getLevel() + " / " + def.getMaxUpgradeTier(),
                                existing.hasCustomName() ? "&7Name: &e" + existing.getCustomName() : "",
                                "&7",
                                "&aKlicke: Verwalten")
                        .glow(true)
                        .build());
                if (slot >= 45) break;
                continue;
            }
            // Hide buildings the player has no permission for (unless admin)
            if (!hasAccessToBuilding(player, def)) continue;
            if (!matchesBuildingSearch(player, def, null)) continue;
            boolean levelOk = village.getLevel() >= def.getRequiredVillageLevel();
            long builtCount = village.getBuildings().stream().filter(b -> b.getTypeKey().equals(def.getId())).count();
            boolean isNew = levelOk && isNewlyUnlocked(player.getUniqueId(), def.getId());

            inv.setItem(slot++, new ItemBuilder(levelOk ? def.getIcon() : Material.GRAY_STAINED_GLASS_PANE)
                    .name((isNew ? "&e&l✦ " : "") + (levelOk ? def.getName() : "&8" + def.getId()))
                    .lore(def.getDescription(),
                            "&7",
                            isNew ? "&e&lNEU Freigeschaltet!" : "",
                            "&7Kosten: &e" + vaultHook.format(def.getBuildMoneyGlobal()),
                            "&7Level benoetigt: &e" + def.getRequiredVillageLevel(),
                            "&7Kapazitaet: &e" + def.getVillagerSlots() + " Bewohner",
                            "&7Gebaut: &e" + builtCount,
                            "&7",
                            levelOk ? "&aKlicke: Optionen für diesen Typ" : "&cLevel " + def.getRequiredVillageLevel() + " benoetigt!")
                    .glow(isNew || levelOk)
                    .build());

            if (slot >= 45) break;
        }

        inv.setItem(53, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&e&lSuche")
                .lore("&7Klicke, um nach einem",
                        "&7Gebäudename im Chat zu suchen.",
                        getBuildingSearchQuery(player).isBlank()
                                ? "&8Aktuell: keine Filter"
                                : "&7Aktuell: &e" + getBuildingSearchQuery(player))
                .build());

        inv.setItem(49, new ItemBuilder(Material.ARROW).name("&7Zurueck").build());
        player.openInventory(inv);
    }

    // --- Building Type Options GUI (Bauen / Verwalten) ---

    public void openBuildingTypeOptionsGui(Player player, Village village, String typeKey) {
        BuildingDefinition def = getDefinition(typeKey);
        if (def == null) return;

        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.BUILDING_TYPE_OPTIONS,
                village.getId().toString() + ":" + typeKey);
        Inventory inv = Bukkit.createInventory(holder, 27, 
                MessageUtil.color("&6&lOptionen: " + def.getName()));

        // Info
        inv.setItem(4, new ItemBuilder(def.getIcon())
                .name(def.getName())
                .lore(def.getDescription(),
                        "&7",
                        "&7Kosten: &e" + vaultHook.format(def.getBuildMoneyGlobal()),
                        "&7Level benoetigt: &e" + def.getRequiredVillageLevel(),
                        "&7Kapazitaet: &e" + def.getVillagerSlots() + " Bewohner")
                .build());

        // Build new building
        long builtCount = village.getBuildings().stream()
                .filter(b -> b.getTypeKey().equals(typeKey))
                .count();
        
        inv.setItem(11, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a&lNeues Gebäude bauen")
                .lore("&7Platziere ein neues Gebäude",
                        "&7dieser Art.",
                        "&7",
                        "&7Bereits gebaut: &e" + builtCount)
                .build());

        // Manage existing buildings
        if (builtCount > 0) {
            inv.setItem(15, new ItemBuilder(Material.CRAFTING_TABLE)
                    .name("&a&lGebäude verwalten")
                    .lore("&7Verwalte bereits gebaute",
                            "&7Gebäude dieser Art.",
                            "&7",
                            "&7Gebäude vorhanden: &e" + builtCount)
                    .build());
        }

        // Back
        inv.setItem(22, new ItemBuilder(Material.ARROW)
                .name("&7Zurueck")
                .build());

        player.openInventory(inv);
    }

    // --- Building Build Variants GUI (Schematic-Auswahl) ---

    public void openBuildingBuildVariantsGui(Player player, Village village, String typeKey) {
        BuildingDefinition def = getDefinition(typeKey);
        if (def == null) return;

        BuildingService buildingService = plugin.getBuildingService();
        if (buildingService == null) return;

        java.util.List<String> variants = buildingService.findSchematicVariations(typeKey);
        if (variants.isEmpty()) return;

        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.BUILDING_BUILD_VARIANTS,
                village.getId().toString() + ":" + typeKey);
        Inventory inv = Bukkit.createInventory(holder, 54,
                MessageUtil.color("&6&lVariante wählen: " + def.getName()));

        inv.setItem(4, new ItemBuilder(def.getIcon())
                .name(def.getName())
                .lore("&7Wähle eine Schematic-Variante für den Bau.",
                        "&7",
                        "&7Kosten: &e" + vaultHook.format(def.getBuildMoneyGlobal()),
                        "&7Basis-Schematic: &e" + (def.getSchematic() != null ? def.getSchematic() : "-"))
                .build());

        int slot = 0;
        for (String variant : variants) {
            if (slot >= 45) break;
            String displayName = variant;
            if (variant.equals(def.getSchematic())) {
                displayName = variant + " &7(Basis)";
            }
            inv.setItem(slot++, new ItemBuilder(Material.PAPER)
                    .name("&e" + displayName)
                    .lore("&7Klicke zum Auswählen dieser Variante.")
                    .build());
        }

        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name("&7Zurueck")
                .build());

        player.openInventory(inv);
    }

    // --- Building Manage Type GUI (Gebäude einer Art verwalten) ---

    public void openBuildingManageTypeGui(Player player, Village village, String typeKey) {
        BuildingDefinition def = getDefinition(typeKey);
        if (def == null) return;

        BuildingService buildingService = plugin.getBuildingService();
        List<VillageBuilding> visibleBuildings = getVisibleBuildingsForManageType(player, village, typeKey);

        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.BUILDING_MANAGE_TYPE,
                village.getId().toString() + ":" + typeKey);
        Inventory inv = Bukkit.createInventory(holder, 54,
                MessageUtil.color("&6&lVerwalten: " + def.getName()));

        int slot = 0;
        for (VillageBuilding building : visibleBuildings) {
            if (slot >= 45) break;

            boolean inProgress = !building.isCompleted();
            boolean paused = inProgress && buildingService != null && buildingService.isBuildingPaused(building.getId());
            Material icon = inProgress ? Material.CLOCK : def.getIcon();
            String status = building.isCompleted() ? "&aFertig" : (paused ? "&6Pausiert" : "&eIm Bau");
            int level = building.getLevel();
            String displayName = building.hasCustomName() ? building.getCustomName() : def.getName() + " #" + (slot + 1);

            inv.setItem(slot++, new ItemBuilder(icon)
                    .name(displayName)
                    .lore(def.getDescription(),
                            building.hasCustomName() ? "&7Typ: &e" + def.getName() : "",
                            "&7",
                            "&7Status: " + status,
                            "&7Level: &e" + level + "/" + def.getMaxUpgradeTier(),
                            "&7Kapazität: &e" + def.getVillagerSlots() + " Bewohner",
                            "&7",
                            inProgress ? "&8Diese Baustelle ist im Menü nicht klickbar." : "&aKlicke für Schildmenü!")
                    .glow(inProgress && !paused)
                    .build());
        }

        inv.setItem(53, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&e&lSuche")
                .lore("&7Klicke, um nach einem",
                        "&7Gebäudename im Chat zu suchen.",
                        getBuildingSearchQuery(player).isBlank()
                                ? "&8Aktuell: keine Filter"
                                : "&7Aktuell: &e" + getBuildingSearchQuery(player))
                .build());

        // Unique / non-buildable-from-menu types (e.g. dorfzentrum) cannot be built again
        if (def.isShowInMenu()) {
            inv.setItem(45, new ItemBuilder(Material.EMERALD_BLOCK)
                    .name("&a&lNeues Gebäude bauen")
                    .lore("&7Richtung wählen und platzieren.",
                            "&7Typ: &e" + def.getName())
                    .build());
        }

        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name("&7Zurueck")
                .build());

        player.openInventory(inv);
    }

    /**
     * Erstellt ein Bell-Item für das Hauptmenü mit Levelup-Informationen.
     */
    private ItemStack createBellItemForMain(Player player, Village village) {
        com.example.village.service.LevelService levelService = plugin.getLevelService();
        boolean hasLevelup = levelService != null
                && levelService.getLevelupFailureReason(village, player) == null
                && levelService.isLevelUpAvailable(village);

        Material material = Material.BELL;
        ItemBuilder builder = new ItemBuilder(material)
                .name("&6&l" + village.getName());
        if (hasLevelup) builder.glow(true);
        
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("&7Level: &e" + village.getLevel());
        lore.add("&7Punkte: &e" + village.getPoints());
        lore.add("&7Mitglieder: &e" + village.getMembers().size());
        lore.add("&7Gebaeude: &e" + village.getBuildings().size());
        lore.add("&7Dorfbewohner: &e" + village.getVillagers().size());
        
        if (hasLevelup) {
            lore.add("");
            lore.add("&a§l✦ LEVELUP VERFUEGBAR ✦");
            lore.add("&7Klick für mehr Informationen!");
        }
        
        builder.lore(lore.toArray(new String[0]));
        if (hasLevelup) builder.glow(true);
        
        return builder.build();
    }

    // Villager Glow Helper Methods
    public int getVillagerGlowMode(UUID playerId) {
        return villagerGlowModes.getOrDefault(playerId, 0);
    }

    public void setVillagerGlowMode(UUID playerId, int mode) {
        if (mode < 0 || mode > 2) return;  // 0=OFF, 1=TEMP, 2=ON
        villagerGlowModes.put(playerId, mode);
    }

    public void cycleVillagerGlowMode(UUID playerId) {
        int currentMode = villagerGlowModes.getOrDefault(playerId, 0);
        setVillagerGlowMode(playerId, (currentMode + 1) % 3);
    }
}
