/**
 * ShadowsEngineInfoCommand - Display BabylonJS engine and scene info
 *
 * Usage: shadowsEngineInfo
 * Shows BabylonJS version, WebGL info, scene settings that might affect shadows
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('ShadowsEngineInfoCommand');

export class ShadowsEngineInfoCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'shadowsEngineInfo';
  }

  description(): string {
    return 'Display BabylonJS engine and scene info for shadow debugging';
  }

  async execute(parameters: any[]): Promise<string> {
    const engineService = this.appContext.services.engine;
    const envService = this.appContext.services.environment;

    if (!engineService) {
      return 'EngineService not available';
    }

    const engine = engineService.getEngine();
    const scene = engineService.getScene();
    const cameraService = this.appContext.services.camera;
    const camera = cameraService?.getCamera();

    if (!engine || !scene) {
      return 'Engine or Scene not available';
    }

    // Get BabylonJS version
    const babylonVersion = (window as any).BABYLON?.Engine?.Version || 'unknown';

    // Get WebGL info
    const webglVersion = engine.webGLVersion;
    const isWebGL2 = (engine as any).isWebGL2;

    // Get scene settings
    const autoClear = scene.autoClear;
    const autoClearDepthAndStencil = scene.autoClearDepthAndStencil;

    // Get camera settings
    const cameraMaxZ = camera?.maxZ || 0;
    const cameraMinZ = camera?.minZ || 0;

    // Get light info
    const sunLight = envService?.getSunLight();
    const lightShadowEnabled = sunLight?.shadowEnabled;
    const lightShadowMinZ = sunLight?.shadowMinZ;
    const lightShadowMaxZ = sunLight?.shadowMaxZ;

    // Check for post-processing
    const postProcesses = scene.postProcesses?.length || 0;
    const activeCameras = scene.activeCameras?.length || 0;

    return `
BabylonJS Engine Info:

VERSION:
  BabylonJS: ${babylonVersion}
  WebGL: ${webglVersion}
  WebGL2: ${isWebGL2}

SCENE:
  autoClear: ${autoClear}
  autoClearDepthAndStencil: ${autoClearDepthAndStencil}
  Post Processes: ${postProcesses}
  Active Cameras: ${activeCameras}

CAMERA:
  minZ: ${cameraMinZ}
  maxZ: ${cameraMaxZ}

LIGHT (DirectionalLight):
  shadowEnabled: ${lightShadowEnabled}
  shadowMinZ: ${lightShadowMinZ}
  shadowMaxZ: ${lightShadowMaxZ}

CRITICAL CHECKS:
  ✓ Camera maxZ should match light shadowMaxZ
  ✓ WebGL2 required for PCF shadows
  ✓ autoClear should be true
    `.trim();
  }
}
