# Design: Bloque 3 — Dominio E-Commerce

## Technical Approach

Cuatro slices verticales (categorias, user-auth, pedidos, carrito) sobre la arquitectura en capas actual, replicando convenciones de `Producto`: entidades JPA (tablas snake_case plurales), DTOs record con validación en español, servicios `@Transactional`, patrón optimistic-lock→409, toda excepción mapeada en `GlobalExceptionHandler`. Sin cambios de stack; embedding y búsquedas intactos (spec product-categories R3). Auth reemplaza `admin/admin123` por `Usuario` en BD con claims `roles` `ROLE_`-prefijados → `hasRole("ADMIN")` y `jwtAdmin()` siguen funcionando. Responde a las 4 specs.

## Architecture Decisions

| # | Decisión | Alternativas | Rationale (evidencia del codebase) |
|---|---|---|---|
| D1 | **Carrito persistente en BD** | Efímero (map/cache in-memory) descartado | API explícitamente STATELESS (`SessionCreationPolicy.STATELESS`): mapa por-usuario reintroduce estado servidor, perdido en restart/escalado. `cache`+`caffeine` declarados pero **sin uso** → el efímero sería primer consumidor (config+eviction nueva). Persistente encaja con convenciones JPA/Testcontainers de los otros bloques y da cobertura de integración real; el checkout Bloque 4 (carrito→Pedido en 1 tx) exigirá persistencia: migrar después cuesta más. Costo acotado: **una tabla `items_carrito` sin header aggregate** (total/precios resueltos en GET); merge por `UNIQUE(usuario_id, producto_id)` (POST suma, PATCH setea); borrado de producto por FK `ON DELETE CASCADE`. Cumple toda la spec: solo autenticado, 404 cross-user, stock-agnóstico, sin checkout |
| D2 | Categoria: FK nullable + regla en service | `nullable=false`+backfill descartado | `ddl-auto=update` sobre 23 filas legacy; backfill es Bloque 4 → inseguro. Nullable deja filas NULL legacy válidas; `@NotNull categoriaId` en create/update (400 campo, español); id desconocido → `CategoriaNotFoundException` 404. `@ManyToOne` unidireccional (sin colección en Categoria); delete referenciado → `existsByCategoriaId` → 409 sin cascade. Embeddings intactos |
| D3 | Pedido agregado con snapshot + stock transaccional | — | `ItemPedido.producto_id` columna Long **sin `@ManyToOne`** + `precio_unitario` snapshot → drift imposible, sin lazy-loading. `Pedido`: `@ManyToOne Usuario`, `@OneToMany(mappedBy="pedido", cascade=ALL, orphanRemoval=true)`, `total` persistido, `estado` español STRING (solo `PENDIENTE`). 1 tx: `stock>=cantidad`? no → `StockInsuficienteException` 409; decremento; `OptimisticLockingFailureException` → `StockUpdateConflictException` 409 → rollback total (patrón `updateStock`; sin pedido parcial); productos cargados por id ordenado → lock determinista. `ProductoService.delete` → `existsByProductoId` → `ProductoConPedidosException` 409. Owner del JWT; GET `{id}` ajeno → 404 |
| D4 | Auth: claims `ROLE_` + JWT unificado | Prefijo en filter descartado (rompe `hasRole`/`jwtAdmin`) | Claims `"ROLE_"+rol` desde BD. **Delete `JwtConfig`** (dead code, secret default divergente — bug latente) → `JwtProperties` record `@ConfigurationProperties("app.jwt")` vía `@EnableConfigurationProperties` (patrón `HuggingFaceProperties`); beans `JwtEncoder`/`JwtDecoder`/`PasswordEncoder` (BCrypt, ya en `spring-security-crypto`) en `SecurityConfig`; TTL desde `app.jwt.expiration` (`${JWT_EXPIRATION_MS:86400000}` en base; `expiresIn` en segundos) → una sola fuente |
| D4b | Login/Register/Seed | — | `findByUsername`+`matches`; usuario desconocido y password mala → misma `CredencialesInvalidasException` 401 (sin enumeración). Register público fuerza CLIENTE; dup → `UsuarioDuplicadoException` 409. Seed `ApplicationRunner` idempotente crea `admin` si falta, BCrypt de `app.seed.admin-password` (`${ADMIN_PASSWORD:admin123}`); jamás re-hashea |
| D5 | Excepciones por jerarquía | 8 handlers individuales descartados (infla diff) | Bases nuevas `RecursoNoEncontradoException`(404) → Categoria/Pedido/ItemCarrito NotFound; `ConflictoException`(409) → UsuarioDuplicado, CategoriaEnUso, ProductoConPedidos, StockInsuficiente. 2 handlers de base + backstop `DataIntegrityViolationException`→409. Mensajes español; excepciones Bloques 1–2 intactas |

## Data Model

| Tabla | Columnas |
|---|---|
| `categorias` | id PK; nombre varchar(100) **not null unique**; descripcion TEXT null |
| `productos` (mod) | + `categoria_id` bigint null FK→categorias |
| `usuarios` | id PK; username varchar(50) not null unique; password varchar(100) not null (BCrypt); rol varchar(20) not null |
| `pedidos` | id PK; usuario_id FK not null; fecha_creacion TIMESTAMP not null; estado varchar(20) not null; total numeric(10,2) not null |
| `items_pedido` | id PK; pedido_id FK not null (cascade); producto_id bigint not null (sin FK); cantidad int not null; precio_unitario numeric(10,2) not null |
| `items_carrito` | id PK; usuario_id FK not null (cascade); producto_id FK not null ON DELETE CASCADE; cantidad int not null; **UNIQUE(usuario_id, producto_id)** |

