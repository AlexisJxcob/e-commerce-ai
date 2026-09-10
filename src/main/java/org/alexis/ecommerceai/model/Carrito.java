package org.alexis.ecommerceai.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Carrito persistente: cabecera del agregado. Un usuario tiene como máximo un
 * carrito (UNIQUE en {@code usuario_id}), y sus líneas cuelgan de él.
 *
 * <p>{@code items} usa {@code cascade = ALL} + {@code orphanRemoval = true}:
 * el ciclo de vida de un {@link CarritoItem} está enteramente gobernado por su
 * carrito, así que quitar una línea de la colección la borra de la base.</p>
 *
 * <p>Las reglas de mutación viven aquí (no en el servicio) para que la
 * invariante "todo item conoce su carrito" no se pueda romper desde fuera.</p>
 */
@Entity
@Table(name = "carritos", uniqueConstraints = {
        @UniqueConstraint(name = "uk_carritos_usuario", columnNames = {"usuario_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Carrito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * {@code @ManyToOne} + UNIQUE en lugar de {@code @OneToOne}: expresa la
     * misma cardinalidad (un carrito por usuario) pero mantiene el proxy LAZY
     * de forma fiable y deja el nombre de la constraint único bajo control
     * explícito, en vez del auto-generado por Hibernate.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_carritos_usuario"))
    private Usuario usuario;

    @OneToMany(mappedBy = "carrito", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<CarritoItem> items = new ArrayList<>();

    /** Añade la línea y mantiene la bidireccionalidad. */
    public void agregarItem(CarritoItem item) {
        items.add(item);
        item.setCarrito(this);
    }

    /** Quita la línea; con orphanRemoval, la fila se elimina al hacer flush. */
    public void quitarItem(CarritoItem item) {
        items.remove(item);
        item.setCarrito(null);
    }

    /** Línea correspondiente a un producto, si existe. */
    public Optional<CarritoItem> buscarItem(Long productoId) {
        if (productoId == null) {
            return Optional.empty();
        }
        return items.stream()
                .filter(item -> productoId.equals(item.getProducto().getId()))
                .findFirst();
    }

    /** Vacía el carrito sin dejar líneas huérfanas. */
    public void vaciar() {
        items.forEach(item -> item.setCarrito(null));
        items.clear();
    }

    /** Número de líneas distintas. */
    public int totalLineas() {
        return items.size();
    }
}
