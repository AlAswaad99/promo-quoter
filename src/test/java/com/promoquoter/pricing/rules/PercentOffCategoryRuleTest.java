package com.promoquoter.pricing.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.pricing.CartLine;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.PricingContext;
import com.promoquoter.pricing.Promos;

class PercentOffCategoryRuleTest {

    private static CartLine line(String category, String unitPrice, int qty) {
        return new CartLine(UUID.randomUUID(), "item", category, new BigDecimal(unitPrice), qty);
    }

    private static PricingContext contextOf(CartLine... lines) {
        return PricingContext.start(List.of(lines), CustomerSegment.REGULAR);
    }

    @Test
    void discountsOnlyLinesInTheCategory() {
        PricingContext context = contextOf(line("KITCHEN", "12.00", 3), line("HOME", "30.00", 1));

        new PercentOffCategoryRule(Promos.percentOff("KITCHEN", "10").build()).apply(context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("3.60");
        assertThat(context.lines().get(0).total()).isEqualByComparingTo("32.40");
        assertThat(context.lines().get(1).discount()).isEqualByComparingTo("0.00");
        assertThat(context.lines().get(1).total()).isEqualByComparingTo("30.00");
    }

    @Test
    void roundsHalfUpToCents() {
        PricingContext context = contextOf(line("KITCHEN", "33.33", 1));

        new PercentOffCategoryRule(Promos.percentOff("KITCHEN", "15").build()).apply(context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("5.00");
        assertThat(context.lines().get(0).total()).isEqualByComparingTo("28.33");
    }

    @Test
    void roundsDownBelowTheHalfCent() {
        PricingContext context = contextOf(line("KITCHEN", "10.01", 1));

        new PercentOffCategoryRule(Promos.percentOff("KITCHEN", "15").build()).apply(context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("1.50");
    }

    @Test
    void hundredPercentBringsTheLineToZero() {
        PricingContext context = contextOf(line("KITCHEN", "9.99", 2));

        new PercentOffCategoryRule(Promos.percentOff("KITCHEN", "100").build()).apply(context);

        assertThat(context.lines().get(0).total()).isEqualByComparingTo("0.00");
    }

    @Test
    void leavesNonMatchingCartsUntouched() {
        PricingContext context = contextOf(line("HOME", "30.00", 1));

        new PercentOffCategoryRule(Promos.percentOff("KITCHEN", "10").build()).apply(context);

        assertThat(context.lines().get(0).total()).isEqualByComparingTo("30.00");
    }

    @Test
    void runsInTheCategoryStage() {
        assertThat(new PercentOffCategoryRule(Promos.percentOff("K", "10").build()).stage())
                .isEqualTo(PercentOffCategoryRule.STAGE);
    }
}
