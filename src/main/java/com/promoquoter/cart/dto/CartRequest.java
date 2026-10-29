package com.promoquoter.cart.dto;

import java.util.List;

import com.promoquoter.pricing.model.CustomerSegment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CartRequest(
        @NotEmpty @Size(max = CartRequest.MAX_ITEMS) List<@NotNull @Valid CartItemRequest> items,
        CustomerSegment customerSegment) {

    public static final int MAX_ITEMS = 100;
    public static final int MAX_QTY = 1000;
}
