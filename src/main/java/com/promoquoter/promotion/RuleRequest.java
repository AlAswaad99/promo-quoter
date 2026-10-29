package com.promoquoter.promotion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.common.error.FieldViolation;
import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.RuleKind;
import com.promoquoter.pricing.condition.Condition;
import com.promoquoter.pricing.condition.FieldScope;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RuleRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull RuleKind kind,
        @Min(-1000000) @Max(1000000) Integer priority,
        @Min(0) @Max(999) Integer stage,
        Boolean stopAfter,
        Boolean active,
        Set<@NotNull CustomerSegment> eligibleSegments,
        JsonNode when,
        JsonNode target,
        JsonNode action) implements CreatePromotionRequest {

    public RuleRequest {
        priority = priority == null ? 0 : priority;
        stopAfter = stopAfter == null ? Boolean.FALSE : stopAfter;
        active = active == null ? Boolean.TRUE : active;
        eligibleSegments = eligibleSegments == null ? Set.of() : eligibleSegments;
    }

    ParsedRule parse(String path, List<FieldViolation> violations) {
        int before = violations.size();
        RuleParser parser = new RuleParser(violations);
        Condition parsedWhen = present(when) ? parser.parseCondition(when, FieldScope.CART, path + ".when") : null;
        Condition parsedTarget = present(target) ? parser.parseCondition(target, FieldScope.LINE, path + ".target") : null;
        ActionSpec parsedAction = parser.parseAction(action, kind, path + ".action");
        int resolvedStage = resolveStage(path, parsedAction, violations);
        if (violations.size() > before || parsedAction == null) {
            return null;
        }
        return new ParsedRule(kind, resolvedStage, parsedWhen, parsedTarget, parsedAction, parser.references());
    }

    private int resolveStage(String path, ActionSpec parsedAction, List<FieldViolation> violations) {
        if (stage == null) {
            return parsedAction == null ? 0 : parsedAction.kind().defaultStage();
        }
        boolean validation = kind == RuleKind.VALIDATION;
        int min = validation ? com.promoquoter.pricing.ActionKind.MIN_VALIDATION_STAGE : 0;
        int max = validation ? com.promoquoter.pricing.ActionKind.MAX_STAGE
                : com.promoquoter.pricing.ActionKind.MAX_PRICING_STAGE;
        if (stage < min || stage > max) {
            violations.add(new FieldViolation(path + ".stage", "must be between " + min + " and " + max + " for a "
                    + kind + " rule"));
        }
        return stage;
    }

    private static boolean present(JsonNode node) {
        return node != null && !node.isNull() && !node.isMissingNode();
    }

    @Override
    @JsonIgnore
    public UUID referencedProductId() {
        return null;
    }

    @Override
    public Promotion toEntity() {
        List<FieldViolation> violations = new ArrayList<>();
        ParsedRule parsed = parse("", violations);
        if (parsed == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed", violations);
        }
        return Promotion.rule(name, active, priority, eligibleSegments, parsed.kind(), parsed.stage(), stopAfter,
                parsed.toJson());
    }
}
