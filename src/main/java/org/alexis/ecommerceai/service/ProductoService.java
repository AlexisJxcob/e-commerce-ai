package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.dto.ProductoRequestDTO;
import org.alexis.ecommerceai.dto.ProductoResponseDTO;
import org.alexis.ecommerceai.exception.ProductoNotFoundException;
import org.alexis.ecommerceai.exception.StockUpdateConflictException;
import org.alexis.ecommerceai.model.Producto;
import org.alexis.ecommerceai.repository.ProductoRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final EmbeddingModel embeddingModel;

    public ProductoService(ProductoRepository productoRepository, EmbeddingModel embeddingModel) {
        this.productoRepository = productoRepository;
        this.embeddingModel = embeddingModel;
    }

    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> findAll() {
        return productoRepository.findAll().stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductoResponseDTO findById(Long id) {
        return productoRepository.findById(id)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new ProductoNotFoundException("Producto no encontrado con id: " + id));
    }

    /**
     * Búsqueda por lenguaje coloquial ("el coso") usando Spring AI + PGVector
     */
    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> buscarPorSimilitud(String queryUsuario, int limite) {
        // Generar vector a partir de la consulta del usuario
        float[] vectorQuery = embeddingModel.embed(queryUsuario);
        String vectorStr = Arrays.toString(vectorQuery);

        return productoRepository.buscarPorSimilitudVectorial(vectorStr, limite)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    /**
     * Búsqueda textual a partir de palabras clave, herramientas y repuestos extraídos por Groq.
     */
    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> buscarPorPalabrasClave(List<String> palabrasClave) {
        if (palabrasClave == null || palabrasClave.isEmpty()) {
            return List.of();
        }

        Map<Long, ProductoResponseDTO> unicos = new LinkedHashMap<>();
        for (String palabra : palabrasClave) {
            if (palabra == null || palabra.isBlank()) {
                continue;
            }
            productoRepository.buscarPorPalabraClave(palabra.trim()).stream()
                    .map(this::toResponseDTO)
                    .forEach(dto -> unicos.putIfAbsent(dto.id(), dto));
        }
        return List.copyOf(unicos.values());
    }

    @Transactional
    public ProductoResponseDTO create(ProductoRequestDTO request) {
        var producto = new Producto();
        producto.setSku(request.sku());
        producto.setNombre(request.nombre());
        producto.setDescripcionTecnica(request.descripcionTecnica());
        producto.setDescripcionColoquial(request.descripcionColoquial());
        producto.setPrecio(request.precio());
        producto.setStock(request.stock());
        producto.setEmbedding(generarEmbedding(request.nombre(), request.descripcionColoquial()));

        producto = productoRepository.save(producto);
        return toResponseDTO(producto);
    }

    @Transactional
    public ProductoResponseDTO update(Long id, ProductoRequestDTO request) {
        var producto = productoRepository.findById(id)
                .orElseThrow(() -> new ProductoNotFoundException("Producto no encontrado con id: " + id));

        producto.setSku(request.sku());
        producto.setNombre(request.nombre());
        producto.setDescripcionTecnica(request.descripcionTecnica());
        producto.setDescripcionColoquial(request.descripcionColoquial());
        producto.setPrecio(request.precio());
        producto.setStock(request.stock());

        // Regenerar el embedding si la descripción cambia
        producto.setEmbedding(generarEmbedding(request.nombre(), request.descripcionColoquial()));

        producto = productoRepository.save(producto);
        return toResponseDTO(producto);
    }

    @Transactional
    public ProductoResponseDTO updateStock(Long id, Integer newStock) {
        try {
            var producto = productoRepository.findById(id)
                    .orElseThrow(() -> new ProductoNotFoundException("Producto no encontrado con id: " + id));
            producto.setStock(newStock);
            producto = productoRepository.save(producto);
            return toResponseDTO(producto);
        } catch (OptimisticLockingFailureException e) {
            throw new StockUpdateConflictException("Conflicto de concurrencia al actualizar stock. Intente nuevamente.");
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!productoRepository.existsById(id)) {
            throw new ProductoNotFoundException("Producto no encontrado con id: " + id);
        }
        productoRepository.deleteById(id);
    }

    /**
     * Genera el vector de búsqueda a partir del nombre y la descripción coloquial del producto.
     */
    private String generarEmbedding(String nombre, String descripcionColoquial) {
        String textoParaVector = nombre + " " + descripcionColoquial;
        float[] vector = embeddingModel.embed(textoParaVector);
        return Arrays.toString(vector);
    }

    private ProductoResponseDTO toResponseDTO(Producto producto) {
        return new ProductoResponseDTO(
                producto.getId(),
                producto.getSku(),
                producto.getNombre(),
                producto.getDescripcionTecnica(),
                producto.getDescripcionColoquial(),
                producto.getPrecio(),
                producto.getStock()
        );
    }
}