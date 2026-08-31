package org.alexis.ecommerceai.dto.groq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(
        String id,
        String model,
        List<Choice> choices
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            Integer index,
            ChatMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }
}
