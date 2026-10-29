package com.promoquoter.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PricingPipelineTest {

    private final List<String> executed = new ArrayList<>();

    private PricingRule rule(String name, int stage, RuleDefinition definition) {
        return new PricingRule() {
            @Override
            public RuleDefinition definition() {
                return definition;
            }

            @Override
            public int stage() {
                return stage;
            }

            @Override
            public void apply(PricingContext context) {
                executed.add(name);
            }
        };
    }

    private PricingRule rule(String name, int stage, int priority, String createdAt) {
        return rule(name, stage, Promos.percentOff("K", "5").priority(priority).createdAt(createdAt).build());
    }

    private void run(PricingRule... rules) {
        new PricingPipeline(List.of(rules)).run(PricingContext.start(List.of(), CustomerSegment.REGULAR));
    }

    @Test
    void ordersByStageRegardlessOfInsertionOrder() {
        run(rule("third", 300, 0, "2026-01-01T00:00:00Z"), rule("first", 100, 0, "2026-01-01T00:00:00Z"),
                rule("second", 200, 0, "2026-01-01T00:00:00Z"));

        assertThat(executed).containsExactly("first", "second", "third");
    }

    @Test
    void ordersByPriorityWithinAStage() {
        run(rule("low", 100, 5, "2026-01-01T00:00:00Z"), rule("high", 100, 1, "2026-01-01T00:00:00Z"));

        assertThat(executed).containsExactly("high", "low");
    }

    @Test
    void ordersByCreationTimeWhenPriorityTies() {
        run(rule("newer", 100, 0, "2026-02-01T00:00:00Z"), rule("older", 100, 0, "2026-01-01T00:00:00Z"));

        assertThat(executed).containsExactly("older", "newer");
    }

    @Test
    void ordersByIdWhenEverythingElseTies() {
        UUID smaller = new UUID(0, 1);
        UUID larger = new UUID(0, 2);
        RuleDefinition first = Promos.percentOff("K", "5").id(smaller).build();
        RuleDefinition second = Promos.percentOff("K", "5").id(larger).build();

        run(rule("larger", 100, second), rule("smaller", 100, first));

        assertThat(executed).containsExactly("smaller", "larger");
    }

    @Test
    void stageWinsOverPriority() {
        run(rule("laterStageHighPriority", 200, -10, "2026-01-01T00:00:00Z"),
                rule("earlierStageLowPriority", 100, 99, "2026-01-01T00:00:00Z"));

        assertThat(executed).containsExactly("earlierStageLowPriority", "laterStageHighPriority");
    }

    @Test
    void runsNothingForAnEmptyRuleSet() {
        run();

        assertThat(executed).isEmpty();
    }
}
