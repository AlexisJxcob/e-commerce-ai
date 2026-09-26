# 🛠️ Repara.ai / Ferretería IA — Especificación Técnica & Blueprint para Frontend en Angular 22

> **Documento de Arquitectura y Especificación de Contratos de API**  
> **Versión:** 1.0.0 — Septiembre 2026  
> **Stack Backend:** Spring Boot 4.1.1 (Java 21), Spring AI 2.0.1, PostgreSQL + pgvector, Flyway 12, Transbank Webpay Plus, Hugging Face Inference API.  
> **Stack Objetivo Frontend:** Angular 22 (Standalone, Zoneless, Signals-first, HTTP Resource, Modern Control Flow).

---

## 1. El Porqué del Proyecto (The "Why" & Visión de Negocio)

### 1.1 El Problema Real en el Mostrador Ferretero
En el comercio ferretero tradicional (físico o digital), el usuario no busca por especificaciones industriales ni por códigos SKU (ej. no busca *"Tubo PVC hidráulico clase 10 de 1/2 pulgada x 6 m"* ni *"Perno hexagonal grado 8.8 cincado M8x40"*).

El cliente promedio y los maestros de faena enfrentan **averías imprevistas o proyectos de mantención**:
- *"Se me reventó una cañería de cobre bajo el lavaplatos y está saliendo agua con fuerza."*
- *"Tengo una fuga en el codo de PVC del desagüe del baño."*
- *"Salter el automático cada vez que enciendo el hervidor en la cocina."*

En un e-commerce convencional de catálogo plano, esa consulta falla estrepitosamente o arroja cero resultados porque el motor relacional busca coincidencia exacta de texto. El usuario abandona la compra por frustración o porque no sabe qué herramientas y consumibles complementarios necesita para completar el trabajo.

