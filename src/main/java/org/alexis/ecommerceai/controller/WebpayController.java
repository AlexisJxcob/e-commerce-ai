package org.alexis.ecommerceai.controller;

import org.alexis.ecommerceai.config.WebpayProperties;
import org.alexis.ecommerceai.dto.webpay.WebpayCommitResponseDTO;
import org.alexis.ecommerceai.service.WebpayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * Controller handling Transbank Webpay Plus return and confirmation callbacks.
 * Context-path: /api, so mapped path is /api/v1/pagos/webpay.
 */
@RestController
@RequestMapping("/v1/pagos/webpay")
public class WebpayController {

    private static final Logger log = LoggerFactory.getLogger(WebpayController.class);

    private final WebpayService webpayService;
    private final WebpayProperties webpayProperties;

    public WebpayController(WebpayService webpayService, WebpayProperties webpayProperties) {
        this.webpayService = webpayService;
        this.webpayProperties = webpayProperties;
    }

    /**
     * Browser return endpoint where Transbank redirects the user after payment.
     * Supports both GET and POST as per Transbank Webpay Plus specifications.
     */
    @RequestMapping(value = "/retorno", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> retornoWebpay(
            @RequestParam(value = "token_ws", required = false) String tokenWs,
            @RequestParam(value = "TBK_TOKEN", required = false) String tbkToken,
            @RequestParam(value = "TBK_ORDEN_COMPRA", required = false) String tbkOrdenCompra,
            @RequestParam(value = "TBK_ID_SESION", required = false) String tbkIdSesion) {

        log.info("Retorno Webpay recibido: token_ws={}, TBK_TOKEN={}, TBK_ORDEN_COMPRA={}",
                tokenWs, tbkToken, tbkOrdenCompra);

        String finalUrl = webpayProperties.finalUrl();

        if (tokenWs != null && !tokenWs.isBlank()) {
            try {
                WebpayCommitResponseDTO commitResponse = webpayService.confirmarTransaccion(tokenWs);
                String status = commitResponse.isAprobada() ? "exito" : "rechazado";
                URI redirectUri = UriComponentsBuilder.fromUriString(finalUrl)
                        .queryParam("token", tokenWs)
                        .queryParam("status", status)
                        .queryParam("buy_order", commitResponse.buyOrder())
                        .build()
                        .toUri();
                return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
            } catch (Exception e) {
                log.error("Error procesando commit en retorno de Webpay: {}", e.getMessage(), e);
                URI redirectUri = UriComponentsBuilder.fromUriString(finalUrl)
                        .queryParam("token", tokenWs)
                        .queryParam("status", "error")
                        .build()
                        .toUri();
                return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
            }
        }

        if (tbkToken != null && !tbkToken.isBlank()) {
            webpayService.registrarCancelacion(tbkToken);
            URI redirectUri = UriComponentsBuilder.fromUriString(finalUrl)
                    .queryParam("token", tbkToken)
                    .queryParam("status", "cancelado")
                    .build()
                    .toUri();
            return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
        }

        // Timeout or invalid flow from Transbank
        URI redirectUri = UriComponentsBuilder.fromUriString(finalUrl)
                .queryParam("status", "invalido")
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
    }

    /**
     * Programmatic confirmation endpoint for SPA applications.
     */
    @PostMapping("/confirmar")
    public ResponseEntity<WebpayCommitResponseDTO> confirmarTransaccion(@RequestBody Map<String, String> payload) {
        String token = payload.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        WebpayCommitResponseDTO response = webpayService.confirmarTransaccion(token);
        return ResponseEntity.ok(response);
    }
}
