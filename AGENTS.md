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

1. Sends the query to **OpenRouter** (LLM chat completions) which returns a
   structured JSON suggestion (keywords, tools, spare parts).
2. Uses the extracted terms for keyword search over the product catalog.
3. Independently supports **vector similarity search** over product embeddings
   stored in **PostgreSQL + pgvector** (`<=>` operator), with embeddings
   generated through Spring AI's `EmbeddingModel` (OpenAI-compatible client
   pointed at OpenRouter's `/embeddings` endpoint).

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
| HTTP client | `spring-boot-starter-restclient` (Spring `RestClient`) | `pom.xml`, `OpenRouterConfig` |
| Persistence | `spring-boot-starter-data-jpa` + `org.postgresql:postgresql` (runtime) | `pom.xml` |
| Security | `spring-boot-starter-security` + OAuth2 authorization-server, client, resource-server starters | `pom.xml`, `SecurityConfig` |
| Validation | `spring-boot-starter-validation` (Jakarta Validation) | `pom.xml`, `ProductoRequestDTO` |
| OpenAPI/Swagger | `springdoc-openapi-starter-webmvc-ui` **3.1.0** | `pom.xml` |
| Spring AI — Embeddings | `spring-ai-starter-model-openai` (OpenAI-compatible client → OpenRouter `/embeddings`) | `pom.xml`, `ProductoService`, `application.properties` |
| Spring AI — Vector store | `spring-ai-starter-vector-store-pgvector` (dependency present; direct SQL used in repo) | `pom.xml`, `ProductoRepository` |
| Spring AI — ETL | `spring-ai-tika-document-reader`, `spring-ai-vector-store-advisor` (declared, no usage found in code) | `pom.xml` |
| JSON | Jackson 3 (`tools.jackson.*` — `ObjectMapper`, `JacksonException`) | `OpenRouterService` |
| Codegen | Lombok (`@Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor`) | `pom.xml`, `Producto`, `OpenRouterProperties` |
| Build | Maven Wrapper 3.9.16 (`mvnw`) | `.mvn/wrapper/maven-wrapper.properties` |
| Tests | `spring-boot-starter-*-test` starters + Testcontainers; 69 tests (unit + integration) | `pom.xml`, `src/test` |

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
│   └── OpenRouterService.java         # OpenRouter /chat/completions client + JSON parsing
├── config/
│   ├── SecurityConfig.java            # Filter chain, JWT decoder bean
│   ├── OpenRouterConfig.java          # RestClient bean ("openRouterRestClient")
│   └── OpenRouterProperties.java      # @ConfigurationProperties("openrouter.api")
├── controller/
│   └── ProductoController.java        # /api/v1/productos (REST + AI endpoints)
├── dto/
│   ├── ProductoRequestDTO.java        # Create/update payload (record + validation)
│   ├── ProductoResponseDTO.java       # API response (record)
│   ├── BusquedaInteligenteResponse.java
│   ├── DiagnoseRequestDTO.java        # POST /diagnose body { "problema": "..." }
│   ├── SugerenciaFerreteriaDTO.java   # LLM JSON contract (keywords/tools/spare parts)
│   └── openrouter/                    # ChatCompletion{Request,Response}, ChatMessage
├── exception/
│   ├── ErrorResponse.java             # Unified error body (record)
│   ├── GlobalExceptionHandler.java    # @RestControllerAdvice
│   └── (OpenRouterException, OpenRouterRateLimitException,
│        ProductoNotFoundException, StockUpdateConflictException)
├── model/
│   └── Producto.java                  # JPA entity "productos" incl. vector(1536) column
├── repository/
│   └── ProductoRepository.java        # JPA + native vector similarity query
├── security/
│   └── JwtAuthenticationFilter.java   # Custom Bearer-JWT filter
└── service/
    └── ProductoService.java           # CRUD, stock, keyword & vector search
