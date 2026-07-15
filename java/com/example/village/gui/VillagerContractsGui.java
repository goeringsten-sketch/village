package com.example.village.gui;

import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.model.VillagerContract;
import com.example.village.util.ItemBuilder;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI für das "Aufträge"-Menü eines Dorfbewohners.
 *
 * Implementiert den im Ausbauplan beschriebenen Lebenszyklus:
 * offen -> aktiv -> abgeschlossen / abgebrochen. Zeigt alle Verträge, in denen
 * dieser Dorfbewohner als Empfänger oder Lieferant beteiligt ist, und erlaubt
 * das Annehmen, Abbrechen und Neuanlegen von Standardaufträgen.
 */
public final class VillagerContractsGui implements InventoryHolder {

    public static final int SLOT_NEW_CONTRACT = 49;
    public static final int SLOT_BACK = 53;
    private static final int LIST_SIZE = 45;

    private final Village village;
    private final CustomVillager villager;
    private final Player player;
    private final Map<Integer, UUID> slotToContract = new LinkedHashMap<>();
    private Inventory inventory;

    public VillagerContractsGui(Village village, CustomVillager villager, Player player) {
        this.village = village;
        this.villager = villager;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 54,
                MessageUtil.text("ui.villager-contracts.title-prefix", "&6Aufträge: ") + villager.getName());
        slotToContract.clear();

        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler());
        }

        List<VillagerContract> relevant = new ArrayList<>();
        for (VillagerContract contract : village.getContracts()) {
            if (villager.getId().equals(contract.getRequesterVillagerId())
                    || villager.getId().equals(contract.getSupplierVillagerId())) {
                relevant.add(contract);
            }
        }
        relevant.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));

        int slot = 0;
        for (VillagerContract contract : relevant) {
            if (slot >= LIST_SIZE) break;
            inventory.setItem(slot, buildContractItem(contract));
            slotToContract.put(slot, contract.getId());
            slot++;
        }

        if (relevant.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.PAPER)
                    .name(MessageUtil.text("ui.villager-contracts.empty", "&7Keine Aufträge vorhanden"))
                    .lore(MessageUtil.text("ui.villager-contracts.empty-lore", "&7Erstelle unten einen neuen Auftrag."))
                    .build());
        }

        inventory.setItem(SLOT_NEW_CONTRACT, new ItemBuilder(Material.WRITABLE_BOOK)
                .name(MessageUtil.text("ui.villager-contracts.new", "&aNeuer Auftrag"))
                .lore(
                        MessageUtil.text("ui.villager-contracts.new-lore-1", "&7Erstellt einen Standardauftrag"),
                        MessageUtil.text("ui.villager-contracts.new-lore-2", "&7zwischen diesem Beruf und"),
                        MessageUtil.text("ui.villager-contracts.new-lore-3", "&7einem passenden Partner."),
                        MessageUtil.text("ui.villager-contracts.new-lore-4", "&7Startet als 'Offen' - muss"),
                        MessageUtil.text("ui.villager-contracts.new-lore-5", "&7noch angenommen werden.")
                )
                .build());

        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name(MessageUtil.text("ui.generic.back", "&7Zurück"))
                .build());

        player.openInventory(inventory);
    }

    private ItemStack buildContractItem(VillagerContract contract) {
        boolean isRequester = villager.getId().equals(contract.getRequesterVillagerId());
        String role = isRequester
                ? MessageUtil.text("ui.villager-contracts.role-requester", "Empfänger")
                : MessageUtil.text("ui.villager-contracts.role-supplier", "Lieferant");
        String partnerName = findPartnerName(contract, isRequester);

        Material icon = contract.getMaterial() != null ? contract.getMaterial() : Material.PAPER;
        String statusColor = switch (contract.getStatus()) {
            case OPEN -> "&e";
            case ACTIVE -> "&b";
            case COMPLETED -> "&a";
            case CANCELLED -> "&c";
        };
        String statusLabel = switch (contract.getStatus()) {
            case OPEN -> MessageUtil.text("ui.villager-contracts.status-open", "Offen");
            case ACTIVE -> MessageUtil.text("ui.villager-contracts.status-active", "Aktiv");
            case COMPLETED -> MessageUtil.text("ui.villager-contracts.status-completed", "Abgeschlossen");
            case CANCELLED -> MessageUtil.text("ui.villager-contracts.status-cancelled", "Abgebrochen");
        };

        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.text("ui.villager-contracts.type", "&7Typ: &f") + contract.getType().name());
        lore.add(MessageUtil.text("ui.villager-contracts.amount", "&7Menge: &f") + contract.getAmount() + "x " + icon.name());
        lore.add(MessageUtil.text("ui.villager-contracts.role", "&7Rolle: &f") + role);
        lore.add(MessageUtil.text("ui.villager-contracts.partner", "&7Partner: &f") + partnerName);
        lore.add(statusColor + statusLabel);
        lore.add(MessageUtil.text("ui.generic.empty", " "));
        if (contract.getStatus() == VillagerContract.Status.OPEN) {
            lore.add(MessageUtil.text("ui.villager-contracts.accept-hint", "&aLinksklick: Annehmen"));
            lore.add(MessageUtil.text("ui.villager-contracts.cancel-hint", "&cRechtsklick: Abbrechen"));
        } else if (contract.getStatus() == VillagerContract.Status.ACTIVE) {
            lore.add(MessageUtil.text("ui.villager-contracts.active-hint-1", "&7Wird automatisch abgewickelt,"));
            lore.add(MessageUtil.text("ui.villager-contracts.active-hint-2", "&7sobald Ware verfügbar ist."));
            lore.add(MessageUtil.text("ui.villager-contracts.cancel-hint", "&cRechtsklick: Abbrechen"));
        }

        return new ItemBuilder(icon)
                .name(statusColor + contract.getNote())
                .lore(lore)
                .glow(contract.getStatus() == VillagerContract.Status.ACTIVE)
                .build();
    }

    private String findPartnerName(VillagerContract contract, boolean isRequester) {
        UUID partnerId = isRequester ? contract.getSupplierVillagerId() : contract.getRequesterVillagerId();
        if (partnerId == null) return "-";
        for (CustomVillager v : village.getVillagers()) {
            if (v.getId().equals(partnerId)) {
                return v.getName();
            }
        }
        return "-";
    }

    private ItemStack filler() {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(MessageUtil.text("ui.generic.empty", " "))
                .build();
    }

    /** Liefert die Vertrags-UUID für einen Listen-Slot (oder null außerhalb der Liste). */
    public UUID getContractIdForSlot(int slot) {
        return slotToContract.get(slot);
    }

    public Village getVillage() { return village; }
    public CustomVillager getVillager() { return villager; }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
