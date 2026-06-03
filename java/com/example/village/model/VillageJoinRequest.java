package com.example.village.model;

import java.util.UUID;

public final class VillageJoinRequest {

    private final UUID playerId;
    private long requestedAt;

    public VillageJoinRequest(UUID playerId) {
        this.playerId = playerId;
        this.requestedAt = System.currentTimeMillis();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public long getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(long requestedAt) {
        this.requestedAt = requestedAt;
    }
}
