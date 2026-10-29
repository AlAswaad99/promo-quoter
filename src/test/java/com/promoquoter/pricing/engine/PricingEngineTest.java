package com.promoquoter.pricing.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.pricing.model.AppliedPromotion;
import com.promoquoter.pricing.model.CartLine;
import com.promoquoter.pricing.model.CustomerSegment;
import com.promoquoter.pricing.model.LineBreakdown;
import com.promoquoter.pricing.model.PriceBreakdown;
import com.promoquoter.pricing.model.PromotionType;

class PricingEngineTest {

    private static final UUID MUG = UUID.randomUUID();
    private static final UUID LAMP = UUID.randomUUID();

    private final PricingEngine engine = new PricingEngine(TestProviders.factory());

    private static CartLine mugs(int qty) {
        return new CartLine(MUG, "Mug", "KITCHEN", new BigDecimal("12.00"), qty);
    }

    private static CartLine lamp(int qty) {
        return new CartLine(LAMP, "Lamp", "HOME", new BigDecimal("30.00"), qty);
    }

    @Test
    void pricesTheWorkedExampleFromTheSpecification() {
        RuleDefinition percent = Promos.percentOff("KITCHEN", "10").named("10% kitchen").build();
        RuleDefinition buyXGetY = Promos.buyXGetY(MUG, 2, 1).named("Mugs 2+1").build();

        PriceBreakdown breakdown = engine.price(List.of(mugs(3)), CustomerSegment.REGULAR,
                List.of(buyXGetY, percent));

        assertThat(breakdown.subtotal()).isEqualByComparingTo("36.00");
        assertThat(breakdown.totalDiscount()).isEqualByComparingTo("14.40");
        assertThat(breakdown.total()).isEqualByComparingTo("21.60");
        assertThat(breakdown.lines()).singleElement().satisfies(line -> {
            assertThat(line.subtotal()).isEqualByComparingTo("36.00");
            assertThat(line.discount()).isEqualByComparingTo("14.40");
            assertThat(line.total()).isEqualByComparingTo("21.60");
        });
        assertThat(breakdown.appliedPromotions()).extracting(AppliedPromotion::sequence).containsExactly(1, 2);
        assertThat(breakdown.appliedPromotions()).extracting(AppliedPromotion::name)
                .containsExactly("10% kitchen", "Mugs 2+1");
        assertThat(breakdown.appliedPromotions()).extracting(AppliedPromotion::type)
                .containsExactly(PromotionType.PERCENT_OFF_CATEGORY, PromotionType.BUY_X_GET_Y);
        assertThat(breakdown.appliedPromotions().get(0).amount()).isEqualByComparingTo("3.60");
        assertThat(breakdown.appliedPromotions().get(1).amount()).isEqualByComparingTo("10.80");
        assertThat(breakdown.appliedPromotions()).allSatisfy(applied -> assertThat(applied.productId()).isEqualTo(MUG));
    }

    @Test
    void definitionOrderDoesNotChangeTheResult() {
        RuleDefinition percent = Promos.percentOff("KITCHEN", "10").build();
        RuleDefinition buyXGetY = Promos.buyXGetY(MUG, 2, 1).build();

        PriceBreakdown one = engine.price(List.of(mugs(3)), CustomerSegment.REGULAR, List.of(percent, buyXGetY));
        PriceBreakdown other = engine.price(List.of(mugs(3)), CustomerSegment.REGULAR, List.of(buyXGetY, percent));

        assertThat(one.total()).isEqualByComparingTo(other.total());
        assertThat(one.appliedPromotions()).extracting(AppliedPromotion::type)
                .isEqualTo(other.appliedPromotions().stream().map(AppliedPromotion::type).toList());
    }

    @Test
    void withoutPromotionsTheTotalEqualsTheSubtotal() {
        PriceBreakdown breakdown = engine.price(List.of(mugs(2), lamp(1)), CustomerSegment.REGULAR, List.of());

        assertThat(breakdown.subtotal()).isEqualByComparingTo("54.00");
        assertThat(breakdown.totalDiscount()).isEqualByComparingTo("0.00");
        assertThat(breakdown.total()).isEqualByComparingTo("54.00");
        assertThat(breakdown.appliedPromotions()).isEmpty();
    }

