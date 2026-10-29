package com.promoquoter.cart.domain;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.promoquoter.cart.dto.CartItemRequest;
import com.promoquoter.cart.dto.CartRequest;
import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.common.error.FieldViolation;
import com.promoquoter.pricing.model.CustomerSegment;

@Component
public class CartNormalizer {

    public NormalizedCart normalize(CartRequest request) {
        Map<UUID, Integer> quantities = new TreeMap<>();
        for (CartItemRequest item : request.items()) {
            quantities.merge(item.productId(), item.qty(), Integer::sum);
        }
        List<FieldViolation> violations = quantities.entrySet().stream()
                .filter(entry -> entry.getValue() > CartRequest.MAX_QTY)
                .map(entry -> new FieldViolation("items", "Total quantity for product " + entry.getKey()
                        + " exceeds " + CartRequest.MAX_QTY))
                .toList();
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed", violations);
        }
        List<NormalizedCart.Item> items = quantities.entrySet().stream()
                .map(entry -> new NormalizedCart.Item(entry.getKey(), entry.getValue()))
                .toList();
        CustomerSegment segment = request.customerSegment() == null ? CustomerSegment.REGULAR
                : request.customerSegment();
        return new NormalizedCart(items, segment);
    }
}
