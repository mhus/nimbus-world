/**
 * CollisionDetector - Collision detection and resolution
 *
 * Implements:
 * - Swept-AABB collision (Y → X → Z order)
 * - PassableFrom logic for one-way blocks and thin walls
 * - Semi-solid/slope collision
 * - Auto-climb detection
 */

import { Vector3 } from '@babylonjs/core';
import { getLogger, Direction } from '@nimbus/shared';
import type { PhysicsEntity, PlayerBlockContext } from './types';
import type { ChunkService } from '../ChunkService';
import { BlockContextAnalyzer } from './BlockContextAnalyzer';
import { SurfaceAnalyzer } from './SurfaceAnalyzer';
import * as PhysicsUtils from './PhysicsUtils';

const logger = getLogger('CollisionDetector');

/**
 * Entity dimensions
 */
interface EntityDimensions {
    height: number;
    width: number;
    footprint: number;
}

/**
 * Collision event callback
 */
export type CollisionEventCallback = (x: number, y: number, z: number, action: string, id?: string, gId?: string) => void;

/**
 * CollisionDetector - Handles all collision detection
 */
export class CollisionDetector {
    private surfaceAnalyzer: SurfaceAnalyzer;
    private collisionEventCallback?: CollisionEventCallback;

    constructor(
        private chunkService: ChunkService,
        private contextAnalyzer: BlockContextAnalyzer,
        private maxClimbHeight: number = 0.1
    ) {
        this.surfaceAnalyzer = new SurfaceAnalyzer(chunkService);
    }

    /**
     * Set callback for collision events
     */
    setCollisionEventCallback(callback: CollisionEventCallback): void {
        this.collisionEventCallback = callback;
    }

    /**
     * Trigger collision event if block has collisionEvent flag
     *
     * @param blockInfo Block that was collided with
     * @param action Action type ('collision', 'climb')
     */
    private triggerCollisionEvent(blockInfo: any, action: string = 'collision'): void {
        if (!this.collisionEventCallback) {
            return;
        }

        if (!blockInfo.block) {
            return;
        }

        const physics = blockInfo.block.currentModifier.physics;
        if (!physics?.collisionEvent) {
            return;
        }

        // Send collision event
        this.collisionEventCallback(
            blockInfo.x,
            blockInfo.y,
            blockInfo.z,
            action,
            blockInfo.block.block.metadata?.id,
            blockInfo.block.block.metadata?.groupId
        );

        logger.debug('Collision event triggered', {
            position: { x: blockInfo.x, y: blockInfo.y, z: blockInfo.z },
            action,
            id: blockInfo.block.block.metadata?.id,
        });
    }

    /**
     * Check and resolve collisions for movement
     * Uses Swept-AABB approach: Y → X → Z
     *
     * @param entity Entity to check
     * @param wishPosition Desired next position
     * @param dimensions Entity dimensions
     * @returns Resolved position after collision
     */
    resolveCollision(
        entity: PhysicsEntity,
        wishPosition: Vector3,
        dimensions: EntityDimensions
    ): Vector3 {
        const resolved = wishPosition.clone();

        // Phase 1: Vertical collision (Y)
        resolved.y = this.resolveVerticalCollision(entity, resolved, dimensions);

        // Phase 2: Horizontal collision (X, Z)
        const horizontal = this.resolveHorizontalCollision(entity, resolved, dimensions);
        resolved.x = horizontal.x;
        resolved.z = horizontal.z;

        return resolved;
    }

