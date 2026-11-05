package com.dishes.api;

import com.dishes.service.kitchen.KitchenIdentityService;
import com.dishes.service.kitchen.KitchenModels.CommitInput;
import com.dishes.service.kitchen.KitchenService;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping(path = "/apis/plugins/dishes/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class KitchenAdminApiController extends ApiExceptionHandler {
    private final KitchenService kitchen;
    private final KitchenIdentityService identities;
    public KitchenAdminApiController(KitchenService kitchen, KitchenIdentityService identities) {
        this.kitchen = kitchen; this.identities = identities;
    }
    @GetMapping("/kitchen")
    public Envelope<Map<String, Object>> kitchen(@RequestParam(name = "date") String date, Principal principal, ServerWebExchange exchange) {
        return Envelope.ok(kitchen.kitchen(date, identities.resolve(principal, exchange)));
    }
    @PostMapping("/kitchen/commit")
    public Envelope<Map<String, Object>> commit(@RequestBody CommitInput input, Principal principal, ServerWebExchange exchange) {
        return Envelope.ok(kitchen.commit(input, identities.resolve(principal, exchange)));
    }
}
