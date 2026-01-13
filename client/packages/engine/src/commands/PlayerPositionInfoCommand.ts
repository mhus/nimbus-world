/**
 * PlayerPositionInfoCommand - Shows detailed player position information
 *
 * Displays:
 * - Current player position (X, Y, Z)
 * - Height data for current column (X, Z)
 * - Selected block information (if any)
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('PlayerPositionInfoCommand');
import type { AppContext } from '../AppContext';
import { worldToChunk } from '../utils/ChunkUtils';

/**
 * PlayerPositionInfo command - Shows player position and environment info
 */
export class PlayerPositionInfoCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'playerPositionInfo';
  }

  description(): string {
    return 'Shows detailed player position information (position, height data, selected block, world info, client info)';
  }

  execute(parameters: any[]): any {
    const playerService = this.appContext.services.player;
    const chunkService = this.appContext.services.chunk;
    const clientService = this.appContext.services.client;

    if (!playerService || !chunkService) {
      logger.error('Required services not available');
      return { error: 'Services not available' };
    }

    const lines: string[] = [];
    lines.push('=== Player Position Info ===');
    lines.push('');

    // 0. World Info
    const worldInfo = this.appContext.worldInfo;
    if (worldInfo) {
      lines.push('World Info:');
      lines.push(`  World ID     : ${worldInfo.worldId}`);
      lines.push(`  Name         : ${worldInfo.name}`);
      if (worldInfo.description) {
        lines.push(`  Description  : ${worldInfo.description}`);
      }
      if (worldInfo.chunkSize !== undefined) {
        lines.push(`  Chunk Size   : ${worldInfo.chunkSize}`);
      }
      if (worldInfo.start) {
        lines.push(`  Start Pos    : (${worldInfo.start.x}, ${worldInfo.start.y}, ${worldInfo.start.z})`);
      }
      if (worldInfo.stop) {
        lines.push(`  Stop Pos     : (${worldInfo.stop.x}, ${worldInfo.stop.y}, ${worldInfo.stop.z})`);
      }
      if (worldInfo.entryPoint) {
        // area is always set and is a string
        const grid = worldInfo.entryPoint.grid;
        lines.push(`  Entry Point   : Area=${worldInfo.entryPoint.area}, Grid=(${grid.q}, ${grid.r})`);
      }
      if (worldInfo.settings) {
        lines.push(`  Max Players  : ${worldInfo.settings.maxPlayers}`);
        lines.push(`  PvP Enabled  : ${worldInfo.settings.pvpEnabled}`);
      }
      lines.push('');
    }

    // 0b. Server Info
    const serverInfo = this.appContext.serverInfo;
    if (serverInfo) {
      lines.push('Server Info:');
      if (serverInfo.name) {
        lines.push(`  Name         : ${serverInfo.name}`);
      }
      if (serverInfo.version) {
        lines.push(`  Version      : ${serverInfo.version}`);
      }
      lines.push('');
    }

    // 0c. Client Info
    if (clientService) {
      lines.push('Client Info:');
      lines.push(`  Type         : ${clientService.getClientType()}`);
      lines.push(`  Dev Mode     : ${clientService.isDevMode()}`);
      lines.push(`  Editor Mode  : ${clientService.isEditor()}`);
      lines.push(`  Language     : ${clientService.getLanguage()}`);
      lines.push('');
    }

    // 0d. Player Info
    const playerInfo = this.appContext.playerInfo;
    if (playerInfo) {
      lines.push('Player Info:');
      lines.push(`  Player ID    : ${playerInfo.playerId}`);
      lines.push(`  Display Name : ${playerInfo.title}`);
      lines.push(`  Walk Speed   : ${playerInfo.effectiveWalkSpeed.toFixed(2)} (base: ${playerInfo.baseWalkSpeed})`);
      lines.push(`  Run Speed    : ${playerInfo.effectiveRunSpeed.toFixed(2)} (base: ${playerInfo.baseRunSpeed})`);
      lines.push(`  Jump Speed   : ${playerInfo.effectiveJumpSpeed.toFixed(2)} (base: ${playerInfo.baseJumpSpeed})`);
      lines.push(`  Eye Height   : ${playerInfo.eyeHeight.toFixed(2)}`);
      lines.push(`  Turn Speed   : ${playerInfo.effectiveTurnSpeed.toFixed(2)} (base: ${playerInfo.baseTurnSpeed})`);
      lines.push(`  Stealth Range: ${playerInfo.stealthRange.toFixed(2)}`);
      lines.push('');
    }

    // 1. Player Position
    const position = playerService.getPosition();
    if (position) {
      lines.push('Player Position:');
      lines.push(`  X: ${position.x.toFixed(2)}`);
      lines.push(`  Y: ${position.y.toFixed(2)}`);
      lines.push(`  Z: ${position.z.toFixed(2)}`);
      lines.push('');

      // Calculate chunk coordinates
      const chunkSize = this.appContext.worldInfo?.chunkSize || 16;
      const { cx, cz } = worldToChunk(Math.floor(position.x), Math.floor(position.z), chunkSize);
      lines.push(`  Chunk: (${cx}, ${cz})`);
      lines.push('');

      // 2. Height Data for current column
      const chunk = chunkService.getChunk(cx, cz);
      if (chunk) {
        const localX = ((Math.floor(position.x) % chunkSize) + chunkSize) % chunkSize;
        const localZ = ((Math.floor(position.z) % chunkSize) + chunkSize) % chunkSize;
        const heightKey = `${localX},${localZ}`;
        const heightData = chunk.data.hightData.get(heightKey);

        lines.push('Height Data (current column):');
        if (heightData) {
          lines.push(`  Max Height   : ${heightData[2]}`);
          lines.push(`  Min Height   : ${heightData[3]}`);
          lines.push(`  Ground Level : ${heightData[4]}`);
          if (heightData[5] !== undefined) {
            lines.push(`  Water Height : ${heightData[5]}`);
          }
        } else {
          lines.push('  No height data available for this column');
        }
        lines.push('');
      } else {
        lines.push('Height Data:');
        lines.push('  Chunk not loaded');
        lines.push('');
      }
    } else {
      lines.push('Player Position: Not available');
      lines.push('');
    }

    lines.push('============================');

    const output = lines.join('\n');
    logger.debug(output);

    // Return structured data
    return {
      worldInfo: this.appContext.worldInfo,
      serverInfo: this.appContext.serverInfo,
      playerInfo: this.appContext.playerInfo,
      clientInfo: clientService ? {
        type: clientService.getClientType(),
        devMode: clientService.isDevMode(),
        editorMode: clientService.isEditor(),
        language: clientService.getLanguage(),
      } : null,
      position: position ? {
        x: position.x,
        y: position.y,
        z: position.z,
      } : null,
    };
  }
}
