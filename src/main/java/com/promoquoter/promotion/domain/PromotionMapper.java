package com.promoquoter.promotion.domain;

import java.util.ArrayList;
import java.util.List;

import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.common.error.FieldViolation;
import com.promoquoter.pricing.engine.RuleDefinition;
import com.promoquoter.promotion.dto.BuyXGetYRequest;
import com.promoquoter.promotion.dto.CreatePromotionRequest;
import com.promoquoter.promotion.dto.PercentOffCategoryRequest;
import com.promoquoter.promotion.dto.PromotionResponse;
import com.promoquoter.promotion.dto.RuleRequest;
import com.promoquoter.promotion.dto.TieredBulkRequest;

final class PromotionMapper {

    private PromotionMapper() {
    }

    static Promotion toEntity(CreatePromotionRequest request) {
        return switch (request) {
            case PercentOffCategoryRequest percentOff -> Promotion.percentOffCategory(percentOff.name(),
                    percentOff.active(), percentOff.priority(), percentOff.eligibleSegments(), percentOff.category(),
                    percentOff.percent());
            case BuyXGetYRequest buyXGetY -> Promotion.buyXGetY(buyXGetY.name(), buyXGetY.active(),
                    buyXGetY.priority(), buyXGetY.eligibleSegments(), buyXGetY.productId(), buyXGetY.buyQty(),
                    buyXGetY.freeQty());
            case TieredBulkRequest tieredBulk -> Promotion.tieredBulk(tieredBulk.name(), tieredBulk.active(),
                    tieredBulk.priority(), tieredBulk.eligibleSegments(), tieredBulk.productId(), tieredBulk.minQty(),
                    tieredBulk.percent());
            case RuleRequest rule -> ruleEntity(rule);
        };
    }

    static PromotionResponse toResponse(Promotion promotion) {
        RuleDefinition rule = promotion.toDefinition();
        return new PromotionResponse(promotion.getId(), promotion.getType(), promotion.getName(),
                promotion.isActive(), promotion.getPriority(),
                promotion.getEligibleSegments().stream().sorted().toList(), promotion.getCategory(),
                promotion.getPercent(), promotion.getProductId(), promotion.getBuyQty(), promotion.getFreeQty(),
                promotion.getMinQty(), promotion.getCreatedAt(), rule.kind(), rule.stage(), rule.stopAfter(),
                RuleJsonWriter.condition(rule.when()), RuleJsonWriter.condition(rule.target()),
                RuleJsonWriter.action(rule.action()));
    }

    private static Promotion ruleEntity(RuleRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        ParsedRule parsed = RuleRequestParser.parse(request, "", violations);
        if (parsed == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed", violations);
        }
        return Promotion.rule(request.name(), request.active(), request.priority(), request.eligibleSegments(),
                parsed.kind(), parsed.stage(), request.stopAfter(), parsed.toJson());
    }
}
