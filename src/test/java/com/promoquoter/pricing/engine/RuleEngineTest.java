package com.promoquoter.pricing.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.pricing.condition.Condition.Comparison;
import com.promoquoter.pricing.condition.Condition;
import com.promoquoter.pricing.condition.ConditionField;
import com.promoquoter.pricing.condition.Operator;
import com.promoquoter.pricing.model.ActionSpec;
import com.promoquoter.pricing.model.AdjustmentDirection;
import com.promoquoter.pricing.model.AppliedPromotion;
import com.promoquoter.pricing.model.CartAdjustment;
import com.promoquoter.pricing.model.CartLine;
import com.promoquoter.pricing.model.CustomerSegment;
import com.promoquoter.pricing.model.GiftRequest;
import com.promoquoter.pricing.model.PriceBreakdown;
import com.promoquoter.pricing.model.RuleViolation;

class RuleEngineTest {

    private static final UUID MUG = UUID.randomUUID();
    private static final UUID LAMP = UUID.randomUUID();
    private static final UUID GIFT = UUID.randomUUID();
    private static final Clock NOVEMBER = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC);

    private final PricingEngine engine = new PricingEngine(TestProviders.factory(), NOVEMBER, new BigDecimal("5.00"));

    private static CartLine mugs(int qty) {
        return new CartLine(MUG, "Mug", "KITCHEN", new BigDecimal("12.00"), qty);
    }

    private static CartLine lamp() {
        return new CartLine(LAMP, "Lamp", "HOME", new BigDecimal("30.00"), 1);
    }

    private PriceBreakdown price(List<CartLine> lines, CustomerSegment segment, RuleDefinition... rules) {
        return engine.price(lines, segment, List.of(rules));
    }

    @Test
    void theWorkedExampleStillPricesTheSameThroughGenericRules() {
        PriceBreakdown breakdown = new PricingEngine(TestProviders.factory()).price(List.of(mugs(3)),
                CustomerSegment.REGULAR, List.of(Promos.buyXGetY(MUG, 2, 1).build(),
                        Promos.percentOff("KITCHEN", "10").build()));

        assertThat(breakdown.total()).isEqualByComparingTo("21.60");
        assertThat(breakdown.totalDiscount()).isEqualByComparingTo("14.40");
        assertThat(breakdown.shipping()).isEqualByComparingTo("0.00");
        assertThat(breakdown.adjustments()).isEmpty();
    }

    @Test
    void flatShippingIsAddedToTheTotalAndAShippingWaiverRemovesIt() {
        PriceBreakdown charged = price(List.of(mugs(3)), CustomerSegment.REGULAR);
        PriceBreakdown waived = price(List.of(mugs(3)), CustomerSegment.REGULAR,
                TestRules.of(new ActionSpec.ShippingWaiver()).when(TestRules.minSubtotal("30")).build());

        assertThat(charged.shipping()).isEqualByComparingTo("5.00");
        assertThat(charged.total()).isEqualByComparingTo("41.00");
        assertThat(waived.shipping()).isEqualByComparingTo("0.00");
        assertThat(waived.total()).isEqualByComparingTo("36.00");
        assertThat(waived.adjustments()).hasSize(1);
    }

    @Test
    void theClockDecidesWhetherADateWindowIsOpen() {
        Condition november = new Comparison(ConditionField.DATE, Operator.BETWEEN,
                List.of(java.time.LocalDate.parse("2026-11-01"), java.time.LocalDate.parse("2026-11-30")));
        Condition december = new Comparison(ConditionField.DATE, Operator.GTE, java.time.LocalDate.parse("2026-12-01"));

        PriceBreakdown breakdown = price(List.of(mugs(1)), CustomerSegment.REGULAR,
                TestRules.percentOff("50").when(november).build(),
                TestRules.amountOff("1").when(december).build());

        assertThat(breakdown.lines().get(0).total()).isEqualByComparingTo("6.00");
    }

    @Test
    void cartLevelRulesRunAfterLineRulesInStageOrder() {
        PriceBreakdown breakdown = price(List.of(mugs(3), lamp()), CustomerSegment.REGULAR,
                TestRules.of(new ActionSpec.Surcharge(new BigDecimal("3.00"))).build(),
                TestRules.of(new ActionSpec.AmountOffCart(new BigDecimal("6.00"))).build(),
                TestRules.percentOff("50").target(TestRules.category("KITCHEN")).build());

        assertThat(breakdown.subtotal()).isEqualByComparingTo("66.00");
        assertThat(breakdown.totalDiscount()).isEqualByComparingTo("24.00");
        assertThat(breakdown.total()).isEqualByComparingTo("50.00");
        assertThat(breakdown.appliedPromotions()).extracting(AppliedPromotion::sequence).containsExactly(1);
        assertThat(breakdown.adjustments()).extracting(CartAdjustment::sequence).containsExactly(2, 3);
        assertThat(breakdown.adjustments()).extracting(CartAdjustment::direction)
                .containsExactly(AdjustmentDirection.DISCOUNT, AdjustmentDirection.CHARGE);
    }

    @Test
    void aCartConditionSeesTheDiscountedTotalFromEarlierRules() {
        Condition totalBelow30 = new Comparison(ConditionField.CART_TOTAL, Operator.LT, new BigDecimal("30"));

        PriceBreakdown breakdown = price(List.of(mugs(3)), CustomerSegment.REGULAR,
                TestRules.percentOff("50").build(),
                TestRules.of(new ActionSpec.Surcharge(new BigDecimal("2.00"))).when(totalBelow30).build());

        assertThat(breakdown.total()).isEqualByComparingTo("25.00");
    }

    @Test
    void stopAfterOnALineRuleFreezesOnlyTheLinesItDiscounted() {
        PriceBreakdown breakdown = price(List.of(mugs(3), lamp()), CustomerSegment.REGULAR,
                TestRules.percentOff("50").target(TestRules.category("KITCHEN")).stopAfter().build(),
                TestRules.percentOff("10").stage(150).build());

        assertThat(breakdown.lines().get(0).total()).isEqualByComparingTo("18.00");
        assertThat(breakdown.lines().get(1).total()).isEqualByComparingTo("27.00");
        assertThat(breakdown.skippedRules()).isEmpty();
    }

    @Test
    void stopAfterOnACartRuleStopsLaterPricingRulesAndRecordsTheSkip() {
        RuleDefinition stopper = TestRules.of(new ActionSpec.AmountOffCart(new BigDecimal("5.00")))
                .named("Exclusive voucher").stopAfter().build();
        RuleDefinition later = TestRules.of(new ActionSpec.Surcharge(new BigDecimal("2.00"))).named("Fee").build();

        PriceBreakdown breakdown = price(List.of(mugs(3)), CustomerSegment.REGULAR, stopper, later);

        assertThat(breakdown.total()).isEqualByComparingTo("36.00");
        assertThat(breakdown.adjustments()).hasSize(1);
        assertThat(breakdown.skippedRules()).hasSize(1);
        assertThat(breakdown.skippedRules().get(0).name()).isEqualTo("Fee");
        assertThat(breakdown.skippedRules().get(0).reason()).contains("Exclusive voucher");
    }

    @Test
    void aStopAfterRuleThatChangesNothingStopsNothing() {
        RuleDefinition noEffect = TestRules.of(new ActionSpec.AmountOffCart(new BigDecimal("5.00")))
                .when(TestRules.minSubtotal("1000")).stopAfter().build();

        PriceBreakdown breakdown = price(List.of(mugs(3)), CustomerSegment.REGULAR, noEffect,
                TestRules.of(new ActionSpec.Surcharge(new BigDecimal("2.00"))).build());

        assertThat(breakdown.total()).isEqualByComparingTo("43.00");
        assertThat(breakdown.skippedRules()).isEmpty();
    }

    @Test
    void validationRulesStillRunAfterAStop() {
        RuleDefinition stopper = TestRules.of(new ActionSpec.AmountOffCart(new BigDecimal("5.00"))).stopAfter().build();
        RuleDefinition validation = TestRules.of(new ActionSpec.Reject("Minimum order is 100")).build();

        PriceBreakdown breakdown = price(List.of(mugs(3)), CustomerSegment.REGULAR, stopper, validation);

        assertThat(breakdown.violations()).hasSize(1);
        assertThat(breakdown.skippedRules()).isEmpty();
    }

    @Test
    void validationRulesSeeTheFinalDiscountedTotalAndCollectEveryViolation() {
        Condition totalBelow30 = new Comparison(ConditionField.CART_TOTAL, Operator.LT, new BigDecimal("30"));
        Condition totalBelow100 = new Comparison(ConditionField.CART_TOTAL, Operator.LT, new BigDecimal("100"));

        PriceBreakdown breakdown = price(List.of(mugs(3)), CustomerSegment.REGULAR,
                TestRules.percentOff("50").build(),
                TestRules.of(new ActionSpec.Reject("Needs at least 30")).named("Min 30").when(totalBelow30).build(),
                TestRules.of(new ActionSpec.Reject("Needs at least 100")).named("Min 100").when(totalBelow100).build());

        assertThat(breakdown.violations()).extracting(RuleViolation::name).containsExactlyInAnyOrder("Min 30", "Min 100");
    }

    @Test
    void giftRulesProduceGiftRequestsButNeverChangeThePrice() {
        PriceBreakdown breakdown = price(List.of(mugs(3)), CustomerSegment.REGULAR,
                TestRules.of(new ActionSpec.FreeGift(GIFT, 1)).target(TestRules.product(MUG)).build());

        assertThat(breakdown.gifts()).extracting(GiftRequest::productId, GiftRequest::qty)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(GIFT, 1));
        assertThat(breakdown.total()).isEqualByComparingTo("41.00");
    }

    @Test
    void rulesRestrictedToASegmentAreIgnoredForOthers() {
        RuleDefinition vipOnly = TestRules.percentOff("50").forSegments(CustomerSegment.VIP).build();

        assertThat(price(List.of(mugs(1)), CustomerSegment.REGULAR, vipOnly).lines().get(0).total())
                .isEqualByComparingTo("12.00");
        assertThat(price(List.of(mugs(1)), CustomerSegment.VIP, vipOnly).lines().get(0).total())
                .isEqualByComparingTo("6.00");
    }

    @Test
    void definitionOrderDoesNotChangeTheResult() {
        List<RuleDefinition> rules = List.of(
                TestRules.of(new ActionSpec.Surcharge(new BigDecimal("3.00"))).build(),
                TestRules.of(new ActionSpec.AmountOffCart(new BigDecimal("6.00"))).build(),
                TestRules.percentOff("50").target(TestRules.category("KITCHEN")).build());

        PriceBreakdown forward = engine.price(List.of(mugs(3), lamp()), CustomerSegment.REGULAR, rules);
        PriceBreakdown reversed = engine.price(List.of(mugs(3), lamp()), CustomerSegment.REGULAR,
                List.of(rules.get(2), rules.get(1), rules.get(0)));

        assertThat(forward.total()).isEqualByComparingTo(reversed.total());
        assertThat(forward.adjustments()).extracting(CartAdjustment::action)
                .isEqualTo(reversed.adjustments().stream().map(CartAdjustment::action).toList());
    }

    @Test
    void anEmptyCartWithNoRulesStillCarriesShippingOnly() {
        PriceBreakdown breakdown = price(List.of(), CustomerSegment.REGULAR);

        assertThat(breakdown.subtotal()).isEqualByComparingTo("0.00");
        assertThat(breakdown.total()).isEqualByComparingTo("5.00");
    }
}
