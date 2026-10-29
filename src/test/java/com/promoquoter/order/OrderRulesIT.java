package com.promoquoter.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.promoquoter.support.ApiFixtures;
import com.promoquoter.support.IntegrationTest;
import com.promoquoter.support.TestDatabase;

@IntegrationTest
class OrderRulesIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApiFixtures api;

    @Autowired
    TestDatabase database;

    String mug;
    String gift;

    @BeforeEach
    void setUp() throws Exception {
        database.clean();
        mug = api.createProduct("Mug", "kitchen", "12.00", 10);
        gift = api.createProduct("Gift Card", "gifts", "5.00", 3);
    }

    private ResultActions confirm(String items, String key) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/cart/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[" + items + "]}");
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private String item(String productId, int qty) {
        return "{\"productId\":\"" + productId + "\",\"qty\":" + qty + "}";
    }

    private String giftRule(int qty) throws Exception {
        return api.createPromotion("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Gift with mugs\","
                + "\"target\":{\"field\":\"product.id\",\"op\":\"eq\",\"value\":\"" + mug + "\"},"
                + "\"action\":{\"kind\":\"FREE_GIFT\",\"productId\":\"" + gift + "\",\"qty\":" + qty + "}}");
    }

    private String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.orderId");
    }

    @Test
    void confirmReservesTheGiftStockAndStoresTheGiftLine() throws Exception {
        giftRule(1);

        confirm(item(mug, 2), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value("24.00"))
                .andExpect(jsonPath("$.gifts", hasSize(1)))
                .andExpect(jsonPath("$.gifts[0].productId").value(gift))
                .andExpect(jsonPath("$.gifts[0].qty").value(1))
                .andExpect(jsonPath("$.gifts[0].unitPrice").value("0.00"))
                .andExpect(jsonPath("$.notes", hasSize(0)));

        assertThat(database.stockOf(mug)).isEqualTo(8);
        assertThat(database.stockOf(gift)).isEqualTo(2);
    }

    @Test
    void theGiftLineCanBeReadBackFromTheStoredOrder() throws Exception {
        giftRule(2);
        String orderId = idOf(confirm(item(mug, 1), null).andExpect(status().isCreated()));

        mockMvc.perform(get("/api/v1/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gifts[0].name").value("Gift Card"))
                .andExpect(jsonPath("$.gifts[0].qty").value(2))
                .andExpect(jsonPath("$.gifts[0].promotionName").value("Gift with mugs"));
    }

    @Test
    void aGiftThatRanOutIsSkippedWithANoteAndTheOrderStillSucceeds() throws Exception {
        giftRule(5);

        confirm(item(mug, 1), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gifts", hasSize(0)))
                .andExpect(jsonPath("$.notes", hasSize(1)))
                .andExpect(jsonPath("$.notes[0]", containsString("Gift with mugs")))
                .andExpect(jsonPath("$.notes[0]", containsString("not enough stock")));

        assertThat(database.stockOf(gift)).isEqualTo(3);
        assertThat(database.stockOf(mug)).isEqualTo(9);
    }

    @Test
    void theCartAndTheGiftShareTheStockOfOneProduct() throws Exception {
        api.createPromotion("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Gift\","
                + "\"action\":{\"kind\":\"FREE_GIFT\",\"productId\":\"" + gift + "\",\"qty\":1}}");

        confirm(item(gift, 2), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gifts", hasSize(1)));
        assertThat(database.stockOf(gift)).isZero();

        confirm(item(gift, 1), null).andExpect(status().isConflict());
        assertThat(database.stockOf(gift)).isZero();
    }

    @Test
    void aShortageOnTheCartLeavesTheGiftStockUntouched() throws Exception {
        giftRule(1);

        confirm(item(mug, 50), null).andExpect(status().isConflict());

        assertThat(database.stockOf(gift)).isEqualTo(3);
        assertThat(database.stockOf(mug)).isEqualTo(10);
        assertThat(database.count("orders")).isZero();
    }

    @Test
    void cartAdjustmentsAndShippingAreStoredWithTheOrder() throws Exception {
        api.createPromotion("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Voucher\","
                + "\"action\":{\"kind\":\"AMOUNT_OFF_CART\",\"amount\":5}}");
        api.createPromotion("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Handling\","
                + "\"action\":{\"kind\":\"SURCHARGE\",\"amount\":2.50}}");

        String orderId = idOf(confirm(item(mug, 3), null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value("33.50")));

        mockMvc.perform(get("/api/v1/orders/" + orderId))
                .andExpect(jsonPath("$.total").value("33.50"))
                .andExpect(jsonPath("$.subtotal").value("36.00"))
                .andExpect(jsonPath("$.totalDiscount").value("5.00"))
                .andExpect(jsonPath("$.shipping").value("0.00"))
                .andExpect(jsonPath("$.adjustments", hasSize(2)))
                .andExpect(jsonPath("$.adjustments[0].name").value("Voucher"))
                .andExpect(jsonPath("$.adjustments[0].direction").value("DISCOUNT"))
                .andExpect(jsonPath("$.adjustments[1].name").value("Handling"))
                .andExpect(jsonPath("$.adjustments[1].direction").value("CHARGE"));
    }

    @Test
    void aReplayReturnsTheSameGiftsAndReservesOnce() throws Exception {
        giftRule(1);

        confirm(item(mug, 2), "gift-key").andExpect(status().isCreated());
        confirm(item(mug, 2), "gift-key")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.gifts", hasSize(1)));

        assertThat(database.stockOf(gift)).isEqualTo(2);
        assertThat(database.stockOf(mug)).isEqualTo(8);
    }

    @Test
    void deletingTheRuleKeepsTheStoredGiftAndAdjustmentHistory() throws Exception {
        String ruleId = giftRule(1);
        String orderId = idOf(confirm(item(mug, 1), null).andExpect(status().isCreated()));

        mockMvc.perform(delete("/api/v1/promotions/" + ruleId)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/orders/" + orderId))
                .andExpect(jsonPath("$.gifts[0].promotionId").value(ruleId))
                .andExpect(jsonPath("$.gifts[0].promotionName").value("Gift with mugs"));
    }

    @Test
    void ordersWithoutRulesCarryEmptyExtras() throws Exception {
        confirm(item(mug, 1), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shipping").value("0.00"))
                .andExpect(jsonPath("$.adjustments", hasSize(0)))
                .andExpect(jsonPath("$.gifts", hasSize(0)))
                .andExpect(jsonPath("$.notes", hasSize(0)));
    }
}
