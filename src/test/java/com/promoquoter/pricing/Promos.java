package com.promoquoter.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public final class Promos {

    private Promos() {
    }

    public static Builder percentOff(String category, String percent) {
        return new Builder(PromotionType.PERCENT_OFF_CATEGORY).category(category).percent(new BigDecimal(percent));
    }

    public static Builder buyXGetY(UUID productId, int buyQty, int freeQty) {
        Builder builder = new Builder(PromotionType.BUY_X_GET_Y);
        builder.productId = productId;
        builder.buyQty = buyQty;
        builder.freeQty = freeQty;
        return builder;
    }

    public static Builder tieredBulk(UUID productId, int minQty, String percent) {
        Builder builder = new Builder(PromotionType.TIERED_BULK).percent(new BigDecimal(percent));
        builder.productId = productId;
        builder.minQty = minQty;
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
        private Integer buyQty;
        private Integer freeQty;
        private Integer minQty;

        private Builder(PromotionType type) {
            this.type = type;
            this.name = type.name();
        }

        private Builder category(String category) {
            this.category = category;
            return this;
        }

        private Builder percent(BigDecimal percent) {
            this.percent = percent;
            return this;
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
            this.segments = EnumSet.copyOf(java.util.List.of(segments));
            return this;
        }

        public PromotionDefinition build() {
            return new PromotionDefinition(id, type, name, active, priority, createdAt, segments, category, percent,
                    productId, buyQty, freeQty, minQty);
        }
    }
}
