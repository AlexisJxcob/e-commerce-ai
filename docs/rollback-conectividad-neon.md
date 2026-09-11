# Plan de rollback de conectividad — Neon Postgres serverless → motor anterior

Runbook del **Bloque 5, paso 6**. Revierte la conectividad a la base de datos
anterior **sin tocar código ni migraciones**: sólo variables de entorno y
reinicio del proceso.

- **Alcance:** retorno a Neon → motor anterior (rollback) y motor anterior → Neon
  (roll-forward).
- **RTO objetivo:** servicio restablecido en **< 3 min**.
- **RTO medido:** **10,8 s** (ver *Simulacro*).

---

## 1. Disparadores

Ejecutar este runbook si, con la app apuntando a Neon, se cumple **cualquiera**:

| Disparador | Umbral / señal |
|---|---|
| Latencia insostenible | p95 de `GET /api/v1/productos/buscar` > 2 s de forma sostenida (> 10 min) sin ser atribuible a Hugging Face |
| Errores de conexión | 5xx en serie con `SQLTransientConnectionException`, `connection is insecure`, `too many connections` o timeouts de pool |
| Incidente en la región | caída/incidente del proyecto Neon o de `aws-sa-east-1` (São Paulo) |
| Cold start inaceptable | el *scale-to-zero* impide cumplir el SLA de la primera petición tras inactividad |
| Bloqueo irrecuperable | migración Flyway bloqueada o `flyway_schema_history` inconsistente |

Las incidencias del proveedor de IA (**Hugging Face**) **no** son motivo de
rollback de base de datos: se manifiestan como 429/502 y no degradan la
conectividad.

## 2. Precondiciones

1. El motor anterior sigue accesible y con datos: contenedor `postgres-vector`
   en `localhost:5432` (`docker -c default ps postgres-vector`) o la instancia
   previa equivalente.
2. Su esquema está a la altura del de Neon (`V1..V4` aplicadas); si Neon tiene
   una migración más nueva, hay que aplicarla también en el motor anterior
   **antes** del fallback, o el arranque fallará en `ddl-auto=validate`.
3. `.env` (o el gestor de secretos del entorno) contiene **los dos** juegos de
   variables. Los valores reales nunca van al repositorio.

## 3. Rollback (Neon → motor anterior)

`T-0`: se declara el incidente y se empieza a cronometrar.

```bash
# T-0 ────────────────────────────────────────────────────────────────
cd <directorio-del-proyecto>

# 1) Detener el servicio actual (libera el pool contra Neon)
pkill -f "[E]CommerceAiApplication"          # o: systemctl stop / docker stop <svc>
# verificar que el puerto quedó libre
ss -ltn | grep ':8080 ' || echo "8080 libre"

# 2) Conmutar SÓLO variables de entorno: motor anterior + credenciales
export DATABASE_URL='jdbc:postgresql://localhost:5432/ecommerce_db?sslmode=disable'
export DATABASE_DIRECT_URL="$DATABASE_URL"   # Flyway migra contra el mismo host
export DATABASE_USER='postgres'
export DATABASE_PASSWORD='<password-local>'  # nunca en el repo

# 3) Arrancar de nuevo
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./mvnw -o spring-boot:run                     # en prod: java -jar target/*.jar
```

`sslmode=disable` es válido **sólo** para un Postgres local; en cualquier motor
remoto se mantiene `?sslmode=require`.

### Verificación (obligatoria antes de dar por restablecido el servicio)

```bash
# a) listado: HTTP 200 y totalElements igual al origen
curl -s http://localhost:8080/api/v1/productos | jq '.totalElements, .numberOfElements'

# b) búsqueda vectorial: HTTP 200 y top-1 conocido
curl -s "http://localhost:8080/api/v1/productos/buscar?q=martillo&limite=5" | jq -r '.[0].sku'

# c) conteo en el motor (read-only)
PGPASSWORD=<password-local> psql -h localhost -U postgres -d ecommerce_db -tAc \
  'select count(*) from productos'
```

El servicio se considera restablecido cuando (a) y (b) devuelven 200 con los
datos esperados. `T-restaurado` = instante de la primera respuesta correcta.

## 4. Divergencia de datos (leer antes de escribir durante el fallback)

Neon y el motor anterior son **dos bases independientes**: durante el fallback
la app escribe **sólo** en el motor anterior.

- Neon queda como una **foto congelada** del momento del corte.
- Toda escritura hecha durante el fallback (productos, pedidos, carritos,
  usuarios) **no existe en Neon**.
- Al volver a Neon hay que **re-migrar los datos** (paso 5: `pg_dump
  --data-only` / `\copy` + `psql`) o aceptar la pérdida.
- Mitigación preferente: declarar **ventana de mantenimiento** durante el
  fallback, o mover el tráfico a Neon en modo escritura tan pronto como se
  resuelva el incidente.

No hay conmutación en caliente: la URL del `DataSource` se fija en el arranque,
así que el rollback **siempre** implica reiniciar el proceso (1 reinicio, ~11 s
medidos).

## 5. Roll-forward (motor anterior → Neon)

```bash
pkill -f "[E]CommerceAiApplication"
set -a; . ./.env; set +a        # recarga DATABASE_URL / DATABASE_DIRECT_URL de Neon
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./mvnw -o spring-boot:run
# mismas verificaciones (a), (b) y (c) del apartado 3
```

Antes de reabrir escrituras: re-migrar los datos generados durante el fallback
(apartado 4).

## 6. Simulacro ejecutado (evidencia)

Fecha: 2026-09-11 · host de desarrollo · `./mvnw -o spring-boot:run`
(artefactos en `/tmp/bloque5-paso6/`).

| Fase | Resultado |
|---|---|
| Partida — servicio sobre Neon (pooled) | arranque **14,7 s**; `totalElements=23`; `buscar` HTTP 200 en 0,60 s, top-1 `MAR-001` |
| Rollback — parada del servicio | app detenida y 8080 libre en **1,0 s** |
| Rollback — primer `200 OK` | **10,4 s** desde T-0 |
| Rollback — servicio 100% restablecido (listado + búsqueda vectorial) | **10,8 s** desde T-0 (criterio: < 3 min → **cumplido**) |
| Paridad de listado Neon vs motor anterior | `md5 = 91bcdc12fcda8d24412dc067706ed090` **idéntico** (misma página de 20 y `totalElements=23`) |
| Paridad de búsqueda vectorial (`q=martillo`) | `md5 = 4372e6906e4a53bc34bbd8f3d038516f` **idéntico**; top-5 `MAR-001, ROD-001, CON-002, HER-001, LLAV-004` |
| Conteo en cada motor (`select count(*) from productos`) | motor anterior **23** / Neon (rama `production`) **23** |
| Roll-forward a Neon | arranque **14,7 s**; `totalElements=23` |

## 7. Limitaciones conocidas

- El motor anterior es un **contenedor local** en el entorno de desarrollo; en
  producción el destino del rollback debe ser la instancia gestionada previa,
  con la misma URL y credenciales en el gestor de secretos.
- Los parámetros de HikariCP del repo están afinados para Neon
  (`initialization-fail-timeout=-1`, `keepalive-time`, `max-lifetime`); son
  inocuos contra el motor anterior, pero un pool menor es más adecuado allí.
- Este runbook no cubre el rollback **de esquema** (revertir migraciones): las
  migraciones Flyway son aditivas e inmutables, y el rollback de esquema es una
  migración nueva `V{N+1}`.
