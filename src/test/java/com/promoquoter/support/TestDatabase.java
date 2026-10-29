package com.promoquoter.support;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.jdbc.JdbcTestUtils;

@Component
public class TestDatabase {

    private static final String[] TABLES_IN_DELETE_ORDER = {"idempotency_record", "order_promotion", "order_line",
            "order_adjustment", "order_gift", "order_note", "orders", "promotion_segment", "promotion", "product"};

    @Autowired
    JdbcTemplate jdbcTemplate;

    public void clean() {
        JdbcTestUtils.deleteFromTables(jdbcTemplate, TABLES_IN_DELETE_ORDER);
    }

    public void setStock(String productId, int stock) {
        jdbcTemplate.update("UPDATE product SET stock = ? WHERE id = ?", stock, UUID.fromString(productId));
    }

    public void changeProduct(String productId, String name, String price) {
        jdbcTemplate.update("UPDATE product SET name = ?, price = ? WHERE id = ?", name, new java.math.BigDecimal(price),
                UUID.fromString(productId));
    }

    public int stockOf(String productId) {
        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class,
                UUID.fromString(productId));
        return stock == null ? -1 : stock;
    }

    public int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
