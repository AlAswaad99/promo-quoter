package com.promoquoter.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.promoquoter.support.ApiFixtures;
import com.promoquoter.support.IntegrationTest;
import com.promoquoter.support.TestDatabase;

@IntegrationTest
class RuleQuoteIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApiFixtures api;

    @Autowired
    TestDatabase database;

    String mug;
    String lamp;
    String gift;

    @BeforeEach
    void setUp() throws Exception {
        database.clean();
        mug = api.createProduct("Mug", "kitchen", "12.00", 10);
        lamp = api.createProduct("Lamp", "home", "30.00", 5);
        gift = api.createProduct("Gift Card", "gifts", "5.00", 2);
    }

    private ResultActions quote(String items) throws Exception {
        return mockMvc.perform(post("/api/v1/cart/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[" + items + "]}"));
    }

    private ResultActions confirm(String items) throws Exception {
        return mockMvc.perform(post("/api/v1/cart/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[" + items + "]}"));
    }

    private String item(String productId, int qty) {
        return "{\"productId\":\"" + productId + "\",\"qty\":" + qty + "}";
    }

    private void rule(String json) throws Exception {
        api.createPromotion(json);
    }

    private static String pricing(String name, String extra, String action) {
        return "{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"" + name + "\"" + extra + ",\"action\":" + action + "}";
    }

    private static String reject(String name, String when, String message) {
        return "{\"type\":\"RULE\",\"kind\":\"VALIDATION\",\"name\":\"" + name + "\",\"when\":" + when
                + ",\"action\":{\"kind\":\"REJECT\",\"message\":\"" + message + "\"}}";
    }

    private static String totalBelow(int amount) {
        return "{\"field\":\"cart.total\",\"op\":\"lt\",\"value\":" + amount + "}";
    }

    @Test
    void quotesWithoutRulesAreUnchangedAndCarryEmptyExtras() throws Exception {
        quote(item(mug, 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("36.00"))
                .andExpect(jsonPath("$.shipping").value("0.00"))
                .andExpect(jsonPath("$.adjustments", hasSize(0)))
                .andExpect(jsonPath("$.gifts", hasSize(0)))
                .andExpect(jsonPath("$.skipped", hasSize(0)));
    }

    @Test
    void aCartAmountDiscountIsAnAdjustmentNotALineDiscount() throws Exception {
        rule(pricing("Voucher", ",\"when\":{\"field\":\"cart.subtotal\",\"op\":\"gte\",\"value\":30}",
                "{\"kind\":\"AMOUNT_OFF_CART\",\"amount\":5}"));

        quote(item(mug, 3))
                .andExpect(jsonPath("$.subtotal").value("36.00"))
                .andExpect(jsonPath("$.totalDiscount").value("5.00"))
                .andExpect(jsonPath("$.total").value("31.00"))
                .andExpect(jsonPath("$.lines[0].discount").value("0.00"))
                .andExpect(jsonPath("$.adjustments", hasSize(1)))
                .andExpect(jsonPath("$.adjustments[0].name").value("Voucher"))
                .andExpect(jsonPath("$.adjustments[0].action").value("AMOUNT_OFF_CART"))
                .andExpect(jsonPath("$.adjustments[0].direction").value("DISCOUNT"))
                .andExpect(jsonPath("$.adjustments[0].amount").value("5.00"))
                .andExpect(jsonPath("$.appliedPromotions", hasSize(0)));
        quote(item(mug, 1)).andExpect(jsonPath("$.adjustments", hasSize(0)));
    }

    @Test
    void aCartDiscountNeverTakesMoreThanTheCartIsWorth() throws Exception {
        rule(pricing("Huge voucher", "", "{\"kind\":\"AMOUNT_OFF_CART\",\"amount\":1000}"));

        quote(item(mug, 1))
                .andExpect(jsonPath("$.total").value("0.00"))
                .andExpect(jsonPath("$.adjustments[0].amount").value("12.00"));
    }

    @Test
    void aSurchargeIsAddedAsAChargeAndKeepsTheTotalsReconciled() throws Exception {
        rule(pricing("Handling fee", "", "{\"kind\":\"SURCHARGE\",\"amount\":2.50}"));
        rule(pricing("Kitchen deal", ",\"target\":{\"field\":\"product.category\",\"op\":\"eq\",\"value\":\"kitchen\"}",
                "{\"kind\":\"PERCENT_OFF\",\"percent\":10}"));

        quote(item(mug, 3))
                .andExpect(jsonPath("$.subtotal").value("36.00"))
                .andExpect(jsonPath("$.totalDiscount").value("3.60"))
                .andExpect(jsonPath("$.total").value("34.90"))
                .andExpect(jsonPath("$.adjustments[0].direction").value("CHARGE"))
                .andExpect(jsonPath("$.adjustments[0].amount").value("2.50"))
                .andExpect(jsonPath("$.adjustments[0].sequence").value(2))
                .andExpect(jsonPath("$.appliedPromotions[0].sequence").value(1));
    }

    @Test
    void aCartRuleWithATargetNeedsAMatchingLine() throws Exception {
        rule(pricing("Lamp fee", ",\"target\":{\"field\":\"product.id\",\"op\":\"eq\",\"value\":\"" + lamp + "\"}",
                "{\"kind\":\"SURCHARGE\",\"amount\":1}"));

        quote(item(mug, 1)).andExpect(jsonPath("$.adjustments", hasSize(0)));
        quote(item(mug, 1) + "," + item(lamp, 1)).andExpect(jsonPath("$.adjustments", hasSize(1)));
    }

    @Test
    void aFreeGiftIsListedAsAZeroPricedLineAndDoesNotChangeTheTotal() throws Exception {
        rule(pricing("Gift with mugs", ",\"target\":{\"field\":\"product.id\",\"op\":\"eq\",\"value\":\"" + mug + "\"}",
                "{\"kind\":\"FREE_GIFT\",\"productId\":\"" + gift + "\",\"qty\":1}"));

        quote(item(mug, 2))
                .andExpect(jsonPath("$.total").value("24.00"))
                .andExpect(jsonPath("$.gifts", hasSize(1)))
                .andExpect(jsonPath("$.gifts[0].productId").value(gift))
                .andExpect(jsonPath("$.gifts[0].name").value("Gift Card"))
                .andExpect(jsonPath("$.gifts[0].qty").value(1))
                .andExpect(jsonPath("$.gifts[0].unitPrice").value("0.00"))
                .andExpect(jsonPath("$.gifts[0].total").value("0.00"))
                .andExpect(jsonPath("$.gifts[0].promotionName").value("Gift with mugs"))
                .andExpect(jsonPath("$.lines", hasSize(1)));
    }

    @Test
    void aGiftThatIsOutOfStockIsSkippedWithAReason() throws Exception {
        rule(pricing("Big gift", "", "{\"kind\":\"FREE_GIFT\",\"productId\":\"" + gift + "\",\"qty\":3}"));

        quote(item(mug, 1))
                .andExpect(jsonPath("$.gifts", hasSize(0)))
                .andExpect(jsonPath("$.skipped", hasSize(1)))
                .andExpect(jsonPath("$.skipped[0].name").value("Big gift"))
                .andExpect(jsonPath("$.skipped[0].reason", containsString("not enough stock")));
    }

    @Test
    void theGiftStockAlsoHasToCoverWhatTheCartItselfBuys() throws Exception {
        rule(pricing("Gift", "", "{\"kind\":\"FREE_GIFT\",\"productId\":\"" + gift + "\",\"qty\":1}"));

        quote(item(gift, 1)).andExpect(jsonPath("$.gifts", hasSize(1)));
        quote(item(gift, 2)).andExpect(jsonPath("$.gifts", hasSize(0))).andExpect(jsonPath("$.skipped", hasSize(1)));
    }

    @Test
    void severalGiftRulesForOneProductShareItsStock() throws Exception {
        rule(pricing("Gift A", "", "{\"kind\":\"FREE_GIFT\",\"productId\":\"" + gift + "\",\"qty\":2}"));
        rule(pricing("Gift B", "", "{\"kind\":\"FREE_GIFT\",\"productId\":\"" + gift + "\",\"qty\":1}"));

        quote(item(mug, 1))
                .andExpect(jsonPath("$.gifts", hasSize(1)))
                .andExpect(jsonPath("$.skipped", hasSize(1)));
    }

    @Test
    void aMatchingValidationRuleRejectsTheQuoteWithEveryViolation() throws Exception {
        rule(reject("Minimum order", totalBelow(100), "Minimum order is 100"));
        rule(reject("Minimum for orders", totalBelow(50), "Orders under 50 are not accepted"));
        rule(reject("Not applicable", totalBelow(5), "Never shown"));

        quote(item(mug, 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RULE_VIOLATION"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].message", containsInAnyOrder("Minimum order is 100",
                        "Orders under 50 are not accepted")))
                .andExpect(jsonPath("$.errors[*].name", containsInAnyOrder("Minimum order", "Minimum for orders")))
                .andExpect(jsonPath("$.errors[0].promotionId").isNotEmpty());
    }

    @Test
    void aValidationRuleThatDoesNotMatchLeavesTheQuoteAlone() throws Exception {
        rule(reject("Minimum order", totalBelow(10), "Minimum order is 10"));

        quote(item(mug, 1)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value("12.00"));
    }

    @Test
    void validationRulesSeeTheDiscountedTotal() throws Exception {
        rule(pricing("Half off", "", "{\"kind\":\"PERCENT_OFF\",\"percent\":50}"));
        rule(reject("Minimum order", totalBelow(15), "Minimum order is 15"));

        quote(item(mug, 3)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value("18.00"))
                .andExpect(jsonPath("$.skipped", hasSize(0)));
        quote(item(mug, 1)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void validationRulesCanLimitQuantitiesPerProduct() throws Exception {
        rule("{\"type\":\"RULE\",\"kind\":\"VALIDATION\",\"name\":\"Mug limit\","
                + "\"target\":{\"all\":[{\"field\":\"product.id\",\"op\":\"eq\",\"value\":\"" + mug + "\"},"
                + "{\"field\":\"line.qty\",\"op\":\"gt\",\"value\":5}]},"
                + "\"action\":{\"kind\":\"REJECT\",\"message\":\"At most 5 mugs per order\"}}");

        quote(item(mug, 5)).andExpect(status().isOk());
        quote(item(mug, 6)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].message").value("At most 5 mugs per order"));
    }

    @Test
    void confirmIsRejectedByTheSameRulesAndReservesNothing() throws Exception {
        rule(reject("Minimum order", totalBelow(100), "Minimum order is 100"));

        confirm(item(mug, 2))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RULE_VIOLATION"));

        assertThat(database.stockOf(mug)).isEqualTo(10);
        assertThat(database.count("orders")).isZero();
    }

    @Test
    void stopAfterOnALineRuleKeepsLaterRulesOffThoseLines() throws Exception {
        rule(pricing("Exclusive half", ",\"stopAfter\":true,\"target\":{\"field\":\"product.id\",\"op\":\"eq\","
                + "\"value\":\"" + mug + "\"}", "{\"kind\":\"PERCENT_OFF\",\"percent\":50}"));
        rule(pricing("Everything 10", ",\"stage\":150", "{\"kind\":\"PERCENT_OFF\",\"percent\":10}"));

        quote(item(mug, 1) + "," + item(lamp, 1))
                .andExpect(jsonPath("$.lines[?(@.name=='Mug')].total", contains("6.00")))
                .andExpect(jsonPath("$.lines[?(@.name=='Lamp')].total", contains("27.00")));
    }

    @Test
    void stopAfterOnACartRuleSkipsLaterPricingRulesAndSaysSo() throws Exception {
        rule(pricing("Exclusive voucher", ",\"stopAfter\":true", "{\"kind\":\"AMOUNT_OFF_CART\",\"amount\":5}"));
        rule(pricing("Handling fee", "", "{\"kind\":\"SURCHARGE\",\"amount\":2}"));

        quote(item(mug, 3))
                .andExpect(jsonPath("$.total").value("31.00"))
                .andExpect(jsonPath("$.skipped", hasSize(1)))
                .andExpect(jsonPath("$.skipped[0].name").value("Handling fee"))
                .andExpect(jsonPath("$.skipped[0].reason", containsString("Exclusive voucher")));
    }
}