Money `precision=10, scale=2`; `@Column(name="snake_case")` como `Producto`. Sin `@Version` fuera de `Producto`.

## API Surface

| Método y path | Auth | Notas |
|---|---|---|
| POST `/auth/login` | public | mod: BD+BCrypt, TTL real; shape `LoginResponse` intacto |
| POST `/auth/register` | public | 201 `UsuarioResponseDTO(id, username, rol)`; nunca password |
| GET `/v1/categorias[/{id}]` | public | nuevo |
| POST/PUT/DELETE `/v1/categorias/**` | ADMIN | dup 409; delete referenciado 409 |
| POST/PUT/DELETE `/v1/productos/**` | ADMIN (existe) | DTO +`categoriaId`; delete 409 si en ItemPedido |
| PATCH `/v1/productos/**` | **ADMIN (fix)** | hoy cae en `anyRequest().authenticated()` → CLIENTE podría stock-patchear |
| POST `/v1/pedidos` | autenticado | 201; 400 validación, 404 producto, 409 stock/race |
| GET `/v1/pedidos[/{id}]` | autenticado | solo propios (newest first); `{id}` owner/ADMIN, resto 404 |
| `/v1/carrito`: GET/DELETE; `/items` POST; `/items/{id}` PATCH/DELETE | autenticado | 404 line/producto ajenos o inexistentes |

`SecurityConfig`: + GET categorias permitAll; POST/PUT/DELETE categorias y PATCH productos → ADMIN; `/auth/register` permitAll; pedidos/carrito por `anyRequest().authenticated()`; reglas nuevas antes de `anyRequest`.

**DTOs**: `CategoriaRequest/ResponseDTO`; `RegisterRequestDTO` (password `@Size(min=8)`); `UsuarioResponseDTO`; `PedidoRequestDTO` (`@NotEmpty` de `LineaPedidoDTO(productoId, cantidad @Min(1))`); `PedidoResponseDTO` (estado/total/fecha + items: productoId, cantidad, precioUnitario, subtotal); `CarritoResponseDTO` (línea: id, productoId, nombre, precioUnitario, cantidad, subtotal; total 2 decimales) + request con `cantidad @Min(1)`. Modificados: `ProductoRequest/ResponseDTO` (+`categoriaId`).

## File Changes

| Archivo | Acción |
|---|---|
| `model/{Categoria,Usuario,Pedido,ItemPedido,ItemCarrito,Rol}`, `repository/*5`, `service/{Categoria,Auth,Pedido,Carrito}Service`, `controller/{Categoria,Pedido,Carrito}Controller` | Create |
| `config/{JwtProperties,AdminSeeder}`, DTOs listados, `exception/` (2 bases + 7 hijas), tests por slice | Create |
| `config/SecurityConfig`, `model/Producto`, `repository/ProductoRepository`, `service/ProductoService`, `dto/ProductoRequest|ResponseDTO`, `controller/AuthController`, `exception/GlobalExceptionHandler`, `application.properties` | Modify |
| `config/JwtConfig` | Delete |

## Testing Strategy

| Capa | Qué → Cómo |
|---|---|
| Unit service | Categoria FK/409; Auth login/register/seed; Pedido totals/404/stock/`OptimisticLockingFailure→409`; Carrito merge/404 cross-user → Mockito+AssertJ (`ProductoServiceTest` pattern) |
| Unit controller | status/validación por endpoint; primer `AuthControllerTest` → standalone MockMvc + validator + handler, contextPath `/api` |
| Exceptions | bases/hijas → 404/409; DIVE → 409 → stub controllers |
| Integración | seed admin en boot; login ok y 401 indistinguible; register→CLIENTE 403 en mutaciones; categoria FK + legacy NULL; pedido decrementa y 409 sin efectos parciales; producto en ItemPedido no borrable; carrito aislamiento/merge/totales/purga; `jwtAdmin()` verde; embedding/search intactos → `AbstractIntegrationTest` (Testcontainers) |

## Threat Matrix

N/A — sin routing de infra, shell, subprocesos, VCS/PR, ejecutables ni integración de procesos. Cambios de reglas HTTP en `SecurityConfig` verificados por tests (CLIENTE 403, sin token 403, ADMIN 200).

## Migration / Rollout

`ddl-auto=update` crea tablas nuevas y `categoria_id` nullable (sin backfill). Seed idempotente preserva `admin/admin123` dev/test; prod exige `ADMIN_PASSWORD`. Breaking: credenciales contra BD. Rollback: revert PR; drop manual. Riesgo principal: tamaño del diff vs presupuesto 400 del PR único (sdd-tasks debe pronosticar).

## Open Questions (→ tasks)

- [ ] Purge de líneas de carrito: ¿solo `ON DELETE CASCADE` (`@OnDelete`) o además purge defensivo? (ambas cumplen el escenario)
- [ ] Nombres exactos de queries derivadas y si `GET /v1/pedidos` embebe items
