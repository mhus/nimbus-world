/**
 * ModelModifierService - Applies model modifiers (bone scale, material color) to entity instances
 *
 * Modifier mapping format: "<category>:<targetName>:<property>[;<category>:<targetName>:<property>]*"
 * Supported:
 *   bone:<boneName>:scale    - Scale a skeleton bone (uniform "1.5" or xyz "1.5,1.0,1.0")
 *   color:<materialName>:tint      - Multiply albedoColor by color ("#RRGGBB" or "r,g,b")
 *   color:<materialName>:baseColor - Replace albedoColor entirely
 */

import { Color3, Vector3 } from '@babylonjs/core';
import type { Skeleton, Bone } from '@babylonjs/core';
import type { PBRMaterial } from '@babylonjs/core';
import type { AbstractMesh } from '@babylonjs/core';
import type { InstantiatedEntries } from '@babylonjs/core';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('ModelModifierService');

interface TargetDescriptor {
  category: 'bone' | 'color';
  targetName: string;
  property: string;
}

export interface ModifierApplyResult {
  clonedMaterials: PBRMaterial[];
}

export class ModelModifierService {

  applyModifiers(
    entityId: string,
    instantiatedResult: InstantiatedEntries,
    modelModifierMapping: Map<string, string> | undefined,
    modelModifier: Record<string, string> | undefined
  ): ModifierApplyResult {
    const result: ModifierApplyResult = { clonedMaterials: [] };

    if (!modelModifierMapping || !modelModifier) {
      return result;
    }

    // Collect skeletons and meshes from instantiated result
    const skeletons = instantiatedResult.skeletons || [];
    const meshes: AbstractMesh[] = [];
    for (const node of instantiatedResult.rootNodes) {
      meshes.push(...node.getChildMeshes());
    }

    // Track cloned materials per original name to deduplicate
    const clonedMaterialMap = new Map<string, PBRMaterial>();

    for (const [modifierKey, value] of Object.entries(modelModifier)) {
      const mappingStr = modelModifierMapping instanceof Map
        ? modelModifierMapping.get(modifierKey)
        : (modelModifierMapping as Record<string, string>)[modifierKey];

      if (!mappingStr) {
        logger.warn('Modifier key has no mapping, skipping', { entityId, modifierKey });
        continue;
      }

      const descriptors = this.parseTargetDescriptors(mappingStr);
      for (const desc of descriptors) {
        try {
          if (desc.category === 'bone') {
            this.applyBoneModifier(entityId, skeletons, desc, value);
          } else if (desc.category === 'color') {
            this.applyColorModifier(entityId, meshes, desc, value, clonedMaterialMap);
          } else {
            logger.warn('Unknown modifier category', { entityId, category: desc.category });
          }
        } catch (e) {
          logger.warn('Failed to apply modifier', { entityId, modifierKey, descriptor: mappingStr, error: e });
        }
      }
    }

    result.clonedMaterials = Array.from(clonedMaterialMap.values());
    return result;
  }

  parseTargetDescriptors(mapping: string): TargetDescriptor[] {
    return mapping.split(';').map(part => {
      const segments = part.trim().split(':');
      if (segments.length !== 3) {
        logger.warn('Invalid descriptor format, expected category:targetName:property', { descriptor: part });
        return null;
      }
      return {
        category: segments[0] as 'bone' | 'color',
        targetName: segments[1],
        property: segments[2],
      };
    }).filter((d): d is TargetDescriptor => d !== null);
  }

  parseScaleValue(value: string): Vector3 {
    if (value.includes(',')) {
      const parts = value.split(',').map(s => parseFloat(s.trim()));
      if (parts.length === 3 && parts.every(p => !isNaN(p))) {
        return new Vector3(parts[0], parts[1], parts[2]);
      }
      logger.warn('Invalid xyz scale value, expected "x,y,z"', { value });
      return new Vector3(1, 1, 1);
    }
    const uniform = parseFloat(value);
    if (isNaN(uniform)) {
      logger.warn('Invalid scale value', { value });
      return new Vector3(1, 1, 1);
    }
    return new Vector3(uniform, uniform, uniform);
  }

