package com.promoquoter.pricing.condition;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.pricing.condition.Condition.All;
import com.promoquoter.pricing.condition.Condition.Any;
import com.promoquoter.pricing.condition.Condition.Comparison;
import com.promoquoter.pricing.condition.Condition.Not;
import com.promoquoter.pricing.model.CustomerSegment;

class ConditionEvaluatorTest {

    private static final UUID MUG = UUID.randomUUID();

    private static Facts facts() {
        Map<ConditionField, Object> values = new EnumMap<>(ConditionField.class);
        values.put(ConditionField.PRODUCT_ID, MUG);
        values.put(ConditionField.PRODUCT_NAME, "Blue Mug");
        values.put(ConditionField.PRODUCT_CATEGORY, "KITCHEN");
        values.put(ConditionField.PRODUCT_PRICE, new BigDecimal("12.00"));
        values.put(ConditionField.PRODUCT_STOCK, 10);
        values.put(ConditionField.LINE_QTY, 3);
        values.put(ConditionField.CART_SUBTOTAL, new BigDecimal("36.00"));
        values.put(ConditionField.CART_TOTAL, new BigDecimal("30.00"));
        values.put(ConditionField.CART_ITEM_COUNT, 3);
        values.put(ConditionField.CUSTOMER_SEGMENT, CustomerSegment.VIP);
        values.put(ConditionField.DATE, LocalDate.parse("2026-11-15"));
        return values::get;
    }

    private static boolean matches(ConditionField field, Operator operator, Object value) {
        return ConditionEvaluator.matches(new Comparison(field, operator, value), facts());
    }

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    @Test
    void numbersCompareByValueNotByScale() {
        assertThat(matches(ConditionField.PRODUCT_PRICE, Operator.EQ, n("12"))).isTrue();
        assertThat(matches(ConditionField.PRODUCT_PRICE, Operator.EQ, n("12.50"))).isFalse();
        assertThat(matches(ConditionField.LINE_QTY, Operator.EQ, n("3.0"))).isTrue();
        assertThat(matches(ConditionField.LINE_QTY, Operator.NE, n("4"))).isTrue();
    }

    @Test
    void orderingOperatorsIncludeTheirBoundaryCorrectly() {
        assertThat(matches(ConditionField.LINE_QTY, Operator.GT, n("3"))).isFalse();
        assertThat(matches(ConditionField.LINE_QTY, Operator.GTE, n("3"))).isTrue();
        assertThat(matches(ConditionField.LINE_QTY, Operator.LT, n("3"))).isFalse();
        assertThat(matches(ConditionField.LINE_QTY, Operator.LTE, n("3"))).isTrue();
        assertThat(matches(ConditionField.CART_SUBTOTAL, Operator.GTE, n("36.00"))).isTrue();
        assertThat(matches(ConditionField.CART_SUBTOTAL, Operator.GT, n("36.01"))).isFalse();
    }

