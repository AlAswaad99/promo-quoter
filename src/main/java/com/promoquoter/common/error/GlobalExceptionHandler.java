package com.promoquoter.common.error;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.promoquoter.common.observability.RequestContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException ex) {
        HttpStatus status = ex.getCode().getStatus();
        if (status == HttpStatus.CONFLICT || status == HttpStatus.UNPROCESSABLE_ENTITY) {
            log.warn("{}: {}", ex.getCode(), ex.getMessage());
        }
        return respond(ex.getCode(), ex.getMessage(), ex.getErrors());
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<Object> handleConcurrencyFailure(ConcurrencyFailureException ex) {
        log.warn("{}: {}", ErrorCode.CONCURRENT_MODIFICATION, ex.getMessage());
        return respond(ErrorCode.CONCURRENT_MODIFICATION, "The resource was modified concurrently, please retry",
                List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> violations.add(new FieldViolation(error.getField(), error.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors().forEach(
                error -> violations.add(new FieldViolation(error.getObjectName(), error.getDefaultMessage())));
        return validationFailed(violations);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String prefix = result.getContainerIndex() == null ? "" : "[" + result.getContainerIndex() + "]";
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors().forEach(error -> violations
                        .add(new FieldViolation(join(prefix, error.getField()), error.getDefaultMessage())));
                parameterErrors.getGlobalErrors().forEach(error -> violations
                        .add(new FieldViolation(join(prefix, error.getObjectName()), error.getDefaultMessage())));
            } else {
                String name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(
                        error -> violations.add(new FieldViolation(join(prefix, name), error.getDefaultMessage())));
            }
        }
        return validationFailed(violations);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        if (ex.getCause() instanceof JsonMappingException mappingException && !mappingException.getPath().isEmpty()) {
            violations.add(new FieldViolation(pathOf(mappingException), "Invalid value"));
        }
        return respond(ErrorCode.MALFORMED_REQUEST, "Request body is missing or malformed", violations);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String name = ex.getPropertyName() == null ? "parameter" : ex.getPropertyName();
        return respond(ErrorCode.MALFORMED_REQUEST, "Invalid value for '" + name + "'",
                List.of(new FieldViolation(name, "Invalid value")));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            HttpStatus resolved = HttpStatus.resolve(statusCode.value());
            problem.setProperty("code", resolved == null ? "ERROR" : resolved.name());
            RequestContext.currentRequestId().ifPresent(requestId -> problem.setProperty("requestId", requestId));
        }
        return response;
    }

    private ResponseEntity<Object> validationFailed(List<FieldViolation> violations) {
        violations.sort(Comparator.comparing(FieldViolation::field, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(FieldViolation::message, Comparator.nullsFirst(Comparator.naturalOrder())));
        return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed", violations);
    }

    private ResponseEntity<Object> respond(ErrorCode code, String detail, List<?> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.getStatus(), detail);
        problem.setType(code.type());
        problem.setTitle(code.getTitle());
        problem.setProperty("code", code.name());
        RequestContext.currentRequestId().ifPresent(requestId -> problem.setProperty("requestId", requestId));
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return ResponseEntity.status(code.getStatus()).body(problem);
    }

    private static String join(String prefix, String field) {
        if (prefix.isEmpty()) {
            return field;
        }
        return field == null ? prefix : prefix + "." + field;
    }

    private static String pathOf(JsonMappingException exception) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : exception.getPath()) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
