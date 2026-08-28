package org.alexis.ecommerceai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SugerenciaFerreteriaDTO(
        List<String> palabrasClave,
        List<String> herramientas,
        List<String> repuestos
) {
    public List<String> palabrasClave() {
        return palabrasClave == null ? List.of() : palabrasClave;
    }

    public List<String> herramientas() {
        return herramientas == null ? List.of() : herramientas;
    }

    public List<String> repuestos() {
        return repuestos == null ? List.of() : repuestos;
    }
}
