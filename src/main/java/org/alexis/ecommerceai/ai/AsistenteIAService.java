package org.alexis.ecommerceai.ai;

import org.alexis.ecommerceai.dto.ProductoResponseDTO;
import org.alexis.ecommerceai.service.ProductoService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AsistenteIAService {

    private final ChatClient chatClient;
    private final ProductoService productoService;

    // Inyectamos ChatClient.Builder para construir la instancia fluida
    // y ProductoService para realizar la búsqueda semántica vectorial previa
    public AsistenteIAService(ChatClient.Builder chatClientBuilder, ProductoService productoService) {
        this.chatClient = chatClientBuilder.build();
        this.productoService = productoService;
    }

    public String buscarRecomendacion(String preferenciaUsuario) {
        // 1. Retrieval: Recuperar los 3 productos con embeddings más similares en PostgreSQL
        List<ProductoResponseDTO> productosCandidatos = productoService.buscarPorSimilitud(preferenciaUsuario, 3);

        // Si la búsqueda vectorial no retorna nada, evitamos la llamada al LLM
        if (productosCandidatos.isEmpty()) {
            return "Lo siento, no encontré productos en nuestro catálogo que coincidan con tu búsqueda.";
        }

        // 2. Formatear la información de los productos como contexto para el LLM
        String contextoProductos = productosCandidatos.stream()
                .map(p -> String.format("- %s (SKU: %s): %s. Precio: $%s. Stock: %d unidades.",
                        p.nombre(),
                        p.sku(),
                        p.descripcionTecnica(),
                        p.precio(),
                        p.stock()))
                .collect(Collectors.joining("\n"));

        // 3. System Prompt con técnica RAG e instrucciones de contención
        var systemPrompt = """
                Eres el asistente virtual experto de una ferretería.
                El usuario realizará consultas en lenguaje coloquial, informal o impreciso (ej. "el coso para apretar la manguera").
                
                Usa EXCLUSIVAMENTE la siguiente lista de productos recuperados de nuestro catálogo para responder:
                
                %s
                
                Instrucciones de respuesta:
                1. Explica amablemente qué producto técnico de la lista corresponde a la necesidad coloquial expresada por el usuario.
                2. Menciona el precio exacto y el stock disponible.
                3. NO inventes productos, especificaciones ni precios fuera de la lista provista.
                4. Mantén un tono servicial, claro y directo.
                """.formatted(contextoProductos);

        // 4. Generación de respuesta enviando System + User Prompt al LLM
        return chatClient.prompt()
                .system(systemPrompt)
                .user(preferenciaUsuario)
                .call()
                .content();
    }
}