# Proposal: Bloque 3 — Real E-Commerce Domain

## Intent

Today: catalog + AI search over hardcoded `admin/admin123`. Bloque 3 adds the real domain — categories, users with roles, orders/items, cart — per repo conventions. Blocks 1–2 stay untouched.

## Scope

**In**
- **Categoria**: CRUD; nullable FK on `productos`; service rule "new product requires categoria"; no backfill/defaults.
- **Usuario/auth**: entity + role; ADMIN seed; public CLIENTE registration; DB login via BCrypt; `roles` claim stays `ROLE_`-prefixed; honor `app.jwt.expiration`; wire/delete dead `JwtConfig`.
- **Pedido + ItemPedido**: aggregate; price snapshots; stock decrement via optimistic-lock → 409; Spanish state enum; per-user.
- **Carrito**: in scope; ephemeral vs persisted decided in design.
- Tests per capability (unit + integration); Strict TDD.

**Out**: Flyway + categoria backfill (Bloque 4); category filtering on search; AI/embedding changes; stack bumps; payments.

## Capabilities

**New** (full spec each):
- `product-categories` — CRUD, Producto FK, create-requires-category.
- `user-auth` — seeded ADMIN, CLIENTE registration, DB login, JWT claims.
- `order-management` — Pedido/ItemPedido, snapshots, stock, states.
- `shopping-cart` — authenticated-user cart (model in design).

**Modified**: None (`openspec/specs/` empty).

## Approach

Per explore §8: unidirectional `@ManyToOne` on `Producto` (embedding untouched); ADMIN seed preserves `jwtAdmin()`/`hasRole("ADMIN")`; Pedido aggregate + 409 stock pattern; endpoints in `SecurityConfig`; Carrito sized to budget in design.

## Affected Areas

| Area | Impact |
|---|---|
| `model/Producto.java` | Modified — nullable categoria FK |
| `controller/AuthController.java` | Modified — DB login, register |
| `config/SecurityConfig.java` | Modified — endpoint rules |
| `config/JwtConfig.java` | Modified — wire or delete |
| `exception/GlobalExceptionHandler.java` | Modified — new exceptions |
| New files | Added — entities, `Rol`, repos, services, DTOs, controllers, seed |
| Tests | Added — unit + integration |

## Risks

| Risk | Likelihood |
|---|---|
| Over 400-line single-PR budget | Med |
| Claim format breaks `hasRole`/`jwtAdmin()` | Low — keep `ROLE_` claims |
| Login change breaks callers | Med — ADMIN seed keeps `admin` |
| Stock concurrency | Med — single tx, 409 |
| Auth ships untested | High — tests same change |

## Rollback Plan

Revert the PR (returns to hardcoded auth). New `ddl-auto=update` tables are additive; drop manually if wanted.

## Dependencies

BCrypt ships with security starter — no new deps. PostgreSQL + pgvector; Testcontainers (Docker).

## Open Questions (defer to spec/design)

- Cart: anonymous vs authenticated-only; guest merge on login.
- Categoria delete/update semantics for FK-holding products.
- Register path/contract; password rules.

## Success Criteria

- [ ] DB login works; bad credentials → 401; seeded `admin` valid; CLIENTE cannot mutate `/v1/productos/**`.
- [ ] Registration creates CLIENTE only; duplicates → 409.
- [ ] `roles` claim unchanged; `jwtAdmin()`/`hasRole("ADMIN")` keep working.
- [ ] Categoria CRUD works; product without categoria → 400; FK nullable.
- [ ] Order snapshots prices, decrements stock atomically; no partial order on insufficient stock.
- [ ] Cart flows work per confirmed model.
- [ ] `./mvnw test` green; embedding/search untouched.
