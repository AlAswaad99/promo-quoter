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
