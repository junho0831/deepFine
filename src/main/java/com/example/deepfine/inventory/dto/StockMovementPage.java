package com.example.deepfine.inventory.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record StockMovementPage(List<StockMovementResponse> items, int page, int size,
        long totalElements, int totalPages) {
    public static StockMovementPage from(Page<StockMovementResponse> result) {
        return new StockMovementPage(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
