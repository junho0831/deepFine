package com.example.deepfine.inventory.repository;

import com.example.deepfine.inventory.entity.IdempotencyRequestEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequestEntity, String> {
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_request(request_key, request_hash) VALUES (:key, :hash)
            ON CONFLICT (request_key) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("key") String key, @Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from IdempotencyRequestEntity r where r.requestKey = :key")
    Optional<IdempotencyRequestEntity> findForUpdate(@Param("key") String key);
}