### 1.2 La Propuesta de Valor de Repara.ai
**Repara.ai** actúa como el **maestro ferretero digital de alta precisión**:
1. **Comprensión Semántica & Diagnóstico Técnico:** Traduce lenguaje coloquial chileno/latinoamericano mediante un LLM (*Llama 3.1 8B Instruct vía Hugging Face Chat*) y extrae una estructura estricta:
   - **Palabras clave normalizadas:** Términos técnicos para indexación.
   - **Herramientas necesarias:** Lo indispensable para la faena (ej. cortatubos, soplete para soldar, llave francesa).
   - **Repuestos y consumibles:** Lo que se debe cambiar o aplicar (ej. codo de cobre 1/2", pasta fundente, soldadura estaño 50%, lija para metales).
2. **Búsqueda Híbrida Inteligente:**
   - **Búsqueda Vectorial (`pgvector`):** Vectores de 384 dimensiones generados con `sentence-transformers/all-MiniLM-L6-v2` contra la columna `embedding` usando distancia coseno (`<=>`), encontrando productos por afinidad conceptual aunque no compartan exactamente el mismo texto.
   - **Búsqueda Relacional Ponderada:** Coincidencias en `nombre`, `descripcionColoquial`, `descripcionTecnica` y `sku`.
3. **Flujo de Compra Certero:** El usuario añade en 1 click el kit completo de reparación al carrito persistente, cotiza despacho o retiro según reglas de negocio server-side, y abona a través de Webpay Plus con control estricto de concurrencia de stock.

---

## 2. Arquitectura de Backend & Seguridad

### 2.1 Topología y Convenciones de Red
- **Puerto por defecto:** `8080`
- **Context-Path:** `/api` (Todas las peticiones del cliente deben llevar el prefijo `/api`).
- **Base URL estándar:** `http://localhost:8080/api`
- **CORS Config:** Habilitado para `http://localhost:4200` (puerto Angular por defecto), soportando `GET, POST, PUT, PATCH, DELETE, OPTIONS`. Cabecera `Authorization` permitida y expuesta.
- **Documentación Swagger / OpenAPI 3:**
  - JSON OpenAPI: `GET /api/api-docs`
  - Swagger UI: `GET /api/swagger-ui.html`

### 2.2 Mecanismo de Autenticación & Autorización
- **Arquitectura:** Stateless con JWT (JSON Web Tokens) firmados con algoritmo **HMAC-SHA256**.
- **Cabecera de autenticación:** `Authorization: Bearer <TOKEN>`
- **Emisión de Token:** En `POST /api/auth/login`. El cuerpo de respuesta entrega `{ token, username, expiresIn }`.
- **Resolución de Roles:** El rol **proviene exclusivamente del claim firmado `roles` dentro del JWT** (`ROLE_CLIENTE` o `ROLE_ADMIN`).
  > ⚠️ **Regla de Seguridad Absoluta:** Ni cabeceras como `X-Role: ADMIN` ni parámetros de URL pueden elevar privilegios. Todo el control de acceso en Spring Security se resuelve evaluando la firma criptográfica del token.

### 2.3 Matriz de Control de Acceso por Endpoint

| Método | Endpoint (Ruta HTTP) | Nivel de Acceso | Descripción |
|---|---|---|---|
| `POST` | `/api/auth/login` | Público | Autenticación de usuarios existentes |
| `POST` | `/api/auth/register` | Público | Registro público (fuerza siempre `ROLE_CLIENTE`) |
| `GET` | `/api/v1/productos/**` | Público | Consulta de catálogo y detalle |
| `GET` | `/api/v1/productos/buscar` | Público | Búsqueda vectorial semántica (pgvector) |
| `GET` | `/api/v1/productos/asistente` | Público | Diagnóstico IA vía parámetro GET |
| `POST` | `/api/v1/productos/diagnose` | **ADMIN** | Diagnóstico IA vía cuerpo JSON |
| `POST` | `/api/v1/productos/reindexar` | **ADMIN** | Generación masiva de embeddings pendientes |
| `POST` | `/api/v1/productos` | **ADMIN** | Creación de productos |
| `PUT` | `/api/v1/productos/{id}` | **ADMIN** | Modificación integral de productos |
| `PATCH` | `/api/v1/productos/{id}/stock` | **ADMIN** | Actualización rápida de stock |
| `DELETE` | `/api/v1/productos/{id}` | **ADMIN** | Eliminación de productos |
| `GET` | `/api/v1/categorias/**` | Público | Listado y jerarquía de categorías |
| `POST` | `/api/v1/categorias` | **ADMIN** | Creación de categoría |
| `PUT` | `/api/v1/categorias/{id}` | **ADMIN** | Modificación de categoría |
| `DELETE` | `/api/v1/categorias/{id}` | **ADMIN** | Eliminación de categoría |
| `GET` | `/api/v1/usuarios/me` | **Autenticado** | Perfil del usuario actual |
| `GET` | `/api/v1/usuarios` | **ADMIN** | Listado de todos los usuarios |
| `GET` | `/api/v1/carrito` | **Autenticado** | Carrito persistente del usuario |
| `POST` | `/api/v1/carrito/items` | **Autenticado** | Agregar ítem al carrito |
| `PATCH` | `/api/v1/carrito/items/{id}` | **Autenticado** | Modificar cantidad de ítem |
| `DELETE` | `/api/v1/carrito/items/{id}` | **Autenticado** | Quitar ítem del carrito |
| `DELETE` | `/api/v1/carrito` | **Autenticado** | Vaciar carrito completo |
| `POST` | `/api/v1/pedidos` | **Autenticado** | Crear pedido desde lista de ítems |
| `POST` | `/api/v1/pedidos/desde-carrito` | **Autenticado** | Convertir carrito en pedido con despacho |
| `POST` | `/api/v1/pedidos/cotizar` | **Autenticado** | Cotizar subtotal, despacho y total |
| `GET` | `/api/v1/pedidos` | **Autenticado** | Historial de pedidos del usuario |
| `GET` | `/api/v1/pedidos/{id}` | **Autenticado** | Detalle de pedido propio (o cualquiera si es ADMIN) |
| `PATCH` | `/api/v1/pedidos/{id}/estado` | **ADMIN** | Avanzar estado en la máquina de ciclo de vida |
| `POST` | `/api/v1/pedidos/{id}/pagar` | **Autenticado** | Iniciar transacción Webpay Plus |
| `ALL` | `/api/v1/pagos/webpay/**` | Público | Callbacks y confirmaciones de pasarela |

---

## 3. Modelo de Dominio y Reglas de Negocio Críticas

### 3.1 Concurrencia y Control de Inventario
- Cada producto posee una columna `version` gestionada mediante **bloqueo optimista (`@Version`)**.
- Al crear un pedido, el decremento de stock se valida y se fuerza un `saveAndFlush`.
- Si dos usuarios intentan comprar la última unidad al mismo tiempo, el perdedor de la carrera recibe inmediatamente un **HTTP 409 Conflict** (`StockUpdateConflictException`), evitando sobreventas o inventarios negativos.

### 3.2 Máquina de Estados del Pedido (`EstadoPedido`)
El ciclo de vida del pedido es estricto e unidireccional, persistido como `VARCHAR(20)`:
```
PENDIENTE ──▶ CONFIRMADO ──▶ ENVIADO ──▶ ENTREGADO (Terminal)
    │              │             │
    └──────────────┴─────────────┴──────▶ CANCELADO (Terminal)
```
- **Transiciones válidas:**
  - `PENDIENTE` ➔ `CONFIRMADO` o `CANCELADO`
  - `CONFIRMADO` ➔ `ENVIADO` o `CANCELADO`
  - `ENVIADO` ➔ `ENTREGADO` o `CANCELADO`
  - `ENTREGADO` y `CANCELADO` son estados terminales (no admiten cambios).

### 3.3 Regla Única de Costo de Entrega (`EnvioService`)
La cotización y el cobro se calculan exclusivamente en el servidor (en pesos chilenos CLP):
- **Retiro en Tienda (`RETIRO`):** `$0 CLP`.
- **Despacho a Domicilio (`DESPACHO`):**
  - Si `subtotal >= $50.000 CLP`: **Gratis (`$0 CLP`)**.
  - Si `subtotal < $50.000 CLP`: **Tarifa plana `$3.990 CLP`**.

### 3.4 Pasarela Transbank Webpay Plus
1. El frontend invoca `POST /api/v1/pedidos/{id}/pagar`.
2. El backend genera la transacción en Webpay y retorna `{ token: string, url: string }`.
3. El frontend redirige mediante un formulario POST automático al portal seguro de Transbank.
4. Tras pagar o cancelar en Transbank, el usuario regresa al callback del backend (`/api/v1/pagos/webpay/retorno`), el cual procesa el commit y redirige al frontend a la URL configurada (ej. `http://localhost:4200/checkout/resultado?token=...&status=exito&buy_order=...`).

### 3.5 Formato Unificado de Errores (`ErrorResponse`)
Todos los errores del backend responden con el siguiente esquema JSON:
```json
{
  "timestamp": "2026-09-25T21:18:26.123",
  "status": 400,
  "message": "Error de validación",
  "fieldErrors": {
    "rut": "El RUT del comprador es obligatorio",
    "email": "El correo del comprador no es válido"
  }
}
```
- `status: 400`: Petición inválida o validación fallida (incluye `fieldErrors`).
- `status: 401`: Credenciales inválidas o token expirado/no provisto.
- `status: 403`: Falta de permisos (requiere rol ADMIN).
- `status: 404`: Recurso no encontrado (producto, categoría, pedido inexistente o ajeno).
- `status: 409`: Conflicto de estado (concurrencia de stock, categoría con productos asociados, jerarquía cíclica, email/username duplicado).
- `status: 429`: Rate limit alcanzado con Hugging Face.
- `status: 502`: Fallo aguas arriba del servicio de IA (Hugging Face).

---

## 4. Catálogo Detallado de APIs (REST Contracts)

### 4.1 Módulo Autenticación (`/api/auth`)

#### `POST /api/auth/login`
- **Acceso:** Público
- **Request Body:**
```json
{
  "username": "juan_perez",
  "password": "Password123!"
}
```
- **Response 200 OK:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "username": "juan_perez",
  "expiresIn": 86400000
}
```
- **Errores:** 401 Unauthorized (`"Credenciales inválidas"`), 400 Bad Request.

#### `POST /api/auth/register`
- **Acceso:** Público (fuerza rol `CLIENTE`).
- **Request Body:**
```json
{
  "username": "juan_perez",
  "password": "Password123!",
  "email": "juan.perez@example.cl",
  "rol": "CLIENTE"
}
```
- **Response 201 Created:**
```json
{
  "id": 15,
  "username": "juan_perez",
  "rol": "CLIENTE",
  "email": "juan.perez@example.cl"
}
```
- **Errores:** 409 Conflict (`"El nombre de usuario ya está en uso"` o email duplicado), 400 Bad Request.

---

### 4.2 Módulo Productos & Asistente IA (`/api/v1/productos`)

#### `GET /api/v1/productos`
- **Acceso:** Público
- **Query Params:** `page` (número, default 0), `size` (tamaño, default 20), `sort` (campo, ej. `nombre,asc`).
- **Response 200 OK:** Page de Spring Data:
```json
{
  "content": [
    {
      "id": 1,
      "sku": "TUB-PVC-001",
      "nombre": "Tubo PVC Hidráulico 1/2 pulgada x 6m",
      "descripcionTecnica": "Tubo de PVC hidráulico presión 10 bar...",
      "descripcionColoquial": "Tubo plástico de agua fría para desagüe y baño",
      "precio": 4590.00,
      "stock": 35,
      "categoriaId": 3
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 85,
    "totalPages": 5
  }
}
```

#### `GET /api/v1/productos/{id}`
- **Acceso:** Público
- **Response 200 OK:** `ProductoResponseDTO` individual.
- **Errores:** 404 Not Found.

#### `GET /api/v1/productos/buscar?q={termino}&limite={n}`
- **Acceso:** Público
- **Parámetros:** `q` (mínimo 2 caracteres, máx 250), `limite` (entero, default 5).
- **Mecanismo:** Búsqueda vectorial semántica pgvector (`MiniLM-L6-v2`, 384 dims, operador `<=>`).
- **Response 200 OK:** Array `ProductoResponseDTO[]`.

#### `GET /api/v1/productos/asistente?q={consultaColoquial}`
- **Acceso:** Público
- **Query Params:** `q` (consulta en lenguaje coloquial, ej. `"fuga en tubo de agua de cobre"`).
- **Response 200 OK:** `BusquedaInteligenteResponse`
```json
{
  "sugerencia": {
    "palabrasClave": ["tuberia cobre", "codo cobre", "soldadura", "pasta fundente"],
    "herramientas": ["soplete gas", "cortatubo de cobre", "llave francesa"],
    "repuestos": ["codo cobre 1/2", "lija fierro", "rollo teflon", "soldadura 50%"]
  },
  "productos": [
    {
      "id": 12,
      "sku": "COP-TUB-012",
      "nombre": "Tubo Cobre 1/2 pulgada x 3m Tipo L",
      "descripcionTecnica": "Tubería de cobre sin costura para agua caliente y fría...",
      "descripcionColoquial": "Cañería de cobre para agua potable",
      "precio": 12990.00,
      "stock": 18,
      "categoriaId": 4
    }
  ]
}
```

#### `POST /api/v1/productos/diagnose`
- **Acceso:** **ADMIN**
- **Request Body:** `{ "problema": "tengo humedad en la pared por un caño roto" }`
- **Response 200 OK:** Misma estructura `BusquedaInteligenteResponse`.

#### `POST /api/v1/productos/reindexar`
- **Acceso:** **ADMIN**
- **Descripción:** Genera y almacena en base de datos los vectores de productos que tengan `embedding IS NULL`.
- **Response 200 OK:**
```json
{
  "procesados": 14,
  "pendientes": 0
}
```

#### `POST /api/v1/productos`
- **Acceso:** **ADMIN**
- **Request Body (`ProductoRequestDTO`):**
```json
{
  "sku": "SOL-EST-005",
  "nombre": "Soldadura Estaño 50% Carrete 250g",
  "precio": 7990.00,
  "stock": 25,
  "descripcionTecnica": "Aleación 50/50 plomo estaño para cañerías de cobre",
  "descripcionColoquial": "Estaño para soldar tubos de cobre con soplete",
  "categoriaId": 4
}
```
- **Response 201 Created:** `ProductoResponseDTO`.

#### `PUT /api/v1/productos/{id}`
- **Acceso:** **ADMIN**
- **Request Body:** Mismo `ProductoRequestDTO`.
- **Response 200 OK:** `ProductoResponseDTO`.

#### `PATCH /api/v1/productos/{id}/stock?stock={nuevoStock}`
- **Acceso:** **ADMIN**
- **Query Param:** `stock` (entero `>= 0`).
- **Response 200 OK:** `ProductoResponseDTO`.

#### `DELETE /api/v1/productos/{id}`
- **Acceso:** **ADMIN**
- **Response:** 204 No Content. (409 si el producto ya cuenta con pedidos asociados históricos).

---

### 4.3 Módulo Categorías (`/api/v1/categorias`)

#### `GET /api/v1/categorias`
- **Acceso:** Público
- **Response 200 OK:** Lista de categorías con soporte para jerarquía mediante `padreId`:
```json
[
  { "id": 1, "nombre": "Gasfitería", "descripcion": "Cañerías, tubos, llaves y válvulas", "padreId": null },
  { "id": 2, "nombre": "Cobre y Accesorios", "descripcion": "Tuberías y uniones de cobre", "padreId": 1 }
]
```

#### `POST /api/v1/categorias`
- **Acceso:** **ADMIN**
- **Request Body:** `{ "nombre": "Electricidad", "descripcion": "Cables y protecciones", "padreId": null }`
- **Response 201 Created:** `CategoriaResponseDTO`. (409 si el nombre ya existe).

#### `PUT /api/v1/categorias/{id}`
- **Acceso:** **ADMIN**
- **Request Body:** `{ "nombre": "Electricidad Domiciliaria", "descripcion": "...", "padreId": 5 }`
- **Response 200 OK:** `CategoriaResponseDTO`. (409 si genera un ciclo en la jerarquía).

#### `DELETE /api/v1/categorias/{id}`
- **Acceso:** **ADMIN**
- **Response:** 204 No Content. (409 si tiene productos asociados o subcategorías hijas).

---

### 4.4 Módulo Carrito Persistente (`/api/v1/carrito`)
*Requiere cabecera `Authorization: Bearer <TOKEN>` en todas sus operaciones.*

#### `GET /api/v1/carrito`
- **Response 200 OK:**
```json
{
  "items": [
    {
      "id": 101,
      "productoId": 12,
      "nombre": "Tubo Cobre 1/2 pulgada x 3m Tipo L",
      "precioUnitario": 12990.00,
      "cantidad": 2,
      "subtotal": 25980.00
    }
  ],
  "total": 25980.00
}
```

#### `POST /api/v1/carrito/items`
- **Request Body:** `{ "productoId": 12, "cantidad": 1 }`
- **Comportamiento:** Si el producto ya existía en el carrito, suma la cantidad de forma idempotente.
- **Response 200 OK:** `LineaCarritoResponseDTO`.

#### `PATCH /api/v1/carrito/items/{id}`
- **Request Body:** `{ "cantidad": 3 }`
- **Response 200 OK:** `LineaCarritoResponseDTO`.

#### `DELETE /api/v1/carrito/items/{id}`
- **Response:** 204 No Content.

#### `DELETE /api/v1/carrito`
- **Descripción:** Vacía todos los ítems del carrito del usuario.
- **Response:** 204 No Content.

---

### 4.5 Módulo Pedidos & Checkout (`/api/v1/pedidos`)
*Requiere cabecera `Authorization: Bearer <TOKEN>`.*

#### `POST /api/v1/pedidos/cotizar`
- **Descripción:** Calcula en el servidor el subtotal del carrito, costo de envío y total final antes de enviar el checkout.
- **Request Body:**
```json
{
  "metodoEntrega": "DESPACHO"
}
```
- **Valores posibles de `metodoEntrega`:** `"RETIRO"` o `"DESPACHO"`.
- **Response 200 OK:**
```json
{
  "subtotal": 25980.00,
  "costoDespacho": 3990.00,
  "total": 29970.00,
  "envioGratisDesde": 50000.00
}
```

#### `POST /api/v1/pedidos/desde-carrito`
- **Descripción:** Transforma el carrito del usuario en un pedido oficial, persiste datos del comprador y despacho, descuenta stock con control optimista, y **vacía el carrito de forma atómica en la misma transacción**.
- **Request Body (`DatosCheckoutDTO`):**
```json
{
  "nombres": "Carlos",
  "apellidos": "Santana Díaz",
  "rut": "12.345.678-5",
  "email": "carlos.santana@example.cl",
  "telefono": "+56912345678",
  "metodoEntrega": "DESPACHO",
  "region": "Metropolitana de Santiago",
  "comuna": "Providencia",
  "direccion": "Av. Nueva Providencia 1881",
  "depto": "Oficina 502",
  "referencias": "Entre Pedro de Valdivia y Marchant Pereira",
  "retiraTercero": false,
  "nombreTercero": null,
  "rutTercero": null
}
```
- **Regla de Validación:** Si `metodoEntrega == "DESPACHO"`, los campos `region`, `comuna` y `direccion` son obligatorios (retorna 400 si faltan).
- **Response 201 Created:** `PedidoResponseDTO`
```json
{
  "id": 48,
  "estado": "PENDIENTE",
  "total": 29970.00,
  "fechaCreacion": "2026-09-25T21:18:26",
  "items": [
    {
      "productoId": 12,
      "productoNombre": "Tubo Cobre 1/2 pulgada x 3m Tipo L",
      "cantidad": 2,
      "precioUnitario": 12990.00,
      "subtotal": 25980.00
    }
  ],
  "webpayToken": null,
  "estadoPago": "PENDIENTE",
  "subtotal": 25980.00,
  "costoDespacho": 3990.00,
  "metodoEntrega": "DESPACHO"
}
```

#### `GET /api/v1/pedidos`
- **Descripción:** Lista los pedidos del usuario autenticado ordenados del más reciente al más antiguo.
- **Response 200 OK:** Array `PedidoResponseDTO[]`.

#### `GET /api/v1/pedidos/{id}`
- **Descripción:** Detalle de un pedido. Un usuario no-admin solo puede consultar sus propios pedidos (404 si intenta ver el de otro).
- **Response 200 OK:** `PedidoResponseDTO`.

#### `PATCH /api/v1/pedidos/{id}/estado`
- **Acceso:** **ADMIN**
- **Request Body:** `{ "estado": "CONFIRMADO" }`
- **Response 200 OK:** `PedidoResponseDTO`.
- **Errores:** 409 Conflict si la transición viola la máquina de estados.

#### `POST /api/v1/pedidos/{id}/pagar`
- **Descripción:** Crea la transacción en Transbank Webpay Plus.
- **Response 200 OK:**
```json
{
  "token": "01ab98273645...",
  "url": "https://webpay3gint.transbank.cl/webpayserver/initTransaction"
}
```

---

## 5. Blueprint Arquitectónico para Frontend en Angular 22

### 5.1 Principios Fundamentales del Stack
1. **100% Standalone & Zoneless:** Sin `AppModule`, utilizando el modo reactivo zoneless de Angular (`provideExperimentalZonelessChangeDetection()`).
2. **Signals-First Reactivity:**
   - Cero subscripciones manuales con `subscribe()`.
   - Estado derivado sincronizado con `computed()`.
   - Side-effects controlados con `effect()`.
   - Peticiones declarativas mediante `httpResource()` o `resource()`.
   - Parámetros de componentes con `input()`, `model()` y eventos con `output()`.
3. **Control Flow Moderno:** `@if`, `@for (item of items(); track item.id)`, `@switch`, `@let`, y carga diferida con `@defer (on viewport; prefetch on idle)`.
4. **Clean / Screaming Architecture:** La estructura del proyecto expresa el dominio de Ferretería IA y sus flujos de usuario, separando la lógica de negocio del framework.

---

### 5.2 Estructura de Directorios del Proyecto Angular 22
```
src/app/
├── app.config.ts                 # Configuración zoneless, router, interceptors, i18n
├── app.routes.ts                 # Rutas de la SPA con lazy-loading
├── app.component.ts              # Shell principal (Header + RouterOutlet + CartDrawer)
├── core/                         # Infraestructura transversal (singleton)
│   ├── auth/
│   │   ├── auth.store.ts         # Signal Store de autenticación (usuario, token, rol)
│   │   ├── auth.guard.ts         # canActivate funcional para rutas privadas
│   │   └── admin.guard.ts        # canActivate funcional para rutas de administración
│   ├── http/
│   │   ├── auth.interceptor.ts   # Inyección de Bearer Token en cabeceras
│   │   └── error.interceptor.ts  # Mapeo y captura centralizada de ErrorResponse
│   ├── models/                   # Interfaces TypeScript espejo de los DTOs Java
│   │   ├── auth.models.ts
│   │   ├── product.models.ts
│   │   ├── category.models.ts
│   │   ├── cart.models.ts
│   │   ├── order.models.ts
│   │   ├── checkout.models.ts
│   │   └── ai.models.ts
│   └── services/                 # Clientes API HTTP puros
│       ├── product-api.service.ts
│       ├── category-api.service.ts
│       ├── cart-api.service.ts
│       ├── order-api.service.ts
│       └── ai-api.service.ts
├── shared/                       # Componentes presentacionales, directivas y pipes
│   ├── components/
│   │   ├── button/               # Botón brutalista técnico accesible
│   │   ├── badge/                # Badges de stock (DISPONIBLE, CRÍTICO, AGOTADO)
│   │   ├── modal/                # Dialog nativo / modal accesible
│   │   └── price-display/        # Formato CLP estricto ($12.990)
│   ├── directives/
│   │   └── typewriter.directive.ts # Efecto máquina de escribir para respuesta IA
│   ├── pipes/
│   │   ├── clp-currency.pipe.ts  # Formato moneda chilena sin decimales
│   │   └── rut-formatter.pipe.ts # Formateo dinámico de RUT (12.345.678-K)
│   └── utils/
│       └── rut-validator.ts      # Validación algorítmica de RUT chileno (módulo 11)
└── features/                     # Páginas de dominio (Smart Components)
    ├── home/                     # Landing + Hero IA + Grilla de Catálogo
    │   ├── components/
    │   │   ├── ai-diagnosis-hero/ # Asistente técnico interactivo
    │   │   ├── category-tabs/    # Selector de categorías
    │   │   └── product-card/     # Tarjeta de producto de taller
    │   └── home.component.ts
    ├── cart/                     # Drawer y detalle de carrito
    │   ├── cart.store.ts         # Signal Store del carrito persistente
    │   └── cart-drawer.component.ts
    ├── checkout/                 # Flujo de compra, cotización y pago
    │   ├── checkout.component.ts # Formulario reactivo chileno + cotizador
    │   └── checkout-resultado.component.ts # Retorno de Webpay
    ├── orders/                   # Mis compras / seguimiento
    │   ├── order-list.component.ts
    │   └── order-detail.component.ts
    └── admin/                    # Panel de administración (solo ROLE_ADMIN)
        ├── admin.component.ts
        ├── components/
        │   ├── product-manager/
        │   ├── category-manager/
        │   ├── order-flow-manager/
        │   └── ai-reindex-bar/   # Monitor de embeddings pgvector
```

---

### 5.3 Modelos TypeScript Espejo (100% Type-Safe)

#### `src/app/core/models/auth.models.ts`
```typescript
export type Rol = 'ROLE_ADMIN' | 'ROLE_CLIENTE';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  expiresIn: number;
}

