package com.example.deepfine.inventory;

import jakarta.persistence.*;

@Entity
@Table(name = "product")
public class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false)
    private long quantity;

    protected ProductEntity() {}

    public Product toProduct() {
        return new Product(id, name, quantity);
    }
}
