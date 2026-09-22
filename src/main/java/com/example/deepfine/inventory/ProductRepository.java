package com.example.deepfine.inventory;

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

    // A missing row cannot be locked. Resolve concurrent creation using the unique name,
    // then acquire PESSIMISTIC_WRITE before changing stock in the same transaction.
    @Modifying
    @Query(value = "INSERT INTO product (name, quantity) VALUES (:name, 0) ON CONFLICT (name) DO NOTHING",
            nativeQuery = true)
    void insertIfAbsent(@Param("name") String name);
}
