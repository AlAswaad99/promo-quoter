package com.promoquoter.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.pricing.CustomerSegment;

class CartNormalizerTest {

    private final CartNormalizer normalizer = new CartNormalizer();

    private static final UUID FIRST = new UUID(0, 1);
    private static final UUID SECOND = new UUID(0, 2);

    @Test
    void mergesDuplicateProductsBySummingQuantities() {
        NormalizedCart cart = normalizer.normalize(new CartRequest(List.of(new CartItemRequest(FIRST, 1),
                new CartItemRequest(SECOND, 4), new CartItemRequest(FIRST, 2)), CustomerSegment.VIP));

        assertThat(cart.items()).containsExactly(new NormalizedCart.Item(FIRST, 3), new NormalizedCart.Item(SECOND, 4));
    }

    @Test
    void ordersItemsByProductIdSoEquivalentCartsMatch() {
        NormalizedCart one = normalizer.normalize(new CartRequest(
                List.of(new CartItemRequest(SECOND, 1), new CartItemRequest(FIRST, 1)), null));
        NormalizedCart other = normalizer.normalize(new CartRequest(
                List.of(new CartItemRequest(FIRST, 1), new CartItemRequest(SECOND, 1)), null));

        assertThat(one).isEqualTo(other);
    }

    @Test
    void defaultsTheSegmentToRegular() {
        NormalizedCart cart = normalizer.normalize(new CartRequest(List.of(new CartItemRequest(FIRST, 1)), null));

        assertThat(cart.customerSegment()).isEqualTo(CustomerSegment.REGULAR);
    }

    @Test
    void keepsAnExplicitSegment() {
        NormalizedCart cart = normalizer.normalize(
                new CartRequest(List.of(new CartItemRequest(FIRST, 1)), CustomerSegment.EMPLOYEE));

        assertThat(cart.customerSegment()).isEqualTo(CustomerSegment.EMPLOYEE);
    }

    @Test
    void rejectsMergedQuantitiesAboveTheLimit() {
        CartRequest request = new CartRequest(
                List.of(new CartItemRequest(FIRST, 600), new CartItemRequest(FIRST, 401)), null);

        assertThatThrownBy(() -> normalizer.normalize(request))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void acceptsMergedQuantitiesExactlyAtTheLimit() {
        NormalizedCart cart = normalizer.normalize(new CartRequest(
                List.of(new CartItemRequest(FIRST, 600), new CartItemRequest(FIRST, 400)), null));

        assertThat(cart.items()).containsExactly(new NormalizedCart.Item(FIRST, 1000));
    }
}
