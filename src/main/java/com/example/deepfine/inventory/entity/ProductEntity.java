package com.example.deepfine.inventory.entity;

import com.example.deepfine.inventory.exception.InventoryException;
import com.example.deepfine.inventory.exception.InventoryErrorCode;

import jakarta.persistence.*;

@Entity
@Table(name = "product")
public class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false)
    private long quantity;

    protected ProductEntity() {}

    public void receive(long amount) {
        validateAmount(amount);
        if (quantity > Long.MAX_VALUE - amount) {
            throw new InventoryException(InventoryErrorCode.STOCK_LIMIT_EXCEEDED);
        }
        quantity += amount;
    }

    public void ship(long amount) {
        validateAmount(amount);
        if (quantity < amount) {
            throw new InventoryException(InventoryErrorCode.INSUFFICIENT_STOCK);
        }
        quantity -= amount;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getQuantity() {
        return quantity;
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new InventoryException(InventoryErrorCode.INVALID_REQUEST);
        }
    }
}
