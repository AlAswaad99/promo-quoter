# PromoQuoter

A Spring Boot REST service that prices a shopping cart with pluggable promotion rules and reserves inventory atomically when an order is confirmed.

- **Quote** a cart: per-line and cart totals plus every applied promotion, in the order it was applied.
- **Confirm** a cart: re-prices, locks the stock rows, decrements stock and stores an order in one transaction. Safe under concurrent calls; retries with an `Idempotency-Key` never reserve twice.
- **Rules** decide prices and what is allowed. A rule is data: optional conditions on the cart, an optional target (which lines), and one action (percent off, amount off, free units, cart discount, surcharge, shipping waiver, free gift, or reject the cart). Rules become Strategy objects built by a Factory and run through an ordered Pipeline. Adding an action kind means adding classes. No orchestration code changes. The three classic promotion types are shortcuts that expand into rules.

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

The HTML coverage report is written to `target/site/jacoco/index.html`. At the time of writing: **96.8 % line, 92.1 % branch coverage** across 445 tests.

| Kind | What it covers |
|---|---|
| Unit | The condition model and evaluator, the rule parser and its limits, every action rule (rounding, caps, thresholds, the free-units table), the pipeline ordering, tie-breaks and `stopAfter`, the audit decorator, the rule factory (eligibility, fail-fast wiring), the engine end to end, cart normalisation, money rounding, paging and sort policy |
| Integration (`*IT`) | Every endpoint on a real Spring context and H2 through MockMvc: success paths, every error code, validation, paging, filters, idempotency, request-id headers and log output, strict input handling, stored rules, rule preview, shipping, gifts and rule violations |
| Concurrency | 20 threads against 5 units of stock (exactly 5 orders, rest get 409); multi-product carts stay all-or-nothing; same `Idempotency-Key` from 10 threads reserves once; opposite lock orders do not deadlock; 12 simultaneous orders share 5 free gifts without overselling |

The concurrency tests have teeth: with the row lock disabled, three of them fail.

## API

Base path `/api/v1`. JSON only. Money is a string with two decimals (`"12.50"`). Timestamps are ISO-8601 UTC. Every response carries an `X-Request-Id` header. Unknown JSON fields are rejected with `400 MALFORMED_REQUEST`.

| Method | Path | Notes |
|---|---|---|
| POST | `/products` | Array of 1–100 products, saved all-or-nothing, `201` |
| GET | `/products`, `/products/{id}` | List supports `page`, `size`, `sort`, `category` |
| POST | `/promotions` | Array of 1–100 promotions or rules of mixed types, all-or-nothing, `201` |
| GET | `/promotions`, `/promotions/{id}` | List supports `page`, `size`, `sort`, `type`, `active`, `kind` |
| PATCH | `/promotions/{id}` | Change `name`, `active`, `priority` and/or `eligibleSegments`. Rule parameters (percent, quantities) are fixed: delete and recreate to swap a rule |
| DELETE | `/promotions/{id}` | Removes the promotion. Past orders keep their audit trail. `204` |
| POST | `/promotions/preview` | `{ "rule": {...}, "cart": {...} }`. Validates the rule like creation, then prices the cart with it plus the active rules. Nothing is saved or reserved |
| POST | `/cart/quote` | Prices a cart. Does not change stock. `422 RULE_VIOLATION` if a validation rule matches |
| POST | `/cart/confirm` | Same body as quote plus optional `Idempotency-Key` header. `201` with an `orderId`. Gifts are reserved with the cart |
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

### Rules

`POST /promotions` also accepts `"type": "RULE"`, the generic form. The three shortcuts above are stored as rules too (the response of `GET /promotions` shows their rule form next to their original parameters).

```json
{ "type": "RULE", "kind": "PRICING", "name": "Kitchen deal in November", "priority": 0,
  "stage": 300, "stopAfter": false, "eligibleSegments": ["VIP"],
  "when":   { "all": [ { "field": "cart.subtotal", "op": "gte", "value": "100.00" },
                       { "field": "date", "op": "between", "value": ["2026-11-01", "2026-11-30"] } ] },
  "target": { "all": [ { "field": "product.category", "op": "eq", "value": "KITCHEN" },
                       { "field": "line.qty", "op": "gte", "value": 10 } ] },
  "action": { "kind": "PERCENT_OFF", "percent": 5 } }
```

