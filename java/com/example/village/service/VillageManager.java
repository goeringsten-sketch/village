package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.data.VillageDatabaseManager;
import com.example.village.model.Village;
import com.example.village.model.VillageBorder;
import com.example.village.model.VillageBuilding;
import com.example.village.model.VillageJoinRequest;
import com.example.village.model.VillageMember;
import com.example.village.model.VillageRelation;
import com.example.village.model.VillageRelationState;
import com.example.village.model.VillageRelationType;
import com.example.village.model.VillageRole;
import com.example.village.hook.WorldGuardHook;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import com.example.village.service.BuildingService;

import java.util.UUID;

public final class VillageManager {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VillageDatabaseManager databaseManager;
    private final java.util.Map<UUID, java.util.Set<UUID>> playerDiscoveries = new java.util.concurrent.ConcurrentHashMap<>();
    private BuildingService buildingService;
    private final WorldGuardHook worldGuardHook;
    private CurrencyService currencyService;

    public VillageManager(VillagePlugin plugin, VillageConfigManager configManager,
                          VillageDatabaseManager databaseManager) {
        this(plugin, configManager, databaseManager, null, null);
    }

    public VillageManager(VillagePlugin plugin, VillageConfigManager configManager,
                          VillageDatabaseManager databaseManager, BuildingService buildingService) {
        this(plugin, configManager, databaseManager, buildingService, null);
    }

