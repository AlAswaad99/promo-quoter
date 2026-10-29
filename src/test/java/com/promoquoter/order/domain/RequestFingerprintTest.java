package com.promoquoter.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.promoquoter.cart.CartItemRequest;
import com.promoquoter.cart.CartNormalizer;
import com.promoquoter.cart.CartRequest;
import com.promoquoter.cart.NormalizedCart;
import com.promoquoter.pricing.CustomerSegment;

class RequestFingerprintTest {

    private static final UUID MUG = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LAMP = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final CartNormalizer normalizer = new CartNormalizer();

    private NormalizedCart normalized(CustomerSegment segment, CartItemRequest... items) {
        return normalizer.normalize(new CartRequest(List.of(items), segment));
    }

    @Test
    void isA64CharacterHexDigest() {
        String fingerprint = RequestFingerprint.of(normalized(null, new CartItemRequest(MUG, 1)));

        assertThat(fingerprint).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void isStableAcrossItemOrder() {
        String first = RequestFingerprint.of(normalized(null, new CartItemRequest(MUG, 1), new CartItemRequest(LAMP, 2)));
        String second = RequestFingerprint.of(normalized(null, new CartItemRequest(LAMP, 2), new CartItemRequest(MUG, 1)));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void isStableWhenDuplicateLinesAreMerged() {
        String split = RequestFingerprint.of(normalized(null, new CartItemRequest(MUG, 1), new CartItemRequest(MUG, 2)));
        String merged = RequestFingerprint.of(normalized(null, new CartItemRequest(MUG, 3)));

        assertThat(split).isEqualTo(merged);
    }

    @Test
    void defaultSegmentEqualsExplicitRegular() {
        String implicit = RequestFingerprint.of(normalized(null, new CartItemRequest(MUG, 1)));
        String explicit = RequestFingerprint.of(normalized(CustomerSegment.REGULAR, new CartItemRequest(MUG, 1)));

        assertThat(implicit).isEqualTo(explicit);
    }

    @Test
    void differsWhenAQuantityChanges() {
        String one = RequestFingerprint.of(normalized(null, new CartItemRequest(MUG, 1)));
        String two = RequestFingerprint.of(normalized(null, new CartItemRequest(MUG, 2)));

        assertThat(one).isNotEqualTo(two);
    }

    @Test
    void differsWhenTheSegmentChanges() {
        String regular = RequestFingerprint.of(normalized(CustomerSegment.REGULAR, new CartItemRequest(MUG, 1)));
        String vip = RequestFingerprint.of(normalized(CustomerSegment.VIP, new CartItemRequest(MUG, 1)));

        assertThat(regular).isNotEqualTo(vip);
    }

    @Test
    void differsWhenAProductChanges() {
        String mug = RequestFingerprint.of(normalized(null, new CartItemRequest(MUG, 1)));
        String lamp = RequestFingerprint.of(normalized(null, new CartItemRequest(LAMP, 1)));

        assertThat(mug).isNotEqualTo(lamp);
    }
}
