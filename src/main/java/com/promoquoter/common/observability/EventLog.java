package com.promoquoter.common.observability;

import org.slf4j.spi.LoggingEventBuilder;

public final class EventLog {

    private final LoggingEventBuilder builder;

    EventLog(LoggingEventBuilder builder) {
        this.builder = builder;
    }

    public EventLog with(String key, Object value) {
        if (value != null) {
            builder.addKeyValue(key, value);
        }
        return this;
    }

    public EventLog cause(Throwable cause) {
        builder.setCause(cause);
        return this;
    }

    public void message(String message, Object... arguments) {
        builder.log(message, arguments);
    }
}
