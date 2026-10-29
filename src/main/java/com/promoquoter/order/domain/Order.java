package com.promoquoter.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import com.promoquoter.cart.QuotedCart;
import com.promoquoter.order.dto.OrderStatus;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.PriceBreakdown;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Order {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_segment", nullable = false, length = 20)
    private CustomerSegment customerSegment;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "total_discount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDiscount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal shipping = BigDecimal.ZERO.setScale(2);

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "order_line", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_index")
    @BatchSize(size = 100)
    private List<OrderLineSnapshot> lines = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "order_promotion", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "entry_index")
    @BatchSize(size = 100)
    private List<AppliedPromotionSnapshot> appliedPromotions = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "order_adjustment", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "entry_index")
    @BatchSize(size = 100)
    private List<CartAdjustmentSnapshot> adjustments = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "order_gift", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "entry_index")
    @BatchSize(size = 100)
    private List<GiftLineSnapshot> giftLines = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "order_note", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "entry_index")
    @Column(name = "note", nullable = false, length = 500)
    @BatchSize(size = 100)
    private List<String> notes = new ArrayList<>();

    static Order confirmed(UUID id, PriceBreakdown breakdown, String idempotencyKey) {
        Order order = new Order();
        order.id = id;
        order.status = OrderStatus.CONFIRMED;
        order.customerSegment = breakdown.customerSegment();
        order.subtotal = breakdown.subtotal();
        order.totalDiscount = breakdown.totalDiscount();
        order.total = breakdown.total();
        order.shipping = breakdown.shipping();
        order.idempotencyKey = idempotencyKey;
        order.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        breakdown.lines().forEach(line -> order.lines.add(new OrderLineSnapshot(line.productId(), line.name(),
                line.category(), line.unitPrice(), line.qty(), line.subtotal(), line.discount(), line.total())));
        breakdown.appliedPromotions().forEach(applied -> order.appliedPromotions.add(new AppliedPromotionSnapshot(
                applied.sequence(), applied.promotionId(), applied.name(), applied.type(), applied.productId(),
                applied.amount())));
        breakdown.adjustments().forEach(adjustment -> order.adjustments.add(new CartAdjustmentSnapshot(
                adjustment.sequence(), adjustment.promotionId(), adjustment.name(), adjustment.action(),
                adjustment.direction(), adjustment.amount())));
        return order;
    }

    static Order confirmed(UUID id, QuotedCart quoted, String idempotencyKey) {
        Order order = confirmed(id, quoted.breakdown(), idempotencyKey);
        quoted.gifts().forEach(gift -> order.giftLines.add(new GiftLineSnapshot(gift.productId(), gift.name(),
                gift.category(), gift.qty(), gift.promotionId(), gift.promotionName())));
        quoted.skipped().forEach(skipped -> order.notes.add(note(skipped.name(), skipped.reason())));
        return order;
    }

    private static String note(String ruleName, String reason) {
        String text = ruleName + ": " + reason;
        return text.length() <= 500 ? text : text.substring(0, 500);
    }
}
