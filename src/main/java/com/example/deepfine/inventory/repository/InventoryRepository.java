package com.example.deepfine.inventory.repository;

import com.example.deepfine.inventory.entity.InventoryEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<InventoryEntity, Long> {
    @Query("select i from InventoryEntity i where i.product.id = :productId and i.warehouseId = :warehouseId")
    Optional<InventoryEntity> findStock(@Param("productId") long productId, @Param("warehouseId") long warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryEntity i where i.product.id = :productId and i.warehouseId = :warehouseId")
    Optional<InventoryEntity> findStockForUpdate(@Param("productId") long productId, @Param("warehouseId") long warehouseId);

    @Modifying
    @Query(value = "INSERT INTO inventory (product_id, warehouse_id, quantity) VALUES (:productId, :warehouseId, 0) "
            + "ON CONFLICT (product_id, warehouse_id) DO NOTHING", nativeQuery = true)
    void insertIfAbsent(@Param("productId") long productId, @Param("warehouseId") long warehouseId);
}
