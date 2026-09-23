package com.example.deepfine.inventory.repository;

import com.example.deepfine.inventory.entity.WarehouseEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<WarehouseEntity, Long> {
    Optional<WarehouseEntity> findByCode(String code);
}
