package com.dishes.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ServerWebInputException;

/**
 * Controller-local error handlers inherited by this plugin's controllers.
 * Halo's host handler adapter does not discover ControllerAdvice in a plugin child context.
 * LemonSlice API error handling, 2026-11-05; GPL-3.0.
 */
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String DEFAULT_ERROR_MESSAGE = "request failed";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Envelope<Object>> handleBusiness(BusinessException ex) {
        var message = (ex.getMessage() == null || ex.getMessage().isBlank()) ? DEFAULT_ERROR_MESSAGE : ex.getMessage();
        log.warn("Business exception, code={}, message={}", ex.code().value(), message);
        var status = switch (ex.code()) {
            case ACCESS_DENIED, PASSWORD_INVALID, CHEF_REQUIRED -> HttpStatus.FORBIDDEN;
            case BAD_REQUEST, INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case CATEGORY_DELETE_CONFLICT, CONFLICT -> HttpStatus.CONFLICT;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        return ResponseEntity.status(status).body(Envelope.error(message, ex.code().value()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Envelope<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        var message = (ex.getMessage() == null || ex.getMessage().isBlank()) ? DEFAULT_ERROR_MESSAGE : ex.getMessage();
        return ResponseEntity.badRequest().body(Envelope.error(message));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<Envelope<Object>> handleInvalidBody(ServerWebInputException ex) {
        return ResponseEntity.badRequest().body(Envelope.error("请求内容不正确", BusinessErrorCode.INVALID_REQUEST.value()));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Envelope<Object>> handleUnhandled(Throwable ex) {
        log.error("Unhandled API exception", ex);
        return ResponseEntity.internalServerError().body(Envelope.error(DEFAULT_ERROR_MESSAGE));
    }
}
