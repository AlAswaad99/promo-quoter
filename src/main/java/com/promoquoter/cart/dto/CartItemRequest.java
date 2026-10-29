package com.promoquoter.cart.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemRequest(
        @NotNull UUID productId,
        @NotNull @Min(1) @Max(CartRequest.MAX_QTY) Integer qty) {
}
