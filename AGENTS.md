# AGENTS.md — E-Commerce AI (Ferretería IA)

Technical context document for AI coding agents working in this repository.
Every statement below is derived from the actual source code in this repo.
Items that could **not** be verified from the code are explicitly listed as
caveats in [Known Gaps & Unverified Items](#known-gaps--unverified-items) —
do not assume them.

---

## 1. Project Overview

A Spring Boot 4 REST API for an AI-powered hardware store ("ferretería")
e-commerce backend. Users describe a problem in **colloquial Spanish**
(e.g. *"tengo una fuga en una tubería de PVC"*) and the API:

1. Sends the query to **Hugging Face Chat** (LLM chat completions) which returns a
   structured JSON suggestion (keywords, tools, spare parts).
2. Uses the extracted terms for keyword search over the product catalog.
3. Independently supports **vector similarity search** over product embeddings
   stored in **PostgreSQL + pgvector** (`<=>` operator), with embeddings
   generated through a dedicated `RestClient` against the **Hugging Face
   Inference API** (model `sentence-transformers/all-MiniLM-L6-v2`, 384
   dimensions).

The domain is Spanish-language: entity names, validation messages, exception
messages, and the LLM system prompt are all in Spanish.

---

## 2. Tech Stack (verified from `pom.xml`)

| Component | Version / Detail | Evidence |
|---|---|---|
| Java | 21 (`<java.version>21</java.version>`) | `pom.xml` |
| Spring Boot (parent) | **4.1.1** | `pom.xml` |
| Spring AI | **2.0.1** (BOM `spring-ai-bom`) | `pom.xml` |
| Web layer | `spring-boot-starter-webmvc` (modular starter, not `starter-web`) | `pom.xml` |
| HTTP client | `spring-boot-starter-restclient` (Spring `RestClient`) | `pom.xml`, `HuggingFaceChatConfig` |
| Persistence | `spring-boot-starter-data-jpa` + `org.postgresql:postgresql` (runtime) | `pom.xml` |
| Security | `spring-boot-starter-security` + OAuth2 authorization-server, client, resource-server starters | `pom.xml`, `SecurityConfig` |
| Validation | `spring-boot-starter-validation` (Jakarta Validation) | `pom.xml`, `ProductoRequestDTO` |
| OpenAPI/Swagger | `springdoc-openapi-starter-webmvc-ui` **3.1.0** | `pom.xml` |
| Spring AI — Embeddings | **no starter** — custom `HuggingFaceEmbeddingModel` (`RestClient`) → HF Inference API `feature-extraction`; `spring-ai-starter-model-openai` was **removed** | `pom.xml`, `HuggingFaceConfig`, `HuggingFaceEmbeddingModel`, `application.properties` |
| Spring AI — Vector store | `spring-ai-starter-vector-store-pgvector` (dependency present; direct SQL used in repo) | `pom.xml`, `ProductoRepository` |
| JSON | Jackson 3 (`tools.jackson.*` — `ObjectMapper`, `JacksonException`) | `HuggingFaceChatService` |
| Codegen | Lombok (`@Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor`) | `pom.xml`, `Producto`, `HuggingFaceChatProperties` |
| Build | Maven Wrapper 3.9.16 (`mvnw`) | `.mvn/wrapper/maven-wrapper.properties` |
| Migraciones | **Flyway 12** (`spring-boot-starter-flyway` + `flyway-database-postgresql`), `src/main/resources/db/migration/V1..V4`; `ddl-auto=validate` en todos los perfiles | `pom.xml`, `flyway_schema_history` |
| Tests | starters `spring-boot-starter-*-test` + Testcontainers (`pgvector/pgvector:pg16`) o rama Neon de pruebas si `NEON_TEST_DATABASE_URL` está definida (`@EnabledIf("…EntornoIntegracion#disponible")`); `@DataJpaTest` con conteo de sentencias Hibernate | `pom.xml`, `src/test` |

> **Note:** Spring Boot 4 / Spring Framework 7 use modular starters
> (`spring-boot-starter-webmvc`, `spring-boot-starter-restclient`) and ship
> Jackson 3 (`tools.jackson`). Do not "fix" these imports to `com.fasterxml` —
> that would break compilation.

---

## 3. Repository Layout & Architecture

```
src/main/java/org/alexis/ecommerceai/
├── ECommerceAiApplication.java        # @SpringBootApplication entry point
├── ai/
│   ├── AsistenteIAService.java        # Orchestrates LLM analysis → product search
│   └── HuggingFaceChatService.java    # Hugging Face Chat client + JSON parsing
├── config/
│   ├── SecurityConfig.java            # Filter chain, rules por endpoint, JWT encoder/decoder, PasswordEncoder
│   ├── CorsConfig.java                # CORS global desde app.cors.* (no hay @CrossOrigin en controladores)
│   ├── AdminSeeder.java               # Seed idempotente del admin inicial (app.seed.admin-password)
│   ├── JwtProperties.java             # @ConfigurationProperties("app.jwt") (secret + expiration + issuer)
│   ├── ApiKeyValidationConfig.java    # Fail-fast si falta la API key de Hugging Face
│   ├── ActuatorConfig.java            # /actuator/{health,info,metrics} permitAll
│   ├── CacheConfig.java               # Caché (Caffeine en prod)
│   ├── WebConfig.java                 # Configuración web adicional
│   ├── HuggingFaceChatConfig.java     # RestClient bean ("huggingFaceChatRestClient")
│   ├── HuggingFaceChatProperties.java # @ConfigurationProperties("huggingface.chat")
│   ├── HuggingFaceConfig.java         # RestClient bean ("huggingFaceRestClient") + EmbeddingModel bean
│   ├── HuggingFaceProperties.java     # @ConfigurationProperties("huggingface.api") (key/model/baseUrl)
│   └── HuggingFaceEmbeddingModel.java # EmbeddingModel impl → HF Inference API (feature-extraction, 384 dims)
├── controller/
│   ├── ProductoController.java        # /api/v1/productos (REST + AI endpoints, incl. reindexar)
│   ├── AuthController.java            # /api/auth (login contra BD + register público CLIENTE)
│   ├── CategoriaController.java       # /api/v1/categorias (CRUD con jerarquía padreId)
│   ├── PedidoController.java          # /api/v1/pedidos (crear/desde-carrito/listar propios/detalle/estado ADMIN)
│   ├── CarritoController.java         # /api/v1/carrito (carrito persistente del principal)
│   └── UsuarioController.java         # /api/v1/usuarios (me autenticado, listado ADMIN)
├── dto/
│   ├── ProductoRequestDTO.java        # Create/update payload (record + validation, incl. categoriaId)
│   ├── ProductoResponseDTO.java       # API response (record)
│   ├── LoginRequest/LoginResponse.java, RegisterRequestDTO, UsuarioResponseDTO
│   ├── CategoriaRequestDTO/CategoriaResponseDTO, PedidoRequestDTO/LineaPedidoDTO/PedidoResponseDTO
│   ├── EstadoPedidoRequestDTO.java    # Transición de estado (ADMIN)
│   ├── AgregarItemCarritoDTO/ActualizarItemCarritoDTO/CarritoResponseDTO/LineaCarritoResponseDTO
│   ├── BusquedaInteligenteResponse.java, DiagnoseRequestDTO, ReindexacionResponse.java
│   ├── SugerenciaFerreteriaDTO.java   # LLM JSON contract (keywords/tools/spare parts)
│   └── huggingface/                    # ChatCompletion{Request,Response}, ChatMessage
├── exception/
│   ├── ErrorResponse.java             # Unified error body (record)
│   ├── GlobalExceptionHandler.java    # @RestControllerAdvice (incl. backstop DataIntegrityViolation → 409)
│   └── (HuggingFaceException, HuggingFaceRateLimitException,
│        ProductoNotFoundException, StockUpdateConflictException,
│        RecursoNoEncontradoException, ConflictoException, CategoriaNotFoundException,
│        CategoriaEnUsoException, UsuarioDuplicadoException, CredencialesInvalidasException,
│        PedidoNotFoundException, StockInsuficienteException, ProductoConPedidosException,
│        CarritoItemNotFoundException, CarritoVacioException, TransicionEstadoInvalidaException)
├── model/
│   ├── Producto.java                  # JPA entity "productos" incl. vector(384) + @ManyToOne Categoria (nullable)
│   ├── Categoria.java                 # JPA entity "categorias" + self-FK padre_id (jerarquía)
│   ├── Usuario.java                   # JPA entity "usuarios" (roles ADMIN/CLIENTE, password BCrypt, email opcional)
│   ├── Pedido.java + ItemPedido.java  # "pedidos" + "items_pedido" (snapshot precioUnitario)
│   ├── EstadoPedido.java              # ciclo de vida PENDIENTE→CONFIRMADO→ENVIADO→ENTREGADO / CANCELADO
│   ├── Carrito.java + CarritoItem.java # "carritos" + "carrito_items" (agregado con cabecera)
├── repository/
│   ├── ProductoRepository.java        # JPA + native vector similarity query + pendientes de embedding
│   ├── CategoriaRepository.java, UsuarioRepository.java
│   ├── PedidoRepository.java (@EntityGraph), ItemPedidoRepository.java
│   ├── CarritoRepository.java (@EntityGraph), CarritoItemRepository.java
├── security/
│   └── JwtAuthenticationFilter.java   # Custom Bearer-JWT filter
└── service/
    ├── ProductoService.java           # CRUD, stock, keyword & vector search, reindexación
    ├── AuthService.java, CategoriaService.java, PedidoService.java, CarritoService.java
    ├── UsuarioService.java            # lectura de usuarios; única salida = UsuarioResponseDTO (sin password)
src/main/resources/
├── application.properties             # Configuración base (Neon, Hikari, Flyway, JWT, Hugging Face)
├── application-{dev,prod,test}.properties  # Perfiles (ninguno define spring.profiles.active)
└── db/migration/                      # Flyway: V1 baseline, V2 jerarquía, V3 carrito, V4 email
src/test/java/.../ECommerceAiApplicationTests.java
docs/rollback-conectividad-neon.md     # Runbook de rollback de conectividad (Bloque 5)
```

**Request flow (AI recommendation):**
`ProductoController` → `AsistenteIAService.buscarRecomendacion()` →
`HuggingFaceChatService.analizarConsulta()` (LLM) → flatten keywords/tools/parts →
`ProductoService.buscarPorPalabrasClave()` → `ProductoRepository.buscarPorPalabraClave()`
(LIKE across `nombre`, `descripcionTecnica`, `descripcionColoquial`, `sku`).

**Vector search flow:**
`ProductoController GET /buscar` → `ProductoService.buscarPorSimilitud()` →
`EmbeddingModel.embed(query)` (HuggingFaceEmbeddingModel) → native SQL
`WHERE p.embedding IS NOT NULL ORDER BY p.embedding <=> CAST(:embedding AS vector) LIMIT :limit`.

**Reindexing flow (pending embeddings):**
`ProductoController POST /reindexar` (ADMIN) →
`ProductoService.reindexarPendientes()` → `ProductoRepository.findPendientesDeEmbedding()`
(products with `embedding IS NULL`) → generates each vector via HF → saves →
`ProductoRepository.countByEmbeddingIsNull()` → `ReindexacionResponse(procesados, pendientes)`.

---

## 4. Data Model & pgvector

`Producto` (`@Table(name = "productos")`):

| Column | Type / Definition | Constraints |
|---|---|---|
| `id` | `Long`, `GenerationType.IDENTITY` | PK |
| `sku` | `String(50)` | `nullable=false, unique=true` |
| `nombre` | `String(100)` | `nullable=false` |
| `descripcion_tecnica` | `TEXT` | nullable |
| `descripcion_coloquial` | `TEXT` | nullable — colloquial terms for the AI search |
| `precio` | `BigDecimal`, `precision=10, scale=2` | `nullable=false` |
| `stock` | `Integer` | `nullable=false` |
| `embedding` | `columnDefinition = "vector(384)"`, stored as `String`, `@ColumnTransformer(write="?::vector")` | direct pgvector column mapping |
| `version` | `Long`, `@Version` | optimistic locking, default 0 |

pgvector facts verified from code:

- The `embedding` column requires the **pgvector extension** to exist in the
  database: `V1__baseline_esquema_inicial.sql` la crea con
  `CREATE EXTENSION IF NOT EXISTS vector;` (idempotente; si el rol no tiene
  privilegio `CREATE`, debe crearse antes de migrar).
- Dimension is **384**, matching the configured embedding model
  `sentence-transformers/all-MiniLM-L6-v2` (exposed as
  `HuggingFaceEmbeddingModel.DIMENSION` / `dimensions()`), and hardcoded in the
  column `vector(384)`. `@ColumnTransformer(write = "?::vector")` applies an
  explicit `?::vector` cast on INSERT/UPDATE: without it PostgreSQL rejects the
  `varchar` parameter (a Java `String`). If you change embedding model, both the
  column definition and `HuggingFaceEmbeddingModel.DIMENSION` must match (see
  caveats).
- Similarity query (`ProductoRepository.buscarPorSimilitudVectorial`):
  `WHERE p.embedding IS NOT NULL
   ORDER BY p.embedding <=> CAST(:embedding AS vector) LIMIT :limit`
  (cosine distance; only products that already have an embedding are returned).
  The embedding string passed in is `Arrays.toString(float[])` — Java array
  syntax that PostgreSQL accepts when cast to `vector`.
- Embeddings are generated in `ProductoService.create()` / `update()` (and in
  `reindexarPendientes()`) from `nombre + " " + descripcionColoquial` and
  persisted as a `String`.
- `ProductoRepository.findPendientesDeEmbedding()` (`embedding IS NULL`) and
  `countByEmbeddingIsNull()` back the reindexación-masiva (seed/reindex) flow.
- `ddl-auto=validate` en **todos** los perfiles (base/dev/test/prod): el esquema
  lo construye Flyway y Hibernate solo valida. Cualquier divergencia
  entidad↔esquema impide arrancar el contexto (falla en tests de integración
  incluidos). `show-sql=true` en dev.

### 4.1 Otras entidades del dominio

| Entidad | Tabla | Notas clave |
|---|---|---|
| `Categoria` | `categorias` | self-FK `padre_id` (V2) mapeada como `@ManyToOne(LAZY) padre`; la jerarquía debe ser acíclica (lo valida `CategoriaService`, la FK no puede) |
| `Usuario` | `usuarios` | `username` UNIQUE, `password` BCrypt (nunca se serializa), `email` opcional UNIQUE (V4), `rol` ADMIN/CLIENTE |
| `EstadoPedido` | (`pedidos.estado`) | enum `PENDIENTE→CONFIRMADO→ENVIADO→ENTREGADO`, más `CANCELADO` desde cualquier estado no terminal; `transicionesValidas()`/`puedeTransicionarA()` son la regla de dominio |
| `Pedido`/`ItemPedido` | `pedidos`/`items_pedido` | `items` con `cascade=ALL, orphanRemoval`; `ItemPedido.productoId` es columna simple (snapshot de precio, sin FK) |
| `Carrito`/`CarritoItem` | `carritos`/`carrito_items` (V3) | 1 carrito por usuario (`uk_carritos_usuario`), UNIQUE `(carrito_id, producto_id)`; `carrito_items.producto_id` con `ON DELETE CASCADE`; `items` con `cascade=ALL, orphanRemoval` |

**Migraciones (inmutables una vez aplicadas):**

| Versión | Contenido |
|---|---|
| V1 | extensión pgvector + `productos`, `categorias`, `usuarios`, `pedidos`, `items_pedido`, `items_carrito` |
| V2 | `categorias.padre_id` + `fk_categorias_padre` + `idx_categorias_padre`; semilla `Sin categoría`; backfill defensivo de `productos.categoria_id` (con `COUNT(*)` previo registrado por `RAISE NOTICE`) |
| V3 | `carritos` + `carrito_items`, migración de datos legacy desde `items_carrito`, `DROP TABLE items_carrito`, índices de FK |
| V4 | `usuarios.email` + `uk_usuarios_email` (nullable: los usuarios sin email no colisionan) |

`spring.flyway.baseline-on-migrate=true` + `baseline-version=1` significa que
sobre una base **no vacía** Flyway baselinaría en V1 y **omitiría** V1: por eso
las migraciones nuevas deben ser siempre aditivas (V2+) y nunca editar V1.

---

## 5. REST API Surface (`/api/v1/productos`)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/productos?page=&size=` | Public | Listado **paginado** (`Page`, 20 por defecto) |
| GET | `/api/v1/productos/{id}` | Public | Get one product |
| GET | `/api/v1/productos/buscar?q=...&limite=5` | Public | pgvector similarity search (top-N) |
| GET | `/api/v1/productos/asistente?q=...` | Public | AI recommendation (Hugging Face Chat + keyword search) |
| POST | `/api/v1/productos/diagnose` | **ADMIN** | Body `{"problema": "..."}` → AI recommendation |
| POST | `/api/v1/productos/reindexar` | **ADMIN** | Re-genera embeddings de productos pendientes → `ReindexacionResponse(procesados, pendientes)` |
| POST | `/api/v1/productos` | **ADMIN** | Create product (validated) |
| PUT | `/api/v1/productos/{id}` | **ADMIN** | Update product (validated) |
| PATCH | `/api/v1/productos/{id}/stock?stock=0` | **ADMIN** | Update stock only (query param, `@Min(0)`) |
| DELETE | `/api/v1/productos/{id}` | **ADMIN** | Delete (204 No Content) |

- CORS es **global** (`CorsConfig`, sin `@CrossOrigin` en controladores) y se
  configura con `app.cors.allowed-origins` (por defecto
  `http://localhost:3001,http://localhost:3000`), `app.cors.allowed-methods`,
  `app.cors.allowed-headers` y `app.cors.max-age`.
- `GET` catalog paths are `permitAll()`; **all** POST/PUT/PATCH/DELETE under
  `/api/v1/productos/**` and `/api/v1/categorias/**` require `hasRole("ADMIN")`;
  everything else requires authentication (see Security section).

### Auth & new domain endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/login` | Public | Login contra BD (`username`+`password`) → `LoginResponse(token, username, expiresIn)` — **no incluye `rol`**: el rol viaja dentro del JWT (claim `roles`). Credenciales inválidas → 401 con mensaje genérico |
| POST | `/api/auth/register` | Public | Registro público; fuerza rol `CLIENTE` aunque el payload pida otro → 201 `UsuarioResponseDTO(id, username, rol, email)` (nunca password). `email` es opcional pero, si viene, se valida con `@Email` y es único → 409 si ya existe. Username duplicado → 409 |
| GET | `/api/v1/categorias` `/api/v1/categorias/{id}` | Public | CRUD de categorías (lectura pública); la respuesta incluye `padreId` (null = raíz) |
| POST | `/api/v1/categorias` | **ADMIN** | Crear (nombre único → 409 si duplicado; `padreId` inexistente → 404; `padreId` = sí misma → 409) |
| PUT/DELETE | `/api/v1/categorias/{id}` | **ADMIN** | Actualizar (incluido mover de rama; crear un ciclo → 409) / borrar. Borrar categoría con ≥1 producto o con subcategorías → 409 (sin cascade) |
| POST | `/api/v1/pedidos` | Autenticado | Crear pedido desde líneas `{productoId, cantidad}` → 201 (estado `PENDIENTE`). Stock insuficiente en la petición → **400**; carrera por la última unidad (bloqueo optimista) → **409**, sin pedido parcial; producto inexistente → 404; cuerpo vacío o cantidad ≤ 0 → 400 |
| POST | `/api/v1/pedidos/desde-carrito` | Autenticado | Convierte el carrito persistente en pedido → 201 y **vacía el carrito solo si el pedido se creó** (misma transacción). Carrito inexistente o vacío → 400 |
| GET | `/api/v1/pedidos` | Autenticado | Pedidos del principal (newest first), con ítems resueltos por `@EntityGraph` (sin N+1) |
| GET | `/api/v1/pedidos/{id}` | Autenticado | Detalle; solo propios (ADMIN puede ver cualquiera); ajeno/inexistente → 404 |
| PATCH | `/api/v1/pedidos/{id}/estado` | **ADMIN** | Transición de estado (`{"estado":"CONFIRMADO"}`) validada por `EstadoPedido`; transición imposible → 409; estado ausente/desconocido → 400 |
| GET | `/api/v1/usuarios/me` | Autenticado | Perfil del principal → `UsuarioResponseDTO` (sin password) |
| GET | `/api/v1/usuarios` | **ADMIN** | Listado de usuarios → `UsuarioResponseDTO[]` (sin password) |
| GET | `/api/v1/carrito` | Autenticado | Carrito persistente del principal (con totales a precio actual) |
| POST | `/api/v1/carrito/items` | Autenticado | Agregar/merge producto (UNIQUE carrito+producto); cantidad `@Positive` |
| PATCH | `/api/v1/carrito/items/{id}` | Autenticado | Cambiar cantidad (`@Positive`); línea ajena → 404 |
| DELETE | `/api/v1/carrito/items/{id}` | Autenticado | Quitar línea (orphanRemoval); producto borrado se purga (FK CASCADE) |
| DELETE | `/api/v1/carrito` | Autenticado | Vaciar carrito |

---

## 6. Security & Authentication

Verified from `SecurityConfig.java` and `JwtAuthenticationFilter.java`:

- Stateless sessions (`SessionCreationPolicy.STATELESS`), CSRF disabled.
- Rule table: `server.servlet.context-path=/api` recorta el prefijo antes de que
  Spring Security evalúe, así que **los `requestMatchers` trabajan sobre el
  `servletPath`, SIN el prefijo `/api`** (no duplicar `/api` en los matchers):
  - `GET /v1/productos/**`, `GET /v1/categorias/**` → `permitAll()`
  - `POST/PUT/PATCH/DELETE /v1/productos/**` y `POST/PUT/DELETE /v1/categorias/**` → `hasRole("ADMIN")` (PATCH stock incluido)
  - `POST /auth/login`, `POST /auth/register` → `permitAll()`
  - `GET /v1/usuarios/me` → `authenticated()`; `/v1/usuarios/**` → `hasRole("ADMIN")` (el `me` se declara **antes** para que no lo capture la regla ADMIN)
  - `PATCH /v1/pedidos/**` → `hasRole("ADMIN")` (transición de estado)
  - `/swagger-ui/**`, `/api-docs/**`, `/swagger-ui.html` → `permitAll()` (openAPI
    docs: `springdoc.api-docs.path=/api-docs`, `springdoc.swagger-ui.path=/swagger-ui.html`)
  - `anyRequest()` → `authenticated()` (pedidos y carrito)
- **El rol sale siempre del JWT firmado** (claim `roles`), nunca de una cabecera
  ni de un parámetro: `X-Role: ADMIN`, `?rol=ADMIN` o un JWT con
  `roles:[ROLE_ADMIN]` firmado con otra clave **no** elevan privilegios
  (verificado en `SeguridadRolesIntegrationTest`).
- **JWTs**: `SecurityConfig` expone tanto un `JwtDecoder` como un `JwtEncoder`
  (ambos `NimbusJwtDecoder/Encoder` HS256 construidos desde la misma
  `SecretKeySpec` derivada de `app.jwt.secret`). El login usa el `JwtEncoder`.
- `JwtAuthenticationFilter` (custom, registered before
  `UsernamePasswordAuthenticationFilter`):
  1. Reads `Authorization: Bearer <token>`.
  2. Decodes with a `NimbusJwtDecoder` built from `app.jwt.secret` (HS256).
  3. Sets `username = jwt.getSubject()`; authorities come from the **`roles`**
     claim (`getClaimAsStringList("roles")`), each mapped to a
     `SimpleGrantedAuthority` — so to satisfy `hasRole("ADMIN")` the claim must
     contain the literal string `ROLE_ADMIN`.
  4. On any `JwtException` the context is cleared (anonymous), the request
     still continues through the chain.
- **Token issuance**: `POST /api/auth/login` valida contra la tabla `usuarios`
  (BCrypt) y emite HS256 JWT con `subject=username` y `roles=[ROLE_<ROL>]`
  desde la fila del usuario; `expiresIn` y firma usan `app.jwt.*`. El admin
  inicial lo siembra `AdminSeeder` (idempotente) con
  `app.seed.admin-password` (`${ADMIN_PASSWORD:admin123}`).
- OAuth2 starters (authorization-server, client, resource-server) are declared
  in `pom.xml` but **no OAuth2 configuration code exists** in `src/main` — the
  authorization-server starter is unused by any `@Configuration`.

---

## 7. DTO Conventions

- **Request DTOs are Java `records`** annotated with Jakarta Validation
  constraints; messages are **Spanish** (e.g. `ProductoRequestDTO`:
  `@NotBlank` sku/nombre, `@Size(max=50/100)`, `@DecimalMin("0.0", inclusive=false)`
  for price, `@Min(0)` for stock, both descriptions `@NotBlank` because the
  colloquial description feeds the AI index).
- **Response DTOs are plain `records`** with no annotations
  (`ProductoResponseDTO`, `BusquedaInteligenteResponse`).
- **Model/LLM-facing DTOs** use `@JsonIgnoreProperties(ignoreUnknown = true)`
  to tolerate extra JSON fields (`SugerenciaFerreteriaDTO`,
  `huggingface/ChatCompletionResponse`, `ChatMessage`). `SugerenciaFerreteriaDTO`
  also null-safe-accessors returning `List.of()`.
- **Mutating DTOs** (`DiagnoseRequestDTO`) may be simple classes with
  getters/setters instead of records.
- Controller validation: `@Valid @RequestBody` for POST/PUT bodies; `@Validated`
  on the controller class enables method-parameter constraints
  (`@RequestParam @NotNull @Min(0) Integer stock`).

---

## 8. Global Exception Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) → always
`ErrorResponse(timestamp, status, message, fieldErrors)`:

| Exception | HTTP Status |
|---|---|
| `MethodArgumentNotValidException` | 400, with per-field messages map |
| `ProductoNotFoundException`, `RecursoNoEncontradoException`, `CategoriaNotFoundException` | 404 |
| `StockInsuficienteException` (stock insuficiente **en la petición**) | **400** |
| `CarritoVacioException` (no hay carrito o está vacío) | **400** |
| `StockUpdateConflictException` (de `OptimisticLockingFailureException`: carrera por la última unidad) | 409 |
| `ConflictoException`, `CategoriaEnUsoException`, `TransicionEstadoInvalidaException`, `UsuarioDuplicadoException`, `DataIntegrityViolationException` | 409 |
| `CredencialesInvalidasException` | 401 |
| `HuggingFaceRateLimitException` (HTTP 429 from Hugging Face) | 429 |
| `HuggingFaceException` | exception's `status` (default 502 → `BAD_GATEWAY`; 401/403 → `UNAUTHORIZED`-style message, non-error codes coerced to 502) |
| any other `Exception` | 500, generic message (details hidden) |

**Contrato de stock (no confundir 400 con 409):** 400 = "la petición pide más
de lo que hay" (comprobación previa, determinista); 409 = "lo había cuando
pediste, otra transacción se adelantó" (bloqueo optimista de
`Producto.version`).

