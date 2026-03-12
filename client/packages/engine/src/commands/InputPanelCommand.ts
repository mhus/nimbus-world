/**
 * InputPanelCommand - Toggle input panel
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';

/**
 * Toggle input panel command
 */
export class InputPanelCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'inputPanel';
  }

  description(): string {
    return 'Toggle the input panel for sending messages';
  }

  execute(_parameters: any[]): any {
    const notificationService = this.appContext.services.notification;

    if (!notificationService) {
      return { error: 'NotificationService not available' };
    }

    notificationService.toggleInputPanel();
    return { message: 'Input panel toggled' };
  }
}
