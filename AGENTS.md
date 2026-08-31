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

1. Sends the query to **Groq** (LLM chat completions, OpenAI-compatible) which returns a
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
| HTTP client | `spring-boot-starter-restclient` (Spring `RestClient`) | `pom.xml`, `GroqConfig` |
| Persistence | `spring-boot-starter-data-jpa` + `org.postgresql:postgresql` (runtime) | `pom.xml` |
| Security | `spring-boot-starter-security` + OAuth2 authorization-server, client, resource-server starters | `pom.xml`, `SecurityConfig` |
| Validation | `spring-boot-starter-validation` (Jakarta Validation) | `pom.xml`, `ProductoRequestDTO` |
| OpenAPI/Swagger | `springdoc-openapi-starter-webmvc-ui` **3.1.0** | `pom.xml` |
| Spring AI — Embeddings | **no starter** — custom `HuggingFaceEmbeddingModel` (`RestClient`) → HF Inference API `feature-extraction`; `spring-ai-starter-model-openai` was **removed** | `pom.xml`, `HuggingFaceConfig`, `HuggingFaceEmbeddingModel`, `application.properties` |
| Spring AI — Vector store | `spring-ai-starter-vector-store-pgvector` (dependency present; direct SQL used in repo) | `pom.xml`, `ProductoRepository` |
| Spring AI — ETL | `spring-ai-tika-document-reader`, `spring-ai-vector-store-advisor` (declared, no usage found in code) | `pom.xml` |
| JSON | Jackson 3 (`tools.jackson.*` — `ObjectMapper`, `JacksonException`) | `GroqService` |
| Codegen | Lombok (`@Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor`) | `pom.xml`, `Producto`, `GroqProperties` |
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
│   └── GroqService.java         # Groq /chat/completions client + JSON parsing
├── config/
│   ├── SecurityConfig.java            # Filter chain, JWT decoder bean
│   ├── GroqConfig.java          # RestClient bean ("groqRestClient")
│   ├── GroqProperties.java      # @ConfigurationProperties("groq.api")
│   ├── HuggingFaceConfig.java         # RestClient bean ("huggingFaceRestClient") + EmbeddingModel bean
│   ├── HuggingFaceProperties.java     # @ConfigurationProperties("huggingface.api") (key/model/baseUrl)
│   └── HuggingFaceEmbeddingModel.java # EmbeddingModel impl → HF Inference API (feature-extraction, 384 dims)
├── controller/
│   └── ProductoController.java        # /api/v1/productos (REST + AI endpoints, incl. reindexar)
├── dto/
│   ├── ProductoRequestDTO.java        # Create/update payload (record + validation)
│   ├── ProductoResponseDTO.java       # API response (record)
│   ├── BusquedaInteligenteResponse.java
│   ├── DiagnoseRequestDTO.java        # POST /diagnose body { "problema": "..." }
│   ├── ReindexacionResponse.java      # record(procesados, pendientes) for POST /reindexar
│   ├── SugerenciaFerreteriaDTO.java   # LLM JSON contract (keywords/tools/spare parts)
│   └── groq/                    # ChatCompletion{Request,Response}, ChatMessage
├── exception/
│   ├── ErrorResponse.java             # Unified error body (record)
│   ├── GlobalExceptionHandler.java    # @RestControllerAdvice
│   └── (GroqException, GroqRateLimitException, HuggingFaceException,
│        HuggingFaceRateLimitException, ProductoNotFoundException,
│        StockUpdateConflictException)
├── model/
│   └── Producto.java                  # JPA entity "productos" incl. vector(384) column
├── repository/
│   └── ProductoRepository.java        # JPA + native vector similarity query + pendientes de embedding
├── security/
│   └── JwtAuthenticationFilter.java   # Custom Bearer-JWT filter
└── service/
    └── ProductoService.java           # CRUD, stock, keyword & vector search, reindexación
