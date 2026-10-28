package com.promoquoter.common.error;

import java.util.List;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient List<?> errors;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, List.of());
    }

    public ApiException(ErrorCode code, String detail, List<?> errors) {
        super(detail);
        this.code = code;
        this.errors = errors;
    }
}
