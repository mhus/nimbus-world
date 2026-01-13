/**
 * CenterTextCommand - Display text in center of screen
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('CenterTextCommand');
import type { AppContext } from '../AppContext';

/**
 * Center text command - Displays permanent text in screen center
 *
 * Usage:
 *   centerText <text>  - Display text in center
 *   centerText         - Clear center text
 *
 * Examples:
 *   centerText "Game Paused"
 *   centerText "Verbindung unterbrochen"
 *   centerText         - Clear text
 */
export class CenterTextCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'centerText';
  }

  description(): string {
    return 'Display permanent text in center of screen (no parameter to clear)';
  }

  execute(parameters: any[]): any {
    const notificationService = this.appContext.services.notification;

    if (!notificationService) {
      logger.error('NotificationService not available');
      return { error: 'NotificationService not available' };
    }

    // No parameter or empty: clear text
    if (parameters.length === 0) {
      notificationService.clearCenterText();
      logger.debug('Center text cleared');
      return { message: 'Center text cleared' };
    }

    // Join all parameters to support multi-word text
    const text = parameters.join(' ');

    // Set center text
    notificationService.setCenterText(text);

    const message = `Center text set: "${text}"`;
    logger.debug(message);

    return {
      text,
      message,
    };
  }
}
