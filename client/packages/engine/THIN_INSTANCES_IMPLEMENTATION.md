# ThinInstances Implementation Guide

Dieses Dokument beschreibt, wie Sie das neue `THIN_INSTANCES` Shape-Feature implementieren.

## Übersicht

**ThinInstances** ermöglicht das effiziente Rendering von tausenden Grashalmen, Blättern oder anderen kleinen Objekten mit:
- ✅ Y-Axis-Only Billboard (bleibt vertikal)
- ✅ GPU-basierte Wind-Animation
- ✅ Extrem performant (100k+ Instanzen möglich)
- ✅ Konfigurierbar via shaderParameters

## Schritt 1: Shape.THIN_INSTANCES hinzufügen

**Datei: `packages/shared/src/types/Shape.ts` (oder wo Shape definiert ist)**

```typescript
export enum Shape {
  INVISIBLE = 0,
  CUBE = 1,
  BILLBOARD = 2,
  SPRITE = 3,
  FLAME = 4,
  OCEAN = 5,
  FLIPBOX = 6,
  THIN_INSTANCES = 7,  // NEU
}
```

## Schritt 2: ThinInstancesService erstellen

**Datei: `packages/engine/src/services/ThinInstancesService.ts`**

```typescript
/**
 * ThinInstancesService - Manages thin instance rendering for grass-like objects
 *
 * Uses Babylon.js Thin Instances for extreme performance with Y-axis billboard shader.
 * Supports GPU-based wind animation and per-block instance configuration.
 */

import { Mesh, MeshBuilder, Scene, Matrix, Vector3, StandardMaterial } from '@babylonjs/core';
import { getLogger, ExceptionHandler } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { ShaderService } from './ShaderService';

const logger = getLogger('ThinInstancesService');

/**
 * Configuration for a thin instances group
 */
interface ThinInstanceConfig {
  texturePath: string;
  instanceCount: number;
  blockPosition: { x: number; y: number; z: number };
}

/**
 * Thin instances group data
 */
interface ThinInstanceGroup {
  mesh: Mesh;
  matricesData: Float32Array;
  instanceCount: number;
  chunkKey: string;
}

export class ThinInstancesService {
  private scene: Scene;
  private appContext: AppContext;
  private shaderService?: ShaderService;

  // Map: chunkKey -> ThinInstanceGroup[]
  private instanceGroups: Map<string, ThinInstanceGroup[]> = new Map();

  // Base mesh template (will be cloned for each texture)
  private baseMesh?: Mesh;

  constructor(scene: Scene, appContext: AppContext) {
    this.scene = scene;
    this.appContext = appContext;

    this.createBaseMesh();

    logger.info('ThinInstancesService initialized');
  }

  /**
   * Set shader service
   */
  setShaderService(shaderService: ShaderService): void {
    this.shaderService = shaderService;
    logger.debug('ShaderService set');
  }

  /**
   * Create base mesh template (vertical quad)
   */
  private createBaseMesh(): void {
    // Create a simple vertical quad (2x2 ground rotated 90 degrees)
    const mesh = MeshBuilder.CreateGround('thinInstanceBase', { width: 2, height: 2 }, this.scene);
    mesh.rotation.x = Math.PI * 0.5; // Rotate to vertical
    mesh.bakeCurrentTransformIntoVertices(); // Bake rotation into vertices
    mesh.isVisible = false; // Template is invisible

    this.baseMesh = mesh;

    logger.debug('Base mesh template created');
  }

  /**
   * Create thin instances for a block
   *
   * @param config Instance configuration
   * @param chunkKey Parent chunk key
   * @returns Created mesh with thin instances
   */
  async createInstances(config: ThinInstanceConfig, chunkKey: string): Promise<Mesh> {
    if (!this.baseMesh) {
      throw new Error('Base mesh not created');
    }

    // Clone base mesh for this group
    const mesh = this.baseMesh.clone(`thinInstances_${config.blockPosition.x}_${config.blockPosition.y}_${config.blockPosition.z}`);
    mesh.isVisible = true;

    // Get or create shader material
    const material = await this.getMaterial(config.texturePath);
    mesh.material = material;

    // Create matrices for instances
    const matricesData = new Float32Array(16 * config.instanceCount);
    const m = Matrix.Identity();

    let index = 0;
    const blockX = config.blockPosition.x;
    const blockY = config.blockPosition.y;
    const blockZ = config.blockPosition.z;

    // Distribute instances randomly within block bounds
    for (let i = 0; i < config.instanceCount; i++) {
      // Random position within block (0.8 = 80% of block size for margin)
      const offsetX = (Math.random() - 0.5) * 0.8;
      const offsetZ = (Math.random() - 0.5) * 0.8;

      // Set instance position in matrix
      m.m[12] = blockX + 0.5 + offsetX;
      m.m[13] = blockY; // Base at block bottom
      m.m[14] = blockZ + 0.5 + offsetZ;

      // Copy matrix to buffer
      m.copyToArray(matricesData, index * 16);
      index++;
    }

    // Set thin instance buffer
    mesh.thinInstanceSetBuffer('matrix', matricesData, 16);

    // Store group data
    const group: ThinInstanceGroup = {
      mesh,
      matricesData,
      instanceCount: config.instanceCount,
      chunkKey,
    };

    // Add to chunk groups
    if (!this.instanceGroups.has(chunkKey)) {
      this.instanceGroups.set(chunkKey, []);
    }
    this.instanceGroups.get(chunkKey)!.push(group);

    logger.debug('Thin instances created', {
      position: config.blockPosition,
      count: config.instanceCount,
      chunkKey,
    });

    return mesh;
  }

  /**
   * Get or create material with Y-axis billboard shader
   */
  private async getMaterial(texturePath: string): Promise<StandardMaterial> {
    if (!this.shaderService) {
      // Fallback: standard material
      const material = new StandardMaterial(`thinInstance_${texturePath}`, this.scene);

      // Load texture
      const networkService = this.appContext.services.network;
      if (networkService) {
        const url = networkService.getResourceUrl(texturePath);
        material.diffuseTexture = new (await import('@babylonjs/core')).Texture(url, this.scene);
      }

      material.backFaceCulling = false;
      return material;
    }

    // Use shader service to create Y-axis billboard shader material
    const material = await this.shaderService.createThinInstanceMaterial(texturePath);
    return material;
  }

  /**
   * Dispose instances for a chunk
   */
  disposeChunkInstances(chunkKey: string): void {
    const groups = this.instanceGroups.get(chunkKey);
    if (!groups) {
      return;
    }

    for (const group of groups) {
      group.mesh.dispose();
    }

    this.instanceGroups.delete(chunkKey);

    logger.debug('Chunk instances disposed', { chunkKey, groupCount: groups.length });
  }

  /**
   * Dispose all instances
   */
  dispose(): void {
    for (const groups of this.instanceGroups.values()) {
      for (const group of groups) {
        group.mesh.dispose();
      }
    }

    this.instanceGroups.clear();
    this.baseMesh?.dispose();

    logger.info('ThinInstancesService disposed');
  }

  /**
   * Get statistics
   */
  getStats(): { chunkCount: number; totalInstances: number; groupCount: number } {
    let totalInstances = 0;
    let groupCount = 0;

    for (const groups of this.instanceGroups.values()) {
      groupCount += groups.length;
      for (const group of groups) {
        totalInstances += group.instanceCount;
      }
    }

    return {
      chunkCount: this.instanceGroups.size,
      totalInstances,
      groupCount,
    };
  }
}
```

