package com.promoquoter.pricing.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.CartLine;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.PricingContext;
import com.promoquoter.pricing.PricingRule;
import com.promoquoter.pricing.RuleDefinition;
import com.promoquoter.pricing.RuleKind;
import com.promoquoter.pricing.TestRules;
import com.promoquoter.pricing.condition.Condition;
import com.promoquoter.pricing.condition.Condition.Comparison;
import com.promoquoter.pricing.condition.ConditionField;
import com.promoquoter.pricing.condition.Operator;

class GenericRuleTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-15");

    private static PricingContext context(CustomerSegment segment, int qty) {
        return PricingContext.start(List.of(
                new CartLine(UUID.randomUUID(), "Mug", "KITCHEN", new BigDecimal("10.00"), qty),
                new CartLine(UUID.randomUUID(), "Lamp", "HOME", new BigDecimal("20.00"), 1)), segment, TODAY,
                BigDecimal.ZERO);
    }

    private static PricingRule rule(RuleDefinition definition) {
        return new PercentOffRuleProvider().create(definition);
    }

    @Test
    void doesNothingWhenTheCartConditionIsNotMet() {
        PricingContext context = context(CustomerSegment.REGULAR, 2);

        rule(TestRules.percentOff("10").when(TestRules.minSubtotal("100")).build()).apply(context);

        assertThat(context.lines()).allSatisfy(line -> assertThat(line.discount()).isEqualByComparingTo("0.00"));
    }

    @Test
    void appliesWhenTheCartConditionIsMet() {
        PricingContext context = context(CustomerSegment.REGULAR, 2);

        rule(TestRules.percentOff("10").when(TestRules.minSubtotal("40")).build()).apply(context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("2.00");
        assertThat(context.lines().get(1).discount()).isEqualByComparingTo("2.00");
    }

    @Test
    void cartConditionsCanUseSegmentAndDate() {
        Condition vipInNovember = TestRules.all(
                new Comparison(ConditionField.CUSTOMER_SEGMENT, Operator.EQ, CustomerSegment.VIP),
                new Comparison(ConditionField.DATE, Operator.BETWEEN,
                        List.of(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"))));
        RuleDefinition definition = TestRules.percentOff("10").when(vipInNovember).build();
        PricingContext vip = context(CustomerSegment.VIP, 1);
        PricingContext regular = context(CustomerSegment.REGULAR, 1);

        rule(definition).apply(vip);
        rule(definition).apply(regular);

        assertThat(vip.lines().get(0).discount()).isEqualByComparingTo("1.00");
        assertThat(regular.lines().get(0).discount()).isEqualByComparingTo("0.00");
    }

    @Test
    void cartConditionsSeeTheRunningTotalAfterEarlierDiscounts() {
        PricingContext context = context(CustomerSegment.REGULAR, 2);
        context.lines().get(0).applyDiscount(new BigDecimal("15.00"));
        Condition totalAtLeast30 = new Comparison(ConditionField.CART_TOTAL, Operator.GTE, new BigDecimal("30"));

        rule(TestRules.percentOff("10").when(totalAtLeast30).build()).apply(context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("15.00");
    }

    @Test
    void targetConditionsPickLinesByQuantityAndProductFields() {
        PricingContext context = context(CustomerSegment.REGULAR, 5);
        Condition bigKitchenLines = TestRules.all(TestRules.category("kitchen"), TestRules.minQty(5));

        rule(TestRules.percentOff("10").target(bigKitchenLines).build()).apply(context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("5.00");
        assertThat(context.lines().get(1).discount()).isEqualByComparingTo("0.00");
    }

    @Test
    void frozenLinesAreSkipped() {
        PricingContext context = context(CustomerSegment.REGULAR, 2);
        context.freeze(context.lines().get(0));

        rule(TestRules.percentOff("10").build()).apply(context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("0.00");
        assertThat(context.lines().get(1).discount()).isEqualByComparingTo("2.00");
    }

    @Test
    void exposesItsDefinitionAndStage() {
        RuleDefinition definition = TestRules.percentOff("10").named("Ten").stage(150).build();

        PricingRule rule = rule(definition);

        assertThat(rule.definition().name()).isEqualTo("Ten");
        assertThat(rule.stage()).isEqualTo(150);
        assertThat(((GenericRule) rule).ruleDefinition()).isEqualTo(definition);
    }

    @Test
    void definitionsRejectAnActionOfTheWrongRuleKind() {
        assertThatThrownBy(() -> new RuleDefinition(UUID.randomUUID(),
                com.promoquoter.pricing.PromotionType.PERCENT_OFF_CATEGORY, "x", RuleKind.VALIDATION, true, 0, 900,
                false, java.time.Instant.now(), java.util.Set.of(), null, null,
                new ActionSpec.PercentOff(BigDecimal.TEN))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleDefinition(UUID.randomUUID(),
                com.promoquoter.pricing.PromotionType.PERCENT_OFF_CATEGORY, "x", RuleKind.PRICING, true, 0, 100,
                false, java.time.Instant.now(), java.util.Set.of(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void definitionsAreEligibleByActivityAndSegment() {
        RuleDefinition everyone = TestRules.percentOff("5").build();
        RuleDefinition vipOnly = TestRules.percentOff("5").forSegments(CustomerSegment.VIP).build();
        RuleDefinition off = TestRules.percentOff("5").inactive().build();

        assertThat(everyone.isEligibleFor(CustomerSegment.REGULAR)).isTrue();
        assertThat(vipOnly.isEligibleFor(CustomerSegment.VIP)).isTrue();
        assertThat(vipOnly.isEligibleFor(CustomerSegment.REGULAR)).isFalse();
        assertThat(off.isEligibleFor(CustomerSegment.VIP)).isFalse();
    }
}