src/main/resources/
└── application.properties             # The only config file (no YAML)
src/test/java/.../ECommerceAiApplicationTests.java
```

**Request flow (AI recommendation):**
`ProductoController` → `AsistenteIAService.buscarRecomendacion()` →
`OpenRouterService.analizarConsulta()` (LLM) → flatten keywords/tools/parts →
`ProductoService.buscarPorPalabrasClave()` → `ProductoRepository.buscarPorPalabraClave()`
(LIKE across `nombre`, `descripcionTecnica`, `descripcionColoquial`, `sku`).

**Vector search flow:**
`ProductoController GET /buscar` → `ProductoService.buscarPorSimilitud()` →
`EmbeddingModel.embed(query)` → native SQL
`ORDER BY p.embedding <=> CAST(:embedding AS vector) LIMIT :limit`.

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
| `embedding` | `columnDefinition = "vector(1536)"`, stored as `String` | direct pgvector column mapping |
| `version` | `Long`, `@Version` | optimistic locking, default 0 |

pgvector facts verified from code:

- The `embedding` column requires the **pgvector extension** to exist in the
  database (`CREATE EXTENSION IF NOT EXISTS vector;`). **No migration/SQL file
  creating the extension exists in the repo** — it must be created manually.
- Dimension is hardcoded to **1536** in the column definition, matching the
  configured embedding model `openai/text-embedding-3-small` (served by
  OpenRouter's `/embeddings` endpoint). If you change embedding model, the
  dimension must match `vector(1536)` (see caveats).
- Similarity query (`ProductoRepository.buscarPorSimilitudVectorial`):
  `ORDER BY p.embedding <=> CAST(:embedding AS vector) LIMIT :limit`
  (cosine distance). The embedding string passed in is
  `Arrays.toString(float[])` — Java array syntax that PostgreSQL accepts when
  cast to `vector`.
- Embeddings are generated in `ProductoService.create()` / `update()` from
  `nombre + " " + descripcionColoquial` and persisted as a `String`.
- `ddl-auto=update` (Hibernate) creates/updates tables; `show-sql=true`.

---

## 5. REST API Surface (`/api/v1/productos`)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/productos` | Public | List all products |
| GET | `/api/v1/productos/{id}` | Public | Get one product |
| GET | `/api/v1/productos/buscar?q=...&limite=5` | Public | pgvector similarity search (top-N) |
| GET | `/api/v1/productos/asistente?q=...` | Public | AI recommendation (OpenRouter + keyword search) |
| POST | `/api/v1/productos/diagnose` | **ADMIN** | Body `{"problema": "..."}` → AI recommendation |
| POST | `/api/v1/productos` | **ADMIN** | Create product (validated) |
| PUT | `/api/v1/productos/{id}` | **ADMIN** | Update product (validated) |
| PATCH | `/api/v1/productos/{id}/stock?stock=0` | **ADMIN** | Update stock only (query param, `@Min(0)`) |
| DELETE | `/api/v1/productos/{id}` | **ADMIN** | Delete (204 No Content) |

- `@CrossOrigin(origins = {"http://localhost:3001"})` at controller level — the
  only allowed origin.
- `GET` paths are `permitAll()`; **all** POST/PUT/PATCH/DELETE under
  `/api/v1/productos/**` require `hasRole("ADMIN")`; everything else requires
  authentication (see Security section).

---

## 6. Security & Authentication

Verified from `SecurityConfig.java` and `JwtAuthenticationFilter.java`:

- Stateless sessions (`SessionCreationPolicy.STATELESS`), CSRF disabled.
- Rule table:
  - `GET /api/v1/productos/**` → `permitAll()`
  - `POST/PUT/DELETE /api/v1/productos/**` → `hasRole("ADMIN")`
  - `anyRequest()` → `authenticated()`
- `JwtAuthenticationFilter` (custom, registered before
  `UsernamePasswordAuthenticationFilter`):
  1. Reads `Authorization: Bearer <token>`.
  2. Decodes with a `NimbusJwtDecoder` built from a **hardcoded HS256 secret**
     in `SecurityConfig.jwtDecoder()` (code comment marks it as an example —
     production must inject it safely).
  3. Sets `username = jwt.getSubject()`; authorities come from the **`roles`**
     claim (`getClaimAsStringList("roles")`), each mapped to a
     `SimpleGrantedAuthority` — so to satisfy `hasRole("ADMIN")` the claim must
     contain the literal string `ROLE_ADMIN`.
  4. On any `JwtException` the context is cleared (anonymous), the request
     still continues through the chain.
- OAuth2 starters (authorization-server, client, resource-server) are declared
  in `pom.xml` but **no OAuth2 configuration code exists** in `src/main` — the
  authorization-server starter is unused by any `@Configuration`.
