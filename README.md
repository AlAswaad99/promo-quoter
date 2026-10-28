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

## API

Base path `/api/v1`. JSON only. Money is a string with two decimals (`"12.50"`). Timestamps are ISO-8601 UTC. Every response carries an `X-Request-Id` header. Unknown JSON fields are rejected with `400 MALFORMED_REQUEST`.

| Method | Path | Notes |
|---|---|---|
| POST | `/products` | Array of 1–100 products, saved all-or-nothing, `201` |
| GET | `/products`, `/products/{id}` | List supports `page`, `size`, `sort`, `category` |
| POST | `/promotions` | Array of 1–100 promotions of mixed types, all-or-nothing, `201` |
| GET | `/promotions`, `/promotions/{id}` | List supports `page`, `size`, `sort`, `type`, `active` |
| PATCH | `/promotions/{id}` | Change `name`, `active`, `priority` and/or `eligibleSegments`. Rule parameters (percent, quantities) are fixed: delete and recreate to swap a rule |
| DELETE | `/promotions/{id}` | Removes the promotion. Past orders keep their audit trail. `204` |
| POST | `/cart/quote` | Prices a cart. Does not check or change stock |
| POST | `/cart/confirm` | Same body as quote plus optional `Idempotency-Key` header. `201` with an `orderId` |
| GET | `/orders`, `/orders/{id}` | Stored orders, with the price snapshot taken at confirmation |

Paging: `page` starts at 0 (default 0), `size` defaults to 20 and is capped at 100, `sort=field,asc|desc`. Sortable fields are limited per resource (products: `name`, `price`, `createdAt`; promotions: `name`, `priority`, `createdAt`; orders: `createdAt`, `total`) and anything else is `400 INVALID_SORT`. Lists are returned as `{ "content": [...], "page", "size", "totalElements", "totalPages" }`.

### Promotion types

```jsonc
{ "type": "PERCENT_OFF_CATEGORY", "name": "10% off kitchen", "category": "kitchen", "percent": 10 }
{ "type": "BUY_X_GET_Y",          "name": "Mugs 2+1", "productId": "<uuid>", "buyQty": 2, "freeQty": 1 }
{ "type": "TIERED_BULK",          "name": "5% off 10+", "productId": "<uuid>", "minQty": 10, "percent": 5 }
```

Every type also accepts `priority` (default 0, lower runs first), `active` (default true) and `eligibleSegments` (a list of `REGULAR`, `VIP`, `EMPLOYEE`; empty means everyone).

### Walkthrough

Start the service, then run these in a shell (`bash`/`zsh`; no `jq` needed).

```bash
BASE=http://localhost:8080/api/v1
H='Content-Type: application/json'
id() { sed -E 's/.*"id":"([^"]+)".*/\1/'; }

MUG=$(curl -s -X POST $BASE/products -H "$H" \
  -d '[{"name":"Mug","category":"kitchen","price":12.00,"stock":10}]' | id)
LAMP=$(curl -s -X POST $BASE/products -H "$H" \
  -d '[{"name":"Lamp","category":"home","price":30.00,"stock":5}]' | id)

curl -s -X POST $BASE/promotions -H "$H" -d '[
  {"type":"PERCENT_OFF_CATEGORY","name":"10% off kitchen","category":"kitchen","percent":10},
  {"type":"BUY_X_GET_Y","name":"Mugs 2+1","productId":"'$MUG'","buyQty":2,"freeQty":1}
]'
```

Quote three mugs:

```bash
curl -s -X POST $BASE/cart/quote -H "$H" \
  -d '{"items":[{"productId":"'$MUG'","qty":3}],"customerSegment":"REGULAR"}'
```

```json
{
  "customerSegment": "REGULAR",
  "lines": [{ "productId": "…", "name": "Mug", "category": "KITCHEN", "unitPrice": "12.00", "qty": 3,
              "subtotal": "36.00", "discount": "14.40", "total": "21.60" }],
  "appliedPromotions": [
    { "sequence": 1, "promotionId": "…", "name": "10% off kitchen", "type": "PERCENT_OFF_CATEGORY", "productId": "…", "amount": "3.60" },
    { "sequence": 2, "promotionId": "…", "name": "Mugs 2+1", "type": "BUY_X_GET_Y", "productId": "…", "amount": "10.80" }
  ],
  "subtotal": "36.00", "totalDiscount": "14.40", "total": "21.60"
}
```

Confirm it. Run the same command twice: the second response has the same `orderId` and an `Idempotent-Replayed: true` header, and stock only drops once.

