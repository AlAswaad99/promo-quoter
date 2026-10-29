package com.promoquoter.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PricingContextTest {

    private static final UUID MUG = UUID.randomUUID();
    private static final UUID LAMP = UUID.randomUUID();

    private static CartLine line(UUID productId, String category, String unitPrice, int qty) {
        return new CartLine(productId, "Item", category, new BigDecimal(unitPrice), qty);
    }

    @Test
    void startsWithSubtotalsEqualToTotals() {
        PricingContext context = PricingContext.start(
                List.of(line(MUG, "KITCHEN", "12.50", 3), line(LAMP, "HOME", "30.00", 1)), CustomerSegment.VIP);

        assertThat(context.customerSegment()).isEqualTo(CustomerSegment.VIP);
        assertThat(context.lines()).extracting(PricedLine::subtotal)
                .containsExactly(new BigDecimal("37.50"), new BigDecimal("30.00"));
        assertThat(context.lines()).extracting(PricedLine::total)
                .containsExactly(new BigDecimal("37.50"), new BigDecimal("30.00"));
        assertThat(context.appliedPromotions()).isEmpty();
    }

    @Test
    void discountIsRoundedHalfUp() {
        PricedLine priced = PricingContext.start(List.of(line(MUG, "KITCHEN", "10.00", 1)), CustomerSegment.REGULAR)
                .lines().get(0);

        BigDecimal applied = priced.applyDiscount(new BigDecimal("1.005"));

        assertThat(applied).isEqualTo(new BigDecimal("1.01"));
        assertThat(priced.total()).isEqualTo(new BigDecimal("8.99"));
        assertThat(priced.discount()).isEqualTo(new BigDecimal("1.01"));
    }

    @Test
    void discountIsCappedAtTheRemainingTotal() {
        PricedLine priced = PricingContext.start(List.of(line(MUG, "KITCHEN", "10.00", 1)), CustomerSegment.REGULAR)
                .lines().get(0);

        BigDecimal first = priced.applyDiscount(new BigDecimal("7.00"));
        BigDecimal second = priced.applyDiscount(new BigDecimal("7.00"));
        BigDecimal third = priced.applyDiscount(new BigDecimal("1.00"));

        assertThat(first).isEqualTo(new BigDecimal("7.00"));
        assertThat(second).isEqualTo(new BigDecimal("3.00"));
        assertThat(third).isEqualTo(new BigDecimal("0.00"));
        assertThat(priced.total()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void negativeDiscountsAreIgnored() {
        PricedLine priced = PricingContext.start(List.of(line(MUG, "KITCHEN", "10.00", 1)), CustomerSegment.REGULAR)
                .lines().get(0);

        BigDecimal applied = priced.applyDiscount(new BigDecimal("-4.00"));

        assertThat(applied).isEqualTo(new BigDecimal("0.00"));
        assertThat(priced.total()).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void appliedPromotionsAreNumberedInOrder() {
        PricingContext context = PricingContext.start(List.of(line(MUG, "KITCHEN", "10.00", 2)),
                CustomerSegment.REGULAR);
        PricedLine priced = context.lines().get(0);
        RuleDefinition first = Promos.percentOff("KITCHEN", "10").named("First").build();
        RuleDefinition second = Promos.buyXGetY(MUG, 1, 1).named("Second").build();

        context.recordApplied(first, priced, new BigDecimal("2.00"));
        context.recordApplied(second, priced, new BigDecimal("9.00"));

        assertThat(context.appliedPromotions()).extracting(AppliedPromotion::sequence).containsExactly(1, 2);
        assertThat(context.appliedPromotions()).extracting(AppliedPromotion::name).containsExactly("First", "Second");
        assertThat(context.appliedPromotions().get(0).productId()).isEqualTo(MUG);
    }

    @Test
    void breakdownReconcilesSubtotalDiscountAndTotal() {
        PricingContext context = PricingContext.start(
                List.of(line(MUG, "KITCHEN", "10.00", 2), line(LAMP, "HOME", "30.00", 1)), CustomerSegment.EMPLOYEE);
        context.lines().get(0).applyDiscount(new BigDecimal("4.00"));
        context.lines().get(1).applyDiscount(new BigDecimal("3.00"));

        PriceBreakdown breakdown = context.toBreakdown();

        assertThat(breakdown.customerSegment()).isEqualTo(CustomerSegment.EMPLOYEE);
        assertThat(breakdown.subtotal()).isEqualTo(new BigDecimal("50.00"));
        assertThat(breakdown.totalDiscount()).isEqualTo(new BigDecimal("7.00"));
        assertThat(breakdown.total()).isEqualTo(new BigDecimal("43.00"));
        assertThat(breakdown.subtotal().subtract(breakdown.totalDiscount())).isEqualTo(breakdown.total());
        assertThat(breakdown.lines()).extracting(LineBreakdown::discount)
                .containsExactly(new BigDecimal("4.00"), new BigDecimal("3.00"));
    }

    @Test
    void emptyCartHasZeroTotals() {
        PriceBreakdown breakdown = PricingContext.start(List.of(), CustomerSegment.REGULAR).toBreakdown();

        assertThat(breakdown.total()).isEqualTo(new BigDecimal("0.00"));
        assertThat(breakdown.subtotal()).isEqualTo(new BigDecimal("0.00"));
        assertThat(breakdown.lines()).isEmpty();
    }
}
