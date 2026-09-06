# Exploration: dominio-ecommerce (Bloque 3 — real e-commerce domain)

Date: 2026-09-05 · Mode: explore (no code/tests written) · Strict TDD active
Scope under exploration: `Categoria`, `Usuario/Cliente` con roles (replaces hardcoded auth), `Pedido` + `ItemPedido`, `Carrito` (ephemeral vs persisted — open), all following existing repo conventions. Stack must not change (Java 21, Spring Boot 4.1.1, Spring AI 2.0.1).

---

## 1. Current State — auth/security (what Bloque 3 replaces)

### 1.1 `AuthController` hardcodes credentials — exact mechanism
`src/main/java/org/alexis/ecommerceai/controller/AuthController.java` (`POST /auth/login`, no `/v1` prefix):

- Class-level constants: `ADMIN_USERNAME = "admin"`, `ADMIN_PASSWORD = "admin123"`, `TOKEN_TTL_SECONDS = 3600`.
- `login(@Valid @RequestBody LoginRequest)` does a **plain constant comparison**:
  `if (!ADMIN_USERNAME.equals(request.username()) || !ADMIN_PASSWORD.equals(request.password())) throw new CredencialesInvalidasException();`
- On success it **mints the JWT inline** (no `JwtEncoder` bean shared): `NimbusJwtEncoder.withSecretKey(new SecretKeySpec(jwtSecret.getBytes(UTF_8), "HmacSHA256"))`, HS256 header, claims: `subject = username`, `issuedAt`, `expiresAt = now + 3600s`, **`roles = List.of("ROLE_ADMIN")` — hardcoded**.
- `jwtSecret` injected via `@Value("${app.jwt.secret:clave-secreta-de-256-bits-para-jwt}")` (inline default literal differs from the dev/profile values — latent bug source).
- Response: `LoginResponse(token, username, expiresIn)` record. TTL constant (3600s) **ignores `app.jwt.expiration`** (86400000 ms in `application-dev/prod.properties`).
- `LoginRequest` is a record: `@NotBlank` username/password, Spanish messages.

### 1.2 Validation & error path for login
- `CredencialesInvalidasException extends RuntimeException`, message `"Credenciales inválidas"`.
- `GlobalExceptionHandler.handleCredencialesInvalidas` → **401** `ErrorResponse(status, message)`.

### 1.3 Security rules — `SecurityConfig.java`
`SecurityConfig` (`@EnableWebSecurity`, stateless, CSRF off, filter chain):

| Rule | Effect |
|---|---|
| `GET /v1/productos/**` | `permitAll()` |
| `POST/PUT/DELETE /v1/productos/**` | `hasRole("ADMIN")` |
| `POST /auth/login` | `permitAll()` |
| `/swagger-ui/**`, `/api-docs/**`, `/swagger-ui.html` | `permitAll()` |
| `anyRequest()` | `authenticated()` |

- Controller mappings use `/v1/...`; the public prefix `/api` comes from `server.servlet.context-path=/api` (runtime + tests via `MockMvcContextPathConfig.CONTEXT_PATH`).
- `jwtDecoder()` bean: `NimbusJwtDecoder.withSecretKey(...)` HS256 from the same `app.jwt.secret`.
- `ActuatorConfig` has a separate `SecurityFilterChain` for `/actuator/**` (health/info/metrics public). `CorsConfig` is a global `WebMvcConfigurer` (origins from `app.cors.allowed-origins`).

### 1.4 JWT validation — `JwtAuthenticationFilter.java`
- `OncePerRequestFilter`, registered **before** `UsernamePasswordAuthenticationFilter`.
- Reads `Authorization: Bearer <token>`; decodes via the shared `JwtDecoder`; then:
  - `username = jwt.getSubject()`
  - `roles = jwt.getClaimAsStringList("roles")` (defaults to empty list)
  - authorities = each role string mapped **verbatim** to `new SimpleGrantedAuthority(...)` → to satisfy `hasRole("ADMIN")` the claim must literally contain **`"ROLE_ADMIN"`** (prefix already embedded in the claim).
  - authentication = `UsernamePasswordAuthenticationToken(username, null, authorities)`.
- On any `JwtException`: clears context, request continues (anonymous → 403 for protected routes).

