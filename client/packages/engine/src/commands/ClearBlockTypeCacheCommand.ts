/**
 * ClearBlockTypeCacheCommand - Clear BlockType cache
 *
 * Usage: /clearBlockTypeCache
 *
 * Clears the internal BlockType cache, forcing reload from server on next use.
 * Useful during development when BlockType definitions change.
 */

import { CommandHandler } from './CommandHandler';
import type { BlockTypeService } from '../services/BlockTypeService';
import { getLogger } from '@nimbus/shared';
import {AppContext} from "../AppContext";

const logger = getLogger('ClearBlockTypeCacheCommand');

export class ClearBlockTypeCacheCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'clearBlockTypeCache';
  }

  description(): string {
    return 'Clear BlockType cache (force reload from server)';
  }

  async execute(parameters: any[]): Promise<any> {
    try {
      this.appContext.services.blockType?.clearCache();

      logger.debug('BlockType cache cleared');

      return 'BlockType cache cleared. BlockTypes will be reloaded from server on next use.';
    } catch (error) {
      logger.error('Failed to clear BlockType cache', {}, error as Error);
      throw error;
    }
  }
}
