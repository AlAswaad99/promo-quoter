package com.promoquoter.pricing.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.pricing.ActionKind;
import com.promoquoter.pricing.ActionRuleProvider;
import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.AdjustmentDirection;
import com.promoquoter.pricing.CartLine;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.GiftRequest;
import com.promoquoter.pricing.PriceBreakdown;
import com.promoquoter.pricing.PricingContext;
import com.promoquoter.pricing.PricingRule;
import com.promoquoter.pricing.RuleDefinition;
import com.promoquoter.pricing.TestRules;

class CartActionRulesTest {

    private static final UUID MUG = UUID.randomUUID();
    private static final UUID LAMP = UUID.randomUUID();
    private static final UUID GIFT = UUID.randomUUID();

    private static PricingContext context(String shippingFee) {
        return PricingContext.start(List.of(
                new CartLine(MUG, "Mug", "KITCHEN", new BigDecimal("12.00"), 3),
                new CartLine(LAMP, "Lamp", "HOME", new BigDecimal("30.00"), 1)), CustomerSegment.REGULAR,
                LocalDate.parse("2026-11-15"), new BigDecimal(shippingFee));
    }

    private static PricingRule rule(RuleDefinition definition) {
        ActionRuleProvider provider = switch (definition.action().kind()) {
            case AMOUNT_OFF_CART -> new AmountOffCartRuleProvider();
            case SURCHARGE -> new SurchargeRuleProvider();
            case SHIPPING_WAIVER -> new ShippingWaiverRuleProvider();
            case FREE_GIFT -> new FreeGiftRuleProvider();
            case REJECT -> new RejectRuleProvider();
            default -> throw new IllegalStateException();
        };
        return provider.create(definition);
    }

    private static RuleDefinition amountOffCart(String amount) {
        return TestRules.of(new ActionSpec.AmountOffCart(new BigDecimal(amount))).build();
    }

    @Test
    void amountOffCartReducesTheGoodsTotalOnce() {
        PricingContext context = context("0");

        rule(TestRules.of(new ActionSpec.AmountOffCart(new BigDecimal("10"))).target(TestRules.category("KITCHEN")).build())
                .apply(context);
        PriceBreakdown breakdown = context.toBreakdown();

        assertThat(breakdown.total()).isEqualTo(new BigDecimal("56.00"));
        assertThat(breakdown.totalDiscount()).isEqualTo(new BigDecimal("10.00"));
        assertThat(breakdown.adjustments()).hasSize(1);
        assertThat(breakdown.adjustments().get(0).direction()).isEqualTo(AdjustmentDirection.DISCOUNT);
        assertThat(breakdown.lines()).allSatisfy(line -> assertThat(line.discount()).isEqualTo(new BigDecimal("0.00")));
    }

    @Test
    void amountOffCartNeverTakesMoreThanTheCartIsWorth() {
        PricingContext context = context("0");

        rule(amountOffCart("1000")).apply(context);

        assertThat(context.toBreakdown().total()).isEqualTo(new BigDecimal("0.00"));
        assertThat(context.adjustments().get(0).amount()).isEqualTo(new BigDecimal("66.00"));
    }

    @Test
    void surchargeAddsToTheTotalAsACharge() {
        PricingContext context = context("0");

        rule(TestRules.of(new ActionSpec.Surcharge(new BigDecimal("2.50"))).when(TestRules.minSubtotal("50")).build())
                .apply(context);

        assertThat(context.toBreakdown().total()).isEqualTo(new BigDecimal("68.50"));
        assertThat(context.adjustments().get(0).direction()).isEqualTo(AdjustmentDirection.CHARGE);
    }

    @Test
    void surchargeDoesNotFireWhenTheCartConditionFails() {
        PricingContext context = context("0");

        rule(TestRules.of(new ActionSpec.Surcharge(new BigDecimal("2.50"))).when(TestRules.minSubtotal("500")).build())
                .apply(context);

        assertThat(context.adjustments()).isEmpty();
        assertThat(context.toBreakdown().total()).isEqualTo(new BigDecimal("66.00"));
    }

    @Test
    void shippingWaiverZeroesTheShippingFee() {
        PricingContext context = context("5.00");

        rule(TestRules.of(new ActionSpec.ShippingWaiver()).when(TestRules.minSubtotal("50")).build()).apply(context);

        assertThat(context.toBreakdown().shipping()).isEqualTo(new BigDecimal("0.00"));
        assertThat(context.toBreakdown().total()).isEqualTo(new BigDecimal("66.00"));
        assertThat(context.adjustments().get(0).action()).isEqualTo(ActionKind.SHIPPING_WAIVER);
    }

    @Test
    void shippingWaiverWithoutAShippingFeeRecordsNothing() {
        PricingContext context = context("0");

        rule(TestRules.of(new ActionSpec.ShippingWaiver()).build()).apply(context);

        assertThat(context.adjustments()).isEmpty();
    }

