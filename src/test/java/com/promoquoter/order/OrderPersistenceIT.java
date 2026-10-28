package com.promoquoter.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import com.promoquoter.pricing.AppliedPromotion;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.LineBreakdown;
import com.promoquoter.pricing.PriceBreakdown;
import com.promoquoter.pricing.PromotionType;
import com.promoquoter.support.IntegrationTest;
import com.promoquoter.support.TestDatabase;

@IntegrationTest
class OrderPersistenceIT {

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    IdempotencyRepository idempotencyRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    TestDatabase testDatabase;

    @BeforeEach
    void clean() {
        testDatabase.clean();
    }

    private static PriceBreakdown breakdown() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<LineBreakdown> lines = List.of(
                new LineBreakdown(first, "Mug", "KITCHEN", new BigDecimal("12.00"), 3, new BigDecimal("36.00"),
                        new BigDecimal("14.40"), new BigDecimal("21.60")),
                new LineBreakdown(second, "Lamp", "HOME", new BigDecimal("30.00"), 1, new BigDecimal("30.00"),
                        new BigDecimal("0.00"), new BigDecimal("30.00")));
        List<AppliedPromotion> applied = List.of(
                new AppliedPromotion(1, UUID.randomUUID(), "Mugs 2+1", PromotionType.BUY_X_GET_Y, first,
                        new BigDecimal("12.00")),
                new AppliedPromotion(2, UUID.randomUUID(), "10% kitchen", PromotionType.PERCENT_OFF_CATEGORY, first,
                        new BigDecimal("2.40")));
        return new PriceBreakdown(CustomerSegment.REGULAR, lines, applied, new BigDecimal("66.00"),
                new BigDecimal("14.40"), new BigDecimal("51.60"));
    }

    @Test
    void anOrderIsReadBackWithLinesAndPromotionsInOrder() {
        UUID id = UUID.randomUUID();
        orderRepository.saveAndFlush(Order.confirmed(id, breakdown(), "key-1"));

        Order loaded = transactionTemplate.execute(status -> {
            Order order = orderRepository.findById(id).orElseThrow();
            order.getLines().size();
            order.getAppliedPromotions().size();
            return order;
        });

        assertThat(loaded.getTotal()).isEqualTo(new BigDecimal("51.60"));
        assertThat(loaded.getLines()).extracting(OrderLineSnapshot::name).containsExactly("Mug", "Lamp");
        assertThat(loaded.getAppliedPromotions()).extracting(AppliedPromotionSnapshot::sequence)
                .containsExactly(1, 2);
        assertThat(loaded.getVersion()).isNotNull();
    }

    @Test
    void anIdempotencyKeyCanOnlyBeStoredOnce() {
        UUID orderId = UUID.randomUUID();
        idempotencyRepository.saveAndFlush(new IdempotencyRecord("key-1", "hash", orderId));

        assertThatThrownBy(() -> idempotencyRepository
                .saveAndFlush(new IdempotencyRecord("key-1", "other", UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(idempotencyRepository.findById("key-1").orElseThrow().getOrderId()).isEqualTo(orderId);
    }
}
