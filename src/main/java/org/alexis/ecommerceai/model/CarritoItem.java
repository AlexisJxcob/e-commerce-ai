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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

/**
 * Línea del carrito. La unicidad (carrito, producto) garantiza que nunca haya
 * dos líneas del mismo producto: reagregar fusiona sumando cantidades.
 *
 * <p>La FK a {@code productos} lleva {@code ON DELETE CASCADE} a nivel de base
 * de datos: si se borra un producto, sus líneas de carrito desaparecen solas y
 * no quedan referencias colgando.</p>
 */
@Entity
@Table(name = "carrito_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_carrito_items_carrito_producto",
                columnNames = {"carrito_id", "producto_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CarritoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrito_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_carrito_items_carrito"))
    private Carrito carrito;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_carrito_items_producto"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Producto producto;

    @Column(nullable = false)
    private Integer cantidad;

    /** Suma unidades a la línea (reagregar el mismo producto). */
    public void incrementar(int delta) {
        if (delta <= 0) {
            throw new IllegalArgumentException("El incremento debe ser positivo");
        }
        this.cantidad = (this.cantidad == null ? 0 : this.cantidad) + delta;
    }

    /** Subtotal a precio actual del producto. */
    public BigDecimal subtotal() {
        return producto.getPrecio().multiply(BigDecimal.valueOf(cantidad));
    }
}
