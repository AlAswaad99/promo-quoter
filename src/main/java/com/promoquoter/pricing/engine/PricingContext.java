package com.promoquoter.pricing.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.promoquoter.common.money.Money;
import com.promoquoter.pricing.condition.ConditionField;
import com.promoquoter.pricing.condition.Facts;
import com.promoquoter.pricing.model.ActionKind;
import com.promoquoter.pricing.model.AdjustmentDirection;
import com.promoquoter.pricing.model.AppliedPromotion;
import com.promoquoter.pricing.model.CartAdjustment;
import com.promoquoter.pricing.model.CartLine;
import com.promoquoter.pricing.model.CustomerSegment;
import com.promoquoter.pricing.model.GiftRequest;
import com.promoquoter.pricing.model.LineBreakdown;
import com.promoquoter.pricing.model.PriceBreakdown;
import com.promoquoter.pricing.model.RuleInfo;
import com.promoquoter.pricing.model.RuleViolation;
import com.promoquoter.pricing.model.SkippedRule;

public final class PricingContext {

    public static final int MAX_GIFT_UNITS = 100;

    private final CustomerSegment customerSegment;
    private final List<PricedLine> lines;
    private final LocalDate date;
    private final List<AppliedPromotion> appliedPromotions = new ArrayList<>();
    private final List<CartAdjustment> adjustments = new ArrayList<>();
    private final List<GiftRequest> gifts = new ArrayList<>();
    private final List<RuleViolation> violations = new ArrayList<>();
    private final List<SkippedRule> skippedRules = new ArrayList<>();
    private final Set<UUID> frozenProducts = new HashSet<>();
    private BigDecimal shipping;
    private BigDecimal cartDiscounts = Money.ZERO;
    private BigDecimal surcharges = Money.ZERO;
    private int giftUnits;
    private boolean stopped;
    private String stoppedBy;

    private PricingContext(CustomerSegment customerSegment, List<PricedLine> lines, LocalDate date,
            BigDecimal shippingFee) {
        this.customerSegment = customerSegment;
        this.lines = lines;
        this.date = date;
        this.shipping = Money.round(shippingFee);
    }

    public static PricingContext start(List<CartLine> cartLines, CustomerSegment customerSegment) {
        return start(cartLines, customerSegment, LocalDate.now(ZoneOffset.UTC), Money.ZERO);
    }

    public static PricingContext start(List<CartLine> cartLines, CustomerSegment customerSegment, LocalDate date,
            BigDecimal shippingFee) {
        return new PricingContext(customerSegment, cartLines.stream().map(PricedLine::new).toList(), date,
                shippingFee);
    }

    public CustomerSegment customerSegment() {
        return customerSegment;
    }

    public List<PricedLine> lines() {
        return lines;
    }

    public List<AppliedPromotion> appliedPromotions() {
        return Collections.unmodifiableList(appliedPromotions);
    }

    public List<CartAdjustment> adjustments() {
        return Collections.unmodifiableList(adjustments);
    }

    public List<GiftRequest> gifts() {
        return Collections.unmodifiableList(gifts);
    }

    public List<RuleViolation> violations() {
        return Collections.unmodifiableList(violations);
    }

    public List<SkippedRule> skippedRules() {
        return Collections.unmodifiableList(skippedRules);
    }

    public BigDecimal shipping() {
        return shipping;
    }

    public BigDecimal goodsTotal() {
        return sum(PricedLine::total).subtract(cartDiscounts);
    }

    public boolean isStopped() {
        return stopped;
    }

    public void stop(RuleInfo by) {
        stopped = true;
        stoppedBy = by.name();
    }

    public String stoppedBy() {
        return stoppedBy;
    }

    public void freeze(PricedLine line) {
        frozenProducts.add(line.productId());
    }

    public boolean isFrozen(PricedLine line) {
        return frozenProducts.contains(line.productId());
    }

    public void recordApplied(RuleInfo promotion, PricedLine line, BigDecimal amount) {
        appliedPromotions.add(new AppliedPromotion(nextSequence(), promotion.id(), promotion.name(),
                promotion.type(), line.productId(), amount));
    }

