package com.dishes.api;

import com.dishes.service.kitchen.KitchenIdentityService;
import com.dishes.service.kitchen.KitchenModels.CartInput;
import com.dishes.service.kitchen.KitchenModels.SubmitInput;
import com.dishes.service.kitchen.KitchenService;
import com.dishes.service.publics.PublicAccessService;
import com.dishes.service.publics.PublicDomainWhitelistService;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping(path = "/plugins/dishes/public", produces = MediaType.APPLICATION_JSON_VALUE)
public class KitchenPublicApiController extends ApiExceptionHandler {
    private final KitchenService kitchen;
    private final KitchenIdentityService identities;
    private final PublicAccessService access;
    private final PublicDomainWhitelistService domains;
    public KitchenPublicApiController(KitchenService kitchen, KitchenIdentityService identities,
                                      PublicAccessService access, PublicDomainWhitelistService domains) {
        this.kitchen = kitchen; this.identities = identities; this.access = access; this.domains = domains;
    }
    private void guard(String token, ServerWebExchange exchange) {
        domains.ensureAllowed(exchange.getRequest()); access.ensureAccess(token);
    }
    @GetMapping("/viewer")
    public Envelope<Map<String, Object>> viewer(Principal principal, ServerWebExchange exchange,
            @RequestHeader(name = "X-Dishes-Access-Token", required = false) String token) {
        guard(token, exchange);
        return Envelope.ok(kitchen.viewer(identities.resolve(principal, exchange)));
    }
    @GetMapping("/order")
    public Envelope<Map<String, Object>> order(@RequestParam(name = "date") String date, @RequestParam(name = "period") String period,
            Principal principal, ServerWebExchange exchange,
            @RequestHeader(name = "X-Dishes-Access-Token", required = false) String token) {
        guard(token, exchange);
        return Envelope.ok(kitchen.order(date, period, identities.resolve(principal, exchange), false));
    }
    @PostMapping("/cart")
    public Envelope<Map<String, Object>> cart(@RequestBody CartInput input, Principal principal, ServerWebExchange exchange,
            @RequestHeader(name = "X-Dishes-Access-Token", required = false) String token) {
        guard(token, exchange);
        return Envelope.ok(kitchen.saveCart(input, identities.resolve(principal, exchange)));
    }
    @PostMapping("/submit")
    public Envelope<Map<String, Object>> submit(@RequestBody SubmitInput input, Principal principal, ServerWebExchange exchange,
            @RequestHeader(name = "X-Dishes-Access-Token", required = false) String token) {
        guard(token, exchange);
        return Envelope.ok(kitchen.submit(input, identities.resolve(principal, exchange)));
    }
    @GetMapping("/daily-menu")
    public Envelope<Map<String, Object>> dailyMenu(@RequestParam(name = "date") String date, Principal principal, ServerWebExchange exchange,
            @RequestHeader(name = "X-Dishes-Access-Token", required = false) String token) {
        guard(token, exchange);
        return Envelope.ok(kitchen.dailyMenu(date, identities.resolve(principal, exchange)));
    }
}
