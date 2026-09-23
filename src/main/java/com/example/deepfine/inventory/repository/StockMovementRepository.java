package com.example.deepfine.inventory.repository;

import com.example.deepfine.inventory.entity.StockMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovementEntity, Long> {}