    /**
     * Resolve vertical (Y-axis) collision
     */
    private resolveVerticalCollision(
        entity: PhysicsEntity,
        wishPosition: Vector3,
        dimensions: EntityDimensions
    ): number {
        const currentY = entity.position.y;
        const wishY = wishPosition.y;
        const movingUp = wishY > currentY;

        // Get blocks above/below
        const context = this.contextAnalyzer.getContext(entity, dimensions);

        if (movingUp) {
            // Check WALL blocks with UP wall (moving up into block from below)
            for (const blockInfo of context.headBlocks.blocks) {
                const physics = blockInfo.block?.currentModifier.physics;
                if (!physics) continue;

                // Check if this is a WALL block with UP wall
                const isWall = physics.solid !== true && physics.passableFrom !== undefined;
                if (isWall && physics.passableFrom && (physics.passableFrom & Direction.UP)) {
                    // UP wall blocks entry from below
                    const ceilingY = blockInfo.y; // Bottom of the block
                    if (wishY + dimensions.height > ceilingY) {
                        entity.velocity.y = 0;
                        this.triggerCollisionEvent(blockInfo);
                        return ceilingY - dimensions.height;
                    }
                }
            }

            // Check head collision with solid blocks
            if (context.headBlocks.hasSolid) {
                // Hit ceiling - clamp to block bottom
                const ceilingY = context.headBlocks.maxY;
                if (wishY + dimensions.height > ceilingY) {
                    entity.velocity.y = 0;

                    // Trigger collision events for head blocks
                    for (const blockInfo of context.headBlocks.blocks) {
                        this.triggerCollisionEvent(blockInfo);
                    }

                    return ceilingY - dimensions.height;
                }
            }
        } else {
            // Check WALL blocks with DOWN wall (falling/moving down into block from above)
            for (const blockInfo of context.groundBlocks.blocks) {
                const physics = blockInfo.block?.currentModifier.physics;
                if (!physics) continue;

                // Check if this is a WALL block with DOWN wall
                const isWall = physics.solid !== true && physics.passableFrom !== undefined;
                if (isWall && physics.passableFrom && (physics.passableFrom & Direction.DOWN)) {
                    // DOWN wall blocks entry from above
                    const groundY = blockInfo.y + 1.0; // Top of the block
                    if (wishY < groundY) {
                        entity.velocity.y = 0;
                        entity.grounded = true;
                        this.triggerCollisionEvent(blockInfo);
                        return groundY;
                    }
                }
            }

            // Check ground collision with solid blocks
            if (context.groundBlocks.hasGround) {
                const groundY = context.groundBlocks.groundY + 1.0; // Top of ground block
                if (wishY < groundY) {
                    entity.velocity.y = 0;
                    entity.grounded = true;

                    // Trigger collision events for ground blocks
                    for (const blockInfo of context.groundBlocks.blocks) {
                        this.triggerCollisionEvent(blockInfo);
                    }

                    return groundY;
                }
            }

            // Check for semi-solid blocks (slopes)
            if (context.groundFootBlocks.isSemiSolid && context.groundFootBlocks.maxHeight > 0) {
                const surfaceY = Math.floor(currentY) + 1.0 + context.groundFootBlocks.maxHeight;
                if (wishY < surfaceY) {
                    entity.velocity.y = 0;
                    entity.grounded = true;
                    entity.onSlope = true;
                    return surfaceY;
                }
            }
        }

        return wishY;
    }

    /**
     * Resolve horizontal (X, Z) collision
     */
    private resolveHorizontalCollision(
        entity: PhysicsEntity,
        wishPosition: Vector3,
        dimensions: EntityDimensions
    ): { x: number; z: number } {
        const dx = wishPosition.x - entity.position.x;
        const dz = wishPosition.z - entity.position.z;

        // No horizontal movement
        if (Math.abs(dx) < 0.001 && Math.abs(dz) < 0.001) {
            return { x: wishPosition.x, z: wishPosition.z };
        }

        // PREDICTIVE COLLISION DETECTION:
        // Calculate intended final position and check if it would collide
        const intendedX = entity.position.x + dx;
        const intendedZ = entity.position.z + dz;

        // Get all blocks that intended position would intersect
        const collisionResult = this.checkCollisionAtIntendedPosition(
            entity,
            dimensions,
            intendedX,
            intendedZ,
            dx,
            dz
        );

        // If no collision, allow full movement
        if (!collisionResult.blocked) {
            return { x: intendedX, z: intendedZ };
        }

        // Collision detected - stop movement completely
        entity.velocity.x = 0;
        entity.velocity.z = 0;

        // Return current position (no movement)
        return { x: entity.position.x, z: entity.position.z };
    }

