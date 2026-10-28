package com.promoquoter.order;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.promoquoter.cart.CartNormalizer;
import com.promoquoter.cart.CartRequest;
import com.promoquoter.cart.NormalizedCart;
import com.promoquoter.cart.QuoteService;
import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.pricing.PriceBreakdown;
import com.promoquoter.product.InventoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    public record ConfirmResult(OrderResponse order, boolean replayed) {
    }

    private final CartNormalizer cartNormalizer;
    private final QuoteService quoteService;
    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final TransactionTemplate transactionTemplate;

    public ConfirmResult confirm(CartRequest request, String idempotencyKey) {
        validateKey(idempotencyKey);
        NormalizedCart cart = cartNormalizer.normalize(request);
        if (idempotencyKey == null) {
            return created(placeOrder(cart, null, null));
        }
        String fingerprint = RequestFingerprint.of(cart);
        Optional<ConfirmResult> replay = replayIfRecorded(idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        try {
            return created(placeOrder(cart, idempotencyKey, fingerprint));
        } catch (DataIntegrityViolationException raceLost) {
            return replayIfRecorded(idempotencyKey, fingerprint).orElseThrow(() -> raceLost);
        }
    }

    private OrderResponse placeOrder(NormalizedCart cart, String idempotencyKey, String fingerprint) {
        return transactionTemplate.execute(status -> {
            UUID orderId = UUID.randomUUID();
            if (idempotencyKey != null) {
                idempotencyRepository.saveAndFlush(new IdempotencyRecord(idempotencyKey, fingerprint, orderId));
            }
            inventoryService.reserve(quantitiesOf(cart));
            PriceBreakdown breakdown = quoteService.price(cart);
            Order order = orderRepository.saveAndFlush(Order.confirmed(orderId, breakdown, idempotencyKey));
            return OrderResponse.from(order);
        });
    }

    private Optional<ConfirmResult> replayIfRecorded(String idempotencyKey, String fingerprint) {
        return idempotencyRepository.findById(idempotencyKey).map(record -> {
            if (!record.getRequestHash().equals(fingerprint)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency-Key was already used with a different request");
            }
            OrderResponse order = transactionTemplate.execute(status -> orderRepository.findById(record.getOrderId())
                    .map(OrderResponse::from)
                    .orElseThrow(() -> new IllegalStateException(
                            "Order " + record.getOrderId() + " is missing for a recorded idempotency key")));
            log.info("Replayed order {} for idempotency key", order.orderId());
            return new ConfirmResult(order, true);
        });
    }

    private ConfirmResult created(OrderResponse order) {
        log.info("Order {} confirmed, total {}", order.orderId(), order.total());
        return new ConfirmResult(order, false);
    }

    private static Map<UUID, Integer> quantitiesOf(NormalizedCart cart) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        cart.items().forEach(item -> quantities.put(item.productId(), item.qty()));
        return quantities;
    }

    private static void validateKey(String idempotencyKey) {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH)) {
            throw new ApiException(ErrorCode.INVALID_IDEMPOTENCY_KEY,
                    "Idempotency-Key must be between 1 and " + MAX_IDEMPOTENCY_KEY_LENGTH + " non-blank characters");
        }
    }
}
