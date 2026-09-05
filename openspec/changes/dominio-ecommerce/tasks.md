# Tasks: Bloque 3 — Dominio E-Commerce

TDD estricto: cada `X.RED` falla antes de su `X.GREEN`; verificación `./mvnw test`.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~3.800–4.500 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR único con `size:exception` (commits por work-unit); rechazado → 5 PRs encadenados |
| Delivery strategy | single-pr |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Focused test | Runtime harness | Rollback boundary |
|---|---|---|---|
| 1. Excepciones 404/409 + DIVE | `./mvnw test -Dtest=GlobalExceptionHandlerTest` | N/A — mapeo unit; HTTP real en integration de slices | revert `exception/RecursoNoEncontradoException.java`/`ConflictoException.java` + handlers base |
| 2. Categorías + FK Producto | `./mvnw test -Dtest=Categoria*Test,ProductoServiceTest` | `./mvnw test -Dtest=ProductoControllerIntegrationTest` (FK legacy NULL) | revert `Categoria*`, DTOs `Categoria*`, mods `Producto*` + tests |
| 3. Auth BD + fix PATCH stock | `./mvnw test -Dtest=AuthServiceTest,AuthControllerTest` | `./mvnw test -Dtest=*IntegrationTest` (seed, 401, CLIENTE 403) | revert `Usuario`/`Rol`/`AuthService`/`AuthController`/`JwtProperties`/`AdminSeeder`, reglas `SecurityConfig.java`; delete `JwtConfig.java` |
| 4. Pedidos | `./mvnw test -Dtest=PedidoServiceTest,PedidoControllerTest` | `./mvnw test -Dtest=*IntegrationTest` (decremento, race 409) | revert `Pedido`/`ItemPedido`+repos/service/controller, guard en `ProductoService.delete` |
| 5. Carrito persistente | `./mvnw test -Dtest=CarritoServiceTest,CarritoControllerTest` | `./mvnw test -Dtest=*IntegrationTest` (merge, cross-user 404, purga) | revert `ItemCarrito`+repo/service/controller, DTOs carrito |

## Phase 1: Foundation — Excepciones

- [ ] 1.1 RED: extender `GlobalExceptionHandlerTest.java` (stub controllers): bases 404/409, `DataIntegrityViolationException`→409
- [ ] 1.2 GREEN: crear `exception/RecursoNoEncontradoException.java`, `exception/ConflictoException.java`; handlers base + backstop DIVE en `GlobalExceptionHandler.java`

## Phase 2: Categorías + FK en Producto

- [ ] 2.1 RED `CategoriaServiceTest.java`+`CategoriaControllerTest.java`: CRUD 201/200/204, dup→409, delete referenciado→409 sin cascade, GET público, sin token→403 (spec S1–S4)
- [ ] 2.2 RED `ProductoServiceTest.java`+integración: sin `categoriaId`→400, id desconocido→404, legacy NULL→200/buscable (S5–S8)
- [ ] 2.3 GREEN: `model/Categoria.java`, `repository/CategoriaRepository.java` (`existsByNombre`/`existsByCategoriaId`), `service/CategoriaService.java`, `controller/CategoriaController.java`; `CategoriaNotFoundException`/`CategoriaEnUsoException`
- [ ] 2.4 GREEN: `model/Producto.java` +`@ManyToOne` nullable; `dto/ProductoRequestDTO.java`/`ProductoResponseDTO.java` +`categoriaId`; validar en `service/ProductoService.java`; `config/SecurityConfig.java`: GET categorias permitAll, POST/PUT/DELETE ADMIN
- [ ] 2.5 GREEN: fixtures con `categoriaId` en `ProductoControllerIntegrationTest.java`/`ProductoControllerTest.java`; embeddings intactos

## Phase 3: Auth BD + fix PATCH stock

- [ ] 3.1 RED `AuthServiceTest.java`+`AuthControllerTest.java`: login admin→`ROLE_ADMIN`+TTL `app.jwt.expiration`; pass mala/usuario ∄→401 iguales; register→201 CLIENTE, dup→409, hint ADMIN→CLIENTE; sin hash
- [ ] 3.2 RED integración (fix #5): PATCH stock CLIENTE→403 (hoy 200), sin token→403, ADMIN→200
- [ ] 3.3 GREEN: `model/Usuario.java`, `model/Rol.java`, `repository/UsuarioRepository.java`, `service/AuthService.java`; `RegisterRequestDTO.java` (`@Size(min=8)`)/`UsuarioResponseDTO.java`; `UsuarioDuplicadoException`
- [ ] 3.4 GREEN: `config/AdminSeeder.java` (idempotente, BCrypt `${ADMIN_PASSWORD:admin123}`); `config/JwtProperties.java` `@ConfigurationProperties("app.jwt")`; beans `JwtEncoder`/`JwtDecoder`/`PasswordEncoder`; `config/SecurityConfig.java` PATCH productos→ADMIN + endpoint /auth/register (read-only) permitAll; login BD+register en `controller/AuthController.java`; `application.properties` +`app.jwt.expiration`
- [ ] 3.5 GREEN: delete `config/JwtConfig.java` (dead, secret divergente)

## Phase 4: Pedidos

- [ ] 4.1 RED `PedidoServiceTest.java`+`PedidoControllerTest.java`: 201 snapshot/total/decremento (S1); insuficiente→409 sin parcial (S2); vacío/0→400, producto ∄→404 (S3); race última unidad→409 (S4); ajeno→404 (S5–S6); snapshot inmutable (S7); con pedidos→409 (S8)
- [ ] 4.2 GREEN: `model/Pedido.java`+`ItemPedido.java` (snapshot, `PENDIENTE`), repos pedido/item, `service/PedidoService.java` (`@Transactional`, lock ordenado, `OptimisticLockingFailureException`→409), `controller/PedidoController.java`; DTOs `PedidoRequestDTO`/`LineaPedidoDTO`/`PedidoResponseDTO`; `PedidoNotFoundException`/`StockInsuficienteException`
- [ ] 4.3 GREEN: `existsByProductoId` en `ProductoRepository.java` + guard `ProductoConPedidosException`→409 en `ProductoService.delete`

## Phase 5: Carrito persistente

- [ ] 5.1 RED `CarritoServiceTest.java`+`CarritoControllerTest.java`: sin token→403 (S1); línea ajena→404 (S2); merge→1 línea (S4)/2 productos→2 (S3); cantidad 0→400, producto ∄→404 (S5); totales precio actual (S6); borrado→excluido+purgado (S7); over-stock ok (S8); pedido no altera carrito (S9)
- [ ] 5.2 GREEN: `model/ItemCarrito.java` (UNIQUE usuario+producto, FK producto CASCADE), `repository/ItemCarritoRepository.java`, `service/CarritoService.java`, `controller/CarritoController.java`; DTOs carrito; `ItemCarritoNotFoundException`; purge defensivo

## Phase 6: Verificación

- [ ] 6.1 Correr `./mvnw test` completo verde (unit+integration); embedding/`jwtAdmin()` intactos
- [ ] 6.2 Actualizar `AGENTS.md`: tablas endpoints/seguridad + sección JWT
