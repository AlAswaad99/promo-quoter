package com.promoquoter.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.promoquoter.support.ApiFixtures;
import com.promoquoter.support.IntegrationTest;
import com.promoquoter.support.TestDatabase;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@IntegrationTest
class StructuredLoggingIT {

    private static final String SECRET_KEY = "super-secret-key-123";
    private static final String SECRET_NAME = "SecretProductName";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApiFixtures api;

    @Autowired
    TestDatabase database;

    @Autowired
    Environment environment;

    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final Logger pricing = (Logger) LoggerFactory.getLogger("com.promoquoter.pricing");
    private ListAppender<ILoggingEvent> appender;
    private String product;

    @BeforeEach
    void setUp() throws Exception {
        database.clean();
        product = api.createProduct(SECRET_NAME, "kitchen", "12.00", 5);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        root.detachAppender(appender);
        pricing.setLevel(null);
    }

    private static String value(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream().filter(pair -> pair.key.equals(key))
                .map(pair -> String.valueOf(pair.value)).findFirst().orElse(null);
    }

    private List<ILoggingEvent> events(LogEvent logEvent) {
        return appender.list.stream().filter(event -> logEvent.eventName().equals(value(event, "event"))).toList();
    }

    private ILoggingEvent only(LogEvent logEvent) {
        List<ILoggingEvent> found = events(logEvent);
        assertThat(found).as(logEvent.eventName()).hasSize(1);
        return found.get(0);
    }

    private ResultActions confirm(String json, String key) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/cart/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private String cart(int qty) {
        return "{\"items\":[{\"productId\":\"" + product + "\",\"qty\":" + qty + "}]}";
    }

    private void clearEvents() {
        appender.list.clear();
    }

