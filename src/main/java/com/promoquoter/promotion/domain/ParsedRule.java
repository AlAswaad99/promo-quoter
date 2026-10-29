package com.promoquoter.promotion.domain;

import java.util.List;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.RuleKind;
import com.promoquoter.pricing.condition.Condition;

record ParsedRule(
        RuleKind kind,
        int stage,
        Condition when,
        Condition target,
        ActionSpec action,
        List<RuleParser.ProductReference> references) {

    String toJson() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        if (when != null) {
            node.set("when", RuleJsonWriter.condition(when));
        }
        if (target != null) {
            node.set("target", RuleJsonWriter.condition(target));
        }
        node.set("action", RuleJsonWriter.action(action));
        return node.toString();
    }
}
