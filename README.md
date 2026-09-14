# 🛹 Skate Shop — Microservices E-Commerce

A full-stack e-commerce platform built with Spring Boot microservices, Apache Kafka, Redis, Stripe payments and an Angular frontend.

> 🧠 **Note:** This project focuses on backend architecture — microservices, event-driven communication, caching and payment integration. The frontend exists to demonstrate that the full flow works end-to-end.

---

## 🔐 Authentication

Authentication and JWT security are intentionally out of scope for this project — they are fully demonstrated in [Project 1 — User Management API](https://github.com/bakaruu/user-management-api).

This project focuses on microservices architecture, event-driven communication and payment integration. In a production environment, an authentication service would sit behind the API Gateway handling JWT validation before routing requests to downstream services.


## ⚙️ Tech Stack

| Technology | |
|---|---|
| ☕ Java 21 + Spring Boot 3.5 | Backend services |
| 🌐 Spring Cloud Gateway + Eureka | API Gateway + Service Discovery |
| 🔌 OpenFeign + Resilience4j | Inter-service calls with circuit breakers |
| 📨 Apache Kafka | Event-driven messaging |
| ⚡ Redis | Product catalog caching |
| 🐘 PostgreSQL + Flyway | Per-service databases (x4) with versioned migrations |
| 🧪 Testcontainers | Real-infra integration tests (Postgres/Kafka/Redis) |
| 💳 Stripe | Payment processing |
| 🅰️ Angular 21 | Frontend SPA |
| 🐳 Docker Compose | Infrastructure |

---

## 🧩 Services

| Service | Port | Description |
|---|---|---|
| Discovery Service | 8761 | Eureka service registry |
| API Gateway | 8080 | Single entry point, load balancing |
| Product Service | 8081 | Product catalog with Redis cache |
| Inventory Service | 8082 | Stock management |
| Order Service | 8083 | Order processing, Kafka producer |
| Notification Service | 8084 | Kafka consumer, email notifications |
| Payment Service | 8085 | Stripe checkout + webhooks |

---

## ✨ Features

| Feature | |
|---|---|
| 🛍️ Product catalog with filters | Category, brand, price range |
| 🛒 Shopping cart → checkout | Client-side cart state, dedicated checkout/review step |
| 📦 Order management | Full order lifecycle with status tracking |
| 💰 Server-side pricing | Order totals are computed from `product-service`, never trusted from the client |
| 📊 Real stock reservation | Stock is reserved synchronously at order time (not just at payment) and released on cancellation/failed payment |
| 💳 Stripe payments | Real checkout session, test mode, webhook idempotency |
| 📨 Event-driven stock | Kafka decouples order → inventory → notification flow |
| ⚡ Redis caching | Product cache with TTL + invalidation |
| 🔍 Service discovery | Dynamic registration via Eureka |
| 🛡️ Resilience | Circuit breakers + fallbacks at the gateway for each downstream service |
| ⏱️ Abandoned checkout recovery | A scheduled sweep cancels orders left `PENDING` past a TTL and releases their stock reservation; the matching Stripe Checkout Session expiry is kept in sync so a stale session can never be paid after the fact |
| 🔁 Idempotent order creation | An `Idempotency-Key` header on order creation means a retried or double-submitted request returns the original order instead of creating - and reserving stock for - a duplicate |
| 🚦 Gateway rate limiting | Per-client-IP token bucket (Bucket4j) on every `/api/**` request, ahead of routing |
| 🔗 Correlation IDs | `X-Correlation-Id` is minted at the gateway and propagated across every HTTP entry point and every Kafka hop, so log lines for one request can be tied together across services — with one known gap: it's lost on the specific Feign calls guarded by a Resilience4j `TimeLimiter` (see `FeignCorrelationIdInterceptor`'s Javadoc) |

---

## 📡 Event & Request Flow
```
Checkout
    │
    ├──▶ order-service ──(Feign)──▶ product-service   (authoritative price lookup)
    │                 └─(Feign)──▶ inventory-service  (synchronous stock reservation)
    │
    ├──▶ Kafka: order-placed ──▶ Notification Service (order confirmation)
    │
    ├──▶ Stripe Checkout Session
    │         │
    │         ├──▶ Webhook: checkout.session.completed
    │         │        └──▶ payment-service → Kafka: payment-completed
    │         │                 ├──▶ order-service → status PAID
    │         │                 ├──▶ inventory-service → decrements stock
    │         │                 └──▶ notification-service → payment confirmation
    │         │
    │         └──▶ Webhook: checkout.session.expired
    │                  └──▶ payment-service → Kafka: payment-failed
    │                           ├──▶ order-service → status CANCELLED
    │                           ├──▶ inventory-service → releases reserved stock
    │                           └──▶ notification-service → payment failed notice
    │
    └──▶ [no webhook ever arrives — abandoned checkout]
              └──▶ order-service: OrderExpiryScheduler (polls every N ms)
                       └──▶ order past reservation-ttl-minutes → status CANCELLED
                                └──▶ inventory-service → releases reserved stock
```

---

## 🔄 Distributed Transactions (Saga Pattern)

There's no distributed transaction spanning `order-service`, `inventory-service` and
`payment-service` — each one commits to its **own** database independently. Consistency across
all three is achieved with a **saga**: a sequence of local transactions, each with a defined
compensating action for when a later step fails, instead of an all-or-nothing distributed commit.
This project mixes both saga styles:

