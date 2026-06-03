package com.example.village.model;

import java.util.List;

public record LightPointSource(String key, String worldName,
                               double x, double y, double z,
                               double radius,
                               List<LightDistanceStep> stages) {
}