Rules when adding exceptions: extend `RuntimeException`, add a `@ExceptionHandler`
in `GlobalExceptionHandler` mapping to the proper HTTP status, keep messages in
Spanish.

---

## 9. Spring AI Integration

- **`EmbeddingModel`** (Spring AI) is injected into `ProductoService`. It is a
  **custom `HuggingFaceEmbeddingModel`** (implements Spring AI's
  `EmbeddingModel`) backed by a dedicated `RestClient` bean
  (`huggingFaceRestClient`) defined in `HuggingFaceConfig` with headers
  `Authorization: Bearer <key>` and `Content-Type: application/json`, plus a
  120s read timeout. The HF embeddings API is **not** OpenAI-compatible, so it
  posts to `{baseUrl}/{model}/pipeline/feature-extraction` with
  `{"inputs": [...], "options": {"wait_for_model": true}}` (model
  `sentence-transformers/all-MiniLM-L6-v2`, **384** dims). Used for: product
  embeddings on create/update/reindex, and query embedding for vector search.
  HTTP 429 → `HuggingFaceRateLimitException`; 401/403 → `HuggingFaceException`
  (auth); other HTTP errors → `HuggingFaceException`; connection failures →
  `HuggingFaceException`. `spring-ai-starter-model-openai` was **removed** from
  `pom.xml` (its auto-configuration exigía `OPENAI_API_KEY`).
