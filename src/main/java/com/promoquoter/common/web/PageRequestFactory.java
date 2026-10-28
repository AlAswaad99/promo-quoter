package com.promoquoter.common.web;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.common.error.FieldViolation;

public final class PageRequestFactory {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequestFactory() {
    }

    public static Pageable create(int page, int size, String sort, SortPolicy policy) {
        if (page < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    List.of(new FieldViolation("page", "must be greater than or equal to 0")));
        }
        if (size < 1) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    List.of(new FieldViolation("size", "must be greater than or equal to 1")));
        }
        return PageRequest.of(page, Math.min(size, MAX_SIZE), resolveSort(sort, policy));
    }

    private static Sort resolveSort(String sort, SortPolicy policy) {
        String field = policy.defaultField();
        Sort.Direction direction = policy.defaultDirection();
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", -1);
            field = parts[0].trim();
            direction = parts.length == 2 ? parseDirection(parts[1], sort) : Sort.Direction.ASC;
            if (parts.length > 2 || !policy.allowedFields().contains(field)) {
                throw invalidSort(sort, policy);
            }
        }
        return Sort.by(new Sort.Order(direction, field), Sort.Order.asc("id"));
    }

    private static Sort.Direction parseDirection(String value, String sort) {
        return Sort.Direction.fromOptionalString(value.trim()).orElseThrow(
                () -> new ApiException(ErrorCode.INVALID_SORT, "Unsupported sort direction in '" + sort + "'"));
    }

    private static ApiException invalidSort(String sort, SortPolicy policy) {
        return new ApiException(ErrorCode.INVALID_SORT,
                "Unsupported sort '" + sort + "'; allowed fields: " + policy.allowedFields().stream().sorted().toList());
    }
}
