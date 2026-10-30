package com.promoquoter.cart.domain;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.promoquoter.cart.dto.RulePreviewRequest;
import com.promoquoter.cart.dto.RulePreviewResponse;
import com.promoquoter.common.observability.LogEvent;
import com.promoquoter.promotion.domain.PromotionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RulePreviewService {

    private final PromotionService promotionService;
    private final CartNormalizer cartNormalizer;
    private final QuoteService quoteService;

    @Transactional(readOnly = true)
    public RulePreviewResponse preview(RulePreviewRequest request) {
        PromotionService.PreviewRule rule = promotionService.previewRule(request.rule());
        NormalizedCart cart = cartNormalizer.normalize(request.cart());
        QuotedCart quoted = quoteService.price(cart, List.of(rule.definition()), false);
        LogEvent.RULE_PREVIEWED.at(log)
                .with("ruleKind", rule.definition().kind())
                .with("actionKind", rule.definition().action().kind())
                .with("violationCount", quoted.breakdown().violations().size())
                .message("Rule previewed");
        return new RulePreviewResponse(rule.response(), QuoteMapper.toResponse(quoted),
                quoted.breakdown().violations().stream().map(QuoteMapper::toEntry).toList());
    }
}
