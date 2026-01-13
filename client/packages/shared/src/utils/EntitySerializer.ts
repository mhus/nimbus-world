/**
 * Entity serialization utilities
 * Convert between Entity/EntityModel/EntityPathway and JSON representations
 */

import type { Entity, EntityModel, EntityPathway } from '../types/EntityData';
import type { EntityPositionUpdateData, EntityInteractionData } from '../network/messages/EntityMessage';

/**
 * Entity serialization helpers
 */
export namespace EntitySerializer {
  /**
   * Serialize Entity to JSON
   * @param entity Entity to serialize
   * @returns JSON string
   */
  export function entityToJSON(entity: Entity): string {
    return JSON.stringify(entity);
  }

  /**
   * Deserialize Entity from JSON
   * @param json JSON string
   * @returns Entity or null if invalid
   */
  export function entityFromJSON(json: string): Entity | null {
    try {
      const data = JSON.parse(json);
      return entityFromObject(data);
    } catch (e) {
      console.error('Failed to parse entity JSON:', e);
      return null;
    }
  }

  /**
   * Convert plain object to Entity
   * @param obj Plain object
   * @returns Entity or null if invalid
   */
  export function entityFromObject(obj: any): Entity | null {
    if (!obj || typeof obj !== 'object') {
      return null;
    }

    if (!obj.id || !obj.name || !obj.model || !obj.movementType) {
      return null;
    }

    return obj as Entity;
  }

  /**
   * Serialize entity array to JSON
   * @param entities Array of entities
   * @returns JSON string
   */
  export function entityArrayToJSON(entities: Entity[]): string {
    return JSON.stringify(entities);
  }

  /**
   * Deserialize entity array from JSON
   * @param json JSON string
   * @returns Array of entities or null
   */
  export function entityArrayFromJSON(json: string): Entity[] | null {
    try {
      const data = JSON.parse(json);

      if (!Array.isArray(data)) {
        return null;
      }

      const entities = data
        .map(entityFromObject)
        .filter((e): e is Entity => e !== null);

      return entities;
    } catch (e) {
      console.error('Failed to parse entity array JSON:', e);
      return null;
    }
  }

  /**
   * Serialize EntityModel to JSON
   * @param model EntityModel to serialize
   * @returns JSON string
   */
  export function modelToJSON(model: EntityModel): string {
    // Convert Maps to objects for JSON serialization
    const obj = {
      ...model,
      poseMapping: Object.fromEntries(model.poseMapping),
      modelModifierMapping: Object.fromEntries(model.modelModifierMapping),
    };
    return JSON.stringify(obj);
  }

  /**
   * Deserialize EntityModel from JSON
   * @param json JSON string
   * @returns EntityModel or null if invalid
   */
  export function modelFromJSON(json: string): EntityModel | null {
    try {
      const data = JSON.parse(json);
      return modelFromObject(data);
    } catch (e) {
      console.error('Failed to parse entity model JSON:', e);
      return null;
    }
  }

  /**
   * Convert plain object to EntityModel
   * @param obj Plain object
   * @returns EntityModel or null if invalid
   */
  export function modelFromObject(obj: any): EntityModel | null {
    if (!obj || typeof obj !== 'object') {
      return null;
    }

    if (!obj.id || !obj.type || !obj.modelPath || !obj.poseType || !obj.dimensions) {
      return null;
    }

    // Convert objects back to Maps
    return {
      ...obj,
      poseMapping: new Map(Object.entries(obj.poseMapping || {})),
      modelModifierMapping: new Map(Object.entries(obj.modelModifierMapping || {})),
    } as EntityModel;
  }

  /**
   * Serialize entity model array to JSON
   * @param models Array of entity models
   * @returns JSON string
   */
  export function modelArrayToJSON(models: EntityModel[]): string {
    const objects = models.map((m) => ({
      ...m,
      poseMapping: Object.fromEntries(m.poseMapping),
      modelModifierMapping: Object.fromEntries(m.modelModifierMapping),
    }));
    return JSON.stringify(objects);
  }

  /**
   * Deserialize entity model array from JSON
   * @param json JSON string
   * @returns Array of entity models or null
   */
  export function modelArrayFromJSON(json: string): EntityModel[] | null {
    try {
      const data = JSON.parse(json);

      if (!Array.isArray(data)) {
        return null;
      }

      const models = data
        .map(modelFromObject)
        .filter((m): m is EntityModel => m !== null);

      return models;
    } catch (e) {
      console.error('Failed to parse entity model array JSON:', e);
      return null;
    }
  }

  /**
   * Serialize EntityPathway to JSON
   * @param pathway EntityPathway to serialize
   * @returns JSON string
   */
  export function pathwayToJSON(pathway: EntityPathway): string {
    return JSON.stringify(pathway);
  }

