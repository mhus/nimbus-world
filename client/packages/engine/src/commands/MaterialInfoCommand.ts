/**
 * MaterialInfoCommand - Shows information about materials
 *
 * Usage:
 * - materialInfo()           : List all cached material keys
 * - materialInfo('key')      : Show detailed info about specific material
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('MaterialInfoCommand');
import type { AppContext } from '../AppContext';

/**
 * MaterialInfo command - Shows material cache and details
 */
export class MaterialInfoCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'materialInfo';
  }

  description(): string {
    return 'Shows material information. Usage: materialInfo() - list all keys, materialInfo(key) - show material details';
  }

  execute(parameters: any[]): any {
    const engineService = this.appContext.services.engine;

    if (!engineService) {
      logger.error('EngineService not available');
      return { error: 'EngineService not available' };
    }

    const materialService = engineService.getMaterialService();
    if (!materialService) {
      logger.error('MaterialService not available');
      return { error: 'MaterialService not available' };
    }

    const lines: string[] = [];

    // Check if a specific material key was provided
    const materialKey = parameters.length > 0 ? String(parameters[0]) : null;

    if (materialKey) {
      // Show details for specific material
      lines.push(`=== Material Info: ${materialKey} ===`);
      lines.push('');

      const material = materialService.getMaterialByKey(materialKey);

      if (!material) {
        lines.push('Material not found in cache');
        logger.debug(lines.join('\n'));
        return { error: 'Material not found', materialKey };
      }

      lines.push('Material Properties:');
      lines.push(`  Name              : ${material.name}`);
      lines.push(`  ID                : ${material.id}`);
      lines.push(`  backFaceCulling   : ${material.backFaceCulling}`);
      lines.push(`  Alpha Mode        : ${material.alphaMode}`);
      lines.push(`  Need Depth Pre-Pass: ${material.needDepthPrePass}`);
      lines.push(`  wireframe         : ${material.wireframe}`);
      lines.push('');

      // Check if it's a StandardMaterial
      if ('diffuseTexture' in material) {
        const stdMat = material as any;
        lines.push('Standard Material Properties:');

        if (stdMat.diffuseTexture) {
          lines.push(`  Diffuse Texture   : ${stdMat.diffuseTexture.url || stdMat.diffuseTexture.name}`);
          lines.push(`    hasAlpha        : ${stdMat.diffuseTexture.hasAlpha}`);
          lines.push(`    uScale          : ${stdMat.diffuseTexture.uScale}`);
          lines.push(`    vScale          : ${stdMat.diffuseTexture.vScale}`);
          lines.push(`    uOffset         : ${stdMat.diffuseTexture.uOffset}`);
          lines.push(`    vOffset         : ${stdMat.diffuseTexture.vOffset}`);
          lines.push(`    wrapU           : ${stdMat.diffuseTexture.wrapU}`);
          lines.push(`    wrapV           : ${stdMat.diffuseTexture.wrapV}`);
        }

        if (stdMat.diffuseColor) {
          lines.push(`  Diffuse Color     : (${stdMat.diffuseColor.r.toFixed(2)}, ${stdMat.diffuseColor.g.toFixed(2)}, ${stdMat.diffuseColor.b.toFixed(2)})`);
        }

        if (stdMat.specularColor) {
          lines.push(`  Specular Color    : (${stdMat.specularColor.r.toFixed(2)}, ${stdMat.specularColor.g.toFixed(2)}, ${stdMat.specularColor.b.toFixed(2)})`);
        }
      }

      // Check if it's a ShaderMaterial
      if ('setTexture' in material && !('diffuseTexture' in material)) {
        lines.push('Shader Material (custom shader)');
      }

      lines.push('');
      lines.push('Complete Material JSON:');
      lines.push(JSON.stringify(material, (key, value) => {
        // Skip circular references and large objects
        if (key === '_scene' || key === '_engine' || key === 'metadata') return undefined;
        return value;
      }, 2).split('\n').map(line => '  ' + line).join('\n'));

    } else {
      // List all material keys
      lines.push('=== Material Cache ===');
      lines.push('');

      const materialKeys = materialService.getAllMaterialKeys();

      lines.push(`Total materials in cache: ${materialKeys.length}`);
      lines.push('');

      if (materialKeys.length > 0) {
        lines.push('Material Keys:');
        materialKeys.forEach((key, index) => {
          lines.push(`  [${index}] ${key}`);
        });
        lines.push('');
        lines.push('Use materialInfo("key") to see details for a specific material');
      } else {
        lines.push('No materials in cache');
      }
    }

    lines.push('');
    lines.push('============================');

    const output = lines.join('\n');
    logger.debug(output);

    // Return structured data
    if (materialKey) {
      const material = materialService.getMaterialByKey(materialKey);
      return {
        materialKey,
        material: material ? {
          name: material.name,
          id: material.id,
          backFaceCulling: material.backFaceCulling,
          alphaMode: material.alphaMode,
        } : null,
      };
    } else {
      return {
        totalMaterials: materialService.getAllMaterialKeys().length,
        materialKeys: materialService.getAllMaterialKeys(),
      };
    }
  }
}