- **Orchestration** for order creation — `order-service` calls the other services directly, in
  order, and is responsible for undoing the previous step if a later one fails.
- **Choreography** for everything after checkout — `order-service` and `payment-service` never
  call `inventory-service` or each other again after that point; each one just reacts
  independently to Kafka events.

### Order creation (orchestrated)

| Step | Service | If it fails |
|---|---|---|
| 1. Fetch authoritative prices | `product-service` | Nothing to undo (read-only) — `order-service` returns 400/503 and stops |
| 2. Reserve stock | `inventory-service` | Nothing to undo yet — a `409` (insufficient stock) means `order-service` stops before persisting anything |
| 3. Persist the order as `PENDING` | `order-service` | **Compensating action:** release the reservation from step 2 (best-effort, in a `catch` block) |
| 4. Publish `order-placed` | Kafka | Informational only — nothing downstream reserves anything off the back of this event, so a failure here needs no compensation |

### After checkout (choreographed via Kafka)

Once the order exists, nothing orchestrates the rest — `payment-service` publishes an event, and
every interested service reacts to it on its own, with no central coordinator:

| Trigger | `order-service` reacts | `inventory-service` reacts | `notification-service` reacts |
|---|---|---|---|
| `payment-completed` | status → `PAID` | stock **decremented for real** (reservation becomes an actual deduction) | sends confirmation |
| `payment-failed` (Stripe session expired) | status → `CANCELLED` | reservation **released** | sends failure notice |
| Abandoned checkout, no webhook ever arrives | `OrderExpiryScheduler` cancels it once past `reservation-ttl-minutes` | reservation released (same `order-cancelled` event, `paid=false`) | sends failure notice |
| Customer hits "back" from Stripe | frontend cancels the pending order immediately, instead of waiting for the TTL sweep above | reservation released right away | — |

The `paid` flag carried on the `order-cancelled` event is what tells `inventory-service` which
compensating action to run — restock a real deduction, or just release a reservation that was
never fulfilled. Mixing those up would either oversell (releasing stock that was already sold) or
silently understock (restocking a reservation that was never deducted in the first place) —
exactly the kind of thing worth testing directly rather than trusting by inspection; see
`OrderEventConsumerTest` in `inventory-service` for both branches.

### The one gap this doesn't cover

If stock reservation succeeds but saving the order then fails for some unrelated reason (e.g. the
database connection drops), `order-service` attempts a best-effort release of that reservation in
a `catch` block. If *that* release call also fails, the reservation is left stuck — there's no
order row for `OrderExpiryScheduler` to ever find and expire, since the order was never
persisted. A production system would close this with an outbox/reconciliation job (e.g.
`inventory-service` periodically auditing "reserved" totals against orders that actually
reference them); this project accepts the gap rather than building full saga-orchestration
tooling for one narrow failure window.

---

## 🚀 Getting Started

### 1. Start infrastructure
```bash
docker-compose up -d
```

Starts PostgreSQL ×4, Kafka, Zookeeper and Redis.

### 2. Start services (in order)
```
1. DiscoveryServiceApplication   → :8761
2. ProductServiceApplication     → :8081
3. InventoryServiceApplication   → :8082
4. OrderServiceApplication       → :8083
5. NotificationServiceApplication → :8084
6. PaymentServiceApplication     → :8085
7. ApiGatewayApplication         → :8080
```

Each service applies its own Flyway migrations (schema + seed data for products/inventory) automatically on startup — no manual `data.sql` step needed.

### 3. Environment variables

Set these in your IDE run configurations for the Payment Service:
```
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
```

Optional, on both Order Service and Payment Service — how long a `PENDING` order can go unpaid
before it's auto-cancelled and its stock released (defaults to 30 minutes if unset; must be kept
equal on both services, since it also sets the matching Stripe Checkout Session expiry):
```
RESERVATION_TTL_MINUTES=30
```

### 4. Start frontend
```bash
cd frontend/skate-shop-frontend
npm install
ng serve
```

Open at `http://localhost:4200`. All API calls go through the Gateway at `http://localhost:8080` (configured in `src/environments/environment.ts`) — the frontend never talks to individual services directly.

---

## 📡 API Documentation

Each service exposes Swagger UI:

| Service | URL |
|---|---|
| Product | `http://localhost:8081/swagger-ui.html` |
| Inventory | `http://localhost:8082/swagger-ui.html` |
| Order | `http://localhost:8083/swagger-ui.html` |
| Payment | `http://localhost:8085/swagger-ui.html` |

---


## 🔑 Test Payment

Use Stripe test card to complete a purchase:

| Field | Value |
|---|---|
| Card number | `4242 4242 4242 4242` |
| Expiry | Any future date |
| CVC | Any 3 digits |

---

## ✅ Testing

Each backend service has unit tests (JUnit 5 + Mockito) and a handful of Testcontainers-backed
integration tests that exercise real Postgres/Kafka/Redis instead of mocks. Run the full suite
(unit + integration) for every service — requires Docker running locally:

```bash
mvn clean verify
```

CI runs the same command on every push/PR; Docker images are only built and pushed on `main`, and
only after the suite is green.

---

## 👤 Author

**Aru** — [GitHub](https://github.com/bakaruu)