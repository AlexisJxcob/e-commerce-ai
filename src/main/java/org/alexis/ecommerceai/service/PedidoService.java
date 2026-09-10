package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.dto.ItemPedidoResponseDTO;
import org.alexis.ecommerceai.dto.LineaPedidoDTO;
import org.alexis.ecommerceai.dto.PedidoRequestDTO;
import org.alexis.ecommerceai.dto.PedidoResponseDTO;
import org.alexis.ecommerceai.exception.CarritoVacioException;
import org.alexis.ecommerceai.exception.PedidoNotFoundException;
import org.alexis.ecommerceai.exception.ProductoNotFoundException;
import org.alexis.ecommerceai.exception.RecursoNoEncontradoException;
import org.alexis.ecommerceai.exception.StockInsuficienteException;
import org.alexis.ecommerceai.exception.StockUpdateConflictException;
import org.alexis.ecommerceai.exception.TransicionEstadoInvalidaException;
import org.alexis.ecommerceai.model.Carrito;
import org.alexis.ecommerceai.model.EstadoPedido;
import org.alexis.ecommerceai.model.ItemPedido;
import org.alexis.ecommerceai.model.Pedido;
import org.alexis.ecommerceai.model.Producto;
import org.alexis.ecommerceai.model.Usuario;
import org.alexis.ecommerceai.repository.CarritoRepository;
import org.alexis.ecommerceai.repository.PedidoRepository;
import org.alexis.ecommerceai.repository.ProductoRepository;
import org.alexis.ecommerceai.repository.UsuarioRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creación y consulta de pedidos.
 *
 * <p>Modelo de concurrencia sobre el stock: la comprobación previa es solo un
 * filtro barato para devolver 400 (petición imposible por sí misma); la
 * garantía real la da el bloqueo optimista de {@code Producto.version}. El
 * decremento se fuerza con {@code saveAndFlush} <b>dentro</b> del try, porque
 * el {@code UPDATE} versionado se emite en el flush: si se dejara al commit de
 * la transacción, el {@code OptimisticLockException} estallaría fuera del catch
 * y el cliente vería un 500 en lugar del 409.</p>
 *
 * <p>Atomicidad del carrito: al crear un pedido desde el carrito, el vaciado
 * ocurre en la misma transacción y <b>después</b> de persistir el pedido, así
 * que un fallo (stock, lock, FK) revierte ambos efectos: nunca queda un carrito
 * vacío sin pedido.</p>
 */
