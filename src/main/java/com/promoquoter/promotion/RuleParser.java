package com.promoquoter.promotion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.promoquoter.common.error.FieldViolation;
import com.promoquoter.pricing.ActionKind;
import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.RuleKind;
import com.promoquoter.pricing.condition.Condition;
import com.promoquoter.pricing.condition.ConditionField;
import com.promoquoter.pricing.condition.FieldScope;
import com.promoquoter.pricing.condition.FieldType;
import com.promoquoter.pricing.condition.Operator;

public final class RuleParser {

    public static final int MAX_DEPTH = 5;
    public static final int MAX_CONDITIONS = 20;
    public static final int MAX_LIST_SIZE = 100;
    public static final int MAX_TEXT_LENGTH = 200;
    public static final int MAX_MESSAGE_LENGTH = 300;
    public static final int MAX_FREE_UNITS = 1000;
    public static final int MAX_GIFT_QTY = 100;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    public record ProductReference(String path, UUID productId) {
    }

    private final List<FieldViolation> violations;
    private final List<ProductReference> references = new ArrayList<>();
    private int conditionCount;

    public RuleParser(List<FieldViolation> violations) {
        this.violations = violations;
    }

    public List<ProductReference> references() {
        return List.copyOf(references);
    }

    public Condition parseCondition(JsonNode node, FieldScope scope, String path) {
        conditionCount = 0;
        int before = violations.size();
        Condition condition = condition(node, scope, path, 1);
        if (conditionCount > MAX_CONDITIONS) {
            violations.add(new FieldViolation(path, "has more than " + MAX_CONDITIONS + " conditions"));
            return null;
        }
        return violations.size() == before ? condition : null;
    }

    public ActionSpec parseAction(JsonNode node, RuleKind ruleKind, String path) {
        int before = violations.size();
        if (node == null || !node.isObject()) {
            violations.add(new FieldViolation(path, "is required and must be an object"));
            return null;
        }
        JsonNode kindNode = node.get("kind");
        if (kindNode == null || !kindNode.isTextual()) {
            violations.add(new FieldViolation(path + ".kind", "is required"));
            return null;
        }
        ActionKind kind;
        try {
            kind = ActionKind.valueOf(kindNode.asText());
        } catch (IllegalArgumentException e) {
            violations.add(new FieldViolation(path + ".kind", "Unknown action kind '" + kindNode.asText()
                    + "'. Allowed: " + Arrays.toString(ActionKind.values())));
            return null;
        }
        if (kind.ruleKind() != ruleKind) {
            violations.add(new FieldViolation(path + ".kind",
                    "Action " + kind + " cannot be used in a " + ruleKind + " rule"));
            return null;
        }
        rejectUnknownKeys(node, path, parametersOf(kind));
        ActionSpec spec = switch (kind) {
            case PERCENT_OFF -> {
                BigDecimal percent = percent(node.get("percent"), path + ".percent");
                yield percent == null ? null : new ActionSpec.PercentOff(percent);
            }
            case AMOUNT_OFF -> {
                BigDecimal amount = amount(node.get("amount"), path + ".amount");
                yield amount == null ? null : new ActionSpec.AmountOff(amount);
            }
            case AMOUNT_OFF_CART -> {
                BigDecimal amount = amount(node.get("amount"), path + ".amount");
                yield amount == null ? null : new ActionSpec.AmountOffCart(amount);
            }
            case SURCHARGE -> {
                BigDecimal amount = amount(node.get("amount"), path + ".amount");
                yield amount == null ? null : new ActionSpec.Surcharge(amount);
            }
            case FREE_UNITS -> {
                Integer buy = integer(node.get("buyQty"), path + ".buyQty", 1, MAX_FREE_UNITS);
                Integer free = integer(node.get("freeQty"), path + ".freeQty", 1, MAX_FREE_UNITS);
                yield buy == null || free == null ? null : new ActionSpec.FreeUnits(buy, free);
            }
            case FREE_GIFT -> {
                UUID productId = uuid(node.get("productId"), path + ".productId");
                Integer qty = integer(node.get("qty"), path + ".qty", 1, MAX_GIFT_QTY);
                if (productId != null) {
                    references.add(new ProductReference(path + ".productId", productId));
                }
                yield productId == null || qty == null ? null : new ActionSpec.FreeGift(productId, qty);
            }
            case SHIPPING_WAIVER -> new ActionSpec.ShippingWaiver();
            case REJECT -> {
                String message = text(node.get("message"), path + ".message", MAX_MESSAGE_LENGTH);
                yield message == null ? null : new ActionSpec.Reject(message);
            }
        };
        return violations.size() == before ? spec : null;
    }

    private static Set<String> parametersOf(ActionKind kind) {
        return switch (kind) {
            case PERCENT_OFF -> Set.of("kind", "percent");
            case AMOUNT_OFF, AMOUNT_OFF_CART, SURCHARGE -> Set.of("kind", "amount");
            case FREE_UNITS -> Set.of("kind", "buyQty", "freeQty");
            case FREE_GIFT -> Set.of("kind", "productId", "qty");
            case SHIPPING_WAIVER -> Set.of("kind");
            case REJECT -> Set.of("kind", "message");
        };
    }

