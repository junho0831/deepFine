package com.example.deepfine.inventory.controller;

import com.example.deepfine.inventory.dto.ProductResponse;
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
    public ProductResponse get(@PathVariable @Positive long id) {
        return inventory.get(id);
    }

    @PostMapping("/receipts")
    public ProductResponse receive(@RequestBody @Valid ReceiveRequest request) {
        return inventory.receive(request);
    }

    @PostMapping("/{id}/shipments")
    public ProductResponse ship(@PathVariable @Positive long id, @RequestBody @Valid ShipRequest request) {
        return inventory.ship(id, request);
    }
}
