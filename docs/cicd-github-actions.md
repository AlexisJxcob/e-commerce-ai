# 🚦 CI/CD con GitHub Actions (Bloque 2)

Automatiza la **compilación**, la **verificación de seguridad estática** (SCA) y la
**validación de migraciones Flyway** *antes* de habilitar el despliegue a Render.

## Arquitectura

```
push a main / pull_request
        │
        ▼
┌──────────────────────────────────────────────────────────────┐
│ CI (.github/workflows/ci.yml)                                │
│  1. build-and-test   → mvn test (unit + Testcontainers)      │
│                       + gate Flyway (Postgres efímero)       │
│  2. dependency-check → OWASP Dependency-Check (CVSS >= 7.0)  │
│  3. dependency-review→ GitHub (solo PR, repo público)        │
└──────────────────────────────────────────────────────────────┘
        │  workflow_run: conclusion == success ∧ head_branch == main
        ▼
┌──────────────────────────────────────────────────────────────┐
│ CD (.github/workflows/cd.yml)                                │
│  → POST al Deploy Hook de Render (secret)                    │
└──────────────────────────────────────────────────────────────┘
```

- **CD espera de verdad a CI**: `workflow_run` se dispara cuando el workflow
  llamado **CI** termina, y el job de deploy re-evalúa
  `conclusion == 'success'` y `head_branch == 'main'`. No corre en paralelo
  ante un push suelto ni tras un CI rojo.
- El despliegue usa el **Deploy Hook de Render** (curl POST a la URL guardada
  en el secret `RENDER_DEPLOY_HOOK_URL`), no un mecanismo genérico de
  imagen/registry.

## Configuración inicial (una sola vez)

### 1. Render — desactivar el auto-deploy nativo

En el servicio Web de Render: **Settings → Auto-Deploy → No** (o pausar el
deploy automático). Si sigue activo, cada push a la rama conectada generaría
un deploy **además** del que dispara el hook → deploys duplicados.

### 2. Render — crear el Deploy Hook

**Settings → Deploy Hook → Create Hook** (apunta a la rama `main`). Render
te da una URL tipo `https://api.render.com/deploy/srv-xxxx?key=yyyy`.

### 3. GitHub — guardar el secret

Repo → **Settings → Secrets and variables → Actions** → *New repository secret*:

| Nombre                    | Valor                          |
|---------------------------|--------------------------------|
| `RENDER_DEPLOY_HOOK_URL`  | URL del Deploy Hook de Render  |
| `NVD_API_KEY` *(opcional)*| API key gratuita de la NVD (acelera el primer sync del escáner) |

## Workflows

### `ci.yml` (nombre exacto: `CI`)

| Job | Qué hace | Umbral |
|-----|----------|--------|
| `build-and-test` | Java 21 Temurin, `./mvnw test` (los tests de integración usan Testcontainers → Docker del runner, sin servicios extra). Después levanta `pgvector/pgvector:pg16` como servicio y corre `flyway:migrate flyway:validate` contra esa base efímera. | Fallo = build rojo |
| `dependency-check` | OWASP Dependency-Check (perfil `owasp` del pom). La base NVD se cachea con `actions/cache` en `~/.m2/repository/org/owasp/dependency-check-data`. *Primer run*: sincroniza ~389k CVEs; sin API key puede tardar mucho, por eso el step acepta el secret `NVD_API_KEY` (gratis) — en runs posteriores solo actualiza el delta. | Falla si hay CVSS **>= 7.0** no suprimido |
| `dependency-review` | GitHub Dependency Review (solo `pull_request`). El repo es **público**, así que no necesita GitHub Advanced Security. | `fail-on-severity: high` |

El gate de Flyway detecta en el PR, antes del merge: **SQL inválido**,
**nombres de versión duplicados** (V2 duplicada, etc.) y migraciones que no
aplican limpiamente desde cero. Nota: contra una base efímera vacía no puede
detectar la *edición* de una migración ya mergeada (no hay histórico previo);
esa edición la detecta Render en el arranque de producción, porque Boot
ejecuta `spring.flyway` (validate) contra el `flyway_schema_history` real.
Regla: **las migraciones mergeadas son inmutables** (nueva V+1 en lugar de
editar).

### `cd.yml` (nombre exacto: `CD`)