export interface RegisterRequest {
  username: string;
  password: string;
  email?: string;
  rol?: string;
}

export interface UsuarioResponse {
  id: number;
  username: string;
  rol: Rol;
  email?: string;
}
```

#### `src/app/core/models/product.models.ts`
```typescript
export interface ProductoResponse {
  id: number;
  sku: string;
  nombre: string;
  descripcionTecnica: string;
  descripcionColoquial: string;
  precio: number;
  stock: number;
  categoriaId: number;
}

export interface ProductoRequest {
  sku: string;
  nombre: string;
  precio: number;
  stock: number;
  descripcionTecnica: string;
  descripcionColoquial: string;
  categoriaId: number;
}

export interface Page<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}
```

#### `src/app/core/models/ai.models.ts`
```typescript
import { ProductoResponse } from './product.models';

export interface SugerenciaFerreteria {
  palabrasClave: string[];
  herramientas: string[];
  repuestos: string[];
}

export interface BusquedaInteligenteResponse {
  sugerencia: SugerenciaFerreteria;
  productos: ProductoResponse[];
}

export interface DiagnoseRequest {
  problema: string;
}

export interface ReindexacionResponse {
  procesados: number;
  pendientes: number;
}
```

#### `src/app/core/models/order.models.ts`
```typescript
export type EstadoPedido = 'PENDIENTE' | 'CONFIRMADO' | 'ENVIADO' | 'ENTREGADO' | 'CANCELADO';
export type EstadoPago = 'PENDIENTE' | 'PAGADO' | 'FALLIDO';
export type MetodoEntrega = 'RETIRO' | 'DESPACHO';

