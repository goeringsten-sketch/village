package com.example.village.gui;

import com.example.village.util.ItemBuilder;
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
                "§e§lBaumenü");

        setupItems(player);
    }

    private void setupItems(Player player) {
        // Row 1: Preview + Info
        // Slot 0: Empty
        // Slot 1: Preview toggle
        inventory.setItem(1, new ItemBuilder(Material.GLASS)
                .name("&b&lVorschau")
                .lore("&7Zeige oder verstecke", "&7die Gebäude-Vorschau")
                .build());

        // Slot 2: Empty
        // Slot 3: Info slot
        inventory.setItem(3, new ItemBuilder(Material.BOOK)
                .name("&6&lBaumenü")
                .lore("&7Wähle eine Option")
                .build());

        // Row 2: Actions
        // Slot 9: Pause/Resume
        if (isPaused) {
            inventory.setItem(9, new ItemBuilder(Material.GREEN_WOOL)
                    .name("&a&lFortsetzen")
                    .lore("&7Setze den Bau fort")
                    .build());
        } else {
            inventory.setItem(9, new ItemBuilder(Material.YELLOW_WOOL)
                    .name("&e&lUnterbrechen")
                    .lore("&7Pausiere den Bau")
                    .build());
        }

        // Slot 11: Confirm
        inventory.setItem(11, new ItemBuilder(Material.GREEN_CONCRETE)
                .name("&a&lFertigstellen")
                .lore("&7Prüfe und vervollständige", "&7den Bau")
                .build());

        // Slot 13: Villager assignment
        inventory.setItem(13, new ItemBuilder(Material.ARMOR_STAND)
                .name("&c&lVillager zuweisen")
                .lore("&7Weise diesen Bau", "&7einem Villager zu")
                .build());

        // Slot 15: Cancel
        inventory.setItem(15, new ItemBuilder(Material.RED_CONCRETE)
                .name("&c&lAbbrechen")
                .lore("&7Bricht den Bau ab", "&7und entfernt das Gebäude")
                .build());

        if (isAdmin) {
            inventory.setItem(17, new ItemBuilder(Material.COMMAND_BLOCK)
                    .name("&c&lAdmin: Sofort fertigstellen")
                    .lore("&7Setzt alle Vorschaubloecke direkt", "&7und markiert den Bau als fertig.")
                    .build());
        }

        // Row 3: Decorative items
        // Slot 26: Close button
        inventory.setItem(26, new ItemBuilder(Material.BARRIER)
                .name("&7&lGUI schließen")
                .lore("&7Drücke Q oder klicke hier")
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
