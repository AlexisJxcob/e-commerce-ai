# 🚀 Guía de Deploy - E-Commerce AI

## 📋 Resumen de Cambios Realizados

Este documento describe todos los cambios realizados para preparar el proyecto **E-Commerce AI** para deploy en producción.

---

## ✅ **Cambios de Configuración**

### 1. **Archivos de Configuración**

#### `application.properties`
- **Mejoras en la configuración de la base de datos**:
  - Configuración del pool de conexiones HikariCP (timeout, tamaño del pool, etc.)
  - Variables de entorno para todas las propiedades sensibles
  - Configuración separada para desarrollo y producción

- **Configuración de JWT**:
  - `app.jwt.secret` ahora se configura mediante variable de entorno
  - `app.jwt.expiration` configurable (default: 24 horas)

- **Configuración de Hugging Face**:
  - Separación clara entre embeddings y chat
  - `huggingface.api.*` para embeddings
  - `huggingface.chat.*` para chat
  - Ambas pueden usar la misma API key

- **Configuración de CORS**:
  - `app.cors.allowed-origins` configurable
  - `app.cors.allowed-methods` configurable
  - `app.cors.max-age` configurable

- **Configuración del servidor**:
  - `server.servlet.context-path=/api` para todos los endpoints
  - Puerto configurable mediante `SERVER_PORT`

#### Nuevos archivos de configuración
- **`application-dev.properties`**: Configuración específica para desarrollo
- **`application-prod.properties`**: Configuración específica para producción
- **`application-test.properties`**: Configuración específica para pruebas

### 2. **Configuración de Seguridad**

#### `SecurityConfig.java`
- ✅ **Swagger UI accesible**: Se agregó `.requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").permitAll()`
- ✅ **JWT Secret configurable**: Ahora se inyecta desde `app.jwt.secret` en lugar de estar hardcodeado

#### `CorsConfig.java` (Nuevo)
- Configuración centralizada de CORS
- Permite personalizar orígenes, métodos y headers permitidos
- Usa variables de entorno para configuración flexible

#### `JwtConfig.java` (Nuevo)
- Configuración centralizada para JWT
- Permite inyectar propiedades de JWT en otros componentes

#### `ApiKeyValidationConfig.java` (Nuevo)
- Validación de API keys al iniciar la aplicación
- Asegura que las claves necesarias para Hugging Face estén configuradas
- Proporciona mensajes de error claros si falta alguna configuración

#### `ActuatorConfig.java` (Nuevo)
- Configuración de seguridad para endpoints de Actuator
- Permite acceder a `/actuator/health`, `/actuator/info` y `/actuator/metrics` sin autenticación

---

## 📦 **Cambios en Dependencias**

### `pom.xml`
- ✅ **Agregado `spring-boot-starter-actuator`**: Para health checks y monitoreo
- ✅ **Agregado `spring-boot-starter-cache`**: Para soporte de cache
- ✅ **Agregado `caffeine`**: Implementación de cache para Spring

---

## 🔧 **Cambios en el Código**

### `ProductoController.java`
- ✅ **Ruta actualizada**: Cambiado de `/api/v1/productos` a `/v1/productos` (el `/api` ahora viene del `server.servlet.context-path`)
- ✅ **CORS removido**: La configuración de CORS ahora es global en `CorsConfig.java`

### `AsistenteIAService.java`
- ✅ **Integración con HuggingFace**: Ahora usa `HuggingFaceChatService` en lugar de Groq

### `HuggingFaceChatService.java`
- ✅ **Nuevo servicio**: Implementa la lógica de chat usando Hugging Face Inference API
- ✅ **Manejo de errores**: Errores específicos para rate limits y problemas de conexión
- ✅ **Parsing de JSON**: Extrae y parsea el JSON devuelto por el modelo

---

## 📝 **Documentación Actualizada**

### `README.md`
- ✅ **Requisitos previos actualizados**: Ahora menciona Hugging Face en lugar de Groq y OpenRouter
- ✅ **Variables de entorno actualizadas**: Reflejan la nueva configuración
- ✅ **Propiedades de configuración actualizadas**: Muestran las nuevas propiedades
- ✅ **Stack tecnológico actualizado**: Refleja el uso de Hugging Face

