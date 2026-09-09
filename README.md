# 🛠️ E-Commerce AI — Ferretería Inteligente

Backend REST de un e-commerce de **ferretería impulsado por IA**. El cliente
describe un problema en lenguaje coloquial (*"tengo una fuga en una tubería de
PVC"*) y la API, gracias a un LLM vía **Groq**, lo traduce a términos
técnicos, herramientas y repuestos, y busca productos en el catálogo usando
**PostgreSQL + pgvector** (búsqueda por similitud de vectores) y coincidencias
por palabras clave.

## 🚀 Características

- **Búsqueda inteligente por IA**: el LLM analiza la consulta del usuario y
  devuelve una sugerencia estructurada (palabras clave, herramientas, repuestos)
  que se cruza con el inventario.
- **Búsqueda vectorial**: embeddings generados con Spring AI y almacenados en
  PostgreSQL con la extensión **pgvector** (operador `<=>`, distancia coseno).
- **CRUD completo de productos** con validación de datos y control de stock
  con bloqueo optimista (conflicto de concurrencia → HTTP 409).
- **API segura**: endpoints de administración protegidos con **JWT (HS256)**.
- **Documentación OpenAPI** integrada con SpringDoc (Swagger UI).
- **Manejo global de errores**: respuestas de error uniformes
  (`timestamp`, `status`, `message`, `fieldErrors`).

## 🧰 Stack Tecnológico

![Java](https://img.shields.io/badge/Java-21-007396?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?style=flat&logo=springboot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.1-6DB33F?style=flat&logo=spring&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-JWT-6DB33F?style=flat&logo=springsecurity&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-4169E1?style=flat&logo=postgresql&logoColor=white)
![OpenRouter](https://img.shields.io/badge/OpenRouter-Embeddings-ff6b35?style=flat)
![Groq](https://img.shields.io/badge/Groq-Chat-f55036?style=flat&logo=groq&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.9.16-C71A36?style=flat&logo=apachemaven&logoColor=white)
![SpringDoc](https://img.shields.io/badge/SpringDoc-OpenAPI-85EA2D?style=flat)

| Tecnología | Uso |
|---|---|
| **Java 21** | Lenguaje y runtime |
| **Spring Boot 4.1.1** | Framework (starters modulares `webmvc`, `restclient`, `data-jpa`) |
| **Spring AI 2.0.1** | `EmbeddingModel` para generar/consultar vectores |
| **Hugging Face** | LLM (chat completions) y embeddings para la búsqueda vectorial |
| **PostgreSQL + pgvector** | Persistencia y búsqueda por similitud vectorial |
| **Spring Security + JWT** | Autenticación stateless con tokens HS256 |
| **SpringDoc OpenAPI 3.1.0** | Swagger UI / OpenAPI |
| **Lombok** | Reducción de código boilerplate |
| **Maven Wrapper 3.9.16** | Build reproducible (`./mvnw`) |

## 📋 Requisitos Previos

- **JDK 21** (p. ej. [Temurin](https://adoptium.net/))
- **PostgreSQL 14+** con la extensión **pgvector** instalada
  ([guía oficial](https://github.com/pgvector/pgvector))
- **Maven 3.9+** (opcional si usas el wrapper `./mvnw`)
- Una **API key de Hugging Face** (gratuita en [huggingface.co](https://huggingface.co))
  para el chat del asistente y los embeddings

## ⚙️ Configuración

### 1. Base de datos (PostgreSQL + pgvector)

```sql
CREATE DATABASE ecommerce_db;
\c ecommerce_db
CREATE EXTENSION IF NOT EXISTS vector;
```

> El esquema está versionado con **Flyway** (migraciones en
> `src/main/resources/db/migration`) y Hibernate usa `ddl-auto=validate`.
> La columna `embedding` es de tipo `vector(384)` — debe coincidir con la
> dimensión del modelo de embeddings utilizado
> (`sentence-transformers/all-MiniLM-L6-v2`).

> **Importante**: Asegúrate de que la extensión pgvector esté instalada en tu base de datos
> antes de iniciar la aplicación (la migración `V1` la crea con
> `CREATE EXTENSION IF NOT EXISTS vector;` si el usuario tiene privilegio `CREATE`).

### 🗄️ Migraciones de esquema (Flyway)

Convención de versionado y reglas de uso (detalle en `docs/`):

- **Ubicación**: `src/main/resources/db/migration/`.
- **Formato de nombre**: secuencia entera correlativa
  `V{N}__{descripcion_en_snake_case}.sql` (ej. `V1__baseline_esquema_inicial.sql`,
  `V2__backfill_categoria_productos.sql`). No se usa sufijo de timestamp:
  la secuencia entera es suficiente para este proyecto y legible en PRs.
- **Inmutabilidad**: una migración **mergeada no se edita jamás** (ni su
  checksum se altera). Todo cambio de esquema posterior es una migración
  nueva con el siguiente número de versión. Si necesitas "corregir" un
  baseline ya aplicado, se hace con `V{N+1}` (p. ej. `ALTER`, `BACKFILL`).
- **Extensión pgvector**: la crea `V1`; las columnas `vector(N)` solo pueden
  existir si la extensión está creada antes de la tabla que las usa.
- **Bases existentes (dev/staging/prod creadas con `ddl-auto`)**:
  `spring.flyway.baseline-on-migrate=true` + `spring.flyway.baseline-version=1`
  marcan la base como "equivalente a V1" (baseline) en el primer arranque:
  Flyway **no ejecuta** `V1` ni borra datos; desde ahí solo aplica
  migraciones nuevas. Si la base actual difiere del esquema de `V1`
  (`ddl-auto=validate` falla con `SchemaManagementException`), corregir la
  discrepancia con una migración nueva, nunca editando `V1`.
- **Nuevas bases**: Flyway ejecuta `V1` desde cero (esquema completo +
  extensión), que es exactamente lo que los tests de integración verifican
  sobre Testcontainers (PostgreSQL + pgvector).
- **Plan de pruebas en staging**:
  1. `./mvnw test`: los tests de integración arrancan Testcontainers con una
     base vacía y verifican que Flyway crea la extensión `vector` y todas las
     tablas desde cero, sin `SchemaManagementException`.
  2. Conectar la app a un clon de staging **con datos** y verificar el
     arranque con `baseline-on-migrate=true` (baseline en V1, datos intactos).
  3. Verificar en el log de arranque que no aparecen advertencias de
     desalineación entre la entidad `Producto` y el esquema con
     `ddl-auto=validate`.

### 2. Variables de entorno

```bash
# Base de datos
export DB_URL="jdbc:postgresql://localhost:5432/ecommerce_db"
export DB_USERNAME="postgres"
export DB_PASSWORD="TU_CONTRASENA" 

# JWT Security
export JWT_SECRET="tu_secret_jwt_de_256_bits_seguro"

# Hugging Face API (para embeddings y chat)
export HUGGINGFACE_API_KEY="hf_TU_API_KEY_AQUI"

# Opcional: Configuración específica para chat
export HUGGINGFACE_CHAT_API_KEY="hf_TU_API_KEY_AQUI"
export HUGGINGFACE_CHAT_MODEL="Meta-Llama/Llama-3.2-3B-Instruct"

# Opcional: Configuración específica para embeddings  
export HUGGINGFACE_EMBEDDING_MODEL="sentence-transformers/all-MiniLM-L6-v2"

# CORS
export CORS_ORIGINS="http://localhost:3001,http://localhost:3000"
```

> ⚠️ **Nunca** comitees API keys ni contraseñas. El repositorio ya ignora
> `.env`, `.env.local` y `application-*.properties`.
> Puedes usar el archivo `.env.example` como plantilla.

### 3. Propiedades relevantes (`src/main/resources/application.properties`)

```properties
# Base de datos
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/ecommerce_db}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}

# JPA
spring.jpa.hibernate.ddl-auto=${JPA_DDL_AUTO:validate}
spring.jpa.show-sql=${JPA_SHOW_SQL:true}

# Flyway (esquema versionado; V1 = baseline inicial)
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1

# JWT Security
app.jwt.secret=${JWT_SECRET:clave-secreta-de-256-bits-para-jwt-cambiar-en-produccion}

# Hugging Face - Embeddings
huggingface.api.key=${HUGGINGFACE_API_KEY}
huggingface.api.model=${HUGGINGFACE_EMBEDDING_MODEL:sentence-transformers/all-MiniLM-L6-v2}
huggingface.api.base-url=${HUGGINGFACE_EMBEDDING_BASE_URL:https://router.huggingface.co/hf-inference/models}

# Hugging Face - Chat
huggingface.chat.key=${HUGGINGFACE_CHAT_API_KEY:${HUGGINGFACE_API_KEY}}
huggingface.chat.model=${HUGGINGFACE_CHAT_MODEL:Meta-Llama/Llama-3.2-3B-Instruct}
huggingface.chat.base-url=${HUGGINGFACE_CHAT_BASE_URL:https://router.huggingface.co/v1}

# Server
server.port=${SERVER_PORT:8080}
server.servlet.context-path=/api
```

## ▶️ Puesta en Marcha

```bash
# 1. Clona el repositorio
git clone <TU_URL_DEL_REPO>
cd e-commerce-ai

# 2. Configura las variables de entorno (sección anterior)

# 3. Arranca la aplicación (descarga Maven automáticamente)
./mvnw spring-boot:run
# En Windows: .\mvnw.cmd spring-boot:run
```

La API quedará disponible en **http://localhost:8080**.

### Verificación rápida

```bash
# Listar productos (público)
curl http://localhost:8080/api/v1/productos

# Búsqueda vectorial (público)
curl "http://localhost:8080/api/v1/productos/buscar?q=pegamento%20para%20pvc&limite=5"

# Asistente IA (público; requiere GROQ_API_KEY configurada)
curl "http://localhost:8080/api/v1/productos/asistente?q=tengo%20una%20fuga%20en%20una%20tuber%C3%ADa%20de%20PVC"
```

## 📖 Documentación de la API (Swagger UI / OpenAPI)

Con SpringDoc integrado, la documentación interactiva está en:

| Recurso | URL |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |

> **Nota**: la configuración de seguridad (`anyRequest().authenticated()`)
> no excluye explícitamente las rutas de Swagger. Si no puedes acceder sin
> token, autentícate con un JWT válido o ajusta temporalmente
> `SecurityConfig` en desarrollo.

### Endpoints principales — `/api/v1/productos`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/productos` | Público | Lista todos los productos |
| `GET` | `/api/v1/productos/{id}` | Público | Obtiene un producto por id |
| `GET` | `/api/v1/productos/buscar?q=…&limite=5` | Público | Búsqueda por similitud vectorial (pgvector) |
| `GET` | `/api/v1/productos/asistente?q=…` | Público | Recomendación IA (Groq + palabras clave) |
| `POST` | `/api/v1/productos/diagnose` | `ADMIN` | Body `{ "problema": "…" }` → recomendación IA |
| `POST` | `/api/v1/productos` | `ADMIN` | Crea un producto |
| `PUT` | `/api/v1/productos/{id}` | `ADMIN` | Actualiza un producto |
| `PATCH` | `/api/v1/productos/{id}/stock?stock=0` | `ADMIN` | Actualiza solo el stock (no negativo) |
| `DELETE` | `/api/v1/productos/{id}` | `ADMIN` | Elimina un producto (204) |

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

- **Flujo**: la API espera un JWT en el header `Authorization: Bearer <token>`.
  Un filtro personalizado (`JwtAuthenticationFilter`) lo decodifica
  (HS256, `NimbusJwtDecoder`) y usa el claim `sub` como usuario y el claim
  `roles` como autoridades.
- Para acceder a endpoints de administración, el token debe incluir
  `"roles": ["ROLE_ADMIN"]`.
- ⚠️ **Este repositorio no emite tokens** (no hay endpoint de login): la
  emisión queda fuera del backend. El secreto HS256 usado para validar está
  **hardcodeado** en `SecurityConfig` (marcado en el código como valor de
  ejemplo) — en producción debe inyectarse de forma segura (variable de
  entorno).

## 🔑 Configuración de API Keys (Groq y OpenRouter)

**Groq — chat del asistente:**

1. Crea una cuenta en [console.groq.com](https://console.groq.com) y genera una
   API key (sección *API Keys*).
2. Defínela en tu entorno:

   ```bash
   export GROQ_API_KEY="gsk_TU_API_KEY_AQUI"
   ```

3. (Opcional) Cambia el modelo en `groq.api.model` (default
   `qwen/qwen3.8-27b`).

**OpenRouter — embeddings:**

1. Crea una cuenta en [openrouter.ai](https://openrouter.ai) y genera una API
   key en el panel (sección *Keys*).
2. Defínela en tu entorno:

   ```bash
   export OPENROUTER_API_KEY="sk-or-v1-TU_API_KEY_AQUI"
   ```

3. El modelo de embeddings se configura en
   `spring.ai.openai.embedding.options.model` (default
   `openai/text-embedding-3-small`, 1536 dimensiones).

**Errores del servicio de chat (`GroqService`):**

- **429** si superas el rate limit de Groq.
- **502** si el modelo no responde con JSON válido o falla la conexión.

## 🗂️ Estructura del Proyecto

```
src/main/java/org/alexis/ecommerceai/
├── ECommerceAiApplication.java      # Punto de entrada (@SpringBootApplication)
├── ai/                              # Lógica de IA (Groq + orquestación)
├── config/                          # Seguridad, RestClient y propiedades Groq
├── controller/                      # API REST (/api/v1/productos)
├── dto/                             # Records de request/response + DTOs de chat (groq/)
├── exception/                       # Errores de dominio y manejo global (@RestControllerAdvice)
├── model/                           # Entidad JPA Producto (incl. columna vector)
├── repository/                      # JPA + consulta nativa de similitud vectorial
├── security/                        # Filtro JWT personalizado
└── service/                         # Lógica de negocio de productos
```

## ⚠️ Notas Importantes

- **Dimensión del vector**: la columna `embedding` está definida como
  `vector(1536)`, que coincide con `openai/text-embedding-3-small` servido por
  OpenRouter. Si cambias de modelo de embeddings, ajusta
  `spring.ai.openai.embedding.options.model` y `columnDefinition` en
  `Producto.java` para que la dimensión coincida.
- **Los embeddings usan OpenRouter**: crear/actualizar productos y la búsqueda
  vectorial requieren `OPENROUTER_API_KEY` configurada (el chat del asistente
  usa `GROQ_API_KEY`).
- **Spring Boot 4 / Jackson 3**: el proyecto usa los starters modulares nuevos
  y `tools.jackson.*` (Jackson 3). No "corrijas" esos imports a
  `com.fasterxml.*`: romperías la compilación.
- **CORS**: solo se permite el origen `http://localhost:3001` (frontend de
  desarrollo).
- **Esquema versionado con Flyway**: `ddl-auto=validate` en todos los
  entornos; las migraciones en `src/main/resources/db/migration` son la
  única fuente de verdad del esquema (ver sección *Migraciones de esquema*).
- **Cobertura de tests**: 174 tests (unitarios + integración con Testcontainers).

## 📄 Licencia

No se ha definido una licencia para este proyecto. Si planeas publicarlo,
agrega un archivo `LICENSE` (p. ej. MIT).

agregar futuramente lic mit / apache

---

*Documentación generada a partir del análisis del código real del repositorio.*
