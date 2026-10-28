package com.promoquoter.cart;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.IntStream;

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
class CartQuoteIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApiFixtures api;

    @Autowired
    TestDatabase testDatabase;

    String mug;
    String lamp;

    @BeforeEach
    void setUp() throws Exception {
        testDatabase.clean();
        mug = api.createProduct("Mug", "kitchen", "12.00", 10);
        lamp = api.createProduct("Lamp", "home", "30.00", 5);
    }

    private ResultActions quote(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/cart/quote").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String cart(String segment, String... items) {
        return "{\"items\":[" + String.join(",", items) + "]"
                + (segment == null ? "" : ",\"customerSegment\":\"" + segment + "\"") + "}";
    }

    private String item(String productId, int qty) {
        return "{\"productId\":\"" + productId + "\",\"qty\":" + qty + "}";
    }

    @Test
    void quotesTheWorkedExampleWithPromotionsInApplicationOrder() throws Exception {
        String buyXGetY = api.buyXGetY("Mugs 2+1", mug, 2, 1, "");
        String percent = api.percentOff("10% kitchen", "kitchen", "10", "");

        quote(cart("REGULAR", item(mug, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerSegment").value("REGULAR"))
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].productId").value(mug))
                .andExpect(jsonPath("$.lines[0].name").value("Mug"))
                .andExpect(jsonPath("$.lines[0].category").value("KITCHEN"))
                .andExpect(jsonPath("$.lines[0].unitPrice").value("12.00"))
                .andExpect(jsonPath("$.lines[0].qty").value(3))
                .andExpect(jsonPath("$.lines[0].subtotal").value("36.00"))
                .andExpect(jsonPath("$.lines[0].discount").value("14.40"))
                .andExpect(jsonPath("$.lines[0].total").value("21.60"))
                .andExpect(jsonPath("$.appliedPromotions", hasSize(2)))
                .andExpect(jsonPath("$.appliedPromotions[0].sequence").value(1))
                .andExpect(jsonPath("$.appliedPromotions[0].promotionId").value(percent))
                .andExpect(jsonPath("$.appliedPromotions[0].name").value("10% kitchen"))
                .andExpect(jsonPath("$.appliedPromotions[0].type").value("PERCENT_OFF_CATEGORY"))
                .andExpect(jsonPath("$.appliedPromotions[0].productId").value(mug))
                .andExpect(jsonPath("$.appliedPromotions[0].amount").value("3.60"))
                .andExpect(jsonPath("$.appliedPromotions[1].sequence").value(2))
                .andExpect(jsonPath("$.appliedPromotions[1].promotionId").value(buyXGetY))
                .andExpect(jsonPath("$.appliedPromotions[1].type").value("BUY_X_GET_Y"))
                .andExpect(jsonPath("$.appliedPromotions[1].amount").value("10.80"))
                .andExpect(jsonPath("$.subtotal").value("36.00"))
                .andExpect(jsonPath("$.totalDiscount").value("14.40"))
                .andExpect(jsonPath("$.total").value("21.60"));
    }

    @Test
    void quotesWithoutPromotions() throws Exception {
        quote(cart(null, item(mug, 2), item(lamp, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.appliedPromotions", hasSize(0)))
                .andExpect(jsonPath("$.subtotal").value("54.00"))
                .andExpect(jsonPath("$.totalDiscount").value("0.00"))
                .andExpect(jsonPath("$.total").value("54.00"));
    }

    @Test
    void defaultsToTheRegularSegment() throws Exception {
        quote(cart(null, item(mug, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerSegment").value("REGULAR"));
    }

    @Test
    void mergesDuplicateLines() throws Exception {
        api.buyXGetY("Mugs 2+1", mug, 2, 1, "");

        quote(cart(null, item(mug, 1), item(mug, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].qty").value(3))
                .andExpect(jsonPath("$.total").value("24.00"));
    }

    @Test
    void appliesSegmentRestrictedPromotionsOnlyToThatSegment() throws Exception {
        api.percentOff("VIP only", "kitchen", "50", ",\"eligibleSegments\":[\"VIP\"]");

        quote(cart("REGULAR", item(mug, 1)))
                .andExpect(jsonPath("$.total").value("12.00"))
                .andExpect(jsonPath("$.appliedPromotions", hasSize(0)));
        quote(cart("VIP", item(mug, 1)))
                .andExpect(jsonPath("$.customerSegment").value("VIP"))
                .andExpect(jsonPath("$.total").value("6.00"))
                .andExpect(jsonPath("$.appliedPromotions", hasSize(1)));
    }

    @Test
    void appliesTieredBulkOnlyAtTheThreshold() throws Exception {
        api.createPromotion("{\"type\":\"TIERED_BULK\",\"name\":\"Bulk\",\"productId\":\"" + mug
                + "\",\"minQty\":5,\"percent\":10}");

        quote(cart(null, item(mug, 4))).andExpect(jsonPath("$.total").value("48.00"));
        quote(cart(null, item(mug, 5)))
                .andExpect(jsonPath("$.total").value("54.00"))
                .andExpect(jsonPath("$.appliedPromotions[0].type").value("TIERED_BULK"))
                .andExpect(jsonPath("$.appliedPromotions[0].amount").value("6.00"));
    }

    @Test
    void ignoresInactivePromotions() throws Exception {
        api.percentOff("Off", "kitchen", "50", ",\"active\":false");

        quote(cart(null, item(mug, 1))).andExpect(jsonPath("$.total").value("12.00"));
    }

    @Test
    void doesNotApplyCategoryPromotionsToOtherCategories() throws Exception {
        api.percentOff("Home only", "home", "50", "");

        quote(cart(null, item(mug, 1))).andExpect(jsonPath("$.total").value("12.00"));
    }

    @Test
    void categoryMatchingIsCaseInsensitive() throws Exception {
        api.percentOff("Kitchen", " KiTcHeN ", "25", "");

        quote(cart(null, item(mug, 1))).andExpect(jsonPath("$.total").value("9.00"));
    }

    @Test
    void stageOrderIsIndependentOfCreationOrder() throws Exception {
        api.buyXGetY("Mugs 2+1", mug, 2, 1, "");
        api.percentOff("10% kitchen", "kitchen", "10", "");

        quote(cart(null, item(mug, 3)))
                .andExpect(jsonPath("$.appliedPromotions[*].type", contains("PERCENT_OFF_CATEGORY", "BUY_X_GET_Y")))
                .andExpect(jsonPath("$.total").value("21.60"));
    }

    @Test
    void priorityOrdersPromotionsInTheSameStage() throws Exception {
        api.percentOff("Second", "kitchen", "20", ",\"priority\":2");
        api.percentOff("First", "kitchen", "10", ",\"priority\":1");

        quote(cart(null, item(mug, 1)))
                .andExpect(jsonPath("$.appliedPromotions[*].name", contains("First", "Second")))
                .andExpect(jsonPath("$.total").value("8.64"));
    }

    @Test
    void doesNotCheckOrChangeStock() throws Exception {
        quote(cart(null, item(mug, 500))).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/products/" + mug)).andExpect(jsonPath("$.stock").value(10));
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        UUID missing = UUID.randomUUID();

        quote(cart(null, item(mug, 1), item(missing.toString(), 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].message").value("Product " + missing + " was not found"));
    }

    @Test
    void rejectsEmptyOrMissingItems() throws Exception {
        quote("{\"items\":[]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("items"));
        quote("{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items"));
    }

    @Test
    void rejectsInvalidQuantitiesAndMissingProductIds() throws Exception {
        quote(cart(null, item(mug, 0), item(mug, 1001), "{\"qty\":1}", "{\"productId\":\"" + mug + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field",
                        contains("items[0].qty", "items[1].qty", "items[2].productId", "items[3].qty")));
    }

    @Test
    void rejectsMergedQuantitiesAboveTheLimit() throws Exception {
        quote(cart(null, item(mug, 600), item(mug, 600)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsMoreThanOneHundredItems() throws Exception {
        String[] items = IntStream.range(0, 101).mapToObj(i -> item(UUID.randomUUID().toString(), 1))
                .toArray(String[]::new);

        quote(cart(null, items))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items"));
    }

    @Test
    void rejectsNullItemsInTheList() throws Exception {
        quote("{\"items\":[null]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsUnknownSegmentsAndMalformedBodies() throws Exception {
        quote(cart("GOLD", item(mug, 1))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        quote("not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        quote("{\"items\":[{\"productId\":\"abc\",\"qty\":1}]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void rejectsAMisspelledCartField() throws Exception {
        quote("{\"items\":[" + item(mug, 1) + "],\"customersegment\":\"VIP\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("customersegment"));
    }

    @Test
    void rejectsAnUnknownCartItemField() throws Exception {
        quote("{\"items\":[{\"productId\":\"" + mug + "\",\"quantity\":1}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