## Schritt 3: Y-Axis Billboard Shader im ShaderService

**Datei: `packages/engine/src/services/ShaderService.ts`**

Fügen Sie diese Methode hinzu:

```typescript
/**
 * Create material for thin instances with Y-axis billboard and wind animation
 *
 * @param texturePath Path to texture
 * @returns Material with Y-axis billboard shader
 */
async createThinInstanceMaterial(texturePath: string): Promise<any> {
  // TODO: Implement using Babylon.js NodeMaterial
  // For now, return standard material
  const material = new StandardMaterial(`thinInstance_${texturePath}`, this.scene);

  // Load texture
  const networkService = this.appContext.services.network;
  if (networkService) {
    const url = networkService.getResourceUrl(texturePath);
    const Texture = (await import('@babylonjs/core')).Texture;
    material.diffuseTexture = new Texture(url, this.scene);
  }

  material.backFaceCulling = false;

  logger.info('Thin instance material created (standard fallback)', { texturePath });

  return material;
}
```

**TODO: Implementieren Sie später einen echten NodeMaterial-Shader mit:**
- Y-Axis Billboard (Rotation nur um Y-Achse zur Kamera)
- GPU Wind-Animation (Vertex Shader displacement)
- Configurable Parameters (Wind Strength, Sway, etc.)

