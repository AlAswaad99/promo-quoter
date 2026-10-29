package com.promoquoter.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.support.IntegrationTest;
import com.promoquoter.support.TestDatabase;

@IntegrationTest
class InventoryServiceIT {

    @Autowired
    InventoryService inventoryService;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    TestDatabase testDatabase;

    UUID mug;
    UUID lamp;

    @BeforeEach
    void setUp() {
        testDatabase.clean();
        mug = productRepository.save(new Product("Mug", "kitchen", new BigDecimal("12.00"), 10)).getId();
        lamp = productRepository.save(new Product("Lamp", "home", new BigDecimal("30.00"), 2)).getId();
    }

    private static List<Object> errorsOf(ApiException exception) {
        return List.copyOf(exception.getErrors());
    }

    private void reserve(Map<UUID, Integer> quantities) {
        transactionTemplate.executeWithoutResult(status -> inventoryService.reserve(quantities));
    }

    private int stockOf(UUID id) {
        return productRepository.findById(id).orElseThrow().getStock();
    }

    @Test
    void reservingDecrementsEveryLine() {
        reserve(Map.of(mug, 4, lamp, 2));

        assertThat(stockOf(mug)).isEqualTo(6);
        assertThat(stockOf(lamp)).isZero();
    }

    @Test
    void reservingTheExactRemainingStockIsAllowed() {
        reserve(Map.of(mug, 10));

        assertThat(stockOf(mug)).isZero();
    }

    @Test
    void shortagesAreReportedForEveryShortProductAndNothingIsReserved() {
        assertThatThrownBy(() -> reserve(Map.of(mug, 11, lamp, 3)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
                    assertThat(errorsOf(exception)).containsExactlyInAnyOrder(
                            new InventoryService.StockShortage(mug, 11, 10),
                            new InventoryService.StockShortage(lamp, 3, 2));
                });

        assertThat(stockOf(mug)).isEqualTo(10);
        assertThat(stockOf(lamp)).isEqualTo(2);
    }

    @Test
    void onlyTheShortProductIsReported() {
        assertThatThrownBy(() -> reserve(Map.of(mug, 1, lamp, 3)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(errorsOf(exception))
                                .containsExactly(new InventoryService.StockShortage(lamp, 3, 2)));

        assertThat(stockOf(mug)).isEqualTo(10);
    }

    @Test
    void missingProductsAreReportedAsNotFound() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> reserve(Map.of(mug, 1, missing, 1)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
                    assertThat(errorsOf(exception)).hasSize(1);
                });

        assertThat(stockOf(mug)).isEqualTo(10);
    }

    @Test
    void reservingRequiresAnExistingTransaction() {
        assertThatThrownBy(() -> inventoryService.reserve(Map.of(mug, 1)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void shortagesAreListedInProductIdOrder() {
        List<UUID> sorted = List.of(mug, lamp).stream().sorted().toList();

        assertThatThrownBy(() -> reserve(Map.of(mug, 99, lamp, 99)))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(errorsOf(exception))
                        .extracting(error -> ((InventoryService.StockShortage) error).productId())
                        .containsExactlyElementsOf(sorted));
    }
}
