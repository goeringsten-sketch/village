package com.example.village.listener;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.hook.VaultHook;
import com.example.village.model.BuildingType;
import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.model.VillageBorder;
import com.example.village.model.VillageBuilding;
import com.example.village.service.BorderPreviewService;
import com.example.village.service.BorderService;
import com.example.village.service.BuildingService;
import com.example.village.service.VillageManager;
import com.example.village.service.VillagerManager;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Handles chat-based input for village naming and coordinate border selection.
 * All Bukkit API calls are dispatched to the main thread via Bukkit.getScheduler().runTask().
 */
public final class ChatInputListener implements Listener {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VillageManager villageManager;
    private final BorderService borderService;
    private final BorderPreviewService previewService;
    private final BuildingService buildingService;
    private final GuiClickListener guiClickListener;
    private final VaultHook vaultHook;
    // Neu: Villager-spezifischer GUI-Listener
    private VillagerGuiClickListener villagerGuiClickListener;
    private VillagerManager villagerManager;

    private final com.example.village.integration.CurrencyIntegrationManager currencyIntegrationManager;

    public ChatInputListener(VillagePlugin plugin,
                             VillageConfigManager configManager,
                             VillageManager villageManager,
                             BorderService borderService,
                             BorderPreviewService previewService,
                             BuildingService buildingService,
                             GuiClickListener guiClickListener,
                             VaultHook vaultHook,
                             com.example.village.integration.CurrencyIntegrationManager currencyIntegrationManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.villageManager = villageManager;
        this.borderService = borderService;
        this.previewService = previewService;
        this.buildingService = buildingService;
        this.guiClickListener = guiClickListener;
        this.vaultHook = vaultHook;
        this.currencyIntegrationManager = currencyIntegrationManager;
    }