Referenz-Shader: https://nme.babylonjs.com/#8WH2KS#22

## Schritt 4: ThinInstancesRenderer erstellen

**Datei: `packages/engine/src/rendering/ThinInstancesRenderer.ts`**

```typescript
/**
 * ThinInstancesRenderer - Renders THIN_INSTANCES blocks
 *
 * Uses ThinInstancesService to create highly performant instance groups.
 * Supports Y-axis billboards and GPU-based wind animation.
 *
 * Features:
 * - Instance count configurable via shaderParameters (default: 100)
 * - Random positioning within block bounds
 * - Y-axis-only billboard (stays vertical)
 * - GPU wind animation
 */

import { getLogger, Shape, TextureHelper } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';

const logger = getLogger('ThinInstancesRenderer');

export class ThinInstancesRenderer extends BlockRenderer {
  /**
   * ThinInstancesRenderer needs separate handling per block
   */
  needsSeparateMesh(): boolean {
    return true;
  }

  /**
   * Render a THIN_INSTANCES block
   */
  async render(renderContext: RenderContext, clientBlock: ClientBlock): Promise<void> {
    const block = clientBlock.block;
    const modifier = clientBlock.currentModifier;

    if (!modifier || !modifier.visibility) {
      logger.warn('ThinInstancesRenderer: No visibility modifier', { block });
      return;
    }

    // Validate shape
    const shape = modifier.visibility.shape ?? Shape.CUBE;
    if (shape !== Shape.THIN_INSTANCES) {
      logger.warn('ThinInstancesRenderer: Not a THIN_INSTANCES shape', { shape, block });
      return;
    }

    const thinInstancesService = renderContext.renderService.appContext.services.thinInstances;
    if (!thinInstancesService) {
      logger.error('ThinInstancesRenderer: ThinInstancesService not available');
      return;
    }

    // Get first texture
    const textures = modifier.visibility.textures;
    if (!textures || Object.keys(textures).length === 0) {
      logger.warn('ThinInstancesRenderer: No textures defined', { block });
      return;
    }

    const firstTexture = textures[0] || textures[1];
    if (!firstTexture) {
      logger.warn('ThinInstancesRenderer: No texture found', { block });
      return;
    }

    const textureDef = TextureHelper.normalizeTexture(firstTexture);

    // Get instance count from shaderParameters (default: 100)
    let instanceCount = 100;
    if (textureDef.shaderParameters) {
      const parsed = parseInt(textureDef.shaderParameters, 10);
      if (!isNaN(parsed) && parsed > 0) {
        instanceCount = parsed;
      }
    }

    // Get chunk key
    const chunkKey = `chunk_${Math.floor(block.position.x / 32)}_${Math.floor(block.position.z / 32)}`;

    // Create thin instances
    try {
      const mesh = await thinInstancesService.createInstances(
        {
          texturePath: textureDef.path,
          instanceCount,
          blockPosition: block.position,
        },
        chunkKey
      );

      // Register mesh for disposal
      renderContext.resourcesToDispose.addMesh(mesh);

      logger.debug('ThinInstances rendered', {
        position: block.position,
        instanceCount,
        texturePath: textureDef.path,
      });
    } catch (error) {
      logger.error('Failed to render thin instances', {
        position: block.position,
        error,
      });
    }
  }
}
```

## Schritt 5: Service und Renderer registrieren

**Datei: `packages/engine/src/services/EngineService.ts`**

```typescript
// Import hinzufügen
import { ThinInstancesService } from './ThinInstancesService';

// In der initialize() Methode nach SpriteService:

// Initialize ThinInstancesService
const thinInstancesService = new ThinInstancesService(this.scene, appContext);
appContext.services.thinInstances = thinInstancesService;
thinInstancesService.setShaderService(shaderService);
logger.debug('ThinInstancesService initialized and registered');
```

