package com.dishes.service.kitchen;

import static com.dishes.service.kitchen.KitchenModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.dishes.api.BusinessErrorCode;
import com.dishes.api.BusinessException;
import com.dishes.domain.DishesStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KitchenParticipantDeletionTest {
    private final MemoryRepository repository = new MemoryRepository();
    private final DishesStore catalogue = mock(DishesStore.class);
    private final com.dishes.service.DishesSettingsService settings = mock(com.dishes.service.DishesSettingsService.class);
    private final Identity a = new Identity("private-a", null, "", true, false, "");
    private final Identity b = new Identity("private-b", null, "", true, false, "");
    private final Identity c = new Identity("private-c", null, "", true, false, "");
    private final Identity chef = new Identity("private-chef", "private-chef-account", "主厨", false, true, "");
    private final KitchenService service = new KitchenService(repository, catalogue, settings,
        Clock.fixed(Instant.parse("2026-11-05T01:00:00Z"), ZoneOffset.UTC));
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void dishes() {
        when(catalogue.listDishes(any())).thenReturn(List.of(
            new DishesStore.Dish(1, 1, "分类", "番茄汤", "/tomato.png", 4, "", true, 0, List.of(), false)));
    }

    @Test
    void deletingPendingPersonRemovesTheirWholeRecordWithoutReorderingOrEditingAnyoneElse() {
        save(a, "甲", 2); save(b, "乙", 3); save(c, "丙", 1);
        var before = admin(); var target = named(before, "乙");
        var remaining = people(before).stream().filter(person -> !person.get("id").equals(target.get("id"))).toList();
        var result = delete((String) target.get("id"));
        assertEquals(remaining, people(result));
        assertFalse(versions(result).containsKey(target.get("id")));
        assertEquals(before.get("activeBatchId"), result.get("activeBatchId"));
        assertEquals(before.get("submittedBatches"), result.get("submittedBatches"));
        assertEquals(((Number) before.get("stateRevision")).longValue() + 1, result.get("stateRevision"));
        assertEquals(2, stored().contributions().size());
        assertFalse(stored().contributions().stream().anyMatch(source -> "乙".equals(source.getSpec().getDisplayName())));
        assertEquals("draft", ((Menu) result.get("menu")).status());
    }

    @Test
    void approvedSourceDeletionDeductsStableKeysAndKeepsOtherSourcesAndMenuMetadata() {
        save(a, "甲", 2); save(b, "乙", 3);
        var first = confirm(List.of(dish(5), custom("custom-a", 1), custom("custom-b", 2)));
        var firstChefId = (String) people(first).stream().filter(person -> !((List<?>) person.get("customItems")).isEmpty())
            .findFirst().orElseThrow().get("id");
        var before = confirm(List.of(dish(5), custom("custom-a", 3), custom("custom-b", 2)));
        var oldMenu = (Menu) before.get("menu"); var bBefore = named(before, "乙");
        var result = delete(firstChefId); var menu = (Menu) result.get("menu");
        assertEquals(List.of(dish(5), custom("custom-a", 2)), menu.items());
        assertEquals(oldMenu.note(), menu.note()); assertEquals(oldMenu.confirmedAt(), menu.confirmedAt());
        assertEquals(oldMenu.confirmedBy(), menu.confirmedBy()); assertEquals(oldMenu.revision() + 1, menu.revision());
        assertFalse(versions(result).containsKey(firstChefId));
        assertFalse(stored().menu().getSpec().getContributionVersions().containsKey(firstChefId));
        assertFalse((boolean) result.get("needsConfirmation"));
        assertEquals(bBefore, named(result, "乙"));
        var afterA = delete((String) named(result, "甲").get("id"));
        assertEquals(List.of(dish(3), custom("custom-a", 2)), ((Menu) afterA.get("menu")).items());
        assertEquals(bBefore, named(afterA, "乙"));
        var afterB = delete((String) bBefore.get("id"));
        assertEquals(List.of(custom("custom-a", 2)), ((Menu) afterB.get("menu")).items());
        assertFalse((boolean) afterB.get("needsConfirmation"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void removingUnapprovedSourceNeverChangesPublishedMenuOrApprovesSomeoneElse(boolean submitted) throws Exception {
        save(a, "甲", 2); confirm(List.of(dish(2)));
        save(b, "乙", 3); save(c, "丙", 1);
        if (submitted) {
            var order = admin();
            service.submit(new SubmitInput("2026-11-05", "lunch", "乙", (String) order.get("activeBatchId"),
                (Map<String, Long>) order.get("draftContributionVersions")), b);
        }
        var before = admin(); var published = json.writeValueAsString(stored().menu());
        var result = delete((String) named(before, "乙").get("id"));
        assertEquals(published, json.writeValueAsString(stored().menu()));
        assertEquals(submitted, result.get("needsConfirmation"));
        assertEquals(named(before, "丙"), named(result, "丙"));
        assertEquals(before.get("activeBatchId"), result.get("activeBatchId"));
        var finalResult = delete((String) named(result, "丙").get("id"));
        assertEquals(published, json.writeValueAsString(stored().menu()));
        assertFalse((boolean) finalResult.get("needsConfirmation"));
    }

    @Test
    void permissionsStaleRevisionWrongContextAndStorageFailureLeaveTheEntireMealUnchanged() throws Exception {
        save(a, "甲", 2); var before = confirm(List.of(dish(2)));
        var id = (String) named(before, "甲").get("id"); var revision = ((Number) before.get("stateRevision")).longValue();
        var original = json.writeValueAsString(repository.states);
        assertEquals(BusinessErrorCode.CHEF_REQUIRED, assertThrows(BusinessException.class,
            () -> service.deleteParticipant("2026-11-05", "lunch", id, revision, a)).code());
        assertEquals(BusinessErrorCode.CONFLICT, assertThrows(BusinessException.class,
            () -> service.deleteParticipant("2026-11-05", "lunch", id, revision - 1, chef)).code());
        assertEquals(BusinessErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
            () -> service.deleteParticipant("2026-11-05", "dinner", id, 0, chef)).code());
        assertEquals(BusinessErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
            () -> service.deleteParticipant("2026-11-05", "lunch", "unknown-participant", revision, chef)).code());
        assertEquals(original, json.writeValueAsString(repository.states));
        repository.failure = new BusinessException(BusinessErrorCode.CONFLICT, "simulated CAS conflict");
        assertEquals(BusinessErrorCode.CONFLICT, assertThrows(BusinessException.class,
            () -> service.deleteParticipant("2026-11-05", "lunch", id, revision, chef)).code());
        assertEquals(original, json.writeValueAsString(repository.states));
        repository.failure = new IllegalStateException("simulated persistence failure");
        assertThrows(IllegalStateException.class, () -> service.deleteParticipant("2026-11-05", "lunch", id, revision, chef));
        assertEquals(original, json.writeValueAsString(repository.states));
    }

    private void save(Identity actor, String nickname, long quantity) {
        var before = service.order("2026-11-05", "lunch", actor, false); var mine = (Map<String, Object>) before.get("mine");
        service.saveCart(new CartInput("2026-11-05", "lunch", nickname, ((Number) mine.get("revision")).longValue(),
            List.of(new SelectionInput(1, quantity, nickname + "菜品备注")), nickname + "想吃其他菜", nickname + "用餐备注",
            null, (String) before.get("activeBatchId")), actor);
    }
    private Map<String, Object> confirm(List<MenuItem> items) {
        var before = admin();
        return (Map<String, Object>) service.commit(new CommitInput("2026-11-05", "lunch", (String) before.get("activeBatchId"),
            ((Number) before.get("stateRevision")).longValue(), ((Menu) before.get("menu")).revision(), versions(before),
            "menu", null, items, "保留最终菜单备注"), chef).get("order");
    }
    private Map<String, Object> delete(String id) {
        return (Map<String, Object>) service.deleteParticipant("2026-11-05", "lunch", id,
            ((Number) admin().get("stateRevision")).longValue(), chef).get("order");
    }
    private Map<String, Object> admin() { return service.order("2026-11-05", "lunch", chef, true); }
    private List<Map<String, Object>> people(Map<String, Object> view) { return (List<Map<String, Object>>) view.get("participants"); }
    private Map<String, Object> named(Map<String, Object> view, String name) {
        return people(view).stream().filter(person -> name.equals(person.get("displayName"))).findFirst().orElseThrow();
    }
    private Map<String, Long> versions(Map<String, Object> view) { return (Map<String, Long>) view.get("contributionVersions"); }
    private KitchenState stored() { return repository.states.get("2026-11-05-lunch"); }
    private MenuItem dish(long quantity) { return new MenuItem(1L, "番茄汤", quantity, "/tomato.png", "保留原菜单菜品备注"); }
    private MenuItem custom(String id, long quantity) { return new MenuItem(null, "同名手写菜", quantity, "", "保留手写菜备注", id); }

    private static class MemoryRepository implements KitchenRepository {
        final Map<String, KitchenState> states = new LinkedHashMap<>();
        RuntimeException failure;
        public KitchenState load(String date, String period) {
            return states.getOrDefault(date + "-" + period, new KitchenState(null, false, new ArrayList<>(), null));
        }
        public void saveState(KitchenState state) {
            if (failure != null) throw failure;
            states.put(state.ticket().getMetadata().getName(), KitchenSnapshots.copy(new KitchenState(
                state.ticket(), true, state.contributions(), state.menu())));
        }
    }
}