- **`kind`**: `PRICING` (changes the price) or `VALIDATION` (rejects the cart when it matches).
- **`target`**: which lines the rule affects, checked per line. Fields: `product.id`, `product.name`, `product.category`, `product.price`, `product.stock`, `line.qty`. Missing means every line.
- **`when`**: whether the rule fires at all, checked once per cart. Fields: `cart.subtotal` (before discounts), `cart.total` (the running total after earlier rules), `cart.itemCount`, `customer.segment`, `date` (today, UTC). Missing means always.
- **Conditions** combine with `all`, `any` and `not` (nested at most 5 deep, at most 20 conditions) and compare with `eq`, `ne`, `gt`, `gte`, `lt`, `lte`, `between` (inclusive), `in`, `notIn`, `startsWith` and `contains`. Text compares case-insensitively. A cart-level action with a `target` fires only when at least one line matches it.
- **`stage`** orders rules (lower runs first, then `priority`, creation time, id). Pricing stages are 0–899 and validation stages 900–999. The default depends on the action.
- **`stopAfter`**: once the rule has an effect, later rules skip the lines it discounted (line actions) or every later pricing rule (cart actions). Skipped rules are listed in `skipped`. Validation rules always run.

| Action | Kind | What it does | Default stage |
|---|---|---|---|
| `PERCENT_OFF {percent}` | pricing, per line | Percent of the line's running total | 100 |
| `FREE_UNITS {buyQty, freeQty}` | pricing, per line | `floor(qty / (buyQty + freeQty)) × freeQty` units free | 200 |
| `AMOUNT_OFF {amount}` | pricing, per line | A fixed amount off each matching line (not per unit), capped at the line total | 300 |
| `AMOUNT_OFF_CART {amount}` | pricing, cart | A cart-level discount, capped at what is left | 400 |
| `FREE_GIFT {productId, qty}` | pricing, cart | Adds a 0.00 line that reserves the gift's stock; skipped when it is not in stock (at most 100 gift units per order) | 500 |
| `SHIPPING_WAIVER` | pricing, cart | Sets shipping to 0.00 | 600 |
| `SURCHARGE {amount}` | pricing, cart | Adds a fee | 700 |
| `REJECT {message}` | validation | Rejects the cart when its conditions match, so you write the violation | 900 |

The legacy shortcuts keep their stages (100, 200, 300), so their prices did not change: `PERCENT_OFF_CATEGORY` is `target: product.category = X` with `PERCENT_OFF`, `BUY_X_GET_Y` is `target: product.id = X` with `FREE_UNITS`, and `TIERED_BULK` is `target: product.id = X and line.qty >= N` with `PERCENT_OFF`.

A rule is validated when it is created: unknown fields, operators that do not fit the field, bad values, wrong action for the kind, stages out of range and anything over the limits give `400 VALIDATION_FAILED` with the field path of each problem (for example `[0].target.all[1].op`), and a product id that does not exist gives `422 UNKNOWN_PRODUCT`. Use `POST /promotions/preview` to try a rule against a sample cart first.

**Shipping** is a flat fee set with `promoquoter.shipping.flat-fee` (default `0.00`, so nothing changes until you set it). Quotes and orders show it as `shipping`, it is part of `total`, and a `SHIPPING_WAIVER` rule removes it.

**Cart-level amounts** appear in `adjustments` (`{sequence, promotionId, name, action, direction, amount}`, direction `DISCOUNT` or `CHARGE`), never spread over the lines. The total is `subtotal - totalDiscount + surcharges + shipping`, and `totalDiscount` covers line and cart discounts.

**Validation rules** run last, on the final discounted cart, for both quote and confirm. A match returns `422 RULE_VIOLATION` with one entry per violated rule (`promotionId`, `name`, `message`). A rejected confirm reserves nothing.

**Gifts** are listed in `gifts` (price 0.00). Confirm reserves their stock under the same row locks as the cart. A gift that is out of stock is skipped and noted in `skipped` (quote) or `notes` (order); the paid order still succeeds.

Orders keep a snapshot of everything: applied promotions, adjustments, shipping, gift lines and notes. Later edits or deletions of rules never rewrite an order.

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
| Strategy | `PricingRule` and one class per action kind (`PercentOffRule`, `FreeUnitsRule`, `FreeGiftRule`, `RejectRule`, ...) | Each rule encapsulates one algorithm. The conditions are data that only decide when it runs |
| Pipeline / Chain | `PricingPipeline` | Sorts the rules by stage, priority, creation time and id, and passes one `PricingContext` through them |
| Factory | `PromotionRuleFactory` + one `ActionRuleProvider` per action kind | Builds rules from persisted `RuleDefinition`s; filters by active flag and customer segment |
| Decorator | `AuditingRule` | Wraps every rule and records exactly what it changed (line discounts, adjustments, gifts, skips) in the audit trail |

The engine has no Spring or JPA dependencies, so it is tested without starting a context.

