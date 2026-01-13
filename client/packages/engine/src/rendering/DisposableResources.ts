/**
 * DisposableResources - Manages disposable resources for chunk lifecycle
 *
 * Tracks all disposable resources (meshes, sprites, etc.) created during chunk rendering.
 * When the chunk is unloaded, all resources are automatically disposed.
 *
 * This provides clean lifecycle management and prevents memory leaks.
 */

import type { Mesh, Sprite, IDisposable } from '@babylonjs/core';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('DisposableResources');

/**
 * DisposableResources - Tracks and disposes rendering resources
 *
 * Features:
 * - Tracks meshes, sprites, and other disposable objects
 * - Automatic disposal when chunk is unloaded
 * - Extensible for future resource types
 */
export class DisposableResources {
  private resources: IDisposable[] = [];
  private namedResources: Map<string, IDisposable> = new Map();

  /**
   * Get or create a named resource
   *
   * If resource with given name exists, returns it.
   * Otherwise, creates resource using factory function and stores it.
   * Useful for sharing resources across multiple blocks (e.g., ocean surfaces).
   *
   * @param name Unique name for the resource
   * @param factory Factory function to create resource if not exists
   * @returns Existing or newly created resource
   */
  getOrCreate<T extends IDisposable>(name: string, factory: () => T): T {
    if (!this.namedResources.has(name)) {
      const resource = factory();
      this.namedResources.set(name, resource);
      this.resources.push(resource); // Also track for disposal
      logger.debug('Named resource created', { name });
    }
    return this.namedResources.get(name) as T;
  }

  /**
   * Get or create a named mesh (convenience method)
   *
   * @param name Unique name for the mesh
   * @param factory Factory function to create mesh if not exists
   * @returns Existing or newly created mesh
   */
  getOrCreateMesh(name: string, factory: () => Mesh): Mesh {
    return this.getOrCreate(name, factory);
  }

  /**
   * Add a disposable resource to be disposed later
   *
   * @param resource Disposable resource to track
   */
  add(resource: IDisposable): void {
    this.resources.push(resource);
  }

  /**
   * Add a mesh to be disposed later (convenience method)
   *
   * @param mesh Mesh to track
   */
  addMesh(mesh: Mesh): void {
    this.add(mesh);
  }

  /**
   * Add a sprite to be disposed later (convenience method)
   *
   * @param sprite Sprite to track
   */
  addSprite(sprite: Sprite): void {
    this.add(sprite);
  }

  /**
   * Get statistics about tracked resources
   */
  getStats(): { total: number; named: number } {
    return {
      total: this.resources.length,
      named: this.namedResources.size,
    };
  }

  /**
   * Dispose all tracked resources
   *
   * Disposes all resources, then clears the tracking arrays.
   */
  dispose(): void {
    // Dispose all resources
    for (const resource of this.resources) {
      try {
        resource.dispose();
      } catch (error) {
        logger.warn('Failed to dispose resource', { error });
      }
    }

    const stats = this.getStats();
    logger.debug('Resources disposed', stats);

    // Clear arrays and maps
    this.resources = [];
    this.namedResources.clear();
  }
}
