# Shopping Cart Specification

## Purpose

Authenticated-user shopping cart for Bloque 3. This spec fixes storage-agnostic BEHAVIOR; the concrete model (ephemeral in-memory vs persisted) is an explicit design decision. Anonymous/guest carts and merge-on-login are out of scope. The cart never decrements stock — stock is validated at order placement (see order-management).

> **Decision point for design — ephemeral vs persisted cart.** Explore §4.4 records tradeoffs (stateless API has no HTTP session; caffeine is declared but unused; persisted carts fit JPA/Testcontainers but add entities, FK implications for product deletion, orphan cleanup and diff size vs the 400-line review budget). Design MUST choose one option, document the rationale, and show how that choice satisfies every requirement below.

## Requirements

### Requirement: Cart is per authenticated user

All `/v1/carrito` endpoints MUST require authentication. Every operation targets the principal's own cart; line identity resolves only within that cart: operating on a line owned by another user MUST return 404.

#### Scenario: Unauthenticated cart access

- GIVEN no valid JWT
- WHEN GET /v1/carrito
- THEN 403 (like other protected endpoints)

#### Scenario: Cross-user line isolation

- GIVEN user A owns a cart line and user B owns none
- WHEN B PATCHes or DELETEs A's line id
- THEN 404

### Requirement: Add, merge and update lines

POST /v1/carrito/items `{productoId, cantidad}` MUST add a line when the product exists (unknown productoId returns 404). `cantidad` MUST be >= 1; otherwise 400 with a Spanish message. Adding a product already present MUST merge into its single line by summing quantities; duplicate product lines MUST NOT exist. PATCH /v1/carrito/items/{id} MUST set the quantity (>= 1). DELETE /v1/carrito/items/{id} MUST remove the line; DELETE /v1/carrito MUST empty the cart.

#### Scenario: Adding two different products

- GIVEN an empty cart
- WHEN two different products are added
- THEN the cart contains two lines

#### Scenario: Re-adding a product merges

- GIVEN a cart line for product P with cantidad 2
- WHEN POST /v1/carrito/items adds the same productId with cantidad 3
- THEN exactly one line for P with cantidad 5

#### Scenario: Invalid quantity or unknown product

- GIVEN cantidad 0, or a productoId that does not exist
- WHEN POST /v1/carrito/items
- THEN 400 (cantidad) or 404 (product) with Spanish messages

### Requirement: Read model resolves current catalog values

GET /v1/carrito MUST return each line with the product's current name and unit price, line subtotals, and a cart total with 2-decimal precision. Lines whose product no longer exists MUST be excluded from response and total, and purged on the next cart mutation.

#### Scenario: Totals follow current prices

- GIVEN two lines for products priced p1, p2 with quantities q1, q2
- WHEN GET /v1/carrito
- THEN the total equals p1*q1 + p2*q2 using the current catalog prices

#### Scenario: Deleted product line is dropped

- GIVEN a cart line whose product was deleted from the catalog
- WHEN GET /v1/carrito
- THEN the line is absent and the total excludes it

### Requirement: Cart is stock-agnostic

Cart operations MUST NOT read, validate or decrement stock; a line quantity MAY exceed current stock. The authoritative stock check happens only at order placement.

#### Scenario: Over-stock quantity accepted in cart

- GIVEN a product with stock 1
- WHEN POST /v1/carrito/items with cantidad 5
- THEN the line is accepted (a later Pedido for it fails with 409)

### Requirement: Order placement is decoupled from the cart

This change MUST NOT add a checkout endpoint: clients convert the cart by posting its lines to POST /v1/pedidos. Placing an order MUST NOT require a cart nor modify one; clients clear it via DELETE /v1/carrito.

#### Scenario: Placing an order leaves the cart intact

- GIVEN a cart with one line and sufficient stock
- WHEN the client posts that line to /v1/pedidos
- THEN the order is created with 201 and the cart still contains the line
