package com.promoquoter.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.promoquoter.support.ApiFixtures;
import com.promoquoter.support.IntegrationTest;
import com.promoquoter.support.TestDatabase;

@IntegrationTest
class RulePreviewIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApiFixtures api;

    @Autowired
    TestDatabase testDatabase;

    @Autowired
    JdbcTemplate jdbcTemplate;

    String mug;

    @BeforeEach
    void setUp() throws Exception {
        testDatabase.clean();
        mug = api.createProduct("Mug", "kitchen", "12.00", 10);
    }

    private ResultActions preview(String rule, String cart) throws Exception {
        return mockMvc.perform(post("/api/v1/promotions/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rule\":" + rule + ",\"cart\":" + cart + "}"));
    }

    private String cartOf(int qty) {
        return "{\"items\":[{\"productId\":\"" + mug + "\",\"qty\":" + qty + "}]}";
    }

    private static String percentOffKitchen(String percent) {
        return "{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Preview deal\","
                + "\"target\":{\"field\":\"product.category\",\"op\":\"eq\",\"value\":\"kitchen\"},"
                + "\"action\":{\"kind\":\"PERCENT_OFF\",\"percent\":" + percent + "}}";
    }

    private int promotionCount() {
        return testDatabase.count("promotion");
    }

    @Test
    void pricesTheCartWithTheRuleAndEchoesTheNormalisedRule() throws Exception {
        preview(percentOffKitchen("10"), cartOf(3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rule.name").value("Preview deal"))
                .andExpect(jsonPath("$.rule.kind").value("PRICING"))
                .andExpect(jsonPath("$.rule.action.percent").value(10))
                .andExpect(jsonPath("$.quote.total").value("32.40"))
                .andExpect(jsonPath("$.quote.appliedPromotions", hasSize(1)))
                .andExpect(jsonPath("$.quote.appliedPromotions[0].name").value("Preview deal"))
                .andExpect(jsonPath("$.violations", hasSize(0)));
    }

    @Test
    void savesNothing() throws Exception {
        preview(percentOffKitchen("10"), cartOf(3)).andExpect(status().isOk());

        assertThat(promotionCount()).isZero();
    }

    @Test
    void stacksTheRuleOnTopOfTheActivePromotions() throws Exception {
        api.buyXGetY("Mugs 2+1", mug, 2, 1, "");

        preview(percentOffKitchen("10"), cartOf(3))
                .andExpect(jsonPath("$.quote.total").value("21.60"))
                .andExpect(jsonPath("$.quote.appliedPromotions[0].name").value("Preview deal"))
                .andExpect(jsonPath("$.quote.appliedPromotions[1].name").value("Mugs 2+1"));
        assertThat(promotionCount()).isEqualTo(1);
    }

    @Test
    void worksWithTheLegacyShortcutPayloads() throws Exception {
        preview("{\"type\":\"PERCENT_OFF_CATEGORY\",\"name\":\"Legacy\",\"category\":\"kitchen\",\"percent\":50}",
                cartOf(2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rule.type").value("PERCENT_OFF_CATEGORY"))
                .andExpect(jsonPath("$.quote.total").value("12.00"));
    }

    @Test
    void aRuleThatIsInactiveInThePreviewDoesNotApply() throws Exception {
        preview(percentOffKitchen("10").replace("\"name\"", "\"active\":false,\"name\""), cartOf(3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quote.total").value("36.00"));
    }

    @Test
    void aMatchingValidationRuleIsReportedWithoutFailingThePreview() throws Exception {
        preview("{\"type\":\"RULE\",\"kind\":\"VALIDATION\",\"name\":\"Minimum order\","
                + "\"when\":{\"field\":\"cart.total\",\"op\":\"lt\",\"value\":100},"
                + "\"action\":{\"kind\":\"REJECT\",\"message\":\"Minimum order is 100\"}}", cartOf(3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.violations", hasSize(1)))
                .andExpect(jsonPath("$.violations[0].name").value("Minimum order"))
                .andExpect(jsonPath("$.violations[0].message").value("Minimum order is 100"))
                .andExpect(jsonPath("$.quote.total").value("36.00"));
    }

    @Test
    void aValidationRuleThatDoesNotMatchReportsNothing() throws Exception {
        preview("{\"type\":\"RULE\",\"kind\":\"VALIDATION\",\"name\":\"Minimum order\","
                + "\"when\":{\"field\":\"cart.total\",\"op\":\"lt\",\"value\":10},"
                + "\"action\":{\"kind\":\"REJECT\",\"message\":\"Minimum order is 10\"}}", cartOf(3))
                .andExpect(jsonPath("$.violations", hasSize(0)));
    }

    @Test
    void aGiftRuleIsReflectedInTheStoredRuleFormButNotTheTotal() throws Exception {
        preview("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Gift\","
                + "\"action\":{\"kind\":\"FREE_GIFT\",\"productId\":\"" + mug + "\",\"qty\":1}}", cartOf(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rule.action.kind").value("FREE_GIFT"))
                .andExpect(jsonPath("$.quote.total").value("12.00"));
    }

    @Test
    void reportsRuleProblemsUnderTheRulePath() throws Exception {
        preview("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Broken\","
                + "\"target\":{\"field\":\"product.brand\",\"op\":\"eq\",\"value\":\"x\"},"
                + "\"action\":{\"kind\":\"PERCENT_OFF\",\"percent\":0}}", cartOf(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("rule.target.field", "rule.action.percent")));
    }

    @Test
    void reportsUnknownProductsInTheRuleAsUnprocessable() throws Exception {
        preview("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Ghost\","
                + "\"target\":{\"field\":\"product.id\",\"op\":\"eq\",\"value\":\"" + UUID.randomUUID() + "\"},"
                + "\"action\":{\"kind\":\"PERCENT_OFF\",\"percent\":5}}", cartOf(1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PRODUCT"))
                .andExpect(jsonPath("$.errors[0].field").value("rule.target.value"));
    }

    @Test
    void validatesTheCartLikeAQuote() throws Exception {
        preview(percentOffKitchen("10"), "{\"items\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("cart.items"));
        preview(percentOffKitchen("10"), "{\"items\":[{\"productId\":\"" + UUID.randomUUID() + "\",\"qty\":1}]}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void bothPartsAreRequired() throws Exception {
        mockMvc.perform(post("/api/v1/promotions/preview").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("rule", "cart")));
    }
}
