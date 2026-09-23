package com.example.deepfine.inventory.dto;

import com.example.deepfine.inventory.entity.StockMovementEntity.Type;
import java.time.Instant;

public record StockMovementResponse(long id, Type type, long quantityDelta, Instant createdAt) {}
