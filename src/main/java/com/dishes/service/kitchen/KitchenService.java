package com.dishes.service.kitchen;

import static com.dishes.service.kitchen.KitchenModels.*;

import com.dishes.api.BusinessErrorCode;
import com.dishes.api.BusinessException;
import com.dishes.domain.DishesStore;
import com.dishes.domain.service.DishTags;


import com.dishes.extension.MealTicketState;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import run.halo.app.extension.Metadata;

/** Shared meal commands, modified 2026-11-05 under GPL-3.0. */
@Service
public class KitchenService {
    private static final List<String> PERIODS = List.of("breakfast", "lunch", "dinner");
    private static final List<String> PERIOD_NAMES = List.of("早餐", "午餐", "晚餐");

    private final KitchenRepository repository;
    private final DishesStore catalogue;
    private final Clock clock;

    @Autowired
    public KitchenService(KitchenRepository repository, DishesStore catalogue) {
        this(repository, catalogue, Clock.system(ZoneId.of("Asia/Shanghai")));
    }
    public KitchenService(KitchenRepository repository, DishesStore catalogue, Clock clock) {
        this.repository = repository; this.catalogue = catalogue;
        this.clock = clock.withZone(ZoneId.of("Asia/Shanghai"));
    }

    public Map<String, Object> viewer(Identity identity) {
        return Map.of("authenticated", !identity.guest(), "displayName", identity.displayName(),
            "canManage", identity.canManage(), "today", today().toString(), "maxDate", today().plusDays(7).toString());
    }

    public synchronized Map<String, Object> order(String date, String period, Identity identity, boolean admin) {
        var day = parseDate(date); var meal = period(period);
        if (admin) requireChef(identity);
        return shared(day.toString(), meal, identity, admin);
    }

    /** Single-process command boundary plus persisted extension versions protect concurrent edits. */
    public synchronized Map<String, Object> saveCart(CartInput input, Identity identity) {
        if (input == null) throw invalid("请填写点餐内容");
        var date = writableDate(input.date()).toString();
        var period = period(input.period());
        if (input.revision() < 0) throw invalid("版本不正确");
        var name = actorName(identity, input.nickname());
        var state = state(date, period);
        var ticket = new Ticket(state.ticket(), state.existing());
        var activeBatch = ticket.entity().getSpec().getActiveBatchId();
        var all = state.contributions();
        var targetId = safe(input.targetParticipantId()).trim();
        var key = contributionKey(identity, date, period, activeBatch);
        MealSource contribution;
        if (targetId.isEmpty()) {
            contribution = all.stream().filter(c -> identity.ownerKey().equals(c.getSpec().getOwnerKey())
                && activeBatch.equals(batch(c.getSpec()))).findFirst().orElse(null);
            if (contribution != null) key = contribution.getId();
        } else {
            contribution = all.stream()
                .filter(c -> targetId.equals(c.getSpec().getParticipantId())).findFirst()
                .orElseThrow(() -> invalid("找不到这餐的点餐记录，请刷新后重试"));
            key = contribution.getId();
        }
        var existing = contribution != null;
        var previous = existing ? contribution.getSpec() : null;
        if (previous != null && (!activeBatch.equals(batch(previous)) || submitted(ticket, batch(previous)))) {
            throw new BusinessException(BusinessErrorCode.ACCESS_DENIED, "这批点餐已提交；主厨请在厨房调整，继续点餐会追加新的一批");
        }
        if (!activeBatch.equals(input.batchId())) {
            throw conflict("这餐已提交了新批次，请刷新后继续点餐");
        }
        var peerEdit = previous != null && !identity.ownerKey().equals(previous.getOwnerKey());
        var sourceDisplayName = peerEdit ? previous.getDisplayName() : name;
        var sourceAvatar = previous == null || (!peerEdit && targetId.isEmpty() && activeBatch.equals(batch(previous)))
            ? safe(identity.avatarUrl()) : safe(previous.getAvatarUrl());
        var revision = previous == null ? 0 : previous.getRevision();
        if (peerEdit && input.revision() != revision) throw conflict("这份点餐已被其他人更新，请刷新后重试");
        var quantityOnly = peerEdit;
        var items = quantityOnly ? peerQuantities(input.items(), previous, period) : selections(input.items(), previous, period);
        var offMenu = quantityOnly ? previous.getOffMenu() : text(input.offMenu(), 1000, "菜单外的想吃内容");
        var remark = quantityOnly ? previous.getRemark() : text(input.remark(), 500, "备注");
        if (previous != null && Objects.equals(previous.getDisplayName(), sourceDisplayName)
            && Objects.equals(safe(previous.getAvatarUrl()), sourceAvatar)
            && Objects.equals(previous.getItems(), items) && Objects.equals(previous.getOffMenu(), offMenu)
            && Objects.equals(previous.getRemark(), remark)) {
            return Map.of("order", shared(date, period, identity, false));
        }
        if (input.revision() != revision) throw conflict("你的点餐已在其他页面更新，请刷新后重试");
        if (!existing && items.isEmpty() && offMenu.isEmpty() && remark.isEmpty()) {
            return Map.of("order", shared(date, period, identity, false));
        }
        if (!existing) {
            contribution = new MealSource();
            contribution.setId(key);
        }
        var spec = new MealSource.Spec();
        spec.setDate(date); spec.setPeriod(period);
        spec.setBatchId(previous == null ? activeBatch : batch(previous));
        spec.setOwnerKey(previous == null ? identity.ownerKey() : previous.getOwnerKey());
        spec.setGuest(previous == null ? identity.guest() : previous.isGuest());
        spec.setUsername(previous == null ? identity.username() : previous.getUsername());
        spec.setDisplayName(sourceDisplayName);
        spec.setAvatarUrl(sourceAvatar);
        spec.setParticipantId(previous == null ? UUID.randomUUID().toString() : previous.getParticipantId());
        spec.setCreatedAt(previous == null ? now() : previous.getCreatedAt());
        spec.setCustomItems(previous == null ? List.of() : customItems(previous));
        spec.setRevision(revision + 1); spec.setItems(items); spec.setOffMenu(offMenu); spec.setRemark(remark);
        var editedAt = now(); spec.setUpdatedAt(editedAt);
        spec.setLastEditedBy(name); spec.setLastEditedAt(editedAt); spec.setLastEditedByUsername(identity.username());
        contribution.setSpec(spec);
        if (!existing) all.add(contribution);
        increment(state.ticket());
        repository.saveState(new KitchenState(state.ticket(), state.existing(), all, state.menu()));
        return Map.of("order", shared(date, period, identity, false));
    }

