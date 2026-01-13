/**
 * Block Service
 * Manages block inspection and debugging operations
 */

import type { BlockOriginDto } from '@nimbus/shared';
import { apiService } from './ApiService';

export interface BlockOriginResponse {
  found: boolean;
  origin?: BlockOriginDto;
  message?: string;
}

export class BlockService {
  /**
   * Find origin of a block at specific coordinates
   */
  async findOrigin(
    worldId: string,
    x: number,
    y: number,
    z: number
  ): Promise<BlockOriginResponse> {
    return apiService.get<BlockOriginResponse>(
      `/control/worlds/${worldId}/blocks/origin`,
      { x, y, z }
    );
  }
}

export const blockService = new BlockService();
