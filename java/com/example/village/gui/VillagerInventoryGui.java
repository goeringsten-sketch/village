package com.example.village.gui;

import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.util.ItemBuilder;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * GUI für Villager-Inventar
 * Zeigt virtuelle Items, die der Villager gesammelt hat
 */
public class VillagerInventoryGui implements InventoryHolder {
    public static final int SLOT_INFO = 45;
    public static final int SLOT_DEPOSIT_STACK = 46;
    public static final int SLOT_DEPOSIT_ONE = 47;
    public static final int SLOT_UPGRADE = 48;
    public static final int SLOT_BACK = 49;
    public static final int SLOT_PERMISSIONS = 50;

    private final CustomVillager villager;
    private final Village village;
    private final Player player;
    private final int maxSlots;
    private Inventory inventory;
    
    public VillagerInventoryGui(CustomVillager villager, Village village, Player player, int maxSlots) {
        this.villager = villager;
        this.village = village;
        this.player = player;
        this.maxSlots = maxSlots;
    }
    
    /**
     * Öffnet das Villager-Inventar
     */
    public void open() {
        int rows = (int)Math.ceil(maxSlots / 9.0);
        int storageSize = Math.max(9, (int)(rows * 9));
        int size = Math.max(54, Math.min(54, storageSize + 9));

        inventory = Bukkit.createInventory(this, size,
                MessageUtil.text("ui.villager-inventory.title-prefix", "§b") + villager.getName()
                        + MessageUtil.text("ui.villager-inventory.title-suffix", " - Inventar"));

        // Zeige Items
        int slot = 0;
        for (Map.Entry<Material, Integer> entry : villager.getInventory().entrySet()) {
            if (slot >= maxSlots) break;

            ItemStack item = new ItemStack(entry.getKey(), Math.min(64, entry.getValue()));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(MessageUtil.text("ui.villager-inventory.item-prefix", "§f") + entry.getKey().name());
                meta.setLore(Arrays.asList(
                        MessageUtil.text("ui.villager-inventory.amount", "§7Menge: §e") + entry.getValue(),
                        MessageUtil.text("ui.villager-inventory.left-click", "§7Linksklick: Entnehmen"),
                        MessageUtil.text("ui.villager-inventory.right-click", "§7Rechtsklick: Entnehmen (1)")
                ));
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
            slot++;
        }

        for (int i = slot; i < maxSlots; i++) {
            ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = pane.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(MessageUtil.text("ui.villager-inventory.free-slot", "§8Freier Speicherplatz"));
                pane.setItemMeta(meta);
            }
            inventory.setItem(i, pane);
        }

        for (int i = maxSlots; i < 45; i++) {
            ItemStack locked = new ItemStack(Material.BARRIER);
            ItemMeta meta = locked.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(MessageUtil.text("ui.villager-inventory.locked-slot", "§cGesperrter Slot"));
                meta.setLore(Arrays.asList(
                        MessageUtil.text("ui.villager-inventory.upgrade-required", "§7Dorf-Upgrade erforderlich"),
                        MessageUtil.text("ui.villager-inventory.upgrade-key", "§7Upgrade-Key: §evillager-storage")
                ));
                locked.setItemMeta(meta);
            }
            inventory.setItem(i, locked);
        }

        // Info-Slot
        inventory.setItem(SLOT_INFO, createInfoItem());
        inventory.setItem(SLOT_DEPOSIT_STACK, new ItemBuilder(Material.CHEST)
                .name(MessageUtil.text("ui.villager-inventory.deposit-stack", "§aStack einlagern"))
                .lore(MessageUtil.text("ui.villager-inventory.deposit-stack-lore", "§7Linksklick: Mainhand-Stack einlagern"))
                .build());
        inventory.setItem(SLOT_DEPOSIT_ONE, new ItemBuilder(Material.HOPPER)
                .name(MessageUtil.text("ui.villager-inventory.deposit-one", "§a1 Item einlagern"))
                .lore(MessageUtil.text("ui.villager-inventory.deposit-one-lore", "§7Linksklick: 1x Mainhand einlagern"))
                .build());
        inventory.setItem(SLOT_UPGRADE, new ItemBuilder(Material.ANVIL)
                .name(MessageUtil.text("ui.villager-inventory.upgrade", "§eLager aufwerten"))
                .lore(MessageUtil.text("ui.villager-inventory.upgrade-lore-1", "§7Erhoeht Slots in 9er-Schritten"),
                        MessageUtil.text("ui.villager-inventory.upgrade-lore-2", "§7Nur Gruender/Admin"))
                .build());

        // Zurück-Button
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name(MessageUtil.text("ui.generic.back", "§7Zurück"))
                .build());
        inventory.setItem(SLOT_PERMISSIONS, createPermissionItem());

        player.openInventory(inventory);
    }
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    
    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.villager-inventory.info", "§6Inventar-Info"));
            meta.setLore(Arrays.asList(
                    MessageUtil.text("ui.villager-inventory.villager", "§7Villager: §e") + villager.getName(),
                    MessageUtil.text("ui.villager-inventory.used-slots", "§7Belegte Slots: §e") + countUsedSlots() + "/" + maxSlots,
                    MessageUtil.text("ui.generic.empty", "§7"),
                    MessageUtil.text("ui.villager-inventory.left-click-stack", "§7Linksklick: Entnehmen (Stack)"),
                    MessageUtil.text("ui.villager-inventory.right-click-one", "§7Rechtsklick: Entnehmen (1)"),
                    MessageUtil.text("ui.villager-inventory.mainhand", "§7Mainhand kann eingelagert werden")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPermissionItem() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.text("ui.villager-inventory.permissions", "§bZugriffsrechte"));
            meta.setLore(Arrays.asList(
                    MessageUtil.text("ui.villager-inventory.permissions-1", "§7Entnehmen/Einlagern: §eGruender, Haendler, Admin"),
                    MessageUtil.text("ui.villager-inventory.permissions-2", "§7Upgrades: §eGruender, Admin")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private int countUsedSlots() {
        return villager.getInventory().size();
    }
    
    /**
     * Hole Villager-UUID für Identifier
     */
    public UUID getVillagerId() {
        return villager.getId();
    }
    
    public CustomVillager getVillager() {
        return villager;
    }
    
    public Village getVillage() {
        return village;
    }
}