- **There is no token-issuing (login) endpoint in this repository.** JWT
  issuance is out of scope of the code; for local testing, generate a token
  externally (HS256, subject, `roles: ["ROLE_ADMIN"]`).

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
  `openrouter/ChatCompletionResponse`, `ChatMessage`). `SugerenciaFerreteriaDTO`
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
| `ProductoNotFoundException` | 404 |
| `StockUpdateConflictException` (from `OptimisticLockingFailureException` on stock update) | 409 |
| `OpenRouterRateLimitException` (HTTP 429 from OpenRouter) | 429 |
| `OpenRouterException` | exception's `status` (default 502 → `BAD_GATEWAY`; non-error codes are coerced to 502) |
| any other `Exception` | 500, generic message (details hidden) |

Rules when adding exceptions: extend `RuntimeException`, add a `@ExceptionHandler`
in `GlobalExceptionHandler` mapping to the proper HTTP status, keep messages in
Spanish.

---

## 9. Spring AI Integration

- **`EmbeddingModel`** (Spring AI, OpenAI-compatible client via
  `spring-ai-starter-model-openai`) is injected into `ProductoService`.
  Configured to hit **OpenRouter's `/embeddings`** endpoint:
  `spring.ai.openai.api-key=${OPENROUTER_API_KEY}`,
  `spring.ai.openai.base-url=https://openrouter.ai/api`,
  `spring.ai.openai.embedding.options.model=openai/text-embedding-3-small`.
  Used for: product embeddings on create/update, and query embedding for
  vector search.
- **OpenRouter** is called via a dedicated `RestClient` bean
  (`openRouterRestClient`) built in `OpenRouterConfig` with headers:
  `Authorization: Bearer <key>`, `HTTP-Referer`, `X-Title`, `Content-Type:
  application/json`.
- `OpenRouterProperties` (`prefix = "openrouter.api"`): `key`, `model`
  (default `openrouter/free`), `baseUrl` (default
  `https://openrouter.ai/api/v1`), `httpReferer` (default
  `http://localhost:8080`), `appTitle` (default `Ferreteria IA App`).
- `OpenRouterService.analizarConsulta()`:
  - Validates key/model/query presence (Spanish error messages).
  - Sends `POST {baseUrl}/chat/completions` with `{model, messages:[system, user]}`
    using a fixed `SYSTEM_PROMPT` that instructs the model to answer **only**
    valid JSON `{palabrasClave[], herramientas[], repuestos[]}` — 3–8 keywords,
    no brands/product codes, empty lists for non-hardware queries.
  - Maps HTTP 429 → `OpenRouterRateLimitException`; other HTTP errors →
    `OpenRouterException`; connection/rest failures wrapped accordingly.
  - Parses the model's text: strips markdown fences and extracts the first
    `{...}` block (`extraerJson`), then deserializes with Jackson 3
    `ObjectMapper` into `SugerenciaFerreteriaDTO`. Parse failure →
    `OpenRouterException`.

---

## 10. Configuration & Environment Variables

`src/main/resources/application.properties` (the only config source; **no
YAML**):

| Property | Current value in repo | Required env var |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/ecommerce_db` | — |
| `spring.datasource.username` | local default (dev) | prefer env override |
| `spring.datasource.password` | local default (dev) | prefer env override |
| `spring.datasource.driver-class-name` | `org.postgresql.Driver` | — |
| `spring.jpa.hibernate.ddl-auto` | `update` | — |
| `spring.jpa.show-sql` | `true` | — |
| `spring.jpa.properties.hibernate.dialect` | `org.hibernate.dialect.PostgreSQLDialect` | — |
| `openrouter.api.key` | `${OPENROUTER_API_KEY}` | **`OPENROUTER_API_KEY`** |
| `openrouter.api.base-url` | `https://openrouter.ai/api/v1` | — |
| `openrouter.api.model` | `openrouter/free` | — |
| `spring.ai.openai.api-key` | `${OPENROUTER_API_KEY}` | **`OPENROUTER_API_KEY`** (embeddings) |
| `spring.ai.openai.base-url` | `https://openrouter.ai/api` | — |
| `spring.ai.openai.embedding.options.model` | `openai/text-embedding-3-small` | — |

- **Never commit real keys.** `OPENROUTER_API_KEY` is resolved from the
  environment; the repo's `.gitignore` already excludes `.env`, `.env.local`
  and `application-local.properties/yml`.
