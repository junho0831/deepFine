package com.example.deepfine.inventory.repository;

import com.example.deepfine.inventory.entity.ProductEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    Optional<ProductEntity> findByName(String name);

    @Modifying
    @Query(value = "INSERT INTO product (name, sku) VALUES (:name, :sku) ON CONFLICT (name) DO NOTHING",
            nativeQuery = true)
    void insertIfAbsent(@Param("name") String name, @Param("sku") String sku);
}
