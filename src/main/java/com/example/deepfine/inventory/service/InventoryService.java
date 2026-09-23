package com.example.deepfine.inventory.service;

import com.example.deepfine.inventory.dto.ProductResponse;
import com.example.deepfine.inventory.dto.ReceiveRequest;
import com.example.deepfine.inventory.dto.ShipRequest;
import com.example.deepfine.inventory.entity.ProductEntity;
import com.example.deepfine.inventory.exception.InventoryException;
import com.example.deepfine.inventory.exception.InventoryErrorCode;
import com.example.deepfine.inventory.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final ProductRepository products;

    public InventoryService(ProductRepository products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    public ProductResponse get(long id) {
        return ProductResponse.from(products.findById(id).orElseThrow(this::notFound));
    }

    @Transactional
    public ProductResponse receive(ReceiveRequest request) {
        ProductEntity product = findOrCreateForUpdate(request.name());
        product.receive(request.quantity());
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse ship(long id, ShipRequest request) {
        ProductEntity product = products.findByIdForUpdate(id).orElseThrow(this::notFound);
        product.ship(request.quantity());
        return ProductResponse.from(product);
    }

    private ProductEntity findOrCreateForUpdate(String name) {
        return products.findByNameForUpdate(name).orElseGet(() -> {
            products.insertIfAbsent(name);
            return products.findByNameForUpdate(name).orElseThrow(this::notFound);
        });
    }

    private InventoryException notFound() {
        return new InventoryException(InventoryErrorCode.PRODUCT_NOT_FOUND);
    }
}
