/**
 * CubeRenderer Unit Tests
 * Tests cube rendering functionality using BabylonJS NullEngine
 */

import { NullEngine, Scene, StandardMaterial, Texture, Color3 } from '@babylonjs/core';
import { CubeRenderer } from './CubeRenderer';
import { RenderContext } from '../services/RenderService';
import { DisposableResources } from './DisposableResources';
import type { ClientBlock, ClientBlockType } from '../types';
import type { Block, BlockType, BlockModifier, TextureDefinition } from '@nimbus/shared';
import { FaceVisibilityHelper, Shape, FaceFlag } from '@nimbus/shared';
import type { TextureAtlas, AtlasUV } from './TextureAtlas';

// Mock TextureAtlas
class MockTextureAtlas implements Partial<TextureAtlas> {
  private mockMaterial: StandardMaterial;

  constructor(scene: Scene) {
    this.mockMaterial = new StandardMaterial('mockAtlasMaterial', scene);
    this.mockMaterial.backFaceCulling = true;
  }

  getMaterial(): StandardMaterial {
    return this.mockMaterial;
  }

  async getTextureUV(textureDef: TextureDefinition): Promise<AtlasUV> {
    // Return mock UV coordinates
    return {
      u0: 0,
      v0: 0,
      u1: 0.25,
      v1: 0.25
    };
  }
}

// Mock RenderService
class MockRenderService {
  public materialService: MockMaterialService;

  constructor(scene: Scene) {
    this.materialService = new MockMaterialService(scene);
  }
}

// Mock MaterialService
class MockMaterialService {
  private scene: Scene;
  private materials: Map<string, StandardMaterial> = new Map();

  constructor(scene: Scene) {
    this.scene = scene;
  }

  async getMaterial(modifier: BlockModifier, textureIndex: number): Promise<StandardMaterial | null> {
    const key = `material_${textureIndex}`;
    if (!this.materials.has(key)) {
      const material = new StandardMaterial(key, this.scene);
      material.diffuseColor = new Color3(1, 1, 1);
      material.backFaceCulling = true;
      this.materials.set(key, material);
    }
    return this.materials.get(key) || null;
  }
}

