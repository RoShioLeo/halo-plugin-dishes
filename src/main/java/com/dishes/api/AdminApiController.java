package com.dishes.api;

import com.dishes.service.AdminFacadeService;
import com.dishes.domain.service.MealSchedule;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/apis/plugins/dishes/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminApiController extends ApiExceptionHandler {

    private final AdminFacadeService adminFacadeService;

    public AdminApiController(AdminFacadeService adminFacadeService) {
        this.adminFacadeService = adminFacadeService;
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
    public Envelope<Map<String, Object>> createDish(@RequestBody DishUpsertReq req) {
        return Envelope.ok(adminFacadeService.createDish(
            req.categoryId(),
            req.name(),
            req.imageUrl(),
            req.recommendationLevel(),
            req.description(),
            req.isAvailable(),
            req.sortOrder(),
            req.tags(),
            req.soldOut()
        ));
    }

    @PutMapping("/dishes/{id}")
    public Envelope<Map<String, Object>> updateDish(@PathVariable("id") long id, @RequestBody DishUpsertReq req) {
        return Envelope.ok(adminFacadeService.updateDish(
            id,
            req.categoryId(),
            req.name(),
            req.imageUrl(),
            req.recommendationLevel(),
            req.description(),
            req.isAvailable(),
            req.sortOrder(),
            req.tags(),
            req.soldOut()
        ));
    }

    @DeleteMapping("/dishes/{id}")
    public Envelope<Map<String, Object>> deleteDish(@PathVariable("id") long id) {
        return Envelope.ok(adminFacadeService.deleteDish(id));
    }

    @GetMapping("/settings")
    public Envelope<Map<String, Object>> getSettings() {
        return Envelope.ok(adminFacadeService.getSettings());
    }

    @PutMapping("/settings")
    public Envelope<Map<String, Object>> updateSettings(@RequestBody SettingsUpdateReq req) {
        var basic = req.basic();
        if (basic == null) throw new BusinessException(BusinessErrorCode.BAD_REQUEST, "请提供基础设置");
        return Envelope.ok(adminFacadeService.updateSettings(
            basic.accessMode(),
            basic.accessPassword(),
            basic.publicAccessUrl(),
            basic.publicLogoUrl(),
            basic.publicSiteTitle(),
            basic.publicBrandTitle(),
            basic.publicBrandSubtitle(),
            basic.publicDomainWhitelist(),
            basic.mealSchedule()
        ));
    }

    public record CategoryCreateReq(String name, String slug, int sortOrder) {
        public CategoryCreateReq {
            if (name == null) name = "";
            if (slug == null) slug = "";
        }
    }

    public record DishUpsertReq(
        long categoryId,
        String name,
        String imageUrl,
        int recommendationLevel,
        String description,
        boolean isAvailable,
        int sortOrder,
        List<String> tags,
        boolean soldOut
    ) {
        public DishUpsertReq {
            if (name == null) name = "";
        }
    }

    public record SettingsUpdateReq(BasicSettingsReq basic) {}

    public record BasicSettingsReq(
        String accessMode,
        String accessPassword,
        String publicAccessUrl,
        String publicLogoUrl,
        String publicSiteTitle,
        String publicBrandTitle,
        String publicBrandSubtitle,
        String publicDomainWhitelist,
        MealSchedule mealSchedule
    ) {}

}