- Trigger: `workflow_run` sobre el workflow `CI`, `branches: [main]`.
- Guard `if`: `conclusion == 'success' && head_branch == 'main'`.
- Único paso efectivo: `curl -X POST` al Deploy Hook (con reintentos).
- `concurrency: cd-render-deploy` (sin cancelación) evita deploys solapados.

## Reproducir los gates localmente

```bash
# 1) Tests completos (requiere Docker para Testcontainers)
./mvnw test

# 2) Gate Flyway contra un Postgres efímero local (misma imagen que CI)
docker run -d --rm --name flyway-gate -e POSTGRES_DB=ecommerce_ci \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
  -p 55432:5432 pgvector/pgvector:pg16
./mvnw -B \
  -Dflyway.url=jdbc:postgresql://localhost:55432/ecommerce_ci \
  -Dflyway.user=postgres -Dflyway.password=postgres \
  flyway:migrate flyway:validate
docker rm -f flyway-gate

# 3) SCA OWASP (el primer sync de la NVD es lento; con NVD_API_KEY en el
#    entorno es mucho más rápido)
NVD_API_KEY=... ./mvnw -Powasp -Dmaven.test.skip=true verify
# Reporte: target/dependency-check-report.html / .json
```

### Clasificar un falso positivo de OWASP

1. Ejecuta el escaneo local y abre `target/dependency-check-report.html`.
2. Añade una entrada `<suppress>` justificada en `owasp-suppression.xml`
   (hay una plantilla comentada dentro).
3. Vuelve a escanear; el hallazgo ya no debe romper el build.
4. La entrada viaja en el PR y se aplica también en CI.

## Checklist de pruebas

- [ ] **CI bloquea un test rojo**: crea una rama con un test fallido → el job
      `build-and-test` falla y el PR no se puede mergear (proteger `main`
      con *required status checks* apuntando a `CI` si se quiere bloqueo duro).
- [ ] **Gate de Flyway detecta SQL inválido**: añade una `V999__mal.sql` con
      sintaxis rota → `flyway:migrate` falla en CI antes del merge (borra el
      archivo al terminar la prueba).
- [ ] **CD no se dispara si CI falla**: fuerza un fallo en `ci.yml` en `main`
      (p. ej. un test rojo) → en Actions debe aparecer `CD` en estado
      *skipped*; cuando CI vuelve a verde sobre `main`, `CD` corre y Render
      despliega.

## Rollback del deploy (respaldo manual)

Render conserva los deploys previos:

- **Dashboard**: servicio → pestaña *Events* → desplegar el deploy anterior
  → *Rollback*. Si no aparece el botón, seleccionar el deploy previo y usar
  la opción de re-desplegarlo.
- **API** (equivalente automatizable; ver doc oficial de la API de Render
  para los endpoints exactos):
  ```bash
  # listar deploys de un servicio (encontrar el id anterior bueno)
  curl -H "Accept: application/json" \
       -H "Authorization: Bearer $RENDER_API_KEY" \
       https://api.render.com/v1/services/$SERVICE_ID/deploys
  # hacer rollback a ese deploy
  curl -X POST \
       -H "Accept: application/json" \
       -H "Authorization: Bearer $RENDER_API_KEY" \
       https://api.render.com/v1/services/$SERVICE_ID/deploys/$DEPLOY_ID/rollback
  ```
  `RENDER_API_KEY` se genera en Render → **Account Settings → API Keys**.

## Ajustes frecuentes y su rollback

| Cambio | Cómo | Riesgo de rollback |
|--------|------|--------------------|
| Umbral CVSS del SCA | `failBuildOnCVSS` en el perfil `owasp` del `pom.xml` | Bajo (cambiar el número) |
| Falsos positivos SCA | `owasp-suppression.xml` | Bajo (borrar la entrada) |
| Major de Postgres del gate | tag de la imagen en `services.postgres` de `ci.yml` (mantener alineado con producción) | Bajo |
| CI ruidoso en ramas | acotar el trigger `on.push.branches` de `ci.yml` | Bajo (revertir el YAML) |
| Deploy duplicado | verificar que el Auto-Deploy nativo de Render esté desactivado | Bajo |

## Archivos del Bloque 2

- `.github/workflows/ci.yml` — workflow `CI`
- `.github/workflows/cd.yml` — workflow `CD` (deploy hook de Render)
- `pom.xml` — plugin `flyway-maven-plugin` + perfil `owasp` (OWASP Dependency-Check)
- `owasp-suppression.xml` — falsos positivos del SCA
- `docs/cicd-github-actions.md` — este documento
