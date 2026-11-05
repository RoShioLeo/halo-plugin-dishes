package com.dishes.service.kitchen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dishes.ExtensionSchemeRegistry;
import com.dishes.api.BusinessErrorCode;
import com.dishes.api.BusinessException;
import com.dishes.extension.MealTicketState;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class HaloKitchenRepositoryAtomicTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final HaloKitchenRepository repository = new HaloKitchenRepository(client, mock(ExtensionSchemeRegistry.class));

    @Test
    void newMealInitializesEmptyAndWritesItsSourcesAndMenuWithExactlyOneCreate() {
        when(client.fetch(MealTicketState.class, "2026-11-05-lunch")).thenReturn(Mono.empty());
        when(client.create(any(MealTicketState.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        var empty = repository.load("2026-11-05", "lunch");
        assertNull(empty.ticket()); assertFalse(empty.existing()); assertTrue(empty.contributions().isEmpty());
        var header = header(); header.getMetadata().setVersion(null);
        repository.saveState(new KitchenState(header, false, List.of(source()), null));
        var captured = ArgumentCaptor.forClass(MealTicketState.class); verify(client).create(captured.capture());
        assertEquals("source-original", captured.getValue().getSpec().getSources().getFirst().getId());
        assertEquals("private-owner", captured.getValue().getSpec().getSources().getFirst().getSpec().getOwnerKey());
        assertNull(header.getSpec().getSources());
        verify(client).fetch(MealTicketState.class, "2026-11-05-lunch"); verifyNoMoreInteractions(client);
    }

    @Test
    void aggregateReadUsesOneDetachedHeaderSnapshotForBothSourcesAndMenu() {
        var header = header(); header.getSpec().setSources(List.of(source()));
        var menu = new MealMenu.Spec(); menu.setRevision(8); menu.setItems(List.of()); header.getSpec().setConfirmedMenu(menu);
        when(client.fetch(MealTicketState.class, "2026-11-05-lunch")).thenReturn(Mono.just(header));
        var read = repository.load("2026-11-05", "lunch");
        assertEquals(8, read.menu().getSpec().getRevision()); assertEquals(1, read.contributions().size());
        read.contributions().getFirst().getSpec().setDisplayName("detached");
        assertEquals("甲", header.getSpec().getSources().getFirst().getSpec().getDisplayName());
        assertEquals(Instant.parse("2026-11-05T00:00:00Z"), read.ticket().getMetadata().getCreationTimestamp());
        verify(client, times(1)).fetch(MealTicketState.class, "2026-11-05-lunch"); verifyNoMoreInteractions(client);
    }

    @Test
    void failedSingleCasLeavesBorrowedSnapshotUntouched() {
        var header = header();
        when(client.update(any(MealTicketState.class))).thenReturn(Mono.error(new OptimisticLockingFailureException("competing state")));
        assertEquals(BusinessErrorCode.CONFLICT, assertThrows(BusinessException.class,
            () -> repository.saveState(new KitchenState(header, true, List.of(source()), null))).code());
        assertNull(header.getSpec().getSources());
        var captured = ArgumentCaptor.forClass(MealTicketState.class); verify(client, times(1)).update(captured.capture());
        assertEquals(7L, captured.getValue().getMetadata().getVersion()); verifyNoMoreInteractions(client);
    }

    private MealTicketState header() {
        var header = new MealTicketState(); var metadata = new Metadata(); metadata.setName("2026-11-05-lunch"); metadata.setVersion(7L);
        metadata.setCreationTimestamp(Instant.parse("2026-11-05T00:00:00Z")); header.setMetadata(metadata);
        var spec = new MealTicketState.Spec(); spec.setDate("2026-11-05"); spec.setPeriod("lunch");
        spec.setActiveBatchId("initial"); spec.setRevision(3); spec.setSubmittedBatches(List.of()); header.setSpec(spec); return header;
    }
    private MealSource source() {
        var source = new MealSource(); source.setId("source-original");
        var spec = new MealSource.Spec(); spec.setDate("2026-11-05"); spec.setPeriod("lunch"); spec.setOwnerKey("private-owner");
        spec.setParticipantId("opaque-original"); spec.setDisplayName("甲"); spec.setItems(List.of()); spec.setCustomItems(List.of());
        spec.setOffMenu("愿望"); spec.setRemark(""); source.setSpec(spec); return source;
    }
}