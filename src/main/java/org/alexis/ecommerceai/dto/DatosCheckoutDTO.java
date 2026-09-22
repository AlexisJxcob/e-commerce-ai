package org.alexis.ecommerceai.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.alexis.ecommerceai.model.MetodoEntrega;

/**
 * Datos de comprador y entrega que el checkout envía al crear el pedido.
 *
 * <p>Estos datos antes se recolectaban en el navegador y se descartaban: el
 * pedido no tenía comprador ni dirección. Ahora viajan y se persisten en la
 * misma transacción que el pedido.</p>
 */
public record DatosCheckoutDTO(

        @NotBlank(message = "Los nombres del comprador son obligatorios")
        @Size(max = 70, message = "Los nombres no pueden superar 70 caracteres")
        String nombres,

        @NotBlank(message = "Los apellidos del comprador son obligatorios")
        @Size(max = 70, message = "Los apellidos no pueden superar 70 caracteres")
        String apellidos,

        @NotBlank(message = "El RUT del comprador es obligatorio")
        @Size(max = 20, message = "El RUT no puede superar 20 caracteres")
        String rut,

        @NotBlank(message = "El correo del comprador es obligatorio")
        @Email(message = "El correo del comprador no es válido")
        @Size(max = 120, message = "El correo no puede superar 120 caracteres")
        String email,

        @NotBlank(message = "El teléfono de contacto es obligatorio")
        @Size(max = 25, message = "El teléfono no puede superar 25 caracteres")
        String telefono,

        @NotNull(message = "El método de entrega es obligatorio")
        MetodoEntrega metodoEntrega,

        @Size(max = 100, message = "La región no puede superar 100 caracteres")
        String region,

        @Size(max = 100, message = "La comuna no puede superar 100 caracteres")
        String comuna,

        @Size(max = 200, message = "La dirección no puede superar 200 caracteres")
        String direccion,

        @Size(max = 100, message = "El departamento no puede superar 100 caracteres")
        String depto,

        @Size(max = 300, message = "Las referencias no pueden superar 300 caracteres")
        String referencias,

        Boolean retiraTercero,

        @Size(max = 150, message = "El nombre de quien retira no puede superar 150 caracteres")
        String nombreTercero,

        @Size(max = 20, message = "El RUT de quien retira no puede superar 20 caracteres")
        String rutTercero
) {

    /**
     * Validación cruzada: elegir despacho obliga a indicar destino completo.
     * Se expresa como restricción del propio DTO para que el error salga como
     * 400 con el mensaje en español, sin un handler adicional.
     */
    @AssertTrue(message = "Para despacho a domicilio debes indicar región, comuna y dirección")
    public boolean isEntregaValida() {
        if (metodoEntrega != MetodoEntrega.DESPACHO) {
            return true;
        }
        return !esBlanco(region) && !esBlanco(comuna) && !esBlanco(direccion);
    }

    private static boolean esBlanco(String valor) {
        return valor == null || valor.isBlank();
    }
}
