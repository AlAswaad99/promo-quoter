package com.promoquoter.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.promoquoter.support.IntegrationTest;

@IntegrationTest
class ProductControllerIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ProductRepository productRepository;

    @BeforeEach
    void cleanDatabase() {
        productRepository.deleteAll();
    }

    private ResultActions createProducts(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String product(String name, String category, String price, int stock) {
        return "{\"name\":\"" + name + "\",\"category\":\"" + category + "\",\"price\":" + price + ",\"stock\":"
                + stock + "}";
    }

    @Test
    void createsABatchOfProducts() throws Exception {
        createProducts("[" + product("Mug", "kitchen", "12", 50) + "," + product("Lamp", " Home ", "30.45", 3)
                + "]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].name").value("Mug"))
                .andExpect(jsonPath("$[0].category").value("KITCHEN"))
                .andExpect(jsonPath("$[0].price").value("12.00"))
                .andExpect(jsonPath("$[0].stock").value(50))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[1].category").value("HOME"))
                .andExpect(jsonPath("$[1].price").value("30.45"));

        assertThat(productRepository.count()).isEqualTo(2);
    }

    @Test
    void acceptsPriceGivenAsString() throws Exception {
        createProducts("[{\"name\":\"Mug\",\"category\":\"k\",\"price\":\"9.9\",\"stock\":1}]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].price").value("9.90"));
    }

    @Test
    void acceptsZeroStock() throws Exception {
        createProducts("[" + product("Mug", "k", "1.00", 0) + "]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].stock").value(0));
    }

    @Test
    void rejectsInvalidFieldsAndReportsEachOne() throws Exception {
        createProducts("[{\"name\":\" \",\"category\":\"\",\"price\":0,\"stock\":-1}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field",
                        contains("[0].category", "[0].name", "[0].price", "[0].stock")));
    }

    @Test
    void rejectsNegativePriceAndTooManyDecimals() throws Exception {
        createProducts("[" + product("A", "k", "-5", 1) + "," + product("B", "k", "1.234", 1) + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("[0].price", "[1].price")));
    }

    @Test
    void rejectsMissingFields() throws Exception {
        createProducts("[{}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("[0].category", "[0].name", "[0].price", "[0].stock")));
    }

    @Test
    void rejectsOverlongNameAndCategory() throws Exception {
        createProducts("[" + product("n".repeat(201), "c".repeat(101), "1", 1) + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("[0].category", "[0].name")));
    }

    @Test
    void rejectsNullElement() throws Exception {
        createProducts("[null]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsEmptyAndOversizedBatches() throws Exception {
        createProducts("[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("products"));

        String oversized = "[" + String.join(",", IntStream.range(0, 101).mapToObj(i -> product("P" + i, "k", "1", 1)).toList())
                + "]";
        createProducts(oversized)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("products"));
    }

    @Test
    void rejectsNonNumericPriceAsMalformed() throws Exception {
        createProducts("[{\"name\":\"a\",\"category\":\"k\",\"price\":\"abc\",\"stock\":1}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void rejectsAPriceBeyondWhatACartCanHold() throws Exception {
        createProducts("[{\"name\":\"Gold\",\"category\":\"x\",\"price\":100000000000.00,\"stock\":1}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("[0].price"));
    }

    @Test
    void acceptsTheLargestAllowedPrice() throws Exception {
        createProducts("[{\"name\":\"Gold\",\"category\":\"x\",\"price\":9999999999.99,\"stock\":1}]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].price").value("9999999999.99"));
    }

    @Test
    void rejectsAnUnknownField() throws Exception {
        createProducts("[{\"name\":\"Cup\",\"category\":\"kitchen\",\"price\":3,\"stock\":1,\"sku\":\"x\"}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void batchIsAllOrNothing() throws Exception {
        createProducts("[" + product("Good", "k", "5", 1) + "," + product(" ", "k", "5", 1) + "]")
                .andExpect(status().isBadRequest());

        assertThat(productRepository.count()).isZero();
    }
}
