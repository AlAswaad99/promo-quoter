package com.promoquoter.pricing.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.pricing.condition.ConditionField;
import com.promoquoter.pricing.model.ActionKind;
import com.promoquoter.pricing.model.AdjustmentDirection;
import com.promoquoter.pricing.model.AppliedPromotion;
import com.promoquoter.pricing.model.CartAdjustment;
import com.promoquoter.pricing.model.CartLine;
import com.promoquoter.pricing.model.CustomerSegment;
import com.promoquoter.pricing.model.GiftRequest;
import com.promoquoter.pricing.model.PriceBreakdown;
import com.promoquoter.pricing.model.RuleViolation;
import com.promoquoter.pricing.model.SkippedRule;

class PricingContextAdjustmentsTest {

    private static final UUID MUG = UUID.randomUUID();
    private static final UUID LAMP = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.parse("2026-11-15");
    private static final RuleDefinition RULE = Promos.percentOff("KITCHEN", "10").named("Rule").build();

    private static PricingContext context(String shippingFee) {
        return PricingContext.start(List.of(
                new CartLine(MUG, "Mug", "KITCHEN", new BigDecimal("12.00"), 3, 10),
                new CartLine(LAMP, "Lamp", "HOME", new BigDecimal("30.00"), 1, 4)), CustomerSegment.VIP, TODAY,
                new BigDecimal(shippingFee));
    }

    @Test
    void cartDiscountIsCappedAtTheRemainingGoodsTotal() {
        PricingContext context = context("0");

        BigDecimal first = context.applyCartDiscount(RULE, new BigDecimal("50.00"));
        BigDecimal second = context.applyCartDiscount(RULE, new BigDecimal("50.00"));

        assertThat(first).isEqualTo(new BigDecimal("50.00"));
        assertThat(second).isEqualTo(new BigDecimal("16.00"));
        assertThat(context.goodsTotal()).isEqualTo(new BigDecimal("0.00"));
        assertThat(context.adjustments()).extracting(CartAdjustment::amount)
                .containsExactly(new BigDecimal("50.00"), new BigDecimal("16.00"));
        assertThat(context.adjustments()).allSatisfy(adjustment -> {
            assertThat(adjustment.direction()).isEqualTo(AdjustmentDirection.DISCOUNT);
            assertThat(adjustment.action()).isEqualTo(ActionKind.AMOUNT_OFF_CART);
        });
    }

    @Test
    void aCartDiscountThatChangesNothingIsNotRecorded() {
        PricingContext context = context("0");

        assertThat(context.applyCartDiscount(RULE, new BigDecimal("-5"))).isEqualTo(new BigDecimal("0.00"));
        assertThat(context.adjustments()).isEmpty();
    }

