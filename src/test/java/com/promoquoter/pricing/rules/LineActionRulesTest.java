package com.promoquoter.pricing.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.promoquoter.pricing.ActionKind;
import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.CartLine;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.PricingContext;
import com.promoquoter.pricing.PricingRule;
import com.promoquoter.pricing.RuleDefinition;
import com.promoquoter.pricing.TestRules;

class LineActionRulesTest {

    private static final UUID PRODUCT = UUID.randomUUID();

    private static CartLine line(UUID productId, String category, String unitPrice, int qty) {
        return new CartLine(productId, "item", category, new BigDecimal(unitPrice), qty);
    }

    private static PricingContext contextOf(CartLine... lines) {
        return PricingContext.start(List.of(lines), CustomerSegment.REGULAR);
    }

    private static PricingContext single(UUID productId, String unitPrice, int qty) {
        return contextOf(line(productId, "K", unitPrice, qty));
    }

    private static void run(RuleDefinition definition, PricingContext context) {
        PricingRule rule = switch (definition.action().kind()) {
            case PERCENT_OFF -> new PercentOffRuleProvider().create(definition);
            case AMOUNT_OFF -> new AmountOffRuleProvider().create(definition);
            case FREE_UNITS -> new FreeUnitsRuleProvider().create(definition);
            default -> throw new IllegalStateException();
        };
        rule.apply(context);
    }

