package com.promoquoter.pricing.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.promoquoter.pricing.model.CustomerSegment;
import com.promoquoter.pricing.model.PromotionType;

public final class Promos {

    private Promos() {
    }

    public static Builder percentOff(String category, String percent) {
        Builder builder = new Builder(PromotionType.PERCENT_OFF_CATEGORY);
        builder.category = category;
        builder.percent = new BigDecimal(percent);
        return builder;
    }

    public static Builder buyXGetY(UUID productId, int buyQty, int freeQty) {
        Builder builder = new Builder(PromotionType.BUY_X_GET_Y);
        builder.productId = productId;
        builder.buyQty = buyQty;
        builder.freeQty = freeQty;
        return builder;
    }

    public static Builder tieredBulk(UUID productId, int minQty, String percent) {
        Builder builder = new Builder(PromotionType.TIERED_BULK);
        builder.productId = productId;
        builder.minQty = minQty;
        builder.percent = new BigDecimal(percent);
        return builder;
    }

    public static final class Builder {

        private final PromotionType type;
        private UUID id = UUID.randomUUID();
        private String name;
        private boolean active = true;
        private int priority;
        private Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        private Set<CustomerSegment> segments = EnumSet.noneOf(CustomerSegment.class);
        private String category;
        private BigDecimal percent;
        private UUID productId;
        private int buyQty;
        private int freeQty;
        private int minQty;

        private Builder(PromotionType type) {
            this.type = type;
            this.name = type.name();
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

        public Builder createdAt(String instant) {
            this.createdAt = Instant.parse(instant);
            return this;
        }

        public Builder inactive() {
            this.active = false;
            return this;
        }

        public Builder forSegments(CustomerSegment... segments) {
            this.segments = EnumSet.copyOf(List.of(segments));
            return this;
        }

        public RuleDefinition build() {
            return switch (type) {
                case PERCENT_OFF_CATEGORY -> LegacyTemplates.percentOffCategory(id, name, active, priority, createdAt,
                        segments, category, percent);
                case BUY_X_GET_Y -> LegacyTemplates.buyXGetY(id, name, active, priority, createdAt, segments,
                        productId, buyQty, freeQty);
                case TIERED_BULK -> LegacyTemplates.tieredBulk(id, name, active, priority, createdAt, segments,
                        productId, minQty, percent);
                case RULE -> throw new IllegalStateException();
            };
        }
    }
}
