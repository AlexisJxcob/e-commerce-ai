package org.alexis.ecommerceai.ai;

import org.alexis.ecommerceai.config.HuggingFaceChatConfig;
import org.alexis.ecommerceai.config.HuggingFaceChatProperties;
import org.alexis.ecommerceai.dto.SugerenciaFerreteriaDTO;
import org.alexis.ecommerceai.exception.HuggingFaceException;
import org.alexis.ecommerceai.exception.HuggingFaceRateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests unitarios de {@link HuggingFaceChatService} con el cliente HTTP mockeado
 * (MockRestServiceServer): nunca se hacen llamadas reales a la API de Hugging Face.
 */
class HuggingFaceChatServiceTest {

    private static final String BASE_URL = "https://router.huggingface.co/v1";

    private HuggingFaceChatProperties properties;
    private MockRestServiceServer server;
    private HuggingFaceChatService service;

    @BeforeEach
    void setUp() {
        properties = new HuggingFaceChatProperties();
        properties.setKey("test-huggingface-key");
        properties.setModel("test-model");
        properties.setBaseUrl(BASE_URL);

        // Construir el cliente con la configuracion real (headers, baseUrl),
        // pero con el request factory interceptado por MockRestServiceServer.
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = new HuggingFaceChatConfig().huggingFaceChatRestClient(builder, properties);

        service = new HuggingFaceChatService(client, properties, JsonMapper.builder().build());
    }

    private void expectChatCompletion(HttpStatus status, String responseBody) {
        server.expect(method(HttpMethod.POST))
                .andExpect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-huggingface-key"))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseBody));
    }

    // ---------- validaciones de configuración ----------

    @Test
    void analizarConsulta_lanzaErrorSiFaltaLaApiKey() {
        properties.setKey("");
        assertThatThrownBy(() -> service.analizarConsulta("fuga"))
                .isInstanceOf(HuggingFaceException.class)
                .hasMessageContaining("huggingface.chat.key");
    }

    @Test
    void analizarConsulta_lanzaErrorSiFaltaElModelo() {
        properties.setModel("");
        assertThatThrownBy(() -> service.analizarConsulta("fuga"))
                .isInstanceOf(HuggingFaceException.class)
                .hasMessageContaining("huggingface.chat.model");
    }

    @Test
    void analizarConsulta_lanzaError400SiLaConsultaEstaVacia() {
        assertThatThrownBy(() -> service.analizarConsulta("   "))
                .isInstanceOf(HuggingFaceException.class)
                .satisfies(ex -> assertThat(((HuggingFaceException) ex).getStatus()).isEqualTo(400));
    }

    // ---------- casos de éxito ----------

    @Test
    void analizarConsulta_parseaJsonConFencesDeMarkdown() {
        String contenido = "```json\n" +
                "{\"palabrasClave\":[\"cinta\",\"teflon\"],\"herramientas\":[\"llave\"]," +
                "\"repuestos\":[\"cinta teflon\"]}\n```";
        String respuesta = "{\"id\":\"c1\",\"model\":\"test-model\",\"choices\":[{\"index\":0," +
                "\"message\":{\"role\":\"assistant\",\"content\":\"" + escaparJson(contenido) + "\"}," +
                "\"finish_reason\":\"stop\"}]}";
        server.expect(method(HttpMethod.POST))
                .andExpect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-huggingface-key"))
                .andRespond(withSuccess(respuesta, MediaType.APPLICATION_JSON));

        SugerenciaFerreteriaDTO resultado = service.analizarConsulta("tengo una fuga en una tuberia");

        assertThat(resultado.palabrasClave()).containsExactly("cinta", "teflon");
        assertThat(resultado.herramientas()).containsExactly("llave");
        assertThat(resultado.repuestos()).containsExactly("cinta teflon");
    }

    @Test
    void analizarConsulta_enviaElModeloYLaConsultaEnElBody() {
        server.expect(method(HttpMethod.POST))
                .andExpect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-huggingface-key"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"model\":\"test-model\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("fuga en la tuberia")))
                .andRespond(withSuccess(
                        "{\"id\":\"c1\",\"model\":\"test-model\",\"choices\":[{\"index\":0," +
                                "\"message\":{\"role\":\"assistant\",\"content\":" +
                                "\"{\\\"palabrasClave\\\":[],\\\"herramientas\\\":[],\\\"repuestos\\\":[]}\"}," +
                                "\"finish_reason\":\"stop\"}]}",
                        MediaType.APPLICATION_JSON));

        SugerenciaFerreteriaDTO resultado = service.analizarConsulta("fuga en la tuberia");

        assertThat(resultado.palabrasClave()).isEmpty();
        server.verify();
    }

    // ---------- errores de Hugging Face ----------

    @Test
    void analizarConsulta_lanzaRateLimitAnteHttp429() {
        expectChatCompletion(HttpStatus.TOO_MANY_REQUESTS, "{\"error\":\"rate limit exceeded\"}");

        assertThatThrownBy(() -> service.analizarConsulta("fuga"))
                .isInstanceOf(HuggingFaceRateLimitException.class)
                .hasMessageContaining("rate limit");
    }

    @Test
    void analizarConsulta_lanzaHuggingFaceExceptionAnteHttp500() {
        server.expect(method(HttpMethod.POST))
                .andExpect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.analizarConsulta("fuga"))
                .isInstanceOf(HuggingFaceException.class)
                .satisfies(ex -> assertThat(((HuggingFaceException) ex).getStatus()).isEqualTo(500));
    }

    @Test
    void analizarConsulta_lanzaErrorSiLaRespuestaEstaVacia() {
        expectChatCompletion(HttpStatus.OK, "{}");

        assertThatThrownBy(() -> service.analizarConsulta("fuga"))
                .isInstanceOf(HuggingFaceException.class)
                .hasMessageContaining("vacía");
    }

    @Test
    void analizarConsulta_lanzaErrorSiElJsonDevueltoNoEsValido() {
        String respuesta = "{\"id\":\"c1\",\"model\":\"test-model\",\"choices\":[{\"index\":0," +
                "\"message\":{\"role\":\"assistant\",\"content\":\"esto no es un JSON\"}," +
                "\"finish_reason\":\"stop\"}]}";
        expectChatCompletion(HttpStatus.OK, respuesta);

        assertThatThrownBy(() -> service.analizarConsulta("fuga"))
                .isInstanceOf(HuggingFaceException.class)
                .hasMessageContaining("interpretar");
    }

    // ---------- extraerJson (método estático de paquete) ----------

    @Test
    void extraerJson_quitaFencesDeMarkdown() {
        assertThat(HuggingFaceChatService.extraerJson("```json\n{\"a\":1}\n```"))
                .isEqualTo("{\"a\":1}");
    }

    @Test
    void extraerJson_extraeObjetoDeTextoConPrefijo() {
        assertThat(HuggingFaceChatService.extraerJson("Aquí va el JSON: {\"a\":1} fin"))
                .isEqualTo("{\"a\":1}");
    }

    @Test
    void extraerJson_devuelveContenidoPlanoSinObjeto() {
        assertThat(HuggingFaceChatService.extraerJson("no hay objeto")).isEqualTo("no hay objeto");
    }

    private static String escaparJson(String texto) {
        return texto.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