### `.env.example` (Nuevo)
- Plantilla completa con todas las variables de entorno necesarias
- Documentación clara de cada variable
- Valores de ejemplo para desarrollo

### `.gitignore`
- ✅ **Actualizado**: Para ignorar archivos de configuración específicos de entorno
- ✅ **Excepciones**: Permite commitear los archivos de configuración de perfil (dev, test, prod)

---

## 🚀 **Preparación para Deploy**

### 1. **Requisitos Previos**

#### Base de Datos
```bash
# Crear la base de datos
CREATE DATABASE ecommerce_db;

# Instalar la extensión pgvector
\c ecommerce_db
CREATE EXTENSION IF NOT EXISTS vector;
```

#### Variables de Entorno
Copia el archivo `.env.example` a `.env` y configura los valores reales:

```bash
# Base de datos
DB_URL=jdbc:postgresql://tu-servidor:5432/ecommerce_db
DB_USERNAME=tu_usuario
DB_PASSWORD=tu_contraseña

# JWT Security (genera un secret seguro)
JWT_SECRET=tu_secret_jwt_de_256_bits

# Hugging Face API Key
HUGGINGFACE_API_KEY=hf_tu_api_key

# CORS (opcional)
CORS_ORIGINS=http://tu-frontend:3000,http://otro-dominio.com
```

### 2. **Configuración del Perfil**

Elige el perfil adecuado según el entorno:

- **Desarrollo**: Usa `application-dev.properties` (ya configurado)
- **Producción**: Usa `application-prod.properties` o configura tus propias propiedades

Para activar un perfil:
```bash
# Desarrollo
java -jar e-commerce-ai.jar --spring.profiles.active=dev

# Producción
java -jar e-commerce-ai.jar --spring.profiles.active=prod
```

O mediante variable de entorno:
```bash
export SPRING_PROFILES_ACTIVE=prod
java -jar e-commerce-ai.jar
```

### 3. **Build del Proyecto**

```bash
# Usando Maven Wrapper
./mvnw clean package

# El JAR generado estará en target/e-commerce-ai-0.0.1-SNAPSHOT.jar
```

### 4. **Ejecutar la Aplicación**

```bash
# Con variables de entorno
DB_URL=jdbc:postgresql://localhost:5432/ecommerce_db \
DB_USERNAME=postgres \
DB_PASSWORD=postgres \
HUGGINGFACE_API_KEY=hf_tu_api_key \
JWT_SECRET=tu_secret_jwt \
java -jar target/e-commerce-ai-0.0.1-SNAPSHOT.jar

# O usando el archivo .env (requiere configuración adicional)
```

### 5. **Verificar el Deploy**

#### Health Check
```bash
curl http://localhost:8080/api/actuator/health
```

#### Swagger UI
Abre en tu navegador:
```
http://localhost:8080/api/swagger-ui.html
```

#### Endpoints Públicos
```bash
# Listar productos
curl http://localhost:8080/api/v1/productos

# Buscar por similitud
curl "http://localhost:8080/api/v1/productos/buscar?q=llave&limite=5"

# Asistente IA
curl "http://localhost:8080/api/v1/productos/asistente?q=tengo una fuga en una tubería"
```

#### Endpoints Protegidos (requieren JWT)
```bash
# Crear producto (requiere rol ADMIN)
curl -X POST http://localhost:8080/api/v1/productos \
  -H "Authorization: Bearer TU_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sku": "TEST001", "nombre": "Producto Test", "precio": 10.99, "stock": 10, "descripcionTecnica": "Descripción técnica", "descripcionColoquial": "Descripción coloquial"}'
```

---

## 🔐 **Generación de JWT para Pruebas**

Para generar un JWT válido para pruebas locales:

```java
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;
import java.util.List;

public class JwtGenerator {
    public static void main(String[] args) {
        String secret = "clave-secreta-de-256-bits-para-jwt-cambiar-en-produccion";
        long expiration = 86400000; // 24 horas
        
        String token = Jwts.builder()
            .setSubject("admin")
            .claim("roles", List.of("ROLE_ADMIN"))
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(SignatureAlgorithm.HS256, secret.getBytes())
            .compact();
        
        System.out.println("JWT Token: " + token);
    }
}
```

