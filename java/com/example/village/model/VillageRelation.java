package com.example.village.model;

import java.util.UUID;

public final class VillageRelation {

    private final UUID otherVillageId;
    private VillageRelationType type;
    private VillageRelationState state;
    private UUID initiatorVillageId;

    public VillageRelation(UUID otherVillageId,
                           VillageRelationType type,
                           VillageRelationState state,
                           UUID initiatorVillageId) {
        this.otherVillageId = otherVillageId;
        this.type = type;
        this.state = state;
        this.initiatorVillageId = initiatorVillageId;
    }

    public UUID getOtherVillageId() {
        return otherVillageId;
    }

    public VillageRelationType getType() {
        return type;
    }

    public void setType(VillageRelationType type) {
        this.type = type;
    }

    public VillageRelationState getState() {
        return state;
    }

    public void setState(VillageRelationState state) {
        this.state = state;
    }

    public UUID getInitiatorVillageId() {
        return initiatorVillageId;
    }

    public void setInitiatorVillageId(UUID initiatorVillageId) {
        this.initiatorVillageId = initiatorVillageId;
    }
}
