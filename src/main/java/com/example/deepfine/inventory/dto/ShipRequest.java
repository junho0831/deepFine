package com.example.deepfine.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ShipRequest(@NotNull(message = "수량은 필수입니다.") @Positive(message = "수량은 양수여야 합니다.") Long quantity) {}
