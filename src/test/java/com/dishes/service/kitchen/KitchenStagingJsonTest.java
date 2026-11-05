package com.dishes.service.kitchen;

import static org.junit.jupiter.api.Assertions.*;
import com.dishes.service.kitchen.KitchenModels.CartInput;
import com.dishes.service.kitchen.KitchenModels.SubmitInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class KitchenStagingJsonTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void submitAcceptsCanonicalDraftVersionsAndConvertsJsonScalarsToLong() throws Exception {
        var input = mapper.readValue("""
            {"date":"2026-11-05","period":"lunch","nickname":"阿冰","batchId":"initial",
             "draftContributionVersions":{"opaque-participant":2}}
            """, SubmitInput.class);
        assertEquals(2L, input.draftContributionVersions().get("opaque-participant"));
    }

    @Test
    void cartCarriesExpectedActiveBatchAlongsideTarget() throws Exception {
        var input = mapper.readValue("""
            {"date":"2026-11-05","period":"lunch","nickname":"阿冰","revision":2,
             "batchId":"current-batch","targetParticipantId":"submitted-source","items":[],"offMenu":"","remark":""}
            """, CartInput.class);
        assertEquals("current-batch", input.batchId());
        assertEquals("submitted-source", input.targetParticipantId());
    }
}
