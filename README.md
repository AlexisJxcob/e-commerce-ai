# 🛠️ E-Commerce AI — Ferretería Inteligente

Backend REST de un e-commerce de **ferretería impulsado por IA**. El cliente
describe un problema en lenguaje coloquial (*"tengo una fuga en una tubería de
PVC"*) y la API, gracias a un LLM servido por **Hugging Face**, lo traduce a
términos técnicos, herramientas y repuestos, y busca productos en el catálogo
usando **PostgreSQL + pgvector** (búsqueda por similitud de vectores) y
coincidencias por palabras clave.

La base de datos de producción es **Neon Postgres serverless** (región
`aws-sa-east-1`, São Paulo), con conexión *pooled* para la aplicación y
*directa* para las migraciones.

## 🚀 Características

- **Búsqueda inteligente por IA**: el LLM (`HuggingFaceChatService`) analiza la
  consulta y devuelve una sugerencia estructurada (palabras clave,
  herramientas, repuestos) que se cruza con el inventario.
- **Búsqueda vectorial**: embeddings de 384 dimensiones generados contra la
  *Inference API* de Hugging Face y almacenados en pgvector (operador `<=>`,
  distancia coseno).
- **CRUD completo de productos** con validación y control de stock con bloqueo
  optimista (conflicto de concurrencia → HTTP 409).
- **Dominio completo**: categorías con jerarquía, carrito persistente, pedidos
  con máquina de estados, usuarios con roles `ADMIN` / `CLIENTE`.
- **API segura**: autenticación **JWT (HS256)** con login propio; el rol sale
  siempre del token firmado.
- **Documentación OpenAPI** integrada con SpringDoc (Swagger UI).
- **Esquema versionado con Flyway** (`ddl-auto=validate` en todos los perfiles).
- **Manejo global de errores**: respuestas uniformes
  (`timestamp`, `status`, `message`, `fieldErrors`).

## 🧰 Stack Tecnológico

![Java](https://img.shields.io/badge/Java-21-007396?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?style=flat&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-JWT-6DB33F?style=flat&logo=springsecurity&logoColor=white)
![Neon](https://img.shields.io/badge/Neon-Postgres%20serverless-00E599?style=flat&logo=postgresql&logoColor=white)
![pgvector](https://img.shields.io/badge/pgvector-0.8.6-4169E1?style=flat&logo=postgresql&logoColor=white)
![Hugging Face](https://img.shields.io/badge/Hugging%20Face-chat%20%2B%20embeddings-FFD21E?style=flat&logo=huggingface&logoColor=black)
![Maven](https://img.shields.io/badge/Maven-3.9.16-C71A36?style=flat&logo=apachemaven&logoColor=white)
![SpringDoc](https://img.shields.io/badge/SpringDoc-OpenAPI-85EA2D?style=flat)

| Tecnología | Uso |
|---|---|
| **Java 21** | Lenguaje y runtime |
| **Spring Boot 4.1.1** | Framework (starters modulares `webmvc`, `restclient`, `data-jpa`) |
| **Spring AI 2.0.1** | Contrato `EmbeddingModel` (implementación propia contra Hugging Face) |
| **Hugging Face** | Chat completions (asistente) y embeddings (búsqueda vectorial) |
| **Neon Postgres serverless** | Base de datos gestionada (pgvector incluido) |
| **pgvector** | Columna `vector(384)` y operador `<=>` |
| **Flyway 12** | Migraciones versionadas, única fuente de verdad del esquema |
| **Spring Security + JWT** | Autenticación stateless HS256 (`/api/auth/login`) |
| **SpringDoc OpenAPI 3.1.0** | Swagger UI / OpenAPI |
| **Lombok** | Reducción de boilerplate en entidades y `@ConfigurationProperties` |
| **Maven Wrapper 3.9.16** | Build reproducible (`./mvnw`) |

## 📋 Requisitos Previos

- **JDK 21** (p. ej. [Temurin](https://adoptium.net/))
- **PostgreSQL con pgvector**:
  - **Neon Postgres serverless** (producción; el plan gratuito incluye pgvector), o
  - un Postgres local con la extensión disponible
    (`docker run -d --name postgres-vector -p 5432:5432 -e POSTGRES_PASSWORD=postgres pgvector/pgvector:pg16`).
- **Maven 3.9+** (opcional si usas el wrapper `./mvnw`)
- Una **API key de Hugging Face** (gratuita en
  [huggingface.co](https://huggingface.co/settings/tokens)) para el chat del
  asistente y para los embeddings
- **Docker** (sólo para la suite de integración con Testcontainers)

## ☁️ Neon Postgres serverless (São Paulo)

Neon entrega **dos connection strings para la misma base de datos** y el
proyecto usa una para cada cosa:

| Variable | Endpoint | Para qué |
|---|---|---|
| `DATABASE_URL` | host con sufijo **`-pooler`** | Tráfico de la aplicación (HikariCP). PgBouncer en *modo transacción* multiplexa las conexiones, que es lo que necesita una arquitectura serverless |
| `DATABASE_DIRECT_URL` | host **sin** `-pooler` | Migraciones **Flyway**, `pg_dump`/`pg_restore` y tareas de administración. PgBouncer en modo transacción no soporta el estado de sesión (advisory locks, DDL) |

Reglas verificadas en este proyecto:

- **`?sslmode=require` es obligatorio** en ambas URLs: Neon rechaza conexiones
  sin TLS (`connection is insecure (try using 'sslmode=require')`). Se añade
  `&channel_binding=require` como recomienda el propio panel de Neon.
- Las credenciales **no** pueden ir embebidas en la URL
  (`postgresql://user:pass@host` falla en pgjdbc con *invalid port number*):
  viajan en `DATABASE_USER` / `DATABASE_PASSWORD`.
- **Flyway apunta siempre al endpoint directo** (`spring.flyway.url`), y declara
  `spring.flyway.user` / `spring.flyway.password` **explícitamente**: en cuanto
  se define `spring.flyway.url`, Spring Boot construye un `DataSource`
  exclusivo para Flyway y deja de heredar las credenciales del pool (sin esas
  dos propiedades el arranque falla con
  `The server requested SCRAM-based authentication...` — SQLState `08004`).
- **HikariCP afinado para serverless** (`application.properties`):

  | Propiedad | Valor | Motivo |
  |---|---|---|
  | `maximum-pool-size` | `10` | En serverless manda el número de instancias; el pooler multiplexa |
  | `minimum-idle` | `2` | Mantiene conexiones calientes sin desperdiciar cupo |
  | `idle-timeout` | `300000` | Recicla conexiones ociosas antes del corte del compute |
  | `max-lifetime` | `600000` | Evita conexiones *zombie* tras un corte del pooler |
  | `connection-timeout` | `20000` | Margen para el *cold start* del compute |
  | `keepalive-time` | `60000` | Detecta antes una conexión muerta |
  | `initialization-fail-timeout` | `-1` | **Crítico**: con el valor por defecto (`1`) el arranque falla si el compute está suspendido; con `-1` el pool arranca y obtiene la conexión de forma perezosa |

- **Scale-to-zero**: el compute se suspende tras unos minutos de inactividad y
  la primera consulta paga un *cold start* (cientos de ms). El pool reconecta
  solo; ninguna petición debe devolver 500 por este motivo.
- **Rollback de conectividad**: procedimiento y tiempos medidos en
  [`docs/rollback-conectividad-neon.md`](docs/rollback-conectividad-neon.md).

### Desarrollo local (motor anterior)

El contenedor `postgres-vector` (`pgvector/pgvector:pg16` o superior) sigue
sirviendo como motor local: basta apuntar `DATABASE_URL` /
`DATABASE_DIRECT_URL` a
`jdbc:postgresql://localhost:5432/ecommerce_db?sslmode=disable` con
`DATABASE_USER=postgres`. **No se toca código** para conmutar de motor.

## ⚙️ Configuración

### 1. Base de datos (PostgreSQL + pgvector)

```sql
CREATE DATABASE ecommerce_db;
\c ecommerce_db
CREATE EXTENSION IF NOT EXISTS vector;
```

> El esquema está versionado con **Flyway** (migraciones en
> `src/main/resources/db/migration`) y Hibernate usa `ddl-auto=validate`.
> La columna `embedding` es `vector(384)` — debe coincidir con
> `HuggingFaceEmbeddingModel.DIMENSION` y con el modelo configurado
> (`sentence-transformers/all-MiniLM-L6-v2`).

> **Importante**: la migración `V1` crea la extensión con
> `CREATE EXTENSION IF NOT EXISTS vector;` si el rol tiene privilegio `CREATE`
> (en Neon lo tiene el rol propietario). Si no, créala antes de migrar.

### 🗄️ Migraciones de esquema (Flyway)

Convención de versionado y reglas de uso (detalle en `docs/`):

- **Ubicación**: `src/main/resources/db/migration/`.
- **Formato de nombre**: secuencia entera correlativa
  `V{N}__{descripcion_en_snake_case}.sql` (ej. `V1__baseline_esquema_inicial.sql`).
  No se usa sufijo de timestamp: la secuencia entera es suficiente y legible en PRs.
- **Inmutabilidad**: una migración **mergeada no se edita jamás** (ni su
  checksum se altera). Todo cambio de esquema posterior es una migración nueva
  con el siguiente número de versión. Si necesitas "corregir" un baseline ya
  aplicado, se hace con `V{N+1}` (`ALTER`, *backfill*).
- **Extensiones**: `V1` crea pgvector; las columnas `vector(N)` sólo pueden
  existir si la extensión está creada antes de la tabla que las usa.
- **Bases existentes**: `spring.flyway.baseline-on-migrate=true` +
  `baseline-version=1` marcan la base como "equivalente a V1" (baseline) en el
  primer arranque: Flyway **no ejecuta** `V1` ni borra datos; desde ahí sólo
  aplica migraciones nuevas. Si la base diverge del esquema esperado,
  `ddl-auto=validate` falla con `SchemaManagementException`: corrige con una
  migración nueva, nunca editando `V1`.
- **Nuevas bases**: Flyway ejecuta `V1` desde cero (esquema completo +
  extensión), que es exactamente lo que verifican los tests de integración.
- **Neon**: aplicar migraciones siempre por el endpoint **directo**
  (`DATABASE_DIRECT_URL`); ninguna migración usa `CREATE INDEX CONCURRENTLY`
  ni índices `ivfflat`/`hnsw`, así que no hace falta `spring.flyway.mixed=true`.
- **Plan de pruebas en staging**:
  1. `./mvnw test`: los tests de integración arrancan una base vacía
     (Testcontainers o rama Neon) y verifican que Flyway crea la extensión
     `vector` y todas las tablas desde cero, sin `SchemaManagementException`.
  2. Conectar la app a un clon de staging **con datos** y verificar el arranque
     con `baseline-on-migrate=true` (baseline en V1, datos intactos).
  3. Verificar en el log de arranque que no hay advertencias de desalineación
     entidad ↔ esquema con `ddl-auto=validate`.

### 2. Variables de entorno

```bash
# Base de datos — Neon Postgres serverless (ver .env.example)
export DATABASE_URL="jdbc:postgresql://<endpoint>-pooler.sa-east-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require"
export DATABASE_DIRECT_URL="jdbc:postgresql://<endpoint>.sa-east-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require"
export DATABASE_USER="neondb_owner"
export DATABASE_PASSWORD="<password-de-neon>"

# JWT Security (OBLIGATORIA: >= 256 bits / 32 caracteres; fail-fast si falta)
export JWT_SECRET="$(openssl rand -hex 32)"
export JWT_EXPIRATION_MS=86400000
export JWT_ISSUER=""            # opcional: si se define, valida el claim 'iss'

# Hugging Face (embeddings + chat)
export HUGGINGFACE_API_KEY="hf_<tu_api_key>"

# Opcional
export HUGGINGFACE_CHAT_API_KEY=""                                        # si difiere de la anterior
export HUGGINGFACE_EMBEDDING_MODEL="sentence-transformers/all-MiniLM-L6-v2"
export HUGGINGFACE_CHAT_MODEL="Meta-Llama/Llama-3.2-3B-Instruct"
export ADMIN_PASSWORD="<password-del-admin-inicial>"
export CORS_ORIGINS="http://localhost:3001,http://localhost:3000"
```

> ⚠️ **Nunca** comitees API keys ni contraseñas. El repositorio ya ignora
> `.env`, `.env.local` y `application-local.properties|yml`.
> Usa `.env.example` como plantilla.

### 3. Propiedades relevantes (`src/main/resources/application.properties`)

```properties
# Base de datos — sin valores por defecto: fail-fast si faltan
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USER}
spring.datasource.password=${DATABASE_PASSWORD}
spring.datasource.hikari.maximum-pool-size=${DB_POOL_SIZE:10}
spring.datasource.hikari.initialization-fail-timeout=${DB_INITIALIZATION_FAIL_TIMEOUT:-1}

# JPA (el esquema lo gobierna Flyway)
spring.jpa.hibernate.ddl-auto=${JPA_DDL_AUTO:validate}

# Flyway — endpoint directo + credenciales explícitas
spring.flyway.url=${DATABASE_DIRECT_URL:${DATABASE_URL}}
spring.flyway.user=${DATABASE_USER}
spring.flyway.password=${DATABASE_PASSWORD}
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1

# JWT Security
app.jwt.secret=${JWT_SECRET}
app.jwt.expiration=${JWT_EXPIRATION_MS:86400000}
app.jwt.issuer=${JWT_ISSUER:}

# Hugging Face — Embeddings
huggingface.api.key=${HUGGINGFACE_API_KEY}
huggingface.api.model=${HUGGINGFACE_EMBEDDING_MODEL:sentence-transformers/all-MiniLM-L6-v2}
huggingface.api.base-url=${HUGGINGFACE_EMBEDDING_BASE_URL:https://router.huggingface.co/hf-inference/models}

# Hugging Face — Chat
huggingface.chat.key=${HUGGINGFACE_CHAT_API_KEY:${HUGGINGFACE_API_KEY}}
huggingface.chat.model=${HUGGINGFACE_CHAT_MODEL:Meta-Llama/Llama-3.2-3B-Instruct}
huggingface.chat.base-url=${HUGGINGFACE_CHAT_BASE_URL:https://router.huggingface.co/v1}

# Server
server.port=${SERVER_PORT:8080}
server.servlet.context-path=/api
```

> El **perfil `prod`** (`application-prod.properties`) sigue leyendo
> `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` en lugar de las variables
> `DATABASE_*`; el perfil por defecto (el que usa este README) es el de
> `application.properties` con `DATABASE_URL` / `DATABASE_DIRECT_URL`.

## ▶️ Puesta en Marcha

```bash
# 1. Clona el repositorio
git clone <TU_URL_DEL_REPO>
cd e-commerce-ai

# 2. Configura las variables de entorno (sección anterior)
set -a; . ./.env; set +a
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# 3. Arranca la aplicación (descarga Maven automáticamente)
./mvnw spring-boot:run
# En Windows: .\mvnw.cmd spring-boot:run
```

La API queda disponible en **http://localhost:8080/api**.

### Verificación rápida

```bash
# Listado paginado de productos (público; por defecto 20 por página)
curl "http://localhost:8080/api/v1/productos?page=0&size=20"

# Búsqueda vectorial (público; requiere HUGGINGFACE_API_KEY)
curl "http://localhost:8080/api/v1/productos/buscar?q=pegamento%20para%20pvc&limite=5"

# Asistente IA (público; requiere HUGGINGFACE_API_KEY — chat)
curl "http://localhost:8080/api/v1/productos/asistente?q=tengo%20una%20fuga%20en%20una%20tuber%C3%ADa%20de%20PVC"

# Login (emite el JWT)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<ADMIN_PASSWORD>"}'
```

## 📖 Documentación de la API (Swagger UI / OpenAPI)

Con SpringDoc integrado y `server.servlet.context-path=/api`, la documentación
interactiva está en:

| Recurso | URL |
|---|---|
| Swagger UI | `http://localhost:8080/api/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/api/api-docs` |

> `/swagger-ui/**`, `/api-docs/**` y `/swagger-ui.html` están en `permitAll()`,
> así que se acceden sin token.

### Endpoints principales — `/api/v1/productos`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/productos?page=&size=` | Público | Listado **paginado** (`Page`, 20 por defecto) |
| `GET` | `/api/v1/productos/{id}` | Público | Obtiene un producto por id |
| `GET` | `/api/v1/productos/buscar?q=…&limite=5` | Público | Búsqueda por similitud vectorial (pgvector) |
| `GET` | `/api/v1/productos/asistente?q=…` | Público | Recomendación IA (Hugging Face chat + palabras clave) |
| `POST` | `/api/v1/productos/diagnose` | `ADMIN` | Body `{ "problema": "…" }` → recomendación IA |
| `POST` | `/api/v1/productos/reindexar` | `ADMIN` | Genera los embeddings pendientes → `{procesados, pendientes}` |
| `POST` | `/api/v1/productos` | `ADMIN` | Crea un producto |
| `PUT` | `/api/v1/productos/{id}` | `ADMIN` | Actualiza un producto |
| `PATCH` | `/api/v1/productos/{id}/stock?stock=0` | `ADMIN` | Actualiza solo el stock (no negativo) |
| `DELETE` | `/api/v1/productos/{id}` | `ADMIN` | Elimina un producto (204) |

El resto del dominio (`/api/v1/categorias`, `/api/v1/carrito`,
`/api/v1/pedidos`, `/api/v1/usuarios`, `/api/auth`) está documentado en Swagger
UI.

### Ejemplo de creación de producto (requiere rol ADMIN)

```bash
curl -X POST http://localhost:8080/api/v1/productos \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TU_JWT>" \
  -d '{
    "sku": "CINTA-TEFLON-12",
    "nombre": "Cinta de teflón 12 m",
    "precio": 15.50,
    "stock": 100,
    "descripcionTecnica": "Cinta selladora de roscas de 12 metros, 1/2 pulgada",
    "descripcionColoquial": "la cinta blanca para que no gotee la llave de agua"
  }'
```

### Formato de error uniforme

```json
{
  "timestamp": "2025-08-30T12:00:00.000",
  "status": 404,
  "message": "Producto no encontrado con id: 999",
  "fieldErrors": null
}
```

## 🔐 Autenticación (JWT)

- **Login propio**: `POST /api/auth/login` (`{"username","password"}`) valida
  contra la tabla `usuarios` (BCrypt) y devuelve
  `{"token","username","expiresIn"}`. El **rol no viaja en la respuesta**: va
  dentro del JWT en el claim `roles` (`["ROLE_ADMIN"]`, `["ROLE_CLIENTE"]`).
  Credenciales inválidas → **401** con mensaje genérico.
- **Registro público**: `POST /api/auth/register` fuerza el rol `CLIENTE`
  aunque el payload pida otro → **201** con `UsuarioResponseDTO`
  (`id`, `username`, `rol`, `email`; nunca `password`).
- **Uso**: la API espera `Authorization: Bearer <token>`.
  `JwtAuthenticationFilter` lo decodifica (HS256) y toma las autoridades del
  claim `roles`; **nunca** de cabeceras ni parámetros del cliente.
- **Secreto**: `app.jwt.secret` (`JWT_SECRET`) es **obligatorio** y debe tener
  ≥ 256 bits (32 caracteres UTF-8); si falta o es corto, la aplicación **no
  arranca** (`JwtProperties` fail-fast). No hay secretos hardcodeados.
- **Admin inicial**: `AdminSeeder` crea `admin` (rol `ADMIN`) en el primer
  arranque si no existe, con `app.seed.admin-password`
  (`${ADMIN_PASSWORD:admin123}`).

## 🤗 API keys de Hugging Face (chat y embeddings)

Un único proveedor para ambas cosas:

| Uso | Propiedad | Modelo por defecto | Dimensión |
|---|---|---|---|
| Embeddings (búsqueda vectorial) | `huggingface.api.*` | `sentence-transformers/all-MiniLM-L6-v2` | **384** |
| Chat (asistente IA) | `huggingface.chat.*` | `Meta-Llama/Llama-3.2-3B-Instruct` | — |

1. Crea una cuenta en [huggingface.co](https://huggingface.co) y genera un
   *access token* en [Settings → Tokens](https://huggingface.co/settings/tokens).
2. Defínelo en tu entorno:

   ```bash
   export HUGGINGFACE_API_KEY="hf_TU_API_KEY_AQUI"
   ```

3. Ambas integraciones usan esa misma key por defecto
   (`huggingface.chat.key=${HUGGINGFACE_CHAT_API_KEY:${HUGGINGFACE_API_KEY}}`).
   El modelo de chat se cambia con `HUGGINGFACE_CHAT_MODEL` (por ejemplo
   `mistralai/Mistral-7B-Instruct-v0.3`) **sin tocar código**; el de embeddings,
   con `HUGGINGFACE_EMBEDDING_MODEL` — si cambias su dimensión, actualiza
   también `vector(N)` en `Producto.java` y `HuggingFaceEmbeddingModel.DIMENSION`.

**Errores del servicio de IA** (mapeados en `GlobalExceptionHandler`):

- **429** si se supera el rate limit de Hugging Face (`HuggingFaceRateLimitException`).
- **502** si el modelo no responde, falla la conexión o devuelve algo que no es
  el JSON esperado (`HuggingFaceException`).
- **401** si la API key es inválida o falta.

## 🧪 Tests

```bash
./mvnw test
```

- La suite usa **PostgreSQL real con pgvector** (nunca base en memoria):
  Testcontainers con `pgvector/pgvector:pg16`, o una **rama Neon de pruebas** si
  defines `NEON_TEST_DATABASE_URL` / `NEON_TEST_DATABASE_DIRECT_URL`
  (`NEON_TEST_DATABASE_USER`, `NEON_TEST_DATABASE_PASSWORD`). Usa siempre una
  rama desechable: la suite escribe y borra datos.
- Incluye `@DataJpaTest` con **conteo de sentencias Hibernate** (anti N+1),
  tests de concurrencia HTTP y verificación de que el rol no se puede escalar
  desde cabeceras/parámetros.
- El gate es `@EnabledIf("…EntornoIntegracion#disponible")`: sin Docker **y** sin
  rama Neon configurada, los tests de integración se desactivan en lugar de
  fallar.

## 🗂️ Estructura del Proyecto

```
src/main/java/org/alexis/ecommerceai/
├── ECommerceAiApplication.java      # Punto de entrada (@SpringBootApplication)
├── ai/                              # AsistenteIA + cliente de chat de Hugging Face
├── config/                          # Security, CORS, JWT, Hikari/Flyway, Actuator, Hugging Face y caché
├── controller/                      # REST: productos, categorías, carrito, pedidos, usuarios, auth
├── dto/                             # Records de request/response (+ dto/huggingface/)
├── exception/                       # Errores de dominio y manejo global (@RestControllerAdvice)
├── model/                           # Entidades JPA (Producto incl. columna vector(384), Categoria, …)
├── repository/                      # JPA + consulta nativa de similitud vectorial
├── security/                        # Filtro JWT personalizado
└── service/                         # Lógica de negocio
docs/
└── rollback-conectividad-neon.md    # Runbook de rollback Neon → motor anterior
```

## ⚠️ Notas Importantes

- **Dimensión del vector**: `vector(384)` coincide con
  `sentence-transformers/all-MiniLM-L6-v2` (`HuggingFaceEmbeddingModel.DIMENSION`).
  Si cambias de modelo de embeddings, ajusta los tres sitios a la vez
  (`Producto.java`, `HuggingFaceEmbeddingModel`, `huggingface.api.model`).
- **Los embeddings los genera Hugging Face**: crear/actualizar productos, la
  búsqueda vectorial y `POST /api/v1/productos/reindexar` requieren
  `HUGGINGFACE_API_KEY`. El asistente (`/asistente`, `/diagnose`) usa el chat
  del mismo proveedor.
- **Spring Boot 4 / Jackson 3**: el proyecto usa los starters modulares nuevos y
  `tools.jackson.*` (Jackson 3). No "corrijas" esos imports a `com.fasterxml.*`:
  romperías la compilación.
- **CORS**: configuración global en `CorsConfig` desde `app.cors.allowed-origins`
  (por defecto `http://localhost:3001,http://localhost:3000`). No hay
  `@CrossOrigin` en los controladores.
- **Context path**: `server.servlet.context-path=/api`, así que las reglas de
  `SecurityConfig` se evalúan **sin** el prefijo `/api`.
- **Esquema versionado con Flyway**: `ddl-auto=validate` en todos los entornos;
  las migraciones en `src/main/resources/db/migration` son la única fuente de
  verdad del esquema.
- **Datos en producción (Neon)**: 23 productos, 9 categorías y 23 embeddings de
  384 dimensiones migrados desde el motor anterior, con paridad verificada
  (conteos, hashes de datos y top-5 de búsqueda vectorial idénticos).

## 📄 Licencia

No se ha definido una licencia para este proyecto. Si planeas publicarlo,
agrega un archivo `LICENSE` (p. ej. MIT).
