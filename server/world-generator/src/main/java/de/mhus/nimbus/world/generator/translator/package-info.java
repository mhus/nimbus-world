/**
 * Translator package for converting textual world descriptions into Composer Model JSON.
 *
 * <p>This package provides services and utilities for AI-powered translation of natural language
 * instructions into structured world definitions. The main entry point is {@link TranslatorService},
 * which uses the Composer Model description document to understand the target format and leverages
 * AI models to perform the translation.</p>
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link TranslatorService} - Main service for translation operations</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Autowired
 * private TranslatorService translatorService;
 *
 * public void translateWorldDescription(String description) {
 *     // Load the Composer Model description
 *     Optional<String> modelDescription = translatorService.loadComposerModelDescription();
 *
 *     // Create a chat model
 *     Optional<AiChat> chat = translatorService.createDefaultTranslatorChatModel();
 *
 *     if (modelDescription.isPresent() && chat.isPresent()) {
 *         // Perform translation (implementation to follow)
 *         String prompt = "Translate this to Composer Model JSON: " + description;
 *         String result = chat.get().ask(prompt);
 *     }
 * }
 * }</pre>
 *
 * @see de.mhus.nimbus.world.generator.translator.TranslatorService
 * @see de.mhus.nimbus.world.ai.model.AiModelService
 * @see de.mhus.nimbus.world.shared.world.WDocumentService
 */
package de.mhus.nimbus.world.generator.translator;
