package com.promoquoter.cart;

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

import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.common.error.FieldViolation;
import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.CartLine;
import com.promoquoter.pricing.GiftRequest;
import com.promoquoter.pricing.PriceBreakdown;
import com.promoquoter.pricing.PricingEngine;
import com.promoquoter.pricing.RuleDefinition;
import com.promoquoter.pricing.SkippedRule;
import com.promoquoter.product.domain.ProductService;
import com.promoquoter.product.domain.ProductSnapshot;
import com.promoquoter.promotion.domain.PromotionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuoteService {

    private final CartNormalizer cartNormalizer;
    private final ProductService productService;
    private final PromotionService promotionService;
    private final PricingEngine pricingEngine;

    @Transactional(readOnly = true)
    public QuoteResponse quote(CartRequest request) {
        return QuoteResponse.from(priceChecked(cartNormalizer.normalize(request), false));
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
            } else if (product.stock() - taken.getOrDefault(product.id(), 0) < request.qty()) {
                skipped.add(new SkippedRule(request.promotionId(), request.name(),
                        "gift " + product.name() + " x" + request.qty() + " skipped: not enough stock"));
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
                    breakdown.violations().stream().map(RuleViolationEntry::from).toList());
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
