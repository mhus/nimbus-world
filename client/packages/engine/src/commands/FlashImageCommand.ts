/**
 * FlashImageCommand - Flash an image on screen
 */

import { CommandHandler } from './CommandHandler';
import {  toNumber , getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('FlashImageCommand');

/**
 * Flash image command - Displays an animated image flash
 *
 * Usage:
 *   flashImage <assetPath> <duration> <opacity>
 *
 * Parameters:
 *   assetPath - Path to PNG image (e.g., "textures/effects/flash.png")
 *   duration  - Animation duration in milliseconds (e.g., 1000)
 *   opacity   - Image opacity 0.0-1.0 (e.g., 0.5)
 *
 * Examples:
 *   flashImage textures/effects/flash.png 1000 0.5
 *   flashImage ui/death.png 2000 0.8
 */
export class FlashImageCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'flashImage';
  }

  description(): string {
    return 'Flash an image on screen (assetPath duration opacity)';
  }

  execute(parameters: any[]): any {
    const notificationService = this.appContext.services.notification;

    if (!notificationService) {
      logger.error('NotificationService not available');
      return { error: 'NotificationService not available' };
    }

    // Validate parameters
    if (parameters.length < 3) {
      logger.error('Usage: flashImage <assetPath> <duration> <opacity>');
      return { error: 'Missing parameters. Usage: flashImage <assetPath> <duration> <opacity>' };
    }

    const assetPath = parameters[0];
    const duration = toNumber(parameters[1]);
    const opacity = toNumber(parameters[2]);

    // Validate assetPath
    if (typeof assetPath !== 'string') {
      logger.error('Asset path must be a string');
      return { error: 'Asset path must be a string' };
    }

    // Validate duration
    if (isNaN(duration) || duration <= 0) {
      logger.error(`Invalid duration: ${parameters[1]}. Must be positive number.`);
      return { error: 'Duration must be a positive number (milliseconds)' };
    }

    // Validate opacity
    if (isNaN(opacity) || opacity < 0 || opacity > 1.0) {
      logger.error(`Invalid opacity: ${parameters[2]}. Must be 0.0-1.0.`);
      return { error: 'Opacity must be between 0.0 and 1.0' };
    }

    // Flash the image
    notificationService.flashImage(assetPath, duration, opacity);

    const message = `Flashing image: ${assetPath} (${duration}ms, opacity ${opacity.toFixed(2)})`;
    logger.debug(message);

    return {
      assetPath,
      duration,
      opacity,
      message,
    };
  }
}