    private void rejectUnknownKeys(JsonNode node, String path, Set<String> allowed) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                violations.add(new FieldViolation(path + "." + name, "is not a known property here"));
            }
        }
    }

    private Condition condition(JsonNode node, FieldScope scope, String path, int depth) {
        conditionCount++;
        if (node == null || !node.isObject()) {
            violations.add(new FieldViolation(path, "must be an object"));
            return null;
        }
        boolean combinator = node.has("all") || node.has("any") || node.has("not");
        if (!combinator) {
            return comparison(node, scope, path);
        }
        if (node.size() != 1) {
            violations.add(new FieldViolation(path, "all, any and not must be the only property of a condition"));
            return null;
        }
        if (depth > MAX_DEPTH) {
            violations.add(new FieldViolation(path, "is nested deeper than " + MAX_DEPTH + " levels"));
            return null;
        }
        if (node.has("not")) {
            Condition child = condition(node.get("not"), scope, path + ".not", depth + 1);
            return child == null ? null : new Condition.Not(child);
        }
        boolean all = node.has("all");
        String key = all ? "all" : "any";
        JsonNode children = node.get(key);
        if (!children.isArray() || children.isEmpty()) {
            violations.add(new FieldViolation(path + "." + key, "must be a non-empty array"));
            return null;
        }
        List<Condition> parsed = new ArrayList<>();
        boolean valid = true;
        for (int index = 0; index < children.size(); index++) {
            Condition child = condition(children.get(index), scope, path + "." + key + "[" + index + "]", depth + 1);
            if (child == null) {
                valid = false;
            } else {
                parsed.add(child);
            }
        }
        if (!valid) {
            return null;
        }
        return all ? new Condition.All(parsed) : new Condition.Any(parsed);
    }

    private Condition comparison(JsonNode node, FieldScope scope, String path) {
        int before = violations.size();
        rejectUnknownKeys(node, path, Set.of("field", "op", "value"));
        for (String required : List.of("field", "op", "value")) {
            if (!node.has(required)) {
                violations.add(new FieldViolation(path + "." + required, "is required"));
            }
        }
        if (violations.size() > before) {
            return null;
        }
        ConditionField field = field(node.get("field"), scope, path + ".field");
        Operator operator = operator(node.get("op"), field, path + ".op");
        if (field == null || operator == null) {
            return null;
        }
        Object value = value(field, operator, node.get("value"), path + ".value");
        if (value == null) {
            return null;
        }
        if (field == ConditionField.PRODUCT_ID) {
            collectProductReferences(value, path + ".value");
        }
        try {
            return new Condition.Comparison(field, operator, value);
        } catch (IllegalArgumentException e) {
            violations.add(new FieldViolation(path, e.getMessage()));
            return null;
        }
    }

    private void collectProductReferences(Object value, String path) {
        if (value instanceof UUID id) {
            references.add(new ProductReference(path, id));
        } else if (value instanceof List<?> items) {
            for (int index = 0; index < items.size(); index++) {
                references.add(new ProductReference(path + "[" + index + "]", (UUID) items.get(index)));
            }
        }
    }

    private ConditionField field(JsonNode node, FieldScope scope, String path) {
        if (!node.isTextual()) {
            violations.add(new FieldViolation(path, "must be a text"));
            return null;
        }
        ConditionField field = ConditionField.byPath(node.asText()).orElse(null);
        if (field == null) {
            violations.add(new FieldViolation(path, "Unknown field '" + node.asText() + "'. Allowed: "
                    + Arrays.stream(ConditionField.values()).filter(candidate -> candidate.scope() == scope)
                            .map(ConditionField::path).toList()));
            return null;
        }
        if (field.scope() != scope) {
            violations.add(new FieldViolation(path, "Field '" + field.path() + "' is a "
                    + field.scope().name().toLowerCase() + " field and cannot be used here"));
            return null;
        }
        return field;
    }

    private Operator operator(JsonNode node, ConditionField field, String path) {
        if (!node.isTextual()) {
            violations.add(new FieldViolation(path, "must be a text"));
            return null;
        }
        Operator operator = Operator.bySymbol(node.asText()).orElse(null);
        if (operator == null) {
            violations.add(new FieldViolation(path, "Unknown operator '" + node.asText() + "'"));
            return null;
        }
        if (field != null && !field.type().supports(operator)) {
            violations.add(new FieldViolation(path, "Operator '" + operator.symbol() + "' cannot be used with "
                    + field.path() + ". Allowed: "
                    + field.type().operators().stream().map(Operator::symbol).sorted().toList()));
            return null;
        }
        return operator;
    }

    private Object value(ConditionField field, Operator operator, JsonNode node, String path) {
        FieldType type = field.type();
        if (operator.takesList() || operator.takesRange()) {
            if (!node.isArray()) {
                violations.add(new FieldViolation(path, "must be an array"));
                return null;
            }
            int expectedMin = operator.takesRange() ? 2 : 1;
            int expectedMax = operator.takesRange() ? 2 : MAX_LIST_SIZE;
            if (node.size() < expectedMin || node.size() > expectedMax) {
                violations.add(new FieldViolation(path, operator.takesRange()
                        ? "must contain exactly 2 values"
                        : "must contain between 1 and " + MAX_LIST_SIZE + " values"));
                return null;
            }
            List<Object> values = new ArrayList<>();
            for (int index = 0; index < node.size(); index++) {
                Object item = scalar(type, node.get(index), path + "[" + index + "]");
                if (item == null) {
                    return null;
                }
                values.add(item);
            }
            if (operator.takesRange() && compare(values.get(0), values.get(1)) > 0) {
                violations.add(new FieldViolation(path, "range start must not be after range end"));
                return null;
            }
            return values;
        }
        if (node.isArray()) {
            violations.add(new FieldViolation(path, "must be a single value for operator '" + operator.symbol() + "'"));
            return null;
        }
        return scalar(type, node, path);
    }

    @SuppressWarnings("unchecked")
    private static int compare(Object first, Object second) {
        if (first instanceof BigDecimal || first instanceof LocalDate) {
            return ((Comparable<Object>) first).compareTo(second);
        }
        return 0;
    }

    private Object scalar(FieldType type, JsonNode node, String path) {
        return switch (type) {
            case NUMBER -> number(node, path);
            case TEXT -> text(node, path, MAX_TEXT_LENGTH);
            case ID -> uuid(node, path);
            case SEGMENT -> segment(node, path);
            case DATE -> date(node, path);
        };
    }

    private BigDecimal number(JsonNode node, String path) {
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText().trim());
            } catch (NumberFormatException e) {
                violations.add(new FieldViolation(path, "must be a number"));
                return null;
            }
        }
        violations.add(new FieldViolation(path, "must be a number"));
        return null;
    }

    private String text(JsonNode node, String path, int maxLength) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            violations.add(new FieldViolation(path, "must be a non-blank text"));
            return null;
        }
        String value = node.asText().trim();
        if (value.length() > maxLength) {
            violations.add(new FieldViolation(path, "must be at most " + maxLength + " characters"));
            return null;
        }
        return value;
    }

    private UUID uuid(JsonNode node, String path) {
        if (node == null || !node.isTextual()) {
            violations.add(new FieldViolation(path, "must be a UUID"));
            return null;
        }
        try {
            return UUID.fromString(node.asText());
        } catch (IllegalArgumentException e) {
            violations.add(new FieldViolation(path, "must be a UUID"));
            return null;
        }
    }

    private CustomerSegment segment(JsonNode node, String path) {
        if (node.isTextual()) {
            try {
                return CustomerSegment.valueOf(node.asText());
            } catch (IllegalArgumentException e) {
                violations.add(new FieldViolation(path, "Unknown segment '" + node.asText() + "'. Allowed: "
                        + Arrays.toString(CustomerSegment.values())));
                return null;
            }
        }
        violations.add(new FieldViolation(path, "must be a customer segment"));
        return null;
    }

    private LocalDate date(JsonNode node, String path) {
        if (node.isTextual()) {
            try {
                return LocalDate.parse(node.asText());
            } catch (DateTimeParseException e) {
                violations.add(new FieldViolation(path, "must be a date like 2026-11-30"));
                return null;
            }
        }
        violations.add(new FieldViolation(path, "must be a date like 2026-11-30"));
        return null;
    }

    private BigDecimal percent(JsonNode node, String path) {
        BigDecimal value = requiredNumber(node, path);
        if (value == null) {
            return null;
        }
        if (value.signum() <= 0 || value.compareTo(HUNDRED) > 0 || value.stripTrailingZeros().scale() > 2) {
            violations.add(new FieldViolation(path, "must be greater than 0 and at most 100 with at most 2 decimals"));
            return null;
        }
        return value;
    }

    private BigDecimal amount(JsonNode node, String path) {
        BigDecimal value = requiredNumber(node, path);
        if (value == null) {
            return null;
        }
        if (value.signum() <= 0 || value.compareTo(MAX_AMOUNT) > 0 || value.stripTrailingZeros().scale() > 2) {
            violations.add(new FieldViolation(path, "must be greater than 0 with at most 2 decimals and 10 integer digits"));
            return null;
        }
        return value;
    }

    private BigDecimal requiredNumber(JsonNode node, String path) {
        if (node == null || !(node.isNumber() || node.isTextual())) {
            violations.add(new FieldViolation(path, "is required and must be a number"));
            return null;
        }
        return number(node, path);
    }

    private Integer integer(JsonNode node, String path, int min, int max) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            violations.add(new FieldViolation(path, "is required and must be a whole number"));
            return null;
        }
        int value = node.intValue();
        if (value < min || value > max) {
            violations.add(new FieldViolation(path, "must be between " + min + " and " + max));
            return null;
        }
        return value;
    }
}