    @Test
    void betweenIsInclusiveOnBothEnds() {
        assertThat(matches(ConditionField.LINE_QTY, Operator.BETWEEN, List.of(n("3"), n("5")))).isTrue();
        assertThat(matches(ConditionField.LINE_QTY, Operator.BETWEEN, List.of(n("1"), n("3")))).isTrue();
        assertThat(matches(ConditionField.LINE_QTY, Operator.BETWEEN, List.of(n("4"), n("9")))).isFalse();
        assertThat(matches(ConditionField.DATE, Operator.BETWEEN,
                List.of(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30")))).isTrue();
        assertThat(matches(ConditionField.DATE, Operator.BETWEEN,
                List.of(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-31")))).isFalse();
    }

    @Test
    void textComparisonIgnoresCaseAndSurroundingSpaces() {
        assertThat(matches(ConditionField.PRODUCT_CATEGORY, Operator.EQ, "kitchen")).isTrue();
        assertThat(matches(ConditionField.PRODUCT_CATEGORY, Operator.EQ, " Kitchen ")).isTrue();
        assertThat(matches(ConditionField.PRODUCT_CATEGORY, Operator.NE, "HOME")).isTrue();
        assertThat(matches(ConditionField.PRODUCT_NAME, Operator.EQ, "blue mug")).isTrue();
    }

    @Test
    void startsWithAndContainsWorkOnNames() {
        assertThat(matches(ConditionField.PRODUCT_NAME, Operator.STARTS_WITH, "blue")).isTrue();
        assertThat(matches(ConditionField.PRODUCT_NAME, Operator.STARTS_WITH, "mug")).isFalse();
        assertThat(matches(ConditionField.PRODUCT_NAME, Operator.CONTAINS, "e m")).isTrue();
        assertThat(matches(ConditionField.PRODUCT_NAME, Operator.CONTAINS, "lamp")).isFalse();
    }

    @Test
    void inAndNotInCheckMembership() {
        assertThat(matches(ConditionField.PRODUCT_CATEGORY, Operator.IN, List.of("home", "kitchen"))).isTrue();
        assertThat(matches(ConditionField.PRODUCT_CATEGORY, Operator.IN, List.of("home"))).isFalse();
        assertThat(matches(ConditionField.PRODUCT_CATEGORY, Operator.NOT_IN, List.of("home"))).isTrue();
        assertThat(matches(ConditionField.PRODUCT_ID, Operator.IN, List.of(UUID.randomUUID(), MUG))).isTrue();
        assertThat(matches(ConditionField.PRODUCT_ID, Operator.NOT_IN, List.of(MUG))).isFalse();
        assertThat(matches(ConditionField.LINE_QTY, Operator.IN, List.of(n("1"), n("3")))).isTrue();
    }

    @Test
    void idsAndSegmentsMatchExactly() {
        assertThat(matches(ConditionField.PRODUCT_ID, Operator.EQ, MUG)).isTrue();
        assertThat(matches(ConditionField.PRODUCT_ID, Operator.NE, MUG)).isFalse();
        assertThat(matches(ConditionField.CUSTOMER_SEGMENT, Operator.EQ, CustomerSegment.VIP)).isTrue();
        assertThat(matches(ConditionField.CUSTOMER_SEGMENT, Operator.IN,
                List.of(CustomerSegment.REGULAR, CustomerSegment.EMPLOYEE))).isFalse();
    }

    @Test
    void datesCompareByDay() {
        assertThat(matches(ConditionField.DATE, Operator.EQ, LocalDate.parse("2026-11-15"))).isTrue();
        assertThat(matches(ConditionField.DATE, Operator.GT, LocalDate.parse("2026-11-14"))).isTrue();
        assertThat(matches(ConditionField.DATE, Operator.LTE, LocalDate.parse("2026-11-14"))).isFalse();
    }

    @Test
    void allRequiresEveryChildAndAnyRequiresOne() {
        Condition category = new Comparison(ConditionField.PRODUCT_CATEGORY, Operator.EQ, "KITCHEN");
        Condition cheap = new Comparison(ConditionField.PRODUCT_PRICE, Operator.LT, n("5"));

        assertThat(ConditionEvaluator.matches(new All(List.of(category, cheap)), facts())).isFalse();
        assertThat(ConditionEvaluator.matches(new Any(List.of(category, cheap)), facts())).isTrue();
        assertThat(ConditionEvaluator.matches(new Any(List.of(cheap, cheap)), facts())).isFalse();
        assertThat(ConditionEvaluator.matches(new All(List.of(category, category)), facts())).isTrue();
    }

    @Test
    void notInvertsItsChild() {
        Condition category = new Comparison(ConditionField.PRODUCT_CATEGORY, Operator.EQ, "KITCHEN");

        assertThat(ConditionEvaluator.matches(new Not(category), facts())).isFalse();
        assertThat(ConditionEvaluator.matches(new Not(new Not(category)), facts())).isTrue();
    }

    @Test
    void nestedTreesAreEvaluatedRecursively() {
        Condition tree = new All(List.of(
                new Comparison(ConditionField.CUSTOMER_SEGMENT, Operator.IN,
                        List.of(CustomerSegment.VIP, CustomerSegment.EMPLOYEE)),
                new Any(List.of(
                        new Comparison(ConditionField.CART_SUBTOTAL, Operator.GTE, n("100")),
                        new All(List.of(
                                new Comparison(ConditionField.PRODUCT_CATEGORY, Operator.EQ, "kitchen"),
                                new Not(new Comparison(ConditionField.LINE_QTY, Operator.LT, n("2"))))))),
                new Comparison(ConditionField.DATE, Operator.GTE, LocalDate.parse("2026-11-01"))));

        assertThat(ConditionEvaluator.matches(tree, facts())).isTrue();
    }
}
