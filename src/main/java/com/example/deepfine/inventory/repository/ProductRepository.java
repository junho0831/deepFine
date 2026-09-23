package com.example.deepfine.inventory.repository;

import com.example.deepfine.inventory.entity.ProductEntity;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductEntity p where p.id = :id")
    Optional<ProductEntity> findByIdForUpdate(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductEntity p where p.name = :name")
    Optional<ProductEntity> findByNameForUpdate(@Param("name") String name);

    // 아직 없는 상품 행은 잠글 수 없으므로 상품명의 유일성 제약으로 동시 등록 충돌을 처리한다.
    // 이후 같은 트랜잭션에서 비관적 쓰기 잠금을 획득한 뒤 재고를 변경한다.
    @Modifying
    @Query(value = "INSERT INTO product (name, quantity) VALUES (:name, 0) ON CONFLICT (name) DO NOTHING",
            nativeQuery = true)
    void insertIfAbsent(@Param("name") String name);
}
