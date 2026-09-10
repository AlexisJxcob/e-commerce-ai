package org.alexis.ecommerceai.repository;

import org.alexis.ecommerceai.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    boolean existsByNombre(String nombre);

    /** Bloquea el borrado de una categoría que todavía tiene subcategorías. */
    boolean existsByPadreId(Long padreId);
}