```bash
curl -i -X POST $BASE/cart/confirm -H "$H" -H "Idempotency-Key: order-1001" \
  -d '{"items":[{"productId":"'$MUG'","qty":3}],"customerSegment":"REGULAR"}'

curl -s $BASE/products/$MUG
```

Failure cases:

```bash
curl -s -X POST $BASE/cart/confirm -H "$H" \
  -d '{"items":[{"productId":"'$MUG'","qty":50}]}'

curl -s -X POST $BASE/cart/confirm -H "$H" -H "Idempotency-Key: order-1001" \
  -d '{"items":[{"productId":"'$MUG'","qty":1}]}'

curl -s -X POST $BASE/products -H "$H" -d '[{"name":"","category":"x","price":-1,"stock":1}]'
```

```json
{ "type": "urn:promoquoter:error:insufficient-stock", "title": "Insufficient stock", "status": 409,
  "detail": "Not enough stock for 1 product(s)", "instance": "/api/v1/cart/confirm",
  "code": "INSUFFICIENT_STOCK", "requestId": "0b2c8c1e-6a3e-4f3c-9d0e-2d4a1f7c9b11",
  "errors": [{ "productId": "…", "requested": 50, "available": 7 }] }
```

The second returns `422 IDEMPOTENCY_KEY_REUSED` and the third `400 VALIDATION_FAILED` with a field-by-field `errors` list.

Browse:

```bash
curl -s "$BASE/products?category=kitchen&sort=price,desc&size=5"
curl -s "$BASE/orders?size=5"
```

## Design

### Layout

Packages are organised by feature, not by layer. Each feature owns its controller, service, entity and repository, and exposes a small public surface. Dependencies only point one way:

```
order ──► cart ──► product
  │         ├────► promotion ──► pricing   (framework-free engine)
  └─────────────► product (InventoryService)
common: error handling, paging, money, observability
```

Entities and repositories are package-private, so another feature can only reach them through a public service.

### Rules engine (`pricing`)

| Pattern | Where | Role |
|---|---|---|
| Strategy | `PricingRule` and one class per promotion type | Each rule encapsulates one discount algorithm |
| Pipeline / Chain | `PricingPipeline` | Sorts the rules and passes one `PricingContext` through them |
| Factory | `PromotionRuleFactory` + one `RuleProvider` per type | Builds rules from persisted `PromotionDefinition`s; filters by active flag and customer segment |
| Decorator | `AuditingRule` | Wraps every rule and records exactly what it changed in the audit trail that becomes `appliedPromotions` |

The engine has no Spring or JPA dependencies, so it is tested without starting a context.

**Adding a promotion type** takes a `PromotionType` value, a rule class, and a `RuleProvider` bean (plus the API request record and storage column if it carries new parameters). The pipeline, the factory and the services are untouched. The factory refuses to start if any `PromotionType` has no provider, so a half-wired type cannot ship.

### Pricing rules

1. A line starts at `unitPrice × qty`.
2. Rules run in stage order: **`PERCENT_OFF_CATEGORY` (100) → `BUY_X_GET_Y` (200) → `TIERED_BULK` (300)**. Within a stage: `priority` ascending, then creation time, then id. Each rule works on the running total left by the previous ones, so discounts stack.
3. `PERCENT_OFF_CATEGORY`: `percent` of the line total for lines in the category (category match is case-insensitive).
4. `BUY_X_GET_Y`: `floor(qty / (buyQty + freeQty)) × freeQty` units are free, valued at the line's current (already discounted) unit price. Free items are never added to the cart automatically. Buy 2 get 1: qty 2 → 0 free, 3 → 1, 6 → 2, 7 → 2.
5. `TIERED_BULK`: `percent` off the line total once `qty >= minQty`.
6. Every discount is rounded `HALF_UP` to cents when applied and capped at the line's remaining total, so a line never goes below `0.00`. Cart totals are the sums of the lines, so lines always reconcile exactly with the total.
7. A promotion applies only if it is active and either has no segment restriction or lists the cart's `customerSegment` (default `REGULAR`).

Worked example (3 mugs at 12.00): 36.00 → 10% off takes 3.60 → 32.40 → the free mug is valued at 32.40 / 3 = 10.80 → **21.60**.

### Concurrency and idempotency

Confirm runs as one transaction:

