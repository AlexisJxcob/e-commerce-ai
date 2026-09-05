package org.alexis.ecommerceai.controller;

import jakarta.validation.Valid;
import org.alexis.ecommerceai.dto.LoginRequest;
import org.alexis.ecommerceai.dto.LoginResponse;
import org.alexis.ecommerceai.dto.RegisterRequestDTO;
import org.alexis.ecommerceai.dto.UsuarioResponseDTO;
import org.alexis.ecommerceai.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.username(), request.password()));
    }

    @PostMapping("/register")
    public ResponseEntity<UsuarioResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        var creado = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }
}
