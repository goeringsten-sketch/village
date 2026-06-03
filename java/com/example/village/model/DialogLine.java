package com.example.village.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialogzeile mit Kontextvariablen.
 */
public class DialogLine {
    private String messageKey;
    private List<String> messageVariants;
    private String context;  // GREETING, GOODBYE, HUNGRY, WORKING, etc.
    private int minReputation;
    private VillagerJob requiredJob;
    private VillagerState requiredState;

    public DialogLine(String messageKey, String context) {
        this.messageKey = messageKey;
        this.context = context;
        this.messageVariants = new ArrayList<>();
        this.minReputation = -100;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public List<String> getMessageVariants() {
        return messageVariants;
    }

    public void addVariant(String message) {
        messageVariants.add(message);
    }

    public String getRandomVariant() {
        if (messageVariants.isEmpty()) return "...";
        return messageVariants.get((int) (Math.random() * messageVariants.size()));
    }

    public String getContext() {
        return context;
    }

    public int getMinReputation() {
        return minReputation;
    }

    public void setMinReputation(int minReputation) {
        this.minReputation = minReputation;
    }

    public VillagerJob getRequiredJob() {
        return requiredJob;
    }

    public void setRequiredJob(VillagerJob job) {
        this.requiredJob = job;
    }

    public VillagerState getRequiredState() {
        return requiredState;
    }

    public void setRequiredState(VillagerState state) {
        this.requiredState = state;
    }
}
