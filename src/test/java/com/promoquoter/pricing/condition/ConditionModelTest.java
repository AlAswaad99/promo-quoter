package com.promoquoter.pricing.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.pricing.condition.Condition.All;
import com.promoquoter.pricing.condition.Condition.Any;
import com.promoquoter.pricing.condition.Condition.Comparison;
import com.promoquoter.pricing.condition.Condition.Not;
import com.promoquoter.pricing.model.CustomerSegment;

class ConditionModelTest {

    private static Comparison category(String value) {
        return new Comparison(ConditionField.PRODUCT_CATEGORY, Operator.EQ, value);
    }

    @Test
    void everyFieldHasAUniquePathAndCanBeLookedUp() {
        for (ConditionField field : ConditionField.values()) {
            assertThat(ConditionField.byPath(field.path())).contains(field);
        }
        assertThat(ConditionField.values()).extracting(ConditionField::path).doesNotHaveDuplicates();
        assertThat(ConditionField.byPath("product.brand")).isEmpty();
    }

    @Test
    void productAndLineFieldsAreLineScopedAndCartFieldsAreCartScoped() {
        assertThat(ConditionField.PRODUCT_CATEGORY.scope()).isEqualTo(FieldScope.LINE);
        assertThat(ConditionField.LINE_QTY.scope()).isEqualTo(FieldScope.LINE);
        assertThat(ConditionField.CART_SUBTOTAL.scope()).isEqualTo(FieldScope.CART);
        assertThat(ConditionField.CUSTOMER_SEGMENT.scope()).isEqualTo(FieldScope.CART);
        assertThat(ConditionField.DATE.scope()).isEqualTo(FieldScope.CART);
    }

    @Test
    void operatorsAreLookedUpBySymbol() {
        assertThat(Operator.bySymbol("notIn")).contains(Operator.NOT_IN);
        assertThat(Operator.bySymbol("startsWith")).contains(Operator.STARTS_WITH);
        assertThat(Operator.bySymbol("regex")).isEmpty();
    }

    @Test
    void textFieldsSupportTextOperatorsButNumbersDoNot() {
        assertThat(FieldType.TEXT.supports(Operator.CONTAINS)).isTrue();
        assertThat(FieldType.NUMBER.supports(Operator.CONTAINS)).isFalse();
        assertThat(FieldType.NUMBER.supports(Operator.BETWEEN)).isTrue();
        assertThat(FieldType.ID.supports(Operator.GT)).isFalse();
        assertThat(FieldType.DATE.supports(Operator.IN)).isFalse();
    }

    @Test
    void comparisonAcceptsAValueOfTheFieldType() {
        assertThat(new Comparison(ConditionField.LINE_QTY, Operator.GTE, new BigDecimal("10")).value())
                .isEqualTo(new BigDecimal("10"));
        assertThat(new Comparison(ConditionField.PRODUCT_ID, Operator.EQ, UUID.randomUUID())).isNotNull();
        assertThat(new Comparison(ConditionField.CUSTOMER_SEGMENT, Operator.EQ, CustomerSegment.VIP)).isNotNull();
        assertThat(new Comparison(ConditionField.DATE, Operator.EQ, LocalDate.parse("2026-11-01"))).isNotNull();
    }

    @Test
    void comparisonRejectsAnOperatorThatDoesNotFitTheField() {
        assertThatThrownBy(() -> new Comparison(ConditionField.PRODUCT_CATEGORY, Operator.GT, "A"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("product.category");
        assertThatThrownBy(() -> new Comparison(ConditionField.LINE_QTY, Operator.STARTS_WITH, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparisonRejectsAValueOfTheWrongType() {
        assertThatThrownBy(() -> new Comparison(ConditionField.LINE_QTY, Operator.EQ, "ten"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("BigDecimal");
        assertThatThrownBy(() -> new Comparison(ConditionField.PRODUCT_ID, Operator.EQ, "abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listOperatorsNeedANonEmptyListOfTheFieldType() {
        assertThat(new Comparison(ConditionField.PRODUCT_CATEGORY, Operator.IN, List.of("A", "B")).value())
                .isEqualTo(List.of("A", "B"));
        assertThatThrownBy(() -> new Comparison(ConditionField.PRODUCT_CATEGORY, Operator.IN, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Comparison(ConditionField.PRODUCT_CATEGORY, Operator.IN, "A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Comparison(ConditionField.LINE_QTY, Operator.NOT_IN, List.of("x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void betweenNeedsExactlyTwoValues() {
        assertThat(new Comparison(ConditionField.CART_SUBTOTAL, Operator.BETWEEN,
                List.of(BigDecimal.ONE, BigDecimal.TEN))).isNotNull();
        assertThatThrownBy(() -> new Comparison(ConditionField.CART_SUBTOTAL, Operator.BETWEEN,
                List.of(BigDecimal.ONE))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Comparison(ConditionField.CART_SUBTOTAL, Operator.BETWEEN,
                List.of(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparisonRequiresAllParts() {
        assertThatThrownBy(() -> new Comparison(null, Operator.EQ, "A")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Comparison(ConditionField.PRODUCT_NAME, null, "A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Comparison(ConditionField.PRODUCT_NAME, Operator.EQ, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void combinatorsNeedAtLeastOneChildAndCopyTheirList() {
        assertThatThrownBy(() -> new All(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Any(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Not(null)).isInstanceOf(IllegalArgumentException.class);
        List<Condition> children = new java.util.ArrayList<>(List.of(category("A")));
        All all = new All(children);
        children.add(category("B"));
        assertThat(all.conditions()).hasSize(1);
    }
}
