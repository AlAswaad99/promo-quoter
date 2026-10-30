package com.promoquoter.order.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.promoquoter.cart.domain.CartNormalizer;
import com.promoquoter.cart.domain.GiftLine;
import com.promoquoter.cart.domain.NormalizedCart;
import com.promoquoter.cart.domain.QuoteService;
import com.promoquoter.cart.domain.QuotedCart;
import com.promoquoter.cart.dto.CartRequest;
import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.common.observability.LogEvent;
import com.promoquoter.order.dto.OrderResponse;
import com.promoquoter.product.domain.InventoryService;

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
            return created(placeOrder(cart, null, null), null);
        }
        String fingerprint = RequestFingerprint.of(cart);
        Optional<ConfirmResult> replay = replayIfRecorded(idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        try {
            return created(placeOrder(cart, idempotencyKey, fingerprint), idempotencyKey);
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
            inventoryService.reserve(quantitiesOf(cart), quoteService.giftProductIds());
            QuotedCart quoted = quoteService.priceChecked(cart, true);
            inventoryService.reserveGifts(giftQuantities(quoted));
            Order order = orderRepository.saveAndFlush(Order.confirmed(orderId, quoted, idempotencyKey));
            LogEvent.STOCK_RESERVED.at(log)
                    .with("orderId", orderId)
                    .with("productCount", cart.items().size())
                    .with("giftUnits", quoted.gifts().stream().mapToInt(GiftLine::qty).sum())
                    .message("Stock reserved");
            return OrderMapper.toResponse(order);
        });
    }

    private Optional<ConfirmResult> replayIfRecorded(String idempotencyKey, String fingerprint) {
        return idempotencyRepository.findById(idempotencyKey).map(record -> {
            if (!record.getRequestHash().equals(fingerprint)) {
                LogEvent.IDEMPOTENCY_CONFLICT.at(log).with("idempotencyKeyHash", RequestFingerprint.keyHash(idempotencyKey))
                        .message("Idempotency key reused with a different request");
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency-Key was already used with a different request");
            }
            OrderResponse order = transactionTemplate.execute(status -> orderRepository.findById(record.getOrderId())
                    .map(OrderMapper::toResponse)
                    .orElseThrow(() -> new IllegalStateException(
                            "Order " + record.getOrderId() + " is missing for a recorded idempotency key")));
            LogEvent.ORDER_REPLAYED.at(log)
                    .with("orderId", order.orderId())
                    .with("idempotencyKeyHash", RequestFingerprint.keyHash(idempotencyKey))
                    .message("Order replayed");
            return new ConfirmResult(order, true);
        });
    }

    private ConfirmResult created(OrderResponse order, String idempotencyKey) {
        LogEvent.ORDER_CONFIRMED.at(log)
                .with("orderId", order.orderId())
                .with("total", order.total())
                .with("shipping", order.shipping())
                .with("giftCount", order.gifts().size())
                .with("idempotencyKeyHash", idempotencyKey == null ? null : RequestFingerprint.keyHash(idempotencyKey))
                .message("Order confirmed");
        return new ConfirmResult(order, false);
    }

    private static Map<UUID, Integer> giftQuantities(QuotedCart quoted) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        quoted.gifts().forEach(gift -> quantities.merge(gift.productId(), gift.qty(), Integer::sum));
        return quantities;
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
