package com.promoquoter.cart.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.promoquoter.cart.dto.CartRequest;
import com.promoquoter.cart.dto.QuoteResponse;
import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.common.error.FieldViolation;
import com.promoquoter.common.observability.LogEvent;
import com.promoquoter.pricing.engine.PricingEngine;
import com.promoquoter.pricing.engine.RuleDefinition;
import com.promoquoter.pricing.model.ActionSpec;
import com.promoquoter.pricing.model.CartLine;
import com.promoquoter.pricing.model.GiftRequest;
import com.promoquoter.pricing.model.PriceBreakdown;
import com.promoquoter.pricing.model.SkippedRule;
import com.promoquoter.product.domain.ProductService;
import com.promoquoter.product.domain.ProductSnapshot;
import com.promoquoter.promotion.domain.PromotionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteService {

    private final CartNormalizer cartNormalizer;
    private final ProductService productService;
    private final PromotionService promotionService;
    private final PricingEngine pricingEngine;

    @Transactional(readOnly = true)
    public QuoteResponse quote(CartRequest request) {
        long startedNanos = System.nanoTime();
        NormalizedCart cart = cartNormalizer.normalize(request);
        QuotedCart quoted = price(cart, List.of(), false);
        PriceBreakdown breakdown = quoted.breakdown();
        if (!breakdown.violations().isEmpty()) {
            LogEvent.QUOTE_REJECTED.at(log).with("violationCount", breakdown.violations().size())
                    .message("Quote rejected by validation rules");
        }
        requireNoViolations(breakdown);
        LogEvent.QUOTE_PRICED.at(log)
                .with("itemCount", cart.items().size())
                .with("segment", cart.customerSegment())
                .with("appliedCount", breakdown.appliedPromotions().size())
                .with("adjustmentCount", breakdown.adjustments().size())
                .with("giftCount", quoted.gifts().size())
                .with("skippedCount", quoted.skipped().size())
                .with("total", breakdown.total())
                .with("durationMs", (System.nanoTime() - startedNanos) / 1_000_000)
                .message("Quote priced");
        return QuoteMapper.toResponse(quoted);
    }

    @Transactional(readOnly = true)
    public QuotedCart priceChecked(NormalizedCart cart, boolean stockReserved) {
        QuotedCart quoted = price(cart, List.of(), stockReserved);
        requireNoViolations(quoted.breakdown());
        return quoted;
    }

    @Transactional(readOnly = true)
    public QuotedCart price(NormalizedCart cart, List<RuleDefinition> extraRules, boolean stockReserved) {
        List<UUID> productIds = cart.items().stream().map(NormalizedCart.Item::productId).toList();
        Map<UUID, ProductSnapshot> products = productService.findSnapshots(productIds).stream()
                .collect(Collectors.toMap(ProductSnapshot::id, Function.identity()));
        rejectMissingProducts(productIds, products);
        List<CartLine> lines = cart.items().stream()
                .map(item -> toCartLine(products.get(item.productId()), item.qty()))
                .toList();
        List<RuleDefinition> rules = new ArrayList<>(promotionService.activeDefinitions());
        rules.addAll(extraRules);
        PriceBreakdown breakdown = pricingEngine.price(lines, cart.customerSegment(), rules);
        return resolveGifts(breakdown, cart, stockReserved);
    }

    @Transactional(readOnly = true)
    public Set<UUID> giftProductIds() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (RuleDefinition definition : promotionService.activeDefinitions()) {
            if (definition.action() instanceof ActionSpec.FreeGift gift) {
                ids.add(gift.productId());
            }
        }
        return ids;
    }

    private QuotedCart resolveGifts(PriceBreakdown breakdown, NormalizedCart cart, boolean stockReserved) {
        List<SkippedRule> skipped = new ArrayList<>(breakdown.skippedRules());
        if (breakdown.gifts().isEmpty()) {
            return new QuotedCart(breakdown, List.of(), skipped);
        }
        Map<UUID, ProductSnapshot> products = productService
                .findSnapshots(breakdown.gifts().stream().map(GiftRequest::productId).distinct().toList()).stream()
                .collect(Collectors.toMap(ProductSnapshot::id, Function.identity()));
        Map<UUID, Integer> taken = new HashMap<>();
        if (!stockReserved) {
            cart.items().forEach(item -> taken.merge(item.productId(), item.qty(), Integer::sum));
        }
        List<GiftLine> gifts = new ArrayList<>();
        for (GiftRequest request : breakdown.gifts()) {
            ProductSnapshot product = products.get(request.productId());
            if (product == null) {
                skipped.add(new SkippedRule(request.promotionId(), request.name(),
                        "gift product " + request.productId() + " does not exist"));
                LogEvent.GIFT_SKIPPED.at(log).with("productId", request.productId()).with("qty", request.qty())
                        .with("reason", "product does not exist").message("Gift skipped");
            } else if (product.stock() - taken.getOrDefault(product.id(), 0) < request.qty()) {
                skipped.add(new SkippedRule(request.promotionId(), request.name(),
                        "gift " + product.name() + " x" + request.qty() + " skipped: not enough stock"));
                LogEvent.GIFT_SKIPPED.at(log).with("productId", request.productId()).with("qty", request.qty())
                        .with("reason", "not enough stock").message("Gift skipped");
            } else {
                taken.merge(product.id(), request.qty(), Integer::sum);
                gifts.add(new GiftLine(product.id(), product.name(), product.category(), request.qty(),
                        request.promotionId(), request.name()));
            }
        }
        return new QuotedCart(breakdown, gifts, skipped);
    }

    private static void requireNoViolations(PriceBreakdown breakdown) {
        if (!breakdown.violations().isEmpty()) {
            throw new ApiException(ErrorCode.RULE_VIOLATION,
                    "The cart violates " + breakdown.violations().size() + " rule(s)",
                    breakdown.violations().stream().map(QuoteMapper::toEntry).toList());
        }
    }

    private static CartLine toCartLine(ProductSnapshot product, int qty) {
        return new CartLine(product.id(), product.name(), product.category(), product.price(), qty, product.stock());
    }

    private static void rejectMissingProducts(List<UUID> requested, Map<UUID, ProductSnapshot> found) {
        List<FieldViolation> missing = requested.stream()
                .filter(id -> !found.containsKey(id))
                .map(id -> new FieldViolation("productId", "Product " + id + " was not found"))
                .toList();
        if (!missing.isEmpty()) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND, "Some products in the cart were not found", missing);
        }
    }
}
