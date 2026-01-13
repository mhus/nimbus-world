/**
 * NotificationCommand - Creates notifications in the NotificationService
 *
 * Usage: notification <type> <from> <message>
 * Example: notification 0 null "System message"
 * Example: notification 11 "Player1" "Hello!"
 */

import { CommandHandler } from './CommandHandler';
import { toNumber, toString, getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { NotificationType } from '../types/Notification';

const logger = getLogger('NotificationCommand');

/**
 * Notification command - Creates notifications
 */
export class NotificationCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'notification';
  }

  description(): string {
    return 'Creates a notification (notification <type> <from> <message> [texturePath])';
  }

  execute(parameters: any[]): any {
    const notificationService = this.appContext.services.notification;

    if (!notificationService) {
      logger.error('NotificationService not available');
      return { error: 'NotificationService not available' };
    }

    if (parameters.length < 3) {
      logger.error('Usage: notification <type> <from> <message> [texturePath]');
      logger.error('  type: 0-31 (0=system, 10-12=chat, 20-21=overlay, 30-31=quest)');
      logger.error('  from: sender name or "null" for no sender');
      logger.error('  message: notification message');
      logger.error('  texturePath: (optional) asset path for icon (e.g., "items/sword.png")');
      logger.error('');
      logger.error('Examples:');
      logger.error('  notification 0 null "System info"');
      logger.error('  notification 11 "Player1" "Hello everyone!"');
      logger.error('  notification 20 null "LEVEL UP!" items/trophy.png');
      return { error: 'Invalid arguments' };
    }

    // Parse type
    const type = toNumber(parameters[0]);
    if (isNaN(type) || type < 0 || type > 31) {
      logger.error('Invalid type: must be a number between 0 and 31');
      return { error: 'Invalid type' };
    }

    // Parse from (null or string)
    const fromParam = toString(parameters[1]);
    const from = fromParam.toLowerCase() === 'null' ? null : fromParam;

    // Find where texturePath might be (search for path-like pattern in remaining params)
    let texturePath: string | null = null;
    let messageEndIndex = parameters.length;

    // Check if last parameter looks like a path (contains . or /)
    const lastParam = toString(parameters[parameters.length - 1]);
    if (lastParam.includes('/') || lastParam.includes('.')) {
      texturePath = lastParam;
      messageEndIndex = parameters.length - 1;
    }

    // Parse message (join parameters between from and optional texturePath)
    const message = parameters.slice(2, messageEndIndex).join(' ');

    // Create notification
    try {
      notificationService.newNotification(type as NotificationType, from, message, texturePath);

      const result = `Notification created: type=${type}, from=${from || 'null'}, message="${message}"${texturePath ? `, icon="${texturePath}"` : ''}`;
      logger.debug(`✓ ${result}`);
      return result;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error(`✗ Failed to create notification: ${errorMessage}`);
      throw error;
    }
  }
}
