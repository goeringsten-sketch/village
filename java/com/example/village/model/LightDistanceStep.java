package com.example.village.model;

public record LightDistanceStep(int distance, int maxLightLevel) {

    public LightDistanceStep {
        if (distance < 0) {
            throw new IllegalArgumentException("distance darf nicht negativ sein");
        }
        if (maxLightLevel < 0 || maxLightLevel > 15) {
            throw new IllegalArgumentException("maxLightLevel muss zwischen 0 und 15 liegen");
        }
    }
}
