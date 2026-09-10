package org.alexis.ecommerceai.repository;

import org.alexis.ecommerceai.model.Carrito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CarritoRepository extends JpaRepository<Carrito, Long> {

    Optional<Carrito> findByUsuarioUsername(String username);

    Optional<Carrito> findByUsuarioId(Long usuarioId);
}