    /** Setzt den VillagerGuiClickListener für Villager-Umbenennung und Job-Auswahl. */
    public void setVillagerGuiClickListener(VillagerGuiClickListener villagerGuiClickListener,
                                             VillagerManager villagerManager) {
        this.villagerGuiClickListener = villagerGuiClickListener;
        this.villagerManager = villagerManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String message = event.getMessage().trim();

        plugin.getDebugConfigManager().debug("chat", "ChatListener event: Player=" + player.getName() + ", Message='" + message + "'");

        // Handle border confirmation
        if (borderService.hasPendingBorderConfirmation(uuid) || borderService.hasPendingBorderActionConfirmation(uuid)) {
            event.setCancelled(true);

            if (message.equalsIgnoreCase("nein")) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (borderService.hasPendingBorderActionConfirmation(uuid)) {
                        BorderService.PendingBorderActionConfirmation confirmation =
                                borderService.removePendingBorderActionConfirmation(uuid);
                        if (confirmation != null) {
                            previewService.clearPreview(player);
                        }
                    } else {
                        BorderService.PendingBorderConfirmation confirmation =
                                borderService.removePendingBorderConfirmation(uuid);
                        if (confirmation != null) {
                            previewService.clearPreview(player);
                        }
                    }
                });
                MessageUtil.send(player, configManager.getPrefix(),
                        "&eGrenzsetzung abgebrochen.");
                return;
            }

            if (message.equalsIgnoreCase("ja")) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (borderService.hasPendingBorderActionConfirmation(uuid)) {
                        BorderService.PendingBorderActionConfirmation confirmation =
                                borderService.removePendingBorderActionConfirmation(uuid);
                        if (confirmation == null) {
                            MessageUtil.send(player, configManager.getPrefix(),
                                    "&cKeine ausstehende Grenzsetzung gefunden.");
                            return;
                        }
                        Village village = confirmation.getVillage();
                        if (confirmation.getTargetBorderId() != null) {
                            int targetId = confirmation.getTargetBorderId();
                            village.getBorders().removeIf(border -> border.getId() == targetId);
                        }
                        for (VillageBorder border : confirmation.getBorders()) {
                            if (border.getId() <= 0) {
                                border.setId(village.nextBorderId());
                            }
                            village.addBorder(border);
                        }
                        villageManager.saveVillage(village);
                        MessageUtil.send(player, configManager.getPrefix(),
                                configManager.message("border-set"));
                        return;
                    }
                    BorderService.PendingBorderConfirmation confirmation =
                            borderService.removePendingBorderConfirmation(uuid);
                    if (confirmation == null) {
                        MessageUtil.send(player, configManager.getPrefix(),
                                "&cKeine ausstehende Grenzsetzung gefunden.");
                        return;
                    }
                    confirmation.getVillage().addBorder(confirmation.getBorder());
                    villageManager.saveVillage(confirmation.getVillage());
                    MessageUtil.send(player, configManager.getPrefix(),
                            configManager.message("border-set"));
                });
                return;
            }

            // Invalid input - remind player
            MessageUtil.send(player, null, "&7Bitte klicke &a[JA] &7oder &c[NEIN]&7 (oder schreibe: ja / nein).");
            return;
        }

        // Handle villager renaming (neu: Villager umbenennen per Chat)
        if (villagerGuiClickListener != null) {
            Map<UUID, UUID> pendingVillagerRenames = villagerGuiClickListener.getPendingVillagerRenames();
            if (pendingVillagerRenames.containsKey(uuid)) {
                event.setCancelled(true);
                UUID villagerId = pendingVillagerRenames.get(uuid);

                if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("abbrechen")) {
                    pendingVillagerRenames.remove(uuid);
                    MessageUtil.send(player, configManager.getPrefix(), "&eUmbenennung abgebrochen.");
                    return;
                }

                if (message.length() < 2 || message.length() > 20) {
                    MessageUtil.send(player, configManager.getPrefix(),
                            "&cDer Name muss zwischen 2 und 20 Zeichen lang sein!");
                    return;
                }

                final String newVillagerName = message;
                pendingVillagerRenames.remove(uuid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    CustomVillager villager = villagerManager.getVillager(villagerId);
                    if (villager == null) {
                        MessageUtil.send(player, configManager.getPrefix(), "&cVillager nicht gefunden.");
                        return;
                    }
                    String oldName = villager.getName();
                    villager.setName(newVillagerName);
                    // Persistenz: Dorf speichern
                    if (villager.getParentVillageId() != null) {
                        villageManager.getVillage(villager.getParentVillageId())
                                .ifPresent(villageManager::saveVillage);
                    }
                    MessageUtil.send(player, configManager.getPrefix(),
                            "&aVillager umbenannt: &e" + oldName + " &a-> &e" + newVillagerName);
                });
                return;
            }
        }

        Map<UUID, UUID> pendingBuildingRenames = guiClickListener.getPendingBuildingRenames();
        if (pendingBuildingRenames.containsKey(uuid)) {
            event.setCancelled(true);
            UUID buildingId = pendingBuildingRenames.remove(uuid);

            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("abbrechen")) {
                MessageUtil.send(player, configManager.getPrefix(), "&eUmbenennung abgebrochen.");
                return;
            }

            if (message.length() < 3 || message.length() > 32) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cDer Name muss zwischen 3 und 32 Zeichen lang sein! Versuche es erneut.");
                return;
            }

            final String newName = message.trim();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Village village = villageManager.getAllVillages().stream()
                        .filter(v -> v.getBuildings().stream().anyMatch(b -> b.getId().equals(buildingId)))
                        .findFirst().orElse(null);
                if (village == null) {
                    MessageUtil.send(player, configManager.getPrefix(), "&cGebäude nicht gefunden.");
                    return;
                }
                VillageBuilding building = village.getBuildings().stream()
                        .filter(b -> buildingId.equals(b.getId()))
                        .findFirst().orElse(null);
                if (building == null) {
                    MessageUtil.send(player, configManager.getPrefix(), "&cGebäude nicht gefunden.");
                    return;
                }
                building.setCustomName(newName);
                villageManager.saveVillage(village);
                plugin.getDebugConfigManager().debug("building", "Renamed building " + buildingId + " to '" + newName + "' for " + player.getName());
                MessageUtil.send(player, configManager.getPrefix(),
                        "&aGebäude umbenannt: &e" + building.getCustomName());
                guiClickListener.getGuiManager().openBuildingDetailGui(player, village, buildingId);
            });
            return;
        }

        Map<UUID, Boolean> pendingBuildingSearches = guiClickListener.getPendingBuildingSearches();
        if (pendingBuildingSearches.containsKey(uuid)) {
            event.setCancelled(true);
            pendingBuildingSearches.remove(uuid);

            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("abbrechen")) {
                MessageUtil.send(player, configManager.getPrefix(), "&eSuche abgebrochen.");
                return;
            }

            final String query = message.trim();
            plugin.getDebugConfigManager().debug("building", "Building search query for " + player.getName() + ": '" + query + "'");
            Bukkit.getScheduler().runTask(plugin, () -> {
                guiClickListener.getGuiManager().setBuildingSearchQuery(player, query);
                Village playerVillage = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
                if (playerVillage == null) {
                    MessageUtil.send(player, configManager.getPrefix(), "&cKein Dorf gefunden.");
                    return;
                }
                guiClickListener.getGuiManager().openBuildingManageGui(player, playerVillage);
            });
            return;
        }

        // Handle village renaming
        Map<UUID, UUID> pendingRenames = guiClickListener.getPendingRenames();
        if (pendingRenames.containsKey(uuid)) {
            event.setCancelled(true);
            UUID villageId = pendingRenames.get(uuid);

            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("abbrechen")) {
                pendingRenames.remove(uuid);
                MessageUtil.send(player, configManager.getPrefix(), "&eUmbenennung abgebrochen.");
                return;
            }

            if (message.length() < 3 || message.length() > 24) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cDer Name muss zwischen 3 und 24 Zeichen lang sein! Versuche es erneut.");
                return;
            }

            if (villageManager.getVillageByName(message).isPresent()) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cEin Dorf mit diesem Namen existiert bereits! Versuche es erneut.");
                return;
            }

            final String newName = message;
            pendingRenames.remove(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Village village = villageManager.getVillage(villageId).orElse(null);
                if (village == null) {
                    MessageUtil.send(player, configManager.getPrefix(),
                            "&cDorf nicht gefunden.");
                    return;
                }
                String oldName = village.getName();
                village.setName(newName);
                villageManager.saveVillage(village);
                MessageUtil.send(player, configManager.getPrefix(),
                        "&aDorf umbenannt: &e" + oldName + " &a-> &e" + newName);
            });
            return;
        }

        // Handle village naming
        Map<UUID, Location> pendingFoundings = guiClickListener.getPendingFoundings();
        if (pendingFoundings.containsKey(uuid)) {
            event.setCancelled(true);
            Location bellLoc = pendingFoundings.remove(uuid);

            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("abbrechen")) {
                MessageUtil.send(player, configManager.getPrefix(), "&eDorfgruendung abgebrochen.");
                return;
            }

            // Validate name
            if (message.length() < 3 || message.length() > 24) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cDer Name muss zwischen 3 und 24 Zeichen lang sein!");
                return;
            }

            if (villageManager.getVillageByName(message).isPresent()) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cEin Dorf mit diesem Namen existiert bereits!");
                return;
            }

            // Dispatch all Bukkit API calls to the main thread
            final String villageName = message;
            final Location finalBellLoc = bellLoc;
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Deduct costs
                double globalCost = configManager.getFoundingGlobalCost();
                if (globalCost > 0 && vaultHook.isAvailable()) {
                    vaultHook.withdraw(player, globalCost);
                }
                double localCost = configManager.getFoundingLocalCost();
                if (localCost > 0) {
                    villageManager.getPlayerVillage(player.getUniqueId())
                            .ifPresent(currentVillage -> {
                                String localCurrencyId = plugin.getCurrencyService().getVillageCurrencyId(currentVillage);
                                plugin.getCurrencyService().removeBalance(player.getUniqueId(), localCurrencyId, localCost);
                                plugin.getCurrencyService().save();
                            });
                }

                // Remove required items
                for (Map<String, Object> req : configManager.getFoundingItemRequirements()) {
                    String matName = String.valueOf(req.get("material"));
                    Object amountObj = req.get("amount");
                    if (amountObj == null) continue;
                    int amount = ((Number) amountObj).intValue();
                    if (amount <= 0) continue; // Validiere dass amount > 0
                    Material mat = Material.matchMaterial(matName);
                    if (mat != null) {
                        player.getInventory().removeItem(
                                new org.bukkit.inventory.ItemStack(mat, amount));
                    }
                }

                // Create village
                Village village;
                try {
                    village = villageManager.createVillage(villageName, player, finalBellLoc);
                } catch (IllegalArgumentException ex) {
                    MessageUtil.send(player, configManager.getPrefix(),
                            "&cEin Dorf mit diesem Namen existiert bereits!");
                    return;
                }
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("village-created")
                                .replace("%name%", village.getName()));

                if (currencyIntegrationManager != null) {
                    currencyIntegrationManager.onVillageCreated(village);
                }

                // Show border preview
                previewService.showPreview(player, village.getBorder(), player.getWorld());

                if (plugin.getLightService() != null) {
                    plugin.getLightService().refreshPlayer(player);
                }
            });
            return;
        }

        // Handle completed building sign template editing
        Map<UUID, GuiClickListener.PendingSignEdit> pendingSignEdits = guiClickListener.getPendingSignEdits();
        if (pendingSignEdits.containsKey(uuid)) {
            event.setCancelled(true);

            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("abbrechen")) {
                pendingSignEdits.remove(uuid);
                MessageUtil.send(player, configManager.getPrefix(), "&eBeschriftung abgebrochen.");
                return;
            }

            GuiClickListener.PendingSignEdit edit = pendingSignEdits.remove(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Village village = villageManager.getVillage(edit.getVillageId()).orElse(null);
                if (village == null) {
                    MessageUtil.send(player, configManager.getPrefix(), "&cDorf nicht gefunden.");
                    return;
                }
                VillageBuilding building = village.getBuildings().stream()
                        .filter(b -> edit.getBuildingId().equals(b.getId()))
                        .findFirst()
                        .orElse(null);
                if (building == null) {
                    MessageUtil.send(player, configManager.getPrefix(), "&cGebäude nicht gefunden.");
                    return;
                }

                String[] parts = message.split("\\|", -1);
                if (parts.length != 4) {
                    MessageUtil.send(player, configManager.getPrefix(),
                            "&cBitte genau 4 Zeilen angeben, getrennt mit |");
                    // Put back into edit mode
                    pendingSignEdits.put(uuid, edit);
                    return;
                }

                String template = String.join("|", parts);
                building.setSignTemplate(template);
                villageManager.saveVillage(village);

                // Apply to existing sign if present
                if (building.getSignLocation() != null) {
                    buildingService.applyBuildingSignTemplate(player, building, configManager);
                }

                MessageUtil.send(player, configManager.getPrefix(), "&aSchild-Beschriftung gespeichert.");
                // Return to building detail GUI
                guiClickListener.openVillageGui(player, village);
            });
            return;
        }

        // Handle building confirmation (Step 2 of two-step flow)
        // Also handle border expansion confirmation
        if (buildingService.hasPendingConfirmation(uuid) || 
            buildingService.getBorderExpansionConfirmations().containsKey(uuid)) {
            event.setCancelled(true);
            
            plugin.getLogger().info("ChatInput: Entered Building/BorderExpansion handler");
            plugin.getLogger().info("  - hasPendingConfirmation: " + buildingService.hasPendingConfirmation(uuid));
            plugin.getLogger().info("  - hasBorderExpansion: " + buildingService.getBorderExpansionConfirmations().containsKey(uuid));

            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("abbrechen")
                    || message.equalsIgnoreCase("nein")) {
                // If we are in border expansion mode, let the border expansion handler below process it.
                if (!buildingService.getBorderExpansionConfirmations().containsKey(uuid)) {
                    plugin.getLogger().info("ChatInput: Cancel/Nein Handler (nicht in BorderExpansion)");
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        BuildingService.PendingBuildingConfirmation pending = buildingService.getPendingConfirmation(uuid);
                        buildingService.cancelBuildingPreview(player);
                        guiClickListener.getPendingBuildingPlacements().remove(uuid);
                        if (pending != null) {
                            // Open village menu back
                            guiClickListener.openVillageGui(player, pending.getVillage());
                        }
                    });
                    return;
                }
                // fall through to border expansion handler
            }

            if (message.equalsIgnoreCase("ja") || message.equalsIgnoreCase("yes")
                    || message.equalsIgnoreCase("bestaetigen") || message.equalsIgnoreCase("bestätigen")) {
                // Only handle normal confirmation if NOT in border expansion mode
                if (!buildingService.getBorderExpansionConfirmations().containsKey(uuid)) {
                    plugin.getLogger().info("ChatInput: Building Confirmation Handler - 'ja/bestätigen' erkannt");
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        BuildingService.PendingBuildingConfirmation pending =
                                buildingService.getPendingConfirmation(uuid);
                        plugin.getLogger().info("ChatInput: getPendingConfirmation result: " + (pending != null ? "FOUND" : "NULL"));
                        
                        if (pending == null) {
                            MessageUtil.send(player, configManager.getPrefix(),
                                    "&cKeine ausstehende Gebaeudeplatzierung gefunden.");
                            return;
                        }

                        plugin.getLogger().info("ChatInput: Rufe confirmBuilding() auf...");
                        // Confirm: clear preview, register building, place sign
                        BuildingService.PlaceResult result = buildingService.confirmBuilding(player);
                        plugin.getLogger().info("ChatInput: confirmBuilding() returned: " + result);

                        switch (result) {
                            case BORDER_EXPANSION_NEEDED -> {
                                plugin.getLogger().info("ChatInput: BORDER_EXPANSION_NEEDED - warte auf ja/nein");
                                // Dialog already shown in confirmBuilding()
                                // Wait for "ja" or "abbrechen"
                                // Do NOT return here - let the next chat input (ja/nein) be processed
                            }
                            case SUCCESS -> {
                                plugin.getLogger().info("ChatInput: Building placement SUCCESS");
                                // Message is sent by confirmBuilding/finalizeBuildingPlacement - don't duplicate here
                            }
                            case NOT_ENOUGH_MONEY -> {
                                plugin.getLogger().info("ChatInput: NOT_ENOUGH_MONEY");
                                MessageUtil.send(player, configManager.getPrefix(),
                                        configManager.message("upgrade-too-expensive"));
                            }
                            case BUILDING_TOO_FAR -> {
                                plugin.getLogger().info("ChatInput: BUILDING_TOO_FAR");
                                MessageUtil.send(player, configManager.getPrefix(),
                                        "&cDas Gebaeude liegt zu weit entfernt von der Dorfglocke!");
                            }
                            case OUTSIDE_BORDER -> {
                                plugin.getLogger().info("ChatInput: OUTSIDE_BORDER");
                                MessageUtil.send(player, configManager.getPrefix(),
                                        "&cDas Gebaeude muss mindestens mit 1 Block im Dorf liegen.");
                            }
                            case ON_START_BORDER -> {
                                MessageUtil.send(player, configManager.getPrefix(),
                                        "&cDas Gebaeude darf nicht auf dem Dorfbrunnen oder auf einem anderen Gebäude/Baustelle platziert werden.");
                            }
                            case com.example.village.service.BuildingService.PlaceResult.OVERLAPS_BUILDING -> {
                                String diag = buildingService.getLastPlacementDiagnostic(player.getUniqueId());
                                String base = "&cDas Gebäude überschneidet sich mit einem bestehenden Gebäude oder Bauplatz.";
                                if (diag != null && !diag.isBlank()) base += " &7(" + diag + ")";
                                MessageUtil.send(player, configManager.getPrefix(), base);
                                buildingService.clearLastPlacementDiagnostic(player.getUniqueId());
                            }
                            case INSIDE_DEFAULT_BORDER -> {
                                MessageUtil.send(player, configManager.getPrefix(),
                                        "&cDas Gebaeude darf nicht vollstaendig nur in der Standard-Grenze (ID 0) liegen,"
                                                + " wenn bereits erweiterte Gebiete existieren.");
                            }
                            default -> {
                                plugin.getLogger().info("ChatInput: Default error case");
                                MessageUtil.send(player, configManager.getPrefix(),
                                    "&cGebaeude konnte nicht platziert werden.");
                            }
                        }
                    });
                    return;
                }
                // If in border expansion mode, fall through to border expansion handler below
            }

            // --- Handle border expansion confirmation (ja/nein) ---
            if (buildingService.getBorderExpansionConfirmations().containsKey(uuid)) {
                event.setCancelled(true);

                if (message.equalsIgnoreCase("ja") || message.equalsIgnoreCase("yes")) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().info("ChatInput: Border Expansion Handler - 'ja' erkannt");
                        BuildingService.PlaceResult result = buildingService.confirmBorderExpansion(player);
                        plugin.getLogger().info("ChatInput: confirmBorderExpansion() returned: " + result);
                        
                        if (result == BuildingService.PlaceResult.SUCCESS) {
                            // Only remove after successful completion
                            buildingService.getBorderExpansionConfirmations().remove(uuid);
                            BuildingService.PendingBuildingConfirmation pending = 
                                    buildingService.getPendingConfirmation(uuid);
                            if (pending != null) {
                                String name = buildingService.getDisplayNameForType(pending.getTypeKey());
                                MessageUtil.send(player, configManager.getPrefix(),
                                        configManager.message("building-placed")
                                                .replace("%building%", name));
                                MessageUtil.send(player, configManager.getPrefix(),
                                        "&7Baue das Gebaeude nach der Vorschau. "
                                                + "Nutze das Schild vor dem Gebaeude fuer Optionen.");
                            }
                        } else if (result == BuildingService.PlaceResult.ON_START_BORDER) {
                            MessageUtil.send(player, configManager.getPrefix(),
                                    "&cDas Gebaeude darf nicht auf dem Dorfbrunnen oder auf einem anderen Gebäude/Baustelle platziert werden.");
                        } else if (result == BuildingService.PlaceResult.INSIDE_DEFAULT_BORDER) {
                            MessageUtil.send(player, configManager.getPrefix(),
                                    "&cDas Gebaeude darf nicht vollstaendig nur in der Standard-Grenze (ID 0) liegen,"
                                            + " wenn bereits erweiterte Gebiete existieren.");
                        } else {
                            plugin.getLogger().info("ChatInput: confirmBorderExpansion() fehlgeschlagen: " + result);
                            MessageUtil.send(player, configManager.getPrefix(),
                                    "&cGebaeude-Platzierung fehlgeschlagen: " + result);
                        }
                    });
                    return;
                }

                if (message.equalsIgnoreCase("nein") || message.equalsIgnoreCase("no") || 
                    message.equalsIgnoreCase("abbr") || message.equalsIgnoreCase("abbrechen")) {
                    plugin.getLogger().info("ChatInput: Border Expansion Handler - 'nein' erkannt");
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        BuildingService.BorderExpansionConfirmation expansionState = 
                                buildingService.getBorderExpansionConfirmations().remove(uuid);
                        if (expansionState != null) {
                            // Clear any remaining preview
                            buildingService.cancelBuildingPreview(player);
                            // Open village menu
                            guiClickListener.openVillageGui(player, expansionState.getVillage());
                        }
                    });
                    return;
                }

                MessageUtil.send(player, configManager.getPrefix(),
                        "&7Schreibe &aja &7um fortzufahren oder &aabbrechen &7zum Ablehnen.");
                return;
            }

            // Invalid input - remind player
            MessageUtil.send(player, configManager.getPrefix(),
                    "&7Schreibe &aja &7oder &abestaetigen &7um das Gebaeude zu bestaetigen, "
                    + "oder &cabbrechen &7zum Abbrechen.");
            return;
        }

        // Handle building placement (Step 1 - show preview)
        Map<UUID, GuiClickListener.PendingBuildingPlacement> pendingBuildings =
                guiClickListener.getPendingBuildingPlacements();
        if (pendingBuildings.containsKey(uuid)) {
            plugin.getLogger().info("=== ChatInput: Spieler hat PendingBuildingPlacement, message='" + message + "' ===");
            event.setCancelled(true);

            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("abbrechen")) {
                plugin.getLogger().info("ChatInput: 'cancel/abbrechen' erkannt");
                pendingBuildings.remove(uuid);
                MessageUtil.send(player, configManager.getPrefix(),
                        "&eGebaeudeplatzierung abgebrochen.");
                return;
            }

            if (message.equalsIgnoreCase("platzieren") || message.equalsIgnoreCase("place")) {
                pendingBuildings.remove(uuid);
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cDieser Flow wurde umgestellt. Bitte Rechtsklick auf einen Block zum Platzieren nutzen.");
                return;
            }

            MessageUtil.send(player, configManager.getPrefix(),
                    "&7Schreibe &aplatzieren&7 um das Gebaeude zu platzieren, "
                            + "oder &cabbrechen&7 zum Abbrechen.");
            return;
        }
        
        // DEBUG: Wenn kein PendingBuildingPlacement, log es
        plugin.getLogger().info("ChatInput: Keine PendingBuildingPlacement fuer " + player.getName() + " - message war: '" + message + "'");

        // Handle coordinate input for border selection
        if (borderService.isInCoordSession(uuid)) {
            event.setCancelled(true);

            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("abbrechen")) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    borderService.cancelCoordSelection(uuid);
                });
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("border-walk-stop"));
                return;
            }

            // Dispatch border operations to the main thread
            final String coordMessage = message;
            Bukkit.getScheduler().runTask(plugin, () -> {
                BorderService.CoordSession session = borderService.getCoordSession(uuid);
                if (session == null) return;

                boolean success = borderService.handleCoordInput(player, coordMessage);

                if (!success) {
                    MessageUtil.send(player, configManager.getPrefix(),
                            "&cUngueltige Eingabe! Format: x z (z.B. 100 200)");
                    return;
                }

                if (session.getPoint1() != null && session.getPoint2() == null) {
                    MessageUtil.send(player, configManager.getPrefix(),
                            configManager.message("coord-prompt-2"));
                } else if (session.getPoint2() != null) {
                    // Session vor finishCoordSelection holen (wird darin entfernt)
                    BorderService.CoordSession sessionForError = borderService.getCoordSession(player.getUniqueId());
                    VillageBorder border = borderService.finishCoordSelection(player);
                    if (border != null) {
                        previewService.showPreview(player, border, player.getWorld());
                        MessageUtil.send(player, configManager.getPrefix(),
                                configManager.message("border-preview-start"));
                        // Store border as pending - player must confirm via chat
                        borderService.addPendingBorderConfirmation(
                                uuid, session.getVillage(), border);
                        // No village prefix for border drawing flow
                        MessageUtil.sendYesNoRunCommand(
                                player,
                                null,
                                "&7Grenze setzen?",
                                "/village borderconfirm ja",
                                "/village borderconfirm nein"
                        );
                    } else {
                        // Fehlerursache aus der Session lesen (wurde vor finishCoordSelection gesichert)
                        String errMsg = (sessionForError != null && sessionForError.getLastError() != null)
                                ? sessionForError.getLastError()
                                : configManager.message("border-invalid")
                                        .replace("%size%", String.valueOf(configManager.getMinSquareSize()));
                        MessageUtil.send(player, configManager.getPrefix(), errMsg);
                        borderService.cancelCoordSelection(uuid);
                    }
                }
            });
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage() == null ? "" : event.getMessage().trim().toLowerCase();
        if (!"/village cancel".equals(command) && !"/village abbrechen".equals(command)) {
            return;
        }
        Player player = event.getPlayer();
        Map<UUID, GuiClickListener.PendingSignEdit> pendingSignEdits = guiClickListener.getPendingSignEdits();
        if (!pendingSignEdits.containsKey(player.getUniqueId())) {
            return;
        }
        pendingSignEdits.remove(player.getUniqueId());
        event.setCancelled(true);
        MessageUtil.send(player, configManager.getPrefix(), "&eBeschriftung abgebrochen.");
    }
}
