package com.example.deepfine.inventory.repository;

import com.example.deepfine.inventory.entity.StockMovementEntity;
import com.example.deepfine.inventory.dto.StockMovementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovementEntity, Long> {
    @Query("""
            select new com.example.deepfine.inventory.dto.StockMovementResponse(
                m.id, m.type, m.quantityDelta, m.createdAt)
            from StockMovementEntity m where m.inventoryId = :inventoryId
            order by m.createdAt desc, m.id desc
            """)
    Page<StockMovementResponse> findHistory(@Param("inventoryId") long inventoryId, Pageable pageable);
}
