package com.example.deepfine.inventory.entity;

import com.example.deepfine.inventory.exception.InventoryException;
import com.example.deepfine.inventory.exception.InventoryErrorCode;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Getter;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "inventory")
public class InventoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @Getter
    private ProductEntity product;

    @Column(name = "warehouse_id", nullable = false)
    private long warehouseId;

    @Column(nullable = false)
    @Getter
    private long quantity;

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

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new InventoryException(InventoryErrorCode.INVALID_REQUEST);
        }
    }
}
