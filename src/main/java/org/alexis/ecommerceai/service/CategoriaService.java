package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.dto.CategoriaRequestDTO;
import org.alexis.ecommerceai.dto.CategoriaResponseDTO;
import org.alexis.ecommerceai.exception.CategoriaEnUsoException;
import org.alexis.ecommerceai.exception.CategoriaNotFoundException;
import org.alexis.ecommerceai.exception.ConflictoException;
import org.alexis.ecommerceai.model.Categoria;
import org.alexis.ecommerceai.repository.CategoriaRepository;
import org.alexis.ecommerceai.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Categorías del catálogo, incluida su jerarquía ({@code padre_id}).
 *
 * <p>Invariantes que este servicio hace cumplir:</p>
 * <ul>
 *   <li>El nombre es único → 409 si se repite.</li>
 *   <li>El padre, si se indica, existe → 404.</li>
 *   <li>El grafo es un árbol: no se admite que una categoría sea su propio
 *       padre ni que se cree un ciclo moviendo una rama bajo su descendiente
 *       → 409. Sin esta validación el esquema lo aceptaría (la self-FK no
 *       puede expresar aciclicidad) y {@code findAll()} entraría en bucle.</li>
 *   <li>No se borra una categoría con productos ni con subcategorías → 409.</li>
 * </ul>
 */
@Service
public class CategoriaService {

    /** Tope defensivo de profundidad al recorrer ancestros. */
    private static final int MAX_PROFUNDIDAD = 100;

    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;

    public CategoriaService(CategoriaRepository categoriaRepository, ProductoRepository productoRepository) {
        this.categoriaRepository = categoriaRepository;
        this.productoRepository = productoRepository;
    }

    @Transactional
    public CategoriaResponseDTO create(CategoriaRequestDTO request) {
        if (categoriaRepository.existsByNombre(request.nombre())) {
            throw new ConflictoException("Ya existe una categoría con el nombre: " + request.nombre());
        }
        var categoria = new Categoria();
        categoria.setNombre(request.nombre());
        categoria.setDescripcion(request.descripcion());
        categoria.setPadre(resolverPadre(request.padreId(), null));
        categoria = categoriaRepository.save(categoria);
        return toResponseDTO(categoria);
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> findAll() {
        return categoriaRepository.findAll().stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoriaResponseDTO findById(Long id) {
        return categoriaRepository.findById(id)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new CategoriaNotFoundException("Categoría no encontrada con id: " + id));
    }

    @Transactional
    public CategoriaResponseDTO update(Long id, CategoriaRequestDTO request) {
        var categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new CategoriaNotFoundException("Categoría no encontrada con id: " + id));
        if (!categoria.getNombre().equals(request.nombre())
                && categoriaRepository.existsByNombre(request.nombre())) {
            throw new ConflictoException("Ya existe una categoría con el nombre: " + request.nombre());
        }
        categoria.setNombre(request.nombre());
        categoria.setDescripcion(request.descripcion());
        categoria.setPadre(resolverPadre(request.padreId(), id));
        categoria = categoriaRepository.save(categoria);
        return toResponseDTO(categoria);
    }

    @Transactional
    public void delete(Long id) {
        if (!categoriaRepository.existsById(id)) {
            throw new CategoriaNotFoundException("Categoría no encontrada con id: " + id);
        }
        if (productoRepository.existsByCategoriaId(id)) {
            throw new CategoriaEnUsoException(
                    "No se puede eliminar la categoría con id: " + id + " porque tiene productos asociados");
        }
        if (categoriaRepository.existsByPadreId(id)) {
            throw new CategoriaEnUsoException(
                    "No se puede eliminar la categoría con id: " + id + " porque tiene subcategorías");
        }
        categoriaRepository.deleteById(id);
    }

    /**
     * Resuelve y valida la categoría padre.
     *
     * @param padreId     id solicitado ({@code null} → raíz)
     * @param categoriaId id de la categoría que se está editando ({@code null} al crear)
     */
    private Categoria resolverPadre(Long padreId, Long categoriaId) {
        if (padreId == null) {
            return null;
        }
        if (padreId.equals(categoriaId)) {
            throw new ConflictoException("Una categoría no puede ser su propia categoría padre");
        }
        Categoria padre = categoriaRepository.findById(padreId)
                .orElseThrow(() -> new CategoriaNotFoundException(
                        "Categoría padre no encontrada con id: " + padreId));

        // Recorrido hacia arriba: si reencontramos la categoría editada, el
        // nuevo enlace cerraría un ciclo. El Set corta además un ciclo ya
        // existente en la base (defensa en profundidad).
        Set<Long> visitados = new HashSet<>();
        Categoria actual = padre;
        int profundidad = 0;
        while (actual != null && profundidad++ < MAX_PROFUNDIDAD) {
            if (!visitados.add(actual.getId())) {
                break;
            }
            if (categoriaId != null && categoriaId.equals(actual.getId())) {
                throw new ConflictoException(
                        "La categoría con id: " + categoriaId + " no puede depender de su propia descendencia");
            }
            actual = actual.getPadre();
        }
        return padre;
    }

    private CategoriaResponseDTO toResponseDTO(Categoria categoria) {
        return new CategoriaResponseDTO(
                categoria.getId(),
                categoria.getNombre(),
                categoria.getDescripcion(),
                categoria.getPadre() != null ? categoria.getPadre().getId() : null
        );
    }
}