src/main/resources/
└── application.properties             # The only config file (no YAML)
src/test/java/.../ECommerceAiApplicationTests.java
```

**Request flow (AI recommendation):**
`ProductoController` → `AsistenteIAService.buscarRecomendacion()` →
`GroqService.analizarConsulta()` (LLM) → flatten keywords/tools/parts →
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
  database (`CREATE EXTENSION IF NOT EXISTS vector;`). **No migration/SQL file
  creating the extension exists in the repo** — it must be created manually.
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
- `ddl-auto=update` (Hibernate) creates/updates tables; `show-sql=true`.

---

## 5. REST API Surface (`/api/v1/productos`)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/productos` | Public | List all products |
| GET | `/api/v1/productos/{id}` | Public | Get one product |
| GET | `/api/v1/productos/buscar?q=...&limite=5` | Public | pgvector similarity search (top-N) |
| GET | `/api/v1/productos/asistente?q=...` | Public | AI recommendation (Groq + keyword search) |
| POST | `/api/v1/productos/diagnose` | **ADMIN** | Body `{"problema": "..."}` → AI recommendation |
| POST | `/api/v1/productos/reindexar` | **ADMIN** | Re-genera embeddings de productos pendientes → `ReindexacionResponse(procesados, pendientes)` |
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
  `groq/ChatCompletionResponse`, `ChatMessage`). `SugerenciaFerreteriaDTO`
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
| `GroqRateLimitException` (HTTP 429 from Groq) | 429 |
| `GroqException` | exception's `status` (default 502 → `BAD_GATEWAY`; non-error codes are coerced to 502) |
| `HuggingFaceRateLimitException` (HTTP 429 from HF) | 429 |
| `HuggingFaceException` | exception's `status` (default 502 → `BAD_GATEWAY`; 401/403 → `UNAUTHORIZED`-style message, non-error codes coerced to 502) |
| any other `Exception` | 500, generic message (details hidden) |

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
  `pom.xml` (its auto-configuration required the OpenRouter/OpenAI key).
- **Groq** (chat del asistente) is called via a dedicated `RestClient` bean
  (`groqRestClient`) built in `GroqConfig` with headers:
  `Authorization: Bearer <key>`, `HTTP-Referer`, `X-Title`, `Content-Type:
  application/json`.
- `GroqProperties` (`prefix = "groq.api"`): `key`, `model`
  (default `qwen/qwen3.8-27b`), `baseUrl` (default
  `https://api.groq.com/openai/v1`), `httpReferer` (default
  `http://localhost:8080`), `appTitle` (default `Ferreteria IA App`).
- `GroqService.analizarConsulta()`:
  - Validates key/model/query presence (Spanish error messages).
  - Sends `POST {baseUrl}/chat/completions` with `{model, messages:[system, user]}`
    using a fixed `SYSTEM_PROMPT` that instructs the model to answer **only**
    valid JSON `{palabrasClave[], herramientas[], repuestos[]}` — 3–8 keywords,
    no brands/product codes, empty lists for non-hardware queries.
  - Maps HTTP 429 → `GroqRateLimitException`; other HTTP errors →
    `GroqException`; connection/rest failures wrapped accordingly.
  - Parses the model's text: strips markdown fences and extracts the first
    `{...}` block (`extraerJson`), then deserializes with Jackson 3
    `ObjectMapper` into `SugerenciaFerreteriaDTO`. Parse failure →
    `GroqException`.

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
| `groq.api.key` | `${GROQ_API_KEY}` | **`GROQ_API_KEY`** (chat) |
| `groq.api.base-url` | `https://api.groq.com/openai/v1` | — |
| `groq.api.model` | `qwen/qwen3.8-27b` | — |
| `huggingface.api.key` | `${HUGGINGFACE_API_KEY}` | **`HUGGINGFACE_API_KEY`** (embeddings) |
| `huggingface.api.model` | `sentence-transformers/all-MiniLM-L6-v2` (default, 384 dims) | — |
| `huggingface.api.base-url` | `https://router.huggingface.co/hf-inference/models` | — |