    public synchronized Map<String, Object> submit(SubmitInput input, Identity identity) {
        if (input == null) throw invalid("请提供要提交的点餐批次");
        var date = writableDate(input.date()).toString(); var period = period(input.period());
        var name = actorName(identity, input.nickname());
        var state = state(date, period);
        var ticket = new Ticket(state.ticket(), state.existing());
        var activeBatch = ticket.entity().getSpec().getActiveBatchId();
        if (!activeBatch.equals(input.batchId())) throw conflict("这批点餐已提交或已更新，请刷新后重试");
        var all = state.contributions();
        var draft = inBatch(all, activeBatch);
        if (!versions(draft).equals(input.draftContributionVersions() == null ? Map.of() : input.draftContributionVersions())) {
            throw conflict("有人更新了这批点餐，请查看最新内容后再提交");
        }
        rotate(ticket, draft, name);
        repository.saveState(new KitchenState(ticket.entity(), state.existing(), all, state.menu()));
        return Map.of("order", shared(date, period, identity, false));
    }

    public synchronized Map<String, Object> dailyMenu(String date, Identity identity) {
        var day = parseDate(date).toString();
        var periods = new ArrayList<Map<String, Object>>();
        for (var code : PERIODS) {
            var order = shared(day, code, identity, false);
            var menu = (Menu) order.get("menu");
            if (!"confirmed".equals(menu.status()) || menu.items().isEmpty()) continue;
            var row = new LinkedHashMap<String, Object>();
            row.put("code", code); row.put("name", periodName(code));
            row.put("status", "confirmed");
            row.put("needsConfirmation", order.get("needsConfirmation")); row.put("items", menu.items());
            row.put("note", menu.note()); row.put("confirmedAt", menu.confirmedAt()); periods.add(row);
        }
        return Map.of("date", day, "periods", periods);
    }

    public synchronized Map<String, Object> kitchen(String date, Identity identity) {
        requireChef(identity);
        var day = parseDate(date).toString();
        return Map.of("date", day, "periods", PERIODS.stream().map(p -> shared(day, p, identity, true)).toList());
    }