1. If an `Idempotency-Key` is present, its record (key, request hash, pre-generated `orderId`) is inserted first. The key is a primary key, so two simultaneous requests with the same key cannot both pass: the second waits on the first, then fails with a duplicate key and is answered with the first order.
2. The product rows are loaded with `SELECT … FOR UPDATE` (pessimistic write lock) in a single query ordered by id, so every transaction takes locks in the same order and carts that share products cannot deadlock.
3. Stock is checked for every line and **all** shortages are reported together in one `409 INSUFFICIENT_STOCK`. Nothing is decremented unless every line fits.
4. Stock is decremented, the cart is priced from the locked rows, and the order with its price snapshot is saved.

If anything fails, the whole transaction rolls back, including the idempotency record. A failed confirm therefore does not "use up" its key and can be retried. Products also carry a `@Version` and a `CHECK (stock >= 0)` constraint as defence in depth; an optimistic-lock or lock-timeout failure returns `409 CONCURRENT_MODIFICATION`.

The request hash covers the customer segment and the merged, id-sorted items, so a cart that is the same after normalisation (items reordered, duplicate lines merged) is treated as the same request.

### Errors

All errors are RFC 7807 `application/problem+json` with extra fields `code`, `requestId` and, where useful, `errors[]`.

| Code | Status | When |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Bean Validation failure; `errors[]` lists `{field, message}` |
| `MALFORMED_REQUEST` | 400 | Bad JSON, wrong types, unknown fields, unknown enum value or promotion `type` |
| `INVALID_SORT` | 400 | Sort field not allowed |
| `INVALID_IDEMPOTENCY_KEY` | 400 | Blank or longer than 100 characters |
| `PRODUCT_NOT_FOUND` / `PROMOTION_NOT_FOUND` / `ORDER_NOT_FOUND` | 404 | Unknown id |
| `INSUFFICIENT_STOCK` | 409 | Not enough stock; `errors[]` lists `{productId, requested, available}` |
| `CONCURRENT_MODIFICATION` | 409 | Lock timeout or optimistic-lock failure |
| `UNKNOWN_PRODUCT` | 422 | A promotion references a product that does not exist |
| `IDEMPOTENCY_KEY_REUSED` | 422 | The key was already used with a different request |
| `INTERNAL_ERROR` | 500 | Anything unexpected; details are logged, never returned |

### Logging and request ids

A servlet filter (`RequestLoggingFilter`) gives every request an id. An incoming `X-Request-Id` is reused when it is 1–64 characters of letters, digits, `.`, `_` or `-`; otherwise a UUID is generated. The id is put in the logging context, so it appears in every log line (`[requestId]`), in the `X-Request-Id` response header and in every error body, and a client can quote it when reporting a problem.

Per request the filter logs a `request started` line and a `request completed` line with method, path, status, `startedAt`, `endedAt` and `durationMs`. The completion line is INFO, WARN for 4xx and ERROR for 5xx. The service also logs INFO when an order is confirmed or replayed, WARN on 409/422 and ERROR with a stack trace on 500. Request bodies are never logged. The `json` profile switches the console output to structured ECS JSON, with the request id as a field.

## Assumptions and trade-offs

- **No authentication.** Not part of the requirements. Idempotency keys and orders are therefore global.
- Single currency, implicit. Prices are `BigDecimal` with two decimals.
- Categories are free text, trimmed and upper-cased. Segments are a fixed enum.
- Promotions **stack** in a fixed stage order. A "best single promotion wins" mode would be a different pipeline, and the structure supports it.
- **Quote does not check stock, and confirm re-prices.** A confirmed price can differ from an earlier quote if prices or promotions changed in between. No quote token is issued.
- Idempotency records never expire and only successful confirms are recorded.
- Orders have one status, `CONFIRMED`, and snapshot product and promotion data so later catalogue changes do not rewrite history.
- Limits (100 items per batch or cart, quantity 1000 per product, page size 100) are defensive defaults, not business rules from the brief.
- In-memory H2, single node. The row locks are database-level, so they are not distributed locks.

## With more time

- Order lifecycle (cancel, fulfil, expiry) with stock release on cancel.
- Product update and delete; a restock endpoint.
- Promotion validity windows (start and end dates) and exclusivity or maximum-stack rules.
- Idempotency key expiry and per-client scoping once there is authentication.
- Distributed tracing, metrics dashboards, Flyway migrations, OpenAPI docs and a compose file with a database such as PostgreSQL. They were left out to keep the dependency list lean.
- Quote tokens that pin a price for a short time.

## Repository workflow

`main` holds the project skeleton, then each feature was built on its own branch and merged with `--no-ff`: `feature/products`, `feature/promotions`, `feature/pricing-engine`, `feature/cart-confirm`, `feature/tiered-bulk-promotion`, `task/docker-and-readme` and `task/update-plan`. The service breakdown and the Postman collection are in [`docs/`](docs).
