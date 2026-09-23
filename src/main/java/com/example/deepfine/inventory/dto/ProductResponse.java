package com.example.deepfine.inventory.dto;

import com.example.deepfine.inventory.entity.InventoryEntity;

public record ProductResponse(long id, String name, long quantity) {
    public static ProductResponse from(InventoryEntity inventory) {
        return new ProductResponse(inventory.getProduct().getId(),
                inventory.getProduct().getName(), inventory.getQuantity());
    }
}
