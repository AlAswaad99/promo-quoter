package com.promoquoter.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.order.dto.OrderStatus;
import com.promoquoter.pricing.AppliedPromotion;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.LineBreakdown;
import com.promoquoter.pricing.PriceBreakdown;
import com.promoquoter.pricing.PromotionType;

class OrderTest {

    private static final UUID MUG = UUID.randomUUID();

    private static PriceBreakdown breakdown() {
        LineBreakdown line = new LineBreakdown(MUG, "Mug", "KITCHEN", new BigDecimal("12.00"), 3,
                new BigDecimal("36.00"), new BigDecimal("14.40"), new BigDecimal("21.60"));
        AppliedPromotion applied = new AppliedPromotion(1, UUID.randomUUID(), "Mugs 2+1", PromotionType.BUY_X_GET_Y,
                MUG, new BigDecimal("12.00"));
        return new PriceBreakdown(CustomerSegment.VIP, List.of(line), List.of(applied), new BigDecimal("36.00"),
                new BigDecimal("14.40"), new BigDecimal("21.60"));
    }

    @Test
    void confirmedOrderCopiesTheBreakdownTotals() {
        UUID id = UUID.randomUUID();

        Order order = Order.confirmed(id, breakdown(), "key-1");

        assertThat(order.getId()).isEqualTo(id);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getCustomerSegment()).isEqualTo(CustomerSegment.VIP);
        assertThat(order.getSubtotal()).isEqualTo(new BigDecimal("36.00"));
        assertThat(order.getTotalDiscount()).isEqualTo(new BigDecimal("14.40"));
        assertThat(order.getTotal()).isEqualTo(new BigDecimal("21.60"));
        assertThat(order.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getVersion()).isNull();
    }

    @Test
    void confirmedOrderSnapshotsLinesAndAppliedPromotions() {
        Order order = Order.confirmed(UUID.randomUUID(), breakdown(), null);

        assertThat(order.getLines()).hasSize(1);
        OrderLineSnapshot line = order.getLines().get(0);
        assertThat(line.productId()).isEqualTo(MUG);
        assertThat(line.name()).isEqualTo("Mug");
        assertThat(line.qty()).isEqualTo(3);
        assertThat(line.total()).isEqualTo(new BigDecimal("21.60"));
        assertThat(order.getAppliedPromotions()).hasSize(1);
        AppliedPromotionSnapshot applied = order.getAppliedPromotions().get(0);
        assertThat(applied.sequence()).isEqualTo(1);
        assertThat(applied.type()).isEqualTo(PromotionType.BUY_X_GET_Y);
        assertThat(applied.amount()).isEqualTo(new BigDecimal("12.00"));
    }

    @Test
    void orderWithoutAKeyHasNoIdempotencyKey() {
        assertThat(Order.confirmed(UUID.randomUUID(), breakdown(), null).getIdempotencyKey()).isNull();
    }

    @Test
    void idempotencyRecordKeepsKeyHashAndOrderId() {
        UUID orderId = UUID.randomUUID();

        IdempotencyRecord record = new IdempotencyRecord("key-1", "abc", orderId);

        assertThat(record.getKey()).isEqualTo("key-1");
        assertThat(record.getRequestHash()).isEqualTo("abc");
        assertThat(record.getOrderId()).isEqualTo(orderId);
        assertThat(record.getCreatedAt()).isNotNull();
    }
}