    /** A single detached proposal, allocation and publication, persisted with one meal-state CAS. */
    public synchronized Map<String, Object> commit(CommitInput input, Identity chef) {
        requireChef(chef);
        if (input == null) throw invalid("请提供待确认的菜单");
        var date = writableDate(input.date()).toString(); var period = period(input.period());
        var state = state(date, period); var ticket = new Ticket(state.ticket(), state.existing());
        var sources = state.contributions(); sources.sort(sourceOrder(ticket));
        var hadDraft = inBatch(sources, state.ticket().getSpec().getActiveBatchId()).stream().anyMatch(source -> active(source.getSpec()));
        var oldMenu = state.menu(); var revision = oldMenu == null ? 0 : oldMenu.getSpec().getRevision();
        if (state.ticket().getSpec().getRevision() != input.stateRevision()
            || !state.ticket().getSpec().getActiveBatchId().equals(input.batchId())
            || revision != input.revision() || !versions(sources).equals(input.contributionVersions())) {
            throw conflict("点餐或菜单已更新，请核对最新内容后重新确认");
        }
        if (!"requests".equals(input.mode()) && !"menu".equals(input.mode())) throw invalid("请选择本次调整的一侧");
        var desired = commitItems(input.items()); var desiredByKey = byKey(desired);
        var note = text(input.note(), 1000, "菜单备注");
        var original = new HashMap<String, MealSource.Spec>();
        for (var source : sources) original.put(source.getId(),
            KitchenSnapshots.copy(source.getSpec(), MealSource.Spec.class));
        if ("menu".equals(input.mode())) {
            if (input.sourceQuantities() != null && !input.sourceQuantities().isEmpty()) {
                throw invalid("菜单调整模式不能同时提交点餐人调整");
            }
            var baseline = sourceTotals(sources); var keys = new java.util.LinkedHashSet<>(baseline.keySet());
            keys.addAll(desiredByKey.keySet());
            for (var key : keys) {
                var current = baseline.getOrDefault(key, 0L);
                var target = desiredByKey.containsKey(key) ? (long) desiredByKey.get(key).quantity() : 0L;
                if (target == current) continue;
                if (target < current) {
                    var remaining = current - target;
                    for (int i = sources.size() - 1; i >= 0 && remaining > 0; i--) {
                        var source = sources.get(i); var quantity = sourceQuantity(source.getSpec(), key);
                        var deduction = Math.min(quantity, remaining);
                        if (deduction > 0) setSourceQuantity(source.getSpec(), key,
                            quantity - deduction, desiredByKey.get(key), period, false);
                        remaining = remaining - deduction;
                    }
                } else if (target > current) {
                    var source = chefSource(sources, state.ticket(), chef);
                    setSourceQuantity(source.getSpec(), key, sourceQuantity(source.getSpec(), key) + (target - current), desiredByKey.get(key), period, true);
                }
            }
        } else {
            var seen = new HashSet<String>();
            for (var adjustment : input.sourceQuantities() == null ? List.<ParticipantQuantities>of() : input.sourceQuantities()) {
                if (adjustment == null) throw invalid("点餐人调整不正确");
                var id = adjustment.participantId();
                var source = id == null ? chefSource(sources, state.ticket(), chef) : sources.stream()
                    .filter(row -> id.equals(row.getSpec().getParticipantId())).findFirst()
                    .orElseThrow(() -> invalid("点餐人已变更，请重新载入"));
                if (!seen.add(source.getId())) throw invalid("点餐人调整重复");
                var isChefOwner = chef.ownerKey().equals(source.getSpec().getOwnerKey());
                var isChefActive = isChefOwner
                    && state.ticket().getSpec().getActiveBatchId().equals(batch(source.getSpec()));
                var quantities = new LinkedHashMap<String, Double>();
                for (var item : adjustment.items() == null ? List.<SourceQuantity>of() : adjustment.items()) {
                    if (item == null || item.key() == null || quantities.putIfAbsent(item.key(), item.quantity()) != null) {
                        throw invalid("菜品数量调整不正确或重复");
                    }
                }
                var keys = new java.util.LinkedHashSet<String>();
                for (var item : source.getSpec().getItems()) keys.add("dish:" + item.dishId());
                for (var item : customItems(source.getSpec())) keys.add(itemKey(item));
                if (!isChefActive && !keys.containsAll(quantities.keySet())) throw invalid("只能给当前主厨新增菜品");
                keys.addAll(quantities.keySet());
                for (var key : keys) {
                    var quantity = quantities.getOrDefault(key, 0.0);
                    if (!Double.isFinite(quantity) || quantity < 0) throw invalid("份数不能为负数或无效数字");
                    if (quantity > 0) quantity(quantity, isChefOwner ? 9999 : Math.max(99, sourceQuantity(source.getSpec(), key)));
                    setSourceQuantity(source.getSpec(), key, quantity, desiredByKey.get(key), period, true);
                }
            }
        }
        // A custom row is a chef-authored dish, identified independently of its name.
        // Keep both projections in sync even when only its label/metadata changed.
        for (var source : sources) source.getSpec().setCustomItems(customItems(source.getSpec()).stream().map(item -> {
            var proposal = desiredByKey.get(itemKey(item));
            return proposal == null ? item : new MenuItem(null, proposal.name(), item.quantity(),
                proposal.imageUrl(), proposal.note(), item.customId());
        }).toList());
        var actual = sourceTotals(sources);
        if (!sameTotals(actual, desiredByKey)) throw invalid("点餐数量与最终菜单不一致，请重新核对");
        for (var source : sources) {
            var before = original.get(source.getId()); var after = source.getSpec();
            if (before == null || !Objects.equals(before.getItems(), after.getItems())
                || !Objects.equals(customItems(before), customItems(after))) {
                after.setRevision(before == null ? 1 : before.getRevision() + 1);
                after.setUpdatedAt(now()); after.setLastEditedAt(now()); after.setLastEditedBy(chef.displayName());
                after.setLastEditedByUsername(chef.username());
            }
        }
        var draft = inBatch(sources, state.ticket().getSpec().getActiveBatchId());
        if (hadDraft || draft.stream().anyMatch(source -> active(source.getSpec()))) ticket = rotate(ticket, draft, chef.displayName(), hadDraft);
        else increment(ticket.entity());
        var menu = new MealMenu();
        var spec = new MealMenu.Spec(); spec.setDate(date); spec.setPeriod(period); spec.setRevision(revision + 1);
        spec.setItems(desired); spec.setNote(note); spec.setConfirmedBy(chef.displayName()); spec.setConfirmedByUsername(chef.username());
        spec.setConfirmedAt(now()); spec.setContributionVersions(Map.copyOf(submittedVersions(sources, ticket))); menu.setSpec(spec);
        repository.saveState(new KitchenState(ticket.entity(), state.existing(), sources, menu));
        return Map.of("order", shared(date, period, chef, true));
    }

