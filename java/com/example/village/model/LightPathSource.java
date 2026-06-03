package com.example.village.model;

public record LightPathSource(String key, String worldName,
                              double fromX, double fromZ,
                              double toX, double toZ,
                              double radius) {
}
