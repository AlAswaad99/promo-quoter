package com.promoquoter.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.promoquoter.support.IntegrationTest;

@IntegrationTest
class ProductStockConstraintIT {

    @Autowired
    ProductRepository productRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
    }

    @Test
    void databaseRejectsAProductWithNegativeStock() {
        Product negative = new Product("Mug", "kitchen", new BigDecimal("12.00"), -1);

        assertThatThrownBy(() -> productRepository.saveAndFlush(negative))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDrivingStockBelowZero() {
        Product mug = productRepository.saveAndFlush(new Product("Mug", "kitchen", new BigDecimal("12.00"), 1));

        assertThatThrownBy(() -> jdbcTemplate.update("update product set stock = stock - 2 where id = ?", mug.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(productRepository.findById(mug.getId()).orElseThrow().getStock()).isEqualTo(1);
    }

    @Test
    void zeroStockIsAllowed() {
        Product soldOut = productRepository.saveAndFlush(new Product("Mug", "kitchen", new BigDecimal("12.00"), 0));

        assertThat(productRepository.findById(soldOut.getId())).isPresent();
    }
}
