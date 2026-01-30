/**
 * AI image generation framework.
 * <p>
 * Provides an abstraction layer for different AI image generation providers
 * (e.g., OpenAI DALL-E, Vertex AI Imagen).
 * <p>
 * Main components:
 * <ul>
 *     <li>{@link de.mhus.nimbus.world.ai.image.AiImageModel} - Interface for image generation instances</li>
 *     <li>{@link de.mhus.nimbus.world.ai.image.LangchainImageModel} - Interface for image model providers</li>
 *     <li>{@link de.mhus.nimbus.world.ai.image.AiImage} - Generated image container</li>
 *     <li>{@link de.mhus.nimbus.world.ai.image.AiImageOptions} - Configuration options</li>
 * </ul>
 * <p>
 * Usage example:
 * <pre>{@code
 * Optional<AiImageModel> modelOpt = aiModelService.createImageModel("openai:dall-e-3");
 * if (modelOpt.isPresent()) {
 *     AiImageModel model = modelOpt.get();
 *     AiImage image = model.generate("A beautiful sunset over mountains");
 *     byte[] imageBytes = image.getBytes();
 * }
 * }</pre>
 */
package de.mhus.nimbus.world.ai.image;
