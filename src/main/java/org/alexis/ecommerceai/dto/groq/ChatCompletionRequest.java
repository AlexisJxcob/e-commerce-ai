package org.alexis.ecommerceai.dto.groq;

import java.util.List;

public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages
) {
}
