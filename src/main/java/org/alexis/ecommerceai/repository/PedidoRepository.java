package org.alexis.ecommerceai.repository;

import org.alexis.ecommerceai.model.Pedido;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Pedidos del usuario con sus ítems ya resueltos en la misma consulta.
     * Sin el {@code EntityGraph}, serializar N pedidos dispararía N consultas
     * adicionales (N+1): el {@code items} de {@link Pedido} es LAZY.
     */
    @EntityGraph(attributePaths = {"items"})
    List<Pedido> findByUsuarioUsernameOrderByIdDesc(String username);

    /**
     * Detalle por id con ítems y usuario en una sola consulta (el chequeo de
     * propiedad en el servicio necesita {@code usuario.username} sin lazy load).
     */
    @EntityGraph(attributePaths = {"items", "usuario"})
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> findConItemsById(@Param("id") Long id);
}
