package com.dishes.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Controller-local error handlers inherited by this plugin's controllers.
 * Halo's host handler adapter does not discover ControllerAdvice in a plugin child context.
 * LemonSlice compatibility modification, 2026-11-05; GPL-3.0.
 */
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String DEFAULT_ERROR_MESSAGE = "request failed";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Envelope<Object>> handleBusiness(BusinessException ex) {
        var message = (ex.getMessage() == null || ex.getMessage().isBlank()) ? DEFAULT_ERROR_MESSAGE : ex.getMessage();
        log.warn("Business exception, code={}, message={}", ex.code().value(), message);
        var status = switch (ex.code()) {
            case ACCESS_DENIED, PASSWORD_INVALID -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(Envelope.error(message, ex.code().value()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Envelope<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        var message = (ex.getMessage() == null || ex.getMessage().isBlank()) ? DEFAULT_ERROR_MESSAGE : ex.getMessage();
        return ResponseEntity.ok(Envelope.error(message));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Envelope<Object>> handleUnhandled(Throwable ex) {
        log.error("Unhandled API exception", ex);
        return ResponseEntity.ok(Envelope.error(DEFAULT_ERROR_MESSAGE));
    }
}
