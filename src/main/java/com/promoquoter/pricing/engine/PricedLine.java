package com.promoquoter.pricing.engine;

import java.math.BigDecimal;
import java.util.UUID;

import com.promoquoter.common.money.Money;
import com.promoquoter.pricing.model.CartLine;

public final class PricedLine {

    private final CartLine cartLine;
    private final BigDecimal subtotal;
    private BigDecimal total;

    PricedLine(CartLine cartLine) {
        this.cartLine = cartLine;
        this.subtotal = Money.round(cartLine.unitPrice().multiply(BigDecimal.valueOf(cartLine.qty())));
        this.total = subtotal;
    }

    public UUID productId() {
        return cartLine.productId();
    }

    public String name() {
        return cartLine.name();
    }

    public String category() {
        return cartLine.category();
    }

    public BigDecimal unitPrice() {
        return cartLine.unitPrice();
    }

    public int qty() {
        return cartLine.qty();
    }

    public int stock() {
        return cartLine.stock();
    }

    public BigDecimal subtotal() {
        return subtotal;
    }

    public BigDecimal total() {
        return total;
    }

    public BigDecimal discount() {
        return subtotal.subtract(total);
    }

    public BigDecimal applyDiscount(BigDecimal requested) {
        BigDecimal applied = Money.round(requested).max(Money.ZERO).min(total);
        total = total.subtract(applied);
        return applied;
    }
}
