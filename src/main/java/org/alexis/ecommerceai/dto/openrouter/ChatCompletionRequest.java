package org.alexis.ecommerceai.dto.openrouter;

import java.util.List;

public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages
) {
}
