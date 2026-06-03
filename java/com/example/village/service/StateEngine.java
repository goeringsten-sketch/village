package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.model.CustomVillager;
import com.example.village.model.VillagerNeed;
import com.example.village.model.VillagerState;
import com.example.village.model.Village;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;

import java.util.*;

/**
 * State Machine für Villager-KI.
 * Behebt: Echtes Gefahrensystem (Mob-Erkennung), Inventar-Verbrauch beim Essen.
 */
public class StateEngine {

    private static final double DANGER_RADIUS = 16.0;
    private static final double FLEE_HUNGER_THRESHOLD = 10.0;

    /** Essbare Items (Material → Hunger-Wiederherstellung) */
    private static final Map<Material, Double> FOOD_VALUES = new EnumMap<>(Material.class);
    static {
        FOOD_VALUES.put(Material.BREAD,           8.0);
        FOOD_VALUES.put(Material.COOKED_BEEF,    12.0);
        FOOD_VALUES.put(Material.COOKED_CHICKEN,  6.0);
        FOOD_VALUES.put(Material.COOKED_PORKCHOP,12.0);
        FOOD_VALUES.put(Material.COOKED_MUTTON,   6.0);
        FOOD_VALUES.put(Material.COOKED_RABBIT,   5.0);
        FOOD_VALUES.put(Material.COOKED_SALMON,   6.0);
        FOOD_VALUES.put(Material.COOKED_COD,      5.0);
        FOOD_VALUES.put(Material.APPLE,           4.0);
        FOOD_VALUES.put(Material.CARROT,          3.0);
        FOOD_VALUES.put(Material.POTATO,          1.0);
        FOOD_VALUES.put(Material.BAKED_POTATO,    5.0);
        FOOD_VALUES.put(Material.MELON_SLICE,     2.0);
        FOOD_VALUES.put(Material.PUMPKIN_PIE,     8.0);
        FOOD_VALUES.put(Material.COOKIE,          2.0);
        FOOD_VALUES.put(Material.CAKE,           14.0);
        FOOD_VALUES.put(Material.GOLDEN_APPLE,   10.0);
    }

    private final VillagePlugin plugin;
    private final Map<UUID, Long> lastStateUpdateTime = new HashMap<>();
    /** NPC-Entität → UUID des Villagers (für räumliche Gefahren-Checks) */
    private final Map<UUID, UUID> npcEntityToVillager = new HashMap<>();

    public StateEngine(VillagePlugin plugin) {
        this.plugin = plugin;
    }

    // ===================== HAUPT-UPDATE =====================

    /**
     * Aktualisiert den Zustand eines Villagers.
     * Priorität: Flucht > Hunger > Erschöpfung > Schlafen > Arbeit > Normal
     */
    public void updateVillagerState(CustomVillager villager, Village village) {
        long now = System.currentTimeMillis();
        long lastUpdate = lastStateUpdateTime.getOrDefault(villager.getId(), 0L);
        if (now - lastUpdate < 1000) return;
        lastStateUpdateTime.put(villager.getId(), now);

        // Priorität 1: Gefahr (Mobs in der Nähe)
        if (isInDanger(villager, village)) {
            if (villager.getCurrentState() != VillagerState.FLEEING) {
                villager.setState(VillagerState.FLEEING);
            }
            return;
        }
        // Falls Gefahr vorbei → aus FLEEING heraus
        if (villager.getCurrentState() == VillagerState.FLEEING) {
            villager.setState(VillagerState.IDLE);
        }

        // Priorität 2: Hunger kritisch
        if (villager.isNeedCritical(VillagerNeed.HUNGER)) {
            if (hasFood(villager)) {
                villager.setState(VillagerState.EATING);
            } else {
                // Kein Essen → IDLE und Moral sinkt
                villager.adjustMorale(-2);
            }
            return;
        }

        // Priorität 3: Müdigkeit (Happiness als Proxy für Erschöpfung)
        if (villager.getNeedValue(VillagerNeed.HAPPINESS) < 20) {
            villager.setState(VillagerState.SLEEPING);
            return;
        }

        // Normaler Zyklus
        cycleNormalBehavior(villager, village);
    }

    // ===================== GEFAHRENSYSTEM =====================

