/**
 * OverlayModelCommand - Apply or remove a player overlay model
 *
 * Sent by server when player activates/deactivates an overlay (mount, vehicle, occupation).
 * Replaces the player's visual model and locks movement parameters.
 *
 * Usage:
 * - /overlayModel <modelId> - Apply overlay model with given EntityModel ID
 * - /overlayModel            - Remove overlay model (empty args)
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('OverlayModelCommand');

export class OverlayModelCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'overlayModel';
  }

  description(): string {
    return 'Apply or remove player overlay model (mount, vehicle)';
  }

  async execute(parameters: any[]): Promise<any> {
    const playerService = this.appContext.services.player;
    if (!playerService) {
      return 'Error: PlayerService not available';
    }

    const modelId = parameters.length > 0 ? String(parameters[0]) : '';

    if (modelId && modelId.length > 0) {
      logger.info('Applying overlay model', { modelId });
      await playerService.applyOverlayModel(modelId);
      return `Overlay model applied: ${modelId}`;
    } else {
      logger.info('Removing overlay model');
      await playerService.removeOverlayModel();
      return 'Overlay model removed';
    }
  }
}
