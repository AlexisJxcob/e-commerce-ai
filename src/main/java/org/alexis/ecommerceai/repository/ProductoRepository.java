package org.alexis.ecommerceai.repository;

import org.alexis.ecommerceai.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    Optional<Producto> findBySku(String sku);

    // Método preparado para búsqueda por similitud vectorial (Spring AI + PGVector)
    // @noinspection SqlResolve
    @Query(value = "SELECT * FROM productos p ORDER BY p.embedding <=> CAST(:vector AS vector) LIMIT :limit",
            nativeQuery = true)
    List<Producto> findSimilarProducts(@Param("vector") String vector, @Param("limit") int limit);
}