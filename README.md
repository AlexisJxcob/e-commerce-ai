# 🛠️ E-Commerce AI — Ferretería Inteligente

Backend REST de un e-commerce de **ferretería impulsado por IA**. El cliente
describe un problema en lenguaje coloquial (*"tengo una fuga en una tubería de
PVC"*) y la API, gracias a un LLM vía **OpenRouter**, lo traduce a términos
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
![OpenRouter](https://img.shields.io/badge/OpenRouter-API-ff6b35?style=flat)
![Maven](https://img.shields.io/badge/Maven-3.9.16-C71A36?style=flat&logo=apachemaven&logoColor=white)
![SpringDoc](https://img.shields.io/badge/SpringDoc-OpenAPI-85EA2D?style=flat)

| Tecnología | Uso |
|---|---|
| **Java 21** | Lenguaje y runtime |
| **Spring Boot 4.1.1** | Framework (starters modulares `webmvc`, `restclient`, `data-jpa`) |
| **Spring AI 2.0.1** | `EmbeddingModel` para generar/consultar vectores (Ollama) |
| **OpenRouter** | LLM (chat completions) para el asistente de ferretería |
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
- **Ollama** en ejecución en `http://localhost:11434` con un modelo de
  embeddings compatible (ver [Notas importantes](#notas-importantes))
- Una **API key de OpenRouter** (gratuita en [openrouter.ai](https://openrouter.ai))

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
export OPENROUTER_API_KEY="sk-or-v1-TU_API_KEY_AQUI"   # ← placeholder
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

# OpenRouter (https://openrouter.ai/docs)
openrouter.api.key=${OPENROUTER_API_KEY}
openrouter.api.base-url=https://openrouter.ai/api/v1
openrouter.api.model=openrouter/free   # o p. ej. meta-llama/llama-3.3-70b-instruct:free
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

# Asistente IA (público; requiere OPENROUTER_API_KEY configurada)
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
| `GET` | `/api/v1/productos/asistente?q=…` | Público | Recomendación IA (OpenRouter + palabras clave) |
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

## 🔑 Configuración de la API Key de OpenRouter

1. Crea una cuenta en [openrouter.ai](https://openrouter.ai) y genera una API
   key en el panel (sección *Keys*).
2. Defínela en tu entorno:

   ```bash
   export OPENROUTER_API_KEY="sk-or-v1-TU_API_KEY_AQUI"
   ```

3. (Opcional) Cambia el modelo en `openrouter.api.model`. Los gratuitos como
   `openrouter/free` son ideales para desarrollo.
4. El servicio `OpenRouterService` responde:
   - **429** si superas el rate limit de OpenRouter.
   - **502** si el modelo no responde con JSON válido o falla la conexión.

## 🗂️ Estructura del Proyecto

```
src/main/java/org/alexis/ecommerceai/
├── ECommerceAiApplication.java      # Punto de entrada (@SpringBootApplication)
├── ai/                              # Lógica de IA (OpenRouter + orquestación)
├── config/                          # Seguridad, RestClient y propiedades OpenRouter
├── controller/                      # API REST (/api/v1/productos)
├── dto/                             # Records de request/response + DTOs de OpenRouter
├── exception/                       # Errores de dominio y manejo global (@RestControllerAdvice)
├── model/                           # Entidad JPA Producto (incl. columna vector)
├── repository/                      # JPA + consulta nativa de similitud vectorial
├── security/                        # Filtro JWT personalizado
└── service/                         # Lógica de negocio de productos
```

## ⚠️ Notas Importantes

- **Dimensión del vector**: la columna `embedding` está definida como
  `vector(1536)`. Si tu modelo de embeddings genera otra dimensión (p. ej.
  768 para `nomic-embed-text` de Ollama), ajusta `columnDefinition` en
  `Producto.java`. Actualmente no hay configuración `spring.ai.*` explícita;
  Spring AI usa los valores por defecto de Ollama (`http://localhost:11434`).
- **Ollama debe estar corriendo** para crear/actualizar productos y para la
  búsqueda vectorial, ya que el `EmbeddingModel` se usa en esas operaciones.
- **Spring Boot 4 / Jackson 3**: el proyecto usa los starters modulares nuevos
  y `tools.jackson.*` (Jackson 3). No "corrijas" esos imports a
  `com.fasterxml.*`: romperías la compilación.
- **CORS**: solo se permite el origen `http://localhost:3001` (frontend de
  desarrollo).
- **Sin migraciones de esquema**: `ddl-auto=update` está pensado para
  desarrollo; para producción se recomienda migraciones (Flyway/Liquibase).
- **Cobertura de tests mínima**: actualmente solo existe el test de contexto
  (`contextLoads`).

## 📄 Licencia

No se ha definido una licencia para este proyecto. Si planeas publicarlo,
agrega un archivo `LICENSE` (p. ej. MIT).

---

*Documentación generada a partir del análisis del código real del repositorio.*
