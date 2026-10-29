package com.promoquoter.cart.domain;

import java.util.List;

import com.promoquoter.pricing.PriceBreakdown;
import com.promoquoter.pricing.SkippedRule;

public record QuotedCart(PriceBreakdown breakdown, List<GiftLine> gifts, List<SkippedRule> skipped) {
}