- PostgreSQL must have the **pgvector extension installed**
  (`CREATE EXTENSION IF NOT EXISTS vector;`) and a database matching
  `spring.datasource.url`.
- If the embedding model changes dimension, update the
  `columnDefinition = "vector(N)"` in `Producto.java` and
  `spring.ai.openai.embedding.options.model`; without this, `<=>` casts can
  fail at query time.

---

## 11. Development Rules (Conventions to Follow)

1. **Java 21**; prefer `records` for DTOs and constructor injection; keep
   Lombok usage minimal (entities/`@ConfigurationProperties`).
2. **Spanish** for user-facing strings: validation messages, exception messages,
   the LLM system prompt, and this domain's DTO field semantics.
3. Never change the OpenRouter `SYSTEM_PROMPT` JSON contract without updating
   `SugerenciaFerreteriaDTO` in the same change.
4. When adding endpoints: update `SecurityConfig` rules, keep `GET` read-only
   endpoints public only if intended (current policy), and document them in
   this file's endpoint table.
5. Any new exception type must be mapped in `GlobalExceptionHandler` with an
   explicit HTTP status.
6. Embeddings are derived from `nombre + descripcionColoquial`; if the formula
   changes, existing rows' embeddings become stale — plan a re-index.
7. `ddl-auto=update` is for dev; do not rely on it for schema migrations in
   production (no Flyway/Liquibase exists in the repo).
8. `RestClient` is the HTTP client of choice (Spring Boot 4 modular starter) —
   do not reintroduce `RestTemplate`.
9. Use the Maven wrapper (`./mvnw`) for builds; `mvnw.cmd` for Windows.
10. Before changing security behavior, note that the JWT secret is currently
    hardcoded in `SecurityConfig` — move it to an environment variable/property
    as part of any security work.

---

## 12. Known Gaps & Unverified Items

The following could **not** be verified from the repository code — do not treat
them as facts:

- **JWT issuance:** no login/token endpoint exists; the OAuth2
  authorization-server/client/resource-server starters are dependencies only.
  How tokens are minted in production is unknown.
- **pgvector extension bootstrap:** no SQL migration creates the extension;
  the database is assumed to already have it.
- **Embedding model & dimensions:** the repo configures
  `spring.ai.openai.embedding.options.model=openai/text-embedding-3-small`
  (1536 dims, matches `vector(1536)`); whether OpenRouter serves that exact
  model id was not end-to-end verified without a live API key.
- **Swagger/OpenAPI reachability:** springdoc is present, but
  `anyRequest().authenticated()` in `SecurityConfig` does not exempt
  `/swagger-ui/**` or `/v3/api-docs` — verified: **403 without a JWT, 200 with**.
- **Tests:** 69 tests across unit (services, controller, exceptions) and
  integration (`@SpringBootTest` + MockMvc + Testcontainers pgvector); the
  old "contextLoads-only" state no longer applies.
- **Frontend:** the repo contains no frontend; `@CrossOrigin` hints at a client
  on `http://localhost:3001` and `ProductoController` comments reference an
  `apiClient.ts` ("Antigravity"), but no such project is in this repository.
- `spring-ai-tika-document-reader` and `spring-ai-vector-store-advisor` are
  declared dependencies with no usage found in `src/main`.

---

## Appendix: Files analyzed to produce this document

`pom.xml`, `src/main/resources/application.properties`,
`src/main/java/org/alexis/ecommerceai/ECommerceAiApplication.java`,
`config/{SecurityConfig,OpenRouterConfig,OpenRouterProperties}.java`,
`security/JwtAuthenticationFilter.java`, `controller/ProductoController.java`,
`ai/{AsistenteIAService,OpenRouterService}.java`, `service/ProductoService.java`,
`repository/ProductoRepository.java`, `model/Producto.java`,
`dto/{ProductoRequestDTO,ProductoResponseDTO,BusquedaInteligenteResponse,DiagnoseRequestDTO,SugerenciaFerreteriaDTO}.java`,
`dto/openrouter/{ChatCompletionRequest,ChatCompletionResponse,ChatMessage}.java`,
`exception/{ErrorResponse,GlobalExceptionHandler,OpenRouterException,OpenRouterRateLimitException,ProductoNotFoundException,StockUpdateConflictException}.java`,
`src/test/java/org/alexis/ecommerceai/ECommerceAiApplicationTests.java`,
`.gitignore`, `.mvn/wrapper/maven-wrapper.properties`.
