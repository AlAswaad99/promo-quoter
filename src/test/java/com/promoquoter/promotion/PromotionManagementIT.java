package com.promoquoter.promotion;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.promoquoter.support.IntegrationTest;
import com.promoquoter.support.TestDatabase;

@IntegrationTest
class PromotionManagementIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestDatabase testDatabase;

    String promotionId;

    @BeforeEach
    void setUp() throws Exception {
        testDatabase.clean();
        String promotion = mockMvc.perform(post("/api/v1/promotions").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"type\":\"PERCENT_OFF_CATEGORY\",\"name\":\"10% kitchen\","
                                + "\"category\":\"kitchen\",\"percent\":10,\"priority\":5}]"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        promotionId = JsonPath.read(promotion, "$[0].id");
    }

    private ResultActions patchPromotion(String id, String json) throws Exception {
        return mockMvc.perform(patch("/api/v1/promotions/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void getsAPromotionById() throws Exception {
        mockMvc.perform(get("/api/v1/promotions/" + promotionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(promotionId))
                .andExpect(jsonPath("$.name").value("10% kitchen"))
                .andExpect(jsonPath("$.category").value("KITCHEN"))
                .andExpect(jsonPath("$.priority").value(5));
    }

    @Test
    void unknownPromotionIsNotFound() throws Exception {
        String missing = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/promotions/" + missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_NOT_FOUND"));
        patchPromotion(missing, "{\"active\":false}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/promotions/" + missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_NOT_FOUND"));
    }

    @Test
    void malformedIdIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/promotions/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void deactivatingMarksThePromotionInactive() throws Exception {
        patchPromotion(promotionId, "{\"active\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/promotions/" + promotionId)).andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/v1/promotions").param("active", "true"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/promotions").param("active", "false"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void reactivatingMarksThePromotionActiveAgain() throws Exception {
        patchPromotion(promotionId, "{\"active\":false}").andExpect(status().isOk());
        patchPromotion(promotionId, "{\"active\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/v1/promotions").param("active", "true"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void patchChangesOnlyTheGivenFields() throws Exception {
        patchPromotion(promotionId, "{\"name\":\"  Renamed  \",\"priority\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.priority").value(1))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.percent").value(10.0));
    }

    @Test
    void patchCanRestrictAndThenOpenUpSegments() throws Exception {
        patchPromotion(promotionId, "{\"eligibleSegments\":[\"VIP\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleSegments", contains("VIP")));
        mockMvc.perform(get("/api/v1/promotions/" + promotionId))
                .andExpect(jsonPath("$.eligibleSegments", contains("VIP")));

        patchPromotion(promotionId, "{\"eligibleSegments\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleSegments", hasSize(0)));
        mockMvc.perform(get("/api/v1/promotions/" + promotionId))
                .andExpect(jsonPath("$.eligibleSegments", hasSize(0)));
    }

    @Test
    void emptyPatchIsRejected() throws Exception {
        patchPromotion(promotionId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("body"));
    }

    @Test
    void invalidPatchValuesAreRejected() throws Exception {
        patchPromotion(promotionId, "{\"name\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        patchPromotion(promotionId, "{\"priority\":2000000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void deletingRemovesThePromotionEverywhere() throws Exception {
        mockMvc.perform(delete("/api/v1/promotions/" + promotionId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/v1/promotions/" + promotionId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/promotions")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void deletingTwiceReturnsNotFoundTheSecondTime() throws Exception {
        mockMvc.perform(delete("/api/v1/promotions/" + promotionId)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/promotions/" + promotionId)).andExpect(status().isNotFound());
    }

    @Test
    void patchCannotChangeTheRuleParameters() throws Exception {
        patchPromotion(promotionId, "{\"percent\":99}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
