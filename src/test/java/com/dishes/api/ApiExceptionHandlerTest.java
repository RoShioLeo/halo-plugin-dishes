package com.dishes.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleBusinessShouldKeepOriginalMessage() {
        var response = handler.handleBusiness(new BusinessException(BusinessErrorCode.ACCESS_DENIED, "需要密码验证"));

        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().ok());
        assertEquals("需要密码验证", response.getBody().message());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void handleIllegalArgumentShouldKeepOriginalMessage() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("notfound"));

        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().ok());
        assertEquals("notfound", response.getBody().message());
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