    @Test
    void everyRequestLogsStartAndCompletionWithTheRequestId() throws Exception {
        clearEvents();

        mockMvc.perform(get("/api/v1/products").header(RequestContext.HEADER, "trace-me"));

        ILoggingEvent started = only(LogEvent.HTTP_REQUEST_STARTED);
        ILoggingEvent completed = only(LogEvent.HTTP_REQUEST_COMPLETED);
        assertThat(started.getMDCPropertyMap()).containsEntry(RequestContext.MDC_KEY, "trace-me");
        assertThat(value(started, "method")).isEqualTo("GET");
        assertThat(value(started, "path")).isEqualTo("/api/v1/products");
        assertThat(completed.getMDCPropertyMap()).containsEntry(RequestContext.MDC_KEY, "trace-me");
        assertThat(value(completed, "status")).isEqualTo("200");
        assertThat(value(completed, "startedAt")).isNotNull();
        assertThat(value(completed, "endedAt")).isNotNull();
        assertThat(Long.parseLong(value(completed, "durationMs"))).isNotNegative();
        assertThat(completed.getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void completionLevelFollowsTheStatus() throws Exception {
        clearEvents();
        mockMvc.perform(get("/does-not-exist"));
        mockMvc.perform(get("/test-support/boom"));

        List<ILoggingEvent> completed = events(LogEvent.HTTP_REQUEST_COMPLETED);
        assertThat(completed).extracting(event -> value(event, "status")).containsExactly("404", "500");
        assertThat(completed).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN, Level.ERROR);
    }

    @Test
    void rejectedRequestsLogTheErrorCodeAndStatus() throws Exception {
        clearEvents();

        mockMvc.perform(get("/api/v1/products/00000000-0000-0000-0000-000000000009"));

        ILoggingEvent rejected = only(LogEvent.REQUEST_REJECTED);
        assertThat(rejected.getLevel()).isEqualTo(Level.WARN);
        assertThat(value(rejected, "code")).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(value(rejected, "status")).isEqualTo("404");
    }

    @Test
    void unexpectedErrorsAreLoggedAtErrorWithTheStackTrace() throws Exception {
        clearEvents();

        mockMvc.perform(get("/test-support/boom"));

        ILoggingEvent unexpected = only(LogEvent.ERROR_UNEXPECTED);
        assertThat(unexpected.getLevel()).isEqualTo(Level.ERROR);
        assertThat(unexpected.getThrowableProxy()).isNotNull();
        assertThat(value(unexpected, "exception")).isNotBlank();
    }

    @Test
    void productAndPromotionChangesAreLogged() throws Exception {
        clearEvents();
        String promotionId = api.percentOff("Ten off", "kitchen", "10", "");
        mockMvc.perform(patch("/api/v1/promotions/" + promotionId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false,\"priority\":3}"));
        mockMvc.perform(delete("/api/v1/promotions/" + promotionId));
        api.createProduct("Another", "home", "5.00", 1);

        assertThat(value(only(LogEvent.PROMOTION_CREATED), "count")).isEqualTo("1");
        assertThat(value(only(LogEvent.PROMOTION_CREATED), "types")).isEqualTo("PERCENT_OFF_CATEGORY");
        ILoggingEvent updated = only(LogEvent.PROMOTION_UPDATED);
        assertThat(value(updated, "promotionId")).isEqualTo(promotionId);
        assertThat(value(updated, "changedFields")).isEqualTo("active,priority");
        assertThat(value(only(LogEvent.PROMOTION_DELETED), "promotionId")).isEqualTo(promotionId);
        assertThat(value(only(LogEvent.PRODUCT_CREATED), "count")).isEqualTo("1");
    }

    @Test
    void aQuoteLogsItsSummary() throws Exception {
        api.percentOff("Ten off", "kitchen", "10", "");
        clearEvents();

        mockMvc.perform(post("/api/v1/cart/quote").contentType(MediaType.APPLICATION_JSON).content(cart(2)));

        ILoggingEvent priced = only(LogEvent.QUOTE_PRICED);
        assertThat(value(priced, "itemCount")).isEqualTo("1");
        assertThat(value(priced, "segment")).isEqualTo("REGULAR");
        assertThat(value(priced, "appliedCount")).isEqualTo("1");
        assertThat(value(priced, "total")).isEqualTo("21.60");
        assertThat(Long.parseLong(value(priced, "durationMs"))).isNotNegative();
    }

    @Test
    void aQuoteRejectedByAValidationRuleIsLoggedAsAWarning() throws Exception {
        api.createPromotion("{\"type\":\"RULE\",\"kind\":\"VALIDATION\",\"name\":\"Limit\","
                + "\"action\":{\"kind\":\"REJECT\",\"message\":\"no\"}}");
        clearEvents();

        mockMvc.perform(post("/api/v1/cart/quote").contentType(MediaType.APPLICATION_JSON).content(cart(1)));

        assertThat(value(only(LogEvent.QUOTE_REJECTED), "violationCount")).isEqualTo("1");
        assertThat(only(LogEvent.QUOTE_REJECTED).getLevel()).isEqualTo(Level.WARN);
        assertThat(value(only(LogEvent.REQUEST_REJECTED), "code")).isEqualTo("RULE_VIOLATION");
        assertThat(events(LogEvent.QUOTE_PRICED)).isEmpty();
    }

    @Test
    void aPreviewLogsTheRuleAndTheViolations() throws Exception {
        clearEvents();

        mockMvc.perform(post("/api/v1/promotions/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rule\":{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"P\","
                        + "\"action\":{\"kind\":\"SURCHARGE\",\"amount\":1}},\"cart\":" + cart(1) + "}"));

        ILoggingEvent previewed = only(LogEvent.RULE_PREVIEWED);
        assertThat(value(previewed, "ruleKind")).isEqualTo("PRICING");
        assertThat(value(previewed, "actionKind")).isEqualTo("SURCHARGE");
        assertThat(value(previewed, "violationCount")).isEqualTo("0");
    }

    @Test
    void aConfirmLogsStockAndTheOrderWithAHashedKey() throws Exception {
        clearEvents();

        String body = confirm(cart(2), SECRET_KEY).andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(body, "$.orderId");

        ILoggingEvent reserved = only(LogEvent.STOCK_RESERVED);
        assertThat(value(reserved, "orderId")).isEqualTo(orderId);
        assertThat(value(reserved, "productCount")).isEqualTo("1");
        assertThat(value(reserved, "giftUnits")).isEqualTo("0");
        ILoggingEvent confirmed = only(LogEvent.ORDER_CONFIRMED);
        assertThat(value(confirmed, "orderId")).isEqualTo(orderId);
        assertThat(value(confirmed, "total")).isEqualTo("24.00");
        assertThat(value(confirmed, "idempotencyKeyHash")).matches("[0-9a-f]{8}");
    }

    @Test
    void aConfirmWithoutAKeyLogsNoHash() throws Exception {
        clearEvents();

        confirm(cart(1), null);

        assertThat(value(only(LogEvent.ORDER_CONFIRMED), "idempotencyKeyHash")).isNull();
    }

    @Test
    void aReplayAndAKeyConflictAreLogged() throws Exception {
        confirm(cart(1), SECRET_KEY);
        String hash = value(only(LogEvent.ORDER_CONFIRMED), "idempotencyKeyHash");
        clearEvents();

        confirm(cart(1), SECRET_KEY);
        confirm(cart(2), SECRET_KEY);

        ILoggingEvent replayed = only(LogEvent.ORDER_REPLAYED);
        assertThat(value(replayed, "idempotencyKeyHash")).isEqualTo(hash);
        ILoggingEvent conflict = only(LogEvent.IDEMPOTENCY_CONFLICT);
        assertThat(conflict.getLevel()).isEqualTo(Level.WARN);
        assertThat(value(conflict, "idempotencyKeyHash")).isEqualTo(hash);
        assertThat(events(LogEvent.ORDER_CONFIRMED)).isEmpty();
    }

    @Test
    void aStockShortageIsLoggedAsAWarningWithTheRejection() throws Exception {
        clearEvents();

        confirm(cart(50), null);

        ILoggingEvent shortage = only(LogEvent.STOCK_SHORTAGE);
        assertThat(shortage.getLevel()).isEqualTo(Level.WARN);
        assertThat(value(shortage, "shortProductCount")).isEqualTo("1");
        assertThat(value(only(LogEvent.REQUEST_REJECTED), "code")).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(events(LogEvent.ORDER_CONFIRMED)).isEmpty();
    }

    @Test
    void aSkippedGiftIsLogged() throws Exception {
        String gift = api.createProduct("Gift", "gifts", "1.00", 0);
        api.createPromotion("{\"type\":\"RULE\",\"kind\":\"PRICING\",\"name\":\"Gift\","
                + "\"action\":{\"kind\":\"FREE_GIFT\",\"productId\":\"" + gift + "\",\"qty\":1}}");
        clearEvents();

        mockMvc.perform(post("/api/v1/cart/quote").contentType(MediaType.APPLICATION_JSON).content(cart(1)));

        ILoggingEvent skipped = only(LogEvent.GIFT_SKIPPED);
        assertThat(value(skipped, "productId")).isEqualTo(gift);
        assertThat(value(skipped, "reason")).isEqualTo("not enough stock");
    }

    @Test
    void neitherSecretsNorRequestBodiesAppearInAnyLogLine() throws Exception {
        api.percentOff("Ten off", "kitchen", "10", "");
        clearEvents();

        confirm(cart(2), SECRET_KEY);
        confirm(cart(2), SECRET_KEY);
        confirm(cart(3), SECRET_KEY);
        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                .content("[{\"name\":\"" + SECRET_NAME + "Two\",\"category\":\"x\",\"price\":1,\"stock\":1}]"));
        mockMvc.perform(post("/api/v1/cart/quote").contentType(MediaType.APPLICATION_JSON).content(cart(1)));

        assertThat(appender.list).isNotEmpty();
        for (ILoggingEvent event : appender.list) {
            String everything = event.getFormattedMessage() + " " + event.getKeyValuePairs() + " "
                    + event.getMDCPropertyMap();
            assertThat(everything).as(everything).doesNotContain(SECRET_KEY).doesNotContain(SECRET_NAME)
                    .doesNotContain("productId\":");
        }
    }

