/**
 * WebGLCheckCommand - Check WebGL support
 *
 * Usage: webglCheck
 * Checks if WebGL2 is available in the browser
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('WebGLCheckCommand');

export class WebGLCheckCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'webglCheck';
  }

  description(): string {
    return 'Check WebGL and WebGL2 support';
  }

  async execute(parameters: any[]): Promise<string> {
    const canvas = document.createElement('canvas');

    // Check WebGL1
    const gl1 = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
    const hasWebGL1 = !!gl1;

    // Check WebGL2
    const gl2 = canvas.getContext('webgl2');
    const hasWebGL2 = !!gl2;

    // Get WebGL version from vendor
    let webglVersion = 'unknown';
    let vendor = 'unknown';
    let renderer = 'unknown';

    if (gl2) {
      webglVersion = 'WebGL2';
      const debugInfo = gl2.getExtension('WEBGL_debug_renderer_info');
      if (debugInfo) {
        vendor = gl2.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL) || 'unknown';
        renderer = gl2.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL) || 'unknown';
      }
    } else if (gl1) {
      webglVersion = 'WebGL1';
      const webgl1 = gl1 as WebGLRenderingContext;
      const debugInfo = webgl1.getExtension('WEBGL_debug_renderer_info');
      if (debugInfo) {
        vendor = webgl1.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL) || 'unknown';
        renderer = webgl1.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL) || 'unknown';
      }
    }

    // Check BabylonJS Engine
    const engineService = this.appContext.services.engine;
    const engine = engineService?.getEngine();
    const babylonWebGL = engine?.webGLVersion || 'unknown';
    const babylonIsWebGL2 = (engine as any)?.isWebGL2 || false;

    logger.info('WebGL check complete', {
      hasWebGL1,
      hasWebGL2,
      babylonWebGL,
      babylonIsWebGL2,
    });

    return `
WebGL Support Check:

BROWSER:
  WebGL1: ${hasWebGL1 ? 'YES' : 'NO'}
  WebGL2: ${hasWebGL2 ? 'YES' : 'NO'}
  Version: ${webglVersion}

HARDWARE:
  Vendor: ${vendor}
  Renderer: ${renderer}

BABYLONJS ENGINE:
  WebGL Version: ${babylonWebGL}
  isWebGL2: ${babylonIsWebGL2}

${!hasWebGL2 ? '\n⚠️  WARNING: WebGL2 NOT available in browser!\nThis severely limits shadow quality and features.\nShadows might not work properly with WebGL1.\n' : '✓ WebGL2 is available!'}
    `.trim();
  }
}
