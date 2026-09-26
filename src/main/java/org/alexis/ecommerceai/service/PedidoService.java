package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.dto.CotizacionResponseDTO;
import org.alexis.ecommerceai.dto.DatosCheckoutDTO;
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
import org.alexis.ecommerceai.model.EstadoPago;
import org.alexis.ecommerceai.model.EstadoPedido;
import org.alexis.ecommerceai.model.ItemPedido;
import org.alexis.ecommerceai.model.MetodoEntrega;
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
    private final EnvioService envioService;

    public PedidoService(PedidoRepository pedidoRepository,
                         ProductoRepository productoRepository,
                         UsuarioRepository usuarioRepository,
                         CarritoRepository carritoRepository,
                         EnvioService envioService) {
        this.pedidoRepository = pedidoRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.carritoRepository = carritoRepository;
        this.envioService = envioService;
    }

    @Transactional
    public PedidoResponseDTO create(String username, PedidoRequestDTO request) {
        Usuario usuario = resolverUsuario(username);
        return crear(usuario, request.items(), null);
    }

    /**
     * Crea el pedido desde el carrito sin datos de comprador/entrega.
     * Se mantiene por compatibilidad; el checkout usa la variante con datos.
     */
    @Transactional
    public PedidoResponseDTO crearDesdeCarrito(String username) {
        return crearDesdeCarrito(username, null);
    }

    /**
     * Crea el pedido a partir del carrito persistente del usuario, persistiendo
     * los datos de comprador y entrega recolectados en el checkout, y vacía el
     * carrito solo si el pedido se creó correctamente.
     *
     * <p>El costo de entrega se calcula con {@link EnvioService}, la misma
     * fuente que alimenta la cotización mostrada en pantalla: lo que se ve y lo
     * que se cobra no pueden divergir.</p>
     */
    @Transactional
    public PedidoResponseDTO crearDesdeCarrito(String username, DatosCheckoutDTO datos) {
        Usuario usuario = resolverUsuario(username);
        Carrito carrito = carritoRepository.findConItemsByUsuarioId(usuario.getId())
                .filter(c -> !c.getItems().isEmpty())
                .orElseThrow(() -> new CarritoVacioException(
                        "El carrito está vacío: no hay productos para generar un pedido"));

        List<LineaPedidoDTO> lineas = carrito.getItems().stream()
                .map(item -> new LineaPedidoDTO(item.getProducto().getId(), item.getCantidad()))
                .toList();

        PedidoResponseDTO pedido = crear(usuario, lineas, datos);

        // Misma transacción que el pedido: si el pedido no se persistió,
        // esta línea no se ejecuta y el carrito queda intacto.
        carrito.vaciar();
        carritoRepository.save(carrito);
        return pedido;
    }

    /**
     * Cotiza la entrega del carrito actual sin crear pedido. El checkout
     * muestra estos montos, de modo que el total en pantalla proviene del
     * servidor y coincide con el que se cobrará.
     */
    @Transactional(readOnly = true)
    public CotizacionResponseDTO cotizar(String username, MetodoEntrega metodo) {
        Usuario usuario = resolverUsuario(username);
        Carrito carrito = carritoRepository.findConItemsByUsuarioId(usuario.getId())
                .filter(c -> !c.getItems().isEmpty())
                .orElseThrow(() -> new CarritoVacioException(
                        "El carrito está vacío: no hay productos para cotizar"));

        BigDecimal subtotal = carrito.getItems().stream()
                .map(item -> item.getProducto().getPrecio()
                        .multiply(BigDecimal.valueOf(item.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal costoDespacho = envioService.calcularCostoDespacho(metodo, subtotal);
        return new CotizacionResponseDTO(
                subtotal,
                costoDespacho,
                subtotal.add(costoDespacho),
                EnvioService.ENVIO_GRATIS_DESDE);
    }

    private PedidoResponseDTO crear(Usuario usuario, List<LineaPedidoDTO> lineas, DatosCheckoutDTO datos) {
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
            BigDecimal subtotal = BigDecimal.ZERO;
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
                item.setProductoNombre(producto.getNombre());
                item.setCantidad(linea.cantidad());
                item.setPrecioUnitario(producto.getPrecio());
                items.add(item);
                subtotal = subtotal.add(producto.getPrecio().multiply(BigDecimal.valueOf(linea.cantidad())));
            }

            MetodoEntrega metodo = datos != null && datos.metodoEntrega() != null
                    ? datos.metodoEntrega()
                    : MetodoEntrega.RETIRO;
            BigDecimal costoDespacho = envioService.calcularCostoDespacho(metodo, subtotal);

            pedido.setItems(items);
            pedido.setSubtotal(subtotal);
            pedido.setCostoDespacho(costoDespacho);
            pedido.setMetodoEntrega(metodo);
            pedido.setTotal(subtotal.add(costoDespacho));
            aplicarDatosCheckout(pedido, datos);
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

    /**
     * Marca un pedido como pagado tras recibir la confirmación de Webpay.
     * Es idempotente: si el pedido ya está CONFIRMADO y con estadoPago PAGADO,
     * retorna sin reprocesar. Si está en un estado que no puede transicionar a
     * CONFIRMADO (ej. CANCELADO), lanza TransicionEstadoInvalidaException.
     */
    @Transactional
    public PedidoResponseDTO marcarComoPagado(Long pedidoId, String authorizationCode) {
        var pedido = pedidoRepository.findConItemsById(pedidoId)
                .orElseThrow(() -> new PedidoNotFoundException("Pedido no encontrado con id: " + pedidoId));

        if (pedido.getEstado() == EstadoPedido.CONFIRMADO && pedido.getEstadoPago() == EstadoPago.PAGADO) {
            return toResponseDTO(pedido);
        }

        EstadoPedido actual = pedido.getEstado();
        if (actual == null || !actual.puedeTransicionarA(EstadoPedido.CONFIRMADO)) {
            throw new TransicionEstadoInvalidaException(
                    "Transición inválida: no se puede pasar de " + actual + " a CONFIRMADO para el pedido #" + pedidoId);
        }

        pedido.setEstado(EstadoPedido.CONFIRMADO);
        pedido.setEstadoPago(EstadoPago.PAGADO);
        if (authorizationCode != null && !authorizationCode.isBlank()) {
            pedido.setWebpayAuthorizationCode(authorizationCode);
        }
        return toResponseDTO(pedidoRepository.save(pedido));
    }

    /**
     * Marca el estado de pago del pedido como FALLIDO si la transacción fue rechazada o anulada.
     */
    @Transactional
    public PedidoResponseDTO marcarComoFallido(Long pedidoId) {
        var pedido = pedidoRepository.findConItemsById(pedidoId)
                .orElseThrow(() -> new PedidoNotFoundException("Pedido no encontrado con id: " + pedidoId));

        if (pedido.getEstadoPago() == EstadoPago.PAGADO) {
            return toResponseDTO(pedido);
        }

        pedido.setEstadoPago(EstadoPago.FALLIDO);
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

    /**
     * Vuelca los datos del checkout en el pedido. Si no vinieron (flujo legado
     * de {@code POST /v1/pedidos}), el pedido queda sin comprador y sólo con el
     * método de entrega por defecto.
     */
    private void aplicarDatosCheckout(Pedido pedido, DatosCheckoutDTO datos) {
        if (datos == null) {
            return;
        }
        pedido.setCompradorNombre(datos.nombres());
        pedido.setCompradorApellidos(datos.apellidos());
        pedido.setCompradorRut(datos.rut());
        pedido.setCompradorEmail(datos.email());
        pedido.setCompradorTelefono(datos.telefono());
        pedido.setDespachoRegion(datos.region());
        pedido.setDespachoComuna(datos.comuna());
        pedido.setDespachoDireccion(datos.direccion());
        pedido.setDespachoDepto(datos.depto());
        pedido.setDespachoReferencias(datos.referencias());
        pedido.setRetiraTercero(Boolean.TRUE.equals(datos.retiraTercero()));
        pedido.setTerceroNombre(datos.nombreTercero());
        pedido.setTerceroRut(datos.rutTercero());
    }

    private PedidoResponseDTO toResponseDTO(Pedido pedido) {
        List<ItemPedidoResponseDTO> items = pedido.getItems() == null ? List.of() : pedido.getItems().stream()
                .map(item -> new ItemPedidoResponseDTO(
                        item.getProductoId(),
                        item.getProductoNombre(),
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
                items,
                pedido.getWebpayToken(),
                pedido.getEstadoPago() != null ? pedido.getEstadoPago().name() : null,
                pedido.getSubtotal() != null ? pedido.getSubtotal() : pedido.getTotal(),
                pedido.getCostoDespacho() != null ? pedido.getCostoDespacho() : BigDecimal.ZERO,
                pedido.getMetodoEntrega() != null ? pedido.getMetodoEntrega().name() : null
        );
    }
}