---

## 📊 **Endpoints Disponibles**

### Públicos (sin autenticación)
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/v1/productos` | Listar todos los productos |
| GET | `/api/v1/productos/{id}` | Obtener un producto por ID |
| GET | `/api/v1/productos/buscar?q={query}&limite={n}` | Búsqueda vectorial |
| GET | `/api/v1/productos/asistente?q={query}` | Búsqueda inteligente con IA |
| GET | `/api/actuator/health` | Health check |
| GET | `/api/actuator/info` | Información de la aplicación |
| GET | `/api/actuator/metrics` | Métricas |
| GET | `/api/swagger-ui.html` | Swagger UI |
| GET | `/api/api-docs` | OpenAPI JSON |

### Protegidos (requieren JWT)
| Método | Endpoint | Rol Requerido | Descripción |
|--------|----------|----------------|-------------|
| POST | `/api/v1/productos/diagnose` | ADMIN | Diagnóstico de problema |
| POST | `/api/v1/productos/reindexar` | ADMIN | Reindexar embeddings |
| POST | `/api/v1/productos` | ADMIN | Crear producto |
| PUT | `/api/v1/productos/{id}` | ADMIN | Actualizar producto |
| PATCH | `/api/v1/productos/{id}/stock?stock={n}` | ADMIN | Actualizar stock |
| DELETE | `/api/v1/productos/{id}` | ADMIN | Eliminar producto |

---

## 🛠️ **Configuración Adicional Recomendada para Producción**

### 1. **Base de Datos**
- Usa `spring.jpa.hibernate.ddl-auto=validate` en producción
- Configura un pool de conexiones adecuado para tu carga
- Considera usar Flyway o Liquibase para migraciones

### 2. **Seguridad**
- Genera un JWT secret seguro: `openssl rand -hex 32`
- Configura HTTPS con un certificado válido
- Considera usar Spring Security OAuth2 para autenticación más robusta

### 3. **Monitoreo**
- Configura Prometheus para métricas
- Configura logging adecuado (ELK stack, etc.)
- Configura alertas para health checks

### 4. **Escalabilidad**
- Considera usar Redis para cache distribuido
- Configura balanceo de carga si es necesario
- Considera usar Kubernetes para orquestación

### 5. **Backup**
- Configura backups regulares de la base de datos
- Considera backup de los embeddings generados

---

## 🐛 **Solución de Problemas Comunes**

### 1. **Error: "pgvector extension not found"**
```bash
# Conéctate a PostgreSQL y ejecuta:
\c ecommerce_db
CREATE EXTENSION IF NOT EXISTS vector;
```

### 2. **Error: "Hugging Face API key not configured"**
Asegúrate de que la variable de entorno `HUGGINGFACE_API_KEY` esté configurada:
```bash
export HUGGINGFACE_API_KEY=hf_tu_api_key
```

### 3. **Error: "JWT secret not configured"**
Asegúrate de que la variable de entorno `JWT_SECRET` esté configurada:
```bash
export JWT_SECRET=tu_secret_jwt
```

### 4. **Error: "Connection refused" para PostgreSQL**
Verifica que:
- PostgreSQL esté en ejecución
- La URL de conexión sea correcta
- Las credenciales sean válidas
- El puerto esté abierto en el firewall

### 5. **Swagger UI no accesible**
Asegúrate de que:
- El endpoint `/api/swagger-ui.html` esté permitido en la configuración de seguridad
- No haya un firewall bloqueando el acceso
- La aplicación esté en ejecución

---

## 📞 **Soporte**

Si encuentras algún problema durante el deploy:

1. Revisa los logs de la aplicación
2. Verifica que todas las variables de entorno estén configuradas correctamente
3. Asegúrate de que la base de datos esté correctamente configurada con pgvector
4. Consulta la documentación oficial de Spring Boot y Spring AI

---

**¡Listo para deploy!** 🎉

El proyecto ahora está completamente configurado y listo para ser desplegado en producción. Todos los problemas de configuración han sido resueltos y se han agregado las mejores prácticas para un deploy exitoso.