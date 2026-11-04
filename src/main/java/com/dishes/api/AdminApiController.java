package com.dishes.api;

import com.dishes.service.AdminFacadeService;
import com.dishes.service.admin.AdminBackupService;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping(path = "/apis/plugins/dishes/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminApiController extends ApiExceptionHandler {

    private final AdminFacadeService adminFacadeService;
    private final AdminBackupService adminBackupService;

    public AdminApiController(AdminFacadeService adminFacadeService, AdminBackupService adminBackupService) {
        this.adminFacadeService = adminFacadeService;
        this.adminBackupService = adminBackupService;
    }

    @GetMapping(value = "/backup/export", produces = "application/zip")
    public ResponseEntity<byte[]> exportBackup(
        @RequestParam(name = "includeOrders", defaultValue = "false") boolean includeOrders
    ) {
        var body = adminBackupService.exportZip(includeOrders);
        var day = java.time.LocalDate.now().toString();
        var filename = "dishes-backup-" + day + ".zip";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .contentLength(body.length)
            .body(body);
    }

    @PostMapping(value = "/backup/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Envelope<Map<String, Object>>> importBackup(@RequestPart("file") FilePart part) {
        return readBackupFileBytes(part)
            .flatMap(
                bytes ->
                    Mono.fromCallable(() -> Envelope.ok(adminBackupService.importZip(bytes)))
                        .subscribeOn(Schedulers.boundedElastic())
            )
            .onErrorResume(
                BusinessException.class,
                ex ->
                    Mono.just(
                        Envelope.error(
                            ex.getMessage() == null || ex.getMessage().isBlank() ? "request failed" : ex.getMessage(),
                            ex.code().value()
                        )
                    )
            )
            .onErrorResume(Throwable.class, ex -> Mono.just(Envelope.error("导入失败，请稍后重试")));
    }

    private static Mono<byte[]> readBackupFileBytes(FilePart part) {
        return DataBufferUtils.join(part.content())
            .switchIfEmpty(Mono.error(new BusinessException(BusinessErrorCode.BAD_REQUEST, "请选择 ZIP 文件")))
            .map(AdminApiController::copyDataBufferToByteArray);
    }

    private static byte[] copyDataBufferToByteArray(DataBuffer dataBuffer) {
        try {
            int n = dataBuffer.readableByteCount();
            byte[] bytes = new byte[n];
            if (n > 0) {
                dataBuffer.read(bytes, 0, n);
            }
            return bytes;
        } finally {
            DataBufferUtils.release(dataBuffer);
        }
    }

    @GetMapping("/categories")
    public Envelope<Map<String, Object>> listCategories() {
        return Envelope.ok(adminFacadeService.listCategories());
    }

    @PostMapping("/categories")
    public Envelope<Map<String, Object>> createCategory(@RequestBody CategoryCreateReq req) {
        return Envelope.ok(adminFacadeService.createCategory(req.name(), req.slug(), req.sortOrder()));
    }

    @PutMapping("/categories/{id}")
    public Envelope<Map<String, Object>> updateCategory(@PathVariable("id") long id, @RequestBody CategoryCreateReq req) {
        return Envelope.ok(adminFacadeService.updateCategory(id, req.name(), req.slug(), req.sortOrder()));
    }

    @DeleteMapping("/categories/{id}")
    public Envelope<Map<String, Object>> deleteCategory(@PathVariable("id") long id) {
        return Envelope.ok(adminFacadeService.deleteCategory(id));
    }

    @GetMapping("/dishes")
    public Envelope<Map<String, Object>> listDishes() {
        return Envelope.ok(adminFacadeService.listDishes());
    }

    @PostMapping("/dishes")
    public Envelope<Map<String, Object>> createDish(@RequestBody DishCreateReq req) {
        return Envelope.ok(adminFacadeService.createDish(
            req.categoryId(),
            req.name(),
            req.imageUrl(),
            req.recommendationLevel(),
            req.description(),
            req.isAvailable(),
            req.sortOrder(),
            req.mealPeriodIds() == null ? List.of() : req.mealPeriodIds()
        ));
    }

    @PutMapping("/dishes/{id}")
    public Envelope<Map<String, Object>> updateDish(@PathVariable("id") long id, @RequestBody DishCreateReq req) {
        return Envelope.ok(adminFacadeService.updateDish(
            id,
            req.categoryId(),
            req.name(),
            req.imageUrl(),
            req.recommendationLevel(),
            req.description(),
            req.isAvailable(),
            req.sortOrder(),
            req.mealPeriodIds() == null ? List.of() : req.mealPeriodIds()
        ));
    }

    @DeleteMapping("/dishes/{id}")
    public Envelope<Map<String, Object>> deleteDish(@PathVariable("id") long id) {
        return Envelope.ok(adminFacadeService.deleteDish(id));
    }

    @GetMapping("/orders")
    public Envelope<Map<String, Object>> listOrders(
        @RequestParam(name = "from", required = false) String from,
        @RequestParam(name = "to", required = false) String to,
        @RequestParam(name = "period", required = false) String period,
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestParam(name = "limit", defaultValue = "30") int limit
    ) {
        return Envelope.ok(adminFacadeService.listOrders(from, to, period, page, limit));
    }

    @GetMapping("/orders/{id}")
    public Envelope<Map<String, Object>> getOrder(@PathVariable("id") long id) {
        return Envelope.ok(adminFacadeService.getOrder(id));
    }

    @GetMapping("/settings")
    public Envelope<Map<String, Object>> getSettings() {
        return Envelope.ok(adminFacadeService.getSettings());
    }

    @PutMapping("/settings")
    public Envelope<Map<String, Object>> updateSettings(@RequestBody SettingsUpdateReq req) {
        var basic = req.basic();
        var notify = req.notifyConfig();
        return Envelope.ok(adminFacadeService.updateSettings(
            basic == null ? null : basic.accessMode(),
            basic == null ? null : basic.accessPassword(),
            basic == null ? null : basic.publicAccessUrl(),
            basic == null ? null : basic.publicLogoUrl(),
            basic == null ? null : basic.publicSiteTitle(),
            basic == null ? null : basic.publicBrandTitle(),
            basic == null ? null : basic.publicBrandSubtitle(),
            basic == null ? null : basic.publicDomainWhitelist(),
            notify == null ? null : notify.enabled(),
            notify == null ? null : notify.channel(),
            notify == null ? null : notify.webhookUrl(),
            notify == null ? null : notify.orderNowEnabled(),
            notify == null ? null : notify.orderReservationEnabled()
        ));
    }

    public record CategoryCreateReq(String name, String slug, int sortOrder) {
        public CategoryCreateReq {
            if (name == null) name = "";
            if (slug == null) slug = "";
        }
    }

    public record DishCreateReq(
        long categoryId,
        String name,
        String imageUrl,
        int recommendationLevel,
        String description,
        boolean isAvailable,
        int sortOrder,
        List<Long> mealPeriodIds
    ) {
        public DishCreateReq {
            if (name == null) name = "";
        }
    }

    public record SettingsUpdateReq(BasicSettingsReq basic, NotifySettingsReq notifyConfig) {}

    public record BasicSettingsReq(
        String accessMode,
        String accessPassword,
        String publicAccessUrl,
        String publicLogoUrl,
        String publicSiteTitle,
        String publicBrandTitle,
        String publicBrandSubtitle,
        String publicDomainWhitelist
    ) {}

    public record NotifySettingsReq(
        Boolean enabled,
        String channel,
        String webhookUrl,
        Boolean orderNowEnabled,
        Boolean orderReservationEnabled
    ) {}
}

