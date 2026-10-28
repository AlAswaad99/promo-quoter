package com.promoquoter.cart;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final QuoteService quoteService;

    @PostMapping("/quote")
    public QuoteResponse quote(@RequestBody @Valid CartRequest request) {
        return quoteService.quote(request);
    }
}