export interface ItemPedidoResponse {
  productoId: number;
  productoNombre?: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
}

export interface PedidoResponse {
  id: number;
  estado: EstadoPedido;
  total: number;
  fechaCreacion: string;
  items: ItemPedidoResponse[];
  webpayToken?: string;
  estadoPago?: EstadoPago;
  subtotal: number;
  costoDespacho: number;
  metodoEntrega?: MetodoEntrega;
}

export interface CotizacionRequest {
  metodoEntrega: MetodoEntrega;
}

export interface CotizacionResponse {
  subtotal: number;
  costoDespacho: number;
  total: number;
  envioGratisDesde: number;
}

export interface DatosCheckout {
  nombres: string;
  apellidos: string;
  rut: string;
  email: string;
  telefono: string;
  metodoEntrega: MetodoEntrega;
  region?: string;
  comuna?: string;
  direccion?: string;
  depto?: string;
  referencias?: string;
  retiraTercero?: boolean;
  nombreTercero?: string;
  rutTercero?: string;
}
```

---

### 5.4 Implementación del Core en Angular 22

#### `src/app/app.config.ts`
```typescript
import { ApplicationConfig, provideExperimentalZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './core/http/auth.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideExperimentalZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding(), withViewTransitions()),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor, errorInterceptor]))
  ]
};
```

#### `src/app/core/http/auth.interceptor.ts`
```typescript
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthStore } from '../auth/auth.store';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authStore = inject(AuthStore);
  const token = authStore.token();

  if (token && !req.url.includes('/auth/login') && !req.url.includes('/auth/register')) {
    const cloned = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    return next(cloned);
  }

  return next(req);
};
```

#### `src/app/core/auth/auth.store.ts` (Signal Store)
```typescript
import { Injectable, computed, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { LoginRequest, LoginResponse, UsuarioResponse, Rol } from '../models/auth.models';
import { tap } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private http = inject(HttpClient);
  private readonly TOKEN_KEY = 'repara_token';
  private readonly USER_KEY = 'repara_user';

  // Signals de estado
  token = signal<string | null>(localStorage.getItem(this.TOKEN_KEY));
  currentUser = signal<UsuarioResponse | null>(
    JSON.parse(localStorage.getItem(this.USER_KEY) || 'null')
  );

  // Computeds reactivos
  isAuthenticated = computed(() => !!this.token());
  isAdmin = computed(() => this.currentUser()?.rol === 'ROLE_ADMIN');

  login(credentials: LoginRequest) {
    return this.http.post<LoginResponse>('/api/auth/login', credentials).pipe(
      tap(res => {
        this.token.set(res.token);
        localStorage.setItem(this.TOKEN_KEY, res.token);
        this.fetchProfile();
      })
    );
  }

  fetchProfile() {
    this.http.get<UsuarioResponse>('/api/v1/usuarios/me').subscribe({
      next: user => {
        this.currentUser.set(user);
        localStorage.setItem(this.USER_KEY, JSON.stringify(user));
      },
      error: () => this.logout()
    });
  }

  logout() {
    this.token.set(null);
    this.currentUser.set(null);
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
  }
}
```

#### `src/app/features/cart/cart.store.ts` (Signal Store de Carrito Persistente)
```typescript
import { Injectable, computed, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CarritoResponse, LineaCarritoResponse } from '../../core/models/cart.models';
import { AuthStore } from '../../core/auth/auth.store';

