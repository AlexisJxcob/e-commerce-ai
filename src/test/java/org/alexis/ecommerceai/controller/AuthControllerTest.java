package org.alexis.ecommerceai.controller;

import org.alexis.ecommerceai.dto.LoginResponse;
import org.alexis.ecommerceai.dto.UsuarioResponseDTO;
import org.alexis.ecommerceai.exception.CredencialesInvalidasException;
import org.alexis.ecommerceai.exception.GlobalExceptionHandler;
import org.alexis.ecommerceai.exception.UsuarioDuplicadoException;
import org.alexis.ecommerceai.service.AuthService;
import org.alexis.ecommerceai.testconfig.MockMvcContextPathConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultRequest(get("/").contextPath(MockMvcContextPathConfig.CONTEXT_PATH))
                .build();
    }

    // ---------- login ----------

    @Test
    void login_credencialesValidas_devuelve200ConToken() throws Exception {
        when(authService.login(eq("admin"), eq("admin123")))
                .thenReturn(new LoginResponse("token-jwt", "admin", 86400));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-jwt"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.expiresIn").value(86400));
    }

    @Test
    void login_credencialesInvalidas_devuelve401() throws Exception {
        when(authService.login(any(), any())).thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    // ---------- register ----------

    @Test
    void register_usuarioValido_devuelve201ClienteSinPassword() throws Exception {
        when(authService.register(any())).thenReturn(new UsuarioResponseDTO(2L, "juan", "CLIENTE"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"juan\",\"password\":\"secreta123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.username").value("juan"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void register_conHintDeAdmin_respondeCliente() throws Exception {
        when(authService.register(any())).thenReturn(new UsuarioResponseDTO(3L, "vivo", "CLIENTE"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"vivo\",\"password\":\"secreta123\",\"rol\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("CLIENTE"));
    }

    @Test
    void register_usernameDuplicado_devuelve409() throws Exception {
        when(authService.register(any()))
                .thenThrow(new UsuarioDuplicadoException("Ya existe un usuario con el nombre: juan"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"juan\",\"password\":\"secreta123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void register_passwordCorta_devuelve400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"juan\",\"password\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }
}
