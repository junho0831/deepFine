package com.example.deepfine.inventory.controller;

import com.example.deepfine.inventory.dto.ProductResponse;
import com.example.deepfine.inventory.dto.StockMovementPage;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import com.example.deepfine.inventory.dto.ReceiveRequest;
import com.example.deepfine.inventory.dto.ShipRequest;
import com.example.deepfine.inventory.service.InventoryService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class InventoryController {
    private final InventoryService inventory;

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable @Positive(message = "상품 ID는 양수여야 합니다.") long id) {
        return inventory.get(id);
    }

    @GetMapping("/{id}/movements")
    public StockMovementPage history(@PathVariable @Positive(message = "상품 ID는 양수여야 합니다.") long id,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "조회 개수는 1 이상이어야 합니다.")
            @Max(value = 100, message = "조회 개수는 100 이하여야 합니다.") int size) {
        return inventory.history(id, page, size);
    }

    @PostMapping("/receipts")
    public ProductResponse receive(@RequestBody @Valid ReceiveRequest request) {
        return inventory.receive(request);
    }

    @PostMapping("/{id}/shipments")
    public ProductResponse ship(@PathVariable @Positive(message = "상품 ID는 양수여야 합니다.") long id, @RequestBody @Valid ShipRequest request) {
        return inventory.ship(id, request);
    }
}
