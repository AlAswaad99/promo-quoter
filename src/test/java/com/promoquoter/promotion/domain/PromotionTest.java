package com.promoquoter.promotion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.PromotionType;
import com.promoquoter.pricing.condition.Condition;
import com.promoquoter.pricing.condition.ConditionField;
import com.promoquoter.pricing.condition.Operator;

class PromotionTest {

    @Test
    void percentOffCategoryNormalisesNameAndCategory() {
        Promotion promotion = Promotion.percentOffCategory("  10% off  ", true, 5, Set.of(), " kitchen ",
                new BigDecimal("10"));

        assertThat(promotion.getType()).isEqualTo(PromotionType.PERCENT_OFF_CATEGORY);
        assertThat(promotion.getName()).isEqualTo("10% off");
        assertThat(promotion.getCategory()).isEqualTo("KITCHEN");
        assertThat(promotion.getPercent()).isEqualByComparingTo("10");
        assertThat(promotion.isActive()).isTrue();
        assertThat(promotion.getPriority()).isEqualTo(5);
        assertThat(promotion.getProductId()).isNull();
    }

    @Test
    void buyXGetYKeepsProductAndQuantities() {
        UUID productId = UUID.randomUUID();

        Promotion promotion = Promotion.buyXGetY("2+1", false, 0, Set.of(), productId, 2, 1);

        assertThat(promotion.getType()).isEqualTo(PromotionType.BUY_X_GET_Y);
        assertThat(promotion.getProductId()).isEqualTo(productId);
        assertThat(promotion.getBuyQty()).isEqualTo(2);
        assertThat(promotion.getFreeQty()).isEqualTo(1);
        assertThat(promotion.isActive()).isFalse();
        assertThat(promotion.getCategory()).isNull();
    }

    @Test
    void emptySegmentsMeanEveryone() {
        Promotion promotion = Promotion.buyXGetY("A", true, 0, Set.of(), UUID.randomUUID(), 1, 1);

        assertThat(promotion.getEligibleSegments()).isEmpty();
    }

    @Test
    void segmentsAreCopiedDefensively() {
        Set<CustomerSegment> segments = EnumSet.of(CustomerSegment.VIP);

        Promotion promotion = Promotion.buyXGetY("A", true, 0, segments, UUID.randomUUID(), 1, 1);
        segments.add(CustomerSegment.EMPLOYEE);

        assertThat(promotion.getEligibleSegments()).containsExactly(CustomerSegment.VIP);
    }

    @Test
    void generatesIdAndCreationTime() {
        Promotion first = Promotion.buyXGetY("A", true, 0, Set.of(), UUID.randomUUID(), 1, 1);
        Promotion second = Promotion.buyXGetY("A", true, 0, Set.of(), UUID.randomUUID(), 1, 1);

        assertThat(first.getId()).isNotNull().isNotEqualTo(second.getId());
        assertThat(first.getCreatedAt()).isNotNull();
        assertThat(first.getVersion()).isNull();
    }

    @Test
    void tieredBulkKeepsProductThresholdAndPercent() {
        UUID productId = UUID.randomUUID();

        Promotion promotion = Promotion.tieredBulk("5% off 10+", true, 2, Set.of(CustomerSegment.VIP), productId, 10,
                new BigDecimal("5"));

        assertThat(promotion.getType()).isEqualTo(PromotionType.TIERED_BULK);
        assertThat(promotion.getProductId()).isEqualTo(productId);
        assertThat(promotion.getMinQty()).isEqualTo(10);
        assertThat(promotion.getPercent()).isEqualByComparingTo("5");
        assertThat(promotion.getBuyQty()).isNull();
    }

    @Test
    void definitionCarriesEveryRuleParameter() {
        UUID productId = UUID.randomUUID();
        Promotion promotion = Promotion.tieredBulk("5% off 10+", true, 2, Set.of(CustomerSegment.VIP), productId, 10,
                new BigDecimal("5"));

        var definition = promotion.toDefinition();

        assertThat(definition.id()).isEqualTo(promotion.getId());
        assertThat(definition.type()).isEqualTo(PromotionType.TIERED_BULK);
        assertThat(definition.stage()).isEqualTo(300);
        assertThat(definition.action()).isEqualTo(new ActionSpec.PercentOff(new BigDecimal("5")));
        assertThat(definition.target()).isEqualTo(new Condition.All(List.of(
                new Condition.Comparison(ConditionField.PRODUCT_ID, Operator.EQ, productId),
                new Condition.Comparison(ConditionField.LINE_QTY, Operator.GTE, new BigDecimal("10")))));
        assertThat(definition.eligibleSegments()).containsExactly(CustomerSegment.VIP);
        assertThat(definition.isEligibleFor(CustomerSegment.VIP)).isTrue();
        assertThat(definition.isEligibleFor(CustomerSegment.REGULAR)).isFalse();
    }
}
