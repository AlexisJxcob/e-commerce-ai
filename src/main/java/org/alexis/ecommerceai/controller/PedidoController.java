package org.alexis.ecommerceai.controller;

import jakarta.validation.Valid;
import org.alexis.ecommerceai.dto.EstadoPedidoRequestDTO;
import org.alexis.ecommerceai.dto.PedidoRequestDTO;
import org.alexis.ecommerceai.dto.PedidoResponseDTO;
import org.alexis.ecommerceai.service.PedidoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<PedidoResponseDTO> crear(@Valid @RequestBody PedidoRequestDTO request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        var creado = pedidoService.create(auth.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    /**
     * Convierte el carrito persistente del usuario autenticado en un pedido.
     * El carrito se vacía sólo si el pedido se creó correctamente.
     */
    @PostMapping("/desde-carrito")
    public ResponseEntity<PedidoResponseDTO> crearDesdeCarrito() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        var creado = pedidoService.crearDesdeCarrito(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    /**
     * Transición de estado (ADMIN). El rol se resuelve en el filtro JWT a
     * partir del claim firmado; {@code SecurityConfig} exige ROLE_ADMIN para
     * PATCH en esta ruta, de modo que ningún header del cliente lo eleva.
     */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<PedidoResponseDTO> cambiarEstado(@PathVariable Long id,
                                                          @Valid @RequestBody EstadoPedidoRequestDTO request) {
        return ResponseEntity.ok(pedidoService.cambiarEstado(id, request.estado()));
    }

    @GetMapping
    public ResponseEntity<List<PedidoResponseDTO>> listar() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(pedidoService.listar(auth.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponseDTO> obtener(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(pedidoService.obtener(auth.getName(), esAdmin, id));
    }
}
