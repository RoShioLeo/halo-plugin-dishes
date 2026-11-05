package com.dishes.service.kitchen;

import java.util.List;
import java.util.Map;

/** Shared-kitchen DTOs. Modified 2026-11-05, GPL-3.0. */
public final class KitchenModels {
    private KitchenModels() {}
    public record Identity(String ownerKey, String username, String displayName, boolean guest, boolean canManage, String avatarUrl) {}
    public record Selection(long dishId, String name, String imageUrl, double quantity, String note, List<String> tags) {}
    public record SelectionInput(long dishId, double quantity, String note) {}
    public record CartInput(String date, String period, String nickname, long revision,
                            List<SelectionInput> items, String offMenu, String remark, String targetParticipantId, String batchId) {}
    public record SubmitInput(String date, String period, String nickname, String batchId,
                              Map<String, Long> draftContributionVersions) {}
    public record SubmittedBatch(String id, String submittedAt, String submittedBy) {}
    public record MenuItem(Long dishId, String name, double quantity, String imageUrl, String note, String customId) {
        public MenuItem(Long dishId, String name, double quantity, String imageUrl, String note) {
            this(dishId, name, quantity, imageUrl, note, null);
        }
    }
    public record SourceQuantity(String key, double quantity) {}
    public record ParticipantQuantities(String participantId, List<SourceQuantity> items) {}
    public record CommitInput(String date, String period, String batchId, long stateRevision, long revision,
                              Map<String, Long> contributionVersions, String mode,
                              List<ParticipantQuantities> sourceQuantities, List<MenuItem> items, String note) {}
    public record Menu(String status, long revision, List<MenuItem> items, String note,
                       String confirmedAt, String confirmedBy) {}
}
