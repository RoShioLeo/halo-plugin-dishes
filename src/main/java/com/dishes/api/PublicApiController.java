package com.dishes.api;

import com.dishes.service.PublicFacadeService;
import com.dishes.service.publics.PublicDomainWhitelistService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/plugins/dishes/public", produces = MediaType.APPLICATION_JSON_VALUE)
public class PublicApiController extends ApiExceptionHandler {

    private final PublicFacadeService publicFacadeService;
    private final PublicDomainWhitelistService publicDomainWhitelistService;

    public PublicApiController(
        PublicFacadeService publicFacadeService,
        PublicDomainWhitelistService publicDomainWhitelistService
    ) {
        this.publicFacadeService = publicFacadeService;
        this.publicDomainWhitelistService = publicDomainWhitelistService;
    }

    @GetMapping("/dishes")
    public Envelope<Map<String, Object>> listDishes(
        @RequestParam(name = "categoryId", required = false) Long categoryId,
        @RequestHeader(name = "X-Dishes-Access-Token", required = false) String token,
        ServerHttpRequest request
    ) {
        publicDomainWhitelistService.ensureAllowed(request);
        return Envelope.ok(publicFacadeService.dishes(categoryId, token));
    }

    @GetMapping("/access/status")
    public Envelope<Map<String, Object>> getAccessStatus(
        @RequestHeader(name = "X-Dishes-Access-Token", required = false) String token,
        ServerHttpRequest request
    ) {
        publicDomainWhitelistService.ensureAllowed(request);
        return Envelope.ok(publicFacadeService.accessStatus(token));
    }

    @GetMapping("/csrf-metadata")
    @SuppressWarnings("unchecked")
    public Mono<Envelope<Map<String, String>>> csrfMetadata(ServerWebExchange exchange) {
        publicDomainWhitelistService.ensureAllowed(exchange.getRequest());
        var attr = exchange.getAttribute(CsrfToken.class.getName());
        if (!(attr instanceof Mono<?>)) {
            return Mono.just(Envelope.ok(Map.of()));
        }
        return ((Mono<CsrfToken>) attr)
            .map(token -> Envelope.ok(Map.of(
                "headerName", token.getHeaderName(),
                "token", token.getToken()
            )))
            .defaultIfEmpty(Envelope.ok(Map.of()));
    }

    public record PasswordVerifyReq(String password) {}
 
    @PostMapping("/access/password-verify")
    public Envelope<Map<String, Object>> verifyPassword(@RequestBody PasswordVerifyReq req, ServerHttpRequest request) {
        publicDomainWhitelistService.ensureAllowed(request);
        return Envelope.ok(publicFacadeService.verifyPassword(req.password()));
    }
}

