package com.example.deepfine.inventory.dto;

import com.example.deepfine.inventory.entity.ProductEntity;

public record ProductResponse(long id, String name, long quantity) {
    public static ProductResponse from(ProductEntity product) {
        return new ProductResponse(product.getId(), product.getName(), product.getQuantity());
    }
}
