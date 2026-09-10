package org.alexis.ecommerceai.repository;

import org.alexis.ecommerceai.model.Carrito;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CarritoRepository extends JpaRepository<Carrito, Long> {

    /** Carrito del usuario con líneas y productos resueltos (evita N+1). */
    @EntityGraph(attributePaths = {"items", "items.producto"})
    Optional<Carrito> findByUsuarioUsername(String username);

    Optional<Carrito> findByUsuarioId(Long usuarioId);

    /** Variante con fetch para mutar el carrito dentro de una transacción. */
    @EntityGraph(attributePaths = {"items", "items.producto"})
    @Query("SELECT c FROM Carrito c WHERE c.usuario.id = :usuarioId")
    Optional<Carrito> findConItemsByUsuarioId(@Param("usuarioId") Long usuarioId);
}
