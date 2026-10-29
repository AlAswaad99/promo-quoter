package com.promoquoter.pricing.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.pricing.model.AppliedPromotion;
import com.promoquoter.pricing.model.CartLine;
import com.promoquoter.pricing.model.CustomerSegment;
import com.promoquoter.pricing.rules.PercentOffRuleProvider;

class AuditingRuleTest {

    @Test
    void recordsOneEntryPerDiscountedLineWithTheExactAmount() {
        UUID mug = UUID.randomUUID();
        UUID lamp = UUID.randomUUID();
        PricingContext context = PricingContext.start(List.of(
                new CartLine(mug, "Mug", "KITCHEN", new BigDecimal("10.00"), 1),
                new CartLine(lamp, "Lamp", "HOME", new BigDecimal("20.00"), 1),
                new CartLine(UUID.randomUUID(), "Pan", "KITCHEN", new BigDecimal("40.00"), 1)), CustomerSegment.REGULAR);
        RuleDefinition promotion = Promos.percentOff("KITCHEN", "10").named("Kitchen 10").build();

        new AuditingRule(new PercentOffRuleProvider().create(promotion)).apply(context);

        assertThat(context.appliedPromotions()).hasSize(2);
        assertThat(context.appliedPromotions()).extracting(AppliedPromotion::sequence).containsExactly(1, 2);
        assertThat(context.appliedPromotions()).extracting(AppliedPromotion::promotionId)
                .containsOnly(promotion.id());
        assertThat(context.appliedPromotions()).extracting(AppliedPromotion::productId).contains(mug)
                .doesNotContain(lamp);
        assertThat(context.appliedPromotions().get(0).amount()).isEqualByComparingTo("1.00");
        assertThat(context.appliedPromotions().get(1).amount()).isEqualByComparingTo("4.00");
    }

    @Test
    void recordsNothingWhenTheDelegateChangesNothing() {
        PricingContext context = PricingContext.start(
                List.of(new CartLine(UUID.randomUUID(), "Lamp", "HOME", new BigDecimal("20.00"), 1)),
                CustomerSegment.REGULAR);

        new AuditingRule(new PercentOffRuleProvider().create(Promos.percentOff("KITCHEN", "10").build())).apply(context);

        assertThat(context.appliedPromotions()).isEmpty();
    }

    @Test
    void recordsOnlyTheDiscountActuallyGrantedWhenCapped() {
        PricingContext context = PricingContext.start(
                List.of(new CartLine(UUID.randomUUID(), "Mug", "KITCHEN", new BigDecimal("10.00"), 1)),
                CustomerSegment.REGULAR);
        context.lines().get(0).applyDiscount(new BigDecimal("9.50"));

        new AuditingRule(new PercentOffRuleProvider().create(Promos.percentOff("KITCHEN", "100").build())).apply(context);

        assertThat(context.appliedPromotions()).singleElement()
                .satisfies(applied -> assertThat(applied.amount()).isEqualByComparingTo("0.50"));
    }

    @Test
    void delegatesStageAndDefinition() {
        RuleDefinition promotion = Promos.percentOff("KITCHEN", "10").build();
        AuditingRule rule = new AuditingRule(new PercentOffRuleProvider().create(promotion));

        assertThat(rule.definition()).isSameAs(promotion);
        assertThat(rule.stage()).isEqualTo(LegacyTemplates.PERCENT_OFF_CATEGORY_STAGE);
    }
}
