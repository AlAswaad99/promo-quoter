package com.promoquoter.promotion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.condition.Condition;
import com.promoquoter.pricing.condition.Condition.All;
import com.promoquoter.pricing.condition.Condition.Any;
import com.promoquoter.pricing.condition.Condition.Comparison;
import com.promoquoter.pricing.condition.Condition.Not;

public final class RuleJsonWriter {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private RuleJsonWriter() {
    }

    public static JsonNode condition(Condition condition) {
        if (condition == null) {
            return null;
        }
        ObjectNode node = NODES.objectNode();
        switch (condition) {
            case All all -> node.set("all", children(all.conditions()));
            case Any any -> node.set("any", children(any.conditions()));
            case Not not -> node.set("not", condition(not.condition()));
            case Comparison comparison -> {
                node.put("field", comparison.field().path());
                node.put("op", comparison.operator().symbol());
                node.set("value", value(comparison.value()));
            }
        }
        return node;
    }

    public static JsonNode action(ActionSpec action) {
        ObjectNode node = NODES.objectNode();
        node.put("kind", action.kind().name());
        switch (action) {
            case ActionSpec.PercentOff percentOff -> node.put("percent", percentOff.percent());
            case ActionSpec.AmountOff amountOff -> node.put("amount", amountOff.amount());
            case ActionSpec.AmountOffCart amountOffCart -> node.put("amount", amountOffCart.amount());
            case ActionSpec.Surcharge surcharge -> node.put("amount", surcharge.amount());
            case ActionSpec.FreeUnits freeUnits -> {
                node.put("buyQty", freeUnits.buyQty());
                node.put("freeQty", freeUnits.freeQty());
            }
            case ActionSpec.FreeGift freeGift -> {
                node.put("productId", freeGift.productId().toString());
                node.put("qty", freeGift.qty());
            }
            case ActionSpec.Reject reject -> node.put("message", reject.message());
            case ActionSpec.ShippingWaiver ignored -> {
            }
        }
        return node;
    }

    private static ArrayNode children(List<Condition> conditions) {
        ArrayNode array = NODES.arrayNode();
        conditions.forEach(child -> array.add(condition(child)));
        return array;
    }

    private static JsonNode value(Object value) {
        if (value instanceof List<?> items) {
            ArrayNode array = NODES.arrayNode();
            items.forEach(item -> array.add(value(item)));
            return array;
        }
        if (value instanceof BigDecimal decimal) {
            return NODES.numberNode(decimal);
        }
        if (value instanceof LocalDate date) {
            return NODES.textNode(date.toString());
        }
        return NODES.textNode(value.toString());
    }
}
