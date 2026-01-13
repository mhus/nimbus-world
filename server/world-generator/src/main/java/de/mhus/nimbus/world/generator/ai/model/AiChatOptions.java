package de.mhus.nimbus.world.generator.ai.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Configuration options for AI chat instances.
 * Contains settings for model behavior, tools, and custom parameters.
 */
@Data
@Builder
public class AiChatOptions {

    /**
     * Temperature for response generation (0.0 - 2.0).
     * Higher values make output more creative/random.
     */
    @Builder.Default
    private Double temperature = 0.7;

    /**
     * Maximum number of tokens in the response.
     */
    @Builder.Default
    private Integer maxTokens = 1000;

    /**
     * Request timeout in seconds.
     */
    @Builder.Default
    private Integer timeoutSeconds = 60;

    /**
     * List of AI tools/functions that the model can use.
     * Implementation-specific format.
     */
    private List<Object> tools;

    /**
     * JSON schema definition for structured outputs.
     * Provider-specific format.
     */
    private Map<String, Object> jsonSchema;

    /**
     * Additional custom parameters for specific providers.
     */
    private Map<String, Object> customParameters;

    /**
     * Enable logging of requests and responses.
     */
    @Builder.Default
    private Boolean logRequests = false;

    /**
     * System message/instructions for the AI.
     */
    private String systemMessage;

    /**
     * Create default options.
     */
    public static AiChatOptions defaults() {
        return AiChatOptions.builder().build();
    }
}
