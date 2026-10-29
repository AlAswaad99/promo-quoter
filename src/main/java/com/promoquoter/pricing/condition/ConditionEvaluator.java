package com.promoquoter.pricing.condition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import com.promoquoter.pricing.condition.Condition.All;
import com.promoquoter.pricing.condition.Condition.Any;
import com.promoquoter.pricing.condition.Condition.Comparison;
import com.promoquoter.pricing.condition.Condition.Not;

public final class ConditionEvaluator {

    private ConditionEvaluator() {
    }

    public static boolean matches(Condition condition, Facts facts) {
        return switch (condition) {
            case All all -> all.conditions().stream().allMatch(child -> matches(child, facts));
            case Any any -> any.conditions().stream().anyMatch(child -> matches(child, facts));
            case Not not -> !matches(not.condition(), facts);
            case Comparison comparison -> compare(comparison, facts.valueOf(comparison.field()));
        };
    }

    private static boolean compare(Comparison comparison, Object actual) {
        Operator operator = comparison.operator();
        Object expected = comparison.value();
        return switch (operator) {
            case EQ -> equal(comparison, actual, expected);
            case NE -> !equal(comparison, actual, expected);
            case GT -> order(comparison, actual, expected) > 0;
            case GTE -> order(comparison, actual, expected) >= 0;
            case LT -> order(comparison, actual, expected) < 0;
            case LTE -> order(comparison, actual, expected) <= 0;
            case BETWEEN -> {
                List<?> bounds = (List<?>) expected;
                yield order(comparison, actual, bounds.get(0)) >= 0 && order(comparison, actual, bounds.get(1)) <= 0;
            }
            case IN -> ((List<?>) expected).stream().anyMatch(item -> equal(comparison, actual, item));
            case NOT_IN -> ((List<?>) expected).stream().noneMatch(item -> equal(comparison, actual, item));
            case STARTS_WITH -> text(actual).startsWith(text(expected));
            case CONTAINS -> text(actual).contains(text(expected));
        };
    }

    private static boolean equal(Comparison comparison, Object actual, Object expected) {
        return switch (comparison.field().type()) {
            case NUMBER -> number(actual).compareTo(number(expected)) == 0;
            case TEXT -> text(actual).equals(text(expected));
            case DATE -> ((LocalDate) actual).isEqual((LocalDate) expected);
            case ID, SEGMENT -> actual.equals(expected);
        };
    }

    private static int order(Comparison comparison, Object actual, Object expected) {
        return switch (comparison.field().type()) {
            case NUMBER -> number(actual).compareTo(number(expected));
            case DATE -> ((LocalDate) actual).compareTo((LocalDate) expected);
            default -> throw new IllegalStateException("Ordering is not defined for " + comparison.field().path());
        };
    }

    private static BigDecimal number(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private static String text(Object value) {
        return value.toString().trim().toUpperCase(Locale.ROOT);
    }
}
