package com.example.village.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class VillageMenuHolder implements InventoryHolder {

    public enum MenuType {
        FOUNDING,
        MAIN,
        RELATIONS,
        RELATION_TARGETS,
        BORDER_SELECTION,
        BUILDINGS,
        BUILDING_DETAIL,
        BUILDING_DIRECTION,
        BUILDING_CATEGORIES,
        BUILDING_TYPE_OPTIONS,
        BUILDING_BUILD_VARIANTS,
        BUILDING_MANAGE_TYPE,
        BUILDING_WHITELIST,
        UPGRADES,
        ROLE_UPGRADES,
        MEMBER_ROLES,
        UPGRADE_DETAIL,
        VILLAGERS,
        VILLAGER_DETAIL,
        VILLAGER_CONFIG,
        VILLAGER_PROFESSION,
        VILLAGER_JOB_DETAILS,
        VILLAGER_CONTRACTS,
        VILLAGER_PRODUCTION,
        VILLAGER_TRADE,
        MEMBERS,
        QUESTS,
        CONFIRM,
        LEVELUP,
        BARRIER_FEEDBACK
    }

    private final MenuType menuType;
    private final String extraData;

    public VillageMenuHolder(MenuType menuType) {
        this.menuType = menuType;
        this.extraData = null;
    }

    public VillageMenuHolder(MenuType menuType, String extraData) {
        this.menuType = menuType;
        this.extraData = extraData;
    }

    public MenuType getMenuType() {
        return menuType;
    }

    public String getExtraData() {
        return extraData;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
