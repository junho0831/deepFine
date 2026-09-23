package com.example.deepfine.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ShipRequest(@NotNull @Positive Long quantity) {}
