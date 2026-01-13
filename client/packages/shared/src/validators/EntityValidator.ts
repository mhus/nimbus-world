/**
 * Entity validation functions
 */

import type { Entity, EntityModel, EntityPathway } from '../types/EntityData';
import type { EntityPositionUpdateData, EntityInteractionData } from '../network/messages/EntityMessage';
import { EntityConstants, LimitConstants } from '../constants/NimbusConstants';

/**
 * Validation result
 */
export interface ValidationResult {
  valid: boolean;
  errors: string[];
  warnings?: string[];
}

/**
 * Entity validators
 */
export namespace EntityValidator {
  /**
   * Validate entity ID format
   * @param id Entity ID
   * @returns True if valid
   */
  export function isValidEntityId(id: string): boolean {
    return (
      typeof id === 'string' &&
      id.length > 0 &&
      id.length <= EntityConstants.ENTITY_ID_MAX_LENGTH
    );
  }

  /**
   * Validate movement type
   * @param type Movement type
   * @returns True if valid
   */
  export function isValidMovementType(type: string): boolean {
    return ['static', 'passive', 'slow', 'dynamic'].includes(type);
  }

  /**
   * Validate pose type
   * @param type Pose type
   * @returns True if valid
   */
  export function isValidPoseType(type: string): boolean {
    return [
      '2-Legs',
      '4-Legs',
      '6-Legs',
      'Wings',
      'Fish',
      'Snake',
      'Humanoid',
      'Slime',
    ].includes(type);
  }

  /**
   * Validate rotation
   * @param rotation Rotation object
   * @returns True if valid
   */
  export function isValidRotation(rotation: any): boolean {
    return (
      rotation &&
      typeof rotation === 'object' &&
      Number.isFinite(rotation.y) &&
      Number.isFinite(rotation.p)
    );
  }

  /**
   * Validate position
   * @param position Position vector
   * @returns True if valid
   */
  export function isValidPosition(position: any): boolean {
    return (
      position &&
      typeof position === 'object' &&
      Number.isFinite(position.x) &&
      Number.isFinite(position.y) &&
      Number.isFinite(position.z)
    );
  }

  /**
   * Validate dimensions
   * @param dimensions Entity dimensions
   * @returns True if valid
   */
  export function isValidDimensions(dimensions: any): boolean {
    if (!dimensions || typeof dimensions !== 'object') {
      return false;
    }

    // Check that at least one movement type has dimensions
    const keys = Object.keys(dimensions);
    if (keys.length === 0) {
      return false;
    }

    // Validate each dimension set
    for (const key of keys) {
      const dim = dimensions[key];
      if (!dim || typeof dim !== 'object') {
        return false;
      }
      if (
        !Number.isFinite(dim.height) ||
        !Number.isFinite(dim.width) ||
        !Number.isFinite(dim.footprint)
      ) {
        return false;
      }
      if (dim.height <= 0 || dim.width <= 0 || dim.footprint <= 0) {
        return false;
      }
    }

    return true;
  }

  /**
   * Validate Entity instance
   * @param entity Entity to validate
   * @returns Validation result
   */
  export function validateEntity(entity: Entity): ValidationResult {
    const errors: string[] = [];
    const warnings: string[] = [];

    // Validate ID
    if (!isValidEntityId(entity.id)) {
      errors.push(`Invalid entity ID: ${entity.id}`);
    }

    // Validate name
    if (!entity.name || typeof entity.name !== 'string') {
      errors.push('Entity name is required');
    }

    // Validate model reference
    if (!entity.model || typeof entity.model !== 'string') {
      errors.push('Entity model reference is required');
    }

    // Validate movement type
    if (!isValidMovementType(entity.movementType)) {
      errors.push(`Invalid movement type: ${entity.movementType}`);
    }

    // Validate optional fields
    if (entity.modelModifier && typeof entity.modelModifier !== 'object') {
      errors.push('Invalid modelModifier: must be an object');
    }

    return { valid: errors.length === 0, errors, warnings };
  }

  /**
   * Validate EntityModel
   * @param model EntityModel to validate
   * @returns Validation result
   */
  export function validateEntityModel(model: EntityModel): ValidationResult {
    const errors: string[] = [];
    const warnings: string[] = [];

    // Validate ID
    if (!isValidEntityId(model.id)) {
      errors.push(`Invalid entity model ID: ${model.id}`);
    }

    // Validate type
    if (!model.type || typeof model.type !== 'string') {
      errors.push('Entity model type is required');
    }

    // Validate model path
    if (!model.modelPath || typeof model.modelPath !== 'string') {
      errors.push('Entity model path is required');
    }

    // Validate pose type
    if (!isValidPoseType(model.poseType)) {
      errors.push(`Invalid pose type: ${model.poseType}`);
    }

    // Validate position offset
    if (!isValidPosition(model.positionOffset)) {
      errors.push('Invalid position offset');
    }

    // Validate rotation offset
    if (!isValidPosition(model.rotationOffset)) {
      errors.push('Invalid rotation offset');
    }

    // Validate dimensions
    if (!isValidDimensions(model.dimensions)) {
      errors.push('Invalid dimensions');
    }

    // Validate maps
    if (!(model.poseMapping instanceof Map)) {
      errors.push('poseMapping must be a Map');
    }

    if (!(model.modelModifierMapping instanceof Map)) {
      errors.push('modelModifierMapping must be a Map');
    }

    return { valid: errors.length === 0, errors, warnings };
  }

