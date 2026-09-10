package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.dto.CarritoResponseDTO;
import org.alexis.ecommerceai.dto.LineaCarritoResponseDTO;
import org.alexis.ecommerceai.exception.CarritoItemNotFoundException;
import org.alexis.ecommerceai.exception.ProductoNotFoundException;
import org.alexis.ecommerceai.exception.RecursoNoEncontradoException;
import org.alexis.ecommerceai.model.Carrito;
import org.alexis.ecommerceai.model.CarritoItem;
import org.alexis.ecommerceai.model.Producto;
import org.alexis.ecommerceai.model.Usuario;
import org.alexis.ecommerceai.repository.CarritoItemRepository;
import org.alexis.ecommerceai.repository.CarritoRepository;
import org.alexis.ecommerceai.repository.ProductoRepository;
import org.alexis.ecommerceai.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Carrito persistente del usuario autenticado, sobre el agregado
 * {@link Carrito} + {@link CarritoItem}.
 *
 * <p>El carrito se crea de forma perezosa: sólo existe una fila en
 * {@code carritos} cuando el usuario agrega su primer producto. Consultar el
 * carrito de alguien que nunca agregó nada devuelve un carrito vacío, no 404.</p>
 *
 * <p>No hay purga defensiva de líneas huérfanas: la FK
 * {@code carrito_items.producto_id} es {@code ON DELETE CASCADE}, así que un
 * producto borrado se lleva sus líneas consigo y el estado inconsistente es
 * irrepresentable.</p>
 */
@Service
public class CarritoService {

    private final CarritoRepository carritoRepository;
    private final CarritoItemRepository carritoItemRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    public CarritoService(CarritoRepository carritoRepository,
                          CarritoItemRepository carritoItemRepository,
                          ProductoRepository productoRepository,
                          UsuarioRepository usuarioRepository) {
        this.carritoRepository = carritoRepository;
        this.carritoItemRepository = carritoItemRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public CarritoResponseDTO ver(String username) {
        return carritoRepository.findByUsuarioUsername(username)
                .map(this::toResponseDTO)
                .orElseGet(() -> new CarritoResponseDTO(List.of(), BigDecimal.ZERO));
    }

    @Transactional
    public LineaCarritoResponseDTO agregar(String username, Long productoId, Integer cantidad) {
        Usuario usuario = resolverUsuario(username);
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ProductoNotFoundException(
                        "Producto no encontrado con id: " + productoId));

        Carrito carrito = carritoRepository.findByUsuarioId(usuario.getId())
                .orElseGet(() -> carritoRepository.save(carritoNuevo(usuario)));

        CarritoItem item = carritoItemRepository
                .findByCarritoIdAndProductoId(carrito.getId(), productoId)
                .orElseGet(() -> {
                    CarritoItem nuevo = new CarritoItem();
                    nuevo.setProducto(producto);
                    nuevo.setCantidad(0);
                    carrito.agregarItem(nuevo);
                    return nuevo;
                });

        item.incrementar(cantidad);
        return toLineaDTO(carritoItemRepository.save(item));
    }

    @Transactional
    public LineaCarritoResponseDTO actualizarCantidad(String username, Long lineaId, Integer cantidad) {
        CarritoItem item = carritoItemRepository
                .findByIdAndCarritoUsuarioUsername(lineaId, username)
                .orElseThrow(() -> new CarritoItemNotFoundException(
                        "Línea de carrito no encontrada con id: " + lineaId));
        item.setCantidad(cantidad);
        return toLineaDTO(carritoItemRepository.save(item));
    }

    @Transactional
    public void eliminarLinea(String username, Long lineaId) {
        CarritoItem item = carritoItemRepository
                .findByIdAndCarritoUsuarioUsername(lineaId, username)
                .orElseThrow(() -> new CarritoItemNotFoundException(
                        "Línea de carrito no encontrada con id: " + lineaId));
        carritoItemRepository.delete(item);
    }

    @Transactional
    public void vaciar(String username) {
        Usuario usuario = resolverUsuario(username);
        carritoRepository.findByUsuarioId(usuario.getId()).ifPresent(carrito -> {
            carrito.vaciar();
            carritoRepository.save(carrito);
        });
    }

    private Carrito carritoNuevo(Usuario usuario) {
        Carrito carrito = new Carrito();
        carrito.setUsuario(usuario);
        return carrito;
    }

    private Usuario resolverUsuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + username));
    }

    private CarritoResponseDTO toResponseDTO(Carrito carrito) {
        List<LineaCarritoResponseDTO> lineas = carrito.getItems().stream()
                .map(this::toLineaDTO)
                .toList();
        BigDecimal total = lineas.stream()
                .map(LineaCarritoResponseDTO::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CarritoResponseDTO(lineas, total);
    }

    private LineaCarritoResponseDTO toLineaDTO(CarritoItem item) {
        Producto producto = item.getProducto();
        return new LineaCarritoResponseDTO(
                item.getId(),
                producto.getId(),
                producto.getNombre(),
                producto.getPrecio(),
                item.getCantidad(),
                item.subtotal()
        );
    }
}
