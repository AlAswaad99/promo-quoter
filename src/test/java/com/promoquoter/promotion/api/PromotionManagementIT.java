package com.promoquoter.promotion.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
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

    String productId;
    String promotionId;

    @BeforeEach
    void setUp() throws Exception {
        testDatabase.clean();
        String product = mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"name\":\"Mug\",\"category\":\"kitchen\",\"price\":10,\"stock\":10}]"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        productId = JsonPath.read(product, "$[0].id");
        String promotion = mockMvc.perform(post("/api/v1/promotions").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"type\":\"PERCENT_OFF_CATEGORY\",\"name\":\"10% kitchen\","
                                + "\"category\":\"kitchen\",\"percent\":10,\"priority\":5}]"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        promotionId = JsonPath.read(promotion, "$[0].id");
    }

    private ResultActions patchPromotion(String id, String json) throws Exception {
        return mockMvc.perform(patch("/api/v1/promotions/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions quoteMugs(String segment) throws Exception {
        return mockMvc.perform(post("/api/v1/cart/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":\"" + productId + "\",\"qty\":2}],\"customerSegment\":\""
                        + segment + "\"}"));
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
    void deactivatingStopsThePromotionFromApplying() throws Exception {
        quoteMugs("REGULAR").andExpect(jsonPath("$.total").value("18.00"));

        patchPromotion(promotionId, "{\"active\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        quoteMugs("REGULAR")
                .andExpect(jsonPath("$.total").value("20.00"))
                .andExpect(jsonPath("$.appliedPromotions", empty()));
    }

    @Test
    void reactivatingRestoresThePromotion() throws Exception {
        patchPromotion(promotionId, "{\"active\":false}").andExpect(status().isOk());
        patchPromotion(promotionId, "{\"active\":true}").andExpect(status().isOk());

        quoteMugs("REGULAR").andExpect(jsonPath("$.total").value("18.00"));
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

        quoteMugs("REGULAR").andExpect(jsonPath("$.total").value("20.00"));
        quoteMugs("VIP").andExpect(jsonPath("$.total").value("18.00"));

        patchPromotion(promotionId, "{\"eligibleSegments\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleSegments", hasSize(0)));

        quoteMugs("REGULAR").andExpect(jsonPath("$.total").value("18.00"));
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
        quoteMugs("REGULAR").andExpect(jsonPath("$.total").value("20.00"));
    }

    @Test
    void deletingAPromotionKeepsTheAuditTrailOfExistingOrders() throws Exception {
        String order = mockMvc.perform(post("/api/v1/cart/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + productId + "\",\"qty\":2}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(order, "$.orderId");

        mockMvc.perform(delete("/api/v1/promotions/" + promotionId)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("18.00"))
                .andExpect(jsonPath("$.appliedPromotions[0].promotionId").value(promotionId));
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
