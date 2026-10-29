package com.promoquoter.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.promoquoter.support.ApiFixtures;
import com.promoquoter.support.IntegrationTest;
import com.promoquoter.support.TestDatabase;

@IntegrationTest
class CheckoutIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApiFixtures api;

    @Autowired
    TestDatabase database;

    String mug;
    String lamp;

    @BeforeEach
    void setUp() throws Exception {
        database.clean();
        mug = api.createProduct("Mug", "kitchen", "12.00", 10);
        lamp = api.createProduct("Lamp", "home", "30.00", 5);
    }

    private ResultActions confirm(String json, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/cart/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    private ResultActions confirm(String json) throws Exception {
        return confirm(json, null);
    }

    private String cart(String segment, String... items) {
        return "{\"items\":[" + String.join(",", items) + "]"
                + (segment == null ? "" : ",\"customerSegment\":\"" + segment + "\"") + "}";
    }

    private String item(String productId, int qty) {
        return "{\"productId\":\"" + productId + "\",\"qty\":" + qty + "}";
    }

    private String orderIdOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.orderId");
    }

    @Test
    void confirmsAnOrderReservesStockAndReturnsTheFinalPrice() throws Exception {
        api.percentOff("10% kitchen", "kitchen", "10", "");
        api.buyXGetY("Mugs 2+1", mug, 2, 1, "");

        confirm(cart("REGULAR", item(mug, 3), item(lamp, 1)))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.orderId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.customerSegment").value("REGULAR"))
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.appliedPromotions[*].type", contains("PERCENT_OFF_CATEGORY", "BUY_X_GET_Y")))
                .andExpect(jsonPath("$.subtotal").value("66.00"))
                .andExpect(jsonPath("$.totalDiscount").value("14.40"))
                .andExpect(jsonPath("$.total").value("51.60"));

        assertThat(database.stockOf(mug)).isEqualTo(7);
        assertThat(database.stockOf(lamp)).isEqualTo(4);
        assertThat(database.count("orders")).isEqualTo(1);
    }

    @Test
    void confirmedPriceMatchesTheQuote() throws Exception {
        api.percentOff("10% kitchen", "kitchen", "10", "");
        String body = cart("VIP", item(mug, 4));

        String quoted = mockMvc.perform(post("/api/v1/cart/quote").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn().getResponse().getContentAsString();
        String confirmed = confirm(body).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(confirmed, "$.total")).isEqualTo(JsonPath.read(quoted, "$.total"));
        assertThat((Object) JsonPath.read(confirmed, "$.lines")).isEqualTo(JsonPath.read(quoted, "$.lines"));
        assertThat((Object) JsonPath.read(confirmed, "$.appliedPromotions"))
                .isEqualTo(JsonPath.read(quoted, "$.appliedPromotions"));
    }

    @Test
    void exactlyTheRemainingStockCanBeBought() throws Exception {
        confirm(cart(null, item(lamp, 5))).andExpect(status().isCreated());

        assertThat(database.stockOf(lamp)).isZero();
        confirm(cart(null, item(lamp, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].available").value(0));
    }

    @Test
    void insufficientStockIsAConflictListingEveryShortProduct() throws Exception {
        confirm(cart(null, item(mug, 11), item(lamp, 6)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.title").value("Insufficient stock"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].productId", containsInAnyOrder(mug, lamp)))
                .andExpect(jsonPath("$.errors[*].requested", containsInAnyOrder(11, 6)))
                .andExpect(jsonPath("$.errors[*].available", containsInAnyOrder(10, 5)))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void aShortageReservesNothingForTheOtherProducts() throws Exception {
        confirm(cart(null, item(mug, 3), item(lamp, 6))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].productId").value(lamp));

        assertThat(database.stockOf(mug)).isEqualTo(10);
        assertThat(database.stockOf(lamp)).isEqualTo(5);
        assertThat(database.count("orders")).isZero();
    }

    @Test
    void unknownProductIsNotFoundAndNothingIsReserved() throws Exception {
        confirm(cart(null, item(mug, 1), item(UUID.randomUUID().toString(), 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        assertThat(database.stockOf(mug)).isEqualTo(10);
        assertThat(database.count("orders")).isZero();
    }

    @Test
    void invalidCartsAreRejectedBeforeAnythingIsReserved() throws Exception {
        confirm("{\"items\":[]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        confirm(cart(null, item(mug, 0))).andExpect(status().isBadRequest());
        confirm("garbage").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        assertThat(database.stockOf(mug)).isEqualTo(10);
    }

    @Test
    void duplicateLinesAreMergedBeforeReserving() throws Exception {
        confirm(cart(null, item(mug, 2), item(mug, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].qty").value(5));

        assertThat(database.stockOf(mug)).isEqualTo(5);
    }

    @Test
    void mergedDuplicatesAreCheckedAgainstStockAsOneLine() throws Exception {
        confirm(cart(null, item(lamp, 3), item(lamp, 3))).andExpect(status().isConflict());

        assertThat(database.stockOf(lamp)).isEqualTo(5);
    }

    @Test
    void withoutAKeyEveryConfirmCreatesANewOrder() throws Exception {
        String first = orderIdOf(confirm(cart(null, item(mug, 1))).andExpect(status().isCreated()));
        String second = orderIdOf(confirm(cart(null, item(mug, 1))).andExpect(status().isCreated()));

        assertThat(first).isNotEqualTo(second);
        assertThat(database.stockOf(mug)).isEqualTo(8);
        assertThat(database.count("idempotency_record")).isZero();
    }

    @Test
    void repeatingAConfirmWithTheSameKeyReplaysTheOriginalOrder() throws Exception {
        ResultActions first = confirm(cart(null, item(mug, 2)), "key-1")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
        String firstBody = first.andReturn().getResponse().getContentAsString();

        ResultActions second = confirm(cart(null, item(mug, 2)), "key-1")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        assertThat(second.andReturn().getResponse().getContentAsString()).isEqualTo(firstBody);
        assertThat(database.stockOf(mug)).isEqualTo(8);
        assertThat(database.count("orders")).isEqualTo(1);
        assertThat(database.count("idempotency_record")).isEqualTo(1);
    }

    @Test
    void equivalentCartsShareAnIdempotencyKey() throws Exception {
        String first = orderIdOf(confirm(cart(null, item(mug, 1), item(lamp, 1), item(mug, 1)), "key-eq"));

        ResultActions replay = confirm(cart("REGULAR", item(lamp, 1), item(mug, 2)), "key-eq")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        assertThat(orderIdOf(replay)).isEqualTo(first);
        assertThat(database.stockOf(mug)).isEqualTo(8);
    }

    @Test
    void reusingAKeyWithADifferentCartIsUnprocessable() throws Exception {
        confirm(cart(null, item(mug, 2)), "key-2").andExpect(status().isCreated());

        confirm(cart(null, item(mug, 3)), "key-2")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        confirm(cart(null, item(lamp, 2)), "key-2").andExpect(status().isUnprocessableEntity());

        assertThat(database.stockOf(mug)).isEqualTo(8);
        assertThat(database.stockOf(lamp)).isEqualTo(5);
    }

    @Test
    void reusingAKeyWithADifferentSegmentIsUnprocessable() throws Exception {
        confirm(cart("REGULAR", item(mug, 1)), "key-3").andExpect(status().isCreated());

        confirm(cart("VIP", item(mug, 1)), "key-3")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void differentKeysCreateDifferentOrders() throws Exception {
        String first = orderIdOf(confirm(cart(null, item(mug, 1)), "key-a"));
        String second = orderIdOf(confirm(cart(null, item(mug, 1)), "key-b"));

        assertThat(first).isNotEqualTo(second);
        assertThat(database.stockOf(mug)).isEqualTo(8);
    }

    @Test
    void aFailedConfirmDoesNotConsumeTheKey() throws Exception {
        database.setStock(lamp, 0);

        confirm(cart(null, item(lamp, 1)), "key-retry").andExpect(status().isConflict());
        assertThat(database.count("idempotency_record")).isZero();

        database.setStock(lamp, 3);
        confirm(cart(null, item(lamp, 1)), "key-retry")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
        assertThat(database.stockOf(lamp)).isEqualTo(2);
    }

    @Test
    void validatesTheIdempotencyKey() throws Exception {
        confirm(cart(null, item(mug, 1)), " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
        confirm(cart(null, item(mug, 1)), "k".repeat(101))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
        confirm(cart(null, item(mug, 1)), "k".repeat(100)).andExpect(status().isCreated());

        assertThat(database.stockOf(mug)).isEqualTo(9);
    }

    @Test
    void confirmRepricesFromCurrentPromotions() throws Exception {
        String before = JsonPath.read(confirm(cart(null, item(mug, 1))).andReturn().getResponse()
                .getContentAsString(), "$.total");
        api.percentOff("Half off", "kitchen", "50", "");
        String after = JsonPath.read(confirm(cart(null, item(mug, 1))).andReturn().getResponse()
                .getContentAsString(), "$.total");

        assertThat(before).isEqualTo("12.00");
        assertThat(after).isEqualTo("6.00");
    }

    @Test
    void aReplayKeepsTheOriginalPriceEvenAfterPromotionsChange() throws Exception {
        confirm(cart(null, item(mug, 1)), "key-price").andExpect(jsonPath("$.total").value("12.00"));
        api.percentOff("Half off", "kitchen", "50", "");

        confirm(cart(null, item(mug, 1)), "key-price")
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.total").value("12.00"));
    }

    @Test
    void largestAllowedPricesStillConfirmWithoutOverflowingTotals() throws Exception {
        String gold = api.createProduct("Gold", "x", "9999999999.99", 1000);

        confirm(cart(null, item(gold, 1000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value("9999999999990.00"));
    }

    @Test
    void rejectsAnUnknownCartItemField() throws Exception {
        confirm("{\"items\":[{\"productId\":\"" + mug + "\",\"quantity\":1}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void theOrderCanBeFetchedAfterwards() throws Exception {
        api.percentOff("10% kitchen", "kitchen", "10", "");
        ResultActions created = confirm(cart(null, item(mug, 2)));
        String id = orderIdOf(created);
        String createdBody = created.andReturn().getResponse().getContentAsString();

        String fetched = mockMvc.perform(get("/api/v1/orders/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((Object) JsonPath.read(fetched, "$")).isEqualTo(JsonPath.read(createdBody, "$"));
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/orders/nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void listsOrdersWithPagingAndSorting() throws Exception {
        confirm(cart(null, item(mug, 3))).andExpect(status().isCreated());
        confirm(cart(null, item(mug, 1))).andExpect(status().isCreated());
        confirm(cart(null, item(mug, 2))).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/orders").param("sort", "total,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].total", contains("12.00", "24.00", "36.00")));
        mockMvc.perform(get("/api/v1/orders").param("sort", "total,desc").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].total").value("12.00"))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/api/v1/orders").param("sort", "status"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void orderHistorySurvivesFutureCatalogueChanges() throws Exception {
        String id = orderIdOf(confirm(cart(null, item(mug, 1))));
        database.changeProduct(mug, "Renamed", "99.00");

        mockMvc.perform(get("/api/v1/orders/" + id))
                .andExpect(jsonPath("$.lines[0].name").value("Mug"))
                .andExpect(jsonPath("$.lines[0].unitPrice").value("12.00"))
                .andExpect(jsonPath("$.total").value(not("99.00")));
    }

    @Test
    void emptyOrderListIsAnEmptyPage() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/orders")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat((Integer) JsonPath.read(response.getContentAsString(), "$.totalElements")).isZero();
    }
}