  /**
   * Deserialize EntityPathway from JSON
   * @param json JSON string
   * @returns EntityPathway or null if invalid
   */
  export function pathwayFromJSON(json: string): EntityPathway | null {
    try {
      const data = JSON.parse(json);
      return pathwayFromObject(data);
    } catch (e) {
      console.error('Failed to parse entity pathway JSON:', e);
      return null;
    }
  }

  /**
   * Convert plain object to EntityPathway
   * @param obj Plain object
   * @returns EntityPathway or null if invalid
   */
  export function pathwayFromObject(obj: any): EntityPathway | null {
    if (!obj || typeof obj !== 'object') {
      return null;
    }

    if (!obj.entityId || !obj.startAt || !Array.isArray(obj.waypoints)) {
      return null;
    }

    return obj as EntityPathway;
  }

  /**
   * Serialize entity pathway array to JSON
   * @param pathways Array of entity pathways
   * @returns JSON string
   */
  export function pathwayArrayToJSON(pathways: EntityPathway[]): string {
    return JSON.stringify(pathways);
  }

  /**
   * Deserialize entity pathway array from JSON
   * @param json JSON string
   * @returns Array of entity pathways or null
   */
  export function pathwayArrayFromJSON(json: string): EntityPathway[] | null {
    try {
      const data = JSON.parse(json);

      if (!Array.isArray(data)) {
        return null;
      }

      const pathways = data
        .map(pathwayFromObject)
        .filter((p): p is EntityPathway => p !== null);

      return pathways;
    } catch (e) {
      console.error('Failed to parse entity pathway array JSON:', e);
      return null;
    }
  }

  /**
   * Serialize EntityPositionUpdateData to JSON
   * @param data EntityPositionUpdateData to serialize
   * @returns JSON string
   */
  export function positionUpdateToJSON(data: EntityPositionUpdateData): string {
    return JSON.stringify(data);
  }

  /**
   * Deserialize EntityPositionUpdateData from JSON
   * @param json JSON string
   * @returns EntityPositionUpdateData or null if invalid
   */
  export function positionUpdateFromJSON(json: string): EntityPositionUpdateData | null {
    try {
      const data = JSON.parse(json);
      return positionUpdateFromObject(data);
    } catch (e) {
      console.error('Failed to parse entity position update JSON:', e);
      return null;
    }
  }

  /**
   * Convert plain object to EntityPositionUpdateData
   * @param obj Plain object
   * @returns EntityPositionUpdateData or null if invalid
   */
  export function positionUpdateFromObject(obj: any): EntityPositionUpdateData | null {
    if (!obj || typeof obj !== 'object') {
      return null;
    }

    if (!obj.pl || !obj.ts) {
      return null;
    }

    return obj as EntityPositionUpdateData;
  }

  /**
   * Serialize entity position update array to JSON
   * @param updates Array of position updates
   * @returns JSON string
   */
  export function positionUpdateArrayToJSON(updates: EntityPositionUpdateData[]): string {
    return JSON.stringify(updates);
  }

  /**
   * Deserialize entity position update array from JSON
   * @param json JSON string
   * @returns Array of position updates or null
   */
  export function positionUpdateArrayFromJSON(json: string): EntityPositionUpdateData[] | null {
    try {
      const data = JSON.parse(json);

      if (!Array.isArray(data)) {
        return null;
      }

      const updates = data
        .map(positionUpdateFromObject)
        .filter((u): u is EntityPositionUpdateData => u !== null);

      return updates;
    } catch (e) {
      console.error('Failed to parse entity position update array JSON:', e);
      return null;
    }
  }

  /**
   * Serialize EntityInteractionData to JSON
   * @param data EntityInteractionData to serialize
   * @returns JSON string
   */
  export function interactionToJSON(data: EntityInteractionData): string {
    return JSON.stringify(data);
  }

  /**
   * Deserialize EntityInteractionData from JSON
   * @param json JSON string
   * @returns EntityInteractionData or null if invalid
   */
  export function interactionFromJSON(json: string): EntityInteractionData | null {
    try {
      const data = JSON.parse(json);
      return interactionFromObject(data);
    } catch (e) {
      console.error('Failed to parse entity interaction JSON:', e);
      return null;
    }
  }

  /**
   * Convert plain object to EntityInteractionData
   * @param obj Plain object
   * @returns EntityInteractionData or null if invalid
   */
  export function interactionFromObject(obj: any): EntityInteractionData | null {
    if (!obj || typeof obj !== 'object') {
      return null;
    }

    if (!obj.entityId || !obj.ts || !obj.ac) {
      return null;
    }

    return obj as EntityInteractionData;
  }
}
