/**
 * SplashScreenCommand - Controls splash screen display
 *
 * Usage: splashscreen <assetPath> [audioPath]
 * Usage: splashscreen (empty to remove)
 * Example: splashscreen images/logo.png
 * Example: splashscreen images/logo.png audio/intro.ogg
 * Example: splashscreen
 */

import { CommandHandler } from './CommandHandler';
import { toString, getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('SplashScreenCommand');

/**
 * SplashScreen command - Shows or hides splash screen
 */
export class SplashScreenCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'splashscreen';
  }

  description(): string {
    return 'Shows or hides splash screen (splashscreen [assetPath] [audioPath])';
  }

  execute(parameters: any[]): any {
    const notificationService = this.appContext.services.notification;

    if (!notificationService) {
      logger.error('NotificationService not available');
      return { error: 'NotificationService not available' };
    }

    // If no parameters, remove splash screen
    if (parameters.length === 0) {
      try {
        notificationService.showSplashScreen('');
        const result = 'Splash screen removed';
        logger.info(`✓ ${result}`);
        return result;
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : String(error);
        logger.error(`✗ Failed to remove splash screen: ${errorMessage}`);
        throw error;
      }
    }

    // Parse assetPath
    const assetPath = toString(parameters[0]);

    if (!assetPath || assetPath.trim() === '') {
      try {
        notificationService.showSplashScreen('');
        const result = 'Splash screen removed';
        logger.info(`✓ ${result}`);
        return result;
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : String(error);
        logger.error(`✗ Failed to remove splash screen: ${errorMessage}`);
        throw error;
      }
    }

    // Parse optional audioPath
    const audioPath = parameters.length > 1 ? toString(parameters[1]) : undefined;

    // Show splash screen
    try {
      notificationService.showSplashScreen(assetPath, audioPath);
      const result = audioPath
        ? `Splash screen shown: ${assetPath} with audio: ${audioPath}`
        : `Splash screen shown: ${assetPath}`;
      logger.info(`✓ ${result}`);
      return result;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error(`✗ Failed to show splash screen: ${errorMessage}`);
      throw error;
    }
  }
}
