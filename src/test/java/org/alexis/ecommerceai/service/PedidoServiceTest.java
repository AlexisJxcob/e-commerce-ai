package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.dto.LineaPedidoDTO;
import org.alexis.ecommerceai.dto.PedidoRequestDTO;
import org.alexis.ecommerceai.dto.PedidoResponseDTO;
import org.alexis.ecommerceai.exception.CarritoVacioException;
import org.alexis.ecommerceai.exception.PedidoNotFoundException;
import org.alexis.ecommerceai.exception.ProductoNotFoundException;
import org.alexis.ecommerceai.exception.StockInsuficienteException;
import org.alexis.ecommerceai.exception.StockUpdateConflictException;
import org.alexis.ecommerceai.exception.TransicionEstadoInvalidaException;
import org.alexis.ecommerceai.model.Carrito;
import org.alexis.ecommerceai.model.CarritoItem;
import org.alexis.ecommerceai.model.EstadoPedido;
import org.alexis.ecommerceai.model.Pedido;
import org.alexis.ecommerceai.model.Producto;
import org.alexis.ecommerceai.model.Rol;
import org.alexis.ecommerceai.model.Usuario;
import org.alexis.ecommerceai.repository.CarritoRepository;
import org.alexis.ecommerceai.repository.PedidoRepository;
import org.alexis.ecommerceai.repository.ProductoRepository;
import org.alexis.ecommerceai.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

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
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private CarritoRepository carritoRepository;

    private PedidoService pedidoService;

    @BeforeEach
    void setUp() {
        pedidoService = new PedidoService(
                pedidoRepository, productoRepository, usuarioRepository, carritoRepository);
    }

    private static Usuario cliente() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setUsername("juan");
        usuario.setPassword("hash");
        usuario.setRol(Rol.CLIENTE);
        return usuario;
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

    // ---------- create ----------

    @Test
    void crear_conStockSuficiente_creaPedidoConSnapshotYDecrementaStock() {
        Producto p1 = producto(1L, "10.00", 5);
        Producto p2 = producto(2L, "20.00", 3);
        when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(cliente()));
        when(productoRepository.findAllById(any())).thenReturn(List.of(p1, p2));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> {
            Pedido guardado = invocation.getArgument(0);
            guardado.setId(100L);
            return guardado;
        });

        PedidoResponseDTO response = pedidoService.create("juan",
                new PedidoRequestDTO(List.of(new LineaPedidoDTO(1L, 2), new LineaPedidoDTO(2L, 1))));

        assertThat(response.estado()).isEqualTo("PENDIENTE");
        assertThat(response.total()).isEqualByComparingTo("40.00");
        assertThat(response.items()).hasSize(2);
        assertThat(p1.getStock()).isEqualTo(3);
        assertThat(p2.getStock()).isEqualTo(2);
    }

    @Test
    void crear_conStockInsuficiente_lanza400SinEfectosParciales() {
        Producto p1 = producto(1L, "10.00", 5);
        Producto p2 = producto(2L, "20.00", 1);
        when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(cliente()));
        when(productoRepository.findAllById(any())).thenReturn(List.of(p1, p2));

        assertThatThrownBy(() -> pedidoService.create("juan",
                new PedidoRequestDTO(List.of(new LineaPedidoDTO(1L, 2), new LineaPedidoDTO(2L, 5)))))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("Stock insuficiente");
        verify(pedidoRepository, never()).save(any(Pedido.class));
        // Ni siquiera se intentó decrementar: la validación precede a la mutación
        verify(productoRepository, never()).saveAndFlush(any(Producto.class));
        assertThat(p1.getStock()).isEqualTo(5);
        assertThat(p2.getStock()).isEqualTo(1);
    }

    @Test
    void crear_conProductoDesconocido_lanza404() {
        when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(cliente()));
        when(productoRepository.findAllById(any())).thenReturn(List.of(producto(1L, "10.00", 5)));

        assertThatThrownBy(() -> pedidoService.create("juan",
                new PedidoRequestDTO(List.of(new LineaPedidoDTO(999L, 1)))))
                .isInstanceOf(ProductoNotFoundException.class);
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void crear_conRaceEnUltimaUnidad_lanza409() {
        Producto p1 = producto(1L, "10.00", 1);
        when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(cliente()));
        when(productoRepository.findAllById(any())).thenReturn(List.of(p1));
        // El flush versionado es el punto donde se detecta que otra transacción
        // ganó la última unidad.
        when(productoRepository.saveAndFlush(any(Producto.class)))
                .thenThrow(new OptimisticLockingFailureException("versión desactualizada"));

        assertThatThrownBy(() -> pedidoService.create("juan",
                new PedidoRequestDTO(List.of(new LineaPedidoDTO(1L, 1)))))
                .isInstanceOf(StockUpdateConflictException.class);
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void crear_guardaSnapshotInmutableAnteCambiosDePrecio() {
        Producto p1 = producto(1L, "10.00", 5);
        when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(cliente()));
        when(productoRepository.findAllById(any())).thenReturn(List.of(p1));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        pedidoService.create("juan", new PedidoRequestDTO(List.of(new LineaPedidoDTO(1L, 2))));

        // El precio cambia después de la compra: el snapshot no se mueve
        p1.setPrecio(new BigDecimal("99.99"));
        var captor = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository).save(captor.capture());
        assertThat(captor.getValue().getItems().get(0).getPrecioUnitario()).isEqualByComparingTo("10.00");
        assertThat(captor.getValue().getTotal()).isEqualByComparingTo("20.00");
    }

    // ---------- listar ----------

    @Test
    void listar_devuelveSoloPedidosDelUsuario() {
        when(pedidoRepository.findByUsuarioUsernameOrderByIdDesc("juan")).thenReturn(List.of());

        pedidoService.listar("juan");

        verify(pedidoRepository).findByUsuarioUsernameOrderByIdDesc("juan");
    }

    // ---------- obtener ----------

    @Test
    void obtener_pedidoAjenoComoNoAdmin_lanza404() {
        Pedido pedido = new Pedido();
        pedido.setId(7L);
        pedido.setUsuario(cliente());
        when(pedidoRepository.findConItemsById(7L)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.obtener("pedro", false, 7L))
                .isInstanceOf(PedidoNotFoundException.class);
    }

    @Test
    void obtener_pedidoAjenoComoAdmin_devuelvePedido() {
        Pedido pedido = new Pedido();
        pedido.setId(7L);
        pedido.setUsuario(cliente());
        pedido.setItems(List.of());
        when(pedidoRepository.findConItemsById(7L)).thenReturn(Optional.of(pedido));

        PedidoResponseDTO response = pedidoService.obtener("admin", true, 7L);

        assertThat(response.id()).isEqualTo(7L);
    }

    @Test
    void obtener_inexistente_lanza404() {
        when(pedidoRepository.findConItemsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pedidoService.obtener("juan", false, 99L))
                .isInstanceOf(PedidoNotFoundException.class);
    }

    // ---------- crearDesdeCarrito ----------

    private static Carrito carritoCon(Usuario usuario, CarritoItem... items) {
        Carrito carrito = new Carrito();
        carrito.setId(50L);
        carrito.setUsuario(usuario);
        for (CarritoItem item : items) {
            carrito.agregarItem(item);
        }
        return carrito;
    }

    private static CarritoItem linea(Producto producto, int cantidad) {
        CarritoItem item = new CarritoItem();
        item.setId(500L + producto.getId());
        item.setProducto(producto);
        item.setCantidad(cantidad);
        return item;
    }

    @Test
    void crearDesdeCarrito_creaPedidoYVaciaElCarrito() {
        Usuario juan = cliente();
        Producto p1 = producto(1L, "10.00", 5);
        Carrito carrito = carritoCon(juan, linea(p1, 2));
        when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(juan));
        when(carritoRepository.findConItemsByUsuarioId(1L)).thenReturn(Optional.of(carrito));
        when(productoRepository.findAllById(any())).thenReturn(List.of(p1));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        PedidoResponseDTO response = pedidoService.crearDesdeCarrito("juan");

        assertThat(response.estado()).isEqualTo("PENDIENTE");
        assertThat(response.total()).isEqualByComparingTo("20.00");
        assertThat(p1.getStock()).isEqualTo(3);
        // El carrito se vacía DESPUÉS de crear el pedido, en la misma transacción
        assertThat(carrito.getItems()).isEmpty();
        verify(carritoRepository).save(carrito);
    }

    @Test
    void crearDesdeCarrito_sinCarrito_lanza400YNoCreaPedido() {
        Usuario juan = cliente();
        when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(juan));
        when(carritoRepository.findConItemsByUsuarioId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pedidoService.crearDesdeCarrito("juan"))
                .isInstanceOf(CarritoVacioException.class);
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void crearDesdeCarrito_conCarritoVacio_lanza400YNoVaciaNada() {
        Usuario juan = cliente();
        Carrito carrito = carritoCon(juan);
        when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(juan));
        when(carritoRepository.findConItemsByUsuarioId(1L)).thenReturn(Optional.of(carrito));

        assertThatThrownBy(() -> pedidoService.crearDesdeCarrito("juan"))
                .isInstanceOf(CarritoVacioException.class);
        verify(carritoRepository, never()).save(any(Carrito.class));
    }

    @Test
    void crearDesdeCarrito_conStockInsuficiente_noVaciaElCarrito() {
        Usuario juan = cliente();
        Producto p1 = producto(1L, "10.00", 1);
        Carrito carrito = carritoCon(juan, linea(p1, 4));
        when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(juan));
        when(carritoRepository.findConItemsByUsuarioId(1L)).thenReturn(Optional.of(carrito));
        when(productoRepository.findAllById(any())).thenReturn(List.of(p1));

        assertThatThrownBy(() -> pedidoService.crearDesdeCarrito("juan"))
                .isInstanceOf(StockInsuficienteException.class);

        // El carrito queda intacto: el vaciado nunca se alcanzó
        assertThat(carrito.getItems()).hasSize(1);
        verify(carritoRepository, never()).save(any(Carrito.class));
    }

    // ---------- cambiarEstado ----------

    private static Pedido pedidoConEstado(EstadoPedido estado) {
        Pedido pedido = new Pedido();
        pedido.setId(7L);
        pedido.setUsuario(cliente());
        pedido.setEstado(estado);
        pedido.setTotal(new BigDecimal("10.00"));
        pedido.setItems(List.of());
        return pedido;
    }

    @Test
    void cambiarEstado_transicionValida_actualizaEstado() {
        Pedido pedido = pedidoConEstado(EstadoPedido.PENDIENTE);
        when(pedidoRepository.findConItemsById(7L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        PedidoResponseDTO response = pedidoService.cambiarEstado(7L, EstadoPedido.CONFIRMADO);

        assertThat(response.estado()).isEqualTo("CONFIRMADO");
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);
    }

    @Test
    void cambiarEstado_transicionInvalida_lanza409() {
        Pedido pedido = pedidoConEstado(EstadoPedido.PENDIENTE);
        when(pedidoRepository.findConItemsById(7L)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.cambiarEstado(7L, EstadoPedido.ENTREGADO))
                .isInstanceOf(TransicionEstadoInvalidaException.class)
                .hasMessageContaining("Transición inválida");
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.PENDIENTE);
    }

    @Test
    void cambiarEstado_desdeTerminal_lanza409() {
        Pedido pedido = pedidoConEstado(EstadoPedido.ENTREGADO);
        when(pedidoRepository.findConItemsById(7L)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.cambiarEstado(7L, EstadoPedido.CANCELADO))
                .isInstanceOf(TransicionEstadoInvalidaException.class);
    }

    @Test
    void cambiarEstado_mismoEstado_esIdempotente() {
        Pedido pedido = pedidoConEstado(EstadoPedido.PENDIENTE);
        when(pedidoRepository.findConItemsById(7L)).thenReturn(Optional.of(pedido));

        PedidoResponseDTO response = pedidoService.cambiarEstado(7L, EstadoPedido.PENDIENTE);

        assertThat(response.estado()).isEqualTo("PENDIENTE");
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }
}
