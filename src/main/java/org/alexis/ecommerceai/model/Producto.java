package org.alexis.ecommerceai.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "productos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion_tecnica", columnDefinition = "TEXT")
    private String descripcionTecnica;

    // Aquí vive la magia del "coso": lenguaje informal, modismos o términos
    // populares
    @Column(name = "descripcion_coloquial", columnDefinition = "TEXT")
    private String descripcionColoquial;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false)
    private Integer stock;

    // Mapeo directo del tipo vector para PostgreSQL
    // Cambia 1536 a las dimensiones de tu modelo de embedding (ej. 1536 para
    // OpenAI, 768 para Ollama/nomic-embed-text)
    @Column(columnDefinition = "vector(1536)")
    private String embedding;

    @Version
    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private Long version;
}