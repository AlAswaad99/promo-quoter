package com.promoquoter.cart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.common.error.FieldViolation;
import com.promoquoter.pricing.CartLine;
import com.promoquoter.pricing.PriceBreakdown;
import com.promoquoter.pricing.PricingEngine;
import com.promoquoter.pricing.RuleDefinition;
import com.promoquoter.product.ProductService;
import com.promoquoter.product.ProductSnapshot;
import com.promoquoter.promotion.PromotionService;

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
        return QuoteResponse.from(price(cartNormalizer.normalize(request)));
    }

    @Transactional(readOnly = true)
    public PriceBreakdown price(NormalizedCart cart) {
        return price(cart, List.of());
    }

    @Transactional(readOnly = true)
    public PriceBreakdown price(NormalizedCart cart, List<RuleDefinition> extraRules) {
        List<UUID> productIds = cart.items().stream().map(NormalizedCart.Item::productId).toList();
        Map<UUID, ProductSnapshot> products = productService.findSnapshots(productIds).stream()
                .collect(Collectors.toMap(ProductSnapshot::id, Function.identity()));
        rejectMissingProducts(productIds, products);
        List<CartLine> lines = cart.items().stream()
                .map(item -> toCartLine(products.get(item.productId()), item.qty()))
                .toList();
        List<RuleDefinition> rules = new ArrayList<>(promotionService.activeDefinitions());
        rules.addAll(extraRules);
        return pricingEngine.price(lines, cart.customerSegment(), rules);
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
