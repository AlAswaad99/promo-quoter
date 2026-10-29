package com.promoquoter.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.promoquoter.support.ApiFixtures;
import com.promoquoter.support.IntegrationTest;
import com.promoquoter.support.TestDatabase;

@IntegrationTest
class ConcurrentGiftCheckoutIT {

    private static final int THREADS = 12;

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
        mug = api.createProduct("Mug", "kitchen", "12.00", 100);
        gift = api.createProduct("Gift Card", "gifts", "5.00", 5);
        api.createPromotion("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Gift with mugs\","
                + "\"target\":{\"field\":\"product.id\",\"op\":\"eq\",\"value\":\"" + mug + "\"},"
                + "\"action\":{\"kind\":\"FREE_GIFT\",\"productId\":\"" + gift + "\",\"qty\":1}}");
    }

    private static String item(String productId, int qty) {
        return "{\"productId\":\"" + productId + "\",\"qty\":" + qty + "}";
    }

    private List<MockHttpServletResponse> fireTogether(List<String> bodies) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(bodies.size());
        CountDownLatch ready = new CountDownLatch(bodies.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (String body : bodies) {
                Callable<MockHttpServletResponse> call = () -> {
                    ready.countDown();
                    go.await();
                    return mockMvc.perform(post("/api/v1/cart/confirm").contentType(MediaType.APPLICATION_JSON)
                            .content(body)).andReturn().getResponse();
                };
                futures.add(executor.submit(call));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }

    private static int giftsIn(MockHttpServletResponse response) throws Exception {
        List<Object> gifts = JsonPath.read(response.getContentAsString(), "$.gifts");
        return gifts.size();
    }

    @Test
    void giftStockIsNeverOversoldAndEveryPaidOrderStillSucceeds() throws Exception {
        List<String> bodies = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            bodies.add("{\"items\":[" + item(mug, 1) + "]}");
        }

        List<MockHttpServletResponse> responses = fireTogether(bodies);

        assertThat(responses).allSatisfy(response -> assertThat(response.getStatus()).isEqualTo(201));
        int ordersWithAGift = 0;
        for (MockHttpServletResponse response : responses) {
            ordersWithAGift += giftsIn(response);
        }
        assertThat(ordersWithAGift).isEqualTo(5);
        assertThat(database.stockOf(gift)).isZero();
        assertThat(database.stockOf(mug)).isEqualTo(100 - THREADS);
        assertThat(database.count("orders")).isEqualTo(THREADS);
        assertThat(database.count("order_gift")).isEqualTo(5);
        assertThat(database.count("order_note")).isEqualTo(THREADS - 5);
    }

    @Test
    void cartsBuyingTheGiftProductThemselvesNeverDeadlockOrOversell() throws Exception {
        List<String> bodies = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            bodies.add(i % 2 == 0
                    ? "{\"items\":[" + item(mug, 1) + "," + item(gift, 1) + "]}"
                    : "{\"items\":[" + item(gift, 1) + "," + item(mug, 1) + "]}");
        }

        List<MockHttpServletResponse> responses = fireTogether(bodies);

        long created = responses.stream().filter(response -> response.getStatus() == 201).count();
        long conflicts = responses.stream().filter(response -> response.getStatus() == 409).count();
        assertThat(created + conflicts).isEqualTo(THREADS);
        assertThat(created).isLessThanOrEqualTo(5);
        assertThat(database.stockOf(gift)).isGreaterThanOrEqualTo(0);
        assertThat(database.count("orders")).isEqualTo((int) created);
        int giftUnits = database.count("order_gift");
        assertThat(5 - database.stockOf(gift)).isEqualTo((int) created + giftUnits);
    }
}
