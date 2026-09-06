package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.dto.ItemPedidoResponseDTO;
import org.alexis.ecommerceai.dto.LineaPedidoDTO;
import org.alexis.ecommerceai.dto.PedidoRequestDTO;
import org.alexis.ecommerceai.dto.PedidoResponseDTO;
import org.alexis.ecommerceai.exception.PedidoNotFoundException;
import org.alexis.ecommerceai.exception.ProductoNotFoundException;
import org.alexis.ecommerceai.exception.RecursoNoEncontradoException;
import org.alexis.ecommerceai.exception.StockInsuficienteException;
import org.alexis.ecommerceai.exception.StockUpdateConflictException;
import org.alexis.ecommerceai.model.EstadoPedido;
import org.alexis.ecommerceai.model.ItemPedido;
import org.alexis.ecommerceai.model.Pedido;
import org.alexis.ecommerceai.model.Producto;
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

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    public PedidoService(PedidoRepository pedidoRepository,
                         ProductoRepository productoRepository,
                         UsuarioRepository usuarioRepository) {
        this.pedidoRepository = pedidoRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public PedidoResponseDTO create(String username, PedidoRequestDTO request) {
        var usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + username));

        List<Long> ids = request.items().stream()
                .map(LineaPedidoDTO::productoId)
                .distinct()
                .sorted()
                .toList();
        Map<Long, Producto> productos = productoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));

        // Primera pasada: validar todo antes de mutar nada (sin pedido parcial).
        for (LineaPedidoDTO linea : request.items()) {
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

        // Segunda pasada: snapshot de precios + decremento, en orden determinista.
        try {
            var pedido = new Pedido();
            pedido.setUsuario(usuario);
            pedido.setFechaCreacion(LocalDateTime.now());
            pedido.setEstado(EstadoPedido.PENDIENTE);
            BigDecimal total = BigDecimal.ZERO;
            List<ItemPedido> items = new ArrayList<>();
            for (LineaPedidoDTO linea : request.items().stream()
                    .sorted(Comparator.comparing(LineaPedidoDTO::productoId))
                    .toList()) {
                Producto producto = productos.get(linea.productoId());
                producto.setStock(producto.getStock() - linea.cantidad());
                productoRepository.save(producto);

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
            throw new StockUpdateConflictException(
                    "Conflicto de concurrencia al registrar el pedido. Intente nuevamente.");
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
        var pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new PedidoNotFoundException("Pedido no encontrado con id: " + id));
        if (!esAdmin && !pedido.getUsuario().getUsername().equals(username)) {
            throw new PedidoNotFoundException("Pedido no encontrado con id: " + id);
        }
        return toResponseDTO(pedido);
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
