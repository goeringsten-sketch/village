package com.example.village.gui;

import com.example.village.util.ItemBuilder;
import com.example.village.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * GUI for building menu when player right-clicks on a building sign.
 * Allows preview toggle, cancel, pause/resume, and villager assignment.
 */
public final class BuildingMenuGui implements InventoryHolder {

    private final Inventory inventory;
    private final UUID playerId;
    private final boolean isPaused;
    private final boolean isAdmin;

    public BuildingMenuGui(Player player, UUID playerId, boolean isPaused) {
        this.playerId = playerId;
        this.isPaused = isPaused;
        this.isAdmin = player.hasPermission("village.admin");

        this.inventory = org.bukkit.Bukkit.createInventory(this, 27,
                MessageUtil.text("ui.building-menu.title", "§e§lBaumenü"));

        setupItems(player);
    }

    private void setupItems(Player player) {
        // Row 1: Preview + Info
        // Slot 0: Empty
        // Slot 1: Preview toggle
        inventory.setItem(1, new ItemBuilder(Material.GLASS)
                .name(MessageUtil.text("ui.building-menu.preview", "&b&lVorschau"))
                .lore(MessageUtil.text("ui.building-menu.preview-lore-1", "&7Zeige oder verstecke"),
                        MessageUtil.text("ui.building-menu.preview-lore-2", "&7die Gebäude-Vorschau"))
                .build());

        // Slot 2: Empty
        // Slot 3: Info slot
        inventory.setItem(3, new ItemBuilder(Material.BOOK)
                .name(MessageUtil.text("ui.building-menu.info", "&6&lBaumenü"))
                .lore(MessageUtil.text("ui.building-menu.choose", "&7Wähle eine Option"))
                .build());

        // Row 2: Actions
        // Slot 9: Pause/Resume
        if (isPaused) {
            inventory.setItem(9, new ItemBuilder(Material.GREEN_WOOL)
                    .name(MessageUtil.text("ui.building-menu.resume", "&a&lFortsetzen"))
                    .lore(MessageUtil.text("ui.building-menu.resume-lore", "&7Setze den Bau fort"))
                    .build());
        } else {
            inventory.setItem(9, new ItemBuilder(Material.YELLOW_WOOL)
                    .name(MessageUtil.text("ui.building-menu.pause", "&e&lUnterbrechen"))
                    .lore(MessageUtil.text("ui.building-menu.pause-lore", "&7Pausiere den Bau"))
                    .build());
        }

        // Slot 11: Confirm
        inventory.setItem(11, new ItemBuilder(Material.GREEN_CONCRETE)
                .name(MessageUtil.text("ui.building-menu.confirm", "&a&lFertigstellen"))
                .lore(MessageUtil.text("ui.building-menu.confirm-lore-1", "&7Prüfe und vervollständige"),
                        MessageUtil.text("ui.building-menu.confirm-lore-2", "&7den Bau"))
                .build());

        // Slot 13: Villager assignment
        inventory.setItem(13, new ItemBuilder(Material.ARMOR_STAND)
                .name(MessageUtil.text("ui.building-menu.assign", "&c&lVillager zuweisen"))
                .lore(MessageUtil.text("ui.building-menu.assign-lore-1", "&7Weise diesen Bau"),
                        MessageUtil.text("ui.building-menu.assign-lore-2", "&7einem Villager zu"))
                .build());

        // Slot 15: Cancel
        inventory.setItem(15, new ItemBuilder(Material.RED_CONCRETE)
                .name(MessageUtil.text("ui.building-menu.cancel", "&c&lAbbrechen"))
                .lore(MessageUtil.text("ui.building-menu.cancel-lore-1", "&7Bricht den Bau ab"),
                        MessageUtil.text("ui.building-menu.cancel-lore-2", "&7und entfernt das Gebäude"))
                .build());

        if (isAdmin) {
            inventory.setItem(17, new ItemBuilder(Material.COMMAND_BLOCK)
                    .name(MessageUtil.text("ui.building-menu.admin-complete", "&c&lAdmin: Sofort fertigstellen"))
                    .lore(MessageUtil.text("ui.building-menu.admin-complete-lore-1", "&7Setzt alle Vorschaubloecke direkt"),
                            MessageUtil.text("ui.building-menu.admin-complete-lore-2", "&7und markiert den Bau als fertig."))
                    .build());
        }

        // Row 3: Decorative items
        // Slot 26: Close button
        inventory.setItem(26, new ItemBuilder(Material.BARRIER)
                .name(MessageUtil.text("ui.building-menu.close", "&7&lGUI schließen"))
                .lore(MessageUtil.text("ui.building-menu.close-lore", "&7Drücke Q oder klicke hier"))
                .build());
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public boolean isPaused() {
        return isPaused;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Determines what action to perform based on clicked slot.
     */
    public BuildingMenuAction getActionForSlot(int slot) {
        switch (slot) {
            case 1:
                return BuildingMenuAction.TOGGLE_PREVIEW;
            case 9:
                return isPaused ? BuildingMenuAction.RESUME : BuildingMenuAction.PAUSE;
            case 11:
                return BuildingMenuAction.CONFIRM;
            case 13:
                return BuildingMenuAction.ASSIGN_VILLAGER;
            case 15:
                return BuildingMenuAction.CANCEL;
            case 17:
                return BuildingMenuAction.ADMIN_FORCE_COMPLETE;
            case 26:
                return BuildingMenuAction.CLOSE;
            default:
                return BuildingMenuAction.NONE;
        }
    }

    public enum BuildingMenuAction {
        TOGGLE_PREVIEW,
        PAUSE,
        RESUME,
        CONFIRM,
        ASSIGN_VILLAGER,
        CANCEL,
        ADMIN_FORCE_COMPLETE,
        CLOSE,
        NONE
    }
}