@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CarritoRepository carritoRepository;

    public PedidoService(PedidoRepository pedidoRepository,
                         ProductoRepository productoRepository,
                         UsuarioRepository usuarioRepository,
                         CarritoRepository carritoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.carritoRepository = carritoRepository;
    }

    @Transactional
    public PedidoResponseDTO create(String username, PedidoRequestDTO request) {
        Usuario usuario = resolverUsuario(username);
        return crear(usuario, request.items());
    }

    /**
     * Crea el pedido a partir del carrito persistente del usuario y lo vacía
     * solo si el pedido se creó correctamente.
     */
    @Transactional
    public PedidoResponseDTO crearDesdeCarrito(String username) {
        Usuario usuario = resolverUsuario(username);
        Carrito carrito = carritoRepository.findConItemsByUsuarioId(usuario.getId())
                .filter(c -> !c.getItems().isEmpty())
                .orElseThrow(() -> new CarritoVacioException(
                        "El carrito está vacío: no hay productos para generar un pedido"));

        List<LineaPedidoDTO> lineas = carrito.getItems().stream()
                .map(item -> new LineaPedidoDTO(item.getProducto().getId(), item.getCantidad()))
                .toList();

        PedidoResponseDTO pedido = crear(usuario, lineas);

        // Misma transacción que el pedido: si el pedido no se persistió,
        // esta línea no se ejecuta y el carrito queda intacto.
        carrito.vaciar();
        carritoRepository.save(carrito);
        return pedido;
    }

    private PedidoResponseDTO crear(Usuario usuario, List<LineaPedidoDTO> lineas) {
        Map<Long, Producto> productos = cargarProductos(lineas);

        // Primera pasada: validar todo antes de mutar nada (sin pedido parcial).
        // Stock insuficiente ya en la petición → 400.
        for (LineaPedidoDTO linea : lineas) {
            Producto producto = productos.get(linea.productoId());
            if (producto == null) {
                throw new ProductoNotFoundException("Producto no encontrado con id: " + linea.productoId());
            }
            if (producto.getStock() < linea.cantidad()) {
                throw new StockInsuficienteException(
                        "Stock insuficiente para el producto con id: " + linea.productoId()
                                + " (disponible: " + producto.getStock() + ", solicitado: " + linea.cantidad() + ")");
            }
        }

        // Segunda pasada: snapshot de precios + decremento, en orden determinista
        // (un orden estable entre transacciones evita deadlocks por tomar las
        // mismas filas en distinto orden).
        try {
            var pedido = new Pedido();
            pedido.setUsuario(usuario);
            pedido.setFechaCreacion(LocalDateTime.now());
            pedido.setEstado(EstadoPedido.PENDIENTE);
            BigDecimal total = BigDecimal.ZERO;
            List<ItemPedido> items = new ArrayList<>();
            for (LineaPedidoDTO linea : lineas.stream()
                    .sorted(Comparator.comparing(LineaPedidoDTO::productoId))
                    .toList()) {
                Producto producto = productos.get(linea.productoId());
                producto.setStock(producto.getStock() - linea.cantidad());
                // flush explícito: aquí es donde el UPDATE lleva el WHERE version
                // y donde se detecta que otra transacción ganó la última unidad.
                productoRepository.saveAndFlush(producto);

                var item = new ItemPedido();
                item.setPedido(pedido);
                item.setProductoId(producto.getId());
                item.setCantidad(linea.cantidad());
                item.setPrecioUnitario(producto.getPrecio());
                items.add(item);
                total = total.add(producto.getPrecio().multiply(BigDecimal.valueOf(linea.cantidad())));
            }
            pedido.setItems(items);
            pedido.setTotal(total);
            pedido = pedidoRepository.save(pedido);
            return toResponseDTO(pedido);
        } catch (OptimisticLockingFailureException e) {
            // Carrera perdida por la última unidad → 409, sin stacktrace al cliente.
            throw new StockUpdateConflictException(
                    "Conflicto de concurrencia al registrar el pedido: el stock cambió mientras se procesaba. "
                            + "Intente nuevamente.");
        }
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listar(String username) {
        return pedidoRepository.findByUsuarioUsernameOrderByIdDesc(username).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO obtener(String username, boolean esAdmin, Long id) {
        var pedido = pedidoRepository.findConItemsById(id)
                .orElseThrow(() -> new PedidoNotFoundException("Pedido no encontrado con id: " + id));
        if (!esAdmin && !pedido.getUsuario().getUsername().equals(username)) {
            throw new PedidoNotFoundException("Pedido no encontrado con id: " + id);
        }
        return toResponseDTO(pedido);
    }

    /**
     * Cambia el estado de un pedido validando la máquina de estados.
     * El llamador (endpoint ADMIN) ya garantizó el rol desde el JWT.
     */
    @Transactional
    public PedidoResponseDTO cambiarEstado(Long id, EstadoPedido nuevoEstado) {
        var pedido = pedidoRepository.findConItemsById(id)
                .orElseThrow(() -> new PedidoNotFoundException("Pedido no encontrado con id: " + id));
        EstadoPedido actual = pedido.getEstado();
        if (actual != null && actual == nuevoEstado) {
            return toResponseDTO(pedido);
        }
        if (actual == null || !actual.puedeTransicionarA(nuevoEstado)) {
            throw new TransicionEstadoInvalidaException(
                    "Transición inválida: no se puede pasar de " + actual + " a " + nuevoEstado
                            + " (transiciones permitidas desde " + actual + ": "
                            + (actual != null ? actual.transicionesValidas() : "ninguna") + ")");
        }
        pedido.setEstado(nuevoEstado);
        return toResponseDTO(pedidoRepository.save(pedido));
    }

    private Map<Long, Producto> cargarProductos(List<LineaPedidoDTO> lineas) {
        List<Long> ids = lineas.stream()
                .map(LineaPedidoDTO::productoId)
                .distinct()
                .sorted()
                .toList();
        return productoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));
    }

    private Usuario resolverUsuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + username));
    }

    private PedidoResponseDTO toResponseDTO(Pedido pedido) {
        List<ItemPedidoResponseDTO> items = pedido.getItems() == null ? List.of() : pedido.getItems().stream()
                .map(item -> new ItemPedidoResponseDTO(
                        item.getProductoId(),
                        item.getCantidad(),
                        item.getPrecioUnitario(),
                        item.getPrecioUnitario().multiply(BigDecimal.valueOf(item.getCantidad()))
                ))
                .toList();
        return new PedidoResponseDTO(
                pedido.getId(),
                pedido.getEstado() != null ? pedido.getEstado().name() : null,
                pedido.getTotal(),
                pedido.getFechaCreacion(),
                items
        );
    }
}
