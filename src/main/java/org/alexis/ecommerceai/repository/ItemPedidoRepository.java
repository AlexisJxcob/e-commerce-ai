package org.alexis.ecommerceai.repository;

import org.alexis.ecommerceai.model.ItemPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemPedidoRepository extends JpaRepository<ItemPedido, Long> {

    boolean existsByProductoId(Long productoId);
}
