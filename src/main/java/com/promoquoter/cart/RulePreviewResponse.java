package com.promoquoter.cart;

import java.util.List;

import com.promoquoter.promotion.PromotionResponse;

public record RulePreviewResponse(PromotionResponse rule, QuoteResponse quote, List<RuleViolationEntry> violations) {
}