### 1.5 Dead / unused auth code (cleanup candidate)
- `config/JwtConfig.java` + nested `JwtProperties` record: **nothing consumes them** (grep across `src` confirms). Duplicates the secret/expiration config that `AuthController` re-reads independently.
- `AuthController` has **zero tests** (no `AuthControllerTest`, no login coverage in integration tests). The only token fabrication in tests is the `jwtAdmin()` helper in `ProductoControllerIntegrationTest`, which signs an HS256 JWT with the **test-profile secret**, `subject "admin"`, `roles ["ROLE_ADMIN"]`.
- No `UserDetailsService`, no `AuthenticationManager`, no `PasswordEncoder` exist anywhere. Single implicit user, single implicit role. OAuth2 starters are declared in `pom.xml` but unused by any config.

---

## 2. Persistence patterns new entities must follow

### 2.1 Entity style (`model/Producto.java` as the template)
- `@Entity @Table(name = "productos")` (plural lowercase); Lombok `@Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor` (minimal Lombok); constructor injection elsewhere.
- ID: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;`
- Multi-word columns mapped with explicit `@Column(name = "snake_case")`; single-word rely on default naming. Text columns `columnDefinition = "TEXT"`; money `precision = 10, scale = 2` `BigDecimal`.
- Constraints: `nullable = false`, `unique = true` on natural keys (`sku`).
- **`@Version private Long version`** with `columnDefinition = "bigint not null default 0"` → optimistic locking (see stock conflict below).
- pgvector quirk: `embedding` stored as Java `String` over a `vector(384)` column with `@ColumnTransformer(write = "?::vector")` — **irrelevant for the new domain**, but any change to `Producto` (e.g. `categoria` FK) must not disturb the embedding write path.
- No relationships exist yet — `Producto` is standalone. `Categoria → Producto` will introduce the **first JPA association**.

### 2.2 Repository style (`ProductoRepository`)
- `interface X extends JpaRepository<Entidad, Long>` with `@Repository`.
- Derived queries (`countByEmbeddingIsNull`), JPQL `@Query` for multi-field `LIKE`, native `@Query` for pgvector `<=>`.
- Not found handling lives in services, not repositories.

### 2.3 Service / transaction style (`ProductoService`)
- `@Service`, constructor injection, final fields.
- Reads: `@Transactional(readOnly = true)`; writes: `@Transactional`. Manual entity→response mapping in private `toResponseDTO(...)`.
- **Optimistic-lock handling pattern**: `updateStock` catches `OptimisticLockingFailureException` → throws `StockUpdateConflictException` → 409. Stock decrement on order placement must reuse this exact pattern.
- Exceptions: Spanish messages (`"Producto no encontrado con id: " + id`).
- Embedding generation: `generarEmbedding(nombre, descripcionColoquial)` — formula is `nombre + " " + descripcionColoquial`. If `categoria` were ever added to the embedding source text, existing rows become stale and only a full re-index fixes it (current `reindexarPendientes()` only fills `embedding IS NULL`). Keep the formula untouched.

### 2.4 Schema management
- `spring.jpa.hibernate.ddl-auto=update` in dev; test profile forces `create-drop` on Testcontainers. **No Flyway/Liquibase.** New tables appear automatically; new NOT NULL FK columns on tables with existing rows are the classic `ddl-auto` hazard (see Risks).

---

## 3. API surface & conventions for new endpoints

### 3.1 Controller shape (`ProductoController` as template)
- `@RestController @RequestMapping("/v1/<recurso>")` + `@Validated` class-level (enables method-param constraints).
- Constructor injection of services; thin controllers (logic in services).
- Status conventions: `200 OK` reads, `201 Created` on POST, `204 No Content` on DELETE, `ResponseEntity.ok(...)`.
- Spanish resource naming in the domain (`productos`). New resources will likely be `/v1/categorias`, `/v1/usuarios|clientes`, `/v1/pedidos`, `/v1/carrito` — naming fixed in design/spec.
- Request DTOs = Java records with Jakarta validation, **Spanish messages** (`ProductoRequestDTO` is the template). Response DTOs = plain records (no annotations). Mutating DTO bodies via `@Valid @RequestBody`; query/param constraints via `@RequestParam @NotNull @Min(message="...")`.
- `@CrossOrigin` per controller is gone — CORS is global via `CorsConfig`.

### 3.2 Error handling contract
- Every domain exception: `extends RuntimeException` with a Spanish default message, plus a dedicated `@ExceptionHandler` in `GlobalExceptionHandler` mapping to an explicit HTTP status.
- Response body always `ErrorResponse(timestamp, status, message, fieldErrors?)`.
- Already mapped: 400 validation (3 variants incl. per-field map), 401 `CredencialesInvalidasException`, 404 `ProductoNotFoundException`, 409 `StockUpdateConflictException`, 429/502 HF. New domain needs: `CategoriaNotFoundException`-style (404), `Pedido`/`ItemPedido` (e.g. stock insuficiente → 409/400, pedido inválido → 400), user auth (reuse/rename `CredencialesInvalidasException`), conflict on duplicate username/sku/email → 409.
- **SecurityConfig rule table must be extended for every new endpoint** (repo dev rule #4).

### 3.3 Testing conventions for the API
- Unit: `@ExtendWith(MockitoExtension.class)` + standalone MockMvc (`MockMvcBuilders.standaloneSetup(controller).setValidator(new LocalValidatorFactoryBean()).setControllerAdvice(new GlobalExceptionHandler())` + default request with `contextPath("/api")`). Spanish method names describing behavior (`crear_conBodyInvalido_devuelve400ConErroresDeCampo`).
- Integration: extend `AbstractIntegrationTest` (`@SpringBootTest(MOCK)` + `@AutoConfigureMockMvc` + Testcontainers `pgvector/pgvector:pg16` + `@DynamicPropertySource` + `ddl-auto=create-drop` + `@ActiveProfiles("test")` + imports `EmbeddingModelTestConfig`, `MockMvcContextPathConfig`). External services mocked via `@MockitoBean` (`HuggingFaceChatService`). Test JWT minted in-helper with the test secret.
- Full suite: **72 `@Test` across 7 test classes** (+ `contextLoads`), run via `./mvnw test`; integration auto-skips without Docker.

---

## 4. Data model implications

### 4.1 Categoria ↔ Producto
- First association in the codebase. Shape to decide in design: `Categoria` entity (`id`, `nombre` unique, `descripcion?`) with `Producto.categoria` as a `@ManyToOne` — **unidirectional** is the least invasive (no `Set<Producto>` collection on `Categoria`; avoids lazy-loading and serialization traps).
- FK nullability is a real question: `nullable=false` + `ddl-auto=update` on a `productos` table that **already has rows** (23 vectorized products in the dev DB per history; empty in test) can fail to apply the constraint or need a default category. Options: nullable FK (product without category allowed) vs required FK + backfill script. Decide in design with evidence from the DB.
- Business coupling: deleting a `Categoria` referenced by products → restrict (409/conflict) vs orphan products. No cascade delete of products.
- Product search impact: keyword/vector/AI searches stay unchanged unless category filtering is added to catalog queries (scope decision: is `GET /v1/productos?categoriaId=` in scope?).

### 4.2 Usuario / Cliente con roles (replacing hardcoded auth)
- One `Usuario` entity with a role vs `Cliente` as role vs separate tables: naming/semantics to fix in design. Evidence from scope wording: "Usuario/Cliente con roles", existing role authority is `ROLE_ADMIN`, and the JWT subject is a username.
- **Password storage**: no `PasswordEncoder` bean exists. `spring-security-crypto` ships with `spring-boot-starter-security`, so `BCryptPasswordEncoder` is available without new deps. Plaintext `admin123` comparison disappears.
- **Roles → claim contract**: `JwtAuthenticationFilter` maps each string in the `roles` claim verbatim to an authority. Two compatible designs: (a) store role enum `ADMIN/CLIENTE` and emit claim entries `"ROLE_ADMIN"`/`"ROLE_CLIENTE"` (keeps `hasRole("ADMIN")` and the integration-test helper working), or (b) store `ROLE_ADMIN` prefixed and/or change the filter to add the prefix. Any change to the claim format **breaks existing `hasRole("ADMIN")` checks and `ProductoControllerIntegrationTest.jwtAdmin()`** — the low-risk path is (a) emit already-prefixed claim values.
- **Login rewrite**: `AuthController` (or a new `/v1/auth`) → repository lookup by username + `PasswordEncoder.matches` → mint JWT with roles read from the DB row. TTL should honor `app.jwt.expiration`; decide whether to finally use or delete dead `JwtConfig`.
- **Registration vs seeded users**: no registration endpoint exists today. Options: (1) admin-only seeding via `CommandLineRunner`/`ApplicationRunner` (keeps `admin/admin123`-era flows working, new clients registered via an ADMIN endpoint), (2) public self-registration `POST` for `CLIENTE`. Public registration raises: who can become `ADMIN` (never via public endpoint), email/username uniqueness conflicts (409), validation DTO. **Backwards compatibility**: external callers (frontend on :3001 per CORS) currently POST `/api/auth/login` with `admin/admin123` — replacing creds must keep an ADMIN seed user or document the breaking change.
- Domain strings stay Spanish (`Usuario`, `Categoria`, `Pedido`, `ItemPedido` naming; validation/exception messages Spanish; DTOs as records).

### 4.3 Pedido + ItemPedido
- Aggregate root `Pedido`: `usuario` (ManyToOne), `fecha`/`creadoEn`, `estado` (enum: e.g. `PENDIENTE, PAGADO, ENVIADO, ENTREGADO, CANCELADO`), `total` (`BigDecimal` precision 10 scale 2, computed), maybe `direccionEntrega`. `ItemPedido`: `pedido` (ManyToOne owning the association), `producto` (ManyToOne), `cantidad`, **`precioUnitario` snapshot at purchase time** (never read live from `Producto` after creation — price drift).
- `Pedido` ↔ `ItemPedido`: `OneToMany` with `cascade = ALL, orphanRemoval = true` on the owning side; total recomputed in the service; creation wrapped in a single `@Transactional`.
- **Stock semantics**: reserving/decrementing stock at order time must catch `OptimisticLockingFailureException` → existing 409 pattern (or a domain-specific `StockInsuficienteException` → 409/400). Decide in design: decrement on order creation, compensation on CANCELADO.
- Repository queries to plan: pedidos por usuario (pageable?), items de un pedido (eager fetch join or service mapping inside the transaction).

### 4.4 Carrito — OPEN (not resolved here)
Evidence relevant to the design decision:
- **No cart code exists.** No `Cart`/`ItemCarrito` entity, service, controller, or DTO.
- **Stateless API**: `SessionCreationPolicy.STATELESS` → no HTTP session to hang an ephemeral cart on.
- `spring-boot-starter-cache` + `caffeine` are declared in `pom.xml` but **zero usage** (`@EnableCaching`/`@Cacheable` absent) → an ephemeral in-memory cart would be the first cache consumer (new config + eviction policy + per-user keys, or a client-held cart token).
- Persisted cart fits existing JPA/Testcontainers conventions but adds entities + repositories + services + endpoints + tests to a change already carrying Categoria + Usuario/auth rework + Pedido, under a **400-line review budget in a single PR**.

Tradeoffs to weigh in design (evidence recorded, not decided):

| Option | Pros | Cons |
|---|---|---|
| Ephemeral (in-memory cache / client-held) | No schema; no orphan cart rows; small diff | Stateless app needs cart-id token or cache keyed by user; lost on restart; expiry/eviction logic; first cache consumer; stock not validated against DB until checkout |
| Persisted (per-user DB rows) | Durable; consistent with Pedido checkout (rows→order in one tx); testable with existing Testcontainers; per-user queries trivial | More entities/DDL; anonymous carts need a temp/guest key or login wall; orphan cleanup; bigger diff vs review budget |

Open questions for the design phase: anonymous vs authenticated-only carts; merge guest cart on login; is `Carrito` in Bloque 3 scope at all or deferred; checkout = cart → Pedido conversion (stock validation + total recompute).

---

## 5. What tests exist & how new domain code will be tested

| Layer | Pattern today | Where new code plugs in |
|---|---|---|
| Unit — controller | Mockito + standalone MockMvc + `LocalValidatorFactoryBean` + `GlobalExceptionHandler` + context-path `/api` (Spanish test names) | `CategoriaControllerTest`, `PedidoControllerTest`, `AuthControllerTest` (first one — auth has zero tests today) |
| Unit — service | Pure Mockito (`@Mock` repo/model) + AssertJ | `UsuarioServiceTest`, `PedidoServiceTest` (stock-conflict, totals), `CategoriaServiceTest` |
| Unit — exception mapping | `GlobalExceptionHandlerTest` with stub controllers asserting HTTP codes | extend for each new exception |
| Integration | `AbstractIntegrationTest` (Testcontainers pgvector, `create-drop`, profile `test`, mocked HF) | persistence + auth round-trips: login against seeded DB user → token → protected endpoint; pedido → stock decrement; categoria FK |
| Auth/security | Only indirectly today (403 w/o token in `ProductoControllerIntegrationTest`; `jwtAdmin()` helper signs with test secret) | replace/keep `jwtAdmin()`; add real-login integration coverage; assert `CLIENTE` vs `ADMIN` route behavior |

- Runner: `./mvnw test` (Strict TDD). Coverage tooling: none configured.
- Test DB bootstrap: `pgvector-init.sql` (`CREATE EXTENSION IF NOT EXISTS vector;`), image `pgvector/pgvector:pg16`.

---

## 6. Risks / unknowns for proposal & design

1. **JWT roles claim format**: filter maps claim strings verbatim to authorities. Modeling roles as `ADMIN/CLIENTE` without emitting `ROLE_`-prefixed claims breaks `hasRole("ADMIN")` everywhere plus the integration helper. Design must keep claim values `ROLE_*`-prefixed (or change filter + all tests atomically).
2. **`ddl-auto=update` + new FK on `productos`**: `Categoria` FK with `nullable=false` over existing product rows can fail/block; nullability + backfill/default-category strategy must be decided with real-DB evidence.
3. **Hardcoded-credential removal is breaking**: no seeded `admin` user exists; external clients using `admin/admin123` will break unless seeding preserves an ADMIN user. Registration vs seeding must be decided (public client self-registration has moderation implications).
4. **Review budget 400 lines, single PR**: Categoria + Usuario/auth rework + Pedido/ItemPedido (+ Carrito?) + tests is a large diff. Carrito scope and per-feature test weight must be sized in the proposal; oversized scope risks violating budget/chained delivery.
5. **Password hashing**: no `PasswordEncoder` bean exists; must be introduced and wired into login + any seeding without adding dependencies.
6. **TTL/property inconsistency**: `AuthController` hardcodes 3600s and its own secret default; `app.jwt.expiration` (86400000) is unused; dead `JwtConfig`. Cleanup decision belongs to the auth rework.
7. **Stock concurrency semantics** for Pedido creation: optimistic-lock 409 pattern exists only for `updateStock`; multi-item decrements need a consistent strategy (single transaction, item ordering to avoid deadlocks, compensation on CANCELADO).
8. **Embedding formula drift**: do not include `categoria` in `nombre + descripcionColoquial` without planning a full re-index.
9. **Test maintenance**: `jwtAdmin()` integration helper and any standalone security tests depend on the test-profile secret (`application-test.properties`); changes to claim content/format require updating them atomically.
10. **No auth tests exist today** — the auth rework ships without a safety net unless new unit/integration auth tests are written as part of the same change (Strict TDD).

---

## 7. Affected areas (known so far)

- `controller/AuthController.java` — replaced (DB-backed login).
- `config/SecurityConfig.java` — rule table for new endpoints; possibly `JwtDecoder`/encoder sharing.
- `config/JwtConfig.java` — use or delete (dead code).
- `security/JwtAuthenticationFilter.java` — only if claim format changes.
- `exception/GlobalExceptionHandler.java` + new exceptions (`CredencialesInvalidasException` reused or extended; Categoria/Pedido/Usuario not-found + conflicts) — Spanish messages.
- `model/Producto.java` — `Categoria` FK (careful with embedding column and `ddl-auto`).
- New: `model/Categoria.java`, `model/Usuario.java`, `model/Pedido.java`, `model/ItemPedido.java`, `model/Rol.java` (enum) + repositories + services + DTOs + controllers (+ optional Carrito).
- `dto/` records for each new resource; `LoginRequest/LoginResponse` evolve (roles/expiry real).
- `application*.properties` — only if seed/JWT props change (no stack version changes).
- Tests: new unit classes + extended `AbstractIntegrationTest` subclasses; `.env.example`/`DEPLOY_GUIDE.md` only if login contract changes (doc).

---

## 8. Recommendation for the proposal phase

Explore first, resolve in design. Recommended posture for the proposal:
- **Auth**: DB-backed `Usuario` + `BCryptPasswordEncoder`, seed an ADMIN user (keeps existing tooling/tests working), keep the `roles` claim emitting `ROLE_*` values, honor `app.jwt.expiration`, delete `JwtConfig` dead code (or wire it).
- **Categoria**: standalone entity + unidirectional `@ManyToOne` on `Producto`; nullable FK unless DB evidence allows a safe backfill.
- **Pedido**: aggregate with snapshot prices, `@OneToMany(cascade=ALL, orphanRemoval=true)` items, stock decrement with the existing optimistic-lock→409 pattern, Spanish state enum.
- **Carrito**: **do not decide here** — size it against the 400-line budget in the proposal; persisted-per-user fits conventions, ephemeral fits budget but adds first cache infrastructure. Strong chance the proposal defers Carrito or trims scope.
- **Ready for proposal**: Yes, with the open decisions above explicitly flagged (roles claim shape, Categoria FK nullability, seed-vs-register, Carrito in/out).