  parseColorValue(value: string): Color3 {
    // Hex format: #RRGGBB
    if (value.startsWith('#')) {
      const hex = value.substring(1);
      if (hex.length === 6) {
        const r = parseInt(hex.substring(0, 2), 16) / 255;
        const g = parseInt(hex.substring(2, 4), 16) / 255;
        const b = parseInt(hex.substring(4, 6), 16) / 255;
        if (!isNaN(r) && !isNaN(g) && !isNaN(b)) {
          return new Color3(r, g, b);
        }
      }
      logger.warn('Invalid hex color value', { value });
      return new Color3(1, 1, 1);
    }
    // Float format: r,g,b (0-1)
    if (value.includes(',')) {
      const parts = value.split(',').map(s => parseFloat(s.trim()));
      if (parts.length === 3 && parts.every(p => !isNaN(p))) {
        return new Color3(parts[0], parts[1], parts[2]);
      }
    }
    logger.warn('Invalid color value, expected "#RRGGBB" or "r,g,b"', { value });
    return new Color3(1, 1, 1);
  }

  private applyBoneModifier(
    entityId: string,
    skeletons: Skeleton[],
    desc: TargetDescriptor,
    value: string
  ): void {
    if (desc.property !== 'scale') {
      logger.warn('Unsupported bone property', { entityId, property: desc.property });
      return;
    }
    if (skeletons.length === 0) {
      logger.warn('No skeleton available for bone modifier', { entityId, boneName: desc.targetName });
      return;
    }

    const scale = this.parseScaleValue(value);
    let found = false;

    for (const skeleton of skeletons) {
      const bone = skeleton.bones.find((b: Bone) => b.name === desc.targetName);
      if (bone) {
        bone.setScale(scale);
        found = true;
        break;
      }
    }

    if (!found) {
      const availableBones = skeletons.flatMap(s => s.bones.map((b: Bone) => b.name));
      logger.warn('Bone not found', { entityId, boneName: desc.targetName, available: availableBones });
    }
  }

  private applyColorModifier(
    entityId: string,
    meshes: AbstractMesh[],
    desc: TargetDescriptor,
    value: string,
    clonedMaterialMap: Map<string, PBRMaterial>
  ): void {
    if (desc.property !== 'tint' && desc.property !== 'baseColor') {
      logger.warn('Unsupported color property', { entityId, property: desc.property });
      return;
    }

    const color = this.parseColorValue(value);
    let found = false;

    for (const mesh of meshes) {
      const mat = mesh.material;
      if (!mat || !mat.name.includes(desc.targetName)) {
        continue;
      }

      // Check if we already cloned this material for this entity
      const originalName = mat.name;
      let clonedMat = clonedMaterialMap.get(originalName);

      if (!clonedMat) {
        // Clone the material for this entity instance
        clonedMat = (mat as PBRMaterial).clone(`${originalName}_${entityId}`) as PBRMaterial;
        clonedMaterialMap.set(originalName, clonedMat);
      }

      // Apply color modification
      if (desc.property === 'tint') {
        // Multiply existing albedoColor with tint color
        const existing = clonedMat.albedoColor || new Color3(1, 1, 1);
        clonedMat.albedoColor = new Color3(
          existing.r * color.r,
          existing.g * color.g,
          existing.b * color.b
        );
      } else {
        // Replace albedoColor
        clonedMat.albedoColor = color;
      }

      // Assign cloned material to this mesh
      mesh.material = clonedMat;
      found = true;
    }

    if (!found) {
      const availableMaterials = meshes
        .map(m => m.material?.name)
        .filter((n): n is string => !!n);
      logger.warn('Material not found', { entityId, materialName: desc.targetName, available: [...new Set(availableMaterials)] });
    }
  }
}
