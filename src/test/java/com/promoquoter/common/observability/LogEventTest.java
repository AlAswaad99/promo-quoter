package com.promoquoter.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.slf4j.event.Level;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class LogEventTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("log-event-test");
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attach() {
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    private static String valueOf(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream().filter(pair -> pair.key.equals(key)).map(pair -> String.valueOf(pair.value))
                .findFirst().orElse(null);
    }

    @Test
    void everyEventHasAUniqueLowercaseDottedName() {
        List<String> names = Arrays.stream(LogEvent.values()).map(LogEvent::eventName).toList();

        assertThat(names).doesNotHaveDuplicates();
        assertThat(names).allMatch(name -> name.matches("[a-z]+(\\.[a-z]+)+"));
    }

    @Test
    void theCatalogueHasTheAgreedEventsAndLevels() {
        assertThat(LogEvent.valueOf("ORDER_CONFIRMED").level()).isEqualTo(Level.INFO);
        assertThat(LogEvent.valueOf("STOCK_SHORTAGE").level()).isEqualTo(Level.WARN);
        assertThat(LogEvent.valueOf("REQUEST_REJECTED").level()).isEqualTo(Level.WARN);
        assertThat(LogEvent.valueOf("ERROR_UNEXPECTED").level()).isEqualTo(Level.ERROR);
        assertThat(LogEvent.valueOf("PRICING_RULE_EVALUATED").level()).isEqualTo(Level.DEBUG);
        assertThat(LogEvent.values()).hasSize(19);
    }

    @Test
    void logsTheEventNameAndFieldsAsKeyValues() {
        LogEvent.ORDER_CONFIRMED.at(logger).with("orderId", "abc").with("total", 10).message("Order confirmed");

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).isEqualTo("Order confirmed");
        assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.INFO);
        assertThat(valueOf(event, "event")).isEqualTo("order.confirmed");
        assertThat(valueOf(event, "orderId")).isEqualTo("abc");
        assertThat(valueOf(event, "total")).isEqualTo("10");
        assertThat(event.getKeyValuePairs().stream().map(pair -> pair.key).collect(Collectors.toList()))
                .startsWith("event");
    }

    @Test
    void nullFieldsAreLeftOut() {
        LogEvent.ORDER_CONFIRMED.at(logger).with("idempotencyKeyHash", null).message("Order confirmed");

        assertThat(valueOf(appender.list.get(0), "idempotencyKeyHash")).isNull();
    }

    @Test
    void theLevelCanBeOverriddenPerCall() {
        LogEvent.HTTP_REQUEST_COMPLETED.at(logger, Level.ERROR).message("request completed");

        assertThat(appender.list.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
    }

    @Test
    void anExceptionIsAttachedAsTheCause() {
        LogEvent.ERROR_UNEXPECTED.at(logger).cause(new IllegalStateException("boom")).message("Unexpected error");

        assertThat(appender.list.get(0).getThrowableProxy().getMessage()).isEqualTo("boom");
    }

    @Test
    void aDisabledLevelLogsNothing() {
        logger.setLevel(ch.qos.logback.classic.Level.INFO);

        LogEvent.PRICING_RULE_EVALUATED.at(logger).with("ruleId", "x").message("Rule evaluated");

        assertThat(appender.list).isEmpty();
    }

    @Test
    void keyValuePairsKeepTheirInsertionOrder() {
        LogEvent.QUOTE_PRICED.at(logger).with("itemCount", 1).with("segment", "VIP").with("total", "5.00")
                .message("Quote priced");

        List<String> keys = appender.list.get(0).getKeyValuePairs().stream().map((KeyValuePair pair) -> pair.key).toList();
        assertThat(keys).containsExactly("event", "itemCount", "segment", "total");
    }
}
