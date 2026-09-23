package com.example.deepfine.inventory.entity;

import com.example.deepfine.inventory.exception.InventoryErrorCode;
import com.example.deepfine.inventory.exception.InventoryException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stock_movement")
public class StockMovementEntity {
    public enum Type { RECEIPT, SHIPMENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_id", nullable = false)
    private long inventoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(name = "quantity_delta", nullable = false)
    private long quantityDelta;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private StockMovementEntity(long inventoryId, Type type, long quantityDelta) {
        this.inventoryId = inventoryId;
        this.type = type;
        this.quantityDelta = quantityDelta;
        this.createdAt = Instant.now();
    }

    public static StockMovementEntity receipt(long inventoryId, long amount) {
        validateAmount(amount);
        return new StockMovementEntity(inventoryId, Type.RECEIPT, amount);
    }

    public static StockMovementEntity shipment(long inventoryId, long amount) {
        validateAmount(amount);
        return new StockMovementEntity(inventoryId, Type.SHIPMENT, -amount);
    }

    private static void validateAmount(long amount) {
        if (amount <= 0) {
            throw new InventoryException(InventoryErrorCode.INVALID_REQUEST);
        }
    }
}
