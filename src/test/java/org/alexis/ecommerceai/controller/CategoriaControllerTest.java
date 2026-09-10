package org.alexis.ecommerceai.controller;

import org.alexis.ecommerceai.dto.CategoriaResponseDTO;
import org.alexis.ecommerceai.exception.CategoriaEnUsoException;
import org.alexis.ecommerceai.exception.CategoriaNotFoundException;
import org.alexis.ecommerceai.exception.ConflictoException;
import org.alexis.ecommerceai.exception.GlobalExceptionHandler;
import org.alexis.ecommerceai.service.CategoriaService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CategoriaControllerTest {

    @Mock
    private CategoriaService categoriaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CategoriaController controller = new CategoriaController(categoriaService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultRequest(get("/").contextPath(MockMvcContextPathConfig.CONTEXT_PATH))
                .build();
    }

    private static CategoriaResponseDTO dto(Long id, String nombre) {
        return new CategoriaResponseDTO(id, nombre, "Descripción de " + nombre, null);
    }

    @Test
    void listar_devuelve200ConCategorias() throws Exception {
        when(categoriaService.findAll()).thenReturn(List.of(dto(1L, "Fijaciones"), dto(2L, "Pinturas")));

        mockMvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("Fijaciones"));
    }

    @Test
    void obtener_devuelve200ConCategoria() throws Exception {
        when(categoriaService.findById(1L)).thenReturn(dto(1L, "Fijaciones"));

        mockMvc.perform(get("/api/v1/categorias/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Fijaciones"));
    }

    @Test
    void obtener_inexistente_devuelve404() throws Exception {
        when(categoriaService.findById(99L))
                .thenThrow(new CategoriaNotFoundException("Categoría no encontrada con id: 99"));

        mockMvc.perform(get("/api/v1/categorias/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void crear_conBodyValido_devuelve201() throws Exception {
        when(categoriaService.create(any())).thenReturn(dto(1L, "Fijaciones"));

        mockMvc.perform(post("/api/v1/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Fijaciones\",\"descripcion\":\"Tornillos\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Fijaciones"));
    }

    @Test
    void crear_conNombreVacio_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"descripcion\":\"Tornillos\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.nombre").exists());
    }

    @Test
    void crear_conNombreDuplicado_devuelve409() throws Exception {
        when(categoriaService.create(any()))
                .thenThrow(new ConflictoException("Ya existe una categoría con el nombre: Fijaciones"));

        mockMvc.perform(post("/api/v1/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Fijaciones\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void actualizar_devuelve200() throws Exception {
        when(categoriaService.update(eq(1L), any())).thenReturn(dto(1L, "Fijaciones"));

        mockMvc.perform(put("/api/v1/categorias/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Fijaciones\",\"descripcion\":\"Nueva\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Fijaciones"));
    }

    @Test
    void eliminar_devuelve204() throws Exception {
        doNothing().when(categoriaService).delete(1L);

        mockMvc.perform(delete("/api/v1/categorias/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void eliminar_referenciada_devuelve409() throws Exception {
        doThrow(new CategoriaEnUsoException("No se puede eliminar la categoría con id: 1 porque tiene productos asociados"))
                .when(categoriaService).delete(1L);

        mockMvc.perform(delete("/api/v1/categorias/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
