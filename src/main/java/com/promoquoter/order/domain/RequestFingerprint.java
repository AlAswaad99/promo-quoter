package com.promoquoter.order.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

import com.promoquoter.cart.domain.NormalizedCart;

final class RequestFingerprint {

    private RequestFingerprint() {
    }

    static String of(NormalizedCart cart) {
        String canonical = cart.customerSegment() + ";" + cart.items().stream()
                .map(item -> item.productId() + ":" + item.qty())
                .collect(Collectors.joining(","));
        return sha256(canonical);
    }

    static String keyHash(String idempotencyKey) {
        return sha256(idempotencyKey).substring(0, 8);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
