package com.example.village.listener;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.gui.GuiManager;
import com.example.village.gui.VillageMenuHolder;
import com.example.village.hook.VaultHook;
import com.example.village.model.BuildingDefinition;
import com.example.village.model.CustomVillager;
import com.example.village.model.PendingBlockSelection;
import com.example.village.model.UpgradeType;
import com.example.village.model.Village;
import com.example.village.model.VillageBuilding;
import com.example.village.model.VillageJoinRequest;
import com.example.village.model.VillageMember;
import com.example.village.model.VillageRole;
import com.example.village.model.VillageRelationState;
import com.example.village.model.VillageRelationType;
import com.example.village.model.VillagerProfession;
import com.example.village.service.BorderPreviewService;
import com.example.village.service.BorderService;
import com.example.village.service.BuildingService;
import com.example.village.service.BuildingConfigLoader;
import com.example.village.service.UpgradeService;
import com.example.village.service.VillageManager;
import com.example.village.integration.CurrencyIntegrationManager;
import com.example.village.model.TradeOffer;
import com.example.village.model.VillagerInventory;
import com.example.village.service.VillagerService;
import com.example.village.trading.VillagerLocalTradeUI;
import com.example.village.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public final class GuiClickListener implements Listener {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VillageManager villageManager;
    private final GuiManager guiManager;
    private final BorderService borderService;
    private final BuildingService buildingService;
    private final UpgradeService upgradeService;
    private final VillagerService villagerService;
    private final CurrencyIntegrationManager currencySystem;
    private final InventoryClickEventListener tradeInventoryListener;
    private final VaultHook vaultHook;
    private final BorderPreviewService previewService;

    // Tracks players who are waiting for name input (ConcurrentHashMap for async chat thread safety)
    private final Map<UUID, org.bukkit.Location> pendingFoundings = new ConcurrentHashMap<>();

    // Tracks bell locations from BellInteractListener (set before opening founding GUI)
    private final Map<UUID, org.bukkit.Location> pendingBellLocations = new ConcurrentHashMap<>();

    // Tracks players who are waiting for a new village name (rename)
    private final Map<UUID, UUID> pendingRenames = new ConcurrentHashMap<>();

    // Tracks players who are waiting to click a building block for job assignment
    private final Map<UUID, PendingJobAssignment> pendingJobAssignments = new ConcurrentHashMap<>();

    // Tracks players who are selecting a villager for job assignment from the building menu
    private final Map<UUID, UUID> pendingVillagerAssignments = new ConcurrentHashMap<>();

    // Tracks players who are in building direction selection flow
    private final Map<UUID, PendingBuildingPlacement> pendingBuildingPlacements = new ConcurrentHashMap<>();

    // Tracks players who are editing a completed building sign template via chat
    private final Map<UUID, PendingSignEdit> pendingSignEdits = new ConcurrentHashMap<>();

    // Tracks players who are renaming a building via chat
    private final Map<UUID, UUID> pendingBuildingRenames = new ConcurrentHashMap<>();

    // Tracks players who are entering a building search query via chat
    private final Map<UUID, Boolean> pendingBuildingSearches = new ConcurrentHashMap<>();

    public GuiClickListener(VillagePlugin plugin, VillageConfigManager configManager,
                            VillageManager villageManager, GuiManager guiManager,
                            BorderService borderService, BuildingService buildingService,
                            UpgradeService upgradeService, VillagerService villagerService,
                            VaultHook vaultHook, BorderPreviewService previewService,
                            CurrencyIntegrationManager currencySystem,
                            InventoryClickEventListener tradeInventoryListener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.villageManager = villageManager;
        this.guiManager = guiManager;
        this.borderService = borderService;
        this.buildingService = buildingService;
        this.upgradeService = upgradeService;
        this.villagerService = villagerService;
        this.currencySystem = currencySystem;
        this.tradeInventoryListener = tradeInventoryListener;
        this.vaultHook = vaultHook;
        this.previewService = previewService;
    }

    public Map<UUID, org.bukkit.Location> getPendingFoundings() {
        return pendingFoundings;
    }

    public Map<UUID, org.bukkit.Location> getPendingBellLocations() {
        return pendingBellLocations;
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

    private String schematicName(String typeKey) {
        return buildingService.getSchematicNameForType(typeKey);
    }

    public Map<UUID, UUID> getPendingRenames() {
        return pendingRenames;
    }

    public Map<UUID, UUID> getPendingBuildingRenames() {
        return pendingBuildingRenames;
    }

    public Map<UUID, Boolean> getPendingBuildingSearches() {
        return pendingBuildingSearches;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        
        // Handle building menu clicks
        if (inv.getHolder() instanceof com.example.village.gui.BuildingMenuGui buildingMenu) {
            handleBuildingMenuClick(event, buildingMenu);
            return;
        }
        
        // Handle villager inventory clicks
        if (inv.getHolder() instanceof com.example.village.gui.VillagerInventoryGui villagerInventory) {
            handleVillagerInventoryClick(event, villagerInventory);
            return;
        }
        
        if (!(inv.getHolder() instanceof VillageMenuHolder holder)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        int slot = event.getRawSlot();

        switch (holder.getMenuType()) {
            case FOUNDING -> handleFoundingClick(player, slot);
            case MAIN -> handleMainClick(player, slot, holder.getExtraData());
            case RELATIONS -> handleRelationsClick(player, slot, holder.getExtraData());
            case RELATION_TARGETS -> handleRelationTargetsClick(player, slot, holder.getExtraData());
            case BORDER_SELECTION -> handleBorderSelectionClick(player, event, slot, holder.getExtraData());
            case BUILDINGS -> handleBuildingsClick(player, slot, holder.getExtraData());
            case BUILDING_CATEGORIES -> handleBuildingCategoriesClick(player, slot, holder.getExtraData());
            case BUILDING_TYPE_OPTIONS -> handleBuildingTypeOptionsClick(player, slot, holder.getExtraData());
            case BUILDING_BUILD_VARIANTS -> handleBuildingBuildVariantsClick(player, slot, holder.getExtraData());
            case BUILDING_MANAGE_TYPE -> handleBuildingManageTypeClick(player, slot, holder.getExtraData());
            case BUILDING_DETAIL -> handleBuildingDetailClick(player, event, slot, holder.getExtraData());
            case BUILDING_WHITELIST -> handleBuildingWhitelistClick(player, slot, holder.getExtraData());
            case UPGRADES -> handleUpgradesClick(player, slot, holder.getExtraData());
            case ROLE_UPGRADES -> handleRoleUpgradesClick(player, slot, holder.getExtraData());
            case MEMBER_ROLES -> handleMemberRolesClick(player, slot, holder.getExtraData());
            case VILLAGERS -> handleVillagersClick(player, slot, holder.getExtraData());
            case VILLAGER_DETAIL -> handleVillagerDetailClick(player, slot, holder.getExtraData());
            case VILLAGER_CONFIG -> handleVillagerConfigClick(player, slot, holder.getExtraData());
            case BUILDING_DIRECTION -> handleBuildingDirectionClick(player, slot, holder.getExtraData());
            case MEMBERS -> handleMembersClick(player, event, slot, holder.getExtraData());
            case QUESTS -> handleQuestsClick(player, slot, holder.getExtraData());
            case LEVELUP -> handleLevelupClick(player, slot);
            case CONFIRM -> handleConfirmClick(player, slot, holder.getExtraData());
            case BARRIER_FEEDBACK -> handleBarrierFeedbackClick(player, slot);
            default -> {}
        }
    }

    /**
     * Handles clicks in the building menu GUI.
     */
    private void handleBuildingMenuClick(InventoryClickEvent event, 
                                        com.example.village.gui.BuildingMenuGui buildingMenu) {
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        int slot = event.getRawSlot();
        com.example.village.gui.BuildingMenuGui.BuildingMenuAction action = buildingMenu.getActionForSlot(slot);
        BuildingService.BuildingSession session = buildingService.getSession(player.getUniqueId());
        Village sessionVillage = session != null ? session.getVillage() : null;
        boolean canBuildSite = sessionVillage == null || player.hasPermission("village.admin")
                || villageManager.canBuildOnSites(sessionVillage, player.getUniqueId());

        switch (action) {
            case TOGGLE_PREVIEW -> {
                if (!canBuildSite) { showBarrierFeedback(player, "&cDu darfst nicht an Baustellen arbeiten."); return; }
                buildingService.toggleBuildingPreview(player);
            }
            case PAUSE -> {
                if (!canBuildSite) { showBarrierFeedback(player, "&cDu darfst nicht an Baustellen arbeiten."); return; }
                buildingService.pauseBuilding(player);
                player.closeInventory();
            }
            case RESUME -> {
                if (!canBuildSite) { showBarrierFeedback(player, "&cDu darfst nicht an Baustellen arbeiten."); return; }
                buildingService.resumeBuilding(player);
                player.closeInventory();
            }
            case CONFIRM -> {
                if (!canBuildSite) { showBarrierFeedback(player, "&cDu darfst nicht an Baustellen arbeiten."); return; }
                boolean valid = buildingService.validateBuilding(player);
                if (valid) {
                    BuildingService.BuildingSession currentSession = buildingService.getSession(player.getUniqueId());
                    String buildingName = currentSession != null ? "Gebäude" : "Gebäude";
                    MessageUtil.send(player, configManager.getPrefix(),
                            configManager.message("building-completed")
                                    .replace("%building%", buildingName));
                } else {
                    MessageUtil.send(player, configManager.getPrefix(),
                            configManager.message("building-invalid"));
                }
                player.closeInventory();
            }
            case ASSIGN_VILLAGER -> {
                if (!canBuildSite) { showBarrierFeedback(player, "&cDu darfst nicht an Baustellen arbeiten."); return; }
                if (sessionVillage == null) {
                    MessageUtil.send(player, configManager.getPrefix(), "&cKein aktiver Bau-Abschnitt gefunden.");
                    return;
                }
                pendingVillagerAssignments.put(player.getUniqueId(), sessionVillage.getId());
                player.closeInventory();
                guiManager.openVillagersGui(player, sessionVillage);
                MessageUtil.send(player, configManager.getPrefix(),
                        "&eWähle einen Dorfbewohner aus, um ihn einem Job zuzuweisen.");
            }
            case CANCEL -> {
                if (sessionVillage != null && !player.hasPermission("village.admin")
                        && !villageManager.canManageBuildings(sessionVillage, player.getUniqueId())) {
                    showBarrierFeedback(player, "&cNur Baumeister/Gruender duerfen abbrechen.");
                    return;
                }
                buildingService.cancelBuilding(player);
                MessageUtil.send(player, configManager.getPrefix(), "&eBau abgebrochen.");
                player.closeInventory();
            }
            case ADMIN_FORCE_COMPLETE -> {
                if (!player.hasPermission("village.admin")) {
                    MessageUtil.send(player, configManager.getPrefix(), "&cKeine Berechtigung.");
                    return;
                }
                if (buildingService.forceCompleteBuilding(player)) {
                    MessageUtil.send(player, configManager.getPrefix(), "&aGebaeude sofort fertiggestellt.");
                } else {
                    MessageUtil.send(player, configManager.getPrefix(), "&cKonnte Bau nicht sofort fertigstellen.");
                }
                player.closeInventory();
            }
            case CLOSE -> {
                player.closeInventory();
            }
            case NONE -> {}
        }
    }

    private void handleFoundingClick(Player player, int slot) {
        if (slot == 11) {
            // Confirm - check requirements
            if (!checkFoundingRequirements(player)) return;

            // Retrieve bell location stored by BellInteractListener
            org.bukkit.Location bellLoc = pendingBellLocations.remove(player.getUniqueId());
            if (bellLoc == null) {
                bellLoc = player.getLocation();
            }

            pendingFoundings.put(player.getUniqueId(), bellLoc);
            player.closeInventory();
            MessageUtil.send(player, configManager.getPrefix(),
                    configManager.message("name-prompt"));
        } else if (slot == 15) {
            // Cancel - clean up stored bell location
            pendingBellLocations.remove(player.getUniqueId());
            player.closeInventory();
        }
    }

    private boolean checkFoundingRequirements(Player player) {
        // Check permission
        String perm = configManager.getFoundingPermission();
        if (!perm.isEmpty() && !player.hasPermission(perm)) {
            MessageUtil.send(player, configManager.getPrefix(),
                    configManager.message("no-permission"));
            return false;
        }

        // Check money
        double globalCost = configManager.getFoundingGlobalCost();
        if (globalCost > 0 && vaultHook.isAvailable()) {
            if (!vaultHook.has(player, globalCost)) {
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("upgrade-too-expensive"));
                return false;
            }
        }
        double localCost = configManager.getFoundingLocalCost();
        if (localCost > 0) {
            var villageAtLocation = villageManager.getVillageAtLocation(player.getLocation()).orElse(null);
            if (villageAtLocation == null) {
                MessageUtil.send(player, configManager.getPrefix(), "&cLokale Gruendungskosten koennen hier nicht bezahlt werden.");
                return false;
            }
            String localCurrencyId = plugin.getCurrencyService().getVillageCurrencyId(villageAtLocation);
            if (plugin.getCurrencyService().getBalance(player.getUniqueId(), localCurrencyId) < localCost) {
                MessageUtil.send(player, configManager.getPrefix(), configManager.message("upgrade-too-expensive"));
                return false;
            }
        }

        // Check items
        for (Map<String, Object> req : configManager.getFoundingItemRequirements()) {
            String matName = String.valueOf(req.get("material"));
            int amount = ((Number) req.get("amount")).intValue();
            Material mat = Material.matchMaterial(matName);
            if (mat == null) continue;

            if (!player.getInventory().contains(mat, amount)) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cDu brauchst " + amount + "x " + matName + "!");
                return false;
            }
        }

        return true;
    }

    private void handleMainClick(Player player, int slot, String data) {
        Village village = villageManager.getVillage(UUID.fromString(data)).orElse(null);
        if (village == null) { player.closeInventory(); return; }
        VillageMember viewer = village.getMember(player.getUniqueId());

        switch (slot) {
            case 4 -> {
                // Bell click - open levelup GUI
                com.example.village.service.LevelService levelService = plugin.getLevelService();
                if (levelService != null) {
                    com.example.village.gui.LevelupGui levelupGui = new com.example.village.gui.LevelupGui(
                            plugin, player, village, levelService, configManager, vaultHook);
                    levelupGui.open();
                }
            }
            case 19 -> guiManager.openUpgradesGui(player, village);
            case 21 -> {
                if (!player.hasPermission("village.admin") && !villageManager.canManageBuildings(village, player.getUniqueId())) {
                    showBarrierFeedback(player, "&cNur Gruender/Baumeister duerfen Gebaeude verwalten.");
                    return;
                }
                guiManager.openBuildingCategoriesGui(player, village);
            }
            case 23 -> {
                if (!player.hasPermission("village.admin") && viewer != null && !viewer.canTradeWithVillagers() && !viewer.canUpgradeVillagers()) {
                    showBarrierFeedback(player, "&cKeine Berechtigung fuer Dorfbewohner-Funktionen.");
                    return;
                }
                guiManager.openVillagersGui(player, village);
            }
            case 25 -> guiManager.openMembersGui(player, village);
            case 27 -> guiManager.openRelationsGui(player, village);
            case 37 -> guiManager.openBorderSelectionGui(player, village);
            case 39 -> guiManager.openVillageQuestsGui(player, village);
            
            case 43 -> {
                // Rename village
                if (!villageManager.canManageVillage(village, player.getUniqueId())
                        && !player.hasPermission("village.admin")) {
                    MessageUtil.send(player, configManager.getPrefix(),
                            configManager.message("no-permission"));
                    return;
                }
                pendingRenames.put(player.getUniqueId(), village.getId());
                player.closeInventory();
                MessageUtil.send(player, configManager.getPrefix(),
                        "&7Gib den neuen Dorfnamen im Chat ein. &8(abbrechen mit 'abbrechen')");
            }
            case 45 -> {
                if (!player.hasPermission("village.admin")) {
                    showBarrierFeedback(player, "&cNur Admins koennen Schematic-Tools nutzen.");
                    return;
                }
                player.closeInventory();
                MessageUtil.send(player, configManager.getPrefix(),
                        "&7Schematic-Hilfe geoeffnet. Nutze &e/village schematic tool&7.");
                player.performCommand("village schematic");
            }
            case 51 -> {
                if (village.isFounder(player.getUniqueId())) {
                    return;
                }
                if (!villageManager.removeMember(village, player.getUniqueId())) {
                    MessageUtil.send(player, configManager.getPrefix(),
                            "&cDu kannst das Dorf nicht verlassen.");
                    return;
                }
                player.closeInventory();
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("village-left").replace("%name%", village.getName()));
                if (plugin.getLightService() != null) {
                    plugin.getLightService().refreshPlayer(player);
                }
            }
            case 53 -> {
                // Delete village - open confirm dialog
                if (!villageManager.canDeleteVillage(village, player.getUniqueId())
                        && !player.hasPermission("village.admin")) {
                    MessageUtil.send(player, configManager.getPrefix(),
                            configManager.message("no-permission"));
                    return;
                }
                guiManager.openConfirmDeleteGui(player, village);
            }
            case 49 -> player.closeInventory();
        }
    }

    private void handleQuestsClick(Player player, int slot, String data) {
        Village village = villageManager.getVillage(UUID.fromString(data)).orElse(null);
        if (village == null) return;
        if (slot == 49) {
            guiManager.openMainGui(player, village);
        }
    }

    private void handleRelationsClick(Player player, int slot, String data) {
        Village village = villageManager.getVillage(UUID.fromString(data)).orElse(null);
        if (village == null) { player.closeInventory(); return; }

        switch (slot) {
            case 19 -> guiManager.openRelationTargetsGui(player, village, VillageRelationType.FRIENDSHIP);
            case 21 -> guiManager.openRelationTargetsGui(player, village, VillageRelationType.TRADE);
            case 23 -> guiManager.openRelationTargetsGui(player, village, VillageRelationType.WAR);
            case 25 -> guiManager.openRelationTargetsGui(player, village, VillageRelationType.CURFEW);
            case 22 -> guiManager.openMainGui(player, village);
            default -> {}
        }
    }

    private void handleRelationTargetsClick(Player player, int slot, String data) {
        if (data == null || !data.contains(":")) {
            player.closeInventory();
            return;
        }

        String[] parts = data.split(":", 2);
        Village village = villageManager.getVillage(UUID.fromString(parts[0])).orElse(null);
        VillageRelationType relationType = VillageRelationType.fromString(parts[1]);
        if (village == null || relationType == null) {
            player.closeInventory();
            return;
        }

        if (slot == 49) {
            guiManager.openRelationsGui(player, village);
            return;
        }

        List<Village> otherVillages = villageManager.getAllVillages().stream()
                .filter(v -> !v.getId().equals(village.getId()))
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
        int targetIndex = slot - 10;
        if (targetIndex < 0 || targetIndex >= otherVillages.size()) {
            return;
        }

        Village target = otherVillages.get(targetIndex);
        com.example.village.model.VillageRelation existing = village.getRelation(target.getId());

        String prefix = configManager.getPrefix();
        boolean refreshMenu = true;

        if (existing == null) {
            if (villageManager.proposeRelation(village, target, relationType)) {
                MessageUtil.send(player, prefix,
                        "&aBeziehung angefragt: " + relationType.getDisplayName()
                                + " mit " + target.getName() + ".");
            } else {
                MessageUtil.send(player, prefix,
                        "&cKonnte keine neue Beziehung erstellen.");
            }
        } else if (!existing.getType().equals(relationType)) {
            MessageUtil.send(player, prefix,
                    "&cEs existiert bereits eine andere Beziehung mit diesem Dorf.");
        } else if (existing.getState() == VillageRelationState.REQUESTED) {
            if (existing.getInitiatorVillageId() != null
                    && existing.getInitiatorVillageId().equals(village.getId())) {
                if (villageManager.cancelRelationRequest(village, target)) {
                    MessageUtil.send(player, prefix,
                            "&aBeziehungsanfrage wurde abgebrochen.");
                } else {
                    MessageUtil.send(player, prefix,
                            "&cKonnte die Anfrage nicht abbrechen.");
                }
            } else {
                if (villageManager.acceptRelation(village, target, relationType)) {
                    MessageUtil.send(player, prefix,
                            "&aBeziehung angenommen: " + relationType.getDisplayName());
                } else {
                    MessageUtil.send(player, prefix,
                            "&cKonnte die Anfrage nicht annehmen.");
                }
            }
        } else if (existing.getState() == VillageRelationState.ACTIVE) {
            if (relationType == VillageRelationType.WAR) {
                if (villageManager.breakRelation(village, target)) {
                    MessageUtil.send(player, prefix,
                            "&aKrieg beendet mit " + target.getName() + ".");
                } else {
                    MessageUtil.send(player, prefix,
                            "&cKonnte den Krieg nicht beenden.");
                }
            } else if (relationType == VillageRelationType.CURFEW) {
                if (villageManager.toggleCurfew(village, target)) {
                    MessageUtil.send(player, prefix,
                            "&aDurchgangssperre aufgehoben mit " + target.getName() + ".");
                } else {
                    MessageUtil.send(player, prefix,
                            "&cKonnte die Durchgangssperre nicht aufheben.");
                }
            } else {
                if (villageManager.breakRelation(village, target)) {
                    MessageUtil.send(player, prefix,
                            "&aBeziehung beendet mit " + target.getName() + ".");
                } else {
                    MessageUtil.send(player, prefix,
                            "&cKonnte die Beziehung nicht beenden.");
                }
            }
        } else if (existing.getState() == VillageRelationState.PENDING_PEACE) {
            if (existing.getInitiatorVillageId() != null
                    && existing.getInitiatorVillageId().equals(village.getId())) {
                if (villageManager.cancelPeaceRequest(village, target)) {
                    MessageUtil.send(player, prefix,
                            "&aFriedensanfrage abgebrochen.");
                } else {
                    MessageUtil.send(player, prefix,
                            "&cKonnte die Friedensanfrage nicht abgebrochen.");
                }
            } else {
                if (villageManager.breakRelation(village, target)) {
                    MessageUtil.send(player, prefix,
                            "&aKrieg beendet mit " + target.getName() + ".");
                } else {
                    MessageUtil.send(player, prefix,
                            "&cKonnte den Krieg nicht beenden.");
                }
            }
        }

        if (refreshMenu) {
            guiManager.openRelationTargetsGui(player, village, relationType);
        }
    }

    private void handleBuildingCategoriesClick(Player player, int slot, String data) {
        // data either "villageId" (show categories) or "villageId:categoryId" (show buildings in category)
        if (data == null) { player.closeInventory(); return; }
        String[] parts = data.split(":", 2);
        Village village = villageManager.getVillage(UUID.fromString(parts[0])).orElse(null);
        if (village == null) { player.closeInventory(); return; }

        // Back button handling: context-sensitive
        if (slot == 49) {
            if (parts.length == 1) {
                guiManager.openMainGui(player, village);
            } else {
                // inside a category -> go back to category list
                guiManager.openBuildingCategoriesGui(player, village);
            }
            return;
        }

        if (parts.length == 2 && slot == 53) {
            pendingBuildingSearches.put(player.getUniqueId(), true);
            player.closeInventory();
            MessageUtil.send(player, configManager.getPrefix(),
                    "&7Gib einen Suchbegriff für Gebäudenamen im Chat ein. &e/cancel &7bricht ab.");
            return;
        }

        BuildingConfigLoader loader = plugin.getBuildingConfigLoader();
        if (loader == null) { player.closeInventory(); return; }

        if (parts.length == 1) {
            // Click on a category -> open category content
            int catIndex = slot;
            List<BuildingConfigLoader.CategoryInfo> cats = loader.getCategories().stream().toList();
            if (catIndex >= 0 && catIndex < cats.size()) {
                String catId = cats.get(catIndex).getId();
                guiManager.openBuildingCategoryContentGui(player, village, catId);
            }
            return;
        }

        // parts.length == 2 -> inside a category content view
        String categoryId = parts[1];
        List<BuildingDefinition> defs = loader.getByCategory(categoryId);
        List<BuildingDefinition> visible = defs.stream().filter(BuildingDefinition::isShowInMenu).toList();
        int defIndex = slot;
        if (defIndex >= 0 && defIndex < visible.size()) {
            BuildingDefinition def = visible.get(defIndex);
            String typeKey = def.getId();
            if (village.getLevel() < def.getRequiredVillageLevel()) {
                MessageUtil.send(player, configManager.getPrefix(), "&cLevel " + def.getRequiredVillageLevel() + " benoetigt!");
                return;
            }
            guiManager.openBuildingManageTypeGui(player, village, typeKey);
        }
    }

    private void handleBuildingTypeOptionsClick(Player player, int slot, String data) {
        if (data == null || !data.contains(":")) {
            player.closeInventory();
            return;
        }

        String[] parts = data.split(":");
        Village village = villageManager.getVillage(UUID.fromString(parts[0])).orElse(null);
        String typeKey = parts[1];
        if (village == null) { player.closeInventory(); return; }
        if (!player.hasPermission("village.admin") && !villageManager.canManageBuildings(village, player.getUniqueId())) {
            showBarrierFeedback(player, "&cNur Baumeister/Gruender duerfen Gebaeude verwalten.");
            return;
        }

        BuildingDefinition def = getDefinition(typeKey);
        if (def == null) { player.closeInventory(); return; }

        switch (slot) {
            case 11 -> {
                // Build new building
                if (def != null && !def.isRequiresSchematic()) {
                    // Non-schematic building -> start placement directly
                    if (buildingService.isInBuildingSession(player.getUniqueId())) {
                        MessageUtil.send(player, configManager.getPrefix(), "&cDu baust bereits ein Gebaeude!");
                        return;
                    }
                    player.closeInventory();
                    buildingService.startPlacementMode(player, village, typeKey, null);
                    return;
                }

                if (buildingService.isInBuildingSession(player.getUniqueId())) {
                    MessageUtil.send(player, configManager.getPrefix(),
                            "&cDu baust bereits ein Gebaeude!");
                    return;
                }

                java.util.List<String> variants = buildingService.findSchematicVariations(typeKey);
                if (variants.isEmpty()) {
                    MessageUtil.send(player, configManager.getPrefix(),
                            "&cFür diesen Gebäudetyp ist keine Schematic hinterlegt.");
                    return;
                }
                if (variants.size() == 1) {
                    player.closeInventory();
                    buildingService.startPlacementMode(player, village, typeKey, null, variants.get(0));
                    return;
                }

                // Multiple schematic options: open variant selection
                guiManager.openBuildingBuildVariantsGui(player, village, typeKey);
            }
            case 15 -> {
                // Manage existing buildings of this type
                guiManager.openBuildingManageTypeGui(player, village, typeKey);
            }
            case 22 -> {
                // Back to categories
                guiManager.openBuildingCategoriesGui(player, village);
            }
            default -> {}
        }
    }

    private void handleBuildingBuildVariantsClick(Player player, int slot, String data) {
        if (data == null || !data.contains(":")) return;
        String[] parts = data.split(":");
        Village village = villageManager.getVillage(UUID.fromString(parts[0])).orElse(null);
        if (village == null) { player.closeInventory(); return; }
        String typeKey = parts[1];
        BuildingDefinition def = getDefinition(typeKey);
        if (def == null) { player.closeInventory(); return; }

        if (slot == 49) {
            guiManager.openBuildingManageTypeGui(player, village, typeKey);
            return;
        }

        java.util.List<String> variants = buildingService.findSchematicVariations(typeKey);
        if (variants.isEmpty() || slot < 0 || slot >= variants.size()) {
            return;
        }

        String selectedVariant = variants.get(slot);
        player.closeInventory();
        buildingService.startPlacementMode(player, village, typeKey, null, selectedVariant);
    }

    private void handleBuildingManageTypeClick(Player player, int slot, String data) {
        if (data == null || !data.contains(":")) {
            player.closeInventory();
            return;
        }

        String[] parts = data.split(":");
        Village village = villageManager.getVillage(UUID.fromString(parts[0])).orElse(null);
        String typeKey = parts[1];
        if (village == null) { player.closeInventory(); return; }

        if (slot == 49) {
            guiManager.openBuildingCategoriesGui(player, village);
            return;
        }

        if (slot == 53) {
            pendingBuildingSearches.put(player.getUniqueId(), true);
            player.closeInventory();
            MessageUtil.send(player, configManager.getPrefix(),
                    "&7Gib einen Suchbegriff für Gebäudenamen im Chat ein. &e/cancel &7bricht ab.");
            return;
        }

        if (slot == 45) {
            BuildingDefinition def = getDefinition(typeKey);
            if (def == null) {
                player.closeInventory();
                return;
            }
            if (buildingService.isInBuildingSession(player.getUniqueId())) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cDu baust bereits ein Gebaeude!");
                return;
            }
            // Non-schematic buildings: start placement directly
            if (!def.isRequiresSchematic()) {
                player.closeInventory();
                buildingService.startPlacementMode(player, village, typeKey, null);
                return;
            }
            java.util.List<String> variants = buildingService.findSchematicVariations(typeKey);
            if (variants.isEmpty()) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cFür diesen Gebäudetyp ist keine Schematic hinterlegt.");
                return;
            }
            if (variants.size() == 1) {
                player.closeInventory();
                buildingService.startPlacementMode(player, village, typeKey, null, variants.get(0));
                return;
            }
            guiManager.openBuildingBuildVariantsGui(player, village, typeKey);
            return;
        }

        java.util.List<VillageBuilding> visibleBuildings = guiManager.getVisibleBuildingsForManageType(player, village, typeKey);

        if (slot >= 0 && slot < visibleBuildings.size()) {
            VillageBuilding building = visibleBuildings.get(slot);
            if (!building.isCompleted()) {
                if (buildingService.getSession(player.getUniqueId()) != null) {
                    buildingService.openBuildingMenu(player);
                    return;
                }
                if (buildingService.startBuildingSessionForExistingBuilding(player, village, building.getId())) {
                    buildingService.openBuildingMenu(player);
                }
                return;
            }
            guiManager.openBuildingDetailGui(player, village, building.getId());
        }
    }

    private void handleLevelupClick(Player player, int slot) {
        // This is handled by LevelupGuiClickListener
        // This method is just a placeholder for routing
    }

    private void handleConfirmClick(Player player, int slot, String data) {
        if (data == null || !data.startsWith("delete:")) {
            player.closeInventory();
            return;
        }

        String villageId = data.substring("delete:".length());
        Village village = villageManager.getVillage(UUID.fromString(villageId)).orElse(null);
        if (village == null) {
            player.closeInventory();
            return;
        }

        if (slot == 11) {
            // Confirm delete
            String villageName = village.getName();
            previewService.clearPreview(player);
            villagerService.replaceCitizensVillagersWithMinecraftVillagers(village);
            villageManager.deleteVillage(village.getId());
            player.closeInventory();
            MessageUtil.send(player, configManager.getPrefix(),
                    configManager.message("village-deleted").replace("%name%", villageName));
        } else if (slot == 15) {
            // Cancel - go back to main menu
            guiManager.openMainGui(player, village);
        }
    }

    private void handleBorderSelectionClick(Player player, InventoryClickEvent event, int slot, String data) {
        Village village = villageManager.getVillage(UUID.fromString(data)).orElse(null);
        if (village == null) { player.closeInventory(); return; }

        if (slot == 19) {
            // Coordinate input
                player.closeInventory();
                borderService.startCoordSelectionWithButtons(player, village);
        } else if (slot == 28) {
            // Walk mode
                player.closeInventory();
                borderService.startWalkSelectionWithButtons(player, village);
        } else if (slot == 53) {
            // Release/reset border to initial square
            if (!villageManager.canManageVillage(village, player.getUniqueId())
                    && !player.hasPermission("village.admin")) {
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("no-permission"));
                return;
            }
            int initialSize = configManager.getInitialSize();
            int heightAbove = configManager.getHeightAbove();
            int heightBelow = configManager.getHeightBelow();
            org.bukkit.Location bellLoc = village.getBellLocation();
            com.example.village.model.VillageBorder newBorder =
                    com.example.village.model.VillageBorder.createSquare(
                            bellLoc, initialSize, heightAbove, heightBelow);
            village.setBorder(newBorder);
            // Clear all buildings when resetting borders
            village.getBuildings().clear();
            villageManager.saveVillage(village);
            previewService.clearPreview(player);
            previewService.showPreview(player, newBorder, player.getWorld());
            player.closeInventory();
            MessageUtil.send(player, configManager.getPrefix(),
                    "&aGrenzen auf das urspruengliche Quadrat zurueckgesetzt. Alle Gebaeude wurden entfernt.");
        } else if (slot == 21) {
            if (!villageManager.canManageVillage(village, player.getUniqueId())
                    && !player.hasPermission("village.admin")) {
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("no-permission"));
                return;
            }
            player.closeInventory();
            borderService.startBorderSplitSelection(player.getUniqueId(), village);
            previewService.showPreviewMultiple(player, village.getBorders(), player.getWorld());
            MessageUtil.send(player, configManager.getPrefix(),
                    "&7Klicke auf die Grenzregion, die geteilt werden soll.");
        } else if (slot == 22) {
            if (!villageManager.canManageVillage(village, player.getUniqueId())
                    && !player.hasPermission("village.admin")) {
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("no-permission"));
                return;
            }
            player.closeInventory();
            borderService.startBorderEnlargeSelection(player.getUniqueId(), village);
            previewService.showPreviewMultiple(player, village.getBorders(), player.getWorld());
            MessageUtil.send(player, configManager.getPrefix(),
                    "&7Klicke auf die Grenzregion, die vergrößert werden soll.");
        } else if (slot == 23) {
            if (!villageManager.canManageVillage(village, player.getUniqueId())
                    && !player.hasPermission("village.admin")) {
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("no-permission"));
                return;
            }
            player.closeInventory();
            borderService.startBorderShrinkSelection(player.getUniqueId(), village);
            previewService.showPreviewMultiple(player, village.getBorders(), player.getWorld());
            MessageUtil.send(player, configManager.getPrefix(),
                    "&7Klicke auf die Grenzregion, die verkleinert werden soll.");
        } else if (slot == 13) {
            // Show borders (left=permanent toggle, right=30s)
            if (previewService != null) {
                if (event.isRightClick()) {
                    previewService.showPreviewMultipleForSeconds(player, village.getBorders(), player.getWorld(), 30);
                    MessageUtil.send(player, configManager.getPrefix(), "&aGrenzen fuer &e30s &aangezeigt.");
                    guiManager.openBorderSelectionGui(player, village);
                    return;
                }

                // Left click toggles permanent on/off
                if (previewService.hasActivePreview(player)) {
                    previewService.clearPreview(player);
                    MessageUtil.send(player, configManager.getPrefix(), "&aGrenzen ausgeblendet.");
                } else {
                    previewService.showPermanentPreview(player, village.getBorders(), player.getWorld());
                    MessageUtil.send(player, configManager.getPrefix(), "&aGrenzen permanent angezeigt.");
                }
            }
            guiManager.openBorderSelectionGui(player, village);
        } else if (slot == 30) {
            if (!villageManager.canManageVillage(village, player.getUniqueId())
                    && !player.hasPermission("village.admin")) {
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("no-permission"));
                return;
            }
            player.closeInventory();
            borderService.startBorderDeleteSelection(player.getUniqueId(), village);
            MessageUtil.send(player, configManager.getPrefix(),
                    "&7Klicke auf einen Block der Grenzflaeche, die du loeschen willst.");
        } else if (slot == 32) {
            if (!villageManager.canManageVillage(village, player.getUniqueId())
                    && !player.hasPermission("village.admin")) {
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("no-permission"));
                return;
            }
            player.closeInventory();
            borderService.startBorderFusionSelection(player.getUniqueId(), village);
            previewService.showPreviewMultiple(player, village.getBorders(), player.getWorld());
            MessageUtil.send(player, configManager.getPrefix(),
                    "&7Klicke nacheinander auf 2+ Grenzflaechen zum Vereinen.");
            MessageUtil.send(player, configManager.getPrefix(),
                    "&7Nach jeder Auswahl erhaelst du [JA]/[NEIN] fuer 'Fertig?'.");
        } else if (slot == 49) {
            guiManager.openMainGui(player, village);
        }
    }

    private void handleBuildingsClick(Player player, int slot, String data) {
        // data ist entweder "villageId" oder "villageId:my"
        String villageId = data;
        boolean isMyBuildings = false;
        
        if (data.contains(":")) {
            String[] parts = data.split(":");
            villageId = parts[0];
            isMyBuildings = "my".equals(parts[1]);
        }
        
        Village village = villageManager.getVillage(UUID.fromString(villageId)).orElse(null);
        if (village == null) { player.closeInventory(); return; }

        // Für "Meine Gebäude" - Klicks auf Gebäude oder Zurück verarbeiten
        if (isMyBuildings) {
            if (slot == 49) {
                guiManager.openBuildingCategoriesGui(player, village);
                return;
            }
            
            // Klick auf ein Gebäude
            java.util.List<VillageBuilding> buildings = village.getBuildings();
            if (slot >= 0 && slot < buildings.size()) {
                VillageBuilding building = buildings.get(slot);
                if (!building.isCompleted()) {
                    if (buildingService.getSession(player.getUniqueId()) != null) {
                        buildingService.openBuildingMenu(player);
                        return;
                    }
                    if (buildingService.startBuildingSessionForExistingBuilding(player, village, building.getId())) {
                        buildingService.openBuildingMenu(player);
                    }
                    return;
                }
                guiManager.openBuildingDetailGui(player, village, building.getId());
            }
            return;
        }

        // Alte Logik für die ursprüngliche openBuildingsGui() (falls noch verwendet)
        if (slot == 49) {
            guiManager.openBuildingCategoriesGui(player, village);
            return;
        }

        // Find the building type by slot index
        List<BuildingDefinition> defs = getDefinitionList();
        if (slot >= 0 && slot < defs.size()) {
            BuildingDefinition def = defs.get(slot);
            String typeKey = def.getId();

            if (village.getLevel() < def.getRequiredVillageLevel()) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cLevel " + def.getRequiredVillageLevel() + " benoetigt!");
                return;
            }

            if (buildingService.isInBuildingSession(player.getUniqueId())) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cDu baust bereits ein Gebaeude!");
                return;
            }

            // Non-schematic buildings: start placement directly
            if (!def.isRequiresSchematic()) {
                player.closeInventory();
                buildingService.startPlacementMode(player, village, typeKey, null);
                return;
            }

            java.util.List<String> variants = buildingService.findSchematicVariations(typeKey);
            if (variants.isEmpty()) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cFür diesen Gebäudetyp ist keine Schematic hinterlegt.");
                return;
            }
            if (variants.size() == 1) {
                player.closeInventory();
                buildingService.startPlacementMode(player, village, typeKey, null, variants.get(0));
                return;
            }

            guiManager.openBuildingBuildVariantsGui(player, village, typeKey);
        }
    }

    private void handleBuildingDirectionClick(Player player, int slot, String data) {
        if (data == null || !data.contains(":")) return;
        String[] parts = data.split(":");
        Village village = villageManager.getVillage(UUID.fromString(parts[0])).orElse(null);
        if (village == null) { player.closeInventory(); return; }
        String typeKey = parts[1];
        BuildingDefinition def = getDefinition(typeKey);
        if (def == null) { player.closeInventory(); return; }

        String direction = null;
        switch (slot) {
            case 4 -> direction = "N";
            case 22 -> direction = "S";
            case 12 -> direction = "W";
            case 14 -> direction = "E";
            case 49 -> {
                guiManager.openBuildingCategoriesGui(player, village);
                return;
            }
            default -> { return; }
        }

        player.closeInventory();
        buildingService.startPlacementMode(player, village, typeKey, direction);
    }

    private void handleBuildingDetailClick(Player player, InventoryClickEvent event, int slot, String data) {
        if (data == null || !data.contains(":")) {
            player.closeInventory();
            return;
        }
        String[] parts = data.split(":", 2);
        Village village = villageManager.getVillage(UUID.fromString(parts[0])).orElse(null);
        UUID buildingId;
        try {
            buildingId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            player.closeInventory();
            return;
        }
        if (village == null) {
            player.closeInventory();
            return;
        }

        VillageBuilding building = village.getBuildings().stream()
                .filter(b -> buildingId.equals(b.getId()))
                .findFirst()
                .orElse(null);
        if (building == null) {
            player.closeInventory();
            return;
        }
        boolean canManageBuilding = player.hasPermission("village.admin")
                || villageManager.canManageBuildings(village, player.getUniqueId());

        switch (slot) {
            case 49 -> {
                guiManager.openBuildingManageTypeGui(player, village, building.getTypeKey());
            }
            case 15 -> {
                if (!canManageBuilding) { showBarrierFeedback(player, "&cNur Baumeister/Gruender duerfen Gebäude umbenennen."); return; }
                pendingBuildingRenames.put(player.getUniqueId(), buildingId);
                player.closeInventory();
                MessageUtil.send(player, configManager.getPrefix(),
                        "&7Gib einen neuen Namen für dieses Gebäude im Chat ein. &e/cancel &7bricht ab.");
            }
            case 37 -> {
                if (building.isSignHidden()) {
                    if (buildingService.wouldReplaceBlockWhenRevealingSign(building)) {
                        BuildingService.PendingSignRevealConfirmation pending =
                                buildingService.createPendingSignRevealConfirmation(player, village, building);
                        MessageUtil.sendYesNoRunCommand(player, configManager.getPrefix(),
                                "&eAm Schild-Ort (&7" + pending.targetLocation().getBlockX() + " "
                                        + pending.targetLocation().getBlockY() + " "
                                        + pending.targetLocation().getBlockZ()
                                        + "&e) steht ein Block. Ersetzen?",
                                "/village signshowconfirm ja",
                                "/village signshowconfirm nein");
                        player.closeInventory();
                        return;
                    }
                    buildingService.revealBuildingSign(player, village, building, true);
                } else {
                    if (building.getSignLocation() != null) {
                        buildingService.removeSignAt(building.getSignLocation());
                    }
                    building.setSignHidden(true);
                    buildingService.refreshBuildingProtectionAndMarker(village, building);
                    villageManager.saveVillage(village);
                    MessageUtil.send(player, configManager.getPrefix(), "&aSchild ausgeblendet.");
                }
                guiManager.openBuildingDetailGui(player, village, buildingId);
            }
            case 21 -> {
                if (!canManageBuilding) { showBarrierFeedback(player, "&cNur Baumeister/Gruender duerfen upgraden."); return; }
                if (buildingService.upgradeBuilding(player, village, buildingId)) {
                    MessageUtil.send(player, configManager.getPrefix(), "&aGebäude erfolgreich verbessert.");
                } else {
                    MessageUtil.send(player, configManager.getPrefix(),
                            "&cUpgrade fehlgeschlagen (Kosten/Level/Status prüfen).");
                }
                guiManager.openBuildingDetailGui(player, village, buildingId);
            }
            case 23 -> {
                if (event.isLeftClick()) {
                    guiManager.openBuildingWhitelistGui(player, village, buildingId);
                    return;
                } else if (event.isRightClick()) {
                    building.setAccessAllMembers(!building.isAccessAllMembers());
                    if (building.isAccessAllMembers()) {
                        building.getAccessList().clear();
                    }
                    MessageUtil.send(player, configManager.getPrefix(),
                            building.isAccessAllMembers()
                                    ? "&aZugriff: alle Dorfmitglieder."
                                    : "&eZugriff: Whitelist-Modus aktiv.");
                }
                buildingService.refreshBuildingProtectionAndMarker(village, building);
                villageManager.saveVillage(village);
                guiManager.openBuildingDetailGui(player, village, buildingId);
            }
            case 25 -> {
                if (!canManageBuilding) { showBarrierFeedback(player, "&cNur Baumeister/Gruender duerfen Schilder verschieben."); return; }
                // Move sign (interactive block click)
                PendingBlockSelection selection = new PendingBlockSelection(
                        player.getUniqueId(),
                        PendingBlockSelection.SelectionType.MOVE_BUILDING_SIGN,
                        buildingId
                );
                buildingService.setPendingBlockSelection(player.getUniqueId(), selection);
                player.closeInventory();
                MessageUtil.send(player, configManager.getPrefix(),
                        "&aRechtsklick auf einen Block (oder Blockseite), um das Schild dort zu platzieren.");
            }
            case 19 -> {
                if (!canManageBuilding) { showBarrierFeedback(player, "&cNur Baumeister/Gruender duerfen Schilder bearbeiten."); return; }
                // Sign label editor entrypoint (chat-based for now)
                player.closeInventory();
                pendingSignEdits.put(player.getUniqueId(), new PendingSignEdit(village.getId(), buildingId));
                MessageUtil.send(player, configManager.getPrefix(),
                        "&7Gib 4 Zeilen für das Schild im Chat ein, getrennt mit &e|&7.");
                String current = building.getSignTemplate() != null && !building.getSignTemplate().isBlank()
                        ? building.getSignTemplate()
                        : "%building_type%|%owner%|Level %level%|[Rechtsklick]";
                MessageUtil.sendClickToCopy(player, configManager.getPrefix(),
                        "&7Aktuelle Vorlage:", current);
                MessageUtil.sendSuggestCommand(player, configManager.getPrefix(), "&7Platzhalter %building_type%:", appendSignPlaceholder(current, "%building_type%"));
                MessageUtil.sendSuggestCommand(player, configManager.getPrefix(), "&7Platzhalter %owner%:", appendSignPlaceholder(current, "%owner%"));
                MessageUtil.sendSuggestCommand(player, configManager.getPrefix(), "&7Platzhalter %level%:", appendSignPlaceholder(current, "%level%"));
                MessageUtil.sendRunCommand(player, configManager.getPrefix(),
                        "&7Abbrechen?", "[ABBRECHEN]", "/village cancel");
            }
            case 43 -> {
                if (!canManageBuilding) { showBarrierFeedback(player, "&cNur Baumeister/Gruender duerfen abreissen."); return; }
                String typeKeyBack = building.getTypeKey();
                if (event.isRightClick()) {
                    buildingService.removeBuildingCompletely(building);
                    village.removeBuilding(buildingId);
                    villageManager.saveVillage(village);
                    MessageUtil.send(player, configManager.getPrefix(),
                            "&cGebäude vollständig abgerissen (inkl. Blöcke).");
                } else {
                    if (buildingService.removeCompletedBuilding(player, village, buildingId)) {
                        MessageUtil.send(player, configManager.getPrefix(),
                                "&eGebäude entfernt (Weltblöcke bleiben bestehen).");
                    } else {
                        MessageUtil.send(player, configManager.getPrefix(),
                                "&cGebäude konnte nicht entfernt werden.");
                    }
                }
                guiManager.openBuildingManageTypeGui(player, village, typeKeyBack);
            }
            default -> {}
        }
    }

    private String appendSignPlaceholder(String currentTemplate, String placeholder) {
        String base = currentTemplate != null && !currentTemplate.isBlank()
                ? currentTemplate.trim()
                : "%building_type%|%owner%|Level %level%|[Rechtsklick]";
        if (base.endsWith(" ")) {
            return base + placeholder;
        }
        return base + " " + placeholder;
    }

    private void handleBuildingWhitelistClick(Player player, int slot, String data) {
        if (data == null || !data.contains(":")) return;
        String[] parts = data.split(":", 2);
        Village village = villageManager.getVillage(UUID.fromString(parts[0])).orElse(null);
        if (village == null) return;
        UUID buildingId = UUID.fromString(parts[1]);
        VillageBuilding building = village.getBuildings().stream()
                .filter(b -> b.getId().equals(buildingId))
                .findFirst().orElse(null);
        if (building == null) return;
        if (slot == 49) {
            guiManager.openBuildingDetailGui(player, village, buildingId);
            return;
        }
        List<UUID> memberIds = new ArrayList<>(village.getMembers().keySet());
        memberIds.sort(Comparator.comparing(id -> {
            String name = org.bukkit.Bukkit.getOfflinePlayer(id).getName();
            return name != null ? name.toLowerCase() : id.toString();
        }));
        if (slot < 0 || slot >= memberIds.size()) return;
        UUID memberId = memberIds.get(slot);
        String memberName = org.bukkit.Bukkit.getOfflinePlayer(memberId).getName();
        if (memberName == null) memberName = memberId.toString().substring(0, 8);
        building.setAccessAllMembers(false);
        if (building.getAccessList().contains(memberId)) {
            building.getAccessList().remove(memberId);
            MessageUtil.send(player, configManager.getPrefix(),
                    "&eWhitelist: &c" + memberName + " entfernt.");
        } else {
            building.getAccessList().add(memberId);
            MessageUtil.send(player, configManager.getPrefix(),
                    "&eWhitelist: &a" + memberName + " hinzugefügt.");
        }
        buildingService.refreshBuildingProtectionAndMarker(village, building);
        villageManager.saveVillage(village);
        guiManager.openBuildingWhitelistGui(player, village, buildingId);
    }

    private void handleUpgradesClick(Player player, int slot, String data) {
        Village village = villageManager.getVillage(UUID.fromString(data)).orElse(null);
        if (village == null) { player.closeInventory(); return; }

        if (slot == 49) {
            guiManager.openMainGui(player, village);
            return;
        }
        if (slot == 45) {
            if (!player.hasPermission("village.admin") && !village.isFounder(player.getUniqueId())) {
                showBarrierFeedback(player, "&cNur der Gruender kann Rollen-Upgrades kaufen.");
                return;
            }
            guiManager.openRoleUpgradesGui(player, village, upgradeService);
            return;
        }

        List<String> keys = new ArrayList<>(configManager.getUpgradeTypes().keySet());
        if (slot >= 0 && slot < keys.size()) {
            String upgradeKey = keys.get(slot);

            UpgradeService.UpgradeResult result = upgradeService.purchaseUpgrade(
                    player, village, upgradeKey);

            switch (result) {
                case SUCCESS -> {
                    UpgradeType type = configManager.getUpgradeType(upgradeKey);
                    String msg = configManager.message("upgrade-purchased").replace("%upgrade%", type != null ? type.getDisplayName() : upgradeKey);
                    // Notify purchaser
                    MessageUtil.send(player, configManager.getPrefix(), msg);
                    // Notify all online village members
                    for (com.example.village.model.VillageMember vm : village.getMembers().values()) {
                        org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(vm.getPlayerId());
                        if (p == null || !p.isOnline()) continue;
                        if (p.getUniqueId().equals(player.getUniqueId())) continue; // already sent
                        MessageUtil.send(p, configManager.getPrefix(), "&e" + player.getName() + " &7hat ein Upgrade gekauft: " + msg);
                    }
                    guiManager.openUpgradesGui(player, village);
                }
                case MAX_LEVEL_REACHED -> MessageUtil.send(player, configManager.getPrefix(),
                        "&cMax Level erreicht!");
                case NOT_ENOUGH_MONEY -> MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("upgrade-too-expensive"));
                case NOT_ENOUGH_POINTS -> MessageUtil.send(player, configManager.getPrefix(),
                        "&cNicht genuegend Dorfpunkte!");
                default -> {}
            }
        }
    }

    private void handleRoleUpgradesClick(Player player, int slot, String data) {
        Village village = villageManager.getVillage(UUID.fromString(data)).orElse(null);
        if (village == null) return;
        if (slot == 49) {
            guiManager.openUpgradesGui(player, village);
            return;
        }
        if (!player.hasPermission("village.admin") && !village.isFounder(player.getUniqueId())) {
            showBarrierFeedback(player, "&cNur der Gruender kann Rollen-Upgrades kaufen.");
            return;
        }
        var unlocks = upgradeService.getRoleUnlocks();
        if (slot < 0 || slot >= unlocks.size()) return;
        var unlock = unlocks.get(slot);
        UpgradeService.UpgradeResult result = upgradeService.purchaseRoleUnlock(player, village, unlock.key());
        switch (result) {
            case SUCCESS -> {
                String msg = "&aRolle freigeschaltet: &e" + unlock.displayName();
                MessageUtil.send(player, configManager.getPrefix(), msg);
                for (com.example.village.model.VillageMember vm : village.getMembers().values()) {
                    org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(vm.getPlayerId());
                    if (p == null || !p.isOnline()) continue;
                    if (p.getUniqueId().equals(player.getUniqueId())) continue;
                    MessageUtil.send(p, configManager.getPrefix(), "&e" + player.getName() + " &7hat eine Rolle freigeschaltet: " + unlock.displayName());
                }
            }
            case MAX_LEVEL_REACHED -> MessageUtil.send(player, configManager.getPrefix(), "&eBereits freigeschaltet.");
            case NOT_ENOUGH_MONEY -> MessageUtil.send(player, configManager.getPrefix(), configManager.message("upgrade-too-expensive"));
            case NOT_ENOUGH_POINTS -> MessageUtil.send(player, configManager.getPrefix(), "&cNicht genuegend Dorfpunkte!");
            default -> MessageUtil.send(player, configManager.getPrefix(), "&cUpgrade fehlgeschlagen.");
        }
        guiManager.openRoleUpgradesGui(player, village, upgradeService);
    }

    private void handleVillagersClick(Player player, int slot, String data) {
        Village village = villageManager.getVillage(UUID.fromString(data)).orElse(null);
        if (village == null) { player.closeInventory(); return; }
        boolean canTrade = player.hasPermission("village.admin") || villageManager.canTradeWithVillagers(village, player.getUniqueId());
        boolean canTrain = player.hasPermission("village.admin") || villageManager.canUpgradeVillagers(village, player.getUniqueId());

        if (slot == 49) {
            pendingVillagerAssignments.remove(player.getUniqueId());
            guiManager.openMainGui(player, village);
            return;
        }

        // Handle villager glow display button (slot 48)
        if (slot == 48) {
            if (!player.hasPermission("village.villager.show.temp") && !player.hasPermission("village.villager.show.on")) {
                showBarrierFeedback(player, "&cKeine Berechtigung für Dorfbewohner-Anzeige.");
                return;
            }
            guiManager.cycleVillagerGlowMode(player.getUniqueId());
            int newMode = guiManager.getVillagerGlowMode(player.getUniqueId());
            String modeStr = switch (newMode) {
                case 1 -> "&6Temporär (15s)";
                case 2 -> "&2Dauerhaft";
                default -> "&7Aus";
            };
            MessageUtil.send(player, configManager.getPrefix(), "&aDorfbewohner-Anzeige: " + modeStr);
            
            // Apply the glow effect immediately
            plugin.getVillagerGlowService().showVillagers(player, village);
            
            guiManager.openVillagersGui(player, village);
            return;
        }

        UUID pendingVillageId = pendingVillagerAssignments.get(player.getUniqueId());
        if (pendingVillageId != null) {
            if (!pendingVillageId.equals(village.getId())) {
                pendingVillagerAssignments.remove(player.getUniqueId());
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cDorfbewohner-Zuweisung abgebrochen. Öffne das Gebäude-Menü erneut.");
                return;
            }
            if (slot == 45) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cWähle zuerst einen vorhandenen Dorfbewohner aus, um ihn zuzuweisen.");
                return;
            }
            if (slot >= 0 && slot < village.getVillagers().size()) {
                CustomVillager villager = village.getVillagers().get(slot);
                pendingJobAssignments.put(player.getUniqueId(), new PendingJobAssignment(village, villager));
                pendingVillagerAssignments.remove(player.getUniqueId());
                player.closeInventory();
                MessageUtil.send(player, configManager.getPrefix(),
                        "&eKlicke nun auf einen Block eines fertigen Gebäudes, um den Job zuzuweisen.");
                return;
            }
        }

        if (slot == 45) {
            if (!canTrain) {
                showBarrierFeedback(player, "&cNur Trainer/Gruender duerfen Bewohner einladen.");
                return;
            }
            // Spawn new villager as normal villager (no profession)
            // Requires available beds
            int totalBeds = villagerService.getTotalBeds(village);
            int usedBeds = villagerService.getUsedBeds(village);
            if (totalBeds <= 0) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cEs gibt keine Betten! Baue zuerst ein Wohnhaus.");
                return;
            }
            if (usedBeds >= totalBeds) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cKeine freien Betten verfuegbar! Baue mehr Wohnhaeuser.");
                return;
            }
            CustomVillager newVillager = villagerService.spawnVillager(village, player.getLocation());
            if (newVillager != null) {
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("villager-spawned"));
            } else {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cKann keinen Dorfbewohner mehr einladen!");
            }
            guiManager.openVillagersGui(player, village);
            return;
        }

        // Click on existing villager
        if (slot >= 0 && slot < village.getVillagers().size()) {
            if (!canTrade && !canTrain) {
                showBarrierFeedback(player, "&cKeine Berechtigung fuer Dorfbewohner-Details.");
                return;
            }
            CustomVillager villager = village.getVillagers().get(slot);
            guiManager.openVillagerDetailGui(player, village, villager);
        }
    }

    private void handleVillagerDetailClick(Player player, int slot, String data) {
        if (data == null || !data.contains(":")) return;
        String[] parts = data.split(":");
        Village village = villageManager.getVillage(UUID.fromString(parts[0])).orElse(null);
        if (village == null) { player.closeInventory(); return; }

        UUID villagerId = UUID.fromString(parts[1]);
        CustomVillager villager = village.getVillagers().stream()
                .filter(v -> v.getId().equals(villagerId))
                .findFirst().orElse(null);
        if (villager == null) { player.closeInventory(); return; }
        boolean canTrade = player.hasPermission("village.admin") || villageManager.canTradeWithVillagers(village, player.getUniqueId());
        boolean canTrain = player.hasPermission("village.admin") || villageManager.canUpgradeVillagers(village, player.getUniqueId());

        switch (slot) {
            case 10 -> {
                // Inventory
                if (!canTrade) { showBarrierFeedback(player, "&cDu darfst nicht auf das Inventar zugreifen."); return; }
                openVillagerInventory(player, village, villager);
            }
            case 12 -> {
                if (!canTrain) { showBarrierFeedback(player, "&cNur Trainer/Gruender duerfen versorgen."); return; }
                // Feed
                villagerService.feedVillager(villager, 30);
                MessageUtil.send(player, configManager.getPrefix(), "&aDorfbewohner gefuettert!");
                guiManager.openVillagerDetailGui(player, village, villager);
            }
            case 14 -> {
                if (!canTrade) {
                    showBarrierFeedback(player, "&cDu darfst nicht mit diesem Dorfbewohner handeln.");
                    return;
                }
                openVillagerTrade(player, village, villager);
            }
            case 16 -> {
                if (!canTrain) { showBarrierFeedback(player, "&cNur Trainer/Gruender duerfen entfernen."); return; }
                // Remove
                villagerService.removeVillager(village, villagerId);
                MessageUtil.send(player, configManager.getPrefix(), "&cDorfbewohner entlassen.");
                guiManager.openVillagersGui(player, village);
            }
            case 22 -> guiManager.openVillagersGui(player, village);
        }
    }

    private void handleVillagerConfigClick(Player player, int slot, String data) {
        if (data == null || !data.contains(":")) return;
        String[] parts = data.split(":");
        Village village = villageManager.getVillage(UUID.fromString(parts[0])).orElse(null);
        if (village == null) { player.closeInventory(); return; }

        UUID villagerId = UUID.fromString(parts[1]);
        CustomVillager villager = village.getVillagers().stream()
                .filter(v -> v.getId().equals(villagerId))
                .findFirst().orElse(null);
        if (villager == null) { player.closeInventory(); return; }
        boolean canTrain = player.hasPermission("village.admin") || villageManager.canUpgradeVillagers(village, player.getUniqueId());
        if (!canTrain) { showBarrierFeedback(player, "&cNur Trainer/Gruender duerfen konfigurieren."); return; }

        switch (slot) {
            case 11 -> {
                // Assign bed
                int totalBeds = villagerService.getTotalBeds(village);
                int usedBeds = villagerService.getUsedBeds(village);
                if (usedBeds >= totalBeds) {
                    MessageUtil.send(player, configManager.getPrefix(),
                            "&cKeine freien Betten verfuegbar!");
                    return;
                }
                // Auto-assign to first available bed
                for (com.example.village.model.VillageBuilding building : village.getBuildings()) {
                    if (!building.isCompleted()) continue;
                    int capacity = villagerService.getVillagerCapacityForBuilding(building);
                    if (capacity <= 0) continue;
                    long assignedCount = village.getVillagers().stream()
                            .filter(v -> building.getId().equals(v.getAssignedBedBuildingId()))
                            .count();
                    if (assignedCount < capacity) {
                        villager.setAssignedBedBuildingId(building.getId());
                        villageManager.saveVillage(village);
                        MessageUtil.send(player, configManager.getPrefix(),
                                "&aBett zugewiesen!");
                        break;
                    }
                }
                guiManager.openVillagerConfigGui(player, village, villager);
            }
            case 13 -> {
                // Choose job - close menu, prompt in chat
                player.closeInventory();
                pendingJobAssignments.put(player.getUniqueId(),
                        new PendingJobAssignment(village, villager));
                MessageUtil.send(player, configManager.getPrefix(),
                        "&7Klicke auf einen Block eines Gebaeudes, um dem Dorfbewohner einen Job zuzuweisen.");
            }
            case 15 -> {
                // Fire from job
                if (!"none".equals(villager.getProfessionKey())) {
                    boolean resetProgress = villagerService.shouldResetProgressOnFire();
                    villagerService.fireFromJob(village, villager, resetProgress);
                    MessageUtil.send(player, configManager.getPrefix(),
                            "&cDorfbewohner wurde vom Job entlassen."
                                    + (resetProgress ? " Fortschritt zurueckgesetzt." : " Fortschritt beibehalten."));
                } else {
                    MessageUtil.send(player, configManager.getPrefix(),
                            "&cDieser Dorfbewohner hat keinen Job.");
                }
                guiManager.openVillagerConfigGui(player, village, villager);
            }
            case 22 -> player.closeInventory();
        }
    }

    private void openVillagerTrade(Player player, Village village, CustomVillager villager) {
        if (currencySystem == null || tradeInventoryListener == null) {
            MessageUtil.send(player, configManager.getPrefix(), "&cHandel ist aktuell deaktiviert.");
            return;
        }
        VillagerInventory inventory = new VillagerInventory(currencySystem.getConfigManager().getVillagerStorageSlots());
        int offerCount = 0;
        for (Map.Entry<org.bukkit.Material, Integer> entry : villager.getInventory().entrySet()) {
            if (offerCount >= 4) break;
            String itemId = entry.getKey().name();
            int amount = Math.min(entry.getValue(), 64);
            String displayName = entry.getKey().name().replace('_', ' ').toLowerCase();
            displayName = Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1);
            inventory.addForSale(new TradeOffer(itemId, displayName, 1, 20 + (offerCount * 10), com.example.village.currency.CurrencyType.LOCAL, amount));
            offerCount++;
        }

        if (inventory.getForSale().isEmpty()) {
            inventory.addForSale(new TradeOffer("OAK_LOG", "Eichenholz", 1, 25, com.example.village.currency.CurrencyType.LOCAL, 64));
            inventory.addForSale(new TradeOffer("WHEAT", "Weizen", 1, 15, com.example.village.currency.CurrencyType.LOCAL, 32));
        }

        UUID villagerUUID = villager.getId();
        String villageId = village.getId().toString();
        tradeInventoryListener.cacheVillagerInventory(villagerUUID, inventory);
        VillagerLocalTradeUI ui = currencySystem.createVillagerLocalTradeUI(villagerUUID.toString(), villager.getName(), villageId, inventory);
        tradeInventoryListener.registerTrading(player.getUniqueId(), ui);
        ui.openTradingUI(player);
    }

    private void openVillagerInventory(Player player, Village village, CustomVillager villager) {
        int maxSlots = getVillagerInventoryMaxSlots(village);
        com.example.village.gui.VillagerInventoryGui inventoryGui = 
                new com.example.village.gui.VillagerInventoryGui(villager, village, player, maxSlots);
        inventoryGui.open();
    }

    private void handleVillagerInventoryClick(InventoryClickEvent event, 
                                              com.example.village.gui.VillagerInventoryGui inventoryGui) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        CustomVillager villager = inventoryGui.getVillager();
        Village village = inventoryGui.getVillage();
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        ItemStack currentItem = event.getCurrentItem();

        boolean canTradeWithVillager = player.hasPermission("village.admin")
                || villageManager.canTradeWithVillagers(village, player.getUniqueId());
        boolean canUpgradeInventory = player.hasPermission("village.admin")
                || village.isFounder(player.getUniqueId());

        if (rawSlot == com.example.village.gui.VillagerInventoryGui.SLOT_BACK) {
            guiManager.openVillagerDetailGui(player, village, villager);
            return;
        }

        if (rawSlot == com.example.village.gui.VillagerInventoryGui.SLOT_UPGRADE) {
            if (!canUpgradeInventory) {
                showBarrierFeedback(player, "&cNur Gruender/Admin duerfen das Lager aufwerten.");
                return;
            }
            if (upgradeVillagerInventorySlots(player, village)) {
                openVillagerInventory(player, village, villager);
            }
            return;
        }

        if (rawSlot == com.example.village.gui.VillagerInventoryGui.SLOT_DEPOSIT_STACK
                || rawSlot == com.example.village.gui.VillagerInventoryGui.SLOT_DEPOSIT_ONE) {
            if (!canTradeWithVillager) {
                showBarrierFeedback(player, "&cDu darfst nicht auf das Inventar zugreifen.");
                return;
            }
            int amount = rawSlot == com.example.village.gui.VillagerInventoryGui.SLOT_DEPOSIT_ONE ? 1 : 64;
            depositFromMainHand(player, village, villager, amount);
            openVillagerInventory(player, village, villager);
            return;
        }

        if (rawSlot < topSize) {
            if (currentItem == null || currentItem.getType() == Material.AIR) return;
            if (!canTradeWithVillager) {
                showBarrierFeedback(player, "&cDu darfst nicht auf das Inventar zugreifen.");
                return;
            }
            withdrawFromVillager(player, villager, currentItem.getType(), event.isLeftClick());
            openVillagerInventory(player, village, villager);
            return;
        }

        if (!canTradeWithVillager || currentItem == null || currentItem.getType() == Material.AIR) {
            return;
        }

        if (rawSlot >= topSize) {
            depositFromPlayerInventory(player, village, villager, currentItem, event.isLeftClick());
            openVillagerInventory(player, village, villager);
        }
    }

    private int getVillagerInventoryMaxSlots(Village village) {
        int level = Math.max(0, village.getUpgradeLevel("villager-storage"));
        return Math.min(54, 9 + (level * 9));
    }

    private boolean hasVillagerInventoryCapacity(CustomVillager villager, int maxSlots, Material material) {
        if (villager.getInventory().containsKey(material)) {
            return true;
        }
        return villager.getInventory().size() < maxSlots;
    }

    private void depositFromMainHand(Player player, Village village, CustomVillager villager, int requestedAmount) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main == null || main.getType() == Material.AIR || requestedAmount <= 0) {
            MessageUtil.send(player, configManager.getPrefix(), "&cHalte ein Item in der Mainhand.");
            return;
        }
        int maxSlots = getVillagerInventoryMaxSlots(village);
        Material mat = main.getType();
        if (!hasVillagerInventoryCapacity(villager, maxSlots, mat)) {
            MessageUtil.send(player, configManager.getPrefix(), "&cKein freier Villager-Slot vorhanden.");
            return;
        }
        int amountToMove = Math.min(main.getAmount(), requestedAmount);
        villager.addItem(mat, amountToMove);
        main.setAmount(main.getAmount() - amountToMove);
        if (main.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            player.getInventory().setItemInMainHand(main);
        }
        villageManager.saveVillage(village);
        MessageUtil.send(player, configManager.getPrefix(), "&a" + amountToMove + "x " + mat.name() + " eingelagert.");
    }

    private void depositFromPlayerInventory(Player player, Village village, CustomVillager villager, ItemStack clickedItem, boolean fullStack) {
        int maxSlots = getVillagerInventoryMaxSlots(village);
        Material mat = clickedItem.getType();
        if (!hasVillagerInventoryCapacity(villager, maxSlots, mat)) {
            MessageUtil.send(player, configManager.getPrefix(), "&cKein freier Villager-Slot vorhanden.");
            return;
        }
        int amountToMove = fullStack ? clickedItem.getAmount() : 1;
        amountToMove = Math.max(1, amountToMove);
        villager.addItem(mat, amountToMove);
        clickedItem.setAmount(clickedItem.getAmount() - amountToMove);
        villageManager.saveVillage(village);
        MessageUtil.send(player, configManager.getPrefix(), "&a" + amountToMove + "x " + mat.name() + " eingelagert.");
    }

    private void withdrawFromVillager(Player player, CustomVillager villager, Material material, boolean fullStack) {
        Map<Material, Integer> inv = villager.getInventory();
        int stored = inv.getOrDefault(material, 0);
        if (stored <= 0) {
            return;
        }
        int quantityToRemove = fullStack ? Math.min(64, stored) : 1;
        ItemStack giveItem = new ItemStack(material, quantityToRemove);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(giveItem);
        int actuallyAdded = quantityToRemove;
        if (!leftover.isEmpty()) {
            int remaining = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            actuallyAdded = quantityToRemove - remaining;
        }
        if (actuallyAdded <= 0) {
            MessageUtil.send(player, configManager.getPrefix(), "&cDein Inventar ist voll.");
            return;
        }
        inv.put(material, stored - actuallyAdded);
        if (inv.get(material) <= 0) {
            inv.remove(material);
        }
        MessageUtil.send(player, configManager.getPrefix(), "&a" + actuallyAdded + "x " + material.name() + " entnommen.");
    }

    private boolean upgradeVillagerInventorySlots(Player player, Village village) {
        int currentLevel = Math.max(0, village.getUpgradeLevel("villager-storage"));
        int currentSlots = getVillagerInventoryMaxSlots(village);
        if (currentSlots >= 54) {
            MessageUtil.send(player, configManager.getPrefix(), "&eVillager-Lager hat bereits das Maximum erreicht.");
            return false;
        }

        int nextLevel = currentLevel + 1;
        int pointsCost = 75 * nextLevel;
        double moneyCost = 250.0 * nextLevel;

        if (village.getPoints() < pointsCost) {
            MessageUtil.send(player, configManager.getPrefix(), "&cNicht genug Dorfpunkte (" + pointsCost + " benoetigt).");
            return false;
        }
        if (vaultHook != null && vaultHook.isAvailable() && !vaultHook.has(player, moneyCost)) {
            MessageUtil.send(player, configManager.getPrefix(), "&cNicht genug Geld (" + moneyCost + " benoetigt).");
            return false;
        }

        if (vaultHook != null && vaultHook.isAvailable()) {
            vaultHook.withdraw(player, moneyCost);
        }
        village.setPoints(village.getPoints() - pointsCost);
        village.setUpgradeLevel("villager-storage", nextLevel);
        villageManager.saveVillage(village);
        MessageUtil.send(player, configManager.getPrefix(),
                "&aVillager-Lager aufgewertet: &e" + currentSlots + " -> " + getVillagerInventoryMaxSlots(village) + " Slots");
        return true;
    }

    private void handleMembersClick(Player player, InventoryClickEvent event, int slot, String data) {
        // data kann "villageId" oder "villageId:memberId" sein
        String villageId = data;
        UUID managedMemberId = null;
        
        if (data.contains(":")) {
            String[] parts = data.split(":", 2);
            villageId = parts[0];
            try {
                managedMemberId = UUID.fromString(parts[1]);
            } catch (IllegalArgumentException e) {
                player.closeInventory();
                return;
            }
        }
        
        Village village = villageManager.getVillage(UUID.fromString(villageId)).orElse(null);
        if (village == null) { player.closeInventory(); return; }

        if (slot == 49) {
            if (managedMemberId != null) {
                // Zurück aus Member-Manage GUI
                guiManager.openMembersGui(player, village);
            } else {
                // Zurück aus Member-Liste GUI
                guiManager.openMainGui(player, village);
            }
            return;
        }

        // Wenn wir ein spezifisches Mitglied verwalten
        if (managedMemberId != null) {
            handleSingleMemberManage(player, event, slot, village, managedMemberId);
            return;
        }

        VillageMember viewer = village.getMember(player.getUniqueId());
        boolean canManage = player.hasPermission("village.admin")
                || villageManager.canManageMembers(village, player.getUniqueId());

        // Handle member management - öffne Member-Manage GUI
        if (canManage && slot >= 0 && slot < 36) {
            List<Map.Entry<java.util.UUID, VillageMember>> members = new ArrayList<>(village.getMembers().entrySet());
            members.sort(Comparator.comparing(entry -> {
                String name = org.bukkit.Bukkit.getOfflinePlayer(entry.getKey()).getName();
                return name != null ? name.toLowerCase() : entry.getKey().toString();
            }));
            if (slot < members.size()) {
                UUID memberId = members.get(slot).getKey();
                guiManager.openMemberManageGui(player, village, memberId);
                return;
            }
        }

        // Handle join requests
        if (canManage && slot >= 36 && slot <= 44) {
            handleJoinRequestsInMembersClick(player, event, slot, villageId);
        }
    }

    private void handleSingleMemberManage(Player player, InventoryClickEvent event, int slot, Village village, UUID memberId) {
        VillageMember viewer = village.getMember(player.getUniqueId());
        boolean canManage = player.hasPermission("village.admin")
                || villageManager.canManageMembers(village, player.getUniqueId());
        
        if (!canManage) {
            player.closeInventory();
            return;
        }
        
        VillageMember member = village.getMember(memberId);
        if (member == null) {
            player.closeInventory();
            return;
        }
        
        // Zurück Button
        if (slot == 22) {
            guiManager.openMembersGui(player, village);
            return;
        }
        
        // Links: Entfernen
        if (event.isLeftClick() && slot == 11) {
            // Gründer kann nicht entfernt werden
            if (village.isFounder(memberId)) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cDer Gründer kann nicht entfernt werden!");
                return;
            }
            String memberName = org.bukkit.Bukkit.getOfflinePlayer(memberId).getName();
            if (memberName == null) memberName = memberId.toString().substring(0, 8);
            MessageUtil.sendYesNoRunCommand(player, configManager.getPrefix(),
                    "&eMitglied &f" + memberName + " &eaus dem Dorf entfernen?",
                    "/village memberremoveconfirm ja " + village.getId() + " " + memberId,
                    "/village memberremoveconfirm nein " + village.getId() + " " + memberId);
            player.closeInventory();
            return;
        }
        
        // Rolle ändern
        if (slot == 13) {
            // Gründer kann Rolle nicht gewechselt werden
            if (village.isFounder(memberId)) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cDie Gründer-Rolle kann nicht gewechselt werden!");
                return;
            }
            if (!player.hasPermission("village.admin") && !villageManager.canAssignRoles(village, player.getUniqueId())) {
                showBarrierFeedback(player, "&cDu darfst keine Rollen vergeben.");
                return;
            }
            guiManager.openMemberRolesGui(player, village, memberId);
            return;
        }
    }

    private void handleJoinRequestsInMembersClick(Player player, InventoryClickEvent event, int slot, String data) {
        String villageId = data;
        Village village = villageManager.getVillage(UUID.fromString(villageId)).orElse(null);
        if (village == null) { player.closeInventory(); return; }

        VillageMember viewer = village.getMember(player.getUniqueId());
        boolean canManage = player.hasPermission("village.admin")
                || villageManager.canManageMembers(village, player.getUniqueId());
        
        if (!canManage || slot < 36 || slot > 44) {
            return;
        }

        List<Map.Entry<UUID, VillageJoinRequest>> requests = new ArrayList<>(village.getJoinRequests().entrySet());
        requests.sort(java.util.Comparator.comparingLong(entry -> entry.getValue().getRequestedAt()));
        int requestIndex = slot - 36;
        if (requestIndex < 0 || requestIndex >= requests.size()) {
            return;
        }

        UUID requesterId = requests.get(requestIndex).getKey();
        String requesterName = org.bukkit.Bukkit.getOfflinePlayer(requesterId).getName();
        if (requesterName == null) requesterName = requesterId.toString().substring(0, 8);

        if (event.isLeftClick()) {
            if (villageManager.acceptJoinRequest(village, requesterId)) {
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("join-request-accepted-admin")
                                .replace("%player%", requesterName)
                                .replace("%name%", village.getName()));
                Player target = org.bukkit.Bukkit.getPlayer(requesterId);
                if (target != null && target.isOnline()) {
                    MessageUtil.send(target, configManager.getPrefix(),
                            configManager.message("join-request-accepted")
                                    .replace("%name%", village.getName()));
                    if (plugin.getLightService() != null) {
                        plugin.getLightService().refreshPlayer(target);
                    }
                }
            } else {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cDie Anfrage konnte nicht angenommen werden.");
            }
        } else if (event.isRightClick()) {
            if (villageManager.declineJoinRequest(village, requesterId)) {
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("join-request-declined-admin")
                                .replace("%player%", requesterName)
                                .replace("%name%", village.getName()));
                Player target = org.bukkit.Bukkit.getPlayer(requesterId);
                if (target != null && target.isOnline()) {
                    MessageUtil.send(target, configManager.getPrefix(),
                            configManager.message("join-request-declined")
                                    .replace("%name%", village.getName()));
                }
            } else {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cDie Anfrage konnte nicht ablehnt werden.");
            }
        }
        
        guiManager.openMembersGui(player, village);
    }

    private void handleMemberRolesClick(Player player, int slot, String data) {
        if (data == null || !data.contains(":")) return;
        String[] parts = data.split(":", 2);
        Village village = villageManager.getVillage(UUID.fromString(parts[0])).orElse(null);
        if (village == null) return;
        UUID memberId = UUID.fromString(parts[1]);
        VillageMember target = village.getMember(memberId);
        if (target == null) return;
        if (slot == 49) {
            guiManager.openMemberManageGui(player, village, memberId);
            return;
        }
        if (!player.hasPermission("village.admin") && !villageManager.canAssignRoles(village, player.getUniqueId())) {
            showBarrierFeedback(player, "&cDu darfst keine Rollen vergeben.");
            return;
        }
        VillageRole[] configurable = {
                VillageRole.HR, VillageRole.BAUMEISTER, VillageRole.BUILDER, VillageRole.HAENDLER, VillageRole.TRAINER
        };
        int idx = (slot - 10) / 2;
        if (slot >= 10 && slot <= 18 && (slot % 2 == 0) && idx >= 0 && idx < configurable.length) {
            VillageRole role = configurable[idx];
            String upg = role.upgradeKey();
            if (upg != null && village.getUpgradeLevel(upg) <= 0) {
                showBarrierFeedback(player, "&cDiese Rolle ist noch nicht freigeschaltet.");
                return;
            }
            java.util.Set<VillageRole> roles = new java.util.HashSet<>(target.getRoles());
            if (roles.contains(role)) roles.remove(role); else roles.add(role);
            roles.remove(VillageRole.FOUNDER);
            roles.add(VillageRole.MEMBER);
            villageManager.setMemberRoles(village, memberId, roles);
            guiManager.openMemberRolesGui(player, village, memberId);
            return;
        }
        if (slot == 40) {
            if (!village.isFounder(player.getUniqueId()) && !player.hasPermission("village.admin")) {
                showBarrierFeedback(player, "&cNur der Gruender kann die Gruender-Rolle uebergeben.");
                return;
            }
            if (memberId.equals(player.getUniqueId()) || village.isFounder(memberId)) {
                showBarrierFeedback(player, "&cWaehle ein anderes Mitglied.");
                return;
            }
            if (villageManager.transferFounder(village, village.getFounderId(), memberId)) {
                MessageUtil.send(player, configManager.getPrefix(), "&aGruender-Rolle uebertragen.");
                guiManager.openMembersGui(player, village);
            } else {
                MessageUtil.send(player, configManager.getPrefix(), "&cUebertragung fehlgeschlagen.");
            }
        }
    }

    private void handleBarrierFeedbackClick(Player player, int slot) {
        if (slot == 8) {
            player.closeInventory();
        }
    }

    private void showBarrierFeedback(Player player, String message) {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.BARRIER_FEEDBACK);
        org.bukkit.inventory.Inventory inv = org.bukkit.Bukkit.createInventory(holder, 9, MessageUtil.color("&cKeine Berechtigung"));
        inv.setItem(4, new com.example.village.util.ItemBuilder(Material.BARRIER)
                .name("&cNicht verfuegbar")
                .lore(message, "&7Diese Option ist gesperrt.")
                .build());
        inv.setItem(8, new com.example.village.util.ItemBuilder(Material.ARROW)
                .name("&7Zurueck")
                .build());
        player.openInventory(inv);
    }

    public Map<UUID, PendingJobAssignment> getPendingJobAssignments() {
        return pendingJobAssignments;
    }

    public Map<UUID, PendingBuildingPlacement> getPendingBuildingPlacements() {
        return pendingBuildingPlacements;
    }

    public Map<UUID, PendingSignEdit> getPendingSignEdits() {
        return pendingSignEdits;
    }

    public void openVillageGui(Player player, Village village) {
        guiManager.openMainGui(player, village);
    }

    public static final class PendingJobAssignment {
        private final Village village;
        private final CustomVillager villager;

        public PendingJobAssignment(Village village, CustomVillager villager) {
            this.village = village;
            this.villager = villager;
        }

        public Village getVillage() { return village; }
        public CustomVillager getVillager() { return villager; }
    }

    public static final class PendingBuildingPlacement {
        private final Village village;
        private final String typeKey;
        private final String direction;

        public PendingBuildingPlacement(Village village, String typeKey, String direction) {
            this.village = village;
            this.typeKey = typeKey;
            this.direction = direction;
        }

        public Village getVillage() { return village; }
        public String getTypeKey() { return typeKey; }
        public String getDirection() { return direction; }
    }

    public static final class PendingSignEdit {
        private final UUID villageId;
        private final UUID buildingId;

        public PendingSignEdit(UUID villageId, UUID buildingId) {
            this.villageId = villageId;
            this.buildingId = buildingId;
        }

        public UUID getVillageId() { return villageId; }
        public UUID getBuildingId() { return buildingId; }
    }
}
