package org.alexis.ecommerceai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Categoría del catálogo. La jerarquía es una self-FK ({@code padre_id})
 * de profundidad arbitraria: {@code null} en la raíz.
 *
 * <p>La relación es {@code LAZY} a propósito: recorrer el árbol completo
 * cargando cada ancestro eager produciría N+1. La FK no lleva cascade
 * (a diferencia de {@code carrito_items.producto_id}): borrar una categoría
 * con hijas o con productos debe fallar explícitamente, no propagarse.</p>
 */
@Entity
@Table(name = "categorias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    /** Categoría padre; {@code null} si es raíz. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "padre_id", foreignKey = @ForeignKey(name = "fk_categorias_padre"))
    private Categoria padre;

    /** true si la categoría no cuelga de ninguna otra. */
    public boolean esRaiz() {
        return padre == null;
    }
}
