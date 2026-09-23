package com.example.deepfine.inventory.entity;

import com.example.deepfine.inventory.dto.ProductResponse;
import com.example.deepfine.inventory.exception.InventoryErrorCode;
import com.example.deepfine.inventory.exception.InventoryException;
import jakarta.persistence.*;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idempotency_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRequestEntity {
    @Id
    @Column(name = "request_key", length = 128)
    private String requestKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_product_id")
    private Long responseProductId;

    @Column(name = "response_name", length = 100)
    private String responseName;

    @Column(name = "response_quantity")
    private Long responseQuantity;

    public Optional<ProductResponse> replay(String hash) {
        if (!requestHash.equals(hash)) {
            throw new InventoryException(InventoryErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (responseProductId == null) return Optional.empty();
        return Optional.of(new ProductResponse(responseProductId, responseName, responseQuantity));
    }

    public void complete(ProductResponse response) {
        responseProductId = response.id();
        responseName = response.name();
        responseQuantity = response.quantity();
    }
}