- **Hugging Face Chat** is called via a dedicated `RestClient` bean
  (`huggingFaceChatRestClient`) built in `HuggingFaceChatConfig` with headers:
  `Authorization: Bearer <key>`, `Content-Type: application/json`.
- `HuggingFaceChatProperties` (`prefix = "huggingface.chat"`): `key`, `model`
  (default `Meta-Llama/Llama-3.2-3B-Instruct`), `baseUrl` (default
  `https://router.huggingface.co/v1`).
- `HuggingFaceChatService.analizarConsulta()`:
  - Validates key/model/query presence (Spanish error messages).
  - Sends `POST {baseUrl}/chat/completions` with `{model, messages:[system, user]}`
    using a fixed `SYSTEM_PROMPT` that instructs the model to answer **only**
    valid JSON `{palabrasClave[], herramientas[], repuestos[]}` — 3–8 keywords,
    no brands/product codes, empty lists for non-hardware queries.
  - Maps HTTP 429 → `HuggingFaceRateLimitException`; other HTTP errors →
    `HuggingFaceException`; connection/rest failures wrapped accordingly.
  - Parses the model's text: strips markdown fences and extracts the first
    `{...}` block (`extraerJson`), then deserializes with Jackson 3
    `ObjectMapper` into `SugerenciaFerreteriaDTO`. Parse failure →
    `HuggingFaceException`.

