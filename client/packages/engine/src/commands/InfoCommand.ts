/**
 * InfoCommand - Shows client information
 */

import {  SHARED_VERSION , getLogger } from '@nimbus/shared';
import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';

const logger = getLogger('InfoCommand');

const CLIENT_VERSION = '2.0.0';

/**
 * Info command - Shows client information
 */
export class InfoCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'info';
  }

  description(): string {
    return 'Shows client information (version, build mode, connection status)';
  }

  execute(parameters: any[]): any {
    const buildMode = __EDITOR__ ? 'Editor' : 'Viewer';
    const clientService = this.appContext.services.client;
    const networkService = this.appContext.services.network;

    // Build info object
    const info = {
      'Client Version': CLIENT_VERSION,
      'Shared Library': SHARED_VERSION,
      'Build Mode': buildMode,
      'Client Type': clientService?.getClientType() || 'Unknown',
      'Development Mode': clientService?.isDevMode() ? 'Yes' : 'No',
      'Server Connected': networkService?.isConnected() ? 'Yes' : 'No',
      'Server Info': this.appContext.serverInfo?.name || 'Not available',
      'World': this.appContext.worldInfo?.name || 'Not loaded',
    };

    // Format output
    const lines: string[] = [];
    lines.push('=== Nimbus Client Info ===');
    lines.push('');

    for (const [key, value] of Object.entries(info)) {
      lines.push(`  ${key.padEnd(20)}: ${value}`);
    }

    lines.push('');
    lines.push('==========================');

    const output = lines.join('\n');

    // Log to console
    logger.debug(output);

    return info;
  }
}