    public BigDecimal applyCartDiscount(RuleInfo promotion, BigDecimal requested) {
        BigDecimal remaining = sum(PricedLine::total).subtract(cartDiscounts);
        BigDecimal applied = Money.round(requested).max(Money.ZERO).min(remaining);
        if (applied.signum() > 0) {
            cartDiscounts = cartDiscounts.add(applied);
            adjustments.add(new CartAdjustment(nextSequence(), promotion.id(), promotion.name(),
                    ActionKind.AMOUNT_OFF_CART, AdjustmentDirection.DISCOUNT, applied));
        }
        return applied;
    }

    public BigDecimal addSurcharge(RuleInfo promotion, BigDecimal amount) {
        BigDecimal charged = Money.round(amount).max(Money.ZERO);
        if (charged.signum() > 0) {
            surcharges = surcharges.add(charged);
            adjustments.add(new CartAdjustment(nextSequence(), promotion.id(), promotion.name(),
                    ActionKind.SURCHARGE, AdjustmentDirection.CHARGE, charged));
        }
        return charged;
    }

    public BigDecimal waiveShipping(RuleInfo promotion) {
        BigDecimal waived = shipping;
        if (waived.signum() > 0) {
            shipping = Money.ZERO;
            adjustments.add(new CartAdjustment(nextSequence(), promotion.id(), promotion.name(),
                    ActionKind.SHIPPING_WAIVER, AdjustmentDirection.DISCOUNT, waived));
        }
        return waived;
    }

    public int requestGift(RuleInfo promotion, UUID productId, int qty) {
        int granted = Math.min(qty, MAX_GIFT_UNITS - giftUnits);
        if (granted > 0) {
            giftUnits += granted;
            gifts.add(new GiftRequest(promotion.id(), promotion.name(), productId, granted));
        }
        return granted;
    }

    public void reject(RuleInfo promotion, String message) {
        violations.add(new RuleViolation(promotion.id(), promotion.name(), message));
    }

    public void skip(RuleInfo promotion, String reason) {
        skippedRules.add(new SkippedRule(promotion.id(), promotion.name(), reason));
    }

    public Facts cartFacts() {
        return field -> switch (field) {
            case CART_SUBTOTAL -> sum(PricedLine::subtotal);
            case CART_TOTAL -> goodsTotal();
            case CART_ITEM_COUNT -> lines.stream().mapToInt(PricedLine::qty).sum();
            case CUSTOMER_SEGMENT -> customerSegment;
            case DATE -> date;
            default -> throw new IllegalStateException(field.path() + " is not a cart field");
        };
    }

    public Facts lineFacts(PricedLine line) {
        return field -> lineValue(field, line);
    }

    private static Object lineValue(ConditionField field, PricedLine line) {
        return switch (field) {
            case PRODUCT_ID -> line.productId();
            case PRODUCT_NAME -> line.name();
            case PRODUCT_CATEGORY -> line.category();
            case PRODUCT_PRICE -> line.unitPrice();
            case PRODUCT_STOCK -> line.stock();
            case LINE_QTY -> line.qty();
            default -> throw new IllegalStateException(field.path() + " is not a line field");
        };
    }

    public PriceBreakdown toBreakdown() {
        BigDecimal subtotal = sum(PricedLine::subtotal);
        BigDecimal goods = goodsTotal();
        BigDecimal total = goods.add(surcharges).add(shipping);
        return new PriceBreakdown(customerSegment, lines.stream().map(PricingContext::breakdownOf).toList(),
                List.copyOf(appliedPromotions), subtotal, subtotal.subtract(goods), total, List.copyOf(adjustments),
                shipping, List.copyOf(gifts), List.copyOf(violations), List.copyOf(skippedRules));
    }

    private static LineBreakdown breakdownOf(PricedLine line) {
        return new LineBreakdown(line.productId(), line.name(), line.category(), line.unitPrice(), line.qty(),
                line.subtotal(), line.discount(), line.total());
    }

    private int nextSequence() {
        return appliedPromotions.size() + adjustments.size() + 1;
    }

    private BigDecimal sum(Function<PricedLine, BigDecimal> selector) {
        return lines.stream().map(selector).reduce(Money.ZERO, BigDecimal::add);
    }
}
