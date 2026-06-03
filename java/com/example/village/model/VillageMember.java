package com.example.village.model;

import java.util.UUID;
import java.util.EnumSet;
import java.util.Set;

public final class VillageMember {

    private final UUID playerId;
    private final EnumSet<VillageRole> roles;
    private long joinedAt;

    public VillageMember(UUID playerId, VillageRole role) {
        this.playerId = playerId;
        this.roles = EnumSet.noneOf(VillageRole.class);
        setRole(role);
        this.joinedAt = System.currentTimeMillis();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public VillageRole getRole() {
        if (roles.contains(VillageRole.FOUNDER)) {
            return VillageRole.FOUNDER;
        }
        for (VillageRole role : roles) {
            if (role != VillageRole.MEMBER) return role;
        }
        return VillageRole.MEMBER;
    }

    public void setRole(VillageRole role) {
        roles.clear();
        if (role == VillageRole.FOUNDER) {
            roles.add(VillageRole.FOUNDER);
            return;
        }
        roles.add(VillageRole.MEMBER);
        if (role != null && role != VillageRole.MEMBER) {
            roles.add(role);
        }
    }

    public Set<VillageRole> getRoles() {
        return EnumSet.copyOf(roles);
    }

    public void setRoles(Set<VillageRole> newRoles) {
        roles.clear();
        if (newRoles != null) {
            roles.addAll(newRoles);
        }
        if (roles.contains(VillageRole.FOUNDER)) {
            roles.clear();
            roles.add(VillageRole.FOUNDER);
            return;
        }
        roles.remove(VillageRole.FOUNDER);
        roles.add(VillageRole.MEMBER);
    }

    public boolean hasRole(VillageRole role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(VillageRole... checkRoles) {
        for (VillageRole role : checkRoles) {
            if (roles.contains(role)) return true;
        }
        return false;
    }

    public boolean canManageMembers() {
        return hasAnyRole(VillageRole.FOUNDER, VillageRole.HR);
    }

    public boolean canAssignRoles() {
        return hasAnyRole(VillageRole.FOUNDER, VillageRole.HR);
    }

    public boolean canManageBuildings() {
        return hasAnyRole(VillageRole.FOUNDER, VillageRole.BAUMEISTER);
    }

    public boolean canBuildOnSites() {
        return hasAnyRole(VillageRole.FOUNDER, VillageRole.BAUMEISTER, VillageRole.BUILDER);
    }

    public boolean canTradeWithVillagers() {
        return hasAnyRole(VillageRole.FOUNDER, VillageRole.HAENDLER);
    }

    public boolean canUpgradeVillagers() {
        return hasAnyRole(VillageRole.FOUNDER, VillageRole.TRAINER);
    }

    public long getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(long joinedAt) {
        this.joinedAt = joinedAt;
    }
}
