# Order Management Specification

## Purpose

Purchase orders for authenticated users: a `Pedido` aggregate with `ItemPedido` lines, per-unit price snapshots, atomic stock decrement and a persisted total, all inside one transaction. Orders belong to the authenticated principal (owner never taken from the body); users only access their own orders and ADMIN may access any. `estado` uses Spanish enum values; every created order starts `PENDIENTE`. Payment, cancellation and further state transitions are deferred to a later change.

## Requirements

### Requirement: Place an order (authenticated)

POST /v1/pedidos MUST require authentication (CLIENTE or ADMIN), derive the owner from the JWT and accept one or more lines `{productoId, cantidad}`. `cantidad` MUST be >= 1 and an empty line list MUST be rejected; violations return 400 with Spanish messages. In a single transaction the system MUST snapshot each product's current price as `precioUnitario`, decrement stock by `cantidad`, and persist `total = sum(precioUnitario * cantidad)`.

#### Scenario: Valid order creation

- GIVEN an authenticated CLIENTE and products with sufficient stock
- WHEN POST /v1/pedidos with two lines
- THEN 201 with estado PENDIENTE, items snapshotting current prices, and total equal to the sum of price times quantity
- AND each product's stock is reduced by its ordered cantidad

#### Scenario: Insufficient stock produces no partial order

- GIVEN a line whose cantidad exceeds the product's stock
- WHEN POST /v1/pedidos
- THEN 409 with a Spanish message, no order is created and no product stock changes

#### Scenario: Validation and lookup failures

- GIVEN an empty items list, a cantidad of 0, or an unknown productoId
- WHEN POST /v1/pedidos
- THEN 400 (empty list / bad cantidad) or 404 (unknown product), and no stock changes

### Requirement: Stock decrement is concurrency-safe

When two order placements race for the same product, exactly one MUST succeed; the loser MUST fail with 409 (optimistic-lock pattern reused) and its whole order MUST roll back. Stock MUST NOT go negative and MUST NOT be decremented twice for one successful order.

#### Scenario: Concurrent orders on the last unit

- GIVEN a product with stock 1
- WHEN two orders each request 1 unit concurrently
- THEN exactly one succeeds and the other returns 409 with no stock change

### Requirement: Order history is per-user and private

GET /v1/pedidos MUST list only the authenticated user's orders, newest first. GET /v1/pedidos/{id} MUST return the order to its owner or to an ADMIN only; any other user MUST receive 404 so existence is not disclosed.

#### Scenario: Owner reads own order

- GIVEN an authenticated user who owns one order
- WHEN GET /v1/pedidos and GET /v1/pedidos/{id}
- THEN both return the order

#### Scenario: Cross-user access is hidden

- GIVEN user B and an order owned by user A
- WHEN B requests GET /v1/pedidos/{idA}
- THEN 404

### Requirement: Snapshot integrity and catalog protection

Later changes to a product's price or stock MUST NOT alter existing orders. A product referenced by any ItemPedido MUST NOT be deletable from the catalog (409 conflict, Spanish message). Within this change, orders MUST be immutable after creation.

#### Scenario: Price change after purchase

- GIVEN an existing order with snapshot prices
- WHEN the product's price is updated afterwards
- THEN the stored item prices and order total do not change

#### Scenario: Deleting an ordered product is blocked

- GIVEN a product referenced by at least one order item
- WHEN an ADMIN DELETEs that product
- THEN 409 conflict