@Injectable({ providedIn: 'root' })
export class CartStore {
  private http = inject(HttpClient);
  private authStore = inject(AuthStore);

  cart = signal<CarritoResponse>({ items: [], total: 0 });
  isOpen = signal<boolean>(false);
  isLoading = signal<boolean>(false);

  // Computeds
  itemCount = computed(() => 
    this.cart().items.reduce((acc, item) => acc + item.cantidad, 0)
  );
  subtotal = computed(() => this.cart().total);

  cargarCarrito() {
    if (!this.authStore.isAuthenticated()) return;
    this.isLoading.set(true);
    this.http.get<CarritoResponse>('/api/v1/carrito').subscribe({
      next: data => {
        this.cart.set(data);
        this.isLoading.set(false);
      },
      error: () => this.isLoading.set(false)
    });
  }

  agregarProducto(productoId: number, cantidad = 1) {
    return this.http.post<LineaCarritoResponse>('/api/v1/carrito/items', { productoId, cantidad }).pipe(
      tap(() => {
        this.cargarCarrito();
        this.isOpen.set(true);
      })
    );
  }

  actualizarCantidad(lineaId: number, cantidad: number) {
    return this.http.patch<LineaCarritoResponse>(`/api/v1/carrito/items/${lineaId}`, { cantidad }).pipe(
      tap(() => this.cargarCarrito())
    );
  }

