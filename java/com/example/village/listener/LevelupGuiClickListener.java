package com.example.village.listener;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.gui.GuiManager;
import com.example.village.gui.LevelupGui;
import com.example.village.gui.VillageMenuHolder;
import com.example.village.hook.VaultHook;
import com.example.village.model.Village;
import com.example.village.service.LevelService;
import com.example.village.service.VillageManager;
import com.example.village.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Optional;

/**
 * Behandelt Klicks im Levelup-Menü.
 */
public final class LevelupGuiClickListener implements Listener {

    private final VillageManager villageManager;
    private final LevelService levelService;
    private final VillageConfigManager configManager;
    private final VaultHook vaultHook;
    private final GuiManager guiManager;

    public LevelupGuiClickListener(VillageManager villageManager, LevelService levelService,
                                   VillageConfigManager configManager, VaultHook vaultHook,
                                   GuiManager guiManager) {
        this.villageManager = villageManager;
        this.levelService = levelService;
        this.configManager = configManager;
        this.vaultHook = vaultHook;
        this.guiManager = guiManager;
    }

    private String t(String path, String fallback) {
        return configManager.text(path, fallback);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof VillageMenuHolder)) return;

        VillageMenuHolder menuHolder = (VillageMenuHolder) holder;
        if (menuHolder.getMenuType() != VillageMenuHolder.MenuType.LEVELUP) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return; // Clicked outside inventory
        }

        Optional<Village> villageOptional = villageManager.getPlayerVillage(player.getUniqueId());
        if (villageOptional.isEmpty()) {
            MessageUtil.send(player, t("messages.village-not-found", "§cDorf nicht gefunden!"));
            player.closeInventory();
            return;
        }
        Village village = villageOptional.get();

        switch (slot) {
            case 45: // Back button
                openVillageMenu(player, village);
                break;
            case 49: // Levelup button
                String failureReason = levelService.getLevelupFailureReason(village, player);
                if (failureReason == null) {
                    performLevelup(player, village);
                } else {
                    MessageUtil.send(player, t("messages.levelup-failed-reason", "§c%reason%").replace("%reason%", failureReason));
                }
                break;
            case 53: // Close button
                player.closeInventory();
                break;
        }
    }

    private void performLevelup(Player player, Village village) {
        if (levelService.performLevelUp(village, player)) {
            MessageUtil.send(player, t("messages.levelup-success", "§aGlückwunsch! Dein Dorf ist jetzt Level §e%level%§a!")
                    .replace("%level%", String.valueOf(village.getLevel())));

            LevelupGui gui = new LevelupGui(
                    VillagePlugin.getPlugin(VillagePlugin.class),
                    player,
                    village,
                    levelService,
                    configManager,
                    vaultHook
            );
            gui.open();

            if (village.getBellLocation() != null && village.getBellLocation().getWorld() != null) {
                village.getBellLocation().getWorld().playSound(village.getBellLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
            }
        } else {
            MessageUtil.send(player, t("messages.levelup-failed", "§cLevelup fehlgeschlagen. Überprüfe deine Ressourcen!"));
        }
    }

    private void openVillageMenu(Player player, Village village) {
        if (guiManager != null) {
            guiManager.openMainGui(player, village);
        } else {
            player.closeInventory();
        }
    }
}