    @Test
    void aCartRuleWithATargetNeedsAtLeastOneMatchingLineAndFiresOnlyOnce() {
        PricingContext matching = context("0");
        PricingContext none = context("0");
        RuleDefinition surcharge = TestRules.of(new ActionSpec.Surcharge(BigDecimal.ONE))
                .target(TestRules.category("KITCHEN")).build();
        RuleDefinition everyLine = TestRules.of(new ActionSpec.Surcharge(BigDecimal.ONE)).build();
        RuleDefinition missing = TestRules.of(new ActionSpec.Surcharge(BigDecimal.ONE))
                .target(TestRules.category("TOYS")).build();

        rule(surcharge).apply(matching);
        rule(everyLine).apply(matching);
        rule(missing).apply(none);

        assertThat(matching.adjustments()).hasSize(2);
        assertThat(none.adjustments()).isEmpty();
    }

    @Test
    void frozenLinesDoNotCountAsMatchingTargets() {
        PricingContext context = context("0");
        context.freeze(context.lines().get(0));

        rule(TestRules.of(new ActionSpec.Surcharge(BigDecimal.ONE)).target(TestRules.category("KITCHEN")).build())
                .apply(context);

        assertThat(context.adjustments()).isEmpty();
    }

    @Test
    void freeGiftRequestsTheGiftProductAndQuantity() {
        PricingContext context = context("0");

        rule(TestRules.of(new ActionSpec.FreeGift(GIFT, 2)).target(TestRules.product(MUG)).build()).apply(context);

        assertThat(context.gifts()).extracting(GiftRequest::productId, GiftRequest::qty).containsExactly(
                org.assertj.core.groups.Tuple.tuple(GIFT, 2));
        assertThat(context.toBreakdown().total()).isEqualTo(new BigDecimal("66.00"));
        assertThat(context.skippedRules()).isEmpty();
    }

    @Test
    void freeGiftRecordsASkipWhenTheOrderLimitIsReached() {
        PricingContext context = context("0");

        rule(TestRules.of(new ActionSpec.FreeGift(GIFT, 100)).build()).apply(context);
        rule(TestRules.of(new ActionSpec.FreeGift(GIFT, 5)).build()).apply(context);

        assertThat(context.gifts()).extracting(GiftRequest::qty).containsExactly(100);
        assertThat(context.skippedRules()).hasSize(1);
        assertThat(context.skippedRules().get(0).reason()).contains("100");
    }

    @Test
    void rejectRecordsAViolationWithTheAuthorsMessageWhenItsConditionsMatch() {
        PricingContext context = context("0");
        RuleDefinition tooLittle = TestRules.of(new ActionSpec.Reject("Minimum order is 100"))
                .named("Minimum order").when(new com.promoquoter.pricing.condition.Condition.Comparison(
                        com.promoquoter.pricing.condition.ConditionField.CART_TOTAL,
                        com.promoquoter.pricing.condition.Operator.LT, new BigDecimal("100"))).build();

        rule(tooLittle).apply(context);

        assertThat(context.violations()).hasSize(1);
        assertThat(context.violations().get(0).name()).isEqualTo("Minimum order");
        assertThat(context.violations().get(0).message()).isEqualTo("Minimum order is 100");
    }

    @Test
    void rejectDoesNothingWhenTheViolationConditionIsNotMet() {
        PricingContext context = context("0");

        rule(TestRules.of(new ActionSpec.Reject("Too many mugs")).target(TestRules.all(TestRules.product(MUG),
                TestRules.minQty(10))).build()).apply(context);

        assertThat(context.violations()).isEmpty();
    }

    @Test
    void rejectCanTargetALineByQuantity() {
        PricingContext context = context("0");

        rule(TestRules.of(new ActionSpec.Reject("At most 2 mugs")).target(TestRules.all(TestRules.product(MUG),
                TestRules.minQty(3))).build()).apply(context);

        assertThat(context.violations()).hasSize(1);
    }

    @Test
    void providersReportTheirKindAndRejectAnotherKindsDefinition() {
        assertThat(new AmountOffCartRuleProvider().kind()).isEqualTo(ActionKind.AMOUNT_OFF_CART);
        assertThat(new SurchargeRuleProvider().kind()).isEqualTo(ActionKind.SURCHARGE);
        assertThat(new ShippingWaiverRuleProvider().kind()).isEqualTo(ActionKind.SHIPPING_WAIVER);
        assertThat(new FreeGiftRuleProvider().kind()).isEqualTo(ActionKind.FREE_GIFT);
        assertThat(new RejectRuleProvider().kind()).isEqualTo(ActionKind.REJECT);
        assertThatThrownBy(() -> new RejectRuleProvider().create(amountOffCart("1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SurchargeRuleProvider().create(amountOffCart("1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validationRulesRunAfterEveryPricingStageByDefault() {
        assertThat(ActionKind.REJECT.defaultStage()).isGreaterThan(ActionKind.SURCHARGE.defaultStage());
        assertThat(ActionKind.REJECT.defaultStage()).isGreaterThan(ActionKind.MAX_PRICING_STAGE);
        assertThat(ActionKind.AMOUNT_OFF_CART.defaultStage()).isGreaterThan(ActionKind.AMOUNT_OFF.defaultStage());
    }
}