    /**
     * Check if intended position would cause collision with solid blocks
     * Returns blocking block info if collision detected
     */
    private checkCollisionAtIntendedPosition(
        entity: PhysicsEntity,
        dimensions: EntityDimensions,
        intendedX: number,
        intendedZ: number,
        dx: number,
        dz: number
    ): {
        blocked: boolean;
        blockingBlock?: { x: number; y: number; z: number; block: any };
    } {
        const footprint = dimensions.footprint;
        const feetY = Math.floor(entity.position.y);
        const numLevels = Math.ceil(dimensions.height);

        // FALL 2: Check if trying to EXIT from a WALL block
        // Check if we're close to the block boundary (0.2 units before crossing)
        const currentBlockX = Math.floor(entity.position.x);
        const currentBlockZ = Math.floor(entity.position.z);
        const threshold = 0.2;

        // Get ALL movement directions (can be multiple for diagonal movement)
        const movementDirs: Direction[] = [];
        if (dx > 0.001) movementDirs.push(Direction.EAST);
        if (dx < -0.001) movementDirs.push(Direction.WEST);
        if (dz > 0.001) movementDirs.push(Direction.NORTH);
        if (dz < -0.001) movementDirs.push(Direction.SOUTH);

        // Determine if we're approaching a block boundary in ANY movement direction
        let shouldCheckExit = false;
        for (const dir of movementDirs) {
            if (dir === Direction.NORTH) {
                const boundary = currentBlockZ + 1.0;
                if (intendedZ >= boundary - threshold) shouldCheckExit = true;
            } else if (dir === Direction.SOUTH) {
                const boundary = currentBlockZ;
                if (intendedZ <= boundary + threshold) shouldCheckExit = true;
            } else if (dir === Direction.EAST) {
                const boundary = currentBlockX + 1.0;
                if (intendedX >= boundary - threshold) shouldCheckExit = true;
            } else if (dir === Direction.WEST) {
                const boundary = currentBlockX;
                if (intendedX <= boundary + threshold) shouldCheckExit = true;
            }
        }

        // Only check Fall 2 if we're approaching block boundary
        if (shouldCheckExit) {

            // Check all vertical levels at current position
            for (let dy = 0; dy < numLevels; dy++) {
                const blockY = feetY + dy;
                const currentBlock = this.chunkService.getBlockAt(currentBlockX, blockY, currentBlockZ);
                if (!currentBlock) continue;

                const physics = currentBlock.currentModifier.physics;

                // Check if current block is a WALL block
                const isCurrentWall = physics?.solid !== true && physics?.passableFrom !== undefined;

                if (isCurrentWall) {
                    // Check if we can leave in ANY of the movement directions
                    let blocked = false;

                    for (const movementDir of movementDirs) {
                        if (!PhysicsUtils.canLeaveTo(physics.passableFrom, movementDir, false)) {
                            blocked = true;
                            break;
                        }
                    }

                    if (blocked) {
                        // Cannot leave through at least one wall side - blocked
                        const blockInfo = { x: currentBlockX, y: blockY, z: currentBlockZ, block: currentBlock };
                        this.triggerCollisionEvent(blockInfo);
                        return { blocked: true, blockingBlock: blockInfo };
                    }
                }
            }
        }

        // Calculate all 4 corners at intended position
        const intendedCorners = [
            { x: intendedX - footprint, z: intendedZ - footprint }, // NW
            { x: intendedX + footprint, z: intendedZ - footprint }, // NE
            { x: intendedX + footprint, z: intendedZ + footprint }, // SE
            { x: intendedX - footprint, z: intendedZ + footprint }, // SW
        ];

        // Check each corner for solid block collision OR wall block
        for (const corner of intendedCorners) {
            const blockX = Math.floor(corner.x);
            const blockZ = Math.floor(corner.z);

            // Skip Fall 1 if player is already standing on this block (Fall 2 handles exit from current block)
            if (currentBlockX === blockX && currentBlockZ === blockZ) {
                continue;
            }

            // Skip Fall 1 if block is not in movement direction (behind us)
            // This prevents footprint corners from triggering Fall 1 for blocks we're moving away from
            const movementDir = PhysicsUtils.getMovementDirection(dx, dz);
            if (movementDir === Direction.NORTH && blockZ <= currentBlockZ) continue; // Moving +Z, skip blocks at or behind current Z
            if (movementDir === Direction.SOUTH && blockZ >= currentBlockZ) continue; // Moving -Z, skip blocks at or ahead current Z
            if (movementDir === Direction.EAST && blockX <= currentBlockX) continue;  // Moving +X, skip blocks at or behind current X
            if (movementDir === Direction.WEST && blockX >= currentBlockX) continue;  // Moving -X, skip blocks at or ahead current X

            // Check all vertical levels
            for (let dy = 0; dy < numLevels; dy++) {
                const blockY = feetY + dy;
                const block = this.chunkService.getBlockAt(blockX, blockY, blockZ);
                if (!block) continue;

                const physics = block.currentModifier.physics;

                // Check if this is a WALL block (solid=false + passableFrom defined)
                const isWall = physics?.solid !== true && physics?.passableFrom !== undefined;

                // Skip if not solid and not a wall
                if (!physics?.solid && !isWall) continue;

                const blockInfo = { x: blockX, y: blockY, z: blockZ, block };

                // Calculate ALL sides of this block we're entering from (can be multiple for diagonal movement)
                // Based on block position relative to current position
                const entryDirs: Direction[] = [];
                if (blockX > currentBlockX) entryDirs.push(Direction.WEST);  // Block is east - entering from west side
                if (blockX < currentBlockX) entryDirs.push(Direction.EAST);  // Block is west - entering from east side
                if (blockZ > currentBlockZ) entryDirs.push(Direction.SOUTH); // Block is north - entering from south side
                if (blockZ < currentBlockZ) entryDirs.push(Direction.NORTH); // Block is south - entering from north side

                // WALL block: Check if it blocks from ANY of the entry directions
                if (isWall) {
                    let blocked = false;

                    for (const entryDir of entryDirs) {
                        if (!PhysicsUtils.canEnterFrom(physics.passableFrom, entryDir, false)) {
                            blocked = true;
                            break;
                        }
                    }

                    if (blocked) {
                        // Wall blocks at least one entry direction - treat as solid
                        this.triggerCollisionEvent(blockInfo);
                        return { blocked: true, blockingBlock: blockInfo };
                    }
                    // Wall allows entry from all directions - can pass through
                    continue;
                }

                // SOLID block: Check passableFrom for one-way gates
                if (physics.passableFrom !== undefined) {
                    // Check if ANY of the entry directions is blocked
                    let blocked = false;
                    for (const entryDir of entryDirs) {
                        if (!PhysicsUtils.canEnterFrom(physics.passableFrom, entryDir, true)) {
                            blocked = true;
                            break;
                        }
                    }

                    if (blocked) {
                        // Blocked by one-way gate
                        this.triggerCollisionEvent(blockInfo);
                        return { blocked: true, blockingBlock: blockInfo };
                    }
                    // Can pass through one-way gate
                    continue;
                }

                // Check if can climb over this block

                // Check 1: Slope blocks with low corner heights (always passable)
                const cornerHeights = this.surfaceAnalyzer.getCornerHeights(block);
                if (cornerHeights) {
                    const maxHeight = Math.max(...cornerHeights);
                    if (maxHeight <= this.maxClimbHeight) {
                        // Low slope - can step over without climb
                        continue;
                    }
                }

                // Check 2: Full 1-block step - check autoClimbable property
                const blockHeight = block.block.position.y + 1.0; // Top of block
                const currentY = entity.position.y;
                const heightDiff = blockHeight - currentY;

                if (heightDiff > 0 && heightDiff <= 1.0) {
                    if (physics.autoClimbable === false) {
                        // Explicitly disabled auto-climb - BLOCKED
                        this.triggerCollisionEvent(blockInfo);
                        return { blocked: true, blockingBlock: blockInfo };
                    } else {
                        // Can auto-climb this block
                        this.triggerCollisionEvent(blockInfo, 'climb');
                        continue;
                    }
                }

                // Check 3: Tall entities can step over 1-block obstacles
                if (heightDiff > 0 && heightDiff <= 1.0 && dimensions.height >= 1.5) {
                    this.triggerCollisionEvent(blockInfo, 'climb');
                    continue;
                }

                // Cannot climb - BLOCKED
                this.triggerCollisionEvent(blockInfo);
                return { blocked: true, blockingBlock: blockInfo };
            }
        }

        return { blocked: false };
    }

