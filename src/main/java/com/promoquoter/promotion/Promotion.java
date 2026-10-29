package com.promoquoter.promotion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import com.promoquoter.common.text.Categories;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.LegacyTemplates;
import com.promoquoter.pricing.RuleDefinition;
import com.promoquoter.pricing.PromotionType;
import com.promoquoter.pricing.RuleKind;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "promotion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Promotion {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PromotionType type;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int priority;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "promotion_segment", joinColumns = @JoinColumn(name = "promotion_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "segment", nullable = false, length = 20)
    @BatchSize(size = 100)
    private Set<CustomerSegment> eligibleSegments = EnumSet.noneOf(CustomerSegment.class);

    @Column(length = 100)
    private String category;

    @Column(precision = 5, scale = 2)
    private BigDecimal percent;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "buy_qty")
    private Integer buyQty;

    @Column(name = "free_qty")
    private Integer freeQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleKind kind = RuleKind.PRICING;

    private Integer stage;

    @Column(name = "stop_after", nullable = false)
    private boolean stopAfter;

    @Lob
    @Column(name = "rule_json")
    private String ruleJson;

    @Column(name = "min_qty")
    private Integer minQty;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private Promotion(PromotionType type, String name, boolean active, int priority,
            Set<CustomerSegment> eligibleSegments) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.name = name.trim();
        this.active = active;
        this.priority = priority;
        this.eligibleSegments = eligibleSegments.isEmpty()
                ? EnumSet.noneOf(CustomerSegment.class)
                : EnumSet.copyOf(eligibleSegments);
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    void update(String newName, Boolean newActive, Integer newPriority, Set<CustomerSegment> newSegments) {
        if (newName != null) {
            this.name = newName.trim();
        }
        if (newActive != null) {
            this.active = newActive;
        }
        if (newPriority != null) {
            this.priority = newPriority;
        }
        if (newSegments != null) {
            this.eligibleSegments = newSegments.isEmpty()
                    ? EnumSet.noneOf(CustomerSegment.class)
                    : EnumSet.copyOf(newSegments);
        }
    }

    RuleDefinition toDefinition() {
        Set<CustomerSegment> segments = Set.copyOf(eligibleSegments);
        return switch (type) {
            case PERCENT_OFF_CATEGORY -> LegacyTemplates.percentOffCategory(id, name, active, priority, createdAt,
                    segments, category, percent);
            case BUY_X_GET_Y -> LegacyTemplates.buyXGetY(id, name, active, priority, createdAt, segments, productId,
                    buyQty, freeQty);
            case TIERED_BULK -> LegacyTemplates.tieredBulk(id, name, active, priority, createdAt, segments, productId,
                    minQty, percent);
            case RULE -> RuleStore.definition(this);
        };
    }

    static Promotion rule(String name, boolean active, int priority, Set<CustomerSegment> eligibleSegments,
            RuleKind kind, int stage, boolean stopAfter, String ruleJson) {
        Promotion promotion = new Promotion(PromotionType.RULE, name, active, priority, eligibleSegments);
        promotion.kind = kind;
        promotion.stage = stage;
        promotion.stopAfter = stopAfter;
        promotion.ruleJson = ruleJson;
        return promotion;
    }

    static Promotion percentOffCategory(String name, boolean active, int priority,
            Set<CustomerSegment> eligibleSegments, String category, BigDecimal percent) {
        Promotion promotion = new Promotion(PromotionType.PERCENT_OFF_CATEGORY, name, active, priority,
                eligibleSegments);
        promotion.category = Categories.normalize(category);
        promotion.percent = percent;
        return promotion;
    }

    static Promotion tieredBulk(String name, boolean active, int priority, Set<CustomerSegment> eligibleSegments,
            UUID productId, int minQty, BigDecimal percent) {
        Promotion promotion = new Promotion(PromotionType.TIERED_BULK, name, active, priority, eligibleSegments);
        promotion.productId = productId;
        promotion.minQty = minQty;
        promotion.percent = percent;
        return promotion;
    }

    static Promotion buyXGetY(String name, boolean active, int priority, Set<CustomerSegment> eligibleSegments,
            UUID productId, int buyQty, int freeQty) {
        Promotion promotion = new Promotion(PromotionType.BUY_X_GET_Y, name, active, priority, eligibleSegments);
        promotion.productId = productId;
        promotion.buyQty = buyQty;
        promotion.freeQty = freeQty;
        return promotion;
    }
}
