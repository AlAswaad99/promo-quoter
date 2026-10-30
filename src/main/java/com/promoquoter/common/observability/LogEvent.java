package com.promoquoter.common.observability;

import org.slf4j.Logger;
import org.slf4j.event.Level;

public enum LogEvent {
    HTTP_REQUEST_STARTED("http.request.started", Level.INFO),
    HTTP_REQUEST_COMPLETED("http.request.completed", Level.INFO),
    REQUEST_REJECTED("request.rejected", Level.WARN),
    ERROR_UNEXPECTED("error.unexpected", Level.ERROR),
    APP_STARTED("app.started", Level.INFO),
    PRODUCT_CREATED("product.created", Level.INFO),
    PROMOTION_CREATED("promotion.created", Level.INFO),
    PROMOTION_UPDATED("promotion.updated", Level.INFO),
    PROMOTION_DELETED("promotion.deleted", Level.INFO),
    RULE_PREVIEWED("rule.previewed", Level.INFO),
    QUOTE_PRICED("quote.priced", Level.INFO),
    QUOTE_REJECTED("quote.rejected", Level.WARN),
    PRICING_RULE_EVALUATED("pricing.rule.evaluated", Level.DEBUG),
    GIFT_SKIPPED("gift.skipped", Level.INFO),
    STOCK_RESERVED("stock.reserved", Level.INFO),
    STOCK_SHORTAGE("stock.shortage", Level.WARN),
    ORDER_CONFIRMED("order.confirmed", Level.INFO),
    ORDER_REPLAYED("order.replayed", Level.INFO),
    IDEMPOTENCY_CONFLICT("idempotency.conflict", Level.WARN);

    public static final String FIELD = "event";

    private final String eventName;
    private final Level level;

    LogEvent(String eventName, Level level) {
        this.eventName = eventName;
        this.level = level;
    }

    public String eventName() {
        return eventName;
    }

    public Level level() {
        return level;
    }

    public EventLog at(Logger logger) {
        return at(logger, level);
    }

    public EventLog at(Logger logger, Level atLevel) {
        return new EventLog(logger.atLevel(atLevel).addKeyValue(FIELD, eventName));
    }
}
