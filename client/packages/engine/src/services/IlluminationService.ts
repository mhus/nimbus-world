import { GlowLayer } from '@babylonjs/core';
import { getLogger } from '@nimbus/shared';
import type { Scene } from '@babylonjs/core';
import type { Mesh } from '@babylonjs/core';
import type { AppContext } from '../AppContext';

const logger = getLogger('IlluminationService');

/**
 * IlluminationService - Manages block illumination and glow effects
 *
 * Features:
 * - Creates and manages scene-wide GlowLayer
 * - Tracks meshes with illumination modifiers
 * - Applies selective glow to illuminated blocks
 */
export class IlluminationService {
  private scene: Scene;
  private appContext: AppContext;
  private glowLayer?: GlowLayer;

  // Track illuminated meshes for cleanup
  private illuminatedMeshes: Set<Mesh> = new Set();

  constructor(scene: Scene, appContext: AppContext) {
    this.scene = scene;
    this.appContext = appContext;

    this.initialize();
  }

  /**
   * Initialize GlowLayer for scene
   */
  private initialize(): void {
    // Create GlowLayer with optimized settings
    this.glowLayer = new GlowLayer('illuminationGlow', this.scene, {
      mainTextureRatio: 0.5, // Half resolution for performance
      blurKernelSize: 32, // Glow spread amount
    });

    // Configure glow intensity
    this.glowLayer.intensity = 1.0;

    logger.info('IlluminationService initialized with GlowLayer');
  }

  /**
   * Register a mesh for illumination glow effect
   *
   * @param mesh Mesh to apply glow to
   * @param color Glow color (hex string)
   * @param intensity Glow intensity (0-1)
   */
  registerMesh(mesh: Mesh, color: string, intensity: number = 1.0): void {
    if (!this.glowLayer) return;

    // Add mesh to glow layer (selective rendering)
    this.glowLayer.addIncludedOnlyMesh(mesh);

    // Track for cleanup
    this.illuminatedMeshes.add(mesh);

    logger.info('Mesh registered for illumination', {
      meshName: mesh.name,
      color,
      intensity,
    });
  }

  /**
   * Unregister a mesh from illumination
   */
  unregisterMesh(mesh: Mesh): void {
    if (!this.glowLayer) return;

    this.glowLayer.removeIncludedOnlyMesh(mesh);
    this.illuminatedMeshes.delete(mesh);
  }

  /**
   * Dispose all resources
   */
  dispose(): void {
    logger.info('Disposing IlluminationService', {
      illuminatedMeshCount: this.illuminatedMeshes.size,
    });

    this.illuminatedMeshes.clear();

    if (this.glowLayer) {
      this.glowLayer.dispose();
      this.glowLayer = undefined;
    }
  }
}
