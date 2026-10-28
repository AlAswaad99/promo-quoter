package com.promoquoter.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.pricing.rules.BuyXGetYRuleProvider;
import com.promoquoter.pricing.rules.PercentOffCategoryRuleProvider;
import com.promoquoter.pricing.rules.TieredBulkRuleProvider;

class PromotionRuleFactoryTest {

    private final PromotionRuleFactory factory = new PromotionRuleFactory(
            List.of(new PercentOffCategoryRuleProvider(), new BuyXGetYRuleProvider(), new TieredBulkRuleProvider()));

    @Test
    void buildsOneAuditedRulePerEligibleDefinition() {
        UUID product = UUID.randomUUID();
        List<PricingRule> rules = factory.create(List.of(Promos.percentOff("K", "10").build(),
                Promos.buyXGetY(product, 2, 1).build()), CustomerSegment.REGULAR);

        assertThat(rules).hasSize(2).allSatisfy(rule -> assertThat(rule).isInstanceOf(AuditingRule.class));
        assertThat(rules).extracting(PricingRule::stage).containsExactly(100, 200);
    }

    @Test
    void skipsInactivePromotions() {
        List<PricingRule> rules = factory.create(List.of(Promos.percentOff("K", "10").inactive().build()),
                CustomerSegment.REGULAR);

        assertThat(rules).isEmpty();
    }

    @Test
    void skipsPromotionsRestrictedToOtherSegments() {
        PromotionDefinition vipOnly = Promos.percentOff("K", "10").forSegments(CustomerSegment.VIP).build();

        assertThat(factory.create(List.of(vipOnly), CustomerSegment.REGULAR)).isEmpty();
        assertThat(factory.create(List.of(vipOnly), CustomerSegment.VIP)).hasSize(1);
    }

    @Test
    void promotionsWithoutSegmentRestrictionApplyToEveryone() {
        PromotionDefinition everyone = Promos.percentOff("K", "10").build();

        for (CustomerSegment segment : CustomerSegment.values()) {
            assertThat(factory.create(List.of(everyone), segment)).hasSize(1);
        }
    }

    @Test
    void supportsSeveralSegmentsOnOnePromotion() {
        PromotionDefinition staff = Promos.percentOff("K", "10")
                .forSegments(CustomerSegment.VIP, CustomerSegment.EMPLOYEE).build();

        assertThat(factory.create(List.of(staff), CustomerSegment.EMPLOYEE)).hasSize(1);
        assertThat(factory.create(List.of(staff), CustomerSegment.REGULAR)).isEmpty();
    }

    @Test
    void failsFastWhenAPromotionTypeHasNoProvider() {
        assertThatThrownBy(() -> new PromotionRuleFactory(List.of(new PercentOffCategoryRuleProvider())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BUY_X_GET_Y");
    }

    @Test
    void failsFastWhenAPromotionTypeHasTwoProviders() {
        assertThatThrownBy(() -> new PromotionRuleFactory(List.of(new PercentOffCategoryRuleProvider(),
                new PercentOffCategoryRuleProvider(), new BuyXGetYRuleProvider(), new TieredBulkRuleProvider())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PERCENT_OFF_CATEGORY");
    }

    @Test
    void providersRejectDefinitionsMissingTheirParameters() {
        PromotionDefinition incomplete = new PromotionDefinition(UUID.randomUUID(), PromotionType.BUY_X_GET_Y, "x",
                true, 0, java.time.Instant.now(), java.util.Set.of(), null, null, null, null, null, null);

        assertThatThrownBy(() -> new BuyXGetYRuleProvider().create(incomplete))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PercentOffCategoryRuleProvider().create(incomplete))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TieredBulkRuleProvider().create(incomplete))
                .isInstanceOf(NullPointerException.class);
    }
}
