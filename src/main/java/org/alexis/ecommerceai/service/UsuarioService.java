package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.dto.UsuarioResponseDTO;
import org.alexis.ecommerceai.exception.RecursoNoEncontradoException;
import org.alexis.ecommerceai.model.Usuario;
import org.alexis.ecommerceai.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lectura de usuarios. Es el único punto por el que una fila de {@code usuarios}
 * llega a la API, y siempre a través de {@link UsuarioResponseDTO}: la entidad
 * nunca se serializa, de modo que el hash BCrypt no puede filtrarse por
 * accidente al agregar un endpoint.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listar() {
        return usuarioRepository.findAll().stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + username));
    }

    private UsuarioResponseDTO toResponseDTO(Usuario usuario) {
        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getRol() != null ? usuario.getRol().name() : null,
                usuario.getEmail()
        );
    }
}
