package org.alexis.ecommerceai.dto.huggingface;

import java.util.List;

public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages
) {
}