  eliminarLinea(lineaId: number) {
    return this.http.delete<void>(`/api/v1/carrito/items/${lineaId}`).pipe(
      tap(() => this.cargarCarrito())
    );
  }

  vaciar() {
    return this.http.delete<void>('/api/v1/carrito').pipe(
      tap(() => this.cart.set({ items: [], total: 0 }))
    );
  }

  toggleDrawer() {
    this.isOpen.update(open => !open);
  }
}
```

---

### 5.5 Flujo del Hero Asistente IA (`ai-diagnosis-hero.component.ts`)
Este componente ilustra el uso de `signal`, `@if`, y la directiva de typewriter para renderizar la respuesta del LLM con feedback de taller:

```typescript
import { Component, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BusquedaInteligenteResponse } from '../../core/models/ai.models';
import { CartStore } from '../cart/cart.store';
import { TypewriterDirective } from '../../shared/directives/typewriter.directive';
import { ClpCurrencyPipe } from '../../shared/pipes/clp-currency.pipe';

@Component({
  selector: 'app-ai-diagnosis-hero',
  standalone: true,
  imports: [TypewriterDirective, ClpCurrencyPipe],
  template: `
    <section class="ai-hero-box">
      <div class="header-tag">ASISTENCIA TÉCNICA DE FAENA // LLM + PGVECTOR</div>
      <h2>Describe tu falla o proyecto en lenguaje cotidiano</h2>
      
      <div class="search-input-wrapper">
        <input 
          type="text" 
          #promptInput
          placeholder="Ej: Tengo una fuga en una cañería de cobre bajo el lavaplatos..."
          (keydown.enter)="diagnosticar(promptInput.value)"
        />
        <button [disabled]="loading()" (click)="diagnosticar(promptInput.value)">
          @if (loading()) { ANALIZANDO CON IA... } @else { DIAGNOSTICAR }
        </button>
      </div>

      @if (diagnosis(); as data) {
        <div class="results-grid">
          <!-- Columna Izquierda: Diagnóstico Estructurado -->
          <div class="diagnosis-card">
            <h3>HERRAMIENTAS RECOMENDADAS</h3>
            <ul class="tag-list">
              @for (h of data.sugerencia.herramientas; track h) {
                <li class="tool-tag">{{ h }}</li>
              }
            </ul>

            <h3>REPUESTOS Y MATERIALES</h3>
            <ul class="tag-list">
              @for (r of data.sugerencia.repuestos; track r) {
                <li class="part-tag">{{ r }}</li>
              }
            </ul>
          </div>

          <!-- Columna Derecha: Productos Compatibles en Inventario -->
          <div class="products-card">
            <h3>PRODUCTOS DE CATÁLOGO DISPONIBLES ({{ data.productos.length }})</h3>
            <div class="product-cards-list">
              @for (prod of data.productos; track prod.id) {
                <div class="mini-product-item">
                  <div class="info">
                    <span class="sku">{{ prod.sku }}</span>
                    <strong>{{ prod.nombre }}</strong>
                    <span class="price">{{ prod.precio | clpCurrency }}</span>
                  </div>
                  <button (click)="cartStore.agregarProducto(prod.id, 1).subscribe()">
                    AGREGAR
                  </button>
                </div>
              }
            </div>
          </div>
        </div>
      }
    </section>
  `
})
export class AiDiagnosisHeroComponent {
  private http = inject(HttpClient);
  cartStore = inject(CartStore);

