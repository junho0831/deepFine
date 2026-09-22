package com.example.deepfine.inventory;

import jakarta.persistence.*;
import org.springframework.http.HttpStatus;

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
            throw new InventoryException(HttpStatus.CONFLICT, "STOCK_LIMIT_EXCEEDED",
                    "입고 후 재고가 허용 가능한 최대 수량을 초과합니다.");
        }
        quantity += amount;
    }

    public Product toProduct() {
        return new Product(id, name, quantity);
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new InventoryException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "수량은 양수여야 합니다.");
        }
    }
}
