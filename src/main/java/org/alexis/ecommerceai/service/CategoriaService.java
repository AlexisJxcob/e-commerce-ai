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

import java.util.List;

@Service
public class CategoriaService {

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
        categoriaRepository.deleteById(id);
    }

    private CategoriaResponseDTO toResponseDTO(Categoria categoria) {
        return new CategoriaResponseDTO(
                categoria.getId(),
                categoria.getNombre(),
                categoria.getDescripcion()
        );
    }
}
