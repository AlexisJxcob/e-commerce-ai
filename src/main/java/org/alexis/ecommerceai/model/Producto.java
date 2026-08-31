package org.alexis.ecommerceai.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

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

    // Mapeo directo del tipo vector para PostgreSQL.
    // Dimensiones del modelo sentence-transformers/all-MiniLM-L6-v2 = 384.
    // Si cambias de modelo de embedding, actualiza también este valor
    // (p. ej. openai/text-embedding-3-small = 1536).
    // @ColumnTransformer aplica el cast explicito ?::vector en INSERT/UPDATE:
    // sin él, PostgreSQL rechaza el parametro varchar (String de Java).
    @ColumnTransformer(write = "?::vector")
    @Column(columnDefinition = "vector(384)")
    private String embedding;

    @Version
    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private Long version;
}