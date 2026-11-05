package com.dishes.service.kitchen;

import static com.dishes.service.kitchen.KitchenModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.dishes.api.BusinessErrorCode;
import com.dishes.api.BusinessException;
import com.dishes.domain.DishesStore;


import com.dishes.extension.MealTicketState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KitchenCommitTest {
    private final MemoryRepository repository = new MemoryRepository();
    private final DishesStore catalogue = mock(DishesStore.class);
    private final Identity a = new Identity("private-a", null, "", true, false, "");
    private final Identity b = new Identity("private-b", null, "", true, false, "");
    private final Identity chef = new Identity("private-chef", "private-chef-account", "主厨", false, true, "/chef.png");
    private final KitchenService service = new KitchenService(repository, catalogue,
        Clock.fixed(Instant.parse("2026-11-05T01:00:00Z"), ZoneOffset.UTC));
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void dishes() {
        when(catalogue.listDishes(any())).thenReturn(List.of(
            new DishesStore.Dish(1, 1, "分类", "番茄汤", "/tomato.png", 4, "", true, 0, List.of(), false),
            new DishesStore.Dish(2, 1, "分类", "米饭", "", 4, "", true, 0, List.of(), false)));
    }

    @Test
    void menuReductionUsesExactReturnedOrderInReverseAndPreservesEverySourceIdentityAndNote() {
        save(a, "甲", 2, "愿望甲", "备注甲"); save(b, "乙", 3, "愿望乙", "备注乙");
        var before = admin(); var ordered = people(before); var firstId = (String) ordered.getFirst().get("id");
        var lastId = (String) ordered.getLast().get("id"); var firstQuantity = quantity(ordered.getFirst());
        var committed = commit(before, "menu", null, List.of(dish(firstQuantity + 1)));
        assertEquals(firstQuantity, quantity(person(committed, firstId)));
        assertEquals(1, quantity(person(committed, lastId)));
        for (var original : ordered) {
            var after = person(committed, (String) original.get("id"));
            assertEquals(original.get("displayName"), after.get("displayName"));
            assertEquals(original.get("offMenu"), after.get("offMenu")); assertEquals(original.get("remark"), after.get("remark"));
            assertEquals("原始菜品备注", ((List<Selection>) after.get("items")).getFirst().note());
        }
        assertTrue(people(committed).stream().allMatch(row -> (boolean) row.get("submitted")));
        assertFalse((boolean) committed.get("needsConfirmation"));
    }

    @Test
    void increaseCreatesAuthenticatedChefCurrentBatchWithoutMergingOlderChefSubmittedRow() {
        save(a, "甲", 2, "", ""); save(chef, "不能伪造", 1, "", "");
        var first = admin();
        service.submit(new SubmitInput("2026-11-05", "lunch", "甲", (String) first.get("activeBatchId"), draftVersions(first)), a);
        var before = admin(); var oldChef = people(before).stream().filter(row -> "主厨".equals(row.get("displayName"))).findFirst().orElseThrow();
        var committed = commit(before, "menu", null, List.of(dish(103)));
        var chefs = people(committed).stream().filter(row -> "主厨".equals(row.get("displayName"))).toList();
        assertEquals(2, chefs.size()); assertEquals(1, quantity(person(committed, (String) oldChef.get("id"))));
        var added = chefs.stream().filter(row -> !row.get("id").equals(oldChef.get("id"))).findFirst().orElseThrow();
        assertEquals(100, quantity(added)); assertEquals("/chef.png", added.get("avatarUrl"));
        assertEquals(2, quantity(people(committed).stream().filter(row -> "甲".equals(row.get("displayName"))).findFirst().orElseThrow()));
    }

    @Test
    void sourceProposalIsNotPersistedUntilCommitAndZeroRemovesOnlyTheDishThenPublishesEmptyMenu() {
        save(a, "甲", 2, "还想喝水", "保留用餐备注");
        var before = admin(); var id = (String) people(before).getFirst().get("id");
        var proposal = List.of(new ParticipantQuantities(id, List.of(new SourceQuantity("dish:1", 0))));
        assertEquals(2, quantity(people(admin()).getFirst()));
        var committed = commit(before, "requests", proposal, List.of(dish(0)));
        var source = person(committed, id);
        assertTrue(((List<?>) source.get("items")).isEmpty()); assertEquals("还想喝水", source.get("offMenu"));
        assertEquals("保留用餐备注", source.get("remark")); assertTrue((boolean) source.get("submitted"));
        assertTrue(((Menu) committed.get("menu")).items().isEmpty());
        assertTrue(((List<?>) service.dailyMenu("2026-11-05", a).get("periods")).isEmpty());
    }

    @Test
    void zeroCommittedSourceWithNoWishOrRemarkDisappearsFromPeopleButRetainsItsConflictVersion() {
        save(a, "甲", 2, "", ""); var before = admin(); var id = (String) people(before).getFirst().get("id");
        var committed = commit(before, "menu", null, List.of(dish(0)));
        assertTrue(people(committed).isEmpty());
        assertEquals(2L, versions(committed).get(id));
        assertEquals(1, repository.current.contributions().size());
        assertTrue(repository.current.contributions().getFirst().getSpec().getItems().isEmpty());
        assertNotEquals(before.get("activeBatchId"), committed.get("activeBatchId"));
        assertEquals(1, ((List<?>) committed.get("submittedBatches")).size());
        assertEquals(BusinessErrorCode.CONFLICT, assertThrows(BusinessException.class, () -> service.saveCart(
            new CartInput("2026-11-05", "lunch", "甲", 0, List.of(new SelectionInput(1, 1, "")), "", "", null,
                (String) before.get("activeBatchId")), a)).code());
    }

    @Test
    void publicChangeMakesFrozenCommitConflictWithoutPartialSourceLockOrMenuChange() throws Exception {
        save(a, "甲", 2, "", ""); var frozen = admin(); save(b, "乙", 3, "", "");
        var actual = snapshot(); var input = input(frozen, "menu", null, List.of(dish(1)));
        assertEquals(BusinessErrorCode.CONFLICT, assertThrows(BusinessException.class, () -> service.commit(input, chef)).code());
        assertEquals(actual, snapshot());
    }

    @Test
    void persistenceFailureAndCasFailureDoNotMutateBorrowedSnapshotsOrPartiallyApplyAnything() throws Exception {
        save(a, "甲", 2, "wish", "remark"); var before = admin(); var actual = snapshot();
        repository.failure = new IllegalStateException("simulated storage outage");
        assertThrows(IllegalStateException.class, () -> service.commit(input(before, "menu", null, List.of(dish(5))), chef));
        assertEquals(actual, snapshot());
        repository.failure = new BusinessException(BusinessErrorCode.CONFLICT, "simulated competing writer");
        assertEquals(BusinessErrorCode.CONFLICT, assertThrows(BusinessException.class,
            () -> service.commit(input(before, "menu", null, List.of(dish(0))), chef)).code());
        assertEquals(actual, snapshot());
    }

    @Test
    void sourceAndMenuMismatchOrSimultaneousModesFailBeforeAnyStorageWrite() throws Exception {
        save(a, "甲", 2, "", ""); var before = admin(); var actual = snapshot();
        var id = (String) people(before).getFirst().get("id");
        var changes = List.of(new ParticipantQuantities(id, List.of(new SourceQuantity("dish:1", 1))));
        assertThrows(BusinessException.class, () -> service.commit(input(before, "requests", changes, List.of(dish(2))), chef));
        assertEquals(actual, snapshot());
        assertThrows(BusinessException.class, () -> service.commit(input(before, "menu", changes, List.of(dish(1))), chef));
        assertEquals(actual, snapshot());
    }

    @Test
    void fractionalMenuOrSourceProposalsAreRejectedWithoutChangingTheMeal() throws Exception {
        save(a, "甲", 2, "", ""); var before = admin(); var actual = snapshot();
        assertEquals(BusinessErrorCode.INVALID_REQUEST, assertThrows(BusinessException.class,
            () -> service.commit(input(before, "menu", null, List.of(dish(2.5))), chef)).code());
        var personId = (String) people(before).getFirst().get("id");
        assertEquals(BusinessErrorCode.INVALID_REQUEST, assertThrows(BusinessException.class,
            () -> service.commit(input(before, "requests", List.of(new ParticipantQuantities(personId,
                List.of(new SourceQuantity("dish:1", 1.5)))), List.of(dish(2))), chef)).code());
        assertEquals(actual, snapshot());
    }

    @Test
    void distinctCustomIdsBecomeChefContributionsAndExposeOnlySafePublicFields() throws Exception {
        save(a, "甲", 2, "不自动变菜单的愿望", ""); var before = admin();
        assertEquals(1, ((List<?>) before.get("workingItems")).size());
        var desired = List.of(dish(2), new MenuItem(null, "加一道汤", 1, "", "", "custom-a"),
            new MenuItem(null, "加一道汤", 2, "", "", "custom-b"));
        var committed = commit(before, "menu", null, desired);
        var chefPerson = people(committed).stream().filter(row -> "主厨".equals(row.get("displayName"))).findFirst().orElseThrow();
        var extras = (List<MenuItem>) chefPerson.get("customItems"); assertEquals(2, extras.size());
        assertEquals(List.of("custom-a", "custom-b"), extras.stream().map(MenuItem::customId).toList());
        var publicView = service.order("2026-11-05", "lunch", a, false);
        var serialized = json.writeValueAsString(publicView);
        assertFalse(serialized.contains("private-chef-account")); assertFalse(serialized.contains("private-chef"));
        assertFalse(serialized.contains("ownerKey")); assertFalse(serialized.contains("confirmedByUsername"));
        assertEquals(2, ((List<?>) publicView.get("customTotals")).size());
        // A later public cart adds a new batch and cannot erase already-published chef extras.
        save(chef, "ignored", 1, "", "");
        assertEquals(2, ((List<?>) person(service.order("2026-11-05", "lunch", a, false), (String) chefPerson.get("id")).get("customItems")).size());
    }

    @Test
    void equalQuantityCustomRenameUpdatesSourceWorkingAndPublishedMetadataWithoutChangingCreator() {
        save(a, "甲", 2, "用户愿望不变", "用户备注不变"); var before = admin();
        var first = commit(before, "menu", null, List.of(dish(2), new MenuItem(null, "旧菜名", 1, "", "旧说明", "stable-custom")));
        var original = people(first).stream().filter(row -> !((List<?>) row.get("customItems")).isEmpty()).findFirst().orElseThrow();
        var renamed = commit(admin(), "menu", null, List.of(dish(2), new MenuItem(null, "新菜名", 1, "/new.png", "新说明", "stable-custom")));
        var source = person(renamed, (String) original.get("id"));
        assertEquals(original.get("displayName"), source.get("displayName")); assertEquals(original.get("username"), source.get("username"));
        assertEquals(original.get("offMenu"), source.get("offMenu")); assertEquals(original.get("remark"), source.get("remark"));
        assertEquals(((Number) original.get("revision")).longValue() + 1, source.get("revision"));
        var item = ((List<MenuItem>) source.get("customItems")).getFirst();
        assertEquals("新菜名", item.name()); assertEquals("新说明", item.note()); assertEquals("/new.png", item.imageUrl());
        assertEquals(item, ((List<MenuItem>) renamed.get("workingItems")).stream().filter(row -> row.dishId() == null).findFirst().orElseThrow());
        assertEquals(item, ((Menu) renamed.get("menu")).items().stream().filter(row -> row.dishId() == null).findFirst().orElseThrow());
    }

    private Map<String, Object> save(Identity identity, String nickname, double quantity, String wish, String remark) {
        var current = service.order("2026-11-05", "lunch", identity, false);
        var mine = (Map<String, Object>) current.get("mine");
        return (Map<String, Object>) service.saveCart(new CartInput("2026-11-05", "lunch", nickname,
            ((Number) mine.get("revision")).longValue(), List.of(new SelectionInput(1, quantity, "原始菜品备注")),
            wish, remark, null, (String) current.get("activeBatchId")), identity).get("order");
    }
    private Map<String, Object> admin() { return service.order("2026-11-05", "lunch", chef, true); }
    private Map<String, Object> commit(Map<String, Object> before, String mode, List<ParticipantQuantities> quantities, List<MenuItem> items) {
        return (Map<String, Object>) service.commit(input(before, mode, quantities, items), chef).get("order");
    }
    private CommitInput input(Map<String, Object> before, String mode, List<ParticipantQuantities> quantities, List<MenuItem> items) {
        return new CommitInput("2026-11-05", "lunch", (String) before.get("activeBatchId"),
            ((Number) before.get("stateRevision")).longValue(), ((Menu) before.get("menu")).revision(),
            versions(before), mode, quantities, items, "最终总备注");
    }
    private List<Map<String, Object>> people(Map<String, Object> view) { return (List<Map<String, Object>>) view.get("participants"); }
    private Map<String, Object> person(Map<String, Object> view, String id) { return people(view).stream().filter(row -> id.equals(row.get("id"))).findFirst().orElseThrow(); }
    private Map<String, Long> versions(Map<String, Object> view) { return (Map<String, Long>) view.get("contributionVersions"); }
    private Map<String, Long> draftVersions(Map<String, Object> view) { return (Map<String, Long>) view.get("draftContributionVersions"); }
    private double quantity(Map<String, Object> person) { return ((List<Selection>) person.get("items")).getFirst().quantity(); }
    private MenuItem dish(double quantity) { return new MenuItem(1L, "番茄汤", quantity, "/tomato.png", "保留菜单菜品备注"); }
    private String snapshot() throws Exception { return json.writeValueAsString(repository.load("2026-11-05", "lunch")); }

    private static class MemoryRepository implements KitchenRepository {
        KitchenState current;
        RuntimeException failure;
        public KitchenState load(String date, String period) {
            return current == null ? new KitchenState(null, false, new ArrayList<>(), null) : current;
        }
        public void saveState(KitchenState value) {
            if (failure != null) throw failure;
            current = KitchenSnapshots.copy(new KitchenState(value.ticket(), true, value.contributions(), value.menu()));
        }
    }
}
