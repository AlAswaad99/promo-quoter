package com.promoquoter.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.jdbc.JdbcTestUtils;

@Component
public class TestDatabase {

    private static final String[] TABLES_IN_DELETE_ORDER = {"promotion_segment", "promotion", "product"};

    @Autowired
    JdbcTemplate jdbcTemplate;

    public void clean() {
        JdbcTestUtils.deleteFromTables(jdbcTemplate, TABLES_IN_DELETE_ORDER);
    }
}
