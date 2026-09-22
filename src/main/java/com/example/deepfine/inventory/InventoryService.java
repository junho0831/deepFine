package com.example.deepfine.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final ProductRepository products;

    public InventoryService(ProductRepository products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    public Product get(long id) {
        return products.findById(id).orElseThrow(this::notFound).toProduct();
    }

    @Transactional
    public Product receive(ReceiveRequest request) {
        ProductEntity product = products.findByNameForUpdate(request.name()).orElseGet(() -> {
            products.insertIfAbsent(request.name());
            return products.findByNameForUpdate(request.name()).orElseThrow(this::notFound);
        });
        product.receive(request.quantity());
        return product.toProduct();
    }

    @Transactional
    public Product ship(long id, ShipRequest request) {
        ProductEntity product = products.findByIdForUpdate(id).orElseThrow(this::notFound);
        product.ship(request.quantity());
        return product.toProduct();
    }

    private InventoryException notFound() {
        return new InventoryException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다.");
    }
}
