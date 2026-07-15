package com.example.village.model;

import org.bukkit.Material;

import java.util.UUID;

/**
 * Ein Produktions- oder Lieferauftrag zwischen zwei Dorfbewohnern.
 */
public final class VillagerContract {

    public enum Type {
        PRODUCTION,
        MATERIAL,
        LOGISTICS,
        RESEARCH,
        TRADE
    }

    public enum Status {
        OPEN,
        ACTIVE,
        COMPLETED,
        CANCELLED
    }

    private final UUID id;
    private final Type type;
    private final UUID requesterVillagerId;
    private final UUID supplierVillagerId;
    private final String materialKey;
    private final int amount;
    private final long createdAt;
    private final long deadlineAt;
    private final String note;
    private Status status;

    public VillagerContract(UUID id,
                            Type type,
                            UUID requesterVillagerId,
                            UUID supplierVillagerId,
                            String materialKey,
                            int amount,
                            long createdAt,
                            long deadlineAt,
                            String note,
                            Status status) {
        this.id = id;
        this.type = type;
        this.requesterVillagerId = requesterVillagerId;
        this.supplierVillagerId = supplierVillagerId;
        this.materialKey = materialKey;
        this.amount = amount;
        this.createdAt = createdAt;
        this.deadlineAt = deadlineAt;
        this.note = note;
        this.status = status;
    }

    public static VillagerContract create(Type type,
                                          UUID requesterVillagerId,
                                          UUID supplierVillagerId,
                                          Material material,
                                          int amount,
                                          long deadlineAt,
                                          String note) {
        return new VillagerContract(UUID.randomUUID(), type, requesterVillagerId, supplierVillagerId,
                material != null ? material.name() : null, amount, System.currentTimeMillis(), deadlineAt, note, Status.OPEN);
    }

    public UUID getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public UUID getRequesterVillagerId() {
        return requesterVillagerId;
    }

    public UUID getSupplierVillagerId() {
        return supplierVillagerId;
    }

    public String getMaterialKey() {
        return materialKey;
    }

    public Material getMaterial() {
        return materialKey == null ? null : Material.matchMaterial(materialKey);
    }

    public int getAmount() {
        return amount;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getDeadlineAt() {
        return deadlineAt;
    }

    public String getNote() {
        return note;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isExpired() {
        return deadlineAt > 0 && System.currentTimeMillis() > deadlineAt;
    }
}
