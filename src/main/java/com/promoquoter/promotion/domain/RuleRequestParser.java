package com.promoquoter.promotion.domain;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.promoquoter.common.error.FieldViolation;
import com.promoquoter.pricing.ActionKind;
import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.RuleKind;
import com.promoquoter.pricing.condition.Condition;
import com.promoquoter.pricing.condition.FieldScope;
import com.promoquoter.promotion.dto.RuleRequest;

final class RuleRequestParser {

    private RuleRequestParser() {
    }

    static ParsedRule parse(RuleRequest request, String path, List<FieldViolation> violations) {
        int before = violations.size();
        RuleParser parser = new RuleParser(violations);
        Condition when = present(request.when())
                ? parser.parseCondition(request.when(), FieldScope.CART, path + ".when") : null;
        Condition target = present(request.target())
                ? parser.parseCondition(request.target(), FieldScope.LINE, path + ".target") : null;
        ActionSpec action = parser.parseAction(request.action(), request.kind(), path + ".action");
        int stage = resolveStage(request, path, action, violations);
        if (violations.size() > before || action == null) {
            return null;
        }
        return new ParsedRule(request.kind(), stage, when, target, action, parser.references());
    }

    private static int resolveStage(RuleRequest request, String path, ActionSpec action,
            List<FieldViolation> violations) {
        if (request.stage() == null) {
            return action == null ? 0 : action.kind().defaultStage();
        }
        boolean validation = request.kind() == RuleKind.VALIDATION;
        int min = validation ? ActionKind.MIN_VALIDATION_STAGE : 0;
        int max = validation ? ActionKind.MAX_STAGE : ActionKind.MAX_PRICING_STAGE;
        if (request.stage() < min || request.stage() > max) {
            violations.add(new FieldViolation(path + ".stage", "must be between " + min + " and " + max + " for a "
                    + request.kind() + " rule"));
        }
        return request.stage();
    }

    private static boolean present(JsonNode node) {
        return node != null && !node.isNull() && !node.isMissingNode();
    }
}
