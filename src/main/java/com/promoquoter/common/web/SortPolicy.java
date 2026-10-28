package com.promoquoter.common.web;

import java.util.Set;

import org.springframework.data.domain.Sort;

public record SortPolicy(Set<String> allowedFields, String defaultField, Sort.Direction defaultDirection) {

    public static SortPolicy of(String defaultField, Sort.Direction defaultDirection, String... allowedFields) {
        return new SortPolicy(Set.of(allowedFields), defaultField, defaultDirection);
    }
}
