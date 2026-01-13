/**
 * Shared types for Nimbus Voxel Engine
 */

// Core types
export * from './Vector3';
export * from './Vector3Color';
export * from './Rotation';
export * from './Color';
export * from './Shape';

// Block types
export * from './Block';
export * from './BlockType';
export * from './BlockModifier';

// Item types
export * from './Item';
export * from './ItemBlockRef';
export * from './ItemRef';
export * from './ItemType';
export * from './ItemModifier';
export * from './TargetingTypes';

// Chunk types
export * from './ChunkData';

// Backdrop types
export * from './Backdrop';

// World types
export * from './World';

// Layer types (from generated)
export * from '../generated/LayerType';
export * from '../generated/entities/WLayer';
export * from '../generated/entities/WLayerModel';

// Generated DTOs
export * from '../generated/dto/LayerDto';
export * from '../generated/dto/CreateLayerRequest';
export * from '../generated/dto/UpdateLayerRequest';
export * from '../generated/dto/LayerModelDto';
export * from '../generated/dto/CreateLayerModelRequest';
export * from '../generated/dto/UpdateLayerModelRequest';
export * from '../generated/dto/BlockOriginDto';

// Animation and effects
export * from './AnimationData';
export * from './AreaData';
export * from './EffectData';

// Entity types
export * from './EntityData';
export * from './ClientEntity';
export * from './ServerEntitySpawnDefinition';

// Player types
export * from './PlayerInfo';
export * from './PlayerMovementState';
export * from './ShortcutDefinition';
export * from './VitalsData';

// Team types
export * from './TeamData';

// Modal types (for IFrame communication)
export * from './Modal';

// Edit action types
export * from './EditAction';
export * from './EditSettings';

// Hex grid types
export * from './HexData';
export * from './HexVector2';
export * from './Area';