- **Never commit real keys.** `GROQ_API_KEY` (chat) and `HUGGINGFACE_API_KEY`
  (embeddings) are resolved from the environment; the repo's `.gitignore`
  already excludes `.env`, `.env.local` and `application-local.properties/yml`.
  (`OPENROUTER_API_KEY` is no longer used — the OpenAI/OpenRouter embedding
  config was removed.)
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

---

## 11. Development Rules (Conventions to Follow)

1. **Java 21**; prefer `records` for DTOs and constructor injection; keep
   Lombok usage minimal (entities/`@ConfigurationProperties`).
2. **Spanish** for user-facing strings: validation messages, exception messages,
   the LLM system prompt, and this domain's DTO field semantics.
3. Never change the Groq `SYSTEM_PROMPT` JSON contract without updating
   `SugerenciaFerreteriaDTO` in the same change.
4. When adding endpoints: update `SecurityConfig` rules, keep `GET` read-only
   endpoints public only if intended (current policy), and document them in
   this file's endpoint table.
5. Any new exception type must be mapped in `GlobalExceptionHandler` with an
   explicit HTTP status.
6. Embeddings are derived from `nombre + descripcionColoquial`; if the formula
   changes, existing rows' embeddings become stale — run `POST
   /api/v1/productos/reindexar` (ADMIN) or plan a re-index.
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
  `sentence-transformers/all-MiniLM-L6-v2` (384 dims, matches `vector(384)` and
  `HuggingFaceEmbeddingModel.DIMENSION`). This is the live configuration after
  the migration from OpenRouter; the HF key is required (`HUGGINGFACE_API_KEY`)
  for embeddings to be generated — a missing key throws `HuggingFaceException`.
- **Tests & data:** 69 tests pass across unit (services, controller, exceptions)
  and integration (`@SpringBootTest` + MockMvc + Testcontainers pgvector); the
  old "contextLoads-only" state no longer applies. Per the migration commit,
  the 23 products in PostgreSQL were successfully vectorized (embeddings
  generated) — this is asserted in the commit message, not re-verified live here
  from code alone.
- **Swagger/OpenAPI reachability:** springdoc is present, but
  `anyRequest().authenticated()` in `SecurityConfig` does not exempt
  `/swagger-ui/**` or `/v3/api-docs` — verified: **403 without a JWT, 200 with**.
- **Frontend:** the repo contains no frontend; `@CrossOrigin` hints at a client
  on `http://localhost:3001` and `ProductoController` comments reference an
  `apiClient.ts` ("Antigravity"), but no such project is in this repository.
- `spring-ai-tika-document-reader` and `spring-ai-vector-store-advisor` are
  declared dependencies with no usage found in `src/main`.

---

## Appendix: Files analyzed to produce this document

`pom.xml`, `src/main/resources/application.properties`,
`src/main/java/org/alexis/ecommerceai/ECommerceAiApplication.java`,
`config/{SecurityConfig,GroqConfig,GroqProperties,HuggingFaceConfig,HuggingFaceProperties,HuggingFaceEmbeddingModel}.java`,
`security/JwtAuthenticationFilter.java`, `controller/ProductoController.java`,
`ai/{AsistenteIAService,GroqService}.java`, `service/ProductoService.java`,
`repository/ProductoRepository.java`, `model/Producto.java`,
`dto/{ProductoRequestDTO,ProductoResponseDTO,BusquedaInteligenteResponse,DiagnoseRequestDTO,ReindexacionResponse,SugerenciaFerreteriaDTO}.java`,
`dto/groq/{ChatCompletionRequest,ChatCompletionResponse,ChatMessage}.java`,
`exception/{ErrorResponse,GlobalExceptionHandler,GroqException,GroqRateLimitException,HuggingFaceException,HuggingFaceRateLimitException,ProductoNotFoundException,StockUpdateConflictException}.java`,
`src/test/java/org/alexis/ecommerceai/ECommerceAiApplicationTests.java`,
`.gitignore`, `.mvn/wrapper/maven-wrapper.properties`.
