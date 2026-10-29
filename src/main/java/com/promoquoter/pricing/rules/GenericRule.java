package com.promoquoter.pricing.rules;

import java.util.List;

import com.promoquoter.pricing.PricedLine;
import com.promoquoter.pricing.PricingContext;
import com.promoquoter.pricing.PricingRule;
import com.promoquoter.pricing.RuleDefinition;
import com.promoquoter.pricing.RuleInfo;
import com.promoquoter.pricing.RuleKind;
import com.promoquoter.pricing.condition.ConditionEvaluator;

public abstract class GenericRule implements PricingRule {

    private final RuleDefinition definition;

    protected GenericRule(RuleDefinition definition) {
        this.definition = definition;
    }

    @Override
    public final RuleInfo definition() {
        return definition;
    }

    public final RuleDefinition ruleDefinition() {
        return definition;
    }

    @Override
    public final RuleKind kind() {
        return definition.kind();
    }

    @Override
    public final boolean stopAfter() {
        return definition.stopAfter();
    }

    @Override
    public final boolean targetsLines() {
        return definition.action().kind().targetsLines();
    }

    @Override
    public final int stage() {
        return definition.stage();
    }

    @Override
    public final void apply(PricingContext context) {
        if (definition.when() != null && !ConditionEvaluator.matches(definition.when(), context.cartFacts())) {
            return;
        }
        execute(context);
    }

    protected abstract void execute(PricingContext context);

    protected final List<PricedLine> targetLines(PricingContext context) {
        return context.lines().stream()
                .filter(line -> !context.isFrozen(line))
                .filter(line -> definition.target() == null
                        || ConditionEvaluator.matches(definition.target(), context.lineFacts(line)))
                .toList();
    }
}
