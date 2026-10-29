package com.promoquoter.promotion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class PromotionControllerIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PromotionRepository promotionRepository;

    @Autowired
    TestDatabase testDatabase;

    String productId;

    @BeforeEach
    void setUp() throws Exception {
        testDatabase.clean();
        String body = mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"name\":\"Mug\",\"category\":\"kitchen\",\"price\":12,\"stock\":10}]"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        productId = JsonPath.read(body, "$[0].id");
    }

    private ResultActions createPromotions(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/promotions").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String percentOff(String name, String category, String percent, String extra) {
        return "{\"type\":\"PERCENT_OFF_CATEGORY\",\"name\":\"" + name + "\",\"category\":\"" + category
                + "\",\"percent\":" + percent + extra + "}";
    }

    private String buyXGetY(String name, String product, String buy, String free, String extra) {
        return "{\"type\":\"BUY_X_GET_Y\",\"name\":\"" + name + "\",\"productId\":\"" + product + "\",\"buyQty\":"
                + buy + ",\"freeQty\":" + free + extra + "}";
    }

    @Test
    void createsPercentOffCategoryWithDefaults() throws Exception {
        createPromotions("[" + percentOff("10% kitchen", " kitchen ", "10", "") + "]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].type").value("PERCENT_OFF_CATEGORY"))
                .andExpect(jsonPath("$[0].name").value("10% kitchen"))
                .andExpect(jsonPath("$[0].category").value("KITCHEN"))
                .andExpect(jsonPath("$[0].percent").value(10.00))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].priority").value(0))
                .andExpect(jsonPath("$[0].eligibleSegments", hasSize(0)))
                .andExpect(jsonPath("$[0].productId").doesNotExist())
                .andExpect(jsonPath("$[0].buyQty").doesNotExist());
    }

    @Test
    void createsBuyXGetYWithSegmentsAndPriority() throws Exception {
        createPromotions("[" + buyXGetY("Mugs 2+1", productId, "2", "1",
                ",\"priority\":3,\"active\":false,\"eligibleSegments\":[\"VIP\",\"EMPLOYEE\"]") + "]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].type").value("BUY_X_GET_Y"))
                .andExpect(jsonPath("$[0].productId").value(productId))
                .andExpect(jsonPath("$[0].buyQty").value(2))
                .andExpect(jsonPath("$[0].freeQty").value(1))
                .andExpect(jsonPath("$[0].priority").value(3))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].eligibleSegments", contains("VIP", "EMPLOYEE")))
                .andExpect(jsonPath("$[0].category").doesNotExist());
    }

    @Test
    void createsMixedBatchAndPersistsIt() throws Exception {
        createPromotions("[" + percentOff("A", "kitchen", "5.5", "") + "," + buyXGetY("B", productId, "1", "1", "")
                + "]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].percent").value(5.50));

        assertThat(promotionRepository.count()).isEqualTo(2);
    }

    @Test
    void rejectsUnknownOrMissingType() throws Exception {
        createPromotions("[{\"type\":\"FREE_LUNCH\",\"name\":\"x\"}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        createPromotions("[{\"name\":\"x\",\"category\":\"k\",\"percent\":5}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void rejectsUnknownSegment() throws Exception {
        createPromotions("[" + percentOff("A", "k", "5", ",\"eligibleSegments\":[\"GOLD\"]") + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void rejectsPercentOutOfRange() throws Exception {
        createPromotions("[" + percentOff("A", "k", "0", "") + "," + percentOff("B", "k", "101", "") + ","
                + percentOff("C", "k", "-1", "") + "," + percentOff("D", "k", "10.123", "") + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field",
                        contains("[0].percent", "[1].percent", "[2].percent", "[3].percent")));
    }

    @Test
    void acceptsBoundaryPercents() throws Exception {
        createPromotions("[" + percentOff("A", "k", "0.01", "") + "," + percentOff("B", "k", "100", "") + "]")
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsMissingPercentCategoryAndName() throws Exception {
        createPromotions("[{\"type\":\"PERCENT_OFF_CATEGORY\"}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("[0].category", "[0].name", "[0].percent")));
    }

    @Test
    void rejectsInvalidBuyXGetYFields() throws Exception {
        createPromotions("[" + buyXGetY("A", productId, "0", "0", "") + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("[0].buyQty", "[0].freeQty")));
        createPromotions("[{\"type\":\"BUY_X_GET_Y\",\"name\":\"A\",\"buyQty\":1,\"freeQty\":1}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("[0].productId")));
    }

    @Test
    void rejectsEmptyOversizedAndNullBatches() throws Exception {
        createPromotions("[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("promotions"));
        createPromotions("[null]").andExpect(status().isBadRequest());

        String oversized = "[" + String.join(",", java.util.Collections.nCopies(101, percentOff("A", "k", "5", "")))
                + "]";
        createPromotions(oversized).andExpect(status().isBadRequest());
    }

    @Test
    void unknownProductIsUnprocessableAndNothingIsSaved() throws Exception {
        UUID missing = UUID.randomUUID();

        createPromotions("[" + percentOff("Fine", "k", "5", "") + "," + buyXGetY("Ghost", missing.toString(), "1", "1", "")
                + "]")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PRODUCT"))
                .andExpect(jsonPath("$.errors[0].field").value("[1].productId"));

        assertThat(promotionRepository.count()).isZero();
    }

    @Test
    void categoryPromotionsDoNotRequireExistingProducts() throws Exception {
        createPromotions("[" + percentOff("Future", "unreleased-category", "5", "") + "]")
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsBuyXGetYQuantitiesBeyondTheCap() throws Exception {
        createPromotions("[" + buyXGetY("A", productId, "2147483647", "2147483647", "") + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", contains("[0].buyQty", "[0].freeQty")));
    }

    @Test
    void acceptsBuyXGetYQuantitiesAtTheCap() throws Exception {
        createPromotions("[" + buyXGetY("A", productId, "1000", "1000", "") + "]")
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsAnUnknownField() throws Exception {
        createPromotions("[" + percentOff("A", "kitchen", "10", ",\"discount\":5") + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void persistedSegmentsAreReadBack() throws Exception {
        createPromotions("[" + percentOff("A", "k", "5", ",\"eligibleSegments\":[\"VIP\"]") + "]")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/promotions"))
                .andExpect(jsonPath("$.content[0].eligibleSegments", contains("VIP")));
    }

    @Test
    void listsAndFiltersByTypeAndActive() throws Exception {
        createPromotions("[" + percentOff("A", "k", "5", "") + "," + percentOff("B", "k", "5", ",\"active\":false")
                + "," + buyXGetY("C", productId, "1", "1", "") + "]").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/promotions").param("sort", "name,asc"))
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/v1/promotions").param("type", "PERCENT_OFF_CATEGORY").param("sort", "name,asc"))
                .andExpect(jsonPath("$.content[*].name", contains("A", "B")));
        mockMvc.perform(get("/api/v1/promotions").param("active", "false"))
                .andExpect(jsonPath("$.content[*].name", contains("B")));
        mockMvc.perform(get("/api/v1/promotions").param("type", "BUY_X_GET_Y").param("active", "true"))
                .andExpect(jsonPath("$.content[*].name", contains("C")));
        mockMvc.perform(get("/api/v1/promotions").param("type", "BUY_X_GET_Y").param("active", "false"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listPaginatesAndSorts() throws Exception {
        createPromotions("[" + percentOff("C", "k", "5", ",\"priority\":1") + "," + percentOff("A", "k", "5", ",\"priority\":3")
                + "," + percentOff("B", "k", "5", ",\"priority\":2") + "]").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/promotions").param("size", "2").param("page", "1").param("sort", "priority,desc"))
                .andExpect(jsonPath("$.content[*].name", contains("C")))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void listRejectsBadTypeAndSort() throws Exception {
        mockMvc.perform(get("/api/v1/promotions").param("type", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(get("/api/v1/promotions").param("sort", "percent"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void createsTieredBulk() throws Exception {
        createPromotions("[{\"type\":\"TIERED_BULK\",\"name\":\"5% off 10+\",\"productId\":\"" + productId
                + "\",\"minQty\":10,\"percent\":5,\"eligibleSegments\":[\"REGULAR\"]}]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].type").value("TIERED_BULK"))
                .andExpect(jsonPath("$[0].productId").value(productId))
                .andExpect(jsonPath("$[0].minQty").value(10))
                .andExpect(jsonPath("$[0].percent").value(5.00))
                .andExpect(jsonPath("$[0].eligibleSegments", contains("REGULAR")))
                .andExpect(jsonPath("$[0].buyQty").doesNotExist());

        mockMvc.perform(get("/api/v1/promotions").param("type", "TIERED_BULK"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].minQty").value(10));
    }

    @Test
    void rejectsInvalidTieredBulkFields() throws Exception {
        createPromotions("[{\"type\":\"TIERED_BULK\",\"name\":\"x\",\"productId\":\"" + productId
                + "\",\"minQty\":1,\"percent\":101}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("[0].minQty", "[0].percent")));
        createPromotions("[{\"type\":\"TIERED_BULK\",\"name\":\"x\",\"minQty\":5,\"percent\":5}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("[0].productId")));
    }

    @Test
    void tieredBulkForAnUnknownProductIsUnprocessable() throws Exception {
        createPromotions("[{\"type\":\"TIERED_BULK\",\"name\":\"x\",\"productId\":\"" + UUID.randomUUID()
                + "\",\"minQty\":5,\"percent\":5}]")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PRODUCT"));
    }

    @Test
    void rejectsATieredBulkThresholdAboveTheCartLimit() throws Exception {
        createPromotions("[{\"type\":\"TIERED_BULK\",\"name\":\"A\",\"productId\":\"" + productId
                + "\",\"minQty\":1001,\"percent\":5}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("[0].minQty"));
    }

    @Test
    void acceptsATieredBulkThresholdAtTheCartLimit() throws Exception {
        createPromotions("[{\"type\":\"TIERED_BULK\",\"name\":\"A\",\"productId\":\"" + productId
                + "\",\"minQty\":1000,\"percent\":5}]")
                .andExpect(status().isCreated());
    }
}
