/**
 * ShadowsDebugCommand - Display detailed shadow debug information
 *
 * Usage: shadowsDebug
 * Shows detailed debug info including light direction, shadow map settings, etc.
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('ShadowsDebugCommand');

export class ShadowsDebugCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'shadowsDebug';
  }

  description(): string {
    return 'Display detailed shadow debug information';
  }

  async execute(parameters: any[]): Promise<string> {
    const envService = this.appContext.services.environment;

    if (!envService) {
      return 'EnvironmentService not available';
    }

    const shadowGen = envService.getShadowGenerator();
    if (!shadowGen) {
      return 'Shadow generator not initialized';
    }

    const sunLight = envService.getSunLight();
    const shadowMap = shadowGen.getShadowMap();
    const info = envService.getShadowInfo();

    // Get light info
    const lightDir = sunLight?.direction;
    const lightIntensity = sunLight?.intensity ?? 0;

    // Get shadow map info
    const renderList = shadowMap?.renderList || [];
    const refreshRate = shadowMap?.refreshRate ?? -1;
    const refreshNames: { [key: number]: string } = {
      0: 'RENDER_ONCE',
      1: 'RENDER_ONEVERYFRAME',
      2: 'RENDER_ONEVERYTWOFRAMES',
    };

    // Get bias settings
    const bias = shadowGen.bias;
    const normalBias = shadowGen.normalBias;

    // Get filter settings
    const useESM = shadowGen.useExponentialShadowMap;
    const usePCF = shadowGen.usePercentageCloserFiltering;
    const usePCSS = shadowGen.useContactHardeningShadow;

    // Check receivers in scene
    const engineService = this.appContext.services.engine;
    const scene = engineService?.getScene();
    let receiversCount = 0;
    let totalMeshes = 0;

    if (scene) {
      totalMeshes = scene.meshes.length;
      for (const mesh of scene.meshes) {
        if (mesh.receiveShadows) {
          receiversCount++;
        }
      }
    }

    // Get light position
    const lightPos = sunLight?.position;

    return `
Shadow Debug Info:

GENERATOR:
  Enabled: ${info.enabled}
  Quality: ${info.quality}
  Map Size: ${info.mapSize}px
  Darkness: ${info.darkness.toFixed(2)}

LIGHT:
  Direction: (${lightDir?.x.toFixed(2)}, ${lightDir?.y.toFixed(2)}, ${lightDir?.z.toFixed(2)})
  Position: (${lightPos?.x.toFixed(1)}, ${lightPos?.y.toFixed(1)}, ${lightPos?.z.toFixed(1)})
  Intensity: ${lightIntensity.toFixed(2)}
  AutoUpdate: ${(sunLight as any)?.autoUpdateExtentsShadowMap}

SHADOW MAP:
  Refresh Rate: ${refreshNames[refreshRate] || `CUSTOM(${refreshRate})`}
  Casters in renderList: ${renderList.length}

RECEIVERS:
  Meshes with receiveShadows: ${receiversCount} / ${totalMeshes}

FILTERS:
  ESM: ${useESM}
  PCF: ${usePCF}
  PCSS: ${usePCSS}

BIAS:
  Bias: ${bias}
  Normal Bias: ${normalBias}

CASCADED SHADOW MAP (CSM):
  Stabilize Cascades: ${info.stabilizeCascades ?? 'unknown'}
  Auto Calc Depth Bounds: ${info.autoCalcDepthBounds ?? 'unknown'}
  Lambda: ${info.lambda?.toFixed(2) ?? 'unknown'}
  Num Cascades: ${info.numCascades ?? 'unknown'}
    `.trim();
  }
}