    /**
     * Check if entity is stuck in solid block and needs push-up
     */
    checkAndPushUp(entity: PhysicsEntity, dimensions: EntityDimensions): boolean {
        const context = this.contextAnalyzer.getContext(entity, dimensions);

        // If inside solid block, push up
        if (context.currentBlocks.hasSolid && !context.currentBlocks.allNonSolid) {
            // Check if space above is clear
            if (!context.headBlocks.hasSolid) {
                entity.position.y += 1.0;
                entity.velocity.y = 0;
                return true;
            }
        }

        return false;
    }

    /**
     * Check ground collision and update grounded state
     */
    checkGroundCollision(entity: PhysicsEntity, dimensions: EntityDimensions): void {
        const context = this.contextAnalyzer.getContext(entity, dimensions);

        // Check if on ground
        if (context.groundBlocks.hasGround) {
            const groundY = context.groundBlocks.groundY + 1.0;
            const distanceToGround = entity.position.y - groundY;

            if (distanceToGround < 0.01) {
                entity.grounded = true;
                entity.position.y = groundY; // Snap to ground
                if (entity.velocity.y < 0) {
                    entity.velocity.y = 0;
                }
            } else {
                entity.grounded = false;
            }
        } else {
            entity.grounded = false;
        }

        // Check for slopes
        entity.onSlope = context.groundFootBlocks.isSemiSolid;
    }