    @Test
    void cartTotalsAreTheSumOfTheLines() {
        PriceBreakdown breakdown = engine.price(List.of(mugs(3), lamp(2)), CustomerSegment.REGULAR,
                List.of(Promos.percentOff("KITCHEN", "10").build(), Promos.percentOff("HOME", "33.33").build()));

        BigDecimal lineTotals = breakdown.lines().stream().map(LineBreakdown::total).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        BigDecimal lineDiscounts = breakdown.lines().stream().map(LineBreakdown::discount).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        assertThat(breakdown.total()).isEqualByComparingTo(lineTotals);
        assertThat(breakdown.totalDiscount()).isEqualByComparingTo(lineDiscounts);
        assertThat(breakdown.subtotal().subtract(breakdown.totalDiscount())).isEqualByComparingTo(breakdown.total());
        assertThat(breakdown.total().scale()).isEqualTo(2);
    }

    @Test
    void totalsNeverGoNegativeWhenDiscountsStackPastTheLinePrice() {
        PriceBreakdown breakdown = engine.price(List.of(mugs(3)), CustomerSegment.REGULAR,
                List.of(Promos.percentOff("KITCHEN", "100").build(), Promos.percentOff("KITCHEN", "100").build(),
                        Promos.buyXGetY(MUG, 1, 1).build()));

        assertThat(breakdown.total()).isEqualByComparingTo("0.00");
        assertThat(breakdown.totalDiscount()).isEqualByComparingTo("36.00");
        assertThat(breakdown.appliedPromotions()).hasSize(1);
    }

    @Test
    void promotionsThatChangeNothingAreNotListed() {
        PriceBreakdown breakdown = engine.price(List.of(lamp(1)), CustomerSegment.REGULAR,
                List.of(Promos.percentOff("KITCHEN", "10").build(), Promos.buyXGetY(MUG, 2, 1).build()));

        assertThat(breakdown.appliedPromotions()).isEmpty();
    }

    @Test
    void appliesVipPromotionsOnlyToVipCarts() {
        RuleDefinition vipOnly = Promos.percentOff("KITCHEN", "50").forSegments(CustomerSegment.VIP).build();

        assertThat(engine.price(List.of(mugs(1)), CustomerSegment.REGULAR, List.of(vipOnly)).total())
                .isEqualByComparingTo("12.00");
        assertThat(engine.price(List.of(mugs(1)), CustomerSegment.VIP, List.of(vipOnly)).total())
                .isEqualByComparingTo("6.00");
    }

    @Test
    void ignoresInactivePromotions() {
        PriceBreakdown breakdown = engine.price(List.of(mugs(1)), CustomerSegment.REGULAR,
                List.of(Promos.percentOff("KITCHEN", "50").inactive().build()));

        assertThat(breakdown.total()).isEqualByComparingTo("12.00");
    }

    @Test
    void sameStagePromotionsStackInPriorityOrder() {
        RuleDefinition tenPercent = Promos.percentOff("KITCHEN", "10").named("A").priority(1).build();
        RuleDefinition twentyPercent = Promos.percentOff("KITCHEN", "20").named("B").priority(2).build();

        PriceBreakdown breakdown = engine.price(List.of(mugs(1)), CustomerSegment.REGULAR,
                List.of(twentyPercent, tenPercent));

        assertThat(breakdown.appliedPromotions()).extracting(AppliedPromotion::name).containsExactly("A", "B");
        assertThat(breakdown.appliedPromotions().get(0).amount()).isEqualByComparingTo("1.20");
        assertThat(breakdown.appliedPromotions().get(1).amount()).isEqualByComparingTo("2.16");
        assertThat(breakdown.total()).isEqualByComparingTo("8.64");
    }

    @Test
    void tieredBulkRunsLastOnTheDiscountedTotal() {
        RuleDefinition percent = Promos.percentOff("KITCHEN", "10").named("category").build();
        RuleDefinition buyXGetY = Promos.buyXGetY(MUG, 2, 1).named("bxgy").build();
        RuleDefinition tiered = Promos.tieredBulk(MUG, 3, "10").named("bulk").build();

        PriceBreakdown breakdown = engine.price(List.of(mugs(3)), CustomerSegment.REGULAR,
                List.of(tiered, buyXGetY, percent));

        assertThat(breakdown.appliedPromotions()).extracting(AppliedPromotion::name)
                .containsExactly("category", "bxgy", "bulk");
        assertThat(breakdown.appliedPromotions().get(2).amount()).isEqualByComparingTo("2.16");
        assertThat(breakdown.total()).isEqualByComparingTo("19.44");
    }

    @Test
    void pricesEachLineIndependently() {
        PriceBreakdown breakdown = engine.price(List.of(mugs(3), lamp(1)), CustomerSegment.REGULAR,
                List.of(Promos.buyXGetY(MUG, 2, 1).build()));

        assertThat(breakdown.lines()).extracting(LineBreakdown::total)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("24.00"), new BigDecimal("30.00"));
        assertThat(breakdown.total()).isEqualByComparingTo("54.00");
    }
}