---

## 10. Configuration & Environment Variables

`src/main/resources/application.properties` (the only config source; **no
YAML**):

| Property | Current value in repo | Required env var |
|---|---|---|
| `spring.datasource.url` | `${DATABASE_URL}` — endpoint **pooled** (`-pooler`) con `?sslmode=require` | **`DATABASE_URL`** (fail-fast) |
| `spring.datasource.username` / `.password` | `${DATABASE_USER}` / `${DATABASE_PASSWORD}` (pgjdbc no admite userinfo en la URL) | **`DATABASE_USER`**, **`DATABASE_PASSWORD`** |
| `spring.datasource.driver-class-name` | `org.postgresql.Driver` | — |
| `spring.datasource.hikari.*` | pool serverless (10 / 2 / 300000 / 600000 / 20000 / 60000) + `initialization-fail-timeout=-1` | `DB_*` (opcionales) |
| `spring.flyway.url` | `${DATABASE_DIRECT_URL:${DATABASE_URL}}` — endpoint **directo**, sin `-pooler` | **`DATABASE_DIRECT_URL`** |
| `spring.flyway.user` / `.password` | `${DATABASE_USER}` / `${DATABASE_PASSWORD}` **explícitos**: al definir `spring.flyway.url`, Boot crea un DataSource solo-Flyway y no hereda las credenciales del pool (si faltan: SCRAM `08004`) | — |
| `spring.jpa.hibernate.ddl-auto` | `validate` (base/dev/test/prod) | — |
| `spring.jpa.show-sql` | `true` (dev) | — |
| `spring.flyway.baseline-on-migrate` | `true` (`baseline-version=1`) | — |
| `spring.jpa.properties.hibernate.dialect` | `org.hibernate.dialect.PostgreSQLDialect` | — |
| `huggingface.chat.key` | `${HUGGINGFACE_CHAT_API_KEY:${HUGGINGFACE_API_KEY}}` (cae a la key de embeddings) | opcional `HUGGINGFACE_CHAT_API_KEY` |
| `huggingface.chat.base-url` | `https://router.huggingface.co/v1` | — |
| `huggingface.chat.model` | `Meta-Llama/Llama-3.2-3B-Instruct` | — |
| `huggingface.api.key` | `${HUGGINGFACE_API_KEY}` | **`HUGGINGFACE_API_KEY`** (embeddings) |
| `huggingface.api.model` | `sentence-transformers/all-MiniLM-L6-v2` (default, 384 dims) | — |
| `huggingface.api.base-url` | `https://router.huggingface.co/hf-inference/models` | — |
| `app.jwt.secret` | `${JWT_SECRET}` — **sin valor por defecto**: fail-fast si falta o mide < 256 bits | **`JWT_SECRET`** |
| `app.jwt.expiration` | `${JWT_EXPIRATION_MS:86400000}` (24 h) | prefer `JWT_EXPIRATION_MS` |
| `app.jwt.issuer` | `${JWT_ISSUER:}` — si se define, valida el claim `iss` | opcional `JWT_ISSUER` |
| `app.seed.admin-password` | `${ADMIN_PASSWORD:admin123}` | prefer `ADMIN_PASSWORD` |