    private MealSource chefSource(List<MealSource> sources, MealTicketState ticket, Identity chef) {
        var batch = ticket.getSpec().getActiveBatchId();
        var existing = sources.stream().filter(source -> chef.ownerKey().equals(source.getSpec().getOwnerKey())
            && batch.equals(batch(source.getSpec()))).findFirst();
        if (existing.isPresent()) return existing.get();
        var source = new MealSource();
        source.setId(contributionKey(chef, ticket.getSpec().getDate(), ticket.getSpec().getPeriod(), batch));
        var spec = new MealSource.Spec(); spec.setDate(ticket.getSpec().getDate()); spec.setPeriod(ticket.getSpec().getPeriod());
        spec.setBatchId(batch); spec.setOwnerKey(chef.ownerKey()); spec.setUsername(chef.username()); spec.setGuest(false);
        spec.setDisplayName(chef.displayName()); spec.setAvatarUrl(safe(chef.avatarUrl())); spec.setParticipantId(UUID.randomUUID().toString());
        spec.setCreatedAt(now()); spec.setUpdatedAt(now()); spec.setItems(List.of()); spec.setCustomItems(List.of());
        spec.setOffMenu(""); spec.setRemark(""); source.setSpec(spec); sources.add(source); return source;
    }

    private static long sourceQuantity(MealSource.Spec source, String key) {
        for (var item : source.getItems()) if (key.equals("dish:" + item.dishId())) return (long) item.quantity();
        for (var item : customItems(source)) if (key.equals(itemKey(item))) return (long) item.quantity();
        return 0L;
    }
    private static Map<String, Long> sourceTotals(List<MealSource> sources) {
        var totals = new LinkedHashMap<String, Long>();
        for (var source : sources) {
            for (var item : source.getSpec().getItems()) totals.merge("dish:" + item.dishId(), (long) item.quantity(), Long::sum);
            for (var item : customItems(source.getSpec())) totals.merge(itemKey(item), (long) item.quantity(), Long::sum);
        }
        return totals;
    }
    private void setSourceQuantity(MealSource.Spec source, String key, double quantity, MenuItem proposal, String period, boolean validateIncrease) {
        var previous = sourceQuantity(source, key);
        if (quantity > 0) quantity(quantity, 9999);
        if (key.startsWith("dish:")) {
            long id;
            try { id = Long.parseLong(key.substring(5)); } catch (RuntimeException ex) { throw invalid("菜品标识不正确"); }
            if (id < 1) throw invalid("菜品标识不正确");
            var items = new ArrayList<>(source.getItems());
            var old = items.stream().filter(item -> item.dishId() == id).findFirst().orElse(null);
            var position = old == null ? items.size() : items.indexOf(old);
            var dish = catalogue.listDishes(null).stream().filter(item -> item.id() == id).findFirst().orElse(null);
            if (validateIncrease && quantity > previous && (dish == null || !dish.isAvailable() || dish.soldOut()
                || !DishTags.applicable(dish.tags(), period))) throw invalid("新增或增加的菜品已下架、缺货或不适用这餐");
            items.removeIf(item -> item.dishId() == id);
            if (quantity > 0) {
                if (old != null) items.add(position, new Selection(old.dishId(), old.name(), old.imageUrl(), quantity, old.note(), old.tags()));
                else if (dish != null) items.add(new Selection(id, dish.name(), safe(dish.imageUrl()), quantity, "", dish.tags()));
                else throw invalid("新增菜品不存在");
            }
            source.setItems(List.copyOf(items));
        } else if (key.startsWith("custom:")) {
            var id = key.substring(7);
            if (!id.matches("[A-Za-z0-9_-]{1,80}")) throw invalid("自定义菜品标识不正确");
            var items = new ArrayList<>(customItems(source)); var old = items.stream()
                .filter(item -> id.equals(item.customId())).findFirst().orElse(null);
            var position = old == null ? items.size() : items.indexOf(old);
            items.removeIf(item -> id.equals(item.customId()));
            if (quantity > 0) {
                if (old == null && (proposal == null || proposal.dishId() != null || !id.equals(proposal.customId()))) {
                    throw invalid("请提供自定义菜品信息");
                }
                items.add(position, withQuantity(old == null ? proposal : old, quantity));
            }
            source.setCustomItems(List.copyOf(items));
        } else throw invalid("菜品标识不正确");
    }
    private List<MenuItem> commitItems(List<MenuItem> inputs) {
        if (inputs == null || inputs.size() > 200) throw invalid("请提供菜单明细，最多 200 行");
        var result = new ArrayList<MenuItem>(); var keys = new HashSet<String>();
        for (var item : inputs) {
            if (item == null || !Double.isFinite(item.quantity()) || item.quantity() < 0) throw invalid("份数不正确");
            if (item.dishId() != null && item.dishId() < 1) throw invalid("菜品编号不正确");
            if (item.dishId() == null && (item.customId() == null || !item.customId().matches("[A-Za-z0-9_-]{1,80}"))) {
                throw invalid("自定义菜品需要独立标识");
            }
            if (!keys.add(itemKey(item))) throw invalid("菜单菜品重复");
            if (item.quantity() == 0) continue;
            result.addAll(menuItems(List.of(new MenuItem(item.dishId(), item.name(), item.quantity(), item.imageUrl(),
                item.note(), item.dishId() == null ? item.customId() : null))));
        }
        return List.copyOf(result);
    }
    private static Map<String, MenuItem> byKey(List<MenuItem> items) {
        var result = new LinkedHashMap<String, MenuItem>(); for (var item : items) result.put(itemKey(item), item); return result;
    }
    private static boolean sameTotals(Map<String, Long> actual, Map<String, MenuItem> desired) {
        if (!actual.keySet().equals(desired.keySet())) return false;
        for (var entry : actual.entrySet()) if (entry.getValue() != (long) desired.get(entry.getKey()).quantity()) return false;
        return true;
    }