  loading = signal(false);
  diagnosis = signal<BusquedaInteligenteResponse | null>(null);

  diagnosticar(query: string) {
    if (!query.trim()) return;
    this.loading.set(true);
    this.http.get<BusquedaInteligenteResponse>(`/api/v1/productos/asistente?q=${encodeURIComponent(query)}`).subscribe({
      next: res => {
        this.diagnosis.set(res);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }
}
```

---

### 5.6 Flujo de Checkout y Retorno de Webpay (`checkout.component.ts`)

```typescript
import { Component, signal, computed, inject, OnInit } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { CartStore } from '../cart/cart.store';
import { CotizacionResponse, MetodoEntrega, CheckoutResponse } from '../../core/models/order.models';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './checkout.component.html'
})
export class CheckoutComponent implements OnInit {
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);
  cartStore = inject(CartStore);

  cotizacion = signal<CotizacionResponse | null>(null);
  procesando = signal(false);

  form = this.fb.group({
    nombres: ['', [Validators.required, Validators.maxLength(70)]],
    apellidos: ['', [Validators.required, Validators.maxLength(70)]],
    rut: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    telefono: ['', [Validators.required]],
    metodoEntrega: ['DESPACHO' as MetodoEntrega, [Validators.required]],
    region: ['Metropolitana de Santiago'],
    comuna: ['Santiago'],
    direccion: [''],
    depto: [''],
    referencias: [''],
    retiraTercero: [false],
    nombreTercero: [''],
    rutTercero: ['']
  });

