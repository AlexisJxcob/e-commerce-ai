package org.alexis.ecommerceai.repository;

import org.alexis.ecommerceai.model.ItemCarrito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemCarritoRepository extends JpaRepository<ItemCarrito, Long> {

    List<ItemCarrito> findByUsuarioUsername(String username);

    Optional<ItemCarrito> findByIdAndUsuarioUsername(Long id, String username);

    Optional<ItemCarrito> findByUsuarioUsernameAndProductoId(String username, Long productoId);

    void deleteByUsuarioUsername(String username);
}
