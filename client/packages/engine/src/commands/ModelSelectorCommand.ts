/**
 * ModelSelectorCommand - Control the model selector
 *
 * The model selector allows selecting multiple blocks simultaneously with individual colors per element.
 * It has two activation levels: enabled and visible.
 *
 * Usage:
 *   modelselector                                                            - Show current status
 *   modelselector enable <defaultColor> <watchBlocks> <show> [x y z color ...] - Enable model selector
 *   modelselector disable                                                    - Disable model selector
 *   modelselector show <true|false>                                          - Show/hide selector
 *   modelselector add <x1> <y1> <z1> <color1> [... <xn> <yn> <zn> <colorn>] - Add coordinates
 *   modelselector remove <x1> <y1> <z1> [... <xn> <yn> <zn>]                - Remove coordinates
 *   modelselector move <offsetX> <offsetY> <offsetZ>                         - Move all blocks by offset
 *   modelselector list                                                       - List all coordinates
 *
 * watchBlocks parameter: Source filter string or "null"/"false" to disable
 *   - If set to source string (e.g., "layerId:name"), only blocks with matching source are added
 *   - If set to "null" or "false", watchBlocks is disabled
 *
 * Examples:
 *   modelselector enable #ffff00 "abc123:GroundLayer" true 10 20 30 #ff0000  - Watch blocks from GroundLayer
 *   modelselector enable #ff0000 null true                                    - Enable without watchBlocks
 *   modelselector enable #ff0000 false true                                   - Enable without watchBlocks
 *   modelselector disable                                                     - Disable model selector
 *   modelselector show false                                                  - Hide without removing coordinates
 *   modelselector add 10 20 30 #ff0000 11 20 30 #00ff00                      - Add two blocks with colors
 *   modelselector remove 10 20 30                                             - Remove one block
 *   modelselector move 5 0 0                                                  - Move all blocks by +5 in X direction
 *   modelselector list                                                        - List all coordinates
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger, toBoolean, toNumber, toString, type Vector3Color } from '@nimbus/shared';
import { Vector3 } from '@babylonjs/core';

const logger = getLogger('ModelSelectorCommand');

export class ModelSelectorCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'modelselector';
  }

  description(): string {
    return 'Control the model selector with individual colors per element (enable|disable|show|add|remove|move|list)';
  }

  async execute(parameters: any[]): Promise<any> {
    const selectService = this.appContext.services.select;
    if (!selectService) {
      return 'Error: SelectService not available';
    }

    // No parameters - show current status
    if (parameters.length === 0) {
      return this.showStatus(selectService);
    }

    const subcommand = toString(parameters[0]).toLowerCase();

    switch (subcommand) {
      case 'enable':
        return this.executeEnable(selectService, parameters.slice(1));
      case 'disable':
        return this.executeDisable(selectService);
      case 'show':
        return this.executeShow(selectService, parameters.slice(1));
      case 'add':
        return this.executeAdd(selectService, parameters.slice(1));
      case 'remove':
        return this.executeRemove(selectService, parameters.slice(1));
      case 'move':
        return this.executeMove(selectService, parameters.slice(1));
      case 'list':
        return this.executeList(selectService);
      default:
        return {
          error: `Unknown subcommand: ${subcommand}`,
          usage: 'modelselector [enable|disable|show|add|remove|move|list]',
          examples: [
            'modelselector enable #ffff00 "abc:Layer" true 10 20 30 #ff0000',
            'modelselector disable',
            'modelselector show false',
            'modelselector add 10 20 30 #ff0000 11 20 30 #00ff00',
            'modelselector remove 10 20 30',
            'modelselector move 5 0 0',
            'modelselector list',
          ],
        };
    }
  }

  /**
   * Show current status
   */
  private showStatus(selectService: any): any {
    const enabled = selectService.isModelSelectorEnabled();
    const visible = selectService.isModelSelectorVisible();
    const watchBlocks = selectService.getModelSelectorWatchBlocks();
    const coordinates = selectService.getModelSelectorCoordinates();

    return {
      enabled,
      visible,
      watchBlocks: watchBlocks || 'null',
      blockCount: coordinates.length,
      usage: 'modelselector [enable|disable|show|add|remove|move|list]',
      examples: [
        'modelselector enable #ffff00 "abc:Layer" true 10 20 30 #ff0000 - Watch Layer source, one red block',
        'modelselector enable #ffff00 null true                          - No source filter',
        'modelselector disable                                           - Disable',
        'modelselector show false                                        - Hide',
        'modelselector add 10 20 30 #ff0000                              - Add red block',
        'modelselector move 5 0 0                                        - Move blocks',
        'modelselector list                                              - List blocks',
      ],
    };
  }

  /**
   * Enable model selector
   * Parameters: <defaultColor> <watchBlocks> <show> [<x1> <y1> <z1> <color1> ... <xn> <yn> <zn> <colorn>]
   * watchBlocks: Source filter string or "null"/"false" to disable
   */
  private executeEnable(selectService: any, params: any[]): any {
    if (params.length < 3) {
      return {
        error: 'Missing required parameters',
        usage: 'modelselector enable <defaultColor> <watchBlocks> <show> [<x1> <y1> <z1> <color1> ...]',
        examples: [
          'modelselector enable #ffff00 "abc:Layer" true                            - Watch blocks from Layer, visible',
          'modelselector enable #ff0000 null true 10 20 30 #00ff00                 - No watch, one green block',
          'modelselector enable #ffff00 false true 10 20 30 #ff0000 11 20 30 #00ff00 - No watch, two blocks',
        ],
      };
    }

    const defaultColor = toString(params[0]);
    const watchBlocksParam = toString(params[1]);
    // Convert parameter: "null", "false", or "" -> null, otherwise use as string
    const watchBlocks = (watchBlocksParam === 'null' || watchBlocksParam === 'false' || watchBlocksParam === '')
      ? null
      : watchBlocksParam;
    const show = toBoolean(params[2]);

    // Parse coordinates (groups of 4: x, y, z, color)
    const coordinates: Vector3Color[] = [];
    for (let i = 3; i < params.length; i += 4) {
      if (i + 3 < params.length) {
        const x = toNumber(params[i]);
        const y = toNumber(params[i + 1]);
        const z = toNumber(params[i + 2]);
        const color = toString(params[i + 3]);
        coordinates.push({ x, y, z, color });
      } else {
        return {
          error: 'Invalid coordinates (must be groups of 4: x y z color)',
          received: params.slice(3),
        };
      }
    }

    try {
      selectService.enableModelSelector(defaultColor, watchBlocks, show, coordinates);
      logger.info('Model selector enabled', { defaultColor, watchBlocks, show, blockCount: coordinates.length });
      return {
        success: true,
        message: 'Model selector enabled',
        defaultColor,
        watchBlocks,
        visible: show,
        blockCount: coordinates.length,
      };
    } catch (error) {
      logger.error('Failed to enable model selector', error);
      return {
        error: 'Failed to enable model selector',
        details: error instanceof Error ? error.message : String(error),
      };
    }
  }

  /**
   * Disable model selector
   */
  private executeDisable(selectService: any): any {
    try {
      selectService.disableModelSelector();
      logger.info('Model selector disabled');
      return {
        success: true,
        message: 'Model selector disabled',
      };
    } catch (error) {
      logger.error('Failed to disable model selector', error);
      return {
        error: 'Failed to disable model selector',
        details: error instanceof Error ? error.message : String(error),
      };
    }
  }

  /**
   * Show or hide model selector
   * Parameters: <true|false>
   */
  private executeShow(selectService: any, params: any[]): any {
    if (params.length === 0) {
      return {
        error: 'Missing required parameter',
        usage: 'modelselector show <true|false>',
        examples: [
          'modelselector show true  - Show model selector',
          'modelselector show false - Hide model selector',
        ],
      };
    }

    const visible = toBoolean(params[0]);

    try {
      selectService.showModelSelector(visible);
      logger.info('Model selector visibility changed', { visible });
      return {
        success: true,
        message: `Model selector ${visible ? 'shown' : 'hidden'}`,
        visible,
      };
    } catch (error) {
      logger.error('Failed to change model selector visibility', error);
      return {
        error: 'Failed to change visibility',
        details: error instanceof Error ? error.message : String(error),
      };
    }
  }

  /**
   * Add coordinates to model selector
   * Parameters: <x1> <y1> <z1> <color1> [... <xn> <yn> <zn> <colorn>]
   */
  private executeAdd(selectService: any, params: any[]): any {
    if (params.length === 0 || params.length % 4 !== 0) {
      return {
        error: 'Invalid coordinates (must be groups of 4: x y z color)',
        usage: 'modelselector add <x1> <y1> <z1> <color1> [... <xn> <yn> <zn> <colorn>]',
        examples: [
          'modelselector add 10 20 30 #ff0000                        - Add one red block',
          'modelselector add 10 20 30 #ff0000 11 20 30 #00ff00      - Add two blocks with colors',
        ],
      };
    }

    // Parse coordinates (groups of 4: x, y, z, color)
    const coordinates: Vector3Color[] = [];
    for (let i = 0; i < params.length; i += 4) {
      const x = toNumber(params[i]);
      const y = toNumber(params[i + 1]);
      const z = toNumber(params[i + 2]);
      const color = toString(params[i + 3]);
      coordinates.push({ x, y, z, color });
    }

    try {
      selectService.addToModelSelector(coordinates);
      logger.info('Coordinates added to model selector', { count: coordinates.length });
      return {
        success: true,
        message: `Added ${coordinates.length} block(s) to model selector`,
        addedCount: coordinates.length,
        totalCount: selectService.getModelSelectorCoordinates().length,
      };
    } catch (error) {
      logger.error('Failed to add coordinates to model selector', error);
      return {
        error: 'Failed to add coordinates',
        details: error instanceof Error ? error.message : String(error),
      };
    }
  }

  /**
   * Remove coordinates from model selector
   * Parameters: <x1> <y1> <z1> [... <xn> <yn> <zn>]
   * Note: Color is not needed for removal, only position matters
   */
  private executeRemove(selectService: any, params: any[]): any {
    if (params.length === 0 || params.length % 3 !== 0) {
      return {
        error: 'Invalid coordinates (must be groups of 3: x y z)',
        usage: 'modelselector remove <x1> <y1> <z1> [... <xn> <yn> <zn>]',
        examples: [
          'modelselector remove 10 20 30           - Remove one block',
          'modelselector remove 10 20 30 11 20 30 - Remove two blocks',
        ],
      };
    }

    // Parse coordinates (groups of 3, color not needed for removal)
    const coordinates: Vector3Color[] = [];
    for (let i = 0; i < params.length; i += 3) {
      const x = toNumber(params[i]);
      const y = toNumber(params[i + 1]);
      const z = toNumber(params[i + 2]);
      coordinates.push({ x, y, z, color: '#000000' }); // Dummy color, not used for removal
    }

    try {
      selectService.removeFromModelSelector(coordinates);
      logger.info('Coordinates removed from model selector', { count: coordinates.length });
      return {
        success: true,
        message: `Removed ${coordinates.length} block(s) from model selector`,
        removedCount: coordinates.length,
        totalCount: selectService.getModelSelectorCoordinates().length,
      };
    } catch (error) {
      logger.error('Failed to remove coordinates from model selector', error);
      return {
        error: 'Failed to remove coordinates',
        details: error instanceof Error ? error.message : String(error),
      };
    }
  }

  /**
   * Move all model selector blocks by offset
   * Parameters: <offsetX> <offsetY> <offsetZ>
   */
  private executeMove(selectService: any, params: any[]): any {
    if (params.length !== 3) {
      return {
        error: 'Invalid parameters (must be exactly 3: offsetX offsetY offsetZ)',
        usage: 'modelselector move <offsetX> <offsetY> <offsetZ>',
        examples: [
          'modelselector move 5 0 0    - Move all blocks +5 in X direction',
          'modelselector move 0 -1 0   - Move all blocks -1 in Y direction',
          'modelselector move 2 3 -1   - Move blocks by offset (2, 3, -1)',
        ],
      };
    }

    const offsetX = toNumber(params[0]);
    const offsetY = toNumber(params[1]);
    const offsetZ = toNumber(params[2]);
    const offset = new Vector3(offsetX, offsetY, offsetZ);

    try {
      selectService.moveModelSelector(offset);
      logger.info('Model selector moved', { offset: { x: offsetX, y: offsetY, z: offsetZ } });
      return {
        success: true,
        message: 'Model selector moved',
        offset: { x: offsetX, y: offsetY, z: offsetZ },
        blockCount: selectService.getModelSelectorCoordinates().length,
      };
    } catch (error) {
      logger.error('Failed to move model selector', error);
      return {
        error: 'Failed to move model selector',
        details: error instanceof Error ? error.message : String(error),
      };
    }
  }

  /**
   * List all coordinates in model selector
   */
  private executeList(selectService: any): any {
    const coordinates = selectService.getModelSelectorCoordinates();

    if (coordinates.length === 0) {
      return {
        message: 'Model selector is empty',
        blockCount: 0,
      };
    }

    const coordList = coordinates.map((coord: Vector3Color) => ({
      x: coord.x,
      y: coord.y,
      z: coord.z,
      color: coord.color,
    }));

    return {
      blockCount: coordinates.length,
      coordinates: coordList,
      enabled: selectService.isModelSelectorEnabled(),
      visible: selectService.isModelSelectorVisible(),
    };
  }
}
