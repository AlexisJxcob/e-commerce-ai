package org.alexis.ecommerceai.repository;

import org.alexis.ecommerceai.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /**
     * Búsqueda semántica por similitud de coseno (distancia <=>, ascendente =
     * más similar primero). Filtra productos sin embedding (NULL) para que la
     * intención del cliente (problema → solución) solo considere productos
     * ya indexados.
     */
    @Query(value = """
        SELECT * FROM productos p
        WHERE p.embedding IS NOT NULL
        ORDER BY p.embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Producto> buscarPorSimilitudVectorial(@Param("embedding") String embedding, @Param("limit") int limit);

    /**
     * Productos que aún no tienen vector de embedding (seeder/reindexación).
     */
    @Query("SELECT p FROM Producto p WHERE p.embedding IS NULL")
    List<Producto> findPendientesDeEmbedding();

    /**
     * Cuenta los productos que aún no tienen vector de embedding.
     */
    long countByEmbeddingIsNull();

    @Query("""
            SELECT p FROM Producto p
            WHERE LOWER(p.nombre) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(p.descripcionTecnica) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(p.descripcionColoquial) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    List<Producto> buscarPorPalabraClave(@Param("keyword") String keyword);
}