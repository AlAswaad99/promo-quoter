package com.promoquoter.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.cart.domain.GiftLine;
import com.promoquoter.cart.domain.QuotedCart;
import com.promoquoter.pricing.model.ActionKind;
import com.promoquoter.pricing.model.AdjustmentDirection;
import com.promoquoter.pricing.model.CartAdjustment;
import com.promoquoter.pricing.model.CustomerSegment;
import com.promoquoter.pricing.model.PriceBreakdown;
import com.promoquoter.pricing.model.SkippedRule;

class OrderSnapshotTest {

    private static final UUID RULE = UUID.randomUUID();
    private static final UUID GIFT = UUID.randomUUID();

    private static PriceBreakdown breakdown() {
        return new PriceBreakdown(CustomerSegment.REGULAR, List.of(), List.of(), new BigDecimal("36.00"),
                new BigDecimal("5.00"), new BigDecimal("36.50"),
                List.of(new CartAdjustment(1, RULE, "Voucher", ActionKind.AMOUNT_OFF_CART, AdjustmentDirection.DISCOUNT,
                        new BigDecimal("5.00")),
                        new CartAdjustment(2, RULE, "Handling", ActionKind.SURCHARGE, AdjustmentDirection.CHARGE,
                                new BigDecimal("2.50"))),
                new BigDecimal("3.00"), List.of(), List.of(), List.of());
    }

    @Test
    void aQuotedCartKeepsShippingAdjustmentsGiftsAndNotes() {
        QuotedCart quoted = new QuotedCart(breakdown(),
                List.of(new GiftLine(GIFT, "Gift Card", "GIFTS", 2, RULE, "Gift with mugs")),
                List.of(new SkippedRule(RULE, "Fee", "stopped by 'Voucher'")));

        Order order = Order.confirmed(UUID.randomUUID(), quoted, "key");

        assertThat(order.getShipping()).isEqualTo(new BigDecimal("3.00"));
        assertThat(order.getTotal()).isEqualTo(new BigDecimal("36.50"));
        assertThat(order.getAdjustments()).extracting(CartAdjustmentSnapshot::name).containsExactly("Voucher", "Handling");
        assertThat(order.getAdjustments()).extracting(CartAdjustmentSnapshot::direction)
                .containsExactly(AdjustmentDirection.DISCOUNT, AdjustmentDirection.CHARGE);
        assertThat(order.getGiftLines()).singleElement().satisfies(gift -> {
            assertThat(gift.productId()).isEqualTo(GIFT);
            assertThat(gift.qty()).isEqualTo(2);
            assertThat(gift.promotionName()).isEqualTo("Gift with mugs");
        });
        assertThat(order.getNotes()).containsExactly("Fee: stopped by 'Voucher'");
    }

    @Test
    void anOrderFromAPlainBreakdownHasNoExtrasAndNoShipping() {
        PriceBreakdown plain = new PriceBreakdown(CustomerSegment.REGULAR, List.of(), List.of(),
                new BigDecimal("10.00"), new BigDecimal("0.00"), new BigDecimal("10.00"));

        Order order = Order.confirmed(UUID.randomUUID(), plain, null);

        assertThat(order.getShipping()).isEqualTo(new BigDecimal("0.00"));
        assertThat(order.getAdjustments()).isEmpty();
        assertThat(order.getGiftLines()).isEmpty();
        assertThat(order.getNotes()).isEmpty();
    }

    @Test
    void veryLongNotesAreTruncatedToTheColumnLength() {
        QuotedCart quoted = new QuotedCart(breakdown(), List.of(),
                List.of(new SkippedRule(RULE, "Rule", "r".repeat(900))));

        Order order = Order.confirmed(UUID.randomUUID(), quoted, null);

        assertThat(order.getNotes()).singleElement().satisfies(note -> assertThat(note).hasSize(500));
    }
}
