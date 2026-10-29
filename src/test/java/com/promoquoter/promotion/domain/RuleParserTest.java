package com.promoquoter.promotion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promoquoter.common.error.FieldViolation;
import com.promoquoter.pricing.condition.Condition.All;
import com.promoquoter.pricing.condition.Condition.Comparison;
import com.promoquoter.pricing.condition.Condition;
import com.promoquoter.pricing.condition.ConditionField;
import com.promoquoter.pricing.condition.FieldScope;
import com.promoquoter.pricing.condition.Operator;
import com.promoquoter.pricing.model.ActionSpec;
import com.promoquoter.pricing.model.CustomerSegment;
import com.promoquoter.pricing.model.RuleKind;

class RuleParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<FieldViolation> violations = new ArrayList<>();
    private final RuleParser parser = new RuleParser(violations);

    private JsonNode json(String text) {
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Condition target(String text) {
        return parser.parseCondition(json(text), FieldScope.LINE, "target");
    }

    private Condition when(String text) {
        return parser.parseCondition(json(text), FieldScope.CART, "when");
    }

    private ActionSpec action(String text, RuleKind kind) {
        return parser.parseAction(json(text), kind, "action");
    }

    private List<String> violationFields() {
        return violations.stream().map(FieldViolation::field).toList();
    }

    @Test
    void parsesACategoryComparisonAndNormalisesTheValue() {
        Condition condition = target("{\"field\":\"product.category\",\"op\":\"eq\",\"value\":\" kitchen \"}");

        assertThat(condition).isEqualTo(new Comparison(ConditionField.PRODUCT_CATEGORY, Operator.EQ, "kitchen"));
        assertThat(violations).isEmpty();
    }

    @Test
    void parsesNumbersGivenAsNumbersOrStrings() {
        assertThat(target("{\"field\":\"line.qty\",\"op\":\"gte\",\"value\":10}"))
                .isEqualTo(new Comparison(ConditionField.LINE_QTY, Operator.GTE, new BigDecimal("10")));
        assertThat(target("{\"field\":\"product.price\",\"op\":\"lt\",\"value\":\"12.50\"}"))
                .isEqualTo(new Comparison(ConditionField.PRODUCT_PRICE, Operator.LT, new BigDecimal("12.50")));
        assertThat(violations).isEmpty();
    }

    @Test
    void parsesIdsSegmentsDatesAndRanges() {
        UUID id = UUID.randomUUID();

        assertThat(target("{\"field\":\"product.id\",\"op\":\"in\",\"value\":[\"" + id + "\"]}"))
                .isEqualTo(new Comparison(ConditionField.PRODUCT_ID, Operator.IN, List.of(id)));
        assertThat(when("{\"field\":\"customer.segment\",\"op\":\"eq\",\"value\":\"VIP\"}"))
                .isEqualTo(new Comparison(ConditionField.CUSTOMER_SEGMENT, Operator.EQ, CustomerSegment.VIP));
        assertThat(when("{\"field\":\"date\",\"op\":\"between\",\"value\":[\"2026-11-01\",\"2026-11-30\"]}"))
                .isEqualTo(new Comparison(ConditionField.DATE, Operator.BETWEEN,
                        List.of(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"))));
        assertThat(violations).isEmpty();
    }

    @Test
    void parsesNestedCombinators() {
        Condition condition = target("{\"all\":[{\"field\":\"product.category\",\"op\":\"eq\",\"value\":\"A\"},"
                + "{\"not\":{\"field\":\"line.qty\",\"op\":\"lt\",\"value\":2}},"
                + "{\"any\":[{\"field\":\"product.price\",\"op\":\"gt\",\"value\":1},"
                + "{\"field\":\"product.stock\",\"op\":\"lte\",\"value\":5}]}]}");

        assertThat(condition).isInstanceOf(All.class);
        assertThat(((All) condition).conditions()).hasSize(3);
        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsAFieldFromTheWrongScope() {
        assertThat(target("{\"field\":\"cart.subtotal\",\"op\":\"gt\",\"value\":1}")).isNull();
        assertThat(when("{\"field\":\"product.category\",\"op\":\"eq\",\"value\":\"A\"}")).isNull();

        assertThat(violationFields()).containsExactly("target.field", "when.field");
        assertThat(violations.get(0).message()).contains("cart field");
    }

    @Test
    void rejectsUnknownFieldsAndOperators() {
        assertThat(target("{\"field\":\"product.brand\",\"op\":\"eq\",\"value\":\"A\"}")).isNull();
        assertThat(target("{\"field\":\"product.category\",\"op\":\"regex\",\"value\":\"A\"}")).isNull();

        assertThat(violationFields()).containsExactly("target.field", "target.op");
        assertThat(violations.get(0).message()).contains("product.category");
    }

    @Test
    void rejectsAnOperatorThatDoesNotFitTheField() {
        assertThat(target("{\"field\":\"product.category\",\"op\":\"gt\",\"value\":\"A\"}")).isNull();
        assertThat(target("{\"field\":\"line.qty\",\"op\":\"contains\",\"value\":1}")).isNull();

        assertThat(violationFields()).containsExactly("target.op", "target.op");
    }

    @Test
    void rejectsValuesOfTheWrongTypeOrShape() {
        assertThat(target("{\"field\":\"line.qty\",\"op\":\"eq\",\"value\":\"ten\"}")).isNull();
        assertThat(target("{\"field\":\"product.id\",\"op\":\"eq\",\"value\":\"abc\"}")).isNull();
        assertThat(when("{\"field\":\"customer.segment\",\"op\":\"eq\",\"value\":\"GOLD\"}")).isNull();
        assertThat(when("{\"field\":\"date\",\"op\":\"eq\",\"value\":\"tomorrow\"}")).isNull();
        assertThat(target("{\"field\":\"line.qty\",\"op\":\"eq\",\"value\":[1,2]}")).isNull();
        assertThat(target("{\"field\":\"line.qty\",\"op\":\"in\",\"value\":5}")).isNull();

        assertThat(violationFields()).containsExactly("target.value", "target.value", "when.value", "when.value",
                "target.value", "target.value");
    }

    @Test
    void rejectsEmptyListsAndBadRanges() {
        assertThat(target("{\"field\":\"product.category\",\"op\":\"in\",\"value\":[]}")).isNull();
        assertThat(target("{\"field\":\"line.qty\",\"op\":\"between\",\"value\":[1]}")).isNull();
        assertThat(target("{\"field\":\"line.qty\",\"op\":\"between\",\"value\":[9,3]}")).isNull();
        assertThat(when("{\"field\":\"date\",\"op\":\"between\",\"value\":[\"2026-12-01\",\"2026-11-01\"]}")).isNull();

        assertThat(violations).hasSize(4);
        assertThat(violations.get(2).message()).contains("start must not be after");
    }

    @Test
    void rejectsListsLongerThanTheLimit() {
        String values = String.join(",", java.util.Collections.nCopies(RuleParser.MAX_LIST_SIZE + 1, "\"A\""));

        assertThat(target("{\"field\":\"product.category\",\"op\":\"in\",\"value\":[" + values + "]}")).isNull();
        assertThat(violations.get(0).message()).contains("between 1 and 100");
    }

    @Test
    void rejectsMissingAndUnknownProperties() {
        assertThat(target("{\"field\":\"line.qty\",\"op\":\"eq\"}")).isNull();
        assertThat(target("{\"field\":\"line.qty\",\"op\":\"eq\",\"value\":1,\"extra\":true}")).isNull();
        assertThat(target("[]")).isNull();

        assertThat(violationFields()).containsExactly("target.value", "target.extra", "target");
    }

    @Test
    void rejectsACombinatorMixedWithOtherProperties() {
        assertThat(target("{\"all\":[{\"field\":\"line.qty\",\"op\":\"eq\",\"value\":1}],\"field\":\"x\"}")).isNull();
        assertThat(target("{\"all\":[]}")).isNull();
        assertThat(target("{\"any\":\"nope\"}")).isNull();

        assertThat(violationFields()).containsExactly("target", "target.all", "target.any");
    }

    @Test
    void reportsEveryInvalidChildWithItsOwnPath() {
        assertThat(target("{\"all\":[{\"field\":\"line.qty\",\"op\":\"eq\",\"value\":\"x\"},"
                + "{\"field\":\"nope\",\"op\":\"eq\",\"value\":1}]}")).isNull();

        assertThat(violationFields()).containsExactly("target.all[0].value", "target.all[1].field");
    }

    @Test
    void rejectsTreesNestedDeeperThanTheLimit() {
        String leaf = "{\"field\":\"line.qty\",\"op\":\"eq\",\"value\":1}";
        String tree = leaf;
        for (int level = 0; level < RuleParser.MAX_DEPTH + 1; level++) {
            tree = "{\"not\":" + tree + "}";
        }

        assertThat(target(tree)).isNull();
        assertThat(violations.get(0).message()).contains("nested deeper than 5");
    }

    @Test
    void acceptsATreeAtExactlyTheDepthLimit() {
        String tree = "{\"field\":\"line.qty\",\"op\":\"eq\",\"value\":1}";
        for (int level = 0; level < RuleParser.MAX_DEPTH; level++) {
            tree = "{\"not\":" + tree + "}";
        }

        assertThat(target(tree)).isNotNull();
        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsMoreConditionsThanTheLimit() {
        String leaf = "{\"field\":\"line.qty\",\"op\":\"eq\",\"value\":1}";
        String tree = "{\"all\":[" + String.join(",", java.util.Collections.nCopies(RuleParser.MAX_CONDITIONS, leaf))
                + "]}";

        assertThat(target(tree)).isNull();
        assertThat(violationFields()).containsExactly("target");
        assertThat(violations.get(0).message()).contains("more than 20 conditions");
    }

    @Test
    void parsesEveryPricingAction() {
        UUID gift = UUID.randomUUID();

        assertThat(action("{\"kind\":\"PERCENT_OFF\",\"percent\":12.5}", RuleKind.PRICING))
                .isEqualTo(new ActionSpec.PercentOff(new BigDecimal("12.5")));
        assertThat(action("{\"kind\":\"AMOUNT_OFF\",\"amount\":5}", RuleKind.PRICING))
                .isEqualTo(new ActionSpec.AmountOff(new BigDecimal("5")));
        assertThat(action("{\"kind\":\"FREE_UNITS\",\"buyQty\":2,\"freeQty\":1}", RuleKind.PRICING))
                .isEqualTo(new ActionSpec.FreeUnits(2, 1));
        assertThat(action("{\"kind\":\"AMOUNT_OFF_CART\",\"amount\":\"7.50\"}", RuleKind.PRICING))
                .isEqualTo(new ActionSpec.AmountOffCart(new BigDecimal("7.50")));
        assertThat(action("{\"kind\":\"SURCHARGE\",\"amount\":2}", RuleKind.PRICING))
                .isEqualTo(new ActionSpec.Surcharge(new BigDecimal("2")));
        assertThat(action("{\"kind\":\"SHIPPING_WAIVER\"}", RuleKind.PRICING)).isEqualTo(new ActionSpec.ShippingWaiver());
        assertThat(action("{\"kind\":\"FREE_GIFT\",\"productId\":\"" + gift + "\",\"qty\":2}", RuleKind.PRICING))
                .isEqualTo(new ActionSpec.FreeGift(gift, 2));
        assertThat(violations).isEmpty();
    }

    @Test
    void parsesTheRejectAction() {
        assertThat(action("{\"kind\":\"REJECT\",\"message\":\" Minimum order is 20 \"}", RuleKind.VALIDATION))
                .isEqualTo(new ActionSpec.Reject("Minimum order is 20"));
        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsAnActionOfTheWrongRuleKind() {
        assertThat(action("{\"kind\":\"REJECT\",\"message\":\"x\"}", RuleKind.PRICING)).isNull();
        assertThat(action("{\"kind\":\"PERCENT_OFF\",\"percent\":5}", RuleKind.VALIDATION)).isNull();

        assertThat(violationFields()).containsExactly("action.kind", "action.kind");
    }

    @Test
    void rejectsUnknownKindsMissingKindsAndUnknownParameters() {
        assertThat(action("{\"kind\":\"BARK\"}", RuleKind.PRICING)).isNull();
        assertThat(action("{\"percent\":5}", RuleKind.PRICING)).isNull();
        assertThat(action("{\"kind\":\"PERCENT_OFF\",\"percent\":5,\"amount\":2}", RuleKind.PRICING)).isNull();
        assertThat(parser.parseAction(null, RuleKind.PRICING, "action")).isNull();

        assertThat(violationFields()).containsExactly("action.kind", "action.kind", "action.amount", "action");
    }

    @Test
    void rejectsInvalidPercentsAndAmounts() {
        assertThat(action("{\"kind\":\"PERCENT_OFF\",\"percent\":0}", RuleKind.PRICING)).isNull();
        assertThat(action("{\"kind\":\"PERCENT_OFF\",\"percent\":101}", RuleKind.PRICING)).isNull();
        assertThat(action("{\"kind\":\"PERCENT_OFF\",\"percent\":10.123}", RuleKind.PRICING)).isNull();
        assertThat(action("{\"kind\":\"PERCENT_OFF\"}", RuleKind.PRICING)).isNull();
        assertThat(action("{\"kind\":\"AMOUNT_OFF\",\"amount\":-1}", RuleKind.PRICING)).isNull();
        assertThat(action("{\"kind\":\"SURCHARGE\",\"amount\":1.234}", RuleKind.PRICING)).isNull();
        assertThat(action("{\"kind\":\"AMOUNT_OFF_CART\",\"amount\":100000000000}", RuleKind.PRICING)).isNull();

        assertThat(violations).hasSize(7);
    }

    @Test
    void acceptsBoundaryPercentsAndAmounts() {
        assertThat(action("{\"kind\":\"PERCENT_OFF\",\"percent\":0.01}", RuleKind.PRICING)).isNotNull();
        assertThat(action("{\"kind\":\"PERCENT_OFF\",\"percent\":100}", RuleKind.PRICING)).isNotNull();
        assertThat(action("{\"kind\":\"AMOUNT_OFF\",\"amount\":9999999999.99}", RuleKind.PRICING)).isNotNull();
        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsInvalidQuantitiesAndGiftParameters() {
        assertThat(action("{\"kind\":\"FREE_UNITS\",\"buyQty\":0,\"freeQty\":1}", RuleKind.PRICING)).isNull();
        assertThat(action("{\"kind\":\"FREE_UNITS\",\"buyQty\":1,\"freeQty\":1001}", RuleKind.PRICING)).isNull();
        assertThat(action("{\"kind\":\"FREE_UNITS\",\"buyQty\":1.5,\"freeQty\":1}", RuleKind.PRICING)).isNull();
        assertThat(action("{\"kind\":\"FREE_GIFT\",\"productId\":\"nope\",\"qty\":1}", RuleKind.PRICING)).isNull();
        assertThat(action("{\"kind\":\"FREE_GIFT\",\"productId\":\"" + UUID.randomUUID() + "\",\"qty\":101}",
                RuleKind.PRICING)).isNull();

        assertThat(violationFields()).containsExactly("action.buyQty", "action.freeQty", "action.buyQty",
                "action.productId", "action.qty");
    }

    @Test
    void rejectsBlankAndOverlongRejectMessages() {
        assertThat(action("{\"kind\":\"REJECT\",\"message\":\"  \"}", RuleKind.VALIDATION)).isNull();
        assertThat(action("{\"kind\":\"REJECT\"}", RuleKind.VALIDATION)).isNull();
        assertThat(action("{\"kind\":\"REJECT\",\"message\":\"" + "m".repeat(301) + "\"}", RuleKind.VALIDATION))
                .isNull();

        assertThat(violationFields()).containsExactly("action.message", "action.message", "action.message");
    }
}
