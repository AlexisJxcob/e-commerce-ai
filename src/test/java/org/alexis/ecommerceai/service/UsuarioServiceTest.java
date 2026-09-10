package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.dto.UsuarioResponseDTO;
import org.alexis.ecommerceai.exception.RecursoNoEncontradoException;
import org.alexis.ecommerceai.model.Rol;
import org.alexis.ecommerceai.model.Usuario;
import org.alexis.ecommerceai.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(usuarioRepository);
    }

    private static Usuario usuario(Long id, String username, Rol rol, String email) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setUsername(username);
        usuario.setPassword("$2a$10$hashQueNuncaDebeSalir");
        usuario.setRol(rol);
        usuario.setEmail(email);
        return usuario;
    }

    @Test
    void listar_devuelveTodosLosUsuariosSinPassword() {
        when(usuarioRepository.findAll()).thenReturn(List.of(
                usuario(1L, "admin", Rol.ADMIN, "admin@example.com"),
                usuario(2L, "juan", Rol.CLIENTE, null)));

        List<UsuarioResponseDTO> usuarios = usuarioService.listar();

        assertThat(usuarios).hasSize(2);
        assertThat(usuarios.get(0).username()).isEqualTo("admin");
        assertThat(usuarios.get(0).rol()).isEqualTo("ADMIN");
        // El DTO no tiene campo password: no hay forma de que se filtre
        assertThat(UsuarioResponseDTO.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("password");
    }

    @Test
    void obtenerPorUsername_devuelveElPerfilPropio() {
        when(usuarioRepository.findByUsername("juan"))
                .thenReturn(Optional.of(usuario(2L, "juan", Rol.CLIENTE, "juan@example.com")));

        UsuarioResponseDTO dto = usuarioService.obtenerPorUsername("juan");

        assertThat(dto.id()).isEqualTo(2L);
        assertThat(dto.email()).isEqualTo("juan@example.com");
        assertThat(dto.rol()).isEqualTo("CLIENTE");
    }

    @Test
    void obtenerPorUsername_inexistente_lanza404() {
        when(usuarioRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.obtenerPorUsername("fantasma"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void obtenerPorUsername_conRolNulo_noRompeLaSerializacion() {
        when(usuarioRepository.findByUsername("sinrol"))
                .thenReturn(Optional.of(usuario(3L, "sinrol", null, null)));

        assertThat(usuarioService.obtenerPorUsername("sinrol").rol()).isNull();
    }
}