**Adding a rule** is data: post a `RULE`. **Adding an action kind** takes an `ActionKind` value, an `ActionSpec` record, a rule class, an `ActionRuleProvider` bean, and a case each in the rule parser and the JSON writer. `PricingEngine`, `PricingPipeline`, `QuoteService` and `CheckoutService` are untouched. The factory refuses to start if any `ActionKind` has no provider, so a half-wired action cannot ship. Conditions are our own small tree evaluator with a fixed operator list: nothing a rule author writes is ever executed as code.

### Pricing rules

1. A line starts at `unitPrice × qty`.
2. Rules run in stage order. The legacy shortcuts sit at **`PERCENT_OFF_CATEGORY` (100) → `BUY_X_GET_Y` (200) → `TIERED_BULK` (300)**, and generic rules use the defaults in the table above. Within a stage: `priority` ascending, then creation time, then id. Each rule works on the running total left by the previous ones, so discounts stack.
3. Percent rules take `percent` of the line's running total (category and text matching is case-insensitive).
4. Free units: `floor(qty / (buyQty + freeQty)) × freeQty` units are free, valued at the line's current (already discounted) unit price. Free items are never added to the cart automatically. Buy 2 get 1: qty 2 → 0 free, 3 → 1, 6 → 2, 7 → 2.
5. Every discount is rounded `HALF_UP` to cents when applied and capped at the line's remaining total, so a line never goes below `0.00`. Cart discounts are capped at what is left of the goods total.
6. The total is the lines after discounts, minus cart discounts, plus surcharges and shipping. Lines and cart amounts always reconcile exactly with the total.
7. A rule applies only if it is active and either has no segment restriction or lists the cart's `customerSegment` (default `REGULAR`), and its `when` and `target` conditions match.

Worked example (3 mugs at 12.00): 36.00 → 10% off takes 3.60 → 32.40 → the free mug is valued at 32.40 / 3 = 10.80 → **21.60**.

### Concurrency and idempotency

Confirm runs as one transaction:

1. If an `Idempotency-Key` is present, its record (key, request hash, pre-generated `orderId`) is inserted first. The key is a primary key, so two simultaneous requests with the same key cannot both pass: the second waits on the first, then fails with a duplicate key and is answered with the first order.
2. The product rows are loaded with `SELECT … FOR UPDATE` (pessimistic write lock) in a single query ordered by id, so every transaction takes locks in the same order and carts that share products cannot deadlock. The gift products of the active gift rules are locked in the same query, so gifts cannot break the lock order.
3. Stock is checked for every line and **all** shortages are reported together in one `409 INSUFFICIENT_STOCK`. Nothing is decremented unless every line fits.
4. Stock is decremented, the cart is priced from the locked rows (a matching validation rule aborts here and rolls everything back), the gift stock that is available is decremented, and the order with its price snapshot is saved.

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
| `UNKNOWN_PRODUCT` | 422 | A promotion or rule references a product that does not exist |
| `RULE_VIOLATION` | 422 | A validation rule matched the cart; `errors[]` lists `{promotionId, name, message}` for every violated rule |
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
- **Shipping** is one flat configured fee, `0.00` by default. There is no per-product or per-destination shipping.
- **Rules** cannot be edited after creation except `name`, `active`, `priority` and `eligibleSegments`: delete and recreate. Products have no brand or owner field, so a rule that should hit "one seller's products" lists their product ids.
- **Gifts** are best effort. A gift that is out of stock is skipped and the paid order still succeeds. Gifts add at most 100 units per order.
- The `date` condition is today in UTC.
- At most 500 promotions can be active at once (creating or re-activating beyond that is a `400`).

## With more time

- Order lifecycle (cancel, fulfil, expiry) with stock release on cancel.
- Product update and delete; a restock endpoint.
- Promotion validity windows (start and end dates) and exclusivity or maximum-stack rules.
- Idempotency key expiry and per-client scoping once there is authentication.
- Distributed tracing, metrics dashboards, Flyway migrations, OpenAPI docs and a compose file with a database such as PostgreSQL. They were left out to keep the dependency list lean.
- Quote tokens that pin a price for a short time.
- Per-unit fixed discounts, per-product shipping costs, rule versioning and a rules editor.

## Repository workflow

`main` holds the project skeleton, then each feature was built on its own branch and merged with `--no-ff`: `feature/products`, `feature/promotions`, `feature/pricing-engine`, `feature/cart-confirm`, `feature/tiered-bulk-promotion`, `task/docker-and-readme`, `task/update-plan` and `rules-engine-rework`. The service breakdown and the Postman collection are in [`docs/`](docs).
