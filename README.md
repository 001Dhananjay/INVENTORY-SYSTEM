# Inventory Service

A high-concurrency, multi-warehouse inventory management microservice built with Spring Boot. It provides atomic stock reservations, a full audit ledger, and automated expiry handling — all designed to prevent overselling even under heavy parallel load.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Features](#features)
3. [Tech Stack](#tech-stack)
4. [Architecture](#architecture)
5. [Project Structure](#project-structure)
6. [Components & Classes](#components--classes)
7. [Database Design](#database-design)
8. [API Endpoints](#api-endpoints)
9. [Setup Instructions](#setup-instructions)
10. [Key Design Decisions](#key-design-decisions)
11. [Future Improvements](#future-improvements)

---

## Project Overview

In any e-commerce or logistics system, stock management gets tricky the moment multiple users try to buy the same item at the same time. This service solves that problem cleanly.

It models inventory across multiple physical warehouses. When an order comes in, stock is **reserved atomically** — meaning it's locked for that order and can't be taken by anyone else. From there, the reservation is either **confirmed** (stock is deducted permanently) or **released** (stock goes back to available). If neither happens within 30 minutes, the system automatically expires the reservation and frees the stock.

Every stock movement — inbound, outbound, reservation, release, confirmation — is written to an immutable audit ledger. This gives you a complete, traceable history of what happened and when.

---

## Features

- **Zero-oversell guarantee** — Pessimistic locking ensures no two transactions can reserve the same stock simultaneously.
- **Atomic multi-item reservations** — All items in a single order are reserved in one transaction; either all succeed or none do.
- **Reservation lifecycle management** — Full support for ACTIVE → CONFIRMED / CANCELLED / EXPIRED transitions.
- **Automatic expiry** — A background scheduler runs every 60 seconds to expire stale reservations and release their stock.
- **Comprehensive audit ledger** — Every stock change is recorded with a type, quantity, timestamp, and reference ID.
- **Multi-warehouse support** — Each product can have separate stock pools per warehouse.
- **Swagger UI** — Interactive API documentation available at runtime.
- **H2 Console** — In-browser database explorer for development and debugging.

---

## Tech Stack

| Layer             | Technology                                  |
|-------------------|---------------------------------------------|
| Language          | Java 17                                     |
| Framework         | Spring Boot 3.2.3                           |
| Persistence       | Spring Data JPA + Hibernate                 |
| Database          | H2 (in-memory, dev) / PostgreSQL (prod)     |
| API Docs          | SpringDoc OpenAPI (Swagger UI) 2.3.0        |
| Boilerplate       | Lombok                                      |
| Build Tool        | Apache Maven 3.9.6                          |
| Scheduling        | Spring `@Scheduled`                         |

---

## Architecture

The service follows a classic **layered architecture**:

```
HTTP Request
    │
    ▼
┌─────────────────────┐
│     Controller      │  ← Validates input, routes to service, returns response
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│      Service        │  ← Business logic, transaction management
│  InventoryService   │
│  WarehouseService   │
│  LedgerService      │
│  ExpiryScheduler    │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│     Repository      │  ← Data access via Spring Data JPA
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│      Database       │  ← H2 (dev) / PostgreSQL (prod)
│  inventory_items    │
│  stock_reservations │
│  stock_ledger       │
│  warehouses         │
└─────────────────────┘
```

The **scheduler** (`ExpiryScheduler`) runs in parallel as a background job, independent of the HTTP request cycle.

---

## Project Structure

```
inventory-service/
├── src/main/java/com/demo/inventory/
│   ├── InventoryApplication.java       # App entry point
│   ├── config/
│   │   └── SwaggerConfig.java          # OpenAPI configuration
│   ├── controller/
│   │   ├── InventoryController.java    # Stock & reservation endpoints
│   │   └── WarehouseController.java    # Warehouse management endpoints
│   ├── service/
│   │   ├── InventoryService.java       # Core reservation & stock logic
│   │   ├── WarehouseService.java       # Warehouse CRUD logic
│   │   ├── LedgerService.java          # Audit trail writing
│   │   └── ExpiryScheduler.java        # Background expiry job
│   ├── repository/
│   │   ├── InventoryItemRepository.java
│   │   ├── StockReservationRepository.java
│   │   ├── StockLedgerRepository.java
│   │   └── WarehouseRepository.java
│   ├── entity/
│   │   ├── InventoryItem.java          # Stock record per product+warehouse
│   │   ├── StockReservation.java       # Active/historical reservations
│   │   ├── StockLedger.java            # Immutable audit log entries
│   │   └── Warehouse.java             # Physical warehouse metadata
│   ├── dto/
│   │   ├── ReservationRequest.java     # Input: reserve stock
│   │   ├── ReservationResponse.java    # Output: reservation result
│   │   ├── AdjustStockRequest.java     # Input: adjust stock levels
│   │   ├── ActionRequest.java          # Input: confirm/release by orderId
│   │   └── GenericResponse.java        # Standard success/error wrapper
│   └── exception/
│       ├── InsufficientStockException.java
│       └── GlobalExceptionHandler.java
└── src/main/resources/
    └── application.yml                 # App configuration
```

---

## Components & Classes

### Entry Point

**`InventoryApplication`**
The standard Spring Boot main class. The only notable addition is `@EnableScheduling`, which activates the background expiry job. Without this annotation, `ExpiryScheduler` would be a bean but its `@Scheduled` methods would never run.

---

### Controllers

Controllers are the front door of the service. They receive HTTP requests, do minimal validation, delegate the real work to services, and return structured responses.

**`InventoryController`** (`/inventory`)
Handles all stock-related operations:
- Reserving stock for an order
- Releasing a reservation (e.g. order cancelled)
- Confirming a reservation (e.g. payment succeeded)
- Adjusting stock levels (inbound/outbound)
- Querying stock by product or warehouse

**`WarehouseController`** (`/warehouses`)
Manages warehouse metadata — creating/updating warehouse records and fetching them by ID or listing all. Warehouses are a prerequisite before any inventory can be tracked.

---

### Services

Services contain the actual business logic. Transactions are managed here, not in controllers.

**`InventoryService`**
The core of the system. Every critical operation lives here:

- `reserveStock()` — Iterates over items in the request, acquires a pessimistic lock on each inventory row, checks available stock, increments `reservedStock`, creates a `StockReservation` record, and writes a `RESERVE` ledger entry. All of this is one transaction — all or nothing.

- `confirmStock()` — Looks up an ACTIVE reservation, marks it CONFIRMED, then decrements both `reservedStock` and `totalStock` on the inventory row. This is what finalizes the sale.

- `releaseStock()` / `processRelease()` — Cancels or expires a reservation, returning the reserved quantity back to the available pool. Idempotent if the reservation is already no longer ACTIVE.

- `adjustStock()` — Manually adds or removes stock (used for restocking, write-offs, corrections). Creates the inventory row if it doesn't exist yet.

- `getProductStock()` / `getWarehouseStock()` — Read-only queries to inspect current stock levels.

**`WarehouseService`**
Thin service that wraps warehouse CRUD. Raises a clear error if a warehouse ID doesn't exist.

**`LedgerService`**
A focused service with one job: write an entry to `stock_ledger`. It uses `Propagation.MANDATORY`, meaning it **must** be called from within an existing transaction. If you accidentally call it outside a transaction, Spring will throw an exception. This is a deliberate safeguard — ledger entries should never be written unless the parent operation also commits successfully.

**`ExpiryScheduler`**
Runs every 60 seconds (configured via `fixedRateString = "60000"`). Queries for all ACTIVE reservations whose `expiresAt` has passed, then calls `processRelease()` for each one, marking them EXPIRED. Errors for individual reservations are caught and logged, so one bad record won't stop the rest from being processed.

---

### Repositories

Repositories are the database access layer, built on Spring Data JPA. You define the method signatures; Spring generates the SQL.

**`InventoryItemRepository`**
The most important repository in the system. The key method is:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<InventoryItem> findByProductIdAndWarehouseIdForUpdate(...)
```
The `PESSIMISTIC_WRITE` lock translates to a `SELECT ... FOR UPDATE` in SQL. This makes other transactions wait until the current one releases the row, preventing concurrent over-reservation.

**`StockReservationRepository`**
- `findByOrderId()` — Used by confirm/release flows to look up an existing reservation.
- `findByStatusAndExpiresAtBefore()` — Used by the expiry scheduler to find stale reservations.

**`StockLedgerRepository`** / **`WarehouseRepository`**
Standard repositories with no custom queries needed.

---

### Entities

Entities map directly to database tables.

**`InventoryItem`** (`inventory_items`)
Tracks stock for a specific product at a specific warehouse. The availability calculation is:
```
availableStock = totalStock - reservedStock - lockedStock
```
The `@Version` field enables **optimistic locking** as a secondary defense layer — if two transactions somehow read the same row simultaneously, only one will write; the other will get a version conflict.

**`StockReservation`** (`stock_reservations`)
A record created every time stock is reserved for an order. Has a lifecycle of states: `ACTIVE → CONFIRMED | CANCELLED | EXPIRED`. The `expiresAt` field drives the scheduler's expiry logic.

**`StockLedger`** (`stock_ledger`)
An append-only audit table. Supported entry types:
| Type      | Meaning                                   |
|-----------|-------------------------------------------|
| `IN`      | Stock added (restock, return)             |
| `OUT`     | Stock removed (write-off, correction)     |
| `RESERVE` | Stock held for a pending order            |
| `RELEASE` | Reserved stock returned (cancel/expiry)   |
| `CONFIRM` | Reserved stock permanently deducted       |

**`Warehouse`** (`warehouses`)
Stores physical location data — country, city, capacity, and status (ACTIVE / INACTIVE / MAINTENANCE). The `id` field is a human-readable code like `WH-IND-01`, used as the foreign key reference throughout the system.

---

### DTOs (Data Transfer Objects)

DTOs are what the API actually accepts and returns. They decouple your API contract from your internal data model.

| DTO                    | Direction | Purpose                                          |
|------------------------|-----------|--------------------------------------------------|
| `ReservationRequest`   | Input     | Order ID + list of items (productId, warehouseId, qty) |
| `ReservationResponse`  | Output    | Reservation status and the returned order ID     |
| `AdjustStockRequest`   | Input     | Product ID, warehouse ID, and quantity delta     |
| `ActionRequest`        | Input     | Simple wrapper carrying just an `orderId`        |
| `GenericResponse`      | Output    | Standard `status` + `message` wrapper            |

---

### Exception Handling

**`InsufficientStockException`**
A custom runtime exception thrown when a reservation request exceeds available stock. Maps to HTTP `422 Unprocessable Entity`.

**`GlobalExceptionHandler`** (`@RestControllerAdvice`)
Intercepts exceptions thrown anywhere in the controller layer and converts them to structured JSON responses with `timestamp`, `status`, `error`, and `message` fields. Catches `InsufficientStockException` specifically, and falls back to a `500 Internal Server Error` for everything else.

---

### Config

**`SwaggerConfig`**
Registers an `OpenAPI` bean that sets the API title and description. SpringDoc uses this to power the Swagger UI at `/swagger-ui/index.html`.

---

## Database Design

### `warehouses`

| Column     | Type    | Notes                                      |
|------------|---------|--------------------------------------------|
| `id`       | VARCHAR | Primary key. Human-readable code (e.g. `WH-IND-01`) |
| `name`     | VARCHAR | Display name                               |
| `country`  | VARCHAR |                                            |
| `city`     | VARCHAR |                                            |
| `capacity` | INTEGER | Max storage units                          |
| `status`   | VARCHAR | `ACTIVE`, `INACTIVE`, `MAINTENANCE`        |

### `inventory_items`

| Column           | Type      | Notes                                            |
|------------------|-----------|--------------------------------------------------|
| `id`             | INTEGER   | Auto-generated PK                                |
| `product_id`     | VARCHAR   | External product identifier                      |
| `warehouse_id`   | VARCHAR   | FK → `warehouses.id`                             |
| `total_stock`    | INTEGER   | Physical units on hand                           |
| `reserved_stock` | INTEGER   | Units held for pending orders                    |
| `locked_stock`   | INTEGER   | Units locked for other reasons (e.g. QA hold)    |
| `version`        | INTEGER   | Optimistic locking version counter               |
| `updated_at`     | TIMESTAMP | Auto-updated on every write                      |

> **Unique constraint:** `(product_id, warehouse_id)` — one row per product per warehouse.

### `stock_reservations`

| Column        | Type      | Notes                                               |
|---------------|-----------|-----------------------------------------------------|
| `id`          | VARCHAR   | UUID, manually assigned                             |
| `order_id`    | VARCHAR   | The external order this reservation belongs to      |
| `product_id`  | VARCHAR   |                                                     |
| `warehouse_id`| VARCHAR   |                                                     |
| `quantity`    | INTEGER   |                                                     |
| `status`      | VARCHAR   | `ACTIVE`, `CONFIRMED`, `CANCELLED`, `EXPIRED`       |
| `expires_at`  | TIMESTAMP | 30 minutes from creation                            |
| `created_at`  | TIMESTAMP | Auto-set on insert                                  |

### `stock_ledger`

| Column         | Type      | Notes                                          |
|----------------|-----------|------------------------------------------------|
| `id`           | INTEGER   | Auto-generated PK                              |
| `product_id`   | VARCHAR   |                                                |
| `warehouse_id` | VARCHAR   |                                                |
| `type`         | VARCHAR   | `IN`, `OUT`, `RESERVE`, `RELEASE`, `CONFIRM`   |
| `quantity`     | INTEGER   | Always positive                                |
| `reference_id` | VARCHAR   | Order ID or `MANUAL_ADJUSTMENT`                |
| `timestamp`    | TIMESTAMP | When the entry was recorded                    |

---

## API Endpoints

Base URL: `http://localhost:8080`

### Inventory

| Method | Endpoint                        | Description                                     |
|--------|---------------------------------|-------------------------------------------------|
| `POST` | `/inventory/reserve`            | Reserve stock for an order                      |
| `POST` | `/inventory/release`            | Release a reservation (cancel)                  |
| `POST` | `/inventory/confirm`            | Confirm a reservation (finalize sale)           |
| `POST` | `/inventory/adjust`             | Adjust stock levels (restock / write-off)       |
| `GET`  | `/inventory/{productId}`        | Get stock for a product across all warehouses   |
| `GET`  | `/inventory/warehouse/{warehouseId}` | Get all stock in a specific warehouse      |

#### `POST /inventory/reserve`

```json
{
  "orderId": "ORD-001",
  "items": [
    {
      "productId": "PROD-A",
      "warehouseId": "WH-IND-01",
      "quantity": 5
    }
  ]
}
```

**Response:**
```json
{
  "status": "RESERVED",
  "reservationId": "ORD-001"
}
```

#### `POST /inventory/confirm` or `/inventory/release`

```json
{
  "orderId": "ORD-001"
}
```

#### `POST /inventory/adjust`

```json
{
  "productId": "PROD-A",
  "warehouseId": "WH-IND-01",
  "quantity": 100
}
```


---

### Warehouses

| Method | Endpoint             | Description                             |
|--------|----------------------|-----------------------------------------|
| `POST` | `/warehouses`        | Create or update a warehouse            |
| `GET`  | `/warehouses`        | List all warehouses                     |
| `GET`  | `/warehouses/{id}`   | Get a single warehouse by ID            |

#### `POST /warehouses`

```json
{
  "id": "WH-IND-01",
  "name": "Mumbai Central Warehouse",
  "country": "India",
  "city": "Mumbai",
  "capacity": 50000,
  "status": "ACTIVE"
}
```

---

### Developer Tools

| Tool           | URL                              |
|----------------|----------------------------------|
| Swagger UI     | http://localhost:8080/swagger-ui/index.html |
| H2 Console     | http://localhost:8080/h2-console |
| OpenAPI JSON   | http://localhost:8080/v3/api-docs |

> H2 credentials: JDBC URL `jdbc:h2:mem:inventorydb`, Username: `sa`, Password: `password`

---

## Setup Instructions

### Prerequisites

- Java 17 or higher
- Maven 
- No database setup required (uses H2 in-memory by default)

### Step 1 – Clone or open the project

### Step 2 – Build the project

### Step 3 – Run the application

### Step 4 – Verify it's running

Open your browser and go to:
- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
- **H2 Console**: http://localhost:8080/h2-console

### Step 5 – Try your first request

Create a warehouse, then reserve some stock:

```bash
# Create a warehouse
curl -X POST http://localhost:8080/warehouses \
  -H "Content-Type: application/json" \
  -d '{"id":"WH-IND-01","name":"Mumbai Warehouse","country":"India","city":"Mumbai","capacity":10000,"status":"ACTIVE"}'

# Add 100 units of stock
curl -X POST http://localhost:8080/inventory/adjust \
  -H "Content-Type: application/json" \
  -d '{"productId":"PROD-001","warehouseId":"WH-IND-01","quantity":100}'

# Reserve 10 units for an order
curl -X POST http://localhost:8080/inventory/reserve \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-001","items":[{"productId":"PROD-001","warehouseId":"WH-IND-01","quantity":10}]}'
```

### Switching to PostgreSQL (Production)

1. Comment out the H2 dependency in `pom.xml` and uncomment the PostgreSQL one.
2. Update `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/inventorydb
    driverClassName: org.postgresql.Driver
    username: your_user
    password: your_password
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate  # Use 'update' only in development
```

---

## Key Design Decisions

### 1. Pessimistic locking over optimistic locking
The `findByProductIdAndWarehouseIdForUpdate()` method uses `PESSIMISTIC_WRITE` (`SELECT FOR UPDATE`). In a high-concurrency environment with many competing reservations for the same SKU, pessimistic locking provides hard serialization — one transaction wins, others wait. Optimistic locking (`@Version`) is retained as a secondary safety net but isn't the primary concurrency control.

### 2. `LedgerService` uses `Propagation.MANDATORY`
The ledger entry is always written inside the same database transaction as the stock update. If the stock update fails and rolls back, the ledger entry rolls back with it. The `MANDATORY` propagation makes this contract explicit — calling `recordLedgerEntry()` without an active transaction is a programming error that will fail loudly at runtime rather than silently writing orphaned ledger entries.

### 3. TTL-based reservation expiry via scheduler
Rather than relying on callers to explicitly release reservations, the system has a safety net: the `ExpiryScheduler` runs every 60 seconds and cleans up any ACTIVE reservations that have passed their expiry time. This prevents stock from being permanently locked if a caller crashes or never gets back to us. The TTL is 30 minutes by default.

### 4. warehouse IDs
Warehouse IDs are strings (e.g. `WH-IND-01`) rather than auto-generated integers. This makes foreign key references readable in logs and ledger entries without needing to join back to the warehouse table.

### 5. `availableStock` is computed, not stored
Rather than maintaining a fourth column, `getAvailableStock()` computes `totalStock - reservedStock - lockedStock` on the fly. This avoids the risk of the computed value going out of sync with its components, which is a particularly bad bug in a stock management system.

### 6. Single-transaction multi-item reservation
All items in a `ReservationRequest` are reserved in a single `@Transactional` block. If any item fails (e.g. insufficient stock or product not found), the entire transaction rolls back. This prevents partial reservations where some items are locked and others aren't.

---

## Future Improvements

- **Switch to PostgreSQL for production** — H2 is great for development, but production workloads need a persistent, replicated database. The POM already has the PostgreSQL dependency commented and ready to enable.

- **Kafka event publishing** — After a reservation, confirmation, or release, publish an event to a Kafka topic (`inventory.reserved`, `inventory.confirmed`, etc.). This lets downstream services (order service, notifications) react asynchronously without tight coupling.

- **Distributed locking for multi-node deployments** — The current pessimistic DB lock works correctly when running a single instance. In a horizontally scaled setup, consider Redis-based distributed locks (Redisson) for scenarios where the scheduler or competing API calls run on different JVMs.

- **Configurable TTL per product or category** — Right now the 30-minute expiry is hardcoded. A more flexible system would allow different TTLs for different product types (e.g. flash-sale items expire in 5 minutes, wholesale orders in 2 hours).

- **Inventory reservation for multiple orders in one request** — Currently, one `ReservationRequest` maps to one `orderId`. A batch reservation API would let callers reserve stock for multiple orders in one round-trip.

- **Dead Letter Queue (DLQ) for failed expiry releases** — If `processRelease()` fails for a reservation during the scheduler run, the error is currently just logged. A DLQ or retry table would ensure failed releases are retried and not silently dropped.

- **Metrics & observability** — Expose Micrometer metrics (available stock levels, reservation rates, expiry counts) to Prometheus, and add distributed tracing with OpenTelemetry for end-to-end request visibility.

- **Unit & integration tests** — The test scope is declared in the POM but no tests are written yet. Priority tests would cover the `reserveStock` concurrency behavior and the expiry scheduler logic.