- **Never commit real keys.** `HUGGINGFACE_CHAT_API_KEY` (chat) and `HUGGINGFACE_API_KEY`
  (embeddings) are resolved from the environment; the repo's `.gitignore`
  already excludes `.env`, `.env.local` and `application-local.properties/yml`.
- JWT secret/expiration ya no están hardcodeados en `SecurityConfig`: viven en
  `app.jwt.*` (leídos por `JwtProperties`) y alimentan `JwtEncoder`/`JwtDecoder`
  (HS256) + el TTL de emisión en el login.
- PostgreSQL must have the **pgvector extension installed**
  (`CREATE EXTENSION IF NOT EXISTS vector;`) and a database matching
  `spring.datasource.url`.
- If the embedding model changes dimension, update the
  `columnDefinition = "vector(N)"` in `Producto.java`,
  `HuggingFaceEmbeddingModel.DIMENSION`, and
  `huggingface.api.model`; without this, `<=>` casts can fail at query time.
- To (re)index products that still have `embedding IS NULL`, call the ADMIN
  endpoint `POST /api/v1/productos/reindexar` (returns
  `ReindexacionResponse(procesados, pendientes)`).

### 10.1 Neon Postgres serverless (producción)

- Proyecto Neon en la región **`aws-sa-east-1` (São Paulo)**, PostgreSQL 18.6 con
  pgvector 0.8.6. El endpoint **pooled** (PgBouncer en modo transacción) sirve el
  tráfico de la aplicación; el **directo** sirve a Flyway y a `pg_dump`/`pg_restore`.
