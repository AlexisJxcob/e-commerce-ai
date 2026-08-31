package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.dto.ProductoRequestDTO;
import org.alexis.ecommerceai.dto.ProductoResponseDTO;
import org.alexis.ecommerceai.exception.ProductoNotFoundException;
import org.alexis.ecommerceai.exception.StockUpdateConflictException;
import org.alexis.ecommerceai.model.Producto;
import org.alexis.ecommerceai.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    private static final float[] VECTOR = {0.1f, 0.2f, 0.3f};

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private EmbeddingModel embeddingModel;

    private ProductoService productoService;

    @BeforeEach
    void setUp() {
        productoService = new ProductoService(productoRepository, embeddingModel);
    }

    private static Producto producto(Long id, String sku, String nombre) {
        Producto p = new Producto();
        p.setId(id);
        p.setSku(sku);
        p.setNombre(nombre);
        p.setPrecio(new BigDecimal("10.00"));
        p.setStock(5);
        return p;
    }

    private static ProductoRequestDTO request(String sku, String nombre, String descripcionTecnica,
                                              String descripcionColoquial, String precio, int stock) {
        return new ProductoRequestDTO(sku, nombre, new BigDecimal(precio), stock,
                descripcionTecnica, descripcionColoquial);
    }

    // ---------- findAll ----------

    @Test
    void findAll_devuelveProductosMapeados() {
        when(productoRepository.findAll()).thenReturn(List.of(
                producto(1L, "SKU-1", "Cinta"),
                producto(2L, "SKU-2", "Llave")
        ));

        List<ProductoResponseDTO> result = productoService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).sku()).isEqualTo("SKU-1");
        assertThat(result.get(0).precio()).isEqualByComparingTo("10.00");
    }

    // ---------- findById ----------

    @Test
    void findById_devuelveProductoCuandoExiste() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1L, "SKU-1", "Cinta")));

        ProductoResponseDTO result = productoService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.nombre()).isEqualTo("Cinta");
    }

    @Test
    void findById_lanzaProductoNotFoundCuandoNoExiste() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.findById(99L))
                .isInstanceOf(ProductoNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ---------- create ----------

    @Test
    void create_generaEmbeddingConNombreYDescripcionColoquial() {
        ProductoRequestDTO request = request("SKU-1", "Cinta", "Tecnica", "la cinta blanca", "10.00", 5);
        when(embeddingModel.embed("Cinta la cinta blanca")).thenReturn(VECTOR);
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocation -> {
            Producto saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        ProductoResponseDTO result = productoService.create(request);

        verify(embeddingModel).embed("Cinta la cinta blanca");
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.sku()).isEqualTo("SKU-1");
    }

    // ---------- update ----------

    @Test
    void update_actualizaCamposYRegeneraEmbedding() {
        Producto existing = producto(1L, "SKU-1", "Cinta");
        when(productoRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(embeddingModel.embed("Cinta Nueva la cinta nueva")).thenReturn(VECTOR);
        when(productoRepository.save(any(Producto.class))).thenReturn(existing);

        ProductoResponseDTO result = productoService.update(1L,
                request("SKU-1", "Cinta Nueva", "Tecnica nueva", "la cinta nueva", "12.00", 3));

        verify(embeddingModel).embed("Cinta Nueva la cinta nueva");
        assertThat(result.precio()).isEqualByComparingTo("12.00");
        assertThat(result.stock()).isEqualTo(3);
    }

    @Test
    void update_lanzaProductoNotFoundCuandoNoExiste() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.update(99L,
                request("SKU-1", "Cinta", "T", "c", "10.00", 5)))
                .isInstanceOf(ProductoNotFoundException.class);
    }

    // ---------- updateStock ----------

    @Test
    void updateStock_actualizaElStock() {
        Producto existing = producto(1L, "SKU-1", "Cinta");
        when(productoRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productoRepository.save(any(Producto.class))).thenReturn(existing);

        ProductoResponseDTO result = productoService.updateStock(1L, 42);

        assertThat(result.stock()).isEqualTo(42);
        verify(productoRepository).save(existing);
    }

    @Test
    void updateStock_lanzaStockConflictAnteConflictoOptimista() {
        Producto existing = producto(1L, "SKU-1", "Cinta");
        when(productoRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productoRepository.save(any(Producto.class)))
                .thenThrow(new OptimisticLockingFailureException("version desactualizada"));

        assertThatThrownBy(() -> productoService.updateStock(1L, 42))
                .isInstanceOf(StockUpdateConflictException.class)
                .hasMessageContaining("concurrencia");
    }

    @Test
    void updateStock_lanzaProductoNotFoundCuandoNoExiste() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.updateStock(99L, 1))
                .isInstanceOf(ProductoNotFoundException.class);
    }

    // ---------- delete ----------

    @Test
    void delete_eliminaProductoExistente() {
        when(productoRepository.existsById(1L)).thenReturn(true);

        productoService.delete(1L);

        verify(productoRepository).deleteById(1L);
    }

    @Test
    void delete_lanzaProductoNotFoundCuandoNoExiste() {
        when(productoRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> productoService.delete(99L))
                .isInstanceOf(ProductoNotFoundException.class);
        verify(productoRepository, never()).deleteById(any());
    }

    // ---------- buscarPorSimilitud (pgvector) ----------

    @Test
    void buscarPorSimilitud_consultaVectorialConEmbedding() {
        when(embeddingModel.embed("pegamento para pvc")).thenReturn(VECTOR);
        when(productoRepository.buscarPorSimilitudVectorial(anyString(), eq(5)))
                .thenReturn(List.of(producto(1L, "SKU-1", "Pegamento")));

        List<ProductoResponseDTO> result = productoService.buscarPorSimilitud("pegamento para pvc", 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sku()).isEqualTo("SKU-1");
        verify(productoRepository).buscarPorSimilitudVectorial("[0.1, 0.2, 0.3]", 5);
    }

    // ---------- buscarPorPalabrasClave ----------

    @Test
    void buscarPorPalabrasClave_devuelveVacioParaNulosOVacios() {
        assertThat(productoService.buscarPorPalabrasClave(null)).isEmpty();
        assertThat(productoService.buscarPorPalabrasClave(List.of())).isEmpty();
        verify(productoRepository, never()).buscarPorPalabraClave(anyString());
    }

    @Test
    void buscarPorPalabrasClave_deduplicaProductosRepetidos() {
        Producto cinta = producto(1L, "SKU-1", "Cinta teflon");
        when(productoRepository.buscarPorPalabraClave("cinta")).thenReturn(List.of(cinta));
        when(productoRepository.buscarPorPalabraClave("teflon")).thenReturn(List.of(cinta));

        List<ProductoResponseDTO> result = productoService.buscarPorPalabrasClave(List.of("cinta", "teflon"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    void buscarPorPalabrasClave_ignoraTerminosEnBlanco() {
        when(productoRepository.buscarPorPalabraClave("cinta")).thenReturn(List.of());

        List<ProductoResponseDTO> result =
                productoService.buscarPorPalabrasClave(List.of("  ", "cinta", "", null));

        assertThat(result).isEmpty();
        verify(productoRepository).buscarPorPalabraClave("cinta");
        verify(productoRepository, never()).buscarPorPalabraClave("  ");
        verify(productoRepository, never()).buscarPorPalabraClave("");
        verify(productoRepository, never()).buscarPorPalabraClave(null);
    }
}