    /**
     * Prüft, ob sich feindliche Mobs oder bewaffnete Spieler in der Nähe befinden.
     */
    private boolean isInDanger(CustomVillager villager, Village village) {
        // Hunger unter 10 % → auch als Gefahren-Signal werten (sehr erschöpft)
        if (villager.getNeedValue(VillagerNeed.HUNGER) < FLEE_HUNGER_THRESHOLD) return true;

        // Räumliche Mob-Erkennung via NPC-Position
        Location npcLocation = getNpcLocation(villager, village);
        if (npcLocation == null) return false;

        World world = npcLocation.getWorld();
        if (world == null) return false;

        for (Entity entity : world.getNearbyEntities(npcLocation, DANGER_RADIUS, DANGER_RADIUS, DANGER_RADIUS)) {
            if (isHostileToVillager(entity, villager)) return true;
        }
        return false;
    }

    private boolean isHostileToVillager(Entity entity, CustomVillager villager) {
        if (entity instanceof Monster) {
            // Zombies, Skeletons, Creepers usw. sind immer feindlich
            if (entity instanceof Zombie) return true;
            if (entity instanceof Skeleton) return true;
            if (entity instanceof Creeper) return true;
            if (entity instanceof Spider spider) {
                // Spinnen nur bei Nacht feindlich
                World w = spider.getWorld();
                return w.getTime() >= 13000 || w.getTime() < 1000;
            }
            return true; // alle anderen Monster auch
        }
        // Spieler mit Schwert oder Axt in der Hand (potenzielle Bedrohung)
        if (entity instanceof Player player) {
            if (!player.hasPermission("village.bypass")) {
                var heldItem = player.getInventory().getItemInMainHand();
                Material mat = heldItem.getType();
                return isMeleeWeapon(mat) || isRangedWeapon(mat);
            }
        }
        return false;
    }

    private boolean isMeleeWeapon(Material mat) {
        return mat == Material.WOODEN_SWORD || mat == Material.STONE_SWORD ||
               mat == Material.IRON_SWORD   || mat == Material.GOLDEN_SWORD ||
               mat == Material.DIAMOND_SWORD || mat == Material.NETHERITE_SWORD ||
               mat == Material.WOODEN_AXE   || mat == Material.STONE_AXE ||
               mat == Material.IRON_AXE     || mat == Material.GOLDEN_AXE ||
               mat == Material.DIAMOND_AXE  || mat == Material.NETHERITE_AXE;
    }

    private boolean isRangedWeapon(Material mat) {
        return mat == Material.BOW || mat == Material.CROSSBOW || mat == Material.TRIDENT;
    }

    /**
     * Holt die aktuelle NPC-Position eines Villagers.
     * Nutzt zuerst workLocation, dann homeLocation, dann Bell des Dorfes.
     */
    private Location getNpcLocation(CustomVillager villager, Village village) {
        if (villager.getWorkLocation() != null) return villager.getWorkLocation();
        if (villager.getHomeLocation() != null) return villager.getHomeLocation();
        if (village != null) return village.getBellLocation();
        return null;
    }

    // ===================== ESSEN =====================

    private boolean hasFood(CustomVillager villager) {
        for (Material food : FOOD_VALUES.keySet()) {
            if (villager.getInventory().getOrDefault(food, 0) > 0) return true;
        }
        return false;
    }

    // ===================== AKTIONEN =====================

    /**
     * Führt die aktuelle State-Aktion eines Villagers aus.
     */
    public void executeStateAction(CustomVillager villager, Village village) {
        switch (villager.getCurrentState()) {
            case WORKING    -> handleWorking(villager, village);
            case SLEEPING   -> handleSleeping(villager);
            case EATING     -> handleEating(villager);
            case HEALING    -> handleHealing(villager);
            case FLEEING    -> handleFleeing(villager, village);
            default         -> {}
        }
    }

    private void handleWorking(CustomVillager villager, Village village) {
        long delta = villager.getLastProductionDelta();
        if (delta > 5000) {
            double output = calculateWorkOutput(villager);
            villager.addXp(output / 10);
            villager.setLastProductionTime(System.currentTimeMillis());
            villager.adjustMorale(-1);
            // Hunger durch Arbeit senken
            villager.setNeedValue(VillagerNeed.HUNGER,
                    Math.max(0, villager.getNeedValue(VillagerNeed.HUNGER) - 0.5));
        }
    }

    private void handleSleeping(CustomVillager villager) {
        long delta = villager.getStateChangeDelta();
        if (delta > 10000) {
            villager.setNeedValue(VillagerNeed.HAPPINESS,
                    Math.min(100, villager.getNeedValue(VillagerNeed.HAPPINESS) + 5));
            if (villager.getNeedValue(VillagerNeed.HAPPINESS) >= 90) {
                villager.setState(VillagerState.IDLE);
            }
        }
    }

