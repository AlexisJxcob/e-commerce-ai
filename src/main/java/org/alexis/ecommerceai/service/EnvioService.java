package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.model.MetodoEntrega;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Única fuente de verdad del costo de entrega.
 *
 * <p>Antes esta regla vivía sólo en el navegador: el total que veía el cliente
 * no era el que se cobraba. Ahora el backend la aplica al cotizar y al crear el
 * pedido, de modo que pantalla y cobro siempre coinciden.</p>
 */
@Service
public class EnvioService {

    /** Monto a partir del cual el despacho a domicilio es gratuito. */
    public static final BigDecimal ENVIO_GRATIS_DESDE = new BigDecimal("50000");

    /** Tarifa plana de despacho estándar bajo el umbral de envío gratis. */
    public static final BigDecimal COSTO_DESPACHO_ESTANDAR = new BigDecimal("3990");

    /**
     * Calcula el costo de entrega para un subtotal dado.
     *
     * @param metodo   forma de entrega; {@code null} se trata como retiro.
     * @param subtotal suma de las líneas del pedido; {@code null} se trata como cero.
     * @return {@code 0} para retiro o despacho sobre el umbral; la tarifa plana en caso contrario.
     */
    public BigDecimal calcularCostoDespacho(MetodoEntrega metodo, BigDecimal subtotal) {
        if (metodo == null || metodo == MetodoEntrega.RETIRO) {
            return BigDecimal.ZERO;
        }
        BigDecimal base = subtotal == null ? BigDecimal.ZERO : subtotal;
        return base.compareTo(ENVIO_GRATIS_DESDE) >= 0 ? BigDecimal.ZERO : COSTO_DESPACHO_ESTANDAR;
    }
}
