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
| **Spring AI 2.0.1** | `EmbeddingModel` para generar/consultar vectores (cliente OpenAI-compatible → OpenRouter) |
| **Groq** | LLM (chat completions) del asistente de ferretería (API OpenAI-compatible) |
| **OpenRouter** | Embeddings para la búsqueda vectorial (`/embeddings`) |
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
- Una **API key de Groq** (gratuita en [console.groq.com](https://console.groq.com))
  para el chat del asistente
- Una **API key de OpenRouter** (gratuita en [openrouter.ai](https://openrouter.ai))
  para los embeddings

## ⚙️ Configuración

### 1. Base de datos (PostgreSQL + pgvector)

```sql
CREATE DATABASE ecommerce_db;
\c ecommerce_db
CREATE EXTENSION IF NOT EXISTS vector;
```

> El proyecto usa `spring.jpa.hibernate.ddl-auto=update`, por lo que las tablas
> se crean automáticamente al arrancar. La columna `embedding` es de tipo
> `vector(1536)` — debe coincidir con la dimensión del modelo de embeddings
> utilizado.

### 2. Variables de entorno

```bash
export GROQ_API_KEY="gsk_TU_API_KEY_AQUI"        # ← placeholder (chat)
export OPENROUTER_API_KEY="sk-or-v1-TU_API_KEY_AQUI"  # ← placeholder (embeddings)
export DB_USERNAME="postgres"      # opcional: usuario de tu base de datos
export DB_PASSWORD="TU_CONTRASENA" # opcional: nunca comitees valores reales
```

> ⚠️ **Nunca** comitees API keys ni contraseñas. El repositorio ya ignora
> `.env`, `.env.local` y `application-local.properties/yml`.
> Si quieres sobreescribir la conexión sin tocar `application.properties`, crea
> un `application-local.properties` (ignorado por git).

### 3. Propiedades relevantes (`src/main/resources/application.properties`)

```properties
spring.application.name=e-commerce-ai

spring.datasource.url=jdbc:postgresql://localhost:5432/ecommerce_db
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Groq (https://console.groq.com) — chat del asistente (API OpenAI-compatible)
groq.api.key=${GROQ_API_KEY}
groq.api.base-url=https://api.groq.com/openai/v1
groq.api.model=qwen/qwen3.8-27b

# Embeddings (cliente OpenAI-compatible de Spring AI) — vía OpenRouter
spring.ai.openai.api-key=${OPENROUTER_API_KEY}
spring.ai.openai.base-url=https://openrouter.ai/api
spring.ai.openai.embedding.options.model=openai/text-embedding-3-small
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
- **Sin migraciones de esquema**: `ddl-auto=update` está pensado para
  desarrollo; para producción se recomienda migraciones (Flyway/Liquibase).
- **Cobertura de tests**: 69 tests (unitarios + integración con Testcontainers).

## 📄 Licencia

No se ha definido una licencia para este proyecto. Si planeas publicarlo,
agrega un archivo `LICENSE` (p. ej. MIT).

---

*Documentación generada a partir del análisis del código real del repositorio.*
