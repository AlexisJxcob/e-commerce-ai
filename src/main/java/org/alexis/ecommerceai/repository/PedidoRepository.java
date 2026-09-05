package org.alexis.ecommerceai.repository;

import org.alexis.ecommerceai.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    List<Pedido> findByUsuarioUsernameOrderByIdDesc(String username);
}