    private Map<String, Object> shared(String date, String period, Identity identity, boolean admin) {
        var state = state(date, period);
        var all = state.contributions();
        var ticket = new Ticket(state.ticket(), state.existing());
        var activeBatch = ticket.entity().getSpec().getActiveBatchId();
        var participants = new ArrayList<Map<String, Object>>();
        var totals = new LinkedHashMap<Long, Aggregate>();
        MealSource.Spec own = null;
        all.sort(sourceOrder(ticket));
        for (var contribution : all) {
            var spec = contribution.getSpec();
            var mine = identity.ownerKey().equals(spec.getOwnerKey()) && activeBatch.equals(batch(spec));
            if (mine) own = spec;
            var submittedBatch = submittedBatch(ticket, batch(spec));
            if (!active(spec)) continue;
            var person = new LinkedHashMap<String, Object>();
            person.put("id", spec.getParticipantId()); person.put("displayName", spec.getDisplayName());
            person.put("avatarUrl", safe(spec.getAvatarUrl()));
            person.put("guest", spec.isGuest()); person.put("mine", mine); person.put("revision", spec.getRevision());
            person.put("batchId", batch(spec)); person.put("submitted", submittedBatch != null);
            person.put("submittedAt", submittedBatch == null ? null : submittedBatch.submittedAt());
            person.put("items", spec.getItems()); person.put("offMenu", spec.getOffMenu());
            person.put("customItems", customItems(spec)); person.put("createdAt", spec.getCreatedAt());
            person.put("remark", spec.getRemark()); person.put("updatedAt", spec.getUpdatedAt());
            person.put("lastEditedBy", spec.getLastEditedBy() == null ? spec.getDisplayName() : spec.getLastEditedBy());
            person.put("lastEditedAt", spec.getLastEditedAt() == null ? spec.getUpdatedAt() : spec.getLastEditedAt());
            if (admin && spec.getUsername() != null) person.put("username", spec.getUsername());
            if (admin && spec.getLastEditedByUsername() != null) person.put("lastEditedByUsername", spec.getLastEditedByUsername());
            participants.add(person);
            for (var item : spec.getItems()) {
                var total = totals.computeIfAbsent(item.dishId(), ignored -> new Aggregate(item));
                total.quantity += (long) item.quantity();
                total.people.add(Map.of("id", spec.getParticipantId(), "displayName", spec.getDisplayName(), "quantity", item.quantity()));
                if (!item.note().isBlank()) total.notes.add(spec.getDisplayName() + "：" + item.note());
            }
        }
        var totalRows = new ArrayList<Map<String, Object>>();
        var draftItems = new ArrayList<MenuItem>();
        for (var total : totals.values()) {
            totalRows.add(Map.of("dishId", total.selection.dishId(), "name", total.selection.name(),
                "imageUrl", total.selection.imageUrl(), "quantity", total.quantity, "people", total.people));
            var notes = String.join("；", total.notes);
            draftItems.add(new MenuItem(total.selection.dishId(), total.selection.name(), total.quantity,
                total.selection.imageUrl(), notes.length() > 255 ? notes.substring(0, 255) : notes));
        }
        var customTotals = new LinkedHashMap<String, MenuItem>();
        for (var contribution : all) for (var item : customItems(contribution.getSpec())) {
            var key = itemKey(item); var previous = customTotals.get(key);
            customTotals.put(key, previous == null ? item : withQuantity(previous, previous.quantity() + item.quantity()));
        }
        draftItems.addAll(customTotals.values());

        var persisted = state.menu();
        var currentVersions = versions(all);
        var submittedVersions = submittedVersions(all, ticket);
        Menu menu;
        boolean needsConfirmation;
        if (persisted == null) {
            menu = new Menu("draft", 0, draftItems, "", null, null);
            needsConfirmation = !submittedVersions.isEmpty();
        } else {
            var spec = persisted.getSpec();
            menu = new Menu("confirmed", spec.getRevision(), spec.getItems(), spec.getNote(), spec.getConfirmedAt(), spec.getConfirmedBy());
            needsConfirmation = !submittedVersions.equals(spec.getContributionVersions());
        }
        var mine = new LinkedHashMap<String, Object>();
        mine.put("revision", own == null ? 0L : own.getRevision());
        mine.put("items", own == null ? List.of() : own.getItems());
        mine.put("offMenu", own == null ? "" : own.getOffMenu());
        mine.put("remark", own == null ? "" : own.getRemark());
        mine.put("nickname", own == null ? identity.displayName() : own.getDisplayName());
        var result = new LinkedHashMap<String, Object>();
        result.put("date", date); result.put("period", period); result.put("periodName", periodName(period));
        result.put("needsConfirmation", needsConfirmation); result.put("participants", participants);
        result.put("totals", totalRows); result.put("mine", mine); result.put("menu", menu);
        result.put("customTotals", List.copyOf(customTotals.values()));
        result.put("activeBatchId", activeBatch);
        result.put("stateRevision", ticket.entity().getSpec().getRevision());
        var draft = inBatch(all, activeBatch);
        result.put("draftContributionVersions", versions(draft));
        result.put("hasDraftChanges", draft.stream().anyMatch(c -> active(c.getSpec())));
        result.put("submittedBatches", ticket.entity().getSpec().getSubmittedBatches());
        if (admin) {
            result.put("contributionVersions", currentVersions);
            result.put("workingItems", workingItems(all));
            result.put("chefParticipantId", own == null ? null : own.getParticipantId());
            result.put("chefDisplayName", identity.displayName()); result.put("chefAvatarUrl", safe(identity.avatarUrl()));
        }
        return result;
    }

