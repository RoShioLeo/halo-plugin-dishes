package com.dishes.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dishes.service.PublicFacadeService;
import com.dishes.service.publics.PublicDomainWhitelistService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

class ControllerLocalExceptionHandlingTest {

    @Test
    void bothControllersExposeLocalBusinessExceptionHandler() {
        var error = new BusinessException(BusinessErrorCode.ACCESS_DENIED, "需要密码验证");
        assertNotNull(new ExceptionHandlerMethodResolver(PublicApiController.class).resolveMethod(error));
        assertNotNull(new ExceptionHandlerMethodResolver(AdminApiController.class).resolveMethod(error));
    }

    @Test
    void wrongPasswordReturnsStructuredForbiddenWithoutGlobalAdvice() {
        var facade = mock(PublicFacadeService.class);
        when(facade.verifyPassword("wrong-password"))
            .thenThrow(new BusinessException(BusinessErrorCode.PASSWORD_INVALID, "密码错误"));

        clientFor(facade).post()
            .uri("/plugins/dishes/public/access/password-verify")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("password", "wrong-password"))
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.ok").isEqualTo(false)
            .jsonPath("$.code").isEqualTo("DISHES_PASSWORD_INVALID")
            .jsonPath("$.message").isEqualTo("密码错误");
    }

    @Test
    void missingAccessTokenReturnsStructuredForbiddenWithoutGlobalAdvice() {
        var facade = mock(PublicFacadeService.class);
        when(facade.dishes(isNull(), isNull()))
            .thenThrow(new BusinessException(BusinessErrorCode.ACCESS_DENIED, "需要密码验证"));

        clientFor(facade).get()
            .uri("/plugins/dishes/public/dishes")
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.ok").isEqualTo(false)
            .jsonPath("$.code").isEqualTo("DISHES_ACCESS_DENIED")
            .jsonPath("$.message").isEqualTo("需要密码验证");
    }

    private WebTestClient clientFor(PublicFacadeService facade) {
        // No ControllerAdvice is supplied: mirror a plugin controller registered in Halo's host.
        return WebTestClient.bindToController(
            new PublicApiController(facade, mock(PublicDomainWhitelistService.class))
        ).build();
    }
}
