# PromoQuoter

A Spring Boot REST service that prices a shopping cart with pluggable promotion rules and reserves inventory atomically when an order is confirmed.

- **Quote** a cart: per-line and cart totals plus every applied promotion, in the order it was applied.
- **Confirm** a cart: re-prices, locks the stock rows, decrements stock and stores an order in one transaction. Safe under concurrent calls; retries with an `Idempotency-Key` never reserve twice.
- **Promotions** are Strategy objects built by a Factory from stored definitions and run through an ordered Pipeline. Adding a promotion type means adding classes. No orchestration code changes.

Java 21, Spring Boot 3.5, Spring Data JPA, H2 (in memory), Actuator, Lombok. One Maven module, packaged by feature.

## Run it

Requires JDK 21. Nothing else: Maven is bundled through the wrapper.

```bash
./mvnw spring-boot:run
```

(On Windows use `mvnw.cmd spring-boot:run`.) The service listens on `http://localhost:8080`.

Health check: http://localhost:8080/actuator/health

Data lives in an in-memory H2 database and disappears on restart. Hibernate creates the schema on startup. There is no seed data; the walkthrough below creates some.

Structured JSON logs (ECS format) instead of the plain pattern:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=json
```

### Docker

```bash
docker build -t promo-quoter .
docker run --rm -p 8080:8080 promo-quoter
```

A multi-stage build: JDK to compile, JRE to run, non-root user.

## Run the tests

```bash
./mvnw test      # all unit and integration tests
./mvnw verify    # tests + coverage report + coverage gate (fails below 85% line coverage)
```

The HTML coverage report is written to `target/site/jacoco/index.html`. At the time of writing: **99.0 % line, 94.1 % branch coverage** across 278 tests.

| Kind | What it covers |
|---|---|
| Unit | Each rule (rounding, caps, thresholds), the pipeline ordering and tie-breaks, the audit decorator, the rule factory (eligibility, fail-fast wiring), the engine end to end, cart normalisation, money rounding, paging and sort policy |
| Integration (`*IT`) | Every endpoint on a real Spring context and H2 through MockMvc: success paths, every error code, validation, paging, filters, idempotency, request-id headers and log output, strict input handling |
| Concurrency | 20 threads against 5 units of stock (exactly 5 orders, rest get 409); multi-product carts stay all-or-nothing; same `Idempotency-Key` from 10 threads reserves once; opposite lock orders do not deadlock |

The concurrency tests have teeth: with the row lock disabled, three of them fail.
