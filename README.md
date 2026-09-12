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