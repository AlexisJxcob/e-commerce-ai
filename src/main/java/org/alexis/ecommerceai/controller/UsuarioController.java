package org.alexis.ecommerceai.controller;

import org.alexis.ecommerceai.dto.UsuarioResponseDTO;
import org.alexis.ecommerceai.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Usuarios. El rol se lee siempre del {@code Authentication} construido por
 * {@code JwtAuthenticationFilter} a partir del claim {@code roles} firmado:
 * ni un header {@code X-Role} ni un parámetro de la petición pueden elevarlo.
 */
@RestController
@RequestMapping("/v1/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /** Perfil del usuario autenticado (cualquier rol). */
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> obtenerActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(usuarioService.obtenerPorUsername(auth.getName()));
    }

    /** Listado completo: solo ADMIN (regla en SecurityConfig). */
    @GetMapping
    public ResponseEntity<List<UsuarioResponseDTO>> listar() {
        return ResponseEntity.ok(usuarioService.listar());
    }
}
