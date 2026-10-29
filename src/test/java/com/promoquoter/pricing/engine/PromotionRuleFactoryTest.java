package com.promoquoter.pricing.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.pricing.model.ActionKind;
import com.promoquoter.pricing.model.ActionSpec;
import com.promoquoter.pricing.model.CustomerSegment;
import com.promoquoter.pricing.rules.PercentOffRuleProvider;

class PromotionRuleFactoryTest {

    private final PromotionRuleFactory factory = TestProviders.factory();

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
        RuleDefinition vipOnly = Promos.percentOff("K", "10").forSegments(CustomerSegment.VIP).build();

        assertThat(factory.create(List.of(vipOnly), CustomerSegment.REGULAR)).isEmpty();
        assertThat(factory.create(List.of(vipOnly), CustomerSegment.VIP)).hasSize(1);
    }

    @Test
    void promotionsWithoutSegmentRestrictionApplyToEveryone() {
        RuleDefinition everyone = Promos.percentOff("K", "10").build();

        for (CustomerSegment segment : CustomerSegment.values()) {
            assertThat(factory.create(List.of(everyone), segment)).hasSize(1);
        }
    }

    @Test
    void supportsSeveralSegmentsOnOnePromotion() {
        RuleDefinition staff = Promos.percentOff("K", "10")
                .forSegments(CustomerSegment.VIP, CustomerSegment.EMPLOYEE).build();

        assertThat(factory.create(List.of(staff), CustomerSegment.EMPLOYEE)).hasSize(1);
        assertThat(factory.create(List.of(staff), CustomerSegment.REGULAR)).isEmpty();
    }

    @Test
    void failsFastWhenAnActionKindHasNoProvider() {
        assertThatThrownBy(() -> new PromotionRuleFactory(List.of(new PercentOffRuleProvider())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FREE_UNITS")
                .hasMessageContaining("REJECT");
    }

    @Test
    void failsFastWhenAnActionKindHasTwoProviders() {
        List<ActionRuleProvider> providers = new java.util.ArrayList<>(TestProviders.all());
        providers.add(new PercentOffRuleProvider());

        assertThatThrownBy(() -> new PromotionRuleFactory(providers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PERCENT_OFF");
    }

    @Test
    void theDefaultProviderSetCoversEveryActionKind() {
        assertThat(TestProviders.all()).extracting(ActionRuleProvider::kind)
                .containsExactlyInAnyOrder(ActionKind.values());
    }

    @Test
    void buildsRulesForEveryActionKindFromDefinitions() {
        List<RuleDefinition> definitions = List.of(
                TestRules.percentOff("5").build(), TestRules.amountOff("1").build(), TestRules.freeUnits(1, 1).build(),
                TestRules.of(new ActionSpec.AmountOffCart(java.math.BigDecimal.ONE)).build(),
                TestRules.of(new ActionSpec.Surcharge(java.math.BigDecimal.ONE)).build(),
                TestRules.of(new ActionSpec.ShippingWaiver()).build(),
                TestRules.of(new ActionSpec.FreeGift(UUID.randomUUID(), 1)).build(),
                TestRules.of(new ActionSpec.Reject("no")).build());

        List<PricingRule> rules = factory.create(definitions, CustomerSegment.REGULAR);

        assertThat(rules).hasSize(8).allSatisfy(rule -> assertThat(rule).isInstanceOf(AuditingRule.class));
        assertThat(rules).extracting(PricingRule::stage).containsExactlyInAnyOrder(100, 300, 200, 400, 700, 600, 500, 900);
    }
}
