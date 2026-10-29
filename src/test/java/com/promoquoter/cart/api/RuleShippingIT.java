package com.promoquoter.cart.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.promoquoter.support.ApiFixtures;
import com.promoquoter.support.IntegrationTest;
import com.promoquoter.support.TestDatabase;

@IntegrationTest
@TestPropertySource(properties = "promoquoter.shipping.flat-fee=5.00")
class RuleShippingIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApiFixtures api;

    @Autowired
    TestDatabase database;

    String mug;

    @BeforeEach
    void setUp() throws Exception {
        database.clean();
        mug = api.createProduct("Mug", "kitchen", "12.00", 100);
    }

    private ResultActions quote(int qty) throws Exception {
        return mockMvc.perform(post("/api/v1/cart/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":\"" + mug + "\",\"qty\":" + qty + "}]}"));
    }

    private void waiverWhenSubtotalAtLeast(int amount) throws Exception {
        api.createPromotion("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Free shipping\","
                + "\"when\":{\"field\":\"cart.subtotal\",\"op\":\"gte\",\"value\":" + amount + "},"
                + "\"action\":{\"kind\":\"SHIPPING_WAIVER\"}}");
    }

    @Test
    void theConfiguredFlatFeeIsShownAndAddedToTheTotal() throws Exception {
        quote(3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipping").value("5.00"))
                .andExpect(jsonPath("$.subtotal").value("36.00"))
                .andExpect(jsonPath("$.total").value("41.00"))
                .andExpect(jsonPath("$.adjustments", hasSize(0)));
    }

    @Test
    void aShippingWaiverRemovesTheFeeAndRecordsWhatWasWaived() throws Exception {
        waiverWhenSubtotalAtLeast(100);

        quote(10)
                .andExpect(jsonPath("$.shipping").value("0.00"))
                .andExpect(jsonPath("$.total").value("120.00"))
                .andExpect(jsonPath("$.adjustments", hasSize(1)))
                .andExpect(jsonPath("$.adjustments[0].action").value("SHIPPING_WAIVER"))
                .andExpect(jsonPath("$.adjustments[0].direction").value("DISCOUNT"))
                .andExpect(jsonPath("$.adjustments[0].amount").value("5.00"));
    }

    @Test
    void aShippingWaiverThatDoesNotMatchLeavesTheFee() throws Exception {
        waiverWhenSubtotalAtLeast(100);

        quote(3).andExpect(jsonPath("$.shipping").value("5.00")).andExpect(jsonPath("$.total").value("41.00"));
    }

    @Test
    void confirmChargesTheSameTotalAsTheQuote() throws Exception {
        waiverWhenSubtotalAtLeast(100);

        mockMvc.perform(post("/api/v1/cart/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + mug + "\",\"qty\":3}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value("41.00"));
    }
}
