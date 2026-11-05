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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KitchenServiceTest {
    private final MemoryRepository repository = new MemoryRepository();
    private final DishesStore catalogue = mock(DishesStore.class);
    private final Identity a = new Identity("private-a", null, "", true, false, "");
    private final Identity b = new Identity("private-b", null, "", true, false, "");
    private final Identity chef = new Identity("private-chef", "chef-private-account", "主厨", false, true, "/chef.png");
    private final KitchenService service = new KitchenService(repository, catalogue,
        Clock.fixed(Instant.parse("2026-11-05T01:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void menu() {
        when(catalogue.listDishes(any())).thenReturn(List.of(dish(false)));
    }
    private DishesStore.Dish dish(boolean soldOut) {
        return new DishesStore.Dish(1, 1, "家常", "番茄汤", "/tomato.png", 4, "", true, 0, List.of("午餐"), soldOut);
    }

    @Test
    void guestsShareOneMealAndSameNicknameCannotImpersonateAnotherOwner() {
        cart(a, "同名访客", 2, "", ""); cart(b, "同名访客", 3, "", "");
        var view = order(a);
        assertEquals(2, people(view).size()); assertEquals(5L, total(view));
        assertEquals(2, ((List<Selection>) mine(view).get("items")).getFirst().quantity());
        assertEquals(3, ((List<Selection>) mine(order(b)).get("items")).getFirst().quantity());
        assertEquals(1, people(view).stream().filter(row -> (boolean) row.get("mine")).count());
    }

    @Test
    void peerCanAdjustOrZeroExistingQuantityButCannotChangeNotesWishesCreatorOrAddAnotherDish() {
        var initial = cart(a, "原点餐人", 2, "想喝水", "少油"); var source = people(initial).getFirst();
        var input = new CartInput("2026-11-05", "lunch", "共同编辑", 1,
            List.of(new SelectionInput(1, 3, "伪造备注")), "伪造愿望", "伪造整单备注", (String) source.get("id"), (String) initial.get("activeBatchId"));
        var edited = unwrap(service.saveCart(input, b)); var after = people(edited).getFirst();
        assertEquals("原点餐人", after.get("displayName")); assertEquals("想喝水", after.get("offMenu"));
        assertEquals("少油", after.get("remark")); assertEquals("原菜品备注", ((List<Selection>) after.get("items")).getFirst().note());
        assertEquals("共同编辑", after.get("lastEditedBy"));
        assertEquals(BusinessErrorCode.CONFLICT, assertThrows(BusinessException.class, () -> service.saveCart(input, b)).code());
        var illegal = new CartInput("2026-11-05", "lunch", "共同编辑", 2,
            List.of(new SelectionInput(999, 1, "")), "", "", (String) source.get("id"), (String) initial.get("activeBatchId"));
        assertEquals(BusinessErrorCode.ACCESS_DENIED, assertThrows(BusinessException.class, () -> service.saveCart(illegal, b)).code());
        var cleared = unwrap(service.saveCart(new CartInput("2026-11-05", "lunch", "共同编辑", 2,
            List.of(new SelectionInput(1, 0, "")), "", "", (String) source.get("id"), (String) initial.get("activeBatchId")), b));
        assertTrue(((List<?>) people(cleared).getFirst().get("items")).isEmpty());
        assertEquals("想喝水", people(cleared).getFirst().get("offMenu"));
    }

    @Test
    void submittedMealIsLockedForOwnerAndPeerAndContinuingAddsAnIndependentBatch() {
        var first = cart(a, "甲", 2, "", ""); var id = (String) people(first).getFirst().get("id");
        var submitted = unwrap(service.submit(new SubmitInput("2026-11-05", "lunch", "甲",
            (String) first.get("activeBatchId"), draftVersions(first)), a));
        for (var actor : List.of(a, b)) assertEquals(BusinessErrorCode.ACCESS_DENIED,
            assertThrows(BusinessException.class, () -> service.saveCart(new CartInput("2026-11-05", "lunch", "访客", 1,
                List.of(new SelectionInput(1, 5, "")), "", "", id, (String) submitted.get("activeBatchId")), actor)).code());
        assertEquals(BusinessErrorCode.CONFLICT, assertThrows(BusinessException.class, () -> service.submit(
            new SubmitInput("2026-11-05", "lunch", "甲", (String) first.get("activeBatchId"), draftVersions(first)), a)).code());
        var appended = cart(a, "甲", 1, "", "");
        assertEquals(2, people(appended).size()); assertEquals(3L, total(appended));
        assertEquals(1, people(appended).stream().filter(row -> (boolean) row.get("submitted")).count());
    }

    @Test
    void publicChefHasOnlyPeerQuantityPermissionAndCannotEditSubmittedSources() {
        var initial = cart(a, "甲", 2, "原愿望", "原备注"); var id = (String) people(initial).getFirst().get("id");
        var pending = unwrap(service.saveCart(new CartInput("2026-11-05", "lunch", "不能冒名", 1,
            List.of(new SelectionInput(1, 1, "伪造菜品备注")), "伪造愿望", "伪造备注", id,
            (String) initial.get("activeBatchId")), chef));
        var source = people(pending).getFirst();
        assertEquals("甲", source.get("displayName")); assertEquals("原愿望", source.get("offMenu"));
        assertEquals("原备注", source.get("remark")); assertEquals("原菜品备注", ((List<Selection>) source.get("items")).getFirst().note());
        var locked = unwrap(service.submit(new SubmitInput("2026-11-05", "lunch", "甲",
            (String) pending.get("activeBatchId"), draftVersions(pending)), a));
        assertEquals(BusinessErrorCode.ACCESS_DENIED, assertThrows(BusinessException.class,
            () -> service.saveCart(new CartInput("2026-11-05", "lunch", "", 2, List.of(), "", "", id,
                (String) locked.get("activeBatchId")), chef)).code());
    }

    @Test
    void cartRequiresTheObservedCurrentBatch() {
        assertEquals(BusinessErrorCode.CONFLICT, assertThrows(BusinessException.class,
            () -> service.saveCart(new CartInput("2026-11-05", "lunch", "甲", 0,
                List.of(new SelectionInput(1, 1, "")), "", "", null, null), a)).code());
        assertTrue(people(order(a)).isEmpty());
    }

    @Test
    void publicCartAcceptsOnlyWholeServingsAndCapsNewQuantitiesAtNinetyNine() {
        for (var quantity : new double[]{0.5, 1.5, -1, Double.NaN, Double.POSITIVE_INFINITY, 100}) {
            assertEquals(BusinessErrorCode.INVALID_REQUEST,
                assertThrows(BusinessException.class, () -> cart(a, "甲", quantity, "", "")).code());
        }
        cart(a, "甲", 99, "", "");
        assertTrue(people(cart(a, "甲", 0, "", "")).isEmpty());
    }

    @Test
    void soldOutRemainsVisibleButCannotBeAddedOrIncreasedAndExistingServingsCanBeRemoved() {
        var original = cart(a, "甲", 3, "", ""); var id = (String) people(original).getFirst().get("id");
        when(catalogue.listDishes(any())).thenReturn(List.of(dish(true)));
        assertThrows(BusinessException.class, () -> cart(b, "乙", 1, "", ""));
        assertThrows(BusinessException.class, () -> cart(a, "甲", 4, "", ""));
        assertThrows(BusinessException.class, () -> service.saveCart(new CartInput("2026-11-05", "lunch", "乙", 1,
            List.of(new SelectionInput(1, 4, "")), "", "", id, (String) original.get("activeBatchId")), b));
        assertEquals(2L, total(cart(a, "甲", 2, "", "")));
        assertTrue(people(cart(a, "甲", 0, "", "")).isEmpty());
        when(catalogue.listDishes(any())).thenReturn(List.of(dish(false)));
        assertEquals(1L, total(cart(b, "乙", 1, "", "")));
    }

    @Test
    void dateWindowMealTagsAndTargetContextAreCheckedBeforeAnyWrite() {
        var initial = cart(a, "甲", 1, "", ""); var id = (String) people(initial).getFirst().get("id");
        assertThrows(BusinessException.class, () -> service.saveCart(new CartInput("2026-11-05", "dinner", "甲", 0,
            List.of(new SelectionInput(1, 1, "")), "", "", null, "initial"), a));
        assertThrows(BusinessException.class, () -> service.saveCart(new CartInput("2026-11-05", "dinner", "乙", 1,
            List.of(), "", "", id, "initial"), b));
        service.saveCart(new CartInput("2026-11-12", "lunch", "甲", 0,
            List.of(new SelectionInput(1, 1, "")), "", "", null, "initial"), a);
        assertThrows(BusinessException.class, () -> service.saveCart(new CartInput("2026-11-13", "lunch", "甲", 0,
            List.of(new SelectionInput(1, 1, "")), "", "", null, "initial"), a));
    }

    @Test
    void pendingChoicesAndWishesDoNotPublishTheHomeMenuOrBecomeCustomDishes() {
        cart(a, "甲", 2, "想吃水果", "少盐"); var before = admin();
        assertEquals(1, ((List<?>) before.get("workingItems")).size());
        assertTrue(((List<?>) service.dailyMenu("2026-11-05", a).get("periods")).isEmpty());
        var committed = commit(before);
        assertEquals(1, ((List<?>) service.dailyMenu("2026-11-05", a).get("periods")).size());
        assertTrue(people(committed).stream().allMatch(row -> ((List<?>) row.get("customItems")).isEmpty()));
        cart(b, "乙", 1, "", "");
        assertEquals(2, ((Menu) order(a).get("menu")).items().getFirst().quantity());
        assertTrue((boolean) order(a).get("hasDraftChanges"));
    }

    @Test
    void publicIdentityProjectionKeepsCreatorAvatarAndNeverLeaksOwnerOrAccount() throws Exception {
        var logged = new Identity("secret-owner", "secret-account", "真实昵称", false, false, "/avatar.png");
        var initial = cart(logged, "伪造昵称", 2, "愿望", "备注"); var source = people(initial).getFirst();
        var updated = unwrap(service.saveCart(new CartInput("2026-11-05", "lunch", "共同编辑", 1,
            List.of(new SelectionInput(1, 1, "")), "", "", (String) source.get("id"), (String) initial.get("activeBatchId")), b));
        assertEquals("真实昵称", people(updated).getFirst().get("displayName"));
        assertEquals("/avatar.png", people(updated).getFirst().get("avatarUrl"));
        var serialized = new ObjectMapper().writeValueAsString(updated);
        assertFalse(serialized.contains("secret-owner")); assertFalse(serialized.contains("secret-account"));
        assertFalse(serialized.contains("ownerKey")); assertFalse(serialized.contains("lastEditedByUsername"));
    }

    @Test
    void editingDoesNotReorderTheReturnedParticipantSequence() {
        cart(a, "甲", 2, "", ""); cart(b, "乙", 3, "", "");
        var before = people(order(a)).stream().map(row -> row.get("id")).toList();
        cart(a, "甲", 4, "", "");
        assertEquals(before, people(order(a)).stream().map(row -> row.get("id")).toList());
    }

    private Map<String, Object> cart(Identity identity, String nickname, double quantity, String wish, String remark) {
        var before = order(identity);
        return unwrap(service.saveCart(new CartInput("2026-11-05", "lunch", nickname, ((Number) mine(before).get("revision")).longValue(),
            List.of(new SelectionInput(1, quantity, "原菜品备注")), wish, remark, null, (String) before.get("activeBatchId")), identity));
    }
    private Map<String, Object> commit(Map<String, Object> before) {
        return unwrap(service.commit(new CommitInput("2026-11-05", "lunch", (String) before.get("activeBatchId"),
            ((Number) before.get("stateRevision")).longValue(), ((Menu) before.get("menu")).revision(),
            (Map<String, Long>) before.get("contributionVersions"), "menu", null, (List<MenuItem>) before.get("workingItems"), ""), chef));
    }
    private Map<String, Object> order(Identity identity) { return service.order("2026-11-05", "lunch", identity, false); }
    private Map<String, Object> admin() { return service.order("2026-11-05", "lunch", chef, true); }
    private Map<String, Object> unwrap(Map<String, Object> result) { return (Map<String, Object>) result.get("order"); }
    private Map<String, Object> mine(Map<String, Object> result) { return (Map<String, Object>) result.get("mine"); }
    private List<Map<String, Object>> people(Map<String, Object> result) { return (List<Map<String, Object>>) result.get("participants"); }
    private Map<String, Long> draftVersions(Map<String, Object> result) { return (Map<String, Long>) result.get("draftContributionVersions"); }
    private long total(Map<String, Object> result) { return ((Number) ((List<Map<String, Object>>) result.get("totals")).getFirst().get("quantity")).longValue(); }

    private static class MemoryRepository implements KitchenRepository {
        final Map<String, KitchenState> states = new HashMap<>();
        public KitchenState load(String date, String period) {
            return states.getOrDefault(date + "-" + period, new KitchenState(null, false, new ArrayList<>(), null));
        }
        public void saveState(KitchenState state) {
            states.put(state.ticket().getMetadata().getName(), KitchenSnapshots.copy(
                new KitchenState(state.ticket(), true, state.contributions(), state.menu())));
        }
    }
}
