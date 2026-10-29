package com.promoquoter.promotion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promoquoter.common.error.FieldViolation;
import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.PromotionType;
import com.promoquoter.pricing.RuleDefinition;
import com.promoquoter.pricing.condition.Condition;
import com.promoquoter.pricing.condition.FieldScope;

final class RuleStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RuleStore() {
    }

    static RuleDefinition definition(Promotion promotion) {
        JsonNode json = read(promotion);
        List<FieldViolation> violations = new ArrayList<>();
        RuleParser parser = new RuleParser(violations);
        Condition when = json.has("when") ? parser.parseCondition(json.get("when"), FieldScope.CART, "when") : null;
        Condition target = json.has("target")
                ? parser.parseCondition(json.get("target"), FieldScope.LINE, "target") : null;
        ActionSpec action = parser.parseAction(json.get("action"), promotion.getKind(), "action");
        if (!violations.isEmpty() || action == null) {
            throw new IllegalStateException("Stored rule " + promotion.getId() + " is invalid: " + violations);
        }
        Set<CustomerSegment> segments = Set.copyOf(promotion.getEligibleSegments());
        return new RuleDefinition(promotion.getId(), PromotionType.RULE, promotion.getName(), promotion.getKind(),
                promotion.isActive(), promotion.getPriority(), promotion.getStage(), promotion.isStopAfter(),
                promotion.getCreatedAt(), segments, when, target, action);
    }

    private static JsonNode read(Promotion promotion) {
        try {
            return MAPPER.readTree(promotion.getRuleJson());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored rule " + promotion.getId() + " is not valid JSON", e);
        }
    }
}