    /**
     * Essen: verbraucht tatsächlich Items aus dem Inventar des Villagers.
     */
    private void handleEating(CustomVillager villager) {
        Map<Material, Integer> inv = villager.getInventory();

        // Suche nach dem nahrhaftesten verfügbaren Lebensmittel
        Material bestFood = null;
        double bestValue = 0;
        for (Map.Entry<Material, Double> entry : FOOD_VALUES.entrySet()) {
            if (inv.getOrDefault(entry.getKey(), 0) > 0 && entry.getValue() > bestValue) {
                bestFood = entry.getKey();
                bestValue = entry.getValue();
            }
        }

        if (bestFood != null) {
            // Item verbrauchen
            int currentAmount = inv.get(bestFood);
            if (currentAmount <= 1) {
                inv.remove(bestFood);
            } else {
                inv.put(bestFood, currentAmount - 1);
            }
            // Hunger wiederherstellen
            double newHunger = Math.min(100, villager.getNeedValue(VillagerNeed.HUNGER) + bestValue);
            villager.setNeedValue(VillagerNeed.HUNGER, newHunger);
            villager.adjustMorale(2);
        } else {
            // Kein Essen → Nothilfe (Hunger leicht anheben um Deadlock zu verhindern)
            double currentHunger = villager.getNeedValue(VillagerNeed.HUNGER);
            villager.setNeedValue(VillagerNeed.HUNGER, Math.min(100, currentHunger + 2));
            villager.adjustMorale(-3);
        }

        if (villager.getNeedValue(VillagerNeed.HUNGER) >= 70) {
            villager.setState(VillagerState.IDLE);
        }
    }

    private void handleHealing(CustomVillager villager) {
        villager.setNeedValue(VillagerNeed.HAPPINESS,
                Math.min(100, villager.getNeedValue(VillagerNeed.HAPPINESS) + 2));
        if (villager.getNeedValue(VillagerNeed.HAPPINESS) >= 90) {
            villager.setState(VillagerState.IDLE);
        }
    }

    private void handleFleeing(CustomVillager villager, Village village) {
        // Flucht-Logik: Bewege NPC in Richtung Dorfzentrum (Bell)
        if (village == null || village.getBellLocation() == null) return;
        // Citizens-Bewegung wird extern angesteuert; hier nur Morale-Abfall
        long delta = villager.getStateChangeDelta();
        if (delta > 3000) {
            villager.adjustMorale(-5);
        }
        // Automatisch aufhören wenn Gefahr vorbei (wird in updateVillagerState gehandelt)
    }

    // ===================== NORMALES VERHALTEN =====================

    private void cycleNormalBehavior(CustomVillager villager, Village village) {
        long stateTime = villager.getStateChangeDelta();

        switch (villager.getCurrentState()) {
            case IDLE -> {
                if (stateTime > 30000 + (long)(Math.random() * 30000)) {
                    villager.setState(VillagerState.WORKING);
                }
            }
            case WORKING -> {
                if (stateTime > 120000 + (long)(Math.random() * 120000)) {
                    // Nach langer Arbeit: leicht Hunger senken
                    villager.setNeedValue(VillagerNeed.HUNGER,
                            Math.max(0, villager.getNeedValue(VillagerNeed.HUNGER) - 5));
                    villager.setState(VillagerState.IDLE);
                }
            }
            case INTERACTING -> {
                if (stateTime > 10000) villager.setState(VillagerState.IDLE);
            }
            case GOSSIPING -> {
                if (stateTime > 20000) villager.setState(VillagerState.IDLE);
            }
            default -> {
                if (stateTime > 60000) villager.setState(VillagerState.IDLE);
            }
        }
    }

    // ===================== HILFSMETHODEN =====================

    private double calculateWorkOutput(CustomVillager villager) {
        double baseOutput = 10.0;
        double skillBonus = villager.getSkills().values().stream()
                .mapToDouble(s -> s.getLevel() * 0.5)
                .sum();
        double moraleMod = villager.getMorale() / 100.0;
        return baseOutput * (1 + skillBonus / 100.0) * moraleMod;
    }

    public void registerNpcEntity(UUID entityId, UUID villagerId) {
        npcEntityToVillager.put(entityId, villagerId);
    }

    public void unregisterNpcEntity(UUID entityId) {
        npcEntityToVillager.remove(entityId);
    }

    public void startStateUpdater() {
        // Dieser Task wird durch VillagerTickService aufgerufen
    }
}
