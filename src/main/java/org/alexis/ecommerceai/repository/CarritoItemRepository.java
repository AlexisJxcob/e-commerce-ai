package org.alexis.ecommerceai.repository;

import org.alexis.ecommerceai.model.CarritoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CarritoItemRepository extends JpaRepository<CarritoItem, Long> {

    /**
     * Línea concreta acotada al dueño del carrito: es lo que convierte un id
     * ajeno en 404 en lugar de permitir operar sobre el carrito de otro.
     */
    Optional<CarritoItem> findByIdAndCarritoUsuarioUsername(Long id, String username);

    Optional<CarritoItem> findByCarritoIdAndProductoId(Long carritoId, Long productoId);
}
