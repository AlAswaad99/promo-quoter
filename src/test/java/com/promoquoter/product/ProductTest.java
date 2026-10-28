package com.promoquoter.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    void normalisesNameCategoryAndPrice() {
        Product product = new Product("  Mug ", " kitchen ", new BigDecimal("12.5"), 7);

        assertThat(product.getName()).isEqualTo("Mug");
        assertThat(product.getCategory()).isEqualTo("KITCHEN");
        assertThat(product.getPrice()).isEqualTo(new BigDecimal("12.50"));
        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    void generatesIdAndCreationTime() {
        Product first = new Product("A", "c", BigDecimal.ONE, 1);
        Product second = new Product("A", "c", BigDecimal.ONE, 1);

        assertThat(first.getId()).isNotNull().isNotEqualTo(second.getId());
        assertThat(first.getCreatedAt()).isNotNull();
        assertThat(first.getVersion()).isNull();
    }
}