- `?sslmode=require` es **obligatorio** (Neon rechaza conexiones sin TLS); el panel
  de Neon añade además `&channel_binding=require`.
- **Scale-to-zero**: el compute se suspende tras unos minutos sin actividad y la
  primera consulta paga un *cold start* (cientos de ms). Con
  `initialization-fail-timeout=-1` el arranque no falla si el compute está
  suspendido y la conexión se obtiene de forma perezosa; `minimum-idle=2` +
  `keepalive-time=60000` reconectan solos tras la suspensión.
- La suite de integración puede correr contra una **rama Neon desechable** con
  `NEON_TEST_DATABASE_URL` / `NEON_TEST_DATABASE_DIRECT_URL` (+
  `NEON_TEST_DATABASE_USER`, `NEON_TEST_DATABASE_PASSWORD`); si no están
  definidas usa Testcontainers. Los tests escriben y borran datos.
- Datos migrados a la rama de producción (Bloque 5): **23 productos, 9 categorías
  y 23 embeddings de 384 dimensiones**, con paridad verificada frente al motor
  anterior (conteos, hashes de datos y top-5 de búsqueda vectorial idénticos).
- **Rollback de conectividad** (solo variables de entorno, sin tocar código):
  `docs/rollback-conectividad-neon.md` — RTO medido **10,8 s** (criterio < 3 min).

