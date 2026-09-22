package com.example.deepfine.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class InventoryController {
    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable @Positive long id) {
        return inventory.get(id);
    }

    @PostMapping("/receipts")
    public Product receive(@RequestBody @Valid ReceiveRequest request) {
        return inventory.receive(request);
    }
}
