package com.promoquoter.promotion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.promoquoter.support.ApiFixtures;
import com.promoquoter.support.IntegrationTest;
import com.promoquoter.support.TestDatabase;

@IntegrationTest
class PromotionRulesIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApiFixtures api;

    @Autowired
    TestDatabase testDatabase;

    @Autowired
    PromotionRepository promotionRepository;

    String mug;
    String lamp;

    @BeforeEach
    void setUp() throws Exception {
        testDatabase.clean();
        mug = api.createProduct("Mug", "kitchen", "12.00", 10);
        lamp = api.createProduct("Lamp", "home", "30.00", 5);
    }

    private ResultActions create(String... rules) throws Exception {
        return mockMvc.perform(post("/api/v1/promotions").contentType(MediaType.APPLICATION_JSON)
                .content("[" + String.join(",", rules) + "]"));
    }

    private String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$[0].id");
    }

    private ResultActions quote(String items, String segment) throws Exception {
        return mockMvc.perform(post("/api/v1/cart/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[" + items + "],\"customerSegment\":\"" + segment + "\"}"));
    }

    private String item(String productId, int qty) {
        return "{\"productId\":\"" + productId + "\",\"qty\":" + qty + "}";
    }

    private static String percentRule(String extra) {
        return "{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Kitchen deal\","
                + "\"target\":{\"field\":\"product.category\",\"op\":\"eq\",\"value\":\"kitchen\"},"
                + "\"action\":{\"kind\":\"PERCENT_OFF\",\"percent\":10}" + extra + "}";
    }

    @Test
    void createsAPricingRuleAndReturnsItInGenericForm() throws Exception {
        create("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Bulk deal\",\"priority\":3,"
                + "\"eligibleSegments\":[\"VIP\"],"
                + "\"when\":{\"field\":\"cart.subtotal\",\"op\":\"gte\",\"value\":\"100.00\"},"
                + "\"target\":{\"all\":[{\"field\":\"product.category\",\"op\":\"eq\",\"value\":\" kitchen \"},"
                + "{\"field\":\"line.qty\",\"op\":\"gte\",\"value\":10}]},"
                + "\"action\":{\"kind\":\"PERCENT_OFF\",\"percent\":5}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].type").value("RULE"))
                .andExpect(jsonPath("$[0].kind").value("PRICING"))
                .andExpect(jsonPath("$[0].name").value("Bulk deal"))
                .andExpect(jsonPath("$[0].priority").value(3))
                .andExpect(jsonPath("$[0].stage").value(100))
                .andExpect(jsonPath("$[0].stopAfter").value(false))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].eligibleSegments", contains("VIP")))
                .andExpect(jsonPath("$[0].when.field").value("cart.subtotal"))
                .andExpect(jsonPath("$[0].when.op").value("gte"))
                .andExpect(jsonPath("$[0].target.all", hasSize(2)))
                .andExpect(jsonPath("$[0].target.all[0].value").value("kitchen"))
                .andExpect(jsonPath("$[0].action.kind").value("PERCENT_OFF"))
                .andExpect(jsonPath("$[0].action.percent").value(5))
                .andExpect(jsonPath("$[0].category").doesNotExist())
                .andExpect(jsonPath("$[0].productId").doesNotExist());
    }

    @Test
    void validationRulesDefaultToTheValidationStage() throws Exception {
        create("{\"type\":\"RULE\",\"kind\":\"VALIDATION\",\"name\":\"Minimum order\","
                + "\"when\":{\"field\":\"cart.total\",\"op\":\"lt\",\"value\":20},"
                + "\"action\":{\"kind\":\"REJECT\",\"message\":\"Minimum order is 20\"}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].kind").value("VALIDATION"))
                .andExpect(jsonPath("$[0].stage").value(900))
                .andExpect(jsonPath("$[0].action.message").value("Minimum order is 20"))
                .andExpect(jsonPath("$[0].target").doesNotExist());
    }

    @Test
    void ruleStagesAndStopFlagsCanBeSet() throws Exception {
        create(percentRule(",\"stage\":250,\"stopAfter\":true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].stage").value(250))
                .andExpect(jsonPath("$[0].stopAfter").value(true));
    }

    @Test
    void legacyShortcutsAreReturnedWithTheirRuleExpansion() throws Exception {
        api.percentOff("10% kitchen", "kitchen", "10", "");
        api.buyXGetY("Mugs 2+1", mug, 2, 1, "");
        api.createPromotion("{\"type\":\"TIERED_BULK\",\"name\":\"Bulk\",\"productId\":\"" + mug
                + "\",\"minQty\":5,\"percent\":10}");

        mockMvc.perform(get("/api/v1/promotions").param("sort", "name,asc"))
                .andExpect(jsonPath("$.content[0].name").value("10% kitchen"))
                .andExpect(jsonPath("$.content[0].kind").value("PRICING"))
                .andExpect(jsonPath("$.content[0].stage").value(100))
                .andExpect(jsonPath("$.content[0].target.field").value("product.category"))
                .andExpect(jsonPath("$.content[0].action.kind").value("PERCENT_OFF"))
                .andExpect(jsonPath("$.content[0].category").value("KITCHEN"))
                .andExpect(jsonPath("$.content[1].name").value("Bulk"))
                .andExpect(jsonPath("$.content[1].stage").value(300))
                .andExpect(jsonPath("$.content[1].target.all", hasSize(2)))
                .andExpect(jsonPath("$.content[1].minQty").value(5))
                .andExpect(jsonPath("$.content[2].name").value("Mugs 2+1"))
                .andExpect(jsonPath("$.content[2].stage").value(200))
                .andExpect(jsonPath("$.content[2].action.kind").value("FREE_UNITS"))
                .andExpect(jsonPath("$.content[2].action.buyQty").value(2));
    }

    @Test
    void invalidRulesReportEveryProblemWithItsFieldPath() throws Exception {
        create("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Broken\","
                + "\"when\":{\"field\":\"product.category\",\"op\":\"eq\",\"value\":\"x\"},"
                + "\"target\":{\"all\":[{\"field\":\"product.category\",\"op\":\"gt\",\"value\":\"x\"},"
                + "{\"field\":\"line.qty\",\"op\":\"eq\",\"value\":\"many\"}]},"
                + "\"action\":{\"kind\":\"PERCENT_OFF\",\"percent\":150},\"stage\":950}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("[0].when.field",
                        "[0].target.all[0].op", "[0].target.all[1].value", "[0].action.percent", "[0].stage")));
    }

    @Test
    void anActionOfTheWrongKindForTheRuleKindIsRejected() throws Exception {
        create("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Wrong\","
                + "\"action\":{\"kind\":\"REJECT\",\"message\":\"no\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("[0].action.kind"));
        create("{\"type\":\"RULE\",\"kind\":\"VALIDATION\",\"name\":\"Wrong\","
                + "\"action\":{\"kind\":\"PERCENT_OFF\",\"percent\":5}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("[0].action.kind"));
    }

    @Test
    void pricingStagesBelongBelowTheValidationRange() throws Exception {
        create(percentRule(",\"stage\":900")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("[0].stage"));
        create("{\"type\":\"RULE\",\"kind\":\"VALIDATION\",\"name\":\"V\",\"stage\":500,"
                + "\"action\":{\"kind\":\"REJECT\",\"message\":\"no\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("[0].stage"));
    }

    @Test
    void missingNameAndKindAreReportedByBeanValidation() throws Exception {
        create("{\"type\":\"RULE\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("[0].kind", "[0].name")));
    }

    @Test
    void aMissingActionIsReported() throws Exception {
        create("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"No action\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("[0].action"));
    }

    @Test
    void unknownRuleLevelPropertiesAreRejected() throws Exception {
        create(percentRule(",\"discount\":5")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void aRuleThatReferencesAnUnknownProductIsUnprocessable() throws Exception {
        String ghost = UUID.randomUUID().toString();

        create("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Ghost target\","
                + "\"target\":{\"field\":\"product.id\",\"op\":\"in\",\"value\":[\"" + mug + "\",\"" + ghost + "\"]},"
                + "\"action\":{\"kind\":\"PERCENT_OFF\",\"percent\":5}}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PRODUCT"))
                .andExpect(jsonPath("$.errors[0].field").value("[0].target.value[1]"));
        create("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Ghost gift\","
                + "\"action\":{\"kind\":\"FREE_GIFT\",\"productId\":\"" + ghost + "\",\"qty\":1}}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("[0].action.productId"));
    }

    @Test
    void aBatchWithOneInvalidRuleSavesNothing() throws Exception {
        create(percentRule(""), "{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Bad\","
                + "\"action\":{\"kind\":\"PERCENT_OFF\",\"percent\":0}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("[1].action.percent"));

        assertThat(promotionRepository.count()).isZero();
    }

    @Test
    void rulesCanBeMixedWithLegacyShortcutsInOneBatch() throws Exception {
        create(percentRule(""), "{\"type\":\"PERCENT_OFF_CATEGORY\",\"name\":\"Legacy\",\"category\":\"home\","
                + "\"percent\":5}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("RULE"))
                .andExpect(jsonPath("$[1].type").value("PERCENT_OFF_CATEGORY"));
    }

    @Test
    void listFiltersByKindAndByTheRuleType() throws Exception {
        create(percentRule(""), "{\"type\":\"RULE\",\"kind\":\"VALIDATION\",\"name\":\"Min\","
                + "\"action\":{\"kind\":\"REJECT\",\"message\":\"no\"}}").andExpect(status().isCreated());
        api.percentOff("Legacy", "home", "5", "");

        mockMvc.perform(get("/api/v1/promotions").param("kind", "VALIDATION"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Min"));
        mockMvc.perform(get("/api/v1/promotions").param("kind", "PRICING"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/promotions").param("type", "RULE"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/promotions").param("kind", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void aStoredRuleCanBeFetchedPatchedAndDeleted() throws Exception {
        String id = idOf(create(percentRule("")).andExpect(status().isCreated()));

        mockMvc.perform(get("/api/v1/promotions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.field").value("product.category"))
                .andExpect(jsonPath("$.action.percent").value(10));
        mockMvc.perform(patch("/api/v1/promotions/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"active\":false,\"priority\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.priority").value(9))
                .andExpect(jsonPath("$.action.percent").value(10));
        mockMvc.perform(patch("/api/v1/promotions/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":{\"kind\":\"PERCENT_OFF\",\"percent\":50}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(delete("/api/v1/promotions/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/promotions/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void aStoredRuleAppliesToQuotes() throws Exception {
        create("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Kitchen deal\","
                + "\"when\":{\"field\":\"cart.subtotal\",\"op\":\"gte\",\"value\":30},"
                + "\"target\":{\"field\":\"product.category\",\"op\":\"eq\",\"value\":\"kitchen\"},"
                + "\"action\":{\"kind\":\"PERCENT_OFF\",\"percent\":10}}").andExpect(status().isCreated());

        quote(item(mug, 3), "REGULAR")
                .andExpect(jsonPath("$.total").value("32.40"))
                .andExpect(jsonPath("$.appliedPromotions[0].type").value("RULE"))
                .andExpect(jsonPath("$.appliedPromotions[0].name").value("Kitchen deal"))
                .andExpect(jsonPath("$.appliedPromotions[0].amount").value("3.60"));
        quote(item(mug, 1), "REGULAR")
                .andExpect(jsonPath("$.total").value("12.00"))
                .andExpect(jsonPath("$.appliedPromotions", hasSize(0)));
    }

    @Test
    void aStoredRuleRespectsItsSegmentAndDateWindow() throws Exception {
        create(percentRule(",\"eligibleSegments\":[\"VIP\"]")).andExpect(status().isCreated());
        create("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Expired\","
                + "\"when\":{\"field\":\"date\",\"op\":\"lt\",\"value\":\"2000-01-01\"},"
                + "\"target\":{\"field\":\"product.id\",\"op\":\"eq\",\"value\":\"" + lamp + "\"},"
                + "\"action\":{\"kind\":\"PERCENT_OFF\",\"percent\":50}}").andExpect(status().isCreated());

        quote(item(mug, 1) + "," + item(lamp, 1), "REGULAR").andExpect(jsonPath("$.total").value("42.00"));
        quote(item(mug, 1) + "," + item(lamp, 1), "VIP").andExpect(jsonPath("$.total").value("40.80"));
    }

    @Test
    void legacyAndGenericRulesStackInStageOrder() throws Exception {
        api.buyXGetY("Mugs 2+1", mug, 2, 1, "");
        create(percentRule("")).andExpect(status().isCreated());

        quote(item(mug, 3), "REGULAR")
                .andExpect(jsonPath("$.total").value("21.60"))
                .andExpect(jsonPath("$.appliedPromotions[0].name").value("Kitchen deal"))
                .andExpect(jsonPath("$.appliedPromotions[1].name").value("Mugs 2+1"));
    }
}
