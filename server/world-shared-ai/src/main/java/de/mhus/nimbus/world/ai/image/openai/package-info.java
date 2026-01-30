/**
 * OpenAI DALL-E image generation provider.
 * <p>
 * Implements the image generation framework using OpenAI's DALL-E models.
 * Supports DALL-E 2 and DALL-E 3.
 * <p>
 * Main components:
 * <ul>
 *     <li>{@link de.mhus.nimbus.world.ai.image.openai.OpenAiImageModelProvider} - Provider implementation</li>
 *     <li>{@link de.mhus.nimbus.world.ai.image.openai.OpenAiImageModelImpl} - Image model implementation</li>
 * </ul>
 * <p>
 * Supported models:
 * <ul>
 *     <li>dall-e-2 - DALL-E 2 (256x256, 512x512, 1024x1024)</li>
 *     <li>dall-e-3 - DALL-E 3 (1024x1024, 1024x1792, 1792x1024)</li>
 * </ul>
 */
package de.mhus.nimbus.world.ai.image.openai;