    public VillageManager(VillagePlugin plugin, VillageConfigManager configManager,
                          VillageDatabaseManager databaseManager, BuildingService buildingService,
                          WorldGuardHook worldGuardHook) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.buildingService = buildingService;
        this.worldGuardHook = worldGuardHook;
    }

    /** Wires {@link BuildingService} after construction (breaks bootstrap cycle). */
    public void setBuildingService(BuildingService buildingService) {
        this.buildingService = buildingService;
    }

    /**
     * Stellt die playerDiscoveries-Map nach einem Serverneustart aus den gespeicherten
     * knownVillageIds jedes Dorf-Mitglieds wieder her. Muss nach dem Laden aller Villages
     * aufgerufen werden (z.B. in VillagePlugin.onEnable nach databaseManager.initialize()).
     */
    public void rebuildPlayerDiscoveriesFromVillages() {
        playerDiscoveries.clear();
        for (Village village : databaseManager.getVillages().values()) {
            // Alle Mitglieder kennen ihr eigenes Dorf
            for (java.util.UUID memberId : village.getMembers().keySet()) {
                java.util.Set<java.util.UUID> discovered = playerDiscoveries
                        .computeIfAbsent(memberId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
                discovered.add(village.getId());
                // Alle gespeicherten bekannten Dörfer dieses Villages übertragen
                discovered.addAll(village.getKnownVillageIds());
            }
        }
    }

    public void setCurrencyService(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    public Village createVillage(String name, Player founder, Location bellLocation) {
        if (getVillageByName(name).isPresent()) {
            throw new IllegalArgumentException("Village name already exists: " + name);
        }
        UUID villageId = UUID.randomUUID();
        Village village = new Village(villageId, name, founder.getUniqueId(), bellLocation);

        int initialSize = configManager.getInitialSize();
        int heightAbove = configManager.getHeightAbove();
        int heightBelow = configManager.getHeightBelow();
        VillageBorder border = VillageBorder.createSquare(bellLocation, initialSize, heightAbove, heightBelow);
        village.setBorder(border);

        databaseManager.addVillage(village);
        if (currencyService != null) {
            currencyService.ensureVillageCurrency(village);
        }
        if (worldGuardHook != null && worldGuardHook.isAvailable()) {
            worldGuardHook.syncVillageRegions(village);
        }
        bindPlayerDiscoveriesToVillage(founder.getUniqueId(), village);
        databaseManager.saveVillage(village);
        return village;
    }

    public void deleteVillage(UUID villageId) {
        Optional<Village> villageOpt = getVillage(villageId);
        if (villageOpt.isPresent()) {
            Village village = villageOpt.get();
            if (currencyService != null) {
                currencyService.deleteVillageCurrency(village);
            }
            // Remove all buildings
            if (buildingService != null) {
                for (VillageBuilding building : village.getBuildings()) {
                    buildingService.removeBuildingCompletely(building);
                }
                // Remove active building sessions for this village
                buildingService.removeSessionsForVillage(villageId);
            }
            if (worldGuardHook != null && worldGuardHook.isAvailable()) {
                worldGuardHook.removeVillageRegion(village);
            }
        }
        databaseManager.removeVillage(villageId);
    }

    public Optional<Village> getVillage(UUID villageId) {
        return Optional.ofNullable(databaseManager.getVillage(villageId));
    }

    public Optional<Village> getVillageByName(String name) {
        return databaseManager.getVillages().values().stream()
                .filter(v -> v.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public Optional<Village> getVillageAtLocation(Location location) {
        String worldName = location.getWorld() != null ? location.getWorld().getName() : null;
        for (Village village : databaseManager.getVillages().values()) {
            if (worldName != null && !worldName.equals(village.getWorldName())) continue;
            if (village.containsLocation(location)) {
                return Optional.of(village);
            }
        }
        return Optional.empty();
    }

    public Optional<Village> getVillageByBell(Location bellLocation) {
        for (Village village : databaseManager.getVillages().values()) {
            Location bell = village.getBellLocation();
            if (bell.getBlockX() == bellLocation.getBlockX()
                    && bell.getBlockY() == bellLocation.getBlockY()
                    && bell.getBlockZ() == bellLocation.getBlockZ()
                    && bell.getWorld() != null
                    && bell.getWorld().equals(bellLocation.getWorld())) {
                return Optional.of(village);
            }
        }
        return Optional.empty();
    }

    public Optional<Village> getPlayerVillage(UUID playerId) {
        for (Village village : databaseManager.getVillages().values()) {
            if (village.isMember(playerId)) {
                return Optional.of(village);
            }
        }
        return Optional.empty();
    }

    public boolean isInAnyVillage(UUID playerId) {
        return getPlayerVillage(playerId).isPresent();
    }

    public boolean addMember(Village village, UUID playerId) {
        if (village.isMember(playerId)) return false;
        int maxMembers = village.getMaxMembers(10, configManager.getUpgradeMembersPerLevel());
        if (village.getMembers().size() >= maxMembers) return false;
        village.addMember(playerId, VillageRole.MEMBER);
        bindPlayerDiscoveriesToVillage(playerId, village);
        databaseManager.saveVillage(village);
        return true;
    }

    public boolean canManageVillage(Village village, UUID playerId) {
        if (village.isFounder(playerId)) {
            return true;
        }
        VillageMember member = village.getMember(playerId);
        return member != null && member.canManageMembers();
    }

    public boolean canDeleteVillage(Village village, UUID playerId) {
        if (village.isFounder(playerId)) {
            return true;
        }
        VillageMember member = village.getMember(playerId);
        return member != null && member.getRole().canDeleteVillage();
    }

    public boolean canManageMembers(Village village, UUID playerId) {
        if (village.isFounder(playerId)) return true;
        VillageMember member = village.getMember(playerId);
        return member != null && member.canManageMembers();
    }

    public boolean canAssignRoles(Village village, UUID playerId) {
        if (village.isFounder(playerId)) return true;
        VillageMember member = village.getMember(playerId);
        return member != null && member.canAssignRoles();
    }

    public boolean canManageBuildings(Village village, UUID playerId) {
        if (village.isFounder(playerId)) return true;
        VillageMember member = village.getMember(playerId);
        return member != null && member.canManageBuildings();
    }

    public boolean canBuildOnSites(Village village, UUID playerId) {
        if (village.isFounder(playerId)) return true;
        VillageMember member = village.getMember(playerId);
        return member != null && member.canBuildOnSites();
    }

    public boolean canTradeWithVillagers(Village village, UUID playerId) {
        if (village.isFounder(playerId)) return true;
        VillageMember member = village.getMember(playerId);
        return member != null && member.canTradeWithVillagers();
    }

    public boolean canUpgradeVillagers(Village village, UUID playerId) {
        if (village.isFounder(playerId)) return true;
        VillageMember member = village.getMember(playerId);
        return member != null && member.canUpgradeVillagers();
    }

    public boolean addJoinRequest(Village village, UUID playerId) {
        if (village.isMember(playerId) || village.hasJoinRequest(playerId) || isInAnyVillage(playerId)) {
            return false;
        }
        village.addJoinRequest(playerId);
        databaseManager.saveVillage(village);
        return true;
    }

    public boolean declineJoinRequest(Village village, UUID playerId) {
        if (!village.hasJoinRequest(playerId)) {
            return false;
        }
        village.removeJoinRequest(playerId);
        databaseManager.saveVillage(village);
        return true;
    }

    public boolean acceptJoinRequest(Village village, UUID playerId) {
        if (!village.hasJoinRequest(playerId)) {
            return false;
        }
        if (isInAnyVillage(playerId)) {
            village.removeJoinRequest(playerId);
            databaseManager.saveVillage(village);
            return false;
        }
        int maxMembers = village.getMaxMembers(10, configManager.getUpgradeMembersPerLevel());
        if (village.getMembers().size() >= maxMembers) {
            return false;
        }
        village.addMember(playerId, VillageRole.MEMBER);
        village.removeJoinRequest(playerId);
        bindPlayerDiscoveriesToVillage(playerId, village);
        databaseManager.saveVillage(village);
        return true;
    }

    public void ensureBuildingTypeOrdinals(Village village) {
        databaseManager.ensureBuildingTypeOrdinals(village);
    }

    public boolean removeMember(Village village, UUID playerId) {
        if (village.isFounder(playerId)) return false;
        village.removeMember(playerId);
        unbindPlayerDiscoveriesFromVillage(playerId, village);
        databaseManager.saveVillage(village);
        return true;
    }

    public boolean promoteMember(Village village, UUID playerId, VillageRole newRole) {
        VillageMember member = village.getMember(playerId);
        if (member == null) return false;
        if (newRole == VillageRole.FOUNDER) return false;
        String unlockKey = newRole.upgradeKey();
        if (unlockKey != null && village.getUpgradeLevel(unlockKey) <= 0) return false;
        java.util.Set<VillageRole> roles = new java.util.HashSet<>(member.getRoles());
        roles.add(newRole);
        member.setRoles(roles);
        databaseManager.saveVillage(village);
        return true;
    }

    public boolean setMemberRoles(Village village, UUID playerId, java.util.Set<VillageRole> roles) {
        VillageMember member = village.getMember(playerId);
        if (member == null) return false;
        member.setRoles(roles);
        databaseManager.saveVillage(village);
        return true;
    }

    public boolean transferFounder(Village village, UUID currentFounderId, UUID newFounderId) {
        if (!village.isFounder(currentFounderId)) return false;
        VillageMember currentFounder = village.getMember(currentFounderId);
        VillageMember newFounder = village.getMember(newFounderId);
        if (currentFounder == null || newFounder == null) return false;
        currentFounder.setRoles(java.util.Set.of(VillageRole.MEMBER));
        newFounder.setRoles(java.util.Set.of(VillageRole.FOUNDER));
        village.setFounderId(newFounderId);
        databaseManager.saveVillage(village);
        return true;
    }

    public Collection<Village> getAllVillages() {
        return databaseManager.getVillages().values();
    }

    public void saveVillage(Village village) {
        if (worldGuardHook != null && worldGuardHook.isAvailable()) {
            worldGuardHook.syncVillageRegions(village);
        }
        databaseManager.saveVillage(village);
    }

    public void saveAll() {
        databaseManager.saveAllVillages();
    }

    public java.util.Set<Village> getKnownVillagesForPlayer(UUID playerId) {
        java.util.Set<Village> known = new java.util.LinkedHashSet<>();
        java.util.Optional<Village> currentVillage = getPlayerVillage(playerId);
        if (currentVillage.isPresent()) {
            Village ownVillage = currentVillage.get();
            known.add(ownVillage);
            for (UUID knownId : ownVillage.getKnownVillageIds()) {
                getVillage(knownId).ifPresent(known::add);
            }
            return known;
        }
        java.util.Set<UUID> discovered = playerDiscoveries.getOrDefault(playerId, java.util.Collections.emptySet());
        for (UUID knownId : discovered) {
            getVillage(knownId).ifPresent(known::add);
        }
        return known;
    }

    public boolean isVillageKnownToPlayer(UUID playerId, Village village) {
        if (village == null) return false;
        java.util.Optional<Village> currentVillage = getPlayerVillage(playerId);
        if (currentVillage.isPresent()) {
            if (currentVillage.get().getId().equals(village.getId())) return true;
            return currentVillage.get().getKnownVillageIds().contains(village.getId());
        }
        return playerDiscoveries.getOrDefault(playerId, java.util.Collections.emptySet()).contains(village.getId());
    }

    public void registerVillageDiscovery(UUID playerId, Village discoveredVillage) {
        if (discoveredVillage == null) return;
        UUID discoveryId = discoveredVillage.getId();
        if (discoveryId == null) return;
        java.util.Set<UUID> discovered = playerDiscoveries.computeIfAbsent(playerId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
        if (!discovered.add(discoveryId)) return;
        java.util.Optional<Village> currentVillage = getPlayerVillage(playerId);
        if (currentVillage.isPresent() && !currentVillage.get().getId().equals(discoveryId)) {
            bindPlayerDiscoveriesToVillage(playerId, currentVillage.get());
        }
    }

    public void bindPlayerDiscoveriesToVillage(UUID playerId, Village village) {
        if (village == null) return;
        java.util.Set<UUID> discovered = playerDiscoveries.getOrDefault(playerId, java.util.Collections.emptySet());
        boolean changed = false;
        for (UUID knownId : discovered) {
            if (!knownId.equals(village.getId()) && !village.getKnownVillageIds().contains(knownId)) {
                village.addKnownVillageId(knownId);
                changed = true;
            }
        }
        if (changed) {
            databaseManager.saveVillage(village);
        }
    }

    public void unbindPlayerDiscoveriesFromVillage(UUID playerId, Village village) {
        if (village == null) return;
        java.util.Set<UUID> known = new java.util.HashSet<>();
        for (UUID memberId : village.getMembers().keySet()) {
            if (memberId.equals(playerId)) continue;
            known.addAll(playerDiscoveries.getOrDefault(memberId, java.util.Collections.emptySet()));
        }
        village.setKnownVillageIds(known);
    }

    public boolean areVillagesFriendlyForPlayer(UUID playerId, Village target) {
        java.util.Optional<Village> playerVillage = getPlayerVillage(playerId);
        return playerVillage.isPresent() && areVillagesFriendly(playerVillage.get(), target);
    }

    public boolean isPlayerMemberOfFriendlyVillage(UUID playerId, Village target) {
        return target != null && areVillagesFriendlyForPlayer(playerId, target);
    }

    public java.util.Set<UUID> getPlayerDiscoveredVillageIds(UUID playerId) {
        return java.util.Collections.unmodifiableSet(playerDiscoveries.getOrDefault(playerId, java.util.Collections.emptySet()));
    }

    public void clearPlayerDiscoveries(UUID playerId) {
        playerDiscoveries.remove(playerId);
    }

    public VillageRelation getRelationBetween(Village a, Village b) {
        if (a == null || b == null) {
            return null;
        }
        return a.getRelation(b.getId());
    }

    public boolean areVillagesFriendly(Village a, Village b) {
        VillageRelation relation = getRelationBetween(a, b);
        return relation != null
                && relation.getType() == VillageRelationType.FRIENDSHIP
                && relation.getState() == VillageRelationState.ACTIVE;
    }

    public void setRelation(Village a, Village b, VillageRelationType type,
                            VillageRelationState state, UUID initiator) {
        if (a == null || b == null) {
            return;
        }
        VillageRelation relationA = new VillageRelation(b.getId(), type, state, initiator);
        VillageRelation relationB = new VillageRelation(a.getId(), type, state, initiator);
        a.addRelation(relationA);
        b.addRelation(relationB);
        databaseManager.saveVillage(a);
        databaseManager.saveVillage(b);
    }

    public void clearRelation(Village a, Village b) {
        if (a == null || b == null) {
            return;
        }
        a.removeRelation(b.getId());
        b.removeRelation(a.getId());
        databaseManager.saveVillage(a);
        databaseManager.saveVillage(b);
    }

    public boolean proposeRelation(Village from, Village to, VillageRelationType type) {
        if (from == null || to == null || type == null) {
            return false;
        }
        VillageRelation existing = getRelationBetween(from, to);
        if (existing != null && existing.getType() == type) {
            if (existing.getState() == VillageRelationState.ACTIVE) {
                return false;
            }
            if (existing.getState() == VillageRelationState.REQUESTED
                    && existing.getInitiatorVillageId().equals(from.getId())) {
                return false;
            }
        }
        setRelation(from, to, type, VillageRelationState.REQUESTED, from.getId());
        return true;
    }

    public boolean acceptRelation(Village accepter, Village proposer, VillageRelationType type) {
        if (accepter == null || proposer == null || type == null) {
            return false;
        }
        VillageRelation existing = getRelationBetween(accepter, proposer);
        if (existing == null || existing.getType() != type) {
            return false;
        }
        if (existing.getState() != VillageRelationState.REQUESTED) {
            return false;
        }
        if (!proposer.getId().equals(existing.getInitiatorVillageId())) {
            return false;
        }
        setRelation(accepter, proposer, type, VillageRelationState.ACTIVE, null);
        return true;
    }

    public boolean cancelRelationRequest(Village requester, Village other) {
        if (requester == null || other == null) {
            return false;
        }
        VillageRelation existing = getRelationBetween(requester, other);
        if (existing == null || existing.getState() != VillageRelationState.REQUESTED) {
            return false;
        }
        if (!requester.getId().equals(existing.getInitiatorVillageId())) {
            return false;
        }
        clearRelation(requester, other);
        return true;
    }

    public boolean declareWar(Village attacker, Village defender) {
        if (attacker == null || defender == null) {
            return false;
        }
        if (getRelationBetween(attacker, defender) != null) {
            return false;
        }
        setRelation(attacker, defender, VillageRelationType.WAR, VillageRelationState.ACTIVE, attacker.getId());
        return true;
    }

    public boolean requestPeace(Village requester, Village opponent) {
        if (requester == null || opponent == null) {
            return false;
        }
        VillageRelation existing = getRelationBetween(requester, opponent);
        if (existing == null || existing.getType() != VillageRelationType.WAR) {
            return false;
        }
        if (existing.getState() == VillageRelationState.PENDING_PEACE) {
            if (existing.getInitiatorVillageId() != null
                    && !existing.getInitiatorVillageId().equals(requester.getId())) {
                clearRelation(requester, opponent);
                return true;
            }
            return false;
        }
        setRelation(requester, opponent, VillageRelationType.WAR,
                VillageRelationState.PENDING_PEACE, requester.getId());
        return true;
    }

    public boolean cancelPeaceRequest(Village requester, Village opponent) {
        if (requester == null || opponent == null) {
            return false;
        }
        VillageRelation existing = getRelationBetween(requester, opponent);
        if (existing == null || existing.getType() != VillageRelationType.WAR
                || existing.getState() != VillageRelationState.PENDING_PEACE) {
            return false;
        }
        if (!requester.getId().equals(existing.getInitiatorVillageId())) {
            return false;
        }
        setRelation(requester, opponent, VillageRelationType.WAR, VillageRelationState.ACTIVE, existing.getInitiatorVillageId());
        return true;
    }

    public boolean toggleCurfew(Village source, Village target) {
        if (source == null || target == null) {
            return false;
        }
        VillageRelation existing = getRelationBetween(source, target);
        if (existing != null && existing.getType() == VillageRelationType.CURFEW) {
            clearRelation(source, target);
            return true;
        }
        if (existing != null) {
            return false;
        }
        setRelation(source, target, VillageRelationType.CURFEW, VillageRelationState.ACTIVE, source.getId());
        return true;
    }

    public boolean breakRelation(Village a, Village b) {
        if (a == null || b == null) {
            return false;
        }
        if (getRelationBetween(a, b) == null) {
            return false;
        }
        clearRelation(a, b);
        return true;
    }

    public boolean isLocationInAnyVillage(Location location) {
        return getVillageAtLocation(location).isPresent();
    }
}