    @Test
    void percentOffDiscountsOnlyTargetedLines() {
        PricingContext context = contextOf(line(UUID.randomUUID(), "KITCHEN", "12.00", 3),
                line(UUID.randomUUID(), "HOME", "30.00", 1));

        run(TestRules.percentOff("10").target(TestRules.category("KITCHEN")).build(), context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("3.60");
        assertThat(context.lines().get(0).total()).isEqualByComparingTo("32.40");
        assertThat(context.lines().get(1).discount()).isEqualByComparingTo("0.00");
    }

    @Test
    void percentOffRoundsHalfUpToCents() {
        PricingContext context = contextOf(line(UUID.randomUUID(), "KITCHEN", "33.33", 1));

        run(TestRules.percentOff("15").target(TestRules.category("kitchen")).build(), context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("5.00");
        assertThat(context.lines().get(0).total()).isEqualByComparingTo("28.33");
    }

    @Test
    void percentOffRoundsDownBelowTheHalfCent() {
        PricingContext context = contextOf(line(UUID.randomUUID(), "KITCHEN", "10.01", 1));

        run(TestRules.percentOff("15").target(TestRules.category("KITCHEN")).build(), context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("1.50");
    }

    @Test
    void hundredPercentBringsTheLineToZero() {
        PricingContext context = contextOf(line(UUID.randomUUID(), "KITCHEN", "9.99", 2));

        run(TestRules.percentOff("100").target(TestRules.category("KITCHEN")).build(), context);

        assertThat(context.lines().get(0).total()).isEqualByComparingTo("0.00");
    }

    @Test
    void twoFullDiscountsNeverMakeALineNegative() {
        PricingContext context = contextOf(line(UUID.randomUUID(), "KITCHEN", "9.99", 2));

        run(TestRules.percentOff("100").target(TestRules.category("KITCHEN")).build(), context);
        run(TestRules.percentOff("100").target(TestRules.category("KITCHEN")).build(), context);
        run(TestRules.amountOff("5").target(TestRules.category("KITCHEN")).build(), context);

        assertThat(context.lines().get(0).total()).isEqualByComparingTo("0.00");
    }

    @Test
    void aRuleWithoutATargetAffectsEveryLine() {
        PricingContext context = contextOf(line(UUID.randomUUID(), "KITCHEN", "10.00", 1),
                line(UUID.randomUUID(), "HOME", "20.00", 1));

        run(TestRules.percentOff("50").build(), context);

        assertThat(context.lines()).extracting(l -> l.total().toPlainString()).containsExactly("5.00", "10.00");
    }

    @ParameterizedTest(name = "buy {0} get {1} free, qty {2} -> discount {3}")
    @CsvSource({
            "2,1,1,0.00", "2,1,2,0.00", "2,1,3,10.00", "2,1,5,10.00", "2,1,6,20.00", "2,1,7,20.00",
            "1,1,1,0.00", "1,1,2,10.00", "1,1,3,10.00", "1,1,4,20.00",
            "3,2,4,0.00", "3,2,5,20.00", "3,2,10,40.00"
    })
    void freeUnitsFollowTheGroupFormula(int buy, int free, int qty, String expectedDiscount) {
        PricingContext context = single(PRODUCT, "10.00", qty);

        run(TestRules.freeUnits(buy, free).target(TestRules.product(PRODUCT)).build(), context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo(expectedDiscount);
    }

    @Test
    void freeUnitsAreValuedAtTheAlreadyDiscountedPrice() {
        PricingContext context = single(PRODUCT, "12.00", 3);
        context.lines().get(0).applyDiscount(new BigDecimal("3.60"));

        run(TestRules.freeUnits(2, 1).target(TestRules.product(PRODUCT)).build(), context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("14.40");
        assertThat(context.lines().get(0).total()).isEqualByComparingTo("21.60");
    }

    @Test
    void freeUnitsRoundDownBelowTheHalfCentAndUpAtIt() {
        PricingContext below = single(PRODUCT, "1.00", 3);
        below.lines().get(0).applyDiscount(new BigDecimal("0.50"));
        PricingContext at = single(PRODUCT, "0.50", 3);
        at.lines().get(0).applyDiscount(new BigDecimal("0.01"));
        RuleDefinition rule = TestRules.freeUnits(2, 1).target(TestRules.product(PRODUCT)).build();

        run(rule, below);
        run(rule, at);

        assertThat(below.lines().get(0).total()).isEqualByComparingTo("1.67");
        assertThat(at.lines().get(0).total()).isEqualByComparingTo("0.99");
    }

    @Test
    void freeUnitsIgnoreOtherProducts() {
        PricingContext context = single(UUID.randomUUID(), "10.00", 6);

        run(TestRules.freeUnits(2, 1).target(TestRules.product(PRODUCT)).build(), context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest(name = "min {0}, qty {1} -> discount {2}")
    @CsvSource({"10,9,0.00", "10,10,10.00", "10,11,11.00", "10,100,100.00", "2,1,0.00", "2,2,2.00"})
    void aQuantityTargetDiscountsOnlyAtOrAboveTheThreshold(int minQty, int qty, String expectedDiscount) {
        PricingContext context = single(PRODUCT, "10.00", qty);

        run(TestRules.percentOff("10")
                .target(TestRules.all(TestRules.product(PRODUCT), TestRules.minQty(minQty))).build(), context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo(expectedDiscount);
    }

    @Test
    void aPercentDiscountUsesTheRunningTotalLeftByEarlierRules() {
        PricingContext context = single(PRODUCT, "10.00", 10);
        context.lines().get(0).applyDiscount(new BigDecimal("20.00"));

        run(TestRules.percentOff("10")
                .target(TestRules.all(TestRules.product(PRODUCT), TestRules.minQty(10))).build(), context);

        assertThat(context.lines().get(0).total()).isEqualByComparingTo("72.00");
    }

    @Test
    void amountOffTakesAFixedAmountOffEachMatchingLineRegardlessOfQuantity() {
        PricingContext context = contextOf(line(UUID.randomUUID(), "KITCHEN", "10.00", 1),
                line(UUID.randomUUID(), "KITCHEN", "10.00", 4), line(UUID.randomUUID(), "HOME", "10.00", 1));

        run(TestRules.amountOff("2.50").target(TestRules.category("KITCHEN")).build(), context);

        assertThat(context.lines()).extracting(l -> l.discount().toPlainString()).containsExactly("2.50", "2.50", "0.00");
    }

    @Test
    void amountOffIsCappedAtTheLineTotal() {
        PricingContext context = contextOf(line(UUID.randomUUID(), "KITCHEN", "3.00", 1));

        run(TestRules.amountOff("10").target(TestRules.category("KITCHEN")).build(), context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("3.00");
        assertThat(context.lines().get(0).total()).isEqualByComparingTo("0.00");
    }

    @Test
    void ruleStagesDefaultByActionKindAndCanBeOverridden() {
        assertThat(TestRules.percentOff("5").build().stage()).isEqualTo(ActionKind.PERCENT_OFF.defaultStage());
        assertThat(TestRules.freeUnits(1, 1).build().stage()).isEqualTo(ActionKind.FREE_UNITS.defaultStage());
        assertThat(TestRules.amountOff("1").build().stage()).isEqualTo(ActionKind.AMOUNT_OFF.defaultStage());
        assertThat(ActionKind.PERCENT_OFF.defaultStage()).isLessThan(ActionKind.FREE_UNITS.defaultStage());
        assertThat(ActionKind.FREE_UNITS.defaultStage()).isLessThan(ActionKind.AMOUNT_OFF.defaultStage());
        assertThat(new PercentOffRuleProvider().create(TestRules.percentOff("5").stage(300).build()).stage())
                .isEqualTo(300);
    }

    @Test
    void providersReportTheirKindAndRejectAnotherKindsDefinition() {
        assertThat(new PercentOffRuleProvider().kind()).isEqualTo(ActionKind.PERCENT_OFF);
        assertThat(new AmountOffRuleProvider().kind()).isEqualTo(ActionKind.AMOUNT_OFF);
        assertThat(new FreeUnitsRuleProvider().kind()).isEqualTo(ActionKind.FREE_UNITS);
        assertThatThrownBy(() -> new PercentOffRuleProvider().create(TestRules.amountOff("1").build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PERCENT_OFF");
        assertThatThrownBy(() -> new FreeUnitsRuleProvider().create(
                TestRules.of(new ActionSpec.Surcharge(BigDecimal.ONE)).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
