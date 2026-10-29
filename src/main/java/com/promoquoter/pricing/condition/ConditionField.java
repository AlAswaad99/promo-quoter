package com.promoquoter.pricing.condition;

import java.util.Arrays;
import java.util.Optional;

public enum ConditionField {
    PRODUCT_ID("product.id", FieldType.ID, FieldScope.LINE),
    PRODUCT_NAME("product.name", FieldType.TEXT, FieldScope.LINE),
    PRODUCT_CATEGORY("product.category", FieldType.TEXT, FieldScope.LINE),
    PRODUCT_PRICE("product.price", FieldType.NUMBER, FieldScope.LINE),
    PRODUCT_STOCK("product.stock", FieldType.NUMBER, FieldScope.LINE),
    LINE_QTY("line.qty", FieldType.NUMBER, FieldScope.LINE),
    CART_SUBTOTAL("cart.subtotal", FieldType.NUMBER, FieldScope.CART),
    CART_TOTAL("cart.total", FieldType.NUMBER, FieldScope.CART),
    CART_ITEM_COUNT("cart.itemCount", FieldType.NUMBER, FieldScope.CART),
    CUSTOMER_SEGMENT("customer.segment", FieldType.SEGMENT, FieldScope.CART),
    DATE("date", FieldType.DATE, FieldScope.CART);

    private final String path;
    private final FieldType type;
    private final FieldScope scope;

    ConditionField(String path, FieldType type, FieldScope scope) {
        this.path = path;
        this.type = type;
        this.scope = scope;
    }

    public String path() {
        return path;
    }

    public FieldType type() {
        return type;
    }

    public FieldScope scope() {
        return scope;
    }

    public static Optional<ConditionField> byPath(String path) {
        return Arrays.stream(values()).filter(field -> field.path.equals(path)).findFirst();
    }
}
