package com.promoquoter.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.hibernate.annotations.Check;

import com.promoquoter.common.money.Money;
import com.promoquoter.common.text.Categories;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product", indexes = @Index(name = "idx_product_category", columnList = "category"))
@Check(name = "chk_product_stock_non_negative", constraints = "stock >= 0")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Product {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    Product(String name, String category, BigDecimal price, int stock) {
        this.id = UUID.randomUUID();
        this.name = name.trim();
        this.category = Categories.normalize(category);
        this.price = Money.round(price);
        this.stock = stock;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