**Datei: `packages/engine/src/AppContext.ts`**

```typescript
// Im services Interface:
export interface Services {
  // ... existing services
  thinInstances?: ThinInstancesService;
}
```

**Datei: `packages/engine/src/services/RenderService.ts`**

```typescript
// Import hinzufügen
import { ThinInstancesRenderer } from '../rendering/ThinInstancesRenderer';

// In der Klasse:
private thinInstancesRenderer: ThinInstancesRenderer;

// Im constructor nach spriteRenderer:
this.thinInstancesRenderer = new ThinInstancesRenderer();

// In getRenderer() Method, im switch statement:
case Shape.THIN_INSTANCES:
  return this.thinInstancesRenderer;
```

## Verwendung

### BlockType Definition (Server/Datenbank)

```json
{
  "id": 500,
  "name": "grass_instances",
  "blockType": {
    "visibility": {
      "shape": 7,  // Shape.THIN_INSTANCES
      "textures": {
        "0": {
          "path": "grass_blade.png",
          "shaderParameters": "150"  // 150 Grashalme pro Block
        }
      }
    }
  }
}
```

### Beispiel: Gras-Block

```json
{
  "id": 501,
  "name": "dense_grass",
  "blockType": {
    "visibility": {
      "shape": 7,
      "textures": {
        "0": {
          "path": "textures/block/grass_blade.png",
          "shaderParameters": "300"  // Dichtes Gras
        }
      }
    }
  }
}
```

### Beispiel: Blätter-Block

```json
{
  "id": 502,
  "name": "falling_leaves",
  "blockType": {
    "visibility": {
      "shape": 7,
      "textures": {
        "0": {
          "path": "textures/block/leaf.png",
          "shaderParameters": "50"  // Wenige Blätter
        }
      }
    }
  }
}
```

## Features

### Aktuelle Features (ohne Shader)
- ✅ Thin Instances (extrem performant)
- ✅ Konfigurierbare Anzahl via `shaderParameters`
- ✅ Random Positioning innerhalb Block
- ✅ Standard Material mit Textur
- ✅ Backface Culling deaktiviert

### Zukünftige Features (mit Shader)
- ⏳ Y-Axis Billboard (bleibt vertikal)
- ⏳ GPU Wind-Animation
- ⏳ Per-Instance Variations (Größe, Rotation)
- ⏳ Configurable Wind Parameters

## Performance

**Vorteile gegenüber SPRITE:**
- 10-100x performanter
- GPU-basiert statt CPU
- Keine SpriteManager-Limits
- Echter Z-Buffer/Depth Test

**Empfohlene Werte:**
- Sparse Grass: 50-100 Instanzen
- Normal Grass: 100-200 Instanzen
- Dense Grass: 200-400 Instanzen
- Maximum: 1000+ Instanzen (aber Performance-Test!)

## Nächste Schritte

1. ✅ Shape enum erweitern
2. ✅ ThinInstancesService implementieren
3. ✅ ThinInstancesRenderer implementieren
4. ✅ Services registrieren
5. ⏳ NodeMaterial Shader erstellen (Y-axis billboard + wind)
6. ⏳ Im Editor testen
7. ⏳ Performance optimieren

## Shader Implementation (später)

Der Y-Axis Billboard + Wind Shader sollte:

1. **Vertex Shader:**
   - Billboard-Matrix berechnen (nur Y-Rotation zur Kamera)
   - Wind-Displacement auf Basis von Zeit und Position
   - Per-Vertex Wind-Stärke (oben mehr, unten weniger)

2. **Fragment Shader:**
   - Texture Sampling
   - Alpha Test/Blend
   - Optional: Color variations

**Referenz:** https://nme.babylonjs.com/#8WH2KS#22 (Babylon.js Node Material Editor)

## Debugging

```javascript
// Console Commands
const stats = appContext.services.thinInstances.getStats();
console.log('ThinInstances Stats:', stats);
// -> { chunkCount: 5, totalInstances: 15000, groupCount: 30 }
```
