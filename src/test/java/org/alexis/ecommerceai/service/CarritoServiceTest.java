package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.dto.CarritoResponseDTO;
import org.alexis.ecommerceai.dto.LineaCarritoResponseDTO;
import org.alexis.ecommerceai.exception.ItemCarritoNotFoundException;
import org.alexis.ecommerceai.exception.ProductoNotFoundException;
import org.alexis.ecommerceai.model.ItemCarrito;
import org.alexis.ecommerceai.model.Producto;
import org.alexis.ecommerceai.model.Rol;
import org.alexis.ecommerceai.model.Usuario;
import org.alexis.ecommerceai.repository.ItemCarritoRepository;
import org.alexis.ecommerceai.repository.ProductoRepository;
import org.alexis.ecommerceai.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarritoServiceTest {

    @Mock
    private ItemCarritoRepository itemCarritoRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private CarritoService carritoService;

    @BeforeEach
    void setUp() {
        carritoService = new CarritoService(itemCarritoRepository, productoRepository, usuarioRepository);
        Usuario juan = new Usuario();
        juan.setId(1L);
        juan.setUsername("juan");
        juan.setPassword("hash");
        juan.setRol(Rol.CLIENTE);
        org.mockito.Mockito.lenient().when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(juan));
    }

    private static Producto producto(Long id, String precio, int stock) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setSku("SKU-" + id);
        producto.setNombre("Producto " + id);
        producto.setPrecio(new BigDecimal(precio));
        producto.setStock(stock);
        return producto;
    }

    private static ItemCarrito linea(Long id, Long productoId, int cantidad) {
        ItemCarrito linea = new ItemCarrito();
        linea.setId(id);
        linea.setCantidad(cantidad);
        Producto producto = producto(productoId, "10.00", 5);
        linea.setProducto(producto);
        return linea;
    }

    // ---------- agregar ----------

    @Test
    void agregar_productoNuevo_creaLinea() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1L, "10.00", 5)));
        when(itemCarritoRepository.findByUsuarioUsernameAndProductoId("juan", 1L)).thenReturn(Optional.empty());
        when(itemCarritoRepository.save(any(ItemCarrito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LineaCarritoResponseDTO response = carritoService.agregar("juan", 1L, 2);

        assertThat(response.cantidad()).isEqualTo(2);
        assertThat(response.productoId()).isEqualTo(1L);
        verify(itemCarritoRepository).save(any(ItemCarrito.class));
    }

    @Test
    void agregar_productoExistente_fusionaEnUnaSolaLinea() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1L, "10.00", 5)));
        ItemCarrito existente = linea(10L, 1L, 2);
        when(itemCarritoRepository.findByUsuarioUsernameAndProductoId("juan", 1L))
                .thenReturn(Optional.of(existente));
        when(itemCarritoRepository.save(any(ItemCarrito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LineaCarritoResponseDTO response = carritoService.agregar("juan", 1L, 3);

        assertThat(response.cantidad()).isEqualTo(5);
        verify(itemCarritoRepository, never()).delete(any(ItemCarrito.class));
    }

    @Test
    void agregar_productoDesconocido_lanza404() {
        when(productoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> carritoService.agregar("juan", 999L, 1))
                .isInstanceOf(ProductoNotFoundException.class);
        verify(itemCarritoRepository, never()).save(any(ItemCarrito.class));
    }

    @Test
    void agregar_cantidadMayorAlStock_igualAceptada() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1L, "10.00", 1)));
        when(itemCarritoRepository.findByUsuarioUsernameAndProductoId("juan", 1L)).thenReturn(Optional.empty());
        when(itemCarritoRepository.save(any(ItemCarrito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LineaCarritoResponseDTO response = carritoService.agregar("juan", 1L, 5);

        assertThat(response.cantidad()).isEqualTo(5);
    }

    // ---------- actualizarCantidad ----------

    @Test
    void actualizarCantidad_seteaLaCantidad() {
        ItemCarrito existente = linea(10L, 1L, 2);
        when(itemCarritoRepository.findByIdAndUsuarioUsername(10L, "juan")).thenReturn(Optional.of(existente));
        when(itemCarritoRepository.save(any(ItemCarrito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LineaCarritoResponseDTO response = carritoService.actualizarCantidad("juan", 10L, 7);

        assertThat(response.cantidad()).isEqualTo(7);
    }

    @Test
    void actualizarCantidad_lineaAjena_lanza404() {
        when(itemCarritoRepository.findByIdAndUsuarioUsername(10L, "juan")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> carritoService.actualizarCantidad("juan", 10L, 7))
                .isInstanceOf(ItemCarritoNotFoundException.class);
    }

    // ---------- eliminarLinea ----------

    @Test
    void eliminarLinea_lineaAjena_lanza404() {
        when(itemCarritoRepository.findByIdAndUsuarioUsername(10L, "juan")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> carritoService.eliminarLinea("juan", 10L))
                .isInstanceOf(ItemCarritoNotFoundException.class);
        verify(itemCarritoRepository, never()).delete(any(ItemCarrito.class));
    }

    // ---------- ver ----------

    @Test
    void ver_resuelvePreciosActualesYTotal() {
        ItemCarrito l1 = linea(10L, 1L, 2);
        l1.getProducto().setPrecio(new BigDecimal("10.00"));
        ItemCarrito l2 = linea(11L, 2L, 1);
        l2.getProducto().setPrecio(new BigDecimal("20.00"));
        when(itemCarritoRepository.findByUsuarioUsername("juan")).thenReturn(List.of(l1, l2));

        CarritoResponseDTO response = carritoService.ver("juan");

        assertThat(response.items()).hasSize(2);
        assertThat(response.total()).isEqualByComparingTo("40.00");
        assertThat(response.items().get(0).nombre()).isEqualTo("Producto 1");
    }

    @Test
    void ver_excluyeYPurgaLineaDeProductoBorrado() {
        ItemCarrito huerfana = new ItemCarrito();
        huerfana.setId(10L);
        huerfana.setCantidad(2);
        huerfana.setProducto(null);
        ItemCarrito valida = linea(11L, 2L, 1);
        when(itemCarritoRepository.findByUsuarioUsername("juan")).thenReturn(List.of(huerfana, valida));

        CarritoResponseDTO response = carritoService.ver("juan");

        assertThat(response.items()).hasSize(1);
        assertThat(response.total()).isEqualByComparingTo("10.00");
        verify(itemCarritoRepository).delete(huerfana);
    }

    // ---------- vaciar ----------

    @Test
    void vaciar_eliminaTodasLasLineasDelUsuario() {
        carritoService.vaciar("juan");

        verify(itemCarritoRepository).deleteByUsuarioUsername("juan");
    }
}