    private List<Selection> peerQuantities(List<SelectionInput> inputs, MealSource.Spec previous, String period) {
        if (inputs == null) inputs = List.of();
        var incoming = new HashMap<Long, SelectionInput>();
        for (var item : inputs) {
            if (item == null || incoming.put(item.dishId(), item) != null) throw peerForbidden();
        }
        var expected = new HashSet<Long>();
        for (var item : previous.getItems()) expected.add(item.dishId());
        if (!expected.containsAll(incoming.keySet())) throw peerForbidden();
        var quantityInputs = new ArrayList<SelectionInput>();
        for (var item : previous.getItems()) {
            // A missing item or zero quantity removes it; remaining source fields stay unchanged.
            var submitted = incoming.get(item.dishId());
            if (submitted != null) quantityInputs.add(new SelectionInput(item.dishId(), submitted.quantity(), item.note()));
        }
        return selections(quantityInputs, previous, period);
    }

    private List<Selection> selections(List<SelectionInput> inputs, MealSource.Spec previous, String period) {
        if (inputs == null) inputs = List.of();
        if (inputs.size() > 50) throw invalid("每次最多选择 50 道菜");
        var catalogueById = new HashMap<Long, DishesStore.Dish>();
        for (var dish : catalogue.listDishes(null)) catalogueById.put(dish.id(), dish);
        var previousById = new HashMap<Long, Selection>();
        if (previous != null) for (var item : previous.getItems()) previousById.put(item.dishId(), item);
        var result = new ArrayList<Selection>(); var seen = new HashSet<Long>();
        for (var input : inputs) {
            if (input == null || input.dishId() < 1 || !seen.add(input.dishId())) throw invalid("菜品不正确或重复");
            if (input.quantity() == 0) continue;
            quantity(input.quantity(), 99);
            var note = text(input.note(), 255, "菜品备注");
            var old = previousById.get(input.dishId()); var dish = catalogueById.get(input.dishId());
            var addition = old == null || input.quantity() > old.quantity();
            if (addition && dish != null && dish.soldOut()) throw invalid("这道菜暂时缺货，只能减少或移除已经选择的份数");
            if (addition && (dish == null || !dish.isAvailable() || !DishTags.applicable(dish.tags(), period))) {
                throw invalid("新增菜品不存在、已下架或不适用这餐，请刷新菜单");
            }
            if (old != null) result.add(new Selection(old.dishId(), old.name(), old.imageUrl(), input.quantity(), note, old.tags()));
            else result.add(new Selection(dish.id(), dish.name(), safe(dish.imageUrl()), input.quantity(), note, dish.tags()));
        }
        return List.copyOf(result);
    }

