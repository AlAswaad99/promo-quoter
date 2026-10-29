package com.promoquoter.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.promoquoter.pricing.condition.Condition;
import com.promoquoter.pricing.condition.Condition.Comparison;
import com.promoquoter.pricing.condition.ConditionField;
import com.promoquoter.pricing.condition.Operator;

public final class TestRules {

    private TestRules() {
    }

    public static Builder of(ActionSpec action) {
        return new Builder(action);
    }

    public static Builder percentOff(String percent) {
        return of(new ActionSpec.PercentOff(new BigDecimal(percent)));
    }

    public static Builder amountOff(String amount) {
        return of(new ActionSpec.AmountOff(new BigDecimal(amount)));
    }

    public static Builder freeUnits(int buyQty, int freeQty) {
        return of(new ActionSpec.FreeUnits(buyQty, freeQty));
    }

    public static Condition category(String category) {
        return new Comparison(ConditionField.PRODUCT_CATEGORY, Operator.EQ, category);
    }

    public static Condition product(UUID productId) {
        return new Comparison(ConditionField.PRODUCT_ID, Operator.EQ, productId);
    }

    public static Condition minQty(int qty) {
        return new Comparison(ConditionField.LINE_QTY, Operator.GTE, BigDecimal.valueOf(qty));
    }

    public static Condition minSubtotal(String amount) {
        return new Comparison(ConditionField.CART_SUBTOTAL, Operator.GTE, new BigDecimal(amount));
    }

    public static Condition all(Condition... conditions) {
        return new Condition.All(List.of(conditions));
    }

    public static final class Builder {

        private final ActionSpec action;
        private UUID id = UUID.randomUUID();
        private String name = "Rule";
        private boolean active = true;
        private int priority;
        private Integer stage;
        private boolean stopAfter;
        private Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        private Set<CustomerSegment> segments = EnumSet.noneOf(CustomerSegment.class);
        private Condition when;
        private Condition target;

        private Builder(ActionSpec action) {
            this.action = action;
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder named(String name) {
            this.name = name;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder stage(int stage) {
            this.stage = stage;
            return this;
        }

        public Builder stopAfter() {
            this.stopAfter = true;
            return this;
        }

        public Builder inactive() {
            this.active = false;
            return this;
        }

        public Builder createdAt(String instant) {
            this.createdAt = Instant.parse(instant);
            return this;
        }

        public Builder forSegments(CustomerSegment... segments) {
            this.segments = EnumSet.copyOf(List.of(segments));
            return this;
        }

        public Builder when(Condition when) {
            this.when = when;
            return this;
        }

        public Builder target(Condition target) {
            this.target = target;
            return this;
        }

        public RuleDefinition build() {
            return new RuleDefinition(id, PromotionType.PERCENT_OFF_CATEGORY, name, action.kind().ruleKind(), active,
                    priority, stage == null ? action.kind().defaultStage() : stage, stopAfter, createdAt, segments,
                    when, target, action);
        }
    }
}
