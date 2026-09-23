package com.example.deepfine.inventory.service;

import com.example.deepfine.inventory.dto.ProductResponse;
import com.example.deepfine.inventory.dto.StockMovementPage;
import org.springframework.data.domain.PageRequest;
import com.example.deepfine.inventory.dto.ReceiveRequest;
import com.example.deepfine.inventory.dto.ShipRequest;
import com.example.deepfine.inventory.entity.InventoryEntity;
import com.example.deepfine.inventory.entity.ProductEntity;
import com.example.deepfine.inventory.entity.StockMovementEntity;
import com.example.deepfine.inventory.exception.InventoryException;
import com.example.deepfine.inventory.exception.InventoryErrorCode;
import com.example.deepfine.inventory.repository.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final ProductRepository products;
    private final InventoryRepository inventories;
    private final WarehouseRepository warehouses;
    private final StockMovementRepository movements;
    private final IdempotencyService idempotency;

    @Transactional(readOnly = true)
    public ProductResponse get(long id) {
        return ProductResponse.from(inventories.findStock(id, defaultWarehouseId()).orElseThrow(this::notFound));
    }

    @Transactional(readOnly = true)
    public StockMovementPage history(long id, int page, int size) {
        InventoryEntity stock = inventories.findStock(id, defaultWarehouseId()).orElseThrow(this::notFound);
        return StockMovementPage.from(movements.findHistory(stock.getId(), PageRequest.of(page, size)));
    }

    @Transactional
    public ProductResponse receive(ReceiveRequest request, String key) {
        String content = "RECEIPT:DEFAULT:" + request.name().length() + ":" + request.name() + ":" + request.quantity();
        return idempotency.execute(key, content, () -> receive(request));
    }

    @Transactional
    public ProductResponse ship(long id, ShipRequest request, String key) {
        String content = "SHIPMENT:DEFAULT:" + id + ":" + request.quantity();
        return idempotency.execute(key, content, () -> ship(id, request));
    }

    @Transactional
    public ProductResponse receive(ReceiveRequest request) {
        ProductEntity product = products.findByName(request.name()).orElseGet(() -> {
            products.insertIfAbsent(request.name(), "AUTO-" + UUID.randomUUID());
            return products.findByName(request.name()).orElseThrow(this::notFound);
        });
        long warehouseId = defaultWarehouseId();
        InventoryEntity stock = inventories.findStockForUpdate(product.getId(), warehouseId).orElseGet(() -> {
            inventories.insertIfAbsent(product.getId(), warehouseId);
            return inventories.findStockForUpdate(product.getId(), warehouseId).orElseThrow(this::notFound);
        });
        stock.receive(request.quantity());
        movements.save(StockMovementEntity.receipt(stock.getId(), request.quantity()));
        return ProductResponse.from(stock);
    }

    @Transactional
    public ProductResponse ship(long id, ShipRequest request) {
        InventoryEntity stock = inventories.findStockForUpdate(id, defaultWarehouseId()).orElseThrow(this::notFound);
        stock.ship(request.quantity());
        movements.save(StockMovementEntity.shipment(stock.getId(), request.quantity()));
        return ProductResponse.from(stock);
    }

    private long defaultWarehouseId() {
        return warehouses.findByCode("DEFAULT")
                .orElseThrow(() -> new IllegalStateException("기본 창고가 없습니다."))
                .getId();
    }

    private InventoryException notFound() {
        return new InventoryException(InventoryErrorCode.PRODUCT_NOT_FOUND);
    }
}