    @Test
    void perRuleDetailIsOnlyLoggedAtDebug() throws Exception {
        api.percentOff("Ten off", "kitchen", "10", "");
        clearEvents();
        mockMvc.perform(post("/api/v1/cart/quote").contentType(MediaType.APPLICATION_JSON).content(cart(1)));
        assertThat(events(LogEvent.PRICING_RULE_EVALUATED)).isEmpty();

        pricing.setLevel(Level.DEBUG);
        clearEvents();
        mockMvc.perform(post("/api/v1/cart/quote").contentType(MediaType.APPLICATION_JSON).content(cart(1)));

        ILoggingEvent evaluated = only(LogEvent.PRICING_RULE_EVALUATED);
        assertThat(evaluated.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(value(evaluated, "ruleName")).isEqualTo("Ten off");
        assertThat(value(evaluated, "stage")).isEqualTo("100");
        assertThat(value(evaluated, "fired")).isEqualTo("true");
        assertThat(value(evaluated, "lineAmount")).isEqualTo("1.20");
    }

    @Test
    void theConsolePatternCarriesTheTimestampRequestIdAndKeyValuePairs() {
        String pattern = environment.getProperty("logging.pattern.console");

        assertThat(pattern).startsWith("%clr(%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX})").contains("%X{requestId:-}")
                .contains("%kvp").contains("%wEx");
    }
}