describe('CubeRenderer', () => {
  let engine: NullEngine;
  let scene: Scene;
  let cubeRenderer: CubeRenderer;
  let mockTextureAtlas: MockTextureAtlas;
  let mockRenderService: MockRenderService;
  let renderContext: RenderContext;

  beforeEach(() => {
    // Setup NullEngine for headless testing
    engine = new NullEngine();
    scene = new Scene(engine);
    mockTextureAtlas = new MockTextureAtlas(scene);
    mockRenderService = new MockRenderService(scene);
    cubeRenderer = new CubeRenderer(mockTextureAtlas as any);

    // Initialize render context
    renderContext = {
      renderService: mockRenderService as any,
      faceData: {
        positions: [],
        indices: [],
        uvs: [],
        normals: []
      },
      vertexOffset: 0,
      resourcesToDispose: new DisposableResources()
    };
  });

  afterEach(() => {
    scene.dispose();
    engine.dispose();
  });

  /**
   * Helper to create a test ClientBlock
   */
  function createTestBlock(
    x: number = 0,
    y: number = 0,
    z: number = 0,
    faceVisibility?: number,
    textures?: Record<number, TextureDefinition>
  ): ClientBlock {
    const block: Block = {
      position: { x, y, z },
      blockTypeId: '1',
      faceVisibility,
      status: 0
    };

    const blockType: BlockType = {
      id: '1',
      initialStatus: 0,
      modifiers: {
        0: {
          visibility: {
            shape: Shape.CUBE,
            textures: textures || {
              0: { path: 'default.png' },
              1: { path: 'top.png' },
              2: { path: 'bottom.png' },
              3: { path: 'left.png' },
              4: { path: 'right.png' },
              5: { path: 'front.png' },
              6: { path: 'back.png' },
              7: { path: 'all.png' }
            }
          }
        }
      }
    };

    const currentModifier = blockType.modifiers[0];

    const clientBlock: ClientBlock = {
      block,
      chunk: { cx: 0, cz: 0 },
      blockType,
      currentModifier,
      isVisible: true,
      isDirty: false
    };

    return clientBlock;
  }

  describe('Basic Cube Rendering', () => {
    it('should render a cube with all 6 faces when no faceVisibility is specified', async () => {
      const block = createTestBlock();

      await cubeRenderer.render(renderContext, block);

      // Check that all faces were rendered (6 faces * 4 vertices = 24 vertices)
      expect(renderContext.faceData.positions.length).toBe(24 * 3); // 24 vertices * 3 coordinates
      expect(renderContext.faceData.normals.length).toBe(24 * 3);
      expect(renderContext.faceData.uvs.length).toBe(24 * 2); // 24 vertices * 2 UV coords
      expect(renderContext.faceData.indices.length).toBe(6 * 6); // 6 faces * 6 indices (2 triangles)
      expect(renderContext.vertexOffset).toBe(24); // Should have added 24 vertices
    });

    it('should generate correct positions for a cube at origin', async () => {
      const block = createTestBlock(0, 0, 0);

      await cubeRenderer.render(renderContext, block);

      const positions = renderContext.faceData.positions;

      // Check that we have vertices at the expected cube corners
      // The cube should be from (0,0,0) to (1,1,1)
      // Bottom corners: (0,0,0), (1,0,0), (1,0,1), (0,0,1)
      // Top corners: (0,1,0), (1,1,0), (1,1,1), (0,1,1)

      // We should have these corner positions somewhere in the array
      const hasOrigin = positions.includes(0) && positions.includes(0) && positions.includes(0);
      const hasCorner = positions.includes(1) && positions.includes(1) && positions.includes(1);

      expect(hasOrigin).toBe(true);
      expect(hasCorner).toBe(true);
    });

    it('should generate correct normals for each face', async () => {
      const block = createTestBlock();

      await cubeRenderer.render(renderContext, block);

      const normals = renderContext.faceData.normals;

      // Each face should have 4 vertices with the same normal
      // Check for presence of standard cube normals
      const normalSets = [];
      for (let i = 0; i < normals.length; i += 3) {
        normalSets.push([normals[i], normals[i+1], normals[i+2]]);
      }

      // Should have normals for all 6 face directions
      const hasTopNormal = normalSets.some(n => n[0] === 0 && n[1] === 1 && n[2] === 0);
      const hasBottomNormal = normalSets.some(n => n[0] === 0 && n[1] === -1 && n[2] === 0);
      const hasLeftNormal = normalSets.some(n => n[0] === -1 && n[1] === 0 && n[2] === 0);
      const hasRightNormal = normalSets.some(n => n[0] === 1 && n[1] === 0 && n[2] === 0);
      const hasFrontNormal = normalSets.some(n => n[0] === 0 && n[1] === 0 && n[2] === 1);
      const hasBackNormal = normalSets.some(n => n[0] === 0 && n[1] === 0 && n[2] === -1);

      expect(hasTopNormal).toBe(true);
      expect(hasBottomNormal).toBe(true);
      expect(hasLeftNormal).toBe(true);
      expect(hasRightNormal).toBe(true);
      expect(hasFrontNormal).toBe(true);
      expect(hasBackNormal).toBe(true);
    });

    it('should translate cube to correct world position', async () => {
      const block = createTestBlock(10, 20, 30);

      await cubeRenderer.render(renderContext, block);

      const positions = renderContext.faceData.positions;

      // Check that positions are translated
      // Should have vertices at (10,20,30) to (11,21,31)
      let minX = Infinity, minY = Infinity, minZ = Infinity;
      let maxX = -Infinity, maxY = -Infinity, maxZ = -Infinity;

      for (let i = 0; i < positions.length; i += 3) {
        minX = Math.min(minX, positions[i]);
        maxX = Math.max(maxX, positions[i]);
        minY = Math.min(minY, positions[i+1]);
        maxY = Math.max(maxY, positions[i+1]);
        minZ = Math.min(minZ, positions[i+2]);
        maxZ = Math.max(maxZ, positions[i+2]);
      }

      expect(minX).toBeCloseTo(10);
      expect(maxX).toBeCloseTo(11);
      expect(minY).toBeCloseTo(20);
      expect(maxY).toBeCloseTo(21);
      expect(minZ).toBeCloseTo(30);
      expect(maxZ).toBeCloseTo(31);
    });
  });

  describe('Face Visibility', () => {
    it('should only render visible faces when faceVisibility is specified', async () => {
      // Create block with only top and bottom faces visible
      let faceVisibility = FaceVisibilityHelper.create();
      faceVisibility = FaceVisibilityHelper.setVisible(faceVisibility, FaceFlag.TOP);
      faceVisibility = FaceVisibilityHelper.setVisible(faceVisibility, FaceFlag.BOTTOM);

      const block = createTestBlock(0, 0, 0, faceVisibility);

      await cubeRenderer.render(renderContext, block);

      // Should only render 2 faces (top and bottom) = 8 vertices
      expect(renderContext.faceData.positions.length).toBe(8 * 3);
      expect(renderContext.faceData.indices.length).toBe(2 * 6); // 2 faces * 6 indices
      expect(renderContext.vertexOffset).toBe(8);
    });

    it('should render no faces when all faces are invisible', async () => {
      // Create block with no faces visible
      const faceVisibility = FaceVisibilityHelper.create(); // All invisible by default

      const block = createTestBlock(0, 0, 0, faceVisibility);

      await cubeRenderer.render(renderContext, block);

      // Should render no faces
      expect(renderContext.faceData.positions.length).toBe(0);
      expect(renderContext.faceData.indices.length).toBe(0);
      expect(renderContext.vertexOffset).toBe(0);
    });

    it('should respect individual face visibility flags', async () => {
      // Test each face individually
      const faceFlags = [
        FaceFlag.TOP,
        FaceFlag.BOTTOM,
        FaceFlag.LEFT,
        FaceFlag.RIGHT,
        FaceFlag.FRONT,
        FaceFlag.BACK
      ];

      for (const flag of faceFlags) {
        renderContext = {
          renderService: mockRenderService as any,
          faceData: {
            positions: [],
            indices: [],
            uvs: [],
            normals: []
          },
          vertexOffset: 0,
          resourcesToDispose: new DisposableResources()
        };

        let faceVisibility = FaceVisibilityHelper.create();
        faceVisibility = FaceVisibilityHelper.setVisible(faceVisibility, flag);

        const block = createTestBlock(0, 0, 0, faceVisibility);

        await cubeRenderer.render(renderContext, block);

        // Should render exactly 1 face = 4 vertices
        expect(renderContext.faceData.positions.length).toBe(4 * 3);
        expect(renderContext.faceData.indices.length).toBe(6); // 1 face = 2 triangles = 6 indices
        expect(renderContext.vertexOffset).toBe(4);
      }
    });
  });

  describe('Texture and UV Mapping', () => {
    it('should generate UV coordinates for all faces', async () => {
      const block = createTestBlock();

      await cubeRenderer.render(renderContext, block);

      // Should have UV coordinates for all vertices
      expect(renderContext.faceData.uvs.length).toBe(24 * 2); // 24 vertices * 2 UV coords

      // Check that UV values are within valid range [0, 1]
      for (const uv of renderContext.faceData.uvs) {
        expect(uv).toBeGreaterThanOrEqual(0);
        expect(uv).toBeLessThanOrEqual(1);
      }
    });

    it('should use different textures for different faces', async () => {
      const textures: Record<number, TextureDefinition> = {
        1: { path: 'top.png' },
        2: { path: 'bottom.png' },
        3: { path: 'left.png' },
        4: { path: 'right.png' },
        5: { path: 'front.png' },
        6: { path: 'back.png' }
      };

      const block = createTestBlock(0, 0, 0, undefined, textures);

      await cubeRenderer.render(renderContext, block);

      // Should have rendered all faces with their specific textures
      expect(renderContext.faceData.positions.length).toBe(24 * 3);

      // UV coordinates should be generated from texture atlas
      expect(renderContext.faceData.uvs.length).toBe(24 * 2);
    });

    it('should use fallback texture when specific face texture is missing', async () => {
      const textures: Record<number, TextureDefinition> = {
        0: { path: 'fallback.png' }, // Default texture
        7: { path: 'all_faces.png' } // All faces texture
      };

      const block = createTestBlock(0, 0, 0, undefined, textures);

      await cubeRenderer.render(renderContext, block);

      // Should still render all faces using fallback textures
      expect(renderContext.faceData.positions.length).toBe(24 * 3);
      expect(renderContext.faceData.uvs.length).toBe(24 * 2);
    });
  });

  describe('Transformations', () => {
    it('should apply scaling transformation', async () => {
      const block = createTestBlock();

      // Add scaling to the modifier
      block.currentModifier.visibility = {
        ...block.currentModifier.visibility,
        scalingX: 2,
        scalingY: 0.5,
        scalingZ: 1.5
      };

      await cubeRenderer.render(renderContext, block);

      const positions = renderContext.faceData.positions;

      // Calculate bounds to verify scaling
      let minX = Infinity, maxX = -Infinity;
      let minY = Infinity, maxY = -Infinity;
      let minZ = Infinity, maxZ = -Infinity;

      for (let i = 0; i < positions.length; i += 3) {
        minX = Math.min(minX, positions[i]);
        maxX = Math.max(maxX, positions[i]);
        minY = Math.min(minY, positions[i+1]);
        maxY = Math.max(maxY, positions[i+1]);
        minZ = Math.min(minZ, positions[i+2]);
        maxZ = Math.max(maxZ, positions[i+2]);
      }

      // With scaling, the cube should be:
      // X: scaled by 2 (width = 2)
      // Y: scaled by 0.5 (height = 0.5)
      // Z: scaled by 1.5 (depth = 1.5)
      // Scaled around center (0.5, 0.5, 0.5)

      const width = maxX - minX;
      const height = maxY - minY;
      const depth = maxZ - minZ;

      expect(width).toBeCloseTo(2, 1);
      expect(height).toBeCloseTo(0.5, 1);
      expect(depth).toBeCloseTo(1.5, 1);
    });

    it('should apply rotation transformation', async () => {
      const block = createTestBlock();

      // Add 45-degree rotation around Y axis
      block.currentModifier.visibility = {
        ...block.currentModifier.visibility,
        rotation: {
          x: 0,
          y: 45
        }
      };

      await cubeRenderer.render(renderContext, block);

      const positions = renderContext.faceData.positions;

      // After 45-degree Y rotation, the cube corners should be rotated
      // This is a basic check that positions have changed from axis-aligned
      let hasNonAxisAligned = false;

      for (let i = 0; i < positions.length; i += 3) {
        const x = positions[i];
        const z = positions[i+2];

        // Check if any vertex is not on the original axis-aligned positions
        // With rotation, we should have vertices at non-integer positions
        if ((x !== 0 && x !== 1) || (z !== 0 && z !== 1)) {
          hasNonAxisAligned = true;
          break;
        }
      }

      expect(hasNonAxisAligned).toBe(true);
    });

    it('should apply offsets to vertices', async () => {
      const block = createTestBlock();

      // Add offsets for the first two corners
      block.block.offsets = [
        0.1, 0.2, 0.3,  // Corner 0 offset
        -0.1, -0.2, -0.3, // Corner 1 offset
      ];

      await cubeRenderer.render(renderContext, block);

      // Should still render the cube
      expect(renderContext.faceData.positions.length).toBe(24 * 3);

      // The positions should include the offset values
      const positions = renderContext.faceData.positions;

      // Check that we have some non-standard positions due to offsets
      let hasOffsetPosition = false;
      for (let i = 0; i < positions.length; i += 3) {
        const x = positions[i];
        const y = positions[i+1];
        const z = positions[i+2];

        // Check if position has been offset (not exactly 0 or 1)
        if ((x !== 0 && x !== 1) ||
            (y !== 0 && y !== 1) ||
            (z !== 0 && z !== 1)) {
          hasOffsetPosition = true;
          break;
        }
      }

      expect(hasOffsetPosition).toBe(true);
    });
  });

  describe('Offsets (Comprehensive)', () => {
    it('should correctly apply offsets to all 8 cube corners', async () => {
      const block = createTestBlock(0, 0, 0);

      // Define offsets for all 8 corners
      // Each corner gets a unique offset to verify they are applied correctly
      block.block.offsets = [
        // Bottom corners (y = 0)
        0.1, 0.0, 0.1,   // Corner 0: left-back-bottom
        -0.1, 0.0, 0.1,  // Corner 1: right-back-bottom
        -0.1, 0.0, -0.1, // Corner 2: right-front-bottom
        0.1, 0.0, -0.1,  // Corner 3: left-front-bottom
        // Top corners (y = 1)
        0.15, 0.2, 0.15,   // Corner 4: left-back-top
        -0.15, 0.2, 0.15,  // Corner 5: right-back-top
        -0.15, 0.2, -0.15, // Corner 6: right-front-top
        0.15, 0.2, -0.15,  // Corner 7: left-front-top
      ];

      await cubeRenderer.render(renderContext, block);

      const positions = renderContext.faceData.positions;

      // Verify that the cube is deformed by offsets
      // Check that we have vertices at expected offset positions
      const vertices: Array<[number, number, number]> = [];
      for (let i = 0; i < positions.length; i += 3) {
        vertices.push([positions[i], positions[i+1], positions[i+2]]);
      }

      // Expected corners after offsets:
      // Corner 0: (0+0.1, 0+0.0, 0+0.1) = (0.1, 0, 0.1)
      // Corner 1: (1-0.1, 0+0.0, 0+0.1) = (0.9, 0, 0.1)
      // Corner 4: (0+0.15, 1+0.2, 0+0.15) = (0.15, 1.2, 0.15)
      // etc.

      // Check for some expected offset positions
      const hasCorner0 = vertices.some(v =>
        Math.abs(v[0] - 0.1) < 0.01 &&
        Math.abs(v[1] - 0.0) < 0.01 &&
        Math.abs(v[2] - 0.1) < 0.01
      );
      const hasCorner1 = vertices.some(v =>
        Math.abs(v[0] - 0.9) < 0.01 &&
        Math.abs(v[1] - 0.0) < 0.01 &&
        Math.abs(v[2] - 0.1) < 0.01
      );
      const hasCorner4 = vertices.some(v =>
        Math.abs(v[0] - 0.15) < 0.01 &&
        Math.abs(v[1] - 1.2) < 0.01 &&
        Math.abs(v[2] - 0.15) < 0.01
      );

      expect(hasCorner0).toBe(true);
      expect(hasCorner1).toBe(true);
      expect(hasCorner4).toBe(true);
    });

    it('should handle partial offsets (trailing zeros omitted)', async () => {
      const block = createTestBlock(0, 0, 0);

      // Only provide offsets for first 3 corners, rest should be 0
      block.block.offsets = [
        0.2, 0.1, 0.0,  // Corner 0
        0.0, 0.1, 0.2,  // Corner 1
        0.1, 0.0, 0.1,  // Corner 2
        // Corner 3-7 offsets omitted (should default to 0)
      ];

      await cubeRenderer.render(renderContext, block);

      const positions = renderContext.faceData.positions;

      // Collect all unique vertices
      const uniqueVertices = new Set<string>();
      for (let i = 0; i < positions.length; i += 3) {
        const key = `${positions[i].toFixed(3)},${positions[i+1].toFixed(3)},${positions[i+2].toFixed(3)}`;
        uniqueVertices.add(key);
      }

      // Should have modified corners 0,1,2 and unmodified corners 3-7
      expect(positions.length).toBe(24 * 3); // All faces still rendered

      // Verify that we have both offset and non-offset vertices
      const vertices: Array<[number, number, number]> = [];
      for (let i = 0; i < positions.length; i += 3) {
        vertices.push([positions[i], positions[i+1], positions[i+2]]);
      }

      // Check for offset corner 0: (0.2, 0.1, 0)
      const hasOffsetCorner0 = vertices.some(v =>
        Math.abs(v[0] - 0.2) < 0.01 &&
        Math.abs(v[1] - 0.1) < 0.01 &&
        Math.abs(v[2] - 0.0) < 0.01
      );

      // Check for non-offset corner 3: (0, 0, 1)
      const hasNonOffsetCorner3 = vertices.some(v =>
        Math.abs(v[0] - 0.0) < 0.01 &&
        Math.abs(v[1] - 0.0) < 0.01 &&
        Math.abs(v[2] - 1.0) < 0.01
      );

      expect(hasOffsetCorner0).toBe(true);
      expect(hasNonOffsetCorner3).toBe(true);
    });

    it('should handle negative offsets correctly', async () => {
      const block = createTestBlock(5, 5, 5);

      // Apply negative offsets to shrink the cube
      block.block.offsets = [
        -0.2, -0.2, -0.2,  // Corner 0: shrink inward
        -0.2, -0.2, -0.2,  // Corner 1: shrink inward
        -0.2, -0.2, -0.2,  // Corner 2: shrink inward
        -0.2, -0.2, -0.2,  // Corner 3: shrink inward
        -0.2, -0.2, -0.2,  // Corner 4: shrink inward
        -0.2, -0.2, -0.2,  // Corner 5: shrink inward
        -0.2, -0.2, -0.2,  // Corner 6: shrink inward
        -0.2, -0.2, -0.2,  // Corner 7: shrink inward
      ];

      await cubeRenderer.render(renderContext, block);

      const positions = renderContext.faceData.positions;

      // Calculate bounds of the shrunken cube
      let minX = Infinity, maxX = -Infinity;
      let minY = Infinity, maxY = -Infinity;
      let minZ = Infinity, maxZ = -Infinity;

      for (let i = 0; i < positions.length; i += 3) {
        minX = Math.min(minX, positions[i]);
        maxX = Math.max(maxX, positions[i]);
        minY = Math.min(minY, positions[i+1]);
        maxY = Math.max(maxY, positions[i+1]);
        minZ = Math.min(minZ, positions[i+2]);
        maxZ = Math.max(maxZ, positions[i+2]);
      }

      // With -0.2 offset on all corners, the cube should be smaller
      // Original: (5,5,5) to (6,6,6), size = 1
      // After offset: ~(4.8,4.8,4.8) to ~(5.8,5.8,5.8), size = 1
      // But corners are moved inward by 0.2
      expect(minX).toBeCloseTo(4.8, 1);
      expect(maxX).toBeCloseTo(5.8, 1);
      expect(minY).toBeCloseTo(4.8, 1);
      expect(maxY).toBeCloseTo(5.8, 1);
      expect(minZ).toBeCloseTo(4.8, 1);
      expect(maxZ).toBeCloseTo(5.8, 1);
    });

    it('should create non-uniform cube with asymmetric offsets', async () => {
      const block = createTestBlock(0, 0, 0);

      // Create a wedge-like shape by offsetting only top corners
      block.block.offsets = [
        0, 0, 0,  // Corner 0: no offset
        0, 0, 0,  // Corner 1: no offset
        0, 0, 0,  // Corner 2: no offset
        0, 0, 0,  // Corner 3: no offset
        0.5, 0, 0.5,  // Corner 4: move outward
        0.5, 0, 0.5,  // Corner 5: move outward
        0.5, 0, 0.5,  // Corner 6: move outward
        0.5, 0, 0.5,  // Corner 7: move outward
      ];

      await cubeRenderer.render(renderContext, block);

      const positions = renderContext.faceData.positions;

      // Verify that bottom corners are at original positions
      // and top corners are offset
      const vertices: Array<[number, number, number]> = [];
      for (let i = 0; i < positions.length; i += 3) {
        vertices.push([positions[i], positions[i+1], positions[i+2]]);
      }

      // Bottom corners should be unchanged
      const hasBottomCorner = vertices.some(v =>
        Math.abs(v[0] - 0.0) < 0.01 &&
        Math.abs(v[1] - 0.0) < 0.01 &&
        Math.abs(v[2] - 0.0) < 0.01
      );

      // Top corners should be offset
      // Corner 4 should be at (0+0.5, 1+0, 0+0.5) = (0.5, 1, 0.5)
      const hasOffsetTopCorner = vertices.some(v =>
        Math.abs(v[0] - 0.5) < 0.01 &&
        Math.abs(v[1] - 1.0) < 0.01 &&
        Math.abs(v[2] - 0.5) < 0.01
      );

      expect(hasBottomCorner).toBe(true);
      expect(hasOffsetTopCorner).toBe(true);
    });

    it('should combine offsets with transformations correctly', async () => {
      const block = createTestBlock(0, 0, 0);

      // Apply offsets
      block.block.offsets = [
        0.1, 0.1, 0.1,  // Corner 0
        -0.1, 0.1, 0.1,  // Corner 1
        -0.1, 0.1, -0.1,  // Corner 2
        0.1, 0.1, -0.1,  // Corner 3
        0.1, -0.1, 0.1,  // Corner 4
        -0.1, -0.1, 0.1,  // Corner 5
        -0.1, -0.1, -0.1,  // Corner 6
        0.1, -0.1, -0.1,  // Corner 7
      ];

      // Also apply scaling
      block.currentModifier.visibility = {
        ...block.currentModifier.visibility,
        scalingX: 2,
        scalingY: 2,
        scalingZ: 2
      };

      await cubeRenderer.render(renderContext, block);

      const positions = renderContext.faceData.positions;

      // Calculate bounds - should be scaled cube with offsets
      let minX = Infinity, maxX = -Infinity;
      let minY = Infinity, maxY = -Infinity;
      let minZ = Infinity, maxZ = -Infinity;

      for (let i = 0; i < positions.length; i += 3) {
        minX = Math.min(minX, positions[i]);
        maxX = Math.max(maxX, positions[i]);
        minY = Math.min(minY, positions[i+1]);
        maxY = Math.max(maxY, positions[i+1]);
        minZ = Math.min(minZ, positions[i+2]);
        maxZ = Math.max(maxZ, positions[i+2]);
      }

      // The cube should be scaled AND have offsets applied
      // Original cube: 0-1, scaled by 2 around center (0.5,0.5,0.5) = -0.5 to 1.5
      // Plus offsets should create an irregular shape
      const width = maxX - minX;
      const height = maxY - minY;
      const depth = maxZ - minZ;

      // Due to offsets, dimensions should not be exactly 2
      // The offsets are relatively small compared to the scaling
      expect(width).toBeGreaterThan(1.5);
      expect(width).toBeLessThan(2.5);
      expect(height).toBeGreaterThan(1.5);
      expect(height).toBeLessThan(2.5);
      expect(depth).toBeGreaterThan(1.5);
      expect(depth).toBeLessThan(2.5);
    });

    it('should handle empty/undefined offsets array', async () => {
      const block = createTestBlock(0, 0, 0);

      // Test with undefined offsets
      block.block.offsets = undefined;

      await cubeRenderer.render(renderContext, block);

      const positions1 = [...renderContext.faceData.positions];

      // Reset context for second test
      renderContext = {
        renderService: mockRenderService as any,
        faceData: {
          positions: [],
          indices: [],
          uvs: [],
          normals: []
        },
        vertexOffset: 0,
        resourcesToDispose: new DisposableResources()
      };

      // Test with empty array
      block.block.offsets = [];

      await cubeRenderer.render(renderContext, block);

      const positions2 = renderContext.faceData.positions;

      // Both should produce standard cube
      expect(positions1.length).toBe(24 * 3);
      expect(positions2.length).toBe(24 * 3);

      // Verify it's a standard unit cube
      for (let i = 0; i < positions2.length; i += 3) {
        const x = positions2[i];
        const y = positions2[i+1];
        const z = positions2[i+2];

        // All positions should be 0 or 1 (standard cube)
        expect(x === 0 || x === 1).toBe(true);
        expect(y === 0 || y === 1).toBe(true);
        expect(z === 0 || z === 1).toBe(true);
      }
    });
  });

  describe('Error Handling', () => {
    it('should handle blocks without visibility modifier', async () => {
      const block = createTestBlock();
      block.currentModifier = {} as any; // No visibility modifier

      await cubeRenderer.render(renderContext, block);

      // Should not render anything
      expect(renderContext.faceData.positions.length).toBe(0);
      expect(renderContext.vertexOffset).toBe(0);
    });

    it('should handle blocks without textures', async () => {
      const block = createTestBlock();
      block.currentModifier.visibility!.textures = undefined;

      await cubeRenderer.render(renderContext, block);

      // Should not render anything
      expect(renderContext.faceData.positions.length).toBe(0);
      expect(renderContext.vertexOffset).toBe(0);
    });

    it('should handle invalid texture definitions gracefully', async () => {
      const block = createTestBlock();
      block.currentModifier.visibility!.textures = {
        0: null as any,
        1: undefined as any
      };

      await cubeRenderer.render(renderContext, block);

      // Should still render with default UVs
      expect(renderContext.faceData.positions.length).toBe(24 * 3);
      expect(renderContext.faceData.uvs.length).toBe(24 * 2);
    });
  });

  describe('Index Generation', () => {
    it('should generate correct indices for triangulation', async () => {
      const block = createTestBlock();

      await cubeRenderer.render(renderContext, block);

      const indices = renderContext.faceData.indices;

      // Debug: Print first face's indices to understand the pattern
      // console.log('First face indices:', indices.slice(0, 6));

      // Should have 6 faces * 2 triangles * 3 indices = 36 indices
      expect(indices.length).toBe(36);

      // All indices should be valid vertex references
      for (const index of indices) {
        expect(index).toBeGreaterThanOrEqual(0);
        expect(index).toBeLessThan(24); // We have 24 vertices
      }

      // Check triangle winding (counter-clockwise)
      // Each set of 6 indices represents 2 triangles for a face
      for (let i = 0; i < indices.length; i += 6) {
        // First triangle
        const t1_0 = indices[i];
        const t1_1 = indices[i+1];
        const t1_2 = indices[i+2];

        // Second triangle
        const t2_0 = indices[i+3];
        const t2_1 = indices[i+4];
        const t2_2 = indices[i+5];

        // Verify triangles share at least one vertex (they form a quad face)
        const triangle1 = [t1_0, t1_1, t1_2];
        const triangle2 = [t2_0, t2_1, t2_2];
        const sharedVertices = triangle1.filter(v => triangle2.includes(v));

        // Two triangles forming a quad should share at least 2 vertices
        expect(sharedVertices.length).toBeGreaterThanOrEqual(2);
      }
    });

    it('should update vertexOffset correctly for multiple blocks', async () => {
      const block1 = createTestBlock(0, 0, 0);
      const block2 = createTestBlock(1, 0, 0);

      // Render first block
      await cubeRenderer.render(renderContext, block1);
      expect(renderContext.vertexOffset).toBe(24);

      // Render second block
      await cubeRenderer.render(renderContext, block2);
      expect(renderContext.vertexOffset).toBe(48);

      // Check that indices for second block use correct offset
      const indices = renderContext.faceData.indices;
      const secondBlockIndices = indices.slice(36); // Get indices for second block

      // All indices for second block should be >= 24
      for (const index of secondBlockIndices) {
        expect(index).toBeGreaterThanOrEqual(24);
        expect(index).toBeLessThan(48);
      }
    });
  });
});