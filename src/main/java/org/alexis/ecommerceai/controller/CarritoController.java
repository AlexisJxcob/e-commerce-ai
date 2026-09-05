package org.alexis.ecommerceai.controller;

import jakarta.validation.Valid;
import org.alexis.ecommerceai.dto.ActualizarItemCarritoDTO;
import org.alexis.ecommerceai.dto.AgregarItemCarritoDTO;
import org.alexis.ecommerceai.dto.CarritoResponseDTO;
import org.alexis.ecommerceai.dto.LineaCarritoResponseDTO;
import org.alexis.ecommerceai.service.CarritoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/carrito")
public class CarritoController {

    private final CarritoService carritoService;

    public CarritoController(CarritoService carritoService) {
        this.carritoService = carritoService;
    }

    @GetMapping
    public ResponseEntity<CarritoResponseDTO> ver() {
        return ResponseEntity.ok(carritoService.ver(usuarioActual()));
    }

    @PostMapping("/items")
    public ResponseEntity<LineaCarritoResponseDTO> agregar(@Valid @RequestBody AgregarItemCarritoDTO request) {
        return ResponseEntity.ok(carritoService.agregar(usuarioActual(), request.productoId(), request.cantidad()));
    }

    @PatchMapping("/items/{id}")
    public ResponseEntity<LineaCarritoResponseDTO> actualizarCantidad(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarItemCarritoDTO request) {
        return ResponseEntity.ok(carritoService.actualizarCantidad(usuarioActual(), id, request.cantidad()));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> eliminarLinea(@PathVariable Long id) {
        carritoService.eliminarLinea(usuarioActual(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> vaciar() {
        carritoService.vaciar(usuarioActual());
        return ResponseEntity.noContent().build();
    }

    private static String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