    private List<MenuItem> menuItems(List<MenuItem> inputs) {
        if (inputs == null) throw invalid("请提供菜单明细（可为空列表）");
        if (inputs.size() > 200) throw invalid("确认菜单最多 200 行");
        var result = new ArrayList<MenuItem>();
        for (var item : inputs) {
            if (item == null) throw invalid("菜单明细不正确");
            quantity(item.quantity(), 9999);
            var name = text(item.name(), 128, "菜名");
            if (name.isBlank()) throw invalid("请填写菜名");
            if (item.dishId() != null && item.dishId() < 1) throw invalid("菜品编号不正确");
            var image = text(item.imageUrl(), 2048, "图片地址");
            if (!image.isEmpty() && !(image.startsWith("https://") || image.startsWith("http://") || image.startsWith("/"))) {
                throw invalid("图片地址需为站内路径或 HTTP(S) 链接");
            }
            result.add(new MenuItem(item.dishId(), name, item.quantity(), image, text(item.note(), 255, "菜品备注"), item.customId()));
        }
        return List.copyOf(result);
    }

    private static boolean active(MealSource.Spec value) {
        return !value.getItems().isEmpty() || !customItems(value).isEmpty()
            || !value.getOffMenu().isBlank() || !value.getRemark().isBlank();
    }
    private static Map<String, Long> versions(List<MealSource> contributions) {
        var versions = new LinkedHashMap<String, Long>();
        for (var contribution : contributions) versions.put(contribution.getSpec().getParticipantId(), contribution.getSpec().getRevision());
        return versions;
    }
    private static String contributionKey(Identity identity, String date, String period, String batchId) {
        return KitchenIdentityService.hash(identity.ownerKey() + "|" + date + "|" + period + "|" + batchId);
    }
    private static String batch(MealSource.Spec spec) { return spec.getBatchId(); }
    private static List<MealSource> inBatch(List<MealSource> all, String batchId) {
        return all.stream().filter(c -> batchId.equals(batch(c.getSpec()))).toList();
    }
    private static SubmittedBatch submittedBatch(Ticket ticket, String batchId) {
        return ticket.entity().getSpec().getSubmittedBatches().stream().filter(b -> batchId.equals(b.id())).findFirst().orElse(null);
    }
    private static boolean submitted(Ticket ticket, String batchId) { return submittedBatch(ticket, batchId) != null; }
    private static Map<String, Long> submittedVersions(List<MealSource> all, Ticket ticket) {
        return versions(all.stream().filter(c -> submitted(ticket, batch(c.getSpec()))).toList());
    }
    private KitchenState state(String date, String period) {
        var loaded = repository.load(date, period);
        loaded = KitchenSnapshots.copy(loaded);
        var entity = loaded.ticket();
        if (entity == null) {
            entity = new MealTicketState(); var metadata = new Metadata(); metadata.setName(menuKey(date, period)); entity.setMetadata(metadata);
            var spec = new MealTicketState.Spec(); spec.setDate(date); spec.setPeriod(period); spec.setActiveBatchId("initial");
            spec.setRevision(0); spec.setSubmittedBatches(List.of()); entity.setSpec(spec);
        }
        return new KitchenState(entity, loaded.existing(), loaded.contributions(), loaded.menu());
    }
    private Ticket rotate(Ticket ticket, List<MealSource> draft, String actorName) {
        return rotate(ticket, draft, actorName, false);
    }
    private Ticket rotate(Ticket ticket, List<MealSource> draft, String actorName, boolean clearedDraft) {
        if (!clearedDraft && draft.stream().noneMatch(c -> active(c.getSpec()))) throw invalid("这一批还没有点餐内容");
        var previous = ticket.entity().getSpec();
        var batches = new ArrayList<>(previous.getSubmittedBatches());
        batches.add(new SubmittedBatch(previous.getActiveBatchId(), now(), actorName));
        var spec = new MealTicketState.Spec(); spec.setDate(previous.getDate()); spec.setPeriod(previous.getPeriod());
        spec.setActiveBatchId(UUID.randomUUID().toString()); spec.setRevision(previous.getRevision() + 1);
        spec.setSubmittedBatches(List.copyOf(batches)); ticket.entity().setSpec(spec);
        return new Ticket(ticket.entity(), true);
    }
    private static void increment(MealTicketState ticket) { ticket.getSpec().setRevision(ticket.getSpec().getRevision() + 1); }
    private static List<MenuItem> customItems(MealSource.Spec source) {
        return source.getCustomItems() == null ? List.of() : source.getCustomItems();
    }
    private static String itemKey(MenuItem item) {
        return item.dishId() == null ? "custom:" + item.customId() : "dish:" + item.dishId();
    }
    private static MenuItem withQuantity(MenuItem item, double quantity) {
        return new MenuItem(item.dishId(), item.name(), quantity, item.imageUrl(), item.note(), item.customId());
    }
    private static Comparator<MealSource> sourceOrder(Ticket ticket) {
        var batches = ticket.entity().getSpec().getSubmittedBatches();
        return Comparator.<MealSource>comparingInt(source -> {
            for (int i = 0; i < batches.size(); i++) if (batches.get(i).id().equals(batch(source.getSpec()))) return i;
            return batches.size();
        }).thenComparing(source -> safe(source.getSpec().getCreatedAt()))
            .thenComparing(source -> source.getSpec().getParticipantId());
    }
    private List<MenuItem> workingItems(List<MealSource> sources) {
        var rows = new LinkedHashMap<String, MenuItem>();
        for (var source : sources) {
            for (var item : source.getSpec().getItems()) {
                var key = "dish:" + item.dishId(); var previous = rows.get(key);
                rows.put(key, previous == null ? new MenuItem(item.dishId(), item.name(), item.quantity(), item.imageUrl(), item.note())
                    : withQuantity(previous, previous.quantity() + item.quantity()));
            }
            for (var item : customItems(source.getSpec())) {
                var key = itemKey(item); var previous = rows.get(key);
                rows.put(key, previous == null ? item : withQuantity(previous, previous.quantity() + item.quantity()));
            }
        }
        return List.copyOf(rows.values());
    }
    private static String actorName(Identity identity, String nickname) {
        var name = identity.guest() ? text(nickname, 32, "昵称") : identity.displayName();
        if (identity.guest() && (name.isBlank() || name.codePoints().anyMatch(Character::isISOControl))) throw invalid("请填写 1–32 个字符的昵称");
        return name;
    }
    private record Ticket(MealTicketState entity, boolean existing) {}
    private static String menuKey(String date, String period) { return date + "-" + period; }
    private LocalDate today() { return LocalDate.now(clock); }
    private String now() { return OffsetDateTime.now(clock).toString(); }
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return today();
        try { return LocalDate.parse(value); } catch (RuntimeException ex) { throw invalid("日期格式应为 YYYY-MM-DD"); }
    }
    private LocalDate writableDate(String value) {
        var date = parseDate(value);
        if (date.isBefore(today()) || date.isAfter(today().plusDays(7))) throw invalid("只可点今天及未来 7 天的餐食");
        return date;
    }
    private static String period(String value) {
        if (!PERIODS.contains(value)) throw invalid("请选择早餐、午餐或晚餐");
        return value;
    }
    private static String periodName(String value) { return PERIOD_NAMES.get(PERIODS.indexOf(value)); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String text(String value, int max, String label) {
        var result = safe(value).trim();
        if (result.length() > max) throw invalid(label + "最多 " + max + " 个字符");
        if (result.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')) throw invalid(label + "包含不支持的控制字符");
        return result;
    }
    private static void quantity(double value, double max) {
        if (!Double.isFinite(value) || value != Math.rint(value) || value < 1 || value > max) {
            throw invalid("份数需为 1–" + (long) max + " 之间的整数");
        }
    }
    private static void requireChef(Identity identity) {
        if (identity.guest() || !identity.canManage()) throw new BusinessException(BusinessErrorCode.CHEF_REQUIRED, "仅厨师管理员可确认菜单");
    }
    private static BusinessException invalid(String message) { return new BusinessException(BusinessErrorCode.INVALID_REQUEST, message); }
    private static BusinessException conflict(String message) { return new BusinessException(BusinessErrorCode.CONFLICT, message); }
    private static BusinessException peerForbidden() {
        return new BusinessException(BusinessErrorCode.ACCESS_DENIED, "只能调整别人已点菜品的份数，不能添加新的菜品");
    }
    private static class Aggregate {
        final Selection selection; long quantity = 0;
        final List<Map<String, Object>> people = new ArrayList<>(); final List<String> notes = new ArrayList<>();
        Aggregate(Selection selection) { this.selection = selection; }
    }
}
