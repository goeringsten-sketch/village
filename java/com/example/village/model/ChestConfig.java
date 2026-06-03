package com.example.village.model;

/** Konfiguration für Truhen-Workstation-Blöcke. */
public final class ChestConfig {

    public enum Access { PUBLIC, VILLAGER_ONLY, PRIVATE }

    private final int initialSlots;
    private final int maxUpgradeableSlots;
    private final Access access;
    private final boolean villagerAccessible;

    public ChestConfig(int initialSlots, int maxUpgradeableSlots, Access access, boolean villagerAccessible) {
        this.initialSlots        = Math.max(9, Math.min(54, initialSlots));
        this.maxUpgradeableSlots = Math.max(this.initialSlots, Math.min(54, maxUpgradeableSlots));
        this.access              = access != null ? access : Access.PUBLIC;
        this.villagerAccessible  = villagerAccessible;
    }

    public int getInitialSlots()          { return initialSlots; }
    public int getMaxUpgradeableSlots()   { return maxUpgradeableSlots; }
    public Access getAccess()             { return access; }
    public boolean isVillagerAccessible() { return villagerAccessible; }
    public boolean isPublic()             { return access == Access.PUBLIC; }

    public static int slotsToRows(int slots) {
        return Math.max(1, Math.min(6, (int) Math.ceil(slots / 9.0)));
    }

    public static ChestConfig publicChest()  { return new ChestConfig(9, 54, Access.PUBLIC, true); }
    public static ChestConfig privateChest() { return new ChestConfig(27, 54, Access.PRIVATE, false); }
}
