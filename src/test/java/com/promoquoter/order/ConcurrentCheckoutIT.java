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
import java.util.function.IntFunction;

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
class ConcurrentCheckoutIT {

    private static final int THREADS = 20;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApiFixtures api;

    @Autowired
    TestDatabase database;

    @BeforeEach
    void setUp() {
        database.clean();
    }

    private static String cart(String... items) {
        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    private static String item(String productId, int qty) {
        return "{\"productId\":\"" + productId + "\",\"qty\":" + qty + "}";
    }

    private List<MockHttpServletResponse> fireTogether(int count, IntFunction<MockHttpServletRequestSpec> requests)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                MockHttpServletRequestSpec spec = requests.apply(i);
                Callable<MockHttpServletResponse> call = () -> {
                    ready.countDown();
                    go.await();
                    var request = post("/api/v1/cart/confirm").contentType(MediaType.APPLICATION_JSON)
                            .content(spec.body());
                    if (spec.idempotencyKey() != null) {
                        request.header("Idempotency-Key", spec.idempotencyKey());
                    }
                    return mockMvc.perform(request).andReturn().getResponse();
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

    private record MockHttpServletRequestSpec(String body, String idempotencyKey) {
    }

    private static long countWithStatus(List<MockHttpServletResponse> responses, int status) {
        return responses.stream().filter(response -> response.getStatus() == status).count();
    }

    @Test
    void neverSellsMoreThanTheStockUnderContention() throws Exception {
        String product = api.createProduct("Scarce", "k", "10.00", 5);

        List<MockHttpServletResponse> responses = fireTogether(THREADS,
                i -> new MockHttpServletRequestSpec(cart(item(product, 1)), null));

        assertThat(countWithStatus(responses, 201)).isEqualTo(5);
        assertThat(countWithStatus(responses, 409)).isEqualTo(THREADS - 5);
        assertThat(database.stockOf(product)).isZero();
        assertThat(database.count("orders")).isEqualTo(5);
        responses.stream().filter(response -> response.getStatus() == 409).forEach(response -> {
            try {
                assertThat((String) JsonPath.read(response.getContentAsString(), "$.code"))
                        .isEqualTo("INSUFFICIENT_STOCK");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void multiUnitCartsStayAllOrNothingUnderContention() throws Exception {
        String scarce = api.createProduct("Scarce", "k", "10.00", 7);
        String plentiful = api.createProduct("Plentiful", "k", "1.00", 100);

        List<MockHttpServletResponse> responses = fireTogether(THREADS,
                i -> new MockHttpServletRequestSpec(cart(item(scarce, 2), item(plentiful, 3)), null));

        long created = countWithStatus(responses, 201);
        assertThat(created).isEqualTo(3);
        assertThat(countWithStatus(responses, 409)).isEqualTo(THREADS - 3);
        assertThat(database.stockOf(scarce)).isEqualTo(1);
        assertThat(database.stockOf(plentiful)).isEqualTo(100 - 3 * (int) created);
        assertThat(database.count("orders")).isEqualTo(3);
    }

    @Test
    void concurrentRequestsWithTheSameKeyReserveOnlyOnce() throws Exception {
        String product = api.createProduct("Shared", "k", "10.00", 50);

        List<MockHttpServletResponse> responses = fireTogether(10,
                i -> new MockHttpServletRequestSpec(cart(item(product, 2)), "same-key"));

        assertThat(countWithStatus(responses, 201)).isEqualTo(10);
        List<String> orderIds = new ArrayList<>();
        for (MockHttpServletResponse response : responses) {
            orderIds.add(JsonPath.read(response.getContentAsString(), "$.orderId"));
        }
        assertThat(orderIds).containsOnly(orderIds.get(0));
        long replays = responses.stream().filter(response -> "true".equals(response.getHeader("Idempotent-Replayed")))
                .count();
        assertThat(replays).isEqualTo(9);
        assertThat(database.stockOf(product)).isEqualTo(48);
        assertThat(database.count("orders")).isEqualTo(1);
        assertThat(database.count("idempotency_record")).isEqualTo(1);
    }

    @Test
    void concurrentRequestsWithTheSameKeyButDifferentCartsAllowOnlyOneCart() throws Exception {
        String product = api.createProduct("Shared", "k", "10.00", 50);

        List<MockHttpServletResponse> responses = fireTogether(8,
                i -> new MockHttpServletRequestSpec(cart(item(product, i + 1)), "contested-key"));

        assertThat(countWithStatus(responses, 201)).isEqualTo(1);
        assertThat(countWithStatus(responses, 422)).isEqualTo(7);
        assertThat(database.count("orders")).isEqualTo(1);
        int reserved = 50 - database.stockOf(product);
        assertThat(reserved).isBetween(1, 8);
    }

    @Test
    void cartsTouchingTheSameProductsInOppositeOrderDoNotDeadlock() throws Exception {
        String first = api.createProduct("A", "k", "10.00", 100);
        String second = api.createProduct("B", "k", "10.00", 100);

        List<MockHttpServletResponse> responses = fireTogether(THREADS, i -> new MockHttpServletRequestSpec(
                i % 2 == 0 ? cart(item(first, 1), item(second, 1)) : cart(item(second, 1), item(first, 1)), null));

        assertThat(countWithStatus(responses, 201)).isEqualTo(THREADS);
        assertThat(database.stockOf(first)).isEqualTo(100 - THREADS);
        assertThat(database.stockOf(second)).isEqualTo(100 - THREADS);
    }

    @Test
    void failedContendersWithAKeyCanRetryAfterRestock() throws Exception {
        String product = api.createProduct("Scarce", "k", "10.00", 1);

        List<MockHttpServletResponse> responses = fireTogether(6,
                i -> new MockHttpServletRequestSpec(cart(item(product, 1)), "retry-" + i));

        assertThat(countWithStatus(responses, 201)).isEqualTo(1);
        assertThat(countWithStatus(responses, 409)).isEqualTo(5);
        assertThat(database.count("idempotency_record")).isEqualTo(1);
    }
}
