package com.promoquoter.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    public static BigDecimal round(BigDecimal amount) {
        return amount.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return round(amount.multiply(percent).movePointLeft(2));
    }

    public static BigDecimal proportion(BigDecimal amount, long numerator, long denominator) {
        return amount.multiply(BigDecimal.valueOf(numerator)).divide(BigDecimal.valueOf(denominator), SCALE, ROUNDING);
    }
}