    /**
     * Check if entity can trigger auto-jump
     */
    canAutoJump(entity: PhysicsEntity, dimensions: EntityDimensions): boolean {
        const context = this.contextAnalyzer.getContext(entity, dimensions);

        return (
            context.footBlocks.autoJump > 0 ||
            context.groundBlocks.autoJump > 0 ||
            context.currentBlocks.hasSolid
        );

    }

    /**
     * return auto-jump value
     */
    getAutoJump(entity: PhysicsEntity, dimensions: EntityDimensions): number {
        const context = this.contextAnalyzer.getContext(entity, dimensions);

        if (!context.currentBlocks.hasSolid) {
            return 0;
        }

        return Math.max(context.footBlocks.autoJump,
            context.groundBlocks.autoJump);
    }

    /**
     * Check if entity is in climbable block
     */
    isClimbing(entity: PhysicsEntity, dimensions: EntityDimensions): boolean {
        const context = this.contextAnalyzer.getContext(entity, dimensions);

        // Check if in climbable block (ladder)
        for (const blockInfo of context.currentBlocks.blocks) {
            if (blockInfo.block?.currentModifier.physics?.climbable) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check and resolve collisions with entities
     *
     * @param playerPosition Player's current position
     * @param playerDimensions Player's dimensions
     * @param entities Entities to check collision against
     * @returns Object with corrected position and list of collided entity IDs
     */
    checkEntityCollisions(
        playerPosition: Vector3,
        playerDimensions: EntityDimensions,
        entities: Array<{
            entityId: string;
            position: Vector3;
            dimensions: { height: number; width: number; footprint: number };
            solid: boolean;
        }>
    ): { position: Vector3; collidedEntities: string[] } {
        const collidedEntities: string[] = [];
        let correctedPosition = playerPosition.clone();

        for (const entity of entities) {
            // Check 2D circle collision (X, Z plane)
            const dx = entity.position.x - correctedPosition.x;
            const dz = entity.position.z - correctedPosition.z;
            const distance2D = Math.sqrt(dx * dx + dz * dz);

            // Calculate minimum distance (sum of radii)
            const minDistance = (entity.dimensions.footprint / 2) + (playerDimensions.footprint / 2);

            // Check if circles overlap
            if (distance2D < minDistance) {
                // Check Y overlap (height check)
                const playerTop = correctedPosition.y + playerDimensions.height;
                const entityTop = entity.position.y + entity.dimensions.height;

                const yOverlap = correctedPosition.y < entityTop && playerTop > entity.position.y;

                if (yOverlap) {
                    // Collision detected
                    collidedEntities.push(entity.entityId);

                    // If entity is solid, push player away
                    if (entity.solid && distance2D > 0.001) {
                        // Calculate push vector (away from entity center)
                        const pushDistance = minDistance - distance2D;
                        const pushX = (dx / distance2D) * pushDistance;
                        const pushZ = (dz / distance2D) * pushDistance;

                        // Apply push (move player away from entity)
                        correctedPosition.x -= pushX;
                        correctedPosition.z -= pushZ;
                    }
                }
            }
        }

        return {
            position: correctedPosition,
            collidedEntities,
        };
    }
}
