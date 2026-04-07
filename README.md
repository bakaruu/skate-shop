# 🛹 Skate Shop — Microservices E-Commerce

A full-stack e-commerce platform built with Spring Boot microservices, Apache Kafka, Redis, Stripe payments and an Angular frontend.

> 🧠 **Note:** This project focuses on backend architecture — microservices, event-driven communication, caching and payment integration. The frontend exists to demonstrate that the full flow works end-to-end.

---

## 🔗 Related Repository

This project consumes a microservices backend built from scratch:
👉 [skate-shop-api](https://github.com/bakaruu/skate-shop)

Make sure all backend services are running before starting the frontend.

---

## ⚙️ Tech Stack

| Technology | |
|---|---|
| ☕ Java 21 + Spring Boot 3.5 | Backend services |
| 🌐 Spring Cloud Gateway + Eureka | API Gateway + Service Discovery |
| 📨 Apache Kafka | Event-driven messaging |
| ⚡ Redis | Product catalog caching |
| 🐘 PostgreSQL | Per-service databases (x4) |
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
| 🛒 Shopping cart | Client-side state with Angular Signals |
| 📦 Order management | Full order lifecycle with status tracking |
| 💳 Stripe payments | Real checkout session, test mode |
| 📨 Event-driven stock | Kafka decouples order → inventory flow |
| ⚡ Redis caching | Product cache with TTL + invalidation |
| 🔍 Service discovery | Dynamic registration via Eureka |

---

## 📡 Event Flow
```
Order Created
    │
    ├──▶ Kafka: order-placed
    │         ├──▶ Inventory Service → decrements stock
    │         └──▶ Notification Service → sends confirmation
    │
    └──▶ Stripe Webhook: payment-completed
              └──▶ Payment Service → updates order to PAID
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

### 3. Environment variables

Set these in your IDE run configurations for the Payment Service:
```
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
```

### 4. Start frontend
```bash
cd frontend/skate-shop-frontend
npm install
ng serve
```

Open at `http://localhost:4200`

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

## 👤 Author

**Aru** — [GitHub](https://github.com/bakaruu)