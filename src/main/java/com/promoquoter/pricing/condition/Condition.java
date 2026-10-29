package com.promoquoter.pricing.condition;

import java.util.List;

public sealed interface Condition {

    record All(List<Condition> conditions) implements Condition {

        public All {
            if (conditions == null || conditions.isEmpty()) {
                throw new IllegalArgumentException("all needs at least one condition");
            }
            conditions = List.copyOf(conditions);
        }
    }

    record Any(List<Condition> conditions) implements Condition {

        public Any {
            if (conditions == null || conditions.isEmpty()) {
                throw new IllegalArgumentException("any needs at least one condition");
            }
            conditions = List.copyOf(conditions);
        }
    }

    record Not(Condition condition) implements Condition {

        public Not {
            if (condition == null) {
                throw new IllegalArgumentException("not needs a condition");
            }
        }
    }

    record Comparison(ConditionField field, Operator operator, Object value) implements Condition {

        public Comparison {
            if (field == null || operator == null || value == null) {
                throw new IllegalArgumentException("field, operator and value are required");
            }
            if (!field.type().supports(operator)) {
                throw new IllegalArgumentException(
                        "operator " + operator.symbol() + " is not valid for " + field.path());
            }
            if (operator.takesList()) {
                requireList(field, value, 1, Integer.MAX_VALUE);
                value = List.copyOf((List<?>) value);
            } else if (operator.takesRange()) {
                requireList(field, value, 2, 2);
                value = List.copyOf((List<?>) value);
            } else {
                requireType(field, value);
            }
        }

        private static void requireList(ConditionField field, Object value, int min, int max) {
            if (!(value instanceof List<?> list) || list.size() < min || list.size() > max) {
                throw new IllegalArgumentException("value for " + field.path() + " must be a list of "
                        + (min == max ? String.valueOf(min) : "at least " + min) + " item(s)");
            }
            list.forEach(item -> requireType(field, item));
        }

        private static void requireType(ConditionField field, Object value) {
            if (!field.type().valueType().isInstance(value)) {
                throw new IllegalArgumentException("value for " + field.path() + " must be a "
                        + field.type().valueType().getSimpleName());
            }
        }
    }
}
