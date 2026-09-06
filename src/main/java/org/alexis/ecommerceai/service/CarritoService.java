package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.dto.CarritoResponseDTO;
import org.alexis.ecommerceai.dto.LineaCarritoResponseDTO;
import org.alexis.ecommerceai.exception.ItemCarritoNotFoundException;
import org.alexis.ecommerceai.exception.ProductoNotFoundException;
import org.alexis.ecommerceai.exception.RecursoNoEncontradoException;
import org.alexis.ecommerceai.model.ItemCarrito;
import org.alexis.ecommerceai.model.Usuario;
import org.alexis.ecommerceai.repository.ItemCarritoRepository;
import org.alexis.ecommerceai.repository.ProductoRepository;
import org.alexis.ecommerceai.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CarritoService {

    private final ItemCarritoRepository itemCarritoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    public CarritoService(ItemCarritoRepository itemCarritoRepository,
                          ProductoRepository productoRepository,
                          UsuarioRepository usuarioRepository) {
        this.itemCarritoRepository = itemCarritoRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public CarritoResponseDTO ver(String username) {
        List<LineaCarritoResponseDTO> lineas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ItemCarrito linea : lineasVigentes(username)) {
            LineaCarritoResponseDTO dto = toResponseDTO(linea);
            lineas.add(dto);
            total = total.add(dto.subtotal());
        }
        return new CarritoResponseDTO(List.copyOf(lineas), total);
    }

    @Transactional
    public LineaCarritoResponseDTO agregar(String username, Long productoId, Integer cantidad) {
        Usuario usuario = resolverUsuario(username);
        var producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ProductoNotFoundException("Producto no encontrado con id: " + productoId));
        purgarHuerfanos(username);

        var linea = itemCarritoRepository.findByUsuarioUsernameAndProductoId(username, productoId)
                .orElseGet(() -> {
                    var nueva = new ItemCarrito();
                    nueva.setUsuario(usuario);
                    nueva.setProducto(producto);
                    nueva.setCantidad(0);
                    return nueva;
                });
        linea.setCantidad(linea.getCantidad() + cantidad);
        linea = itemCarritoRepository.save(linea);
        return toResponseDTO(linea);
    }

    @Transactional
    public LineaCarritoResponseDTO actualizarCantidad(String username, Long lineaId, Integer cantidad) {
        purgarHuerfanos(username);
        var linea = itemCarritoRepository.findByIdAndUsuarioUsername(lineaId, username)
                .orElseThrow(() -> new ItemCarritoNotFoundException(
                        "Línea de carrito no encontrada con id: " + lineaId));
        linea.setCantidad(cantidad);
        linea = itemCarritoRepository.save(linea);
        return toResponseDTO(linea);
    }

    @Transactional
    public void eliminarLinea(String username, Long lineaId) {
        var linea = itemCarritoRepository.findByIdAndUsuarioUsername(lineaId, username)
                .orElseThrow(() -> new ItemCarritoNotFoundException(
                        "Línea de carrito no encontrada con id: " + lineaId));
        itemCarritoRepository.delete(linea);
    }

    @Transactional
    public void vaciar(String username) {
        resolverUsuario(username);
        itemCarritoRepository.deleteByUsuarioUsername(username);
    }

    private Usuario resolverUsuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + username));
    }

    /**
     * Líneas cuyo producto sigue existiendo. Las huérfanas (producto borrado
     * sin cascada) se excluyen de la respuesta y se purgan de forma defensiva.
     */
    private List<ItemCarrito> lineasVigentes(String username) {
        List<ItemCarrito> vigentes = new ArrayList<>();
        for (ItemCarrito linea : itemCarritoRepository.findByUsuarioUsername(username)) {
            if (linea.getProducto() == null) {
                itemCarritoRepository.delete(linea);
                continue;
            }
            vigentes.add(linea);
        }
        return vigentes;
    }

    private void purgarHuerfanos(String username) {
        lineasVigentes(username);
    }

    private LineaCarritoResponseDTO toResponseDTO(ItemCarrito linea) {
        var producto = linea.getProducto();
        BigDecimal subtotal = producto.getPrecio().multiply(BigDecimal.valueOf(linea.getCantidad()));
        return new LineaCarritoResponseDTO(
                linea.getId(),
                producto.getId(),
                producto.getNombre(),
                producto.getPrecio(),
                linea.getCantidad(),
                subtotal
        );
    }
}
