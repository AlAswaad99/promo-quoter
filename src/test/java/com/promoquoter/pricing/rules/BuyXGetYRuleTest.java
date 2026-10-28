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

class BuyXGetYRuleTest {

    private static final UUID PRODUCT = UUID.randomUUID();

    private static PricingContext contextOf(UUID productId, String unitPrice, int qty) {
        return PricingContext.start(List.of(new CartLine(productId, "item", "K", new BigDecimal(unitPrice), qty)),
                CustomerSegment.REGULAR);
    }

    @ParameterizedTest(name = "buy {0} get {1} free, qty {2} -> discount {3}")
    @CsvSource({
            "2,1,1,0.00", "2,1,2,0.00", "2,1,3,10.00", "2,1,5,10.00", "2,1,6,20.00", "2,1,7,20.00",
            "1,1,1,0.00", "1,1,2,10.00", "1,1,3,10.00", "1,1,4,20.00",
            "3,2,4,0.00", "3,2,5,20.00", "3,2,10,40.00"
    })
    void freeUnitsFollowTheGroupFormula(int buy, int free, int qty, String expectedDiscount) {
        PricingContext context = contextOf(PRODUCT, "10.00", qty);

        new BuyXGetYRule(Promos.buyXGetY(PRODUCT, buy, free).build()).apply(context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo(expectedDiscount);
    }

    @Test
    void valuesFreeUnitsAtTheAlreadyDiscountedPrice() {
        PricingContext context = contextOf(PRODUCT, "12.00", 3);
        context.lines().get(0).applyDiscount(new BigDecimal("3.60"));

        new BuyXGetYRule(Promos.buyXGetY(PRODUCT, 2, 1).build()).apply(context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("14.40");
        assertThat(context.lines().get(0).total()).isEqualByComparingTo("21.60");
    }

    @Test
    void roundsDownBelowTheHalfCent() {
        PricingContext context = contextOf(PRODUCT, "1.00", 3);
        context.lines().get(0).applyDiscount(new BigDecimal("0.50"));

        new BuyXGetYRule(Promos.buyXGetY(PRODUCT, 2, 1).build()).apply(context);

        assertThat(context.lines().get(0).total()).isEqualByComparingTo("1.67");
    }

    @Test
    void roundsHalfUpAtTheHalfCent() {
        PricingContext context = contextOf(PRODUCT, "0.50", 3);
        context.lines().get(0).applyDiscount(new BigDecimal("0.01"));

        new BuyXGetYRule(Promos.buyXGetY(PRODUCT, 2, 1).build()).apply(context);

        assertThat(context.lines().get(0).total()).isEqualByComparingTo("0.99");
    }

    @Test
    void ignoresOtherProducts() {
        PricingContext context = contextOf(UUID.randomUUID(), "10.00", 6);

        new BuyXGetYRule(Promos.buyXGetY(PRODUCT, 2, 1).build()).apply(context);

        assertThat(context.lines().get(0).discount()).isEqualByComparingTo("0.00");
    }

    @Test
    void runsAfterTheCategoryStage() {
        assertThat(new BuyXGetYRule(Promos.buyXGetY(PRODUCT, 1, 1).build()).stage()).isEqualTo(BuyXGetYRule.STAGE);
    }
}
