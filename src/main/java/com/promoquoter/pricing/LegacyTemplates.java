package com.promoquoter.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.promoquoter.pricing.condition.Condition;
import com.promoquoter.pricing.condition.Condition.Comparison;
import com.promoquoter.pricing.condition.ConditionField;
import com.promoquoter.pricing.condition.Operator;

public final class LegacyTemplates {

    public static final int PERCENT_OFF_CATEGORY_STAGE = 100;
    public static final int BUY_X_GET_Y_STAGE = 200;
    public static final int TIERED_BULK_STAGE = 300;

    private LegacyTemplates() {
    }

    public static RuleDefinition percentOffCategory(UUID id, String name, boolean active, int priority,
            Instant createdAt, Set<CustomerSegment> segments, String category, BigDecimal percent) {
        return new RuleDefinition(id, PromotionType.PERCENT_OFF_CATEGORY, name, RuleKind.PRICING, active, priority,
                PERCENT_OFF_CATEGORY_STAGE, false, createdAt, segments, null,
                new Comparison(ConditionField.PRODUCT_CATEGORY, Operator.EQ, category),
                new ActionSpec.PercentOff(percent));
    }

    public static RuleDefinition buyXGetY(UUID id, String name, boolean active, int priority, Instant createdAt,
            Set<CustomerSegment> segments, UUID productId, int buyQty, int freeQty) {
        return new RuleDefinition(id, PromotionType.BUY_X_GET_Y, name, RuleKind.PRICING, active, priority,
                BUY_X_GET_Y_STAGE, false, createdAt, segments, null,
                new Comparison(ConditionField.PRODUCT_ID, Operator.EQ, productId),
                new ActionSpec.FreeUnits(buyQty, freeQty));
    }

    public static RuleDefinition tieredBulk(UUID id, String name, boolean active, int priority, Instant createdAt,
            Set<CustomerSegment> segments, UUID productId, int minQty, BigDecimal percent) {
        Condition target = new Condition.All(List.of(
                new Comparison(ConditionField.PRODUCT_ID, Operator.EQ, productId),
                new Comparison(ConditionField.LINE_QTY, Operator.GTE, BigDecimal.valueOf(minQty))));
        return new RuleDefinition(id, PromotionType.TIERED_BULK, name, RuleKind.PRICING, active, priority,
                TIERED_BULK_STAGE, false, createdAt, segments, null, target, new ActionSpec.PercentOff(percent));
    }
}
