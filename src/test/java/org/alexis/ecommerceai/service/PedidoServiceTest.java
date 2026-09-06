package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.dto.LineaPedidoDTO;
import org.alexis.ecommerceai.dto.PedidoRequestDTO;
import org.alexis.ecommerceai.dto.PedidoResponseDTO;
import org.alexis.ecommerceai.exception.PedidoNotFoundException;
import org.alexis.ecommerceai.exception.ProductoNotFoundException;
import org.alexis.ecommerceai.exception.StockInsuficienteException;
import org.alexis.ecommerceai.exception.StockUpdateConflictException;
import org.alexis.ecommerceai.model.Pedido;
import org.alexis.ecommerceai.model.Producto;
import org.alexis.ecommerceai.model.Rol;
import org.alexis.ecommerceai.model.Usuario;
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

    private PedidoService pedidoService;

    @BeforeEach
    void setUp() {
        pedidoService = new PedidoService(pedidoRepository, productoRepository, usuarioRepository);
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
    void crear_conStockInsuficiente_lanza409SinEfectosParciales() {
        Producto p1 = producto(1L, "10.00", 5);
        Producto p2 = producto(2L, "20.00", 1);
        when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(cliente()));
        when(productoRepository.findAllById(any())).thenReturn(List.of(p1, p2));

        assertThatThrownBy(() -> pedidoService.create("juan",
                new PedidoRequestDTO(List.of(new LineaPedidoDTO(1L, 2), new LineaPedidoDTO(2L, 5)))))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("Stock insuficiente");
        verify(pedidoRepository, never()).save(any(Pedido.class));
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
        when(productoRepository.save(any(Producto.class)))
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
        when(pedidoRepository.findById(7L)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> pedidoService.obtener("pedro", false, 7L))
                .isInstanceOf(PedidoNotFoundException.class);
    }

    @Test
    void obtener_pedidoAjenoComoAdmin_devuelvePedido() {
        Pedido pedido = new Pedido();
        pedido.setId(7L);
        pedido.setUsuario(cliente());
        pedido.setItems(List.of());
        when(pedidoRepository.findById(7L)).thenReturn(Optional.of(pedido));

        PedidoResponseDTO response = pedidoService.obtener("admin", true, 7L);

        assertThat(response.id()).isEqualTo(7L);
    }

    @Test
    void obtener_inexistente_lanza404() {
        when(pedidoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pedidoService.obtener("juan", false, 99L))
                .isInstanceOf(PedidoNotFoundException.class);
    }
}
