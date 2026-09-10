package org.alexis.ecommerceai.testconfig;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Inspector de sentencias que hace <b>determinista</b> la carrera por la última
 * unidad de un producto, sin tocar el código de producción.
 *
 * <p>El problema: para que el bloqueo optimista falle, la lectura de stock de
 * la transacción perdedora debe ocurrir antes del commit de la ganadora. Si se
 * lanzan dos peticiones "a la vez" y una llega tarde, la segunda ve
 * {@code stock = 0} en la comprobación previa y responde 400 en lugar de 409,
 * con lo que el test se vuelve intermitente.</p>
 *
 * <p>Solución: se retiene el primer {@code UPDATE productos} hasta que dos
 * transacciones distintas hayan ejecutado su {@code SELECT ... productos}
 * (la comprobación de stock). Con ambas lecturas ya hechas, la segunda
 * actualización choca necesariamente con la versión incrementada → 409.</p>
 *
 * <p>Es un artefacto exclusivo de test: se registra vía
 * {@code hibernate.session_factory.statement_inspector} y sólo actúa entre
 * {@link #armar()} y {@link #desarmar()}. El {@code await} tiene tope de tiempo
 * para que un fallo de sincronización nunca deje la build colgada.</p>
 */
public class InspectorBloqueoCompra implements StatementInspector {

    private static final long ESPERA_MAXIMA_MS = 10_000;

    private static final AtomicBoolean ARMADO = new AtomicBoolean(false);
    private static final AtomicBoolean UPDATE_RETENIDO = new AtomicBoolean(false);
    private static final AtomicInteger LECTURAS_PRODUCTOS = new AtomicInteger();

    private static volatile CountDownLatch lecturas = new CountDownLatch(2);

    /** Activa la retención y reinicia el contador de lecturas. */
    public static void armar() {
        lecturas = new CountDownLatch(2);
        LECTURAS_PRODUCTOS.set(0);
        UPDATE_RETENIDO.set(false);
        ARMADO.set(true);
    }

    public static void desarmar() {
        ARMADO.set(false);
        lecturas.countDown();
        lecturas.countDown();
    }

    @Override
    public String inspect(String sql) {
        if (!ARMADO.get() || sql == null) {
            return sql;
        }
        String normalizado = sql.stripLeading().toLowerCase();

        if (normalizado.startsWith("select") && normalizado.contains("productos")) {
            LECTURAS_PRODUCTOS.incrementAndGet();
            lecturas.countDown();
        } else if (normalizado.startsWith("update") && normalizado.contains("productos")
                && UPDATE_RETENIDO.compareAndSet(false, true)) {
            try {
                // Se libera cuando la otra transacción ya leyó el stock, o al
                // agotarse el tope (para no colgar la build jamás).
                lecturas.await(ESPERA_MAXIMA_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return sql;
    }
}