    @Test
    void surchargesAddToTheTotalAndAreRecordedAsCharges() {
        PricingContext context = context("0");

        context.addSurcharge(RULE, new BigDecimal("2.50"));
        context.addSurcharge(RULE, new BigDecimal("-1.00"));
        PriceBreakdown breakdown = context.toBreakdown();

        assertThat(breakdown.total()).isEqualTo(new BigDecimal("68.50"));
        assertThat(breakdown.adjustments()).hasSize(1);
        assertThat(breakdown.adjustments().get(0).direction()).isEqualTo(AdjustmentDirection.CHARGE);
        assertThat(breakdown.adjustments().get(0).action()).isEqualTo(ActionKind.SURCHARGE);
        assertThat(breakdown.totalDiscount()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void shippingIsPartOfTheTotalUntilItIsWaived() {
        PricingContext context = context("5.00");

        assertThat(context.toBreakdown().shipping()).isEqualTo(new BigDecimal("5.00"));
        assertThat(context.toBreakdown().total()).isEqualTo(new BigDecimal("71.00"));

        BigDecimal waived = context.waiveShipping(RULE);
        PriceBreakdown breakdown = context.toBreakdown();

        assertThat(waived).isEqualTo(new BigDecimal("5.00"));
        assertThat(breakdown.shipping()).isEqualTo(new BigDecimal("0.00"));
        assertThat(breakdown.total()).isEqualTo(new BigDecimal("66.00"));
        assertThat(breakdown.adjustments()).hasSize(1);
        assertThat(breakdown.adjustments().get(0).action()).isEqualTo(ActionKind.SHIPPING_WAIVER);
        assertThat(breakdown.adjustments().get(0).amount()).isEqualTo(new BigDecimal("5.00"));
    }

    @Test
    void waivingFreeShippingChangesNothingAndIsNotRecorded() {
        PricingContext context = context("0");

        assertThat(context.waiveShipping(RULE)).isEqualTo(new BigDecimal("0.00"));
        assertThat(context.adjustments()).isEmpty();
    }

    @Test
    void breakdownReconcilesSubtotalDiscountsSurchargesAndShipping() {
        PricingContext context = context("4.00");
        context.lines().get(0).applyDiscount(new BigDecimal("6.00"));
        context.applyCartDiscount(RULE, new BigDecimal("10.00"));
        context.addSurcharge(RULE, new BigDecimal("3.00"));

        PriceBreakdown breakdown = context.toBreakdown();

        assertThat(breakdown.subtotal()).isEqualTo(new BigDecimal("66.00"));
        assertThat(breakdown.totalDiscount()).isEqualTo(new BigDecimal("16.00"));
        assertThat(breakdown.total()).isEqualTo(new BigDecimal("57.00"));
        assertThat(breakdown.subtotal().subtract(breakdown.totalDiscount()).add(new BigDecimal("3.00"))
                .add(breakdown.shipping())).isEqualTo(breakdown.total());
    }

    @Test
    void lineAndCartEntriesShareOneSequence() {
        PricingContext context = context("0");
        PricedLine mug = context.lines().get(0);

        context.recordApplied(RULE, mug, new BigDecimal("1.00"));
        context.applyCartDiscount(RULE, new BigDecimal("2.00"));
        context.recordApplied(RULE, mug, new BigDecimal("3.00"));

        assertThat(context.appliedPromotions()).extracting(AppliedPromotion::sequence).containsExactly(1, 3);
        assertThat(context.adjustments()).extracting(CartAdjustment::sequence).containsExactly(2);
    }

    @Test
    void giftUnitsAreLimitedPerOrder() {
        PricingContext context = context("0");

        assertThat(context.requestGift(RULE, MUG, 60)).isEqualTo(60);
        assertThat(context.requestGift(RULE, MUG, 60)).isEqualTo(40);
        assertThat(context.requestGift(RULE, LAMP, 1)).isZero();

        assertThat(context.gifts()).extracting(GiftRequest::qty).containsExactly(60, 40);
    }

    @Test
    void violationsAndSkipsAreRecordedWithTheRuleIdentity() {
        PricingContext context = context("0");

        context.reject(RULE, "Minimum order is 100");
        context.skip(RULE, "stopped by another rule");

        assertThat(context.violations()).containsExactly(new RuleViolation(RULE.id(), "Rule", "Minimum order is 100"));
        assertThat(context.skippedRules()).containsExactly(new SkippedRule(RULE.id(), "Rule", "stopped by another rule"));
        assertThat(context.toBreakdown().violations()).hasSize(1);
        assertThat(context.toBreakdown().skippedRules()).hasSize(1);
    }

    @Test
    void freezingALineAndStoppingTheCartAreTracked() {
        PricingContext context = context("0");
        PricedLine mug = context.lines().get(0);

        assertThat(context.isFrozen(mug)).isFalse();
        assertThat(context.isStopped()).isFalse();
        context.freeze(mug);
        context.stop(RULE);

        assertThat(context.isFrozen(mug)).isTrue();
        assertThat(context.isFrozen(context.lines().get(1))).isFalse();
        assertThat(context.isStopped()).isTrue();
        assertThat(context.stoppedBy()).isEqualTo("Rule");
    }

    @Test
    void lineFactsExposeProductAndQuantity() {
        PricingContext context = context("0");
        var facts = context.lineFacts(context.lines().get(0));

        assertThat(facts.valueOf(ConditionField.PRODUCT_ID)).isEqualTo(MUG);
        assertThat(facts.valueOf(ConditionField.PRODUCT_NAME)).isEqualTo("Mug");
        assertThat(facts.valueOf(ConditionField.PRODUCT_CATEGORY)).isEqualTo("KITCHEN");
        assertThat(facts.valueOf(ConditionField.PRODUCT_PRICE)).isEqualTo(new BigDecimal("12.00"));
        assertThat(facts.valueOf(ConditionField.PRODUCT_STOCK)).isEqualTo(10);
        assertThat(facts.valueOf(ConditionField.LINE_QTY)).isEqualTo(3);
        assertThatThrownBy(() -> facts.valueOf(ConditionField.CART_SUBTOTAL)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cartFactsReflectTheRunningState() {
        PricingContext context = context("0");
        var facts = context.cartFacts();

        assertThat(facts.valueOf(ConditionField.CART_SUBTOTAL)).isEqualTo(new BigDecimal("66.00"));
        assertThat(facts.valueOf(ConditionField.CART_TOTAL)).isEqualTo(new BigDecimal("66.00"));
        assertThat(facts.valueOf(ConditionField.CART_ITEM_COUNT)).isEqualTo(4);
        assertThat(facts.valueOf(ConditionField.CUSTOMER_SEGMENT)).isEqualTo(CustomerSegment.VIP);
        assertThat(facts.valueOf(ConditionField.DATE)).isEqualTo(TODAY);

        context.lines().get(0).applyDiscount(new BigDecimal("6.00"));
        context.applyCartDiscount(RULE, new BigDecimal("10.00"));

        assertThat(facts.valueOf(ConditionField.CART_SUBTOTAL)).isEqualTo(new BigDecimal("66.00"));
        assertThat(facts.valueOf(ConditionField.CART_TOTAL)).isEqualTo(new BigDecimal("50.00"));
        assertThatThrownBy(() -> facts.valueOf(ConditionField.LINE_QTY)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startingWithoutShippingOrDateKeepsTheOldBehaviour() {
        PriceBreakdown breakdown = PricingContext.start(List.of(
                new CartLine(MUG, "Mug", "KITCHEN", new BigDecimal("12.00"), 3)), CustomerSegment.REGULAR)
                .toBreakdown();

        assertThat(breakdown.total()).isEqualTo(new BigDecimal("36.00"));
        assertThat(breakdown.shipping()).isEqualTo(new BigDecimal("0.00"));
        assertThat(breakdown.adjustments()).isEmpty();
        assertThat(breakdown.gifts()).isEmpty();
    }
}
