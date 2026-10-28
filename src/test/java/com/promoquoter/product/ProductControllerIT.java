package com.promoquoter.product;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void getsAProductById() throws Exception {
        String id = idOfCreated(product("Mug", "kitchen", "12", 5));

        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Mug"))
                .andExpect(jsonPath("$.price").value("12.00"))
                .andExpect(jsonPath("$.stock").value(5));
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void malformedIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void listsWithPaginationMetadata() throws Exception {
        seed(25);

        mockMvc.perform(get("/api/v1/products").param("page", "2").param("size", "10").param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Item 20"));
    }

    @Test
    void listDefaultsToTwentyPerPage() throws Exception {
        seed(25);

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void listCapsPageSizeAtOneHundred() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void listSortsByPrice() throws Exception {
        createProducts("[" + product("Cheap", "k", "1", 1) + "," + product("Mid", "k", "5", 1) + ","
                + product("Dear", "k", "9", 1) + "]").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/products").param("sort", "price,desc"))
                .andExpect(jsonPath("$.content[*].name", contains("Dear", "Mid", "Cheap")));
        mockMvc.perform(get("/api/v1/products").param("sort", "price"))
                .andExpect(jsonPath("$.content[*].name", contains("Cheap", "Mid", "Dear")));
    }

    @Test
    void listFiltersByCategoryCaseInsensitively() throws Exception {
        createProducts("[" + product("Mug", "kitchen", "1", 1) + "," + product("Lamp", "home", "1", 1) + ","
                + product("Pan", "KITCHEN", "1", 1) + "]").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/products").param("category", " Kitchen ").param("sort", "name,asc"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].name", contains("Mug", "Pan")));
    }

    @Test
    void blankCategoryFilterIsIgnored() throws Exception {
        seed(3);

        mockMvc.perform(get("/api/v1/products").param("category", " "))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void rejectsSortOutsideAllowList() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sort", "stock,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void rejectsNegativePageAndZeroSize() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/products").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void emptyListIsStillAPage() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    private void seed(int count) throws Exception {
        String batch = "[" + String.join(",",
                IntStream.range(0, count).mapToObj(i -> product("Item " + String.format("%02d", i), "k", "1", 1)).toList())
                + "]";
        createProducts(batch).andExpect(status().isCreated());
    }

    private String idOfCreated(String productJson) throws Exception {
        String body = createProducts("[" + productJson + "]").andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$[0].id");
    }
}