---

## 11. Development Rules (Conventions to Follow)

1. **Java 21**; prefer `records` for DTOs and constructor injection; keep
   Lombok usage minimal (entities/`@ConfigurationProperties`).
2. **Spanish** for user-facing strings: validation messages, exception messages,
   the LLM system prompt, and this domain's DTO field semantics.
3. Never change the Hugging Face `SYSTEM_PROMPT` JSON contract without updating
   `SugerenciaFerreteriaDTO` in the same change.
4. When adding endpoints: update `SecurityConfig` rules, keep `GET` read-only
   endpoints public only if intended (current policy), and document them in
   this file's endpoint table.
5. Any new exception type must be mapped in `GlobalExceptionHandler` with an
   explicit HTTP status.
6. Embeddings are derived from `nombre + descripcionColoquial`; if the formula
   changes, existing rows' embeddings become stale — run `POST
   /api/v1/productos/reindexar` (ADMIN) or plan a re-index.
7. **El esquema es de Flyway, no de Hibernate**: `ddl-auto=validate` en todos
   los perfiles. Todo cambio de esquema es una migración nueva
   (`V5__...`) — **nunca** editar una versión ya aplicada ni confiar en que
   Hibernate actualice algo. Con `baseline-on-migrate=true`, sobre una base no
   vacía Flyway baselinaría en V1: las migraciones nuevas deben ser aditivas.
   Para aplicar migraciones contra la base local:
   `./mvnw -Dflyway.url=... -Dflyway.user=... -Dflyway.password=... flyway:migrate`
   (o dejar que Spring Boot las aplique al arrancar).
8. Toda consulta de lectura que devuelva una colección (`Pedido.items`,
   `Carrito.items` + `producto`) debe traerla con `@EntityGraph`/fetch join y
   estar cubierta por un test de conteo de sentencias
   (`*RepositoryJpaTest`), para que un N+1 futuro rompa el build.
9. `RestClient` is the HTTP client of choice (Spring Boot 4 modular starter) —
   do not reintroduce `RestTemplate`.
10. Use the Maven wrapper (`./mvnw`) for builds; `mvnw.cmd` for Windows.
11. JWT secret/expiration viven en `app.jwt.*` (`JwtProperties`); cualquier
    cambio de seguridad debe mantener esa única fuente de config. El rol se lee
    del JWT, jamás de cabeceras o parámetros del cliente.

---

## 12. Known Gaps & Unverified Items

The following could **not** be verified from the repository code — do not treat
them as facts:

- **Bootstrap de pgvector:** `V1` crea la extensión
  (`CREATE EXTENSION IF NOT EXISTS vector;`). Requiere que el rol de la base
  tenga privilegio `CREATE` sobre la base; si no lo tiene, la extensión debe
  crearse antes de migrar.
- **Embedding model & dimensions:** the repo configures
  `sentence-transformers/all-MiniLM-L6-v2` (384 dims, matches `vector(384)` and
  `HuggingFaceEmbeddingModel.DIMENSION`). This is the live configuration;
  the HF key is required (`HUGGINGFACE_API_KEY`)
  for embeddings to be generated — a missing key throws `HuggingFaceException`.
- **Tests:** la suite completa (unitarios + integración con Testcontainers)
  incluye `@DataJpaTest` con conteo de sentencias sobre PostgreSQL real, un test
  de concurrencia HTTP (`CompraConcurrenteIntegrationTest`) y la verificación de
  escalada de privilegios. El gate es
  `@EnabledIf("…EntornoIntegracion#disponible")`: sin Docker **y** sin rama Neon
  configurada, los tests de integración se desactivan en lugar de fallar. Los
  números exactos de la última ejecución están en el reporte del Bloque 4, no aquí.
- **Datos:** la semilla `Sin categoría` y el backfill de `productos.categoria_id`
  los aplica V2. Tras el Bloque 5 la rama de producción de Neon tiene los 23
  productos (con embedding generado) y las 9 categorías del motor anterior con
  los IDs preservados; la rama de pruebas aloja 1 producto de caché y sus
  secuencias quedaron avanzadas (requiere reset antes de reutilizarla). No hay
  pedidos ni usuarios reales aparte del admin sembrado.
- **Swagger/OpenAPI reachability:** springdoc is present; `SecurityConfig`
  permite `/swagger-ui/**`, `/api-docs/**` y `/swagger-ui.html` via `permitAll()`.
- **Frontend:** the repo contains no frontend; `app.cors.allowed-origins` apunta
  a un cliente en `http://localhost:3001`/`3000` y los comentarios de
  `ProductoController` mencionan un `apiClient.ts` ("Antigravity"), pero ningún
  proyecto así está en este repositorio.
- **Fuera de alcance / pendiente:** cancelar un pedido no
  devuelve stock al inventario (`EstadoPedido.CANCELADO` solo cambia el estado);
  no hay endpoint de auto-gestión de perfil (solo lectura `me`/listado).

---

## Appendix: Files analyzed to produce this document

`pom.xml`, `src/main/resources/application.properties`,
`src/main/java/org/alexis/ecommerceai/ECommerceAiApplication.java`,
`config/{SecurityConfig,CorsConfig,JwtProperties,ApiKeyValidationConfig,ActuatorConfig,CacheConfig,WebConfig,HuggingFaceChatConfig,HuggingFaceChatProperties,HuggingFaceConfig,HuggingFaceProperties,HuggingFaceEmbeddingModel}.java`,
`src/main/resources/application-{dev,prod,test}.properties`, `docs/rollback-conectividad-neon.md`, `.env.example`,
`security/JwtAuthenticationFilter.java`, `controller/ProductoController.java`,
`ai/{AsistenteIAService,HuggingFaceChatService}.java`, `service/ProductoService.java`,
`repository/ProductoRepository.java`, `model/Producto.java`,
`dto/{ProductoRequestDTO,ProductoResponseDTO,BusquedaInteligenteResponse,DiagnoseRequestDTO,ReindexacionResponse,SugerenciaFerreteriaDTO}.java`,
`dto/huggingface/{ChatCompletionRequest,ChatCompletionResponse,ChatMessage}.java`,
`exception/{ErrorResponse,GlobalExceptionHandler,HuggingFaceException,HuggingFaceRateLimitException,ProductoNotFoundException,StockUpdateConflictException}.java`,
`src/test/java/org/alexis/ecommerceai/ECommerceAiApplicationTests.java`,
`.gitignore`, `.mvn/wrapper/maven-wrapper.properties`.
