/**
 * ShadowsInfoCommand - Display shadow system information
 *
 * Usage: shadowsInfo
 * Shows detailed information about the current shadow system state
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('ShadowsInfoCommand');

export class ShadowsInfoCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'shadowsInfo';
  }

  description(): string {
    return 'Display shadow system information';
  }

  async execute(parameters: any[]): Promise<string> {
    const envService = this.appContext.services.environment;

    if (!envService) {
      return 'EnvironmentService not available';
    }

    const info = envService.getShadowInfo();
    const engineService = this.appContext.services.engine;
    const fps = engineService?.getEngine()?.getFps() ?? 0;

    // Refresh rate meanings
    const refreshRateName = info.refreshRate === 0 ? 'ONCE' :
                           info.refreshRate === 1 ? 'EVERY_FRAME' :
                           `CUSTOM(${info.refreshRate})`;

    return `
Shadow System Info:
  Enabled: ${info.enabled}
  Quality: ${info.quality}
  Map Size: ${info.mapSize}px
  Active Casters: ${info.activeCasters}
  Darkness: ${info.darkness.toFixed(2)} (0=no shadows, 1=dark)
  Refresh Rate: ${refreshRateName}
  FPS: ${fps.toFixed(1)}
    `.trim();
  }
}
