package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.dto.CarritoResponseDTO;
import org.alexis.ecommerceai.dto.LineaCarritoResponseDTO;
import org.alexis.ecommerceai.exception.CarritoItemNotFoundException;
import org.alexis.ecommerceai.exception.ProductoNotFoundException;
import org.alexis.ecommerceai.model.Carrito;
import org.alexis.ecommerceai.model.CarritoItem;
import org.alexis.ecommerceai.model.Producto;
import org.alexis.ecommerceai.model.Rol;
import org.alexis.ecommerceai.model.Usuario;
import org.alexis.ecommerceai.repository.CarritoItemRepository;
import org.alexis.ecommerceai.repository.CarritoRepository;
import org.alexis.ecommerceai.repository.ProductoRepository;
import org.alexis.ecommerceai.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarritoServiceTest {

    private static final Long CARRITO_ID = 50L;

    @Mock
    private CarritoRepository carritoRepository;

    @Mock
    private CarritoItemRepository carritoItemRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private CarritoService carritoService;

    @BeforeEach
    void setUp() {
        carritoService = new CarritoService(
                carritoRepository, carritoItemRepository, productoRepository, usuarioRepository);
        Usuario juan = new Usuario();
        juan.setId(1L);
        juan.setUsername("juan");
        juan.setPassword("hash");
        juan.setRol(Rol.CLIENTE);
        lenient().when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(juan));
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

    private static Carrito carrito() {
        Carrito carrito = new Carrito();
        carrito.setId(CARRITO_ID);
        return carrito;
    }

    private static CarritoItem linea(Long id, Long productoId, int cantidad) {
        CarritoItem item = new CarritoItem();
        item.setId(id);
        item.setCantidad(cantidad);
        item.setProducto(producto(productoId, "10.00", 5));
        return item;
    }

    /** Simula la generación IDENTITY al persistir una línea nueva. */
    private void persistirLineaConId(Long id) {
        when(carritoItemRepository.save(any(CarritoItem.class))).thenAnswer(invocation -> {
            CarritoItem item = invocation.getArgument(0);
            if (item.getId() == null) {
                item.setId(id);
            }
            return item;
        });
    }

    // ---------- agregar ----------

    @Test
    void agregar_primerProducto_creaCarritoYLinea() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1L, "10.00", 5)));
        when(carritoRepository.findByUsuarioId(1L)).thenReturn(Optional.empty());
        when(carritoRepository.save(any(Carrito.class))).thenAnswer(invocation -> {
            Carrito nuevo = invocation.getArgument(0);
            nuevo.setId(CARRITO_ID);
            return nuevo;
        });
        when(carritoItemRepository.findByCarritoIdAndProductoId(CARRITO_ID, 1L)).thenReturn(Optional.empty());
        persistirLineaConId(100L);

        LineaCarritoResponseDTO response = carritoService.agregar("juan", 1L, 2);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.cantidad()).isEqualTo(2);
        assertThat(response.productoId()).isEqualTo(1L);
        verify(carritoRepository).save(any(Carrito.class));
        verify(carritoItemRepository).save(any(CarritoItem.class));
    }

    @Test
    void agregar_carritoYaExistente_noCreaOtro() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1L, "10.00", 5)));
        when(carritoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(carrito()));
        when(carritoItemRepository.findByCarritoIdAndProductoId(CARRITO_ID, 1L)).thenReturn(Optional.empty());
        persistirLineaConId(100L);

        carritoService.agregar("juan", 1L, 1);

        verify(carritoRepository, never()).save(any(Carrito.class));
    }

    @Test
    void agregar_productoExistente_fusionaEnUnaSolaLinea() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1L, "10.00", 5)));
        when(carritoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(carrito()));
        when(carritoItemRepository.findByCarritoIdAndProductoId(CARRITO_ID, 1L))
                .thenReturn(Optional.of(linea(100L, 1L, 2)));
        persistirLineaConId(100L);

        LineaCarritoResponseDTO response = carritoService.agregar("juan", 1L, 3);

        assertThat(response.cantidad()).isEqualTo(5);
        verify(carritoItemRepository).save(any(CarritoItem.class));
    }

    @Test
    void agregar_productoDesconocido_lanza404() {
        when(productoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> carritoService.agregar("juan", 999L, 1))
                .isInstanceOf(ProductoNotFoundException.class);
        verify(carritoItemRepository, never()).save(any(CarritoItem.class));
    }

    @Test
    void agregar_cantidadMayorAlStock_igualAceptada() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1L, "10.00", 1)));
        when(carritoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(carrito()));
        when(carritoItemRepository.findByCarritoIdAndProductoId(CARRITO_ID, 1L)).thenReturn(Optional.empty());
        persistirLineaConId(100L);

        LineaCarritoResponseDTO response = carritoService.agregar("juan", 1L, 5);

        assertThat(response.cantidad()).isEqualTo(5);
    }

    // ---------- actualizarCantidad ----------

    @Test
    void actualizarCantidad_seteaLaCantidad() {
        when(carritoItemRepository.findByIdAndCarritoUsuarioUsername(100L, "juan"))
                .thenReturn(Optional.of(linea(100L, 1L, 2)));
        persistirLineaConId(100L);

        LineaCarritoResponseDTO response = carritoService.actualizarCantidad("juan", 100L, 7);

        assertThat(response.cantidad()).isEqualTo(7);
    }

    @Test
    void actualizarCantidad_lineaAjena_lanza404() {
        when(carritoItemRepository.findByIdAndCarritoUsuarioUsername(100L, "juan"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> carritoService.actualizarCantidad("juan", 100L, 7))
                .isInstanceOf(CarritoItemNotFoundException.class);
    }

    // ---------- eliminarLinea ----------

    @Test
    void eliminarLinea_lineaAjena_lanza404() {
        Carrito carrito = carrito();
        carrito.agregarItem(linea(100L, 1L, 2));
        when(carritoRepository.findByUsuarioUsername("juan")).thenReturn(Optional.of(carrito));

        assertThatThrownBy(() -> carritoService.eliminarLinea("juan", 999L))
                .isInstanceOf(CarritoItemNotFoundException.class);
        // La línea propia sigue en el agregado: no se borró nada
        assertThat(carrito.getItems()).hasSize(1);
    }

    @Test
    void eliminarLinea_sinCarrito_lanza404() {
        when(carritoRepository.findByUsuarioUsername("juan")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> carritoService.eliminarLinea("juan", 100L))
                .isInstanceOf(CarritoItemNotFoundException.class);
    }

    @Test
    void eliminarLinea_propia_laQuitaDelAgregadoYGuarda() {
        Carrito carrito = carrito();
        CarritoItem propia = linea(100L, 1L, 2);
        CarritoItem otra = linea(101L, 2L, 1);
        carrito.agregarItem(propia);
        carrito.agregarItem(otra);
        when(carritoRepository.findByUsuarioUsername("juan")).thenReturn(Optional.of(carrito));

        carritoService.eliminarLinea("juan", 100L);

        // Se quita de la colección (orphanRemoval borra la fila): el carrito en
        // memoria queda consistente con la base dentro de la misma transacción
        assertThat(carrito.getItems()).containsExactly(otra);
        verify(carritoRepository).save(carrito);
    }

    // ---------- ver ----------

    @Test
    void ver_resuelvePreciosActualesYTotal() {
        Carrito carrito = carrito();
        CarritoItem l1 = linea(100L, 1L, 2);
        l1.getProducto().setPrecio(new BigDecimal("10.00"));
        CarritoItem l2 = linea(101L, 2L, 1);
        l2.getProducto().setPrecio(new BigDecimal("20.00"));
        carrito.agregarItem(l1);
        carrito.agregarItem(l2);
        when(carritoRepository.findByUsuarioUsername("juan")).thenReturn(Optional.of(carrito));

        CarritoResponseDTO response = carritoService.ver("juan");

        assertThat(response.items()).hasSize(2);
        assertThat(response.total()).isEqualByComparingTo("40.00");
        assertThat(response.items().get(0).nombre()).isEqualTo("Producto 1");
    }

    @Test
    void ver_sinCarrito_devuelveCarritoVacio() {
        when(carritoRepository.findByUsuarioUsername("juan")).thenReturn(Optional.empty());

        CarritoResponseDTO response = carritoService.ver("juan");

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isEqualByComparingTo("0");
    }

    // ---------- vaciar ----------

    @Test
    void vaciar_eliminaTodasLasLineasDelUsuario() {
        Carrito carrito = carrito();
        carrito.agregarItem(linea(100L, 1L, 2));
        when(carritoRepository.findByUsuarioId(1L)).thenReturn(Optional.of(carrito));
        when(carritoRepository.save(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        carritoService.vaciar("juan");

        assertThat(carrito.getItems()).isEmpty();
        verify(carritoRepository).save(carrito);
    }

    @Test
    void vaciar_sinCarrito_noHaceNada() {
        when(carritoRepository.findByUsuarioId(1L)).thenReturn(Optional.empty());

        carritoService.vaciar("juan");

        verify(carritoRepository, never()).save(any(Carrito.class));
    }
}