  /**
   * Validate EntityPathway
   * @param pathway EntityPathway to validate
   * @returns Validation result
   */
  export function validateEntityPathway(pathway: EntityPathway): ValidationResult {
    const errors: string[] = [];
    const warnings: string[] = [];

    // Validate entity ID
    if (!isValidEntityId(pathway.entityId)) {
      errors.push(`Invalid entity ID: ${pathway.entityId}`);
    }

    // Validate startAt timestamp
    if (!Number.isFinite(pathway.startAt) || pathway.startAt < 0) {
      errors.push(`Invalid startAt timestamp: ${pathway.startAt}`);
    }

    // Validate waypoints array
    if (!Array.isArray(pathway.waypoints)) {
      errors.push('Waypoints must be an array');
    } else if (pathway.waypoints.length === 0) {
      warnings.push('Pathway has no waypoints');
    } else {
      // Validate each waypoint
      pathway.waypoints.forEach((waypoint, index) => {
        if (!Number.isFinite(waypoint.timestamp)) {
          errors.push(`Waypoint[${index}]: Invalid timestamp`);
        }
        if (!isValidPosition(waypoint.target)) {
          errors.push(`Waypoint[${index}]: Invalid target position`);
        }
        if (!isValidRotation(waypoint.rotation)) {
          errors.push(`Waypoint[${index}]: Invalid rotation`);
        }
        if (!Number.isFinite(waypoint.pose)) {
          errors.push(`Waypoint[${index}]: Invalid pose`);
        }
      });

      // Check waypoint timestamp ordering
      for (let i = 1; i < pathway.waypoints.length; i++) {
        if (pathway.waypoints[i].timestamp <= pathway.waypoints[i - 1].timestamp) {
          warnings.push(
            `Waypoint[${i}]: Timestamp not increasing (${pathway.waypoints[i].timestamp} <= ${pathway.waypoints[i - 1].timestamp})`
          );
        }
      }
    }

    return { valid: errors.length === 0, errors, warnings };
  }

  /**
   * Validate entity array
   * @param entities Array of entities
   * @param maxCount Maximum allowed entities
   * @returns Validation result
   */
  export function validateEntityArray(
    entities: Entity[],
    maxCount: number = LimitConstants.MAX_ENTITIES_PER_MESSAGE
  ): ValidationResult {
    const errors: string[] = [];
    const warnings: string[] = [];

    if (!Array.isArray(entities)) {
      errors.push('Entities must be an array');
      return { valid: false, errors, warnings };
    }

    if (entities.length > maxCount) {
      errors.push(`Too many entities: ${entities.length} (max: ${maxCount})`);
    }

    // Validate each entity
    entities.forEach((entity, index) => {
      const result = validateEntity(entity);
      if (!result.valid) {
        errors.push(`Entity[${index}]: ${result.errors.join(', ')}`);
      }
    });

    // Check for duplicate IDs
    const ids = new Set<string>();
    entities.forEach((entity, index) => {
      if (ids.has(entity.id)) {
        errors.push(`Duplicate entity ID at index ${index}: ${entity.id}`);
      }
      ids.add(entity.id);
    });

    return { valid: errors.length === 0, errors, warnings };
  }

  /**
   * Validate entity model array
   * @param models Array of entity models
   * @param maxCount Maximum allowed models
   * @returns Validation result
   */
  export function validateEntityModelArray(
    models: EntityModel[],
    maxCount: number = LimitConstants.MAX_ENTITIES_PER_MESSAGE
  ): ValidationResult {
    const errors: string[] = [];
    const warnings: string[] = [];

    if (!Array.isArray(models)) {
      errors.push('Entity models must be an array');
      return { valid: false, errors, warnings };
    }

    if (models.length > maxCount) {
      errors.push(`Too many entity models: ${models.length} (max: ${maxCount})`);
    }

    // Validate each model
    models.forEach((model, index) => {
      const result = validateEntityModel(model);
      if (!result.valid) {
        errors.push(`EntityModel[${index}]: ${result.errors.join(', ')}`);
      }
    });

    // Check for duplicate IDs
    const ids = new Set<string>();
    models.forEach((model, index) => {
      if (ids.has(model.id)) {
        errors.push(`Duplicate entity model ID at index ${index}: ${model.id}`);
      }
      ids.add(model.id);
    });

    return { valid: errors.length === 0, errors, warnings };
  }

  /**
   * Quick validation (only critical checks)
   * @param entity Entity to validate
   * @returns True if valid
   */
  export function isValid(entity: Entity): boolean {
    return (
      isValidEntityId(entity.id) &&
      typeof entity.name === 'string' &&
      typeof entity.model === 'string' &&
      isValidMovementType(entity.movementType)
    );
  }

