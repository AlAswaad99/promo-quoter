package com.promoquoter.pricing.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.promoquoter.pricing.CartLine;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.PricingContext;
import com.promoquoter.pricing.Promos;

class TieredBulkRuleTest {

    private static final UUID PRODUCT = UUID.randomUUID();

    private static PricingContext contextOf(UUID productId, int qty) {
        return PricingContext.start(List.of(new CartLine(productId, "item", "K", new BigDecimal("10.00"), qty)),
                CustomerSegment.REGULAR);
    }

    @ParameterizedTest(name = "min {0}, qty {1} -> discount {2}")
    @CsvSource({"10,9,0.00", "10,10,10.00", "10,11,11.00", "10,100,100.00", "2,1,0.00", "2,2,2.00"})
    void discountsOnlyAtOrAboveTheThreshold(int minQty, int qty, String expectedDiscount) {
        PricingContext context = contextOf(PRODUCT, qty);

        new TieredBulkRule(Promos.tieredBulk(PRODUCT, minQty, "10").build()).apply(context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo(expectedDiscount);
    }

    @Test
    void discountsTheRunningTotalLeftByEarlierStages() {
        PricingContext context = contextOf(PRODUCT, 10);
        context.lines().get(0).applyDiscount(new BigDecimal("20.00"));

        new TieredBulkRule(Promos.tieredBulk(PRODUCT, 10, "10").build()).apply(context);

        assertThat(context.lines().get(0).total()).isEqualByComparingTo("72.00");
    }

    @Test
    void ignoresOtherProducts() {
        PricingContext context = contextOf(UUID.randomUUID(), 50);

        new TieredBulkRule(Promos.tieredBulk(PRODUCT, 10, "10").build()).apply(context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("0.00");
    }

    @Test
    void runsAfterBuyXGetY() {
        assertThat(TieredBulkRule.STAGE).isGreaterThan(BuyXGetYRule.STAGE);
        assertThat(new TieredBulkRule(Promos.tieredBulk(PRODUCT, 2, "5").build()).stage())
                .isEqualTo(TieredBulkRule.STAGE);
    }
}
