package com.example.village;

import com.example.village.api.VillageApi;
import com.example.village.command.GlobalMoneyCommand;
import com.example.village.command.TradeCommand;
import com.example.village.command.VillageCommand;
import com.example.village.config.CurrencyConfigManager;
import com.example.village.config.DebugConfigManager;
import com.example.village.config.VillageConfigManager;
import com.example.village.config.VillageLightConfigManager;
import com.example.village.data.VillageDatabaseManager;
import com.example.village.gui.GuiManager;
import com.example.village.integration.CurrencyIntegrationManager;
import com.example.village.listener.PlayerJoinLeaveListener;
import com.example.village.listener.PathEffectListener;
import com.example.village.listener.WorkstationInteractListener;
import com.example.village.service.AestheticScoreService;
import com.example.village.service.BuildingChestManager;
import com.example.village.service.BuildingConfigLoader;
import com.example.village.service.PathService;
import com.example.village.service.PlayerNutritionService;
import com.example.village.service.ProductionService;
import com.example.village.listener.PlayerTradeInteractListener;
import com.example.village.listener.VillagerGuiClickListener;
import com.example.village.hook.CitizensHook;
import com.example.village.hook.BlueMapHook;
import com.example.village.hook.ProtocolLibHook;
import com.example.village.hook.VaultHook;
import com.example.village.hook.WorldEditHook;
import com.example.village.hook.WorldGuardHook;
import com.example.village.listener.BellInteractListener;
import com.example.village.listener.BellProtectionListener;
import com.example.village.listener.BorderManageSelectionListener;
import com.example.village.listener.BlockSelectionListener;
import com.example.village.listener.AreaPermissionListener;
import com.example.village.listener.BorderWalkListener;
import com.example.village.listener.BuildingInteractListener;
import com.example.village.listener.BuildingSignPlacementListener;
import com.example.village.listener.BedAssignmentListener;
import com.example.village.listener.ChatInputListener;
import com.example.village.listener.GuiClickListener;
import com.example.village.listener.InventoryClickEventListener;
import com.example.village.listener.JobAssignmentListener;
import com.example.village.listener.SignInteractListener;
import com.example.village.listener.VillagerDeathListener;
import com.example.village.listener.VillageDiscoveryListener;
import com.example.village.listener.VillageLightListener;
import com.example.village.listener.VillagePointsListener;
import com.example.village.listener.VillagerInteractListener;
import com.example.village.service.BorderPreviewService;
import com.example.village.service.BorderService;
import com.example.village.service.BorderValidationService;
import com.example.village.service.BuildingService;
import com.example.village.service.VillagerContractService;
import com.example.village.service.DialogueSystem;
import com.example.village.service.CurrencyService;
import com.example.village.service.EconomyService;
import com.example.village.service.LevelService;
import com.example.village.service.QuestManager;
import com.example.village.service.SkillTreeManager;
import com.example.village.service.StateEngine;
import com.example.village.service.UpgradeService;
import com.example.village.service.VillageLightService;
import com.example.village.service.VillageManager;
import com.example.village.service.VillagerService;
import com.example.village.service.VillagerNutritionService;
import com.example.village.service.VillagerTickService;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class VillagePlugin extends JavaPlugin {

    private VillageConfigManager configManager;
    private DebugConfigManager debugConfigManager;
    private VillageLightConfigManager lightConfigManager;
    private VillageDatabaseManager databaseManager;
    private VillageManager villageManager;
    private BorderService borderService;
    private BorderPreviewService previewService;
    private BuildingService buildingService;
    private VillagerService villagerService;
    private VillagerContractService villagerContractService;
    private com.example.village.service.VillagerGlowService villagerGlowService;
    private LevelService levelService;
    private UpgradeService upgradeService;
    private EconomyService economyService;
    private VillageLightService lightService;
    private GuiManager guiManager;
    private VillageApi api;

    // Neuanfüge: Advanced Villager System
    private StateEngine stateEngine;
    private VillagerNutritionService villagerNutritionService;
    private com.example.village.service.VillagerManager advancedVillagerManager;
    private VillagerTickService villagerTickerService;
    private QuestManager questManager;
    private SkillTreeManager skillTreeManager;
    private DialogueSystem dialogueSystem;
    private VillagerGuiClickListener villagerGuiClickListener;

    // Hooks
    private VaultHook vaultHook;
    private WorldEditHook worldEditHook;
    private WorldGuardHook worldGuardHook;
    private CitizensHook citizensHook;
    private ProtocolLibHook protocolLibHook;
    private BlueMapHook blueMapHook;

    private CurrencyService currencyService;
    private CurrencyConfigManager currencyConfigManager;
    private PlayerJoinLeaveListener playerJoinLeaveListener;
    private GlobalMoneyCommand globalMoneyCommand;
    private CurrencyIntegrationManager currencyIntegrationManager;
    private InventoryClickEventListener inventoryClickEventListener;
    private TradeCommand tradeCommand;
    private VillageDiscoveryListener villageDiscoveryListener;

    // ── Neues Gebäude-System (area-/WB-basiert) ───────────────────
    private BuildingConfigLoader buildingConfigLoader;
    private AestheticScoreService aestheticScoreService;
    private BuildingChestManager buildingChestManager;
    private ProductionService productionService;
    private PathService pathService;
    private PlayerNutritionService playerNutritionService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        debugConfigManager = new DebugConfigManager(this);
        debugConfigManager.load();

        // Stelle sicher dass config/ Ordner existiert
        File configDir = new File(getDataFolder(), "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        // Frisch installierte Server sollen die Standard-Configs immer im config/-Ordner erhalten.
        File questsFile = new File(configDir, "quests-and-villagers.yml");
        if (!questsFile.exists()) {
            saveResource("config/quests-and-villagers.yml", false);
        }

        File currenciesFile = new File(configDir, "currencies.yml");
        if (!currenciesFile.exists()) {
            saveResource("config/currencies.yml", false);
        }

        File lightFile = new File(configDir, "light-limits.yml");
        if (!lightFile.exists()) {
            saveResource("config/light-limits.yml", false);
        }
        File debugFile = new File(configDir, "debug.yml");
        if (!debugFile.exists()) {
            saveResource("config/debug.yml", false);
        }
        File villageFile = new File(configDir, "village.yml");
        if (!villageFile.exists()) {
            saveResource("config/village.yml", false);
        }
        File playersFile = new File(configDir, "players.yml");
        if (!playersFile.exists()) {
            saveResource("config/players.yml", false);
        }
        File buildingsFile = new File(configDir, "buildings.yml");
        if (!buildingsFile.exists()) {
            saveResource("config/buildings.yml", false);
        }
        File villagersFile = new File(configDir, "villagers.yml");
        if (!villagersFile.exists()) {
            saveResource("config/villagers.yml", false);
        }

        setupSchematicsFolder();

        // Config
        configManager = new VillageConfigManager(this);
        configManager.loadAll();
        MessageUtil.setTextResolver(key -> configManager.text(key, null));
        lightConfigManager = new VillageLightConfigManager(this);
        lightConfigManager.load();

        // Neuer BuildingConfigLoader – liest categories-Sektion aus buildings.yml
        buildingConfigLoader = new BuildingConfigLoader(this);
        buildingConfigLoader.load();
        aestheticScoreService = new AestheticScoreService(buildingConfigLoader);

        // Hooks
        vaultHook = new VaultHook();
        vaultHook.setup(getLogger());

        worldEditHook = new WorldEditHook();
        worldEditHook.setup(getLogger());

        worldGuardHook = new WorldGuardHook();
        worldGuardHook.setup(getLogger());

        citizensHook = new CitizensHook(this);
        citizensHook.setup(getLogger());

        protocolLibHook = new ProtocolLibHook();
        protocolLibHook.setup(getLogger());
        
        blueMapHook = new BlueMapHook();
        blueMapHook.setup(getLogger());

        // Data
        databaseManager = new VillageDatabaseManager(this);
        databaseManager.initialize();

        // Services
        villageManager = new VillageManager(this, configManager, databaseManager, null, worldGuardHook);
        // Entdeckte Dörfer aus persistierten Daten wiederherstellen (Fix: Re-Discovery nach Neustart)
        villageManager.rebuildPlayerDiscoveriesFromVillages();

        BorderValidationService validationService = new BorderValidationService();
        borderService = new BorderService(this, configManager, villageManager, validationService);

        currencyConfigManager = new CurrencyConfigManager(new File(getDataFolder(), "config/currencies.yml"), getLogger());
        currencyConfigManager.loadConfig();
        currencyService = new CurrencyService(this, currencyConfigManager, vaultHook);
        villageManager.setCurrencyService(currencyService);

        economyService = new EconomyService(this, configManager, vaultHook);
        upgradeService = new UpgradeService(this, configManager, villageManager, economyService, currencyService);
        levelService = new LevelService(this, configManager, villageManager, vaultHook, currencyService);

        buildingService = new BuildingService(this, configManager, villageManager,
                economyService, currencyService, worldEditHook, worldGuardHook, blueMapHook);
        buildingService.setAestheticScoreService(aestheticScoreService);
        villageManager.setBuildingService(buildingService);
        migrateMissingVillageCenters();
        previewService = new BorderPreviewService(this, configManager, buildingService);

        villagerService = new VillagerService(this, configManager, villageManager, citizensHook);
        villagerContractService = new VillagerContractService(this, villageManager, databaseManager);
        villagerService.setContractService(villagerContractService);

        lightService = new VillageLightService(this, configManager, lightConfigManager, villageManager,
                protocolLibHook, worldGuardHook);

        villagerNutritionService = new VillagerNutritionService(this, configManager);
        stateEngine = new StateEngine(this, villagerNutritionService);
        villagerService.setNutritionService(villagerNutritionService);
        villagerTickerService = new VillagerTickService(this, stateEngine, villageManager);
        villagerTickerService.setNutritionService(villagerNutritionService);

        advancedVillagerManager = new com.example.village.service.VillagerManager(
                this, citizensHook, villageManager, stateEngine);
        questManager = new QuestManager(this, villageManager, vaultHook, currencyService);
        villagerNutritionService.setQuestManager(questManager);
        skillTreeManager = new SkillTreeManager(this, vaultHook);
        dialogueSystem = new DialogueSystem(this);
        currencyIntegrationManager = new CurrencyIntegrationManager(this);

        // ── Neues Gebäude-System ──────────────────────────────────
        buildingChestManager = new BuildingChestManager(this, villageManager, buildingConfigLoader);
        villagerNutritionService.setChestManager(buildingChestManager);
        productionService    = new ProductionService(this, villageManager, buildingConfigLoader, buildingChestManager);
        pathService          = new PathService(this, villageManager, buildingConfigLoader);
        currencyIntegrationManager.initialize();
        inventoryClickEventListener = new InventoryClickEventListener(currencyIntegrationManager, getLogger());
        inventoryClickEventListener.setQuestManager(questManager);
        tradeCommand = new TradeCommand(this, currencyIntegrationManager, inventoryClickEventListener, getLogger());

        playerNutritionService = new PlayerNutritionService(this, configManager);
        playerNutritionService.start();

        // GUI
        guiManager = new GuiManager(this, configManager, vaultHook, villageManager);
        guiManager.setPreviewService(previewService);

        // Villager Glow Service
        villagerGlowService = new com.example.village.service.VillagerGlowService(this, guiManager, villageManager, configManager);

        playerJoinLeaveListener = new PlayerJoinLeaveListener(currencyService, getLogger());

        // Villager GUI Click Listener (neu: implementiert Job/Rename/Trade/Transfer/etc.)
        villagerGuiClickListener = new VillagerGuiClickListener(
                this, advancedVillagerManager, skillTreeManager, questManager, villageManager);

        // API
        api = new VillageApi(villageManager, levelService);

        // Listeners
        GuiClickListener guiClickListener = new GuiClickListener(this, configManager,
                villageManager, guiManager, borderService, buildingService,
                upgradeService, villagerService, vaultHook, previewService,
                currencyIntegrationManager, inventoryClickEventListener);

        registerListeners(guiClickListener);
        
        // Register PlayerHotbarSlotListener for hotbar detail visualization
        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new com.example.village.listener.PlayerHotbarSlotListener(playerNutritionService), this);

        // Commands
        VillageCommand villageCommand = new VillageCommand(this, configManager, villageManager,
                guiManager, levelService, previewService, buildingService, villagerService, vaultHook, lightService,
                borderService, worldGuardHook, guiClickListener, pathService, currencyService);
        globalMoneyCommand = new GlobalMoneyCommand(configManager, villageManager, currencyService, vaultHook);

        var villageCmd = getCommand("village");
        if (villageCmd != null) {
            villageCmd.setExecutor(villageCommand);
            villageCmd.setTabCompleter(villageCommand);
        }

        var sendMoneyCmd = getCommand("sendmoney");
        if (sendMoneyCmd != null) {
            sendMoneyCmd.setExecutor(globalMoneyCommand);
            sendMoneyCmd.setTabCompleter(globalMoneyCommand);
        }
        var balanceCmd = getCommand("balance");
        if (balanceCmd != null) {
            balanceCmd.setExecutor(globalMoneyCommand);
            balanceCmd.setTabCompleter(globalMoneyCommand);
        }
        var balancesCmd = getCommand("balances");
        if (balancesCmd != null) {
            balancesCmd.setExecutor(globalMoneyCommand);
            balancesCmd.setTabCompleter(globalMoneyCommand);
        }
        var tradeCmd = getCommand("trade");
        if (tradeCmd != null) {
            tradeCmd.setExecutor(tradeCommand);
            tradeCmd.setTabCompleter(tradeCommand);
        }

        // Hunger scoreboard command
        var hungerCmd = getCommand("hunger");
        if (hungerCmd != null) {
            hungerCmd.setExecutor(new com.example.village.command.HungerCommand(playerNutritionService, configManager));
        }

        // Start villager AI tasks
        villagerService.startVillagerTasks();
        villagerTickerService.start();  // Starte neuen advanced Tick-Service
        lightService.start();
        productionService.start();      // Starte Produktionszyklus

        // Placement timeout checker (every second)
        Bukkit.getScheduler().runTaskTimer(this,
                () -> buildingService.checkPlacementTimeouts(), 20L, 20L);

        // Autosave task (every 5 minutes, sync to avoid ConcurrentModificationException)
        Bukkit.getScheduler().runTaskTimer(this,
                () -> villageManager.saveAll(), 6000L, 6000L);

        getLogger().info("Village Plugin v" + getDescription().getVersion() + " aktiviert.");
    }

    public DebugConfigManager getDebugConfigManager() {
        return debugConfigManager;
    }

    public VillageDiscoveryListener getVillageDiscoveryListener() {
        return villageDiscoveryListener;
    }

    public boolean shouldUseBundledResources() {
        return debugConfigManager != null && debugConfigManager.isBackupConfigsEnabled();
    }

    /**
     * Migration: For existing villages that pre-date the dorfzentrum-as-building feature,
     * auto-registers the dorfzentrum VillageBuilding at the bell location and places its sign.
     */
    private void migrateMissingVillageCenters() {
        if (databaseManager == null || buildingService == null) return;
        for (com.example.village.model.Village village : databaseManager.getVillages().values()) {
            boolean hasCenter = village.getBuildings().stream()
                    .anyMatch(b -> "dorfzentrum".equals(b.getTypeKey()));
            if (hasCenter) continue;

            org.bukkit.Location bell = village.getBellLocation();
            if (bell == null || bell.getWorld() == null) {
                getLogger().warning("[Village] Dorfzentrum-Migration: Dorf '" + village.getName()
                        + "' hat keine gültige Bell-Location – übersprungen.");
                continue;
            }

            com.example.village.model.VillageBuilding center =
                    new com.example.village.model.VillageBuilding(
                            java.util.UUID.randomUUID(), "dorfzentrum", bell);
            center.setCompleted(true);
            center.setOwnerId(village.getFounderId());
            center.setDirection("N");
            center.setTypeOrdinal(1);
            village.addBuilding(center);

            buildingService.initVillageCenterSign(village, center);
            getLogger().info("[Village] Dorfzentrum für Dorf '" + village.getName() + "' nachregistriert.");
        }
    }

    public void onDisable() {
        if (previewService != null) previewService.clearAllPreviews();
        if (villagerTickerService != null) villagerTickerService.stop();
        if (villagerService != null) villagerService.stopVillagerTasks();
        if (lightService != null) lightService.stop();
        if (productionService != null) productionService.stop();
        if (playerNutritionService != null) playerNutritionService.stop();
        if (buildingChestManager != null) buildingChestManager.save();
        if (currencyService != null) currencyService.save();
        if (currencyIntegrationManager != null) currencyIntegrationManager.shutdown();
        if (questManager != null) questManager.savePlayerQuestData();
        if (villageManager != null) villageManager.saveAll();
        if (databaseManager != null) databaseManager.shutdown();

        getLogger().info("Village Plugin deaktiviert.");
    }

    private void registerListeners(GuiClickListener guiClickListener) {
        var pm = Bukkit.getPluginManager();

        pm.registerEvents(new BellInteractListener(configManager, villageManager, guiManager, guiClickListener), this);
        pm.registerEvents(new com.example.village.listener.PlayerNutritionListener(playerNutritionService), this);
        pm.registerEvents(new BellProtectionListener(configManager, villageManager), this);
        pm.registerEvents(new AreaPermissionListener(configManager, villageManager, worldGuardHook), this);
        pm.registerEvents(new BorderWalkListener(this, configManager, borderService, previewService, villageManager), this);
        pm.registerEvents(new com.example.village.listener.BorderCoordClickListener(borderService, previewService), this);
        pm.registerEvents(playerJoinLeaveListener, this);
        pm.registerEvents(guiClickListener, this);
        pm.registerEvents(new com.example.village.listener.LevelupGuiClickListener(
                villageManager, levelService, configManager, vaultHook, guiManager), this);
        pm.registerEvents(new PlayerTradeInteractListener(tradeCommand), this);
        pm.registerEvents(inventoryClickEventListener, this);
        ChatInputListener chatInputListener = new ChatInputListener(this, configManager, villageManager, borderService,
                previewService, buildingService, guiClickListener, vaultHook, currencyIntegrationManager);
        pm.registerEvents(chatInputListener, this);
        villageDiscoveryListener = new VillageDiscoveryListener(villageManager, configManager, this, levelService);
        villageDiscoveryListener.setBuildingService(buildingService);
        pm.registerEvents(villageDiscoveryListener, this);
        // Villager GUI Listener registrieren
        pm.registerEvents(villagerGuiClickListener, this);
        // ChatInputListener mit Villager-Rename verknüpfen
        chatInputListener.setVillagerGuiClickListener(villagerGuiClickListener, advancedVillagerManager);
        pm.registerEvents(new BuildingInteractListener(configManager, buildingService), this);
        pm.registerEvents(new VillagePointsListener(villageManager, levelService), this);
        pm.registerEvents(new VillageLightListener(this, lightService, villageManager), this);
        pm.registerEvents(new VillagerInteractListener(this, configManager, villageManager,
                villagerService, guiManager, guiClickListener), this);
        pm.registerEvents(new JobAssignmentListener(this, configManager, villageManager,
                villagerService, guiClickListener), this);
        pm.registerEvents(new BedAssignmentListener(this, configManager, villageManager,
                villagerService, guiClickListener, guiManager), this);
        pm.registerEvents(new SignInteractListener(this, villageManager, guiManager), this);
        pm.registerEvents(new BlockSelectionListener(buildingService, villageManager), this);
        pm.registerEvents(new BorderManageSelectionListener(configManager, villageManager, borderService, previewService), this);
        pm.registerEvents(new BuildingSignPlacementListener(buildingService, villageManager, configManager), this);
        pm.registerEvents(new VillagerDeathListener(this, villageManager, citizensHook), this);

        // ── Neues Gebäude-System ──────────────────────────────────
        pm.registerEvents(new PathEffectListener(pathService), this);
        pm.registerEvents(new WorkstationInteractListener(this, villageManager, buildingConfigLoader,
            buildingChestManager, productionService, pathService, guiClickListener), this);
    }

    /**
     * Creates the schematics folder and extracts default schematics from the plugin JAR.
     * This is similar to saveDefaultConfig() but for the schematics directory.
     */
    private void setupSchematicsFolder() {
        java.io.File schematicsFolder = new java.io.File(getDataFolder(), "schematics");
        
        // Create schematics folder if it doesn't exist
        if (!schematicsFolder.exists()) {
            if (schematicsFolder.mkdirs()) {
                getLogger().info("Schematics-Ordner erstellt: " + schematicsFolder.getPath());
            } else {
                getLogger().warning("Konnte Schematics-Ordner nicht erstellen!");
                return;
            }
        }

        // List of all default schematics (try to extract from JAR if available)
        String[] defaultSchematics = {
                "schematics/house.schem",
                "schematics/farm.schem",
                "schematics/shop.schem",
                "schematics/barracks.schem",
                "schematics/watchtower.schem",
                "schematics/wall.schem",
                "schematics/factory.schem",
                "schematics/storage.schem",
                "schematics/road.schem",
                "schematics/library.schem",
                "schematics/tavern.schem",
                "schematics/marketplace.schem"
        };

        int extractedCount = 0;
        for (String schematicPath : defaultSchematics) {
            java.io.InputStream input = getResource(schematicPath);
            if (input == null) {
                // Schematic not found in JAR - this is fine, admin can add it manually
                continue;
            }

            String fileName = schematicPath.substring(schematicPath.lastIndexOf("/") + 1);
            java.io.File outputFile = new java.io.File(schematicsFolder, fileName);

            // Only extract if the file doesn't already exist (user might have modified it)
            if (!outputFile.exists()) {
                try {
                    java.nio.file.Files.copy(input, outputFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    getLogger().info("Standard-Schematic extrahiert: " + fileName);
                    extractedCount++;
                } catch (java.io.IOException e) {
                    getLogger().warning("Fehler beim Extrahieren der Schematic: " + fileName);
                    e.printStackTrace();
                } finally {
                    try {
                        input.close();
                    } catch (java.io.IOException ignored) {
                    }
                }
            }
        }

        if (extractedCount > 0) {
            getLogger().info("Es wurden " + extractedCount + " Standard-Schematics extrahiert.");
        } else {
            getLogger().info("Keine neuen Standard-Schematics gefunden (möglicherweise bereits vorhanden oder nicht im JAR enthalten).");
        }
    }

    // --- Getters for other plugins ---

    public VillageConfigManager getVillageConfigManager() { return configManager; }
    public VillageManager getVillageManager() { return villageManager; }
    public VillageLightService getLightService() { return lightService; }
    public VillageApi getApi() { return api; }
    public VaultHook getVaultHook() { return vaultHook; }
    public WorldEditHook getWorldEditHook() { return worldEditHook; }
    public WorldGuardHook getWorldGuardHook() { return worldGuardHook; }
    public CitizensHook getCitizensHook() { return citizensHook; }
    public BlueMapHook getBlueMapHook() { return blueMapHook; }
    public LevelService getLevelService() { return levelService; }
    public CurrencyService getCurrencyService() { return currencyService; }
    public BuildingService getBuildingService() { return buildingService; }
    public VillagerService getVillagerService() { return villagerService; }
    public GuiManager getGuiManager() { return guiManager; }
    public com.example.village.service.VillagerGlowService getVillagerGlowService() { return villagerGlowService; }

    // Neue Advanced Villager System Getter
    public StateEngine getStateEngine() { return stateEngine; }
    public VillagerNutritionService getVillagerNutritionService() { return villagerNutritionService; }
    public com.example.village.service.VillagerManager getAdvancedVillagerManager() { return advancedVillagerManager; }
    public VillagerContractService getVillagerContractService() { return villagerContractService; }
    public QuestManager getQuestManager() { return questManager; }
    public SkillTreeManager getSkillTreeManager() { return skillTreeManager; }
    public DialogueSystem getDialogueSystem() { return dialogueSystem; }
    public VillagerGuiClickListener getVillagerGuiClickListener() { return villagerGuiClickListener; }
    public InventoryClickEventListener getInventoryClickEventListener() { return inventoryClickEventListener; }
    public VillageConfigManager getConfigManager() { return configManager; }

    // Neues Gebäude-System
    public BuildingConfigLoader getBuildingConfigLoader()  { return buildingConfigLoader; }
    public AestheticScoreService getAestheticScoreService() { return aestheticScoreService; }
    public BuildingChestManager getBuildingChestManager()  { return buildingChestManager; }
    public ProductionService getProductionService()        { return productionService; }
    public PathService getPathService()                    { return pathService; }
}