  /**
   * Quick validation for EntityModel
   * @param model EntityModel to validate
   * @returns True if valid
   */
  export function isValidModel(model: EntityModel): boolean {
    return (
      isValidEntityId(model.id) &&
      typeof model.type === 'string' &&
      typeof model.modelPath === 'string' &&
      isValidPoseType(model.poseType) &&
      isValidDimensions(model.dimensions)
    );
  }

  /**
   * Validate EntityPositionUpdateData
   * @param data Position update data to validate
   * @returns Validation result
   */
  export function validatePositionUpdate(data: EntityPositionUpdateData): ValidationResult {
    const errors: string[] = [];
    const warnings: string[] = [];

    // Validate player/entity ID
    if (!data.pl || typeof data.pl !== 'string') {
      errors.push('Player/entity ID (pl) is required');
    }

    // Validate timestamp
    if (!Number.isFinite(data.ts) || data.ts < 0) {
      errors.push(`Invalid timestamp: ${data.ts}`);
    }

    // Validate optional position
    if (data.p && !isValidPosition(data.p)) {
      errors.push('Invalid position');
    }

    // Validate optional rotation
    if (data.r && !isValidRotation(data.r)) {
      errors.push('Invalid rotation');
    }

    // Validate optional velocity
    if (data.v && !isValidPosition(data.v)) {
      errors.push('Invalid velocity');
    }

    // Validate optional pose
    if (data.po !== undefined && !Number.isFinite(data.po)) {
      errors.push(`Invalid pose ID: ${data.po}`);
    }

    // Validate optional target arrival
    if (data.ta) {
      if (
        !Number.isFinite(data.ta.x) ||
        !Number.isFinite(data.ta.y) ||
        !Number.isFinite(data.ta.z) ||
        !Number.isFinite(data.ta.ts)
      ) {
        errors.push('Invalid target arrival data');
      }
      if (data.ta.ts < data.ts) {
        warnings.push('Target arrival timestamp is before current timestamp');
      }
    }

    return { valid: errors.length === 0, errors, warnings };
  }

  /**
   * Validate EntityPositionUpdateData array
   * @param updates Array of position updates
   * @param maxCount Maximum allowed updates
   * @returns Validation result
   */
  export function validatePositionUpdateArray(
    updates: EntityPositionUpdateData[],
    maxCount: number = LimitConstants.MAX_ENTITIES_PER_MESSAGE
  ): ValidationResult {
    const errors: string[] = [];
    const warnings: string[] = [];

    if (!Array.isArray(updates)) {
      errors.push('Position updates must be an array');
      return { valid: false, errors, warnings };
    }

    if (updates.length > maxCount) {
      errors.push(`Too many position updates: ${updates.length} (max: ${maxCount})`);
    }

    // Validate each update
    updates.forEach((update, index) => {
      const result = validatePositionUpdate(update);
      if (!result.valid) {
        errors.push(`PositionUpdate[${index}]: ${result.errors.join(', ')}`);
      }
    });

    return { valid: errors.length === 0, errors, warnings };
  }

  /**
   * Validate EntityInteractionData
   * @param data Interaction data to validate
   * @returns Validation result
   */
  export function validateInteraction(data: EntityInteractionData): ValidationResult {
    const errors: string[] = [];
    const warnings: string[] = [];

    // Validate entity ID
    if (!isValidEntityId(data.entityId)) {
      errors.push(`Invalid entity ID: ${data.entityId}`);
    }

    // Validate timestamp
    if (!Number.isFinite(data.ts) || data.ts < 0) {
      errors.push(`Invalid timestamp: ${data.ts}`);
    }

    // Validate action
    if (!data.ac || typeof data.ac !== 'string') {
      errors.push('Action (ac) is required');
    } else {
      // Warn about unknown actions
      const knownActions = ['use', 'talk', 'attack', 'touch', 'pickup', 'drop'];
      if (!knownActions.includes(data.ac)) {
        warnings.push(`Unknown action: ${data.ac}`);
      }
    }

    // Validate optional parameters
    if (data.pa && typeof data.pa !== 'object') {
      errors.push('Parameters (pa) must be an object');
    }

    return { valid: errors.length === 0, errors, warnings };
  }

  /**
   * Quick validation for EntityPositionUpdateData
   * @param data Position update data to validate
   * @returns True if valid
   */
  export function isValidPositionUpdate(data: EntityPositionUpdateData): boolean {
    return (
      typeof data.pl === 'string' &&
      Number.isFinite(data.ts) &&
      data.ts >= 0
    );
  }

  /**
   * Quick validation for EntityInteractionData
   * @param data Interaction data to validate
   * @returns True if valid
   */
  export function isValidInteraction(data: EntityInteractionData): boolean {
    return (
      isValidEntityId(data.entityId) &&
      Number.isFinite(data.ts) &&
      data.ts >= 0 &&
      typeof data.ac === 'string' &&
      data.ac.length > 0
    );
  }
}
