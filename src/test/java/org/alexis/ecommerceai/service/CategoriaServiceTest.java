package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.dto.CategoriaRequestDTO;
import org.alexis.ecommerceai.dto.CategoriaResponseDTO;
import org.alexis.ecommerceai.exception.CategoriaEnUsoException;
import org.alexis.ecommerceai.exception.CategoriaNotFoundException;
import org.alexis.ecommerceai.exception.ConflictoException;
import org.alexis.ecommerceai.model.Categoria;
import org.alexis.ecommerceai.repository.CategoriaRepository;
import org.alexis.ecommerceai.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private ProductoRepository productoRepository;

    private CategoriaService categoriaService;

    @BeforeEach
    void setUp() {
        categoriaService = new CategoriaService(categoriaRepository, productoRepository);
    }

    private static Categoria categoria(Long id, String nombre) {
        Categoria categoria = new Categoria();
        categoria.setId(id);
        categoria.setNombre(nombre);
        categoria.setDescripcion("Descripción de " + nombre);
        return categoria;
    }

    // ---------- create ----------

    @Test
    void crear_devuelveCategoriaCuandoNombreEsUnico() {
        when(categoriaRepository.existsByNombre("Fijaciones")).thenReturn(false);
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(invocation -> {
            Categoria guardada = invocation.getArgument(0);
            guardada.setId(1L);
            return guardada;
        });

        CategoriaResponseDTO result = categoriaService.create(new CategoriaRequestDTO("Fijaciones", "Tornillos y tuercas", null));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.nombre()).isEqualTo("Fijaciones");
    }

    @Test
    void crear_conNombreDuplicado_lanzaConflicto() {
        when(categoriaRepository.existsByNombre("Fijaciones")).thenReturn(true);

        assertThatThrownBy(() -> categoriaService.create(new CategoriaRequestDTO("Fijaciones", "Otra", null)))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("Fijaciones");
        verify(categoriaRepository, never()).save(any(Categoria.class));
    }

    // ---------- findById ----------

    @Test
    void buscarPorId_devuelveCategoriaCuandoExiste() {
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria(1L, "Fijaciones")));

        CategoriaResponseDTO result = categoriaService.findById(1L);

        assertThat(result.nombre()).isEqualTo("Fijaciones");
    }

    @Test
    void buscarPorId_inexistente_lanzaCategoriaNotFound() {
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoriaService.findById(99L))
                .isInstanceOf(CategoriaNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ---------- findAll ----------

    @Test
    void listar_devuelveTodasLasCategorias() {
        when(categoriaRepository.findAll()).thenReturn(List.of(
                categoria(1L, "Fijaciones"),
                categoria(2L, "Pinturas")
        ));

        assertThat(categoriaService.findAll()).hasSize(2);
    }

    // ---------- update ----------

    @Test
    void actualizar_conNombreDeOtraCategoria_lanzaConflicto() {
        Categoria existente = categoria(1L, "Fijaciones");
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(categoriaRepository.existsByNombre("Pinturas")).thenReturn(true);

        assertThatThrownBy(() -> categoriaService.update(1L, new CategoriaRequestDTO("Pinturas", "Desc", null)))
                .isInstanceOf(ConflictoException.class);
        verify(categoriaRepository, never()).save(any(Categoria.class));
    }

    @Test
    void actualizar_inexistente_lanzaCategoriaNotFound() {
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoriaService.update(99L, new CategoriaRequestDTO("X", null, null)))
                .isInstanceOf(CategoriaNotFoundException.class);
    }

    // ---------- delete ----------

    @Test
    void eliminar_referenciadaPorProductos_lanzaCategoriaEnUso() {
        when(categoriaRepository.existsById(1L)).thenReturn(true);
        when(productoRepository.existsByCategoriaId(1L)).thenReturn(true);

        assertThatThrownBy(() -> categoriaService.delete(1L))
                .isInstanceOf(CategoriaEnUsoException.class);
        verify(categoriaRepository, never()).deleteById(1L);
    }

    @Test
    void eliminar_noReferenciada_eliminaSinError() {
        when(categoriaRepository.existsById(1L)).thenReturn(true);
        when(productoRepository.existsByCategoriaId(1L)).thenReturn(false);

        categoriaService.delete(1L);

        verify(categoriaRepository).deleteById(1L);
    }

    @Test
    void eliminar_inexistente_lanzaCategoriaNotFound() {
        when(categoriaRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> categoriaService.delete(99L))
                .isInstanceOf(CategoriaNotFoundException.class);
    }

    @Test
    void eliminar_conSubcategorias_lanzaCategoriaEnUso() {
        when(categoriaRepository.existsById(1L)).thenReturn(true);
        when(productoRepository.existsByCategoriaId(1L)).thenReturn(false);
        when(categoriaRepository.existsByPadreId(1L)).thenReturn(true);

        assertThatThrownBy(() -> categoriaService.delete(1L))
                .isInstanceOf(CategoriaEnUsoException.class)
                .hasMessageContaining("subcategorías");
        verify(categoriaRepository, never()).deleteById(1L);
    }

    // ---------- jerarquía (padre_id) ----------

    @Test
    void crear_conPadre_resuelveLaJerarquiaYLaExponeEnLaRespuesta() {
        Categoria padre = categoria(1L, "Herramientas");
        when(categoriaRepository.existsByNombre("Taladros")).thenReturn(false);
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(padre));
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(inv -> {
            Categoria guardada = inv.getArgument(0);
            guardada.setId(2L);
            return guardada;
        });

        CategoriaResponseDTO result = categoriaService.create(new CategoriaRequestDTO("Taladros", "Sub", 1L));

        assertThat(result.padreId()).isEqualTo(1L);
    }

    @Test
    void crear_sinPadre_esCategoriaRaiz() {
        when(categoriaRepository.existsByNombre("Herramientas")).thenReturn(false);
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(inv -> {
            Categoria guardada = inv.getArgument(0);
            guardada.setId(1L);
            return guardada;
        });

        assertThat(categoriaService.create(new CategoriaRequestDTO("Herramientas", null, null)).padreId())
                .isNull();
        verify(categoriaRepository, never()).findById(any(Long.class));
    }

    @Test
    void crear_conPadreInexistente_lanza404() {
        when(categoriaRepository.existsByNombre("Taladros")).thenReturn(false);
        when(categoriaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoriaService.create(new CategoriaRequestDTO("Taladros", null, 999L)))
                .isInstanceOf(CategoriaNotFoundException.class);
        verify(categoriaRepository, never()).save(any(Categoria.class));
    }

    @Test
    void crear_conPadreSiMismoEnUpdate_lanzaConflicto() {
        Categoria existente = categoria(1L, "Herramientas");
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> categoriaService.update(1L, new CategoriaRequestDTO("Herramientas", null, 1L)))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("su propia categoría padre");
    }

    /** Mover una rama bajo su propio descendiente cerraría un ciclo. */
    @Test
    void actualizar_creandoCiclo_lanzaConflicto() {
        Categoria raiz = categoria(1L, "Herramientas");
        Categoria hija = categoria(2L, "Taladros");
        Categoria nieta = categoria(3L, "Taladros inalámbricos");
        hija.setPadre(raiz);
        nieta.setPadre(hija);
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(raiz));
        when(categoriaRepository.findById(3L)).thenReturn(Optional.of(nieta));

        // La raíz (1) intenta colgar de su nieta (3) → ciclo
        assertThatThrownBy(() -> categoriaService.update(1L, new CategoriaRequestDTO("Herramientas", null, 3L)))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("descendencia");
        verify(categoriaRepository, never()).save(any(Categoria.class));
    }

    @Test
    void actualizar_conPadreValido_actualizaLaJerarquia() {
        Categoria raiz = categoria(1L, "Herramientas");
        Categoria hija = categoria(2L, "Taladros");
        when(categoriaRepository.findById(2L)).thenReturn(Optional.of(hija));
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(raiz));
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoriaResponseDTO result = categoriaService.update(2L, new CategoriaRequestDTO("Taladros", "Desc", 1L));

        assertThat(result.padreId()).isEqualTo(1L);
        assertThat(hija.getPadre()).isSameAs(raiz);
    }
}
