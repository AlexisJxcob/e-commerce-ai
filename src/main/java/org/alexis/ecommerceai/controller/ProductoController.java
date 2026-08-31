package org.alexis.ecommerceai.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.alexis.ecommerceai.dto.BusquedaInteligenteResponse;
import org.alexis.ecommerceai.dto.DiagnoseRequestDTO; // DTO para el body { "problema": "..." }
import org.alexis.ecommerceai.dto.ProductoRequestDTO;
import org.alexis.ecommerceai.dto.ProductoResponseDTO;
import org.alexis.ecommerceai.ai.AsistenteIAService;
import org.alexis.ecommerceai.service.ProductoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/productos")
@CrossOrigin(origins = {"http://localhost:3001"}) // Habilita CORS para el frontend
@Validated
public class ProductoController {

    private final ProductoService productoService;
    private final AsistenteIAService asistenteIAService;

    public ProductoController(ProductoService productoService, AsistenteIAService asistenteIAService) {
        this.productoService = productoService;
        this.asistenteIAService = asistenteIAService;
    }

    @GetMapping
    public ResponseEntity<List<ProductoResponseDTO>> listar() {
        return ResponseEntity.ok(productoService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponseDTO> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(productoService.findById(id));
    }

    // Búsqueda vectorial directa (PGVector - Top N similares)
    @GetMapping("/buscar")
    public ResponseEntity<List<ProductoResponseDTO>> buscarPorSimilitud(
            @RequestParam("q") String query,
            @RequestParam(value = "limite", defaultValue = "5") int limite) {
        return ResponseEntity.ok(productoService.buscarPorSimilitud(query, limite));
    }

    // Búsqueda inteligente vía GET (?q=...)
    @GetMapping("/asistente")
    public ResponseEntity<BusquedaInteligenteResponse> consultarAsistente(@RequestParam("q") String query) {
        return ResponseEntity.ok(asistenteIAService.buscarRecomendacion(query));
    }

    // Endpoint POST para conectar directamente con apiClient.ts de Antigravity
    @PostMapping("/diagnose")
    public ResponseEntity<BusquedaInteligenteResponse> diagnosticarProblema(@RequestBody DiagnoseRequestDTO request) {
        return ResponseEntity.ok(asistenteIAService.buscarRecomendacion(request.getProblema()));
    }

    @PostMapping
    public ResponseEntity<ProductoResponseDTO> crear(@Valid @RequestBody ProductoRequestDTO request) {
        var creado = productoService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponseDTO> actualizar(@PathVariable Long id,
                                                          @Valid @RequestBody ProductoRequestDTO request) {
        return ResponseEntity.ok(productoService.update(id, request));
    }

    @PatchMapping("/{id}/stock")
    public ResponseEntity<ProductoResponseDTO> actualizarStock(
            @PathVariable Long id,
            @RequestParam @NotNull @Min(value = 0, message = "El stock no puede ser negativo") Integer stock) {
        return ResponseEntity.ok(productoService.updateStock(id, stock));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        productoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