  ngOnInit() {
    this.actualizarCotizacion(this.form.value.metodoEntrega as MetodoEntrega);
    this.form.get('metodoEntrega')?.valueChanges.subscribe(metodo => {
      if (metodo) this.actualizarCotizacion(metodo as MetodoEntrega);
    });
  }

  actualizarCotizacion(metodo: MetodoEntrega) {
    this.http.post<CotizacionResponse>('/api/v1/pedidos/cotizar', { metodoEntrega: metodo }).subscribe({
      next: res => this.cotizacion.set(res)
    });
  }

  confirmarYPagar() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.procesando.set(true);

    // 1. Crear Pedido desde el carrito con los datos del comprador
    this.http.post<any>('/api/v1/pedidos/desde-carrito', this.form.value).subscribe({
      next: pedido => {
        // 2. Iniciar flujo Webpay Plus
        this.http.post<CheckoutResponse>(`/api/v1/pedidos/${pedido.id}/pagar`, {}).subscribe({
          next: webpay => {
            // 3. Redirección automática POST al portal de Transbank
            const form = document.createElement('form');
            form.method = 'POST';
            form.action = webpay.url;

            const tokenInput = document.createElement('input');
            tokenInput.type = 'hidden';
            tokenInput.name = 'token_ws';
            tokenInput.value = webpay.token;

            form.appendChild(tokenInput);
            document.body.appendChild(form);
            form.submit();
          },
          error: () => this.procesando.set(false)
        });
      },
      error: () => this.procesando.set(false)
    });
  }
}
```

---

## 6. Guía de Arranque y Configuración del Proxy

Para evitar problemas de CORS durante el desarrollo y consumir el contexto `/api` limpiamente:

### `proxy.conf.json`
```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true,
    "logLevel": "debug"
  }
}
```

En `angular.json`:
```json
"serve": {
  "builder": "@angular/build:dev-server",
  "options": {
    "proxyConfig": "proxy.conf.json"
  }
}
```

---

## 7. Checklist de Calidad para el Frontend
- [ ] **No tocar `zone.js`:** La aplicación compila y opera completamente con Zoneless change detection.
- [ ] **RUT Chileno:** Formateo y validación de módulo 11 en tiempo real en los inputs de checkout.
- [ ] **Manejo de 409 Conflict:** Si el usuario intenta pagar un producto agotado por concurrencia, la UI muestra un toast claro de stock insuficiente y refresca el carrito sin crashear.
- [ ] **Debounce en Búsqueda:** El buscador por similitud (`/api/v1/productos/buscar`) utiliza `toSignal` con `debounceTime(300)` o señales vinculadas para no saturar pgvector.
- [ ] **Estado Persistente del Carrito:** El total y la cantidad de ítems se actualizan en el Header de forma reactiva a través del `CartStore`.
