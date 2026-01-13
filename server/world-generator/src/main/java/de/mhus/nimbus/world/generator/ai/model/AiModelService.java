package de.mhus.nimbus.world.generator.ai.model;

import de.mhus.nimbus.shared.service.SSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing AI models and their providers.
 * Maintains a lazy-loaded list of LangchainModel implementations
 * and provides model name mapping through SSettingsService.
 */
@Service
@Slf4j
public class AiModelService {

    private static final String MAPPING_PREFIX = "ai.model.mapping.";

    private final List<LangchainModel> modelProviders;
    private final SSettingsService settingsService;
    private final Map<String, LangchainModel> providerCache = new ConcurrentHashMap<>();

    public AiModelService(List<LangchainModel> modelProviders,
                          SSettingsService settingsService) {
        this.modelProviders = modelProviders;
        this.settingsService = settingsService;

        log.info("Initializing AiModelService with {} providers", modelProviders.size());

        // Initialize provider cache
        for (LangchainModel provider : modelProviders) {
            providerCache.put(provider.getName(), provider);
            log.info("Registered AI provider: {} (available: {})",
                    provider.getName(), provider.isAvailable());
        }
    }

    /**
     * Create an AI chat instance by full model name.
     * Format: provider:model or default:name (which resolves via mapping)
     *
     * @param fullModelName Full model name (e.g., "openai:gpt-4", "default:chat")
     * @param options Chat configuration options
     * @return AI chat instance if available
     */
    public Optional<AiChat> createChat(String fullModelName, AiChatOptions options) {
        if (fullModelName == null || fullModelName.isBlank()) {
            log.warn("Empty model name provided");
            return Optional.empty();
        }

        // Resolve default: prefix
        String resolvedName = resolveModelName(fullModelName);

        // Parse provider:model
        String[] parts = resolvedName.split(":", 2);
        if (parts.length != 2) {
            log.warn("Invalid model name format: {}. Expected 'provider:model'", resolvedName);
            return Optional.empty();
        }

        String providerName = parts[0];
        String modelName = parts[1];

        // Get provider
        LangchainModel provider = providerCache.get(providerName);
        if (provider == null) {
            log.warn("Unknown AI provider: {}", providerName);
            return Optional.empty();
        }

        if (!provider.isAvailable()) {
            log.warn("AI provider not available: {}", providerName);
            return Optional.empty();
        }

        // Create chat
        try {
            Optional<AiChat> chat = provider.createAiChat(modelName, options);
            if (chat.isPresent()) {
                log.info("Created AI chat: {}", chat.get().getName());
            } else {
                log.warn("Provider {} could not create model: {}", providerName, modelName);
            }
            return chat;
        } catch (Exception e) {
            log.error("Failed to create AI chat: {}:{}", providerName, modelName, e);
            return Optional.empty();
        }
    }

    /**
     * Create an AI chat instance with default options.
     *
     * @param fullModelName Full model name
     * @return AI chat instance if available
     */
    public Optional<AiChat> createChat(String fullModelName) {
        return createChat(fullModelName, AiChatOptions.defaults());
    }

    /**
     * Register a model mapping in SSettingsService.
     * Maps "default:name" to a specific "provider:model".
     *
     * @param alias Alias name (without "default:" prefix)
     * @param targetModel Target model name (e.g., "openai:gpt-4")
     */
    public void registerMapping(String alias, String targetModel) {
        String settingKey = MAPPING_PREFIX + alias;
        settingsService.setStringValue(settingKey, targetModel);
        log.info("Registered model mapping: default:{} -> {}", alias, targetModel);
    }

    /**
     * Get all available provider names.
     *
     * @return List of provider names
     */
    public List<String> getAvailableProviders() {
        return modelProviders.stream()
                .filter(LangchainModel::isAvailable)
                .map(LangchainModel::getName)
                .toList();
    }

    /**
     * Get a specific provider by name.
     *
     * @param providerName Provider name
     * @return Provider if available
     */
    public Optional<LangchainModel> getProvider(String providerName) {
        return Optional.ofNullable(providerCache.get(providerName));
    }

    /**
     * Check if a specific provider is available.
     *
     * @param providerName Provider name
     * @return true if provider exists and is available
     */
    public boolean isProviderAvailable(String providerName) {
        LangchainModel provider = providerCache.get(providerName);
        return provider != null && provider.isAvailable();
    }

    /**
     * Get all registered model mappings from SSettingsService.
     *
     * @return Map of alias to target model
     */
    public Map<String, String> getMappings() {
        Map<String, String> mappings = new ConcurrentHashMap<>();

        // Load all settings with prefix "ai.model.mapping."
        List<de.mhus.nimbus.shared.persistence.SSettings> settings =
                settingsService.getSettingsByType("string");

        for (var setting : settings) {
            if (setting.getKey().startsWith(MAPPING_PREFIX)) {
                String alias = setting.getKey().substring(MAPPING_PREFIX.length());
                String targetModel = setting.getValue();
                if (targetModel != null && !targetModel.isBlank()) {
                    mappings.put("default:" + alias, targetModel);
                }
            }
        }

        return mappings;
    }

    private String resolveModelName(String fullModelName) {
        // Check if it's a default: mapping
        if (fullModelName.startsWith("default:")) {
            String alias = fullModelName.substring("default:".length());
            String settingKey = MAPPING_PREFIX + alias;
            String resolved = settingsService.getStringValue(settingKey);

            if (resolved != null && !resolved.isBlank()) {
                log.debug("Resolved model mapping: {} -> {}", fullModelName, resolved);
                return resolved;
            }
            log.warn("No mapping found for: {} (key: {})", fullModelName, settingKey);
        }
        return fullModelName;
    }
}
