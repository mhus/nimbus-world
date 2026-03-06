package de.mhus.nimbus.world.life.behavior;

import de.mhus.nimbus.generated.types.ENTITY_POSES;
import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.generated.types.Rotation;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Waypoint;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.model.SimulationState;
import de.mhus.nimbus.world.life.movement.BlockBasedMovement;
import de.mhus.nimbus.world.shared.gameplay.CombatStat;
import de.mhus.nimbus.world.shared.gameplay.CombatStrategy;
import de.mhus.nimbus.world.shared.gameplay.EntityCombatData;
import de.mhus.nimbus.world.shared.redis.VitalDeltaPublisher;
import de.mhus.nimbus.world.shared.session.WSessionPosition;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.world.WEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Generates combat pathways for entities in combat mode.
 *
 * Strategies:
 * - FLEE: Run away from nearest attacker
 * - ATTACK_FLEE: Attack once, then flee
 * - ATTACK_REPEAT: Run towards attacker, attack, repeat
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CombatBehaviorHandler {

    private static final int FLEE_WAYPOINTS = 4;
    private static final int ATTACK_WAYPOINTS = 3;
    private static final double DEFAULT_ATTACK_RANGE = 0.5;
    private static final double FLEE_SPEED_MULTIPLIER = 1.5;

    private final BlockBasedMovement blockMovement;
    private final WSessionService sessionService;
    private final VitalDeltaPublisher vitalDeltaPublisher;

    /**
     * Generate a combat pathway based on entity strategy and attacker positions.
     *
     * @return EntityPathway or null if no valid pathway could be generated
     */
    public EntityPathway generateCombatPathway(WEntity entity, SimulationState state, long currentTime, WorldId worldId) {
        CombatStrategy strategy = state.getCombatStrategy();
        if (strategy == null) strategy = CombatStrategy.FLEE;

        // Find nearest attacker position
        Vector3 attackerPos = findNearestAttackerPosition(state);
        if (attackerPos == null) {
            log.debug("No attacker position found for entity {}, exiting combat", entity.getEntityId());
            state.exitCombat();
            return null;
        }

        Vector3 entityPos = entity.getPosition();
        if (entityPos == null) return null;

        // Check if attacker is too far away — exit combat
        double maxRange = getServerDouble(entity, "combat_maxRange", 30.0);
        double distToAttacker = distance(entityPos, attackerPos);
        if (distToAttacker > maxRange) {
            log.debug("World {}: Entity {} attacker too far (dist={}, max={}), exiting combat",
                    worldId, entity.getEntityId(), String.format("%.1f", distToAttacker), maxRange);
            state.exitCombat();
            return null;
        }

        return switch (strategy) {
            case FLEE -> generateFleePathway(entity, entityPos, attackerPos, currentTime, worldId);
            case ATTACK_FLEE -> generateAttackFleePathway(entity, state, entityPos, attackerPos, currentTime, worldId);
            case ATTACK_REPEAT -> generateAttackRepeatPathway(entity, state, entityPos, attackerPos, currentTime, worldId);
        };
    }

    /**
     * FLEE: Generate pathway running away from attacker.
     */
    private EntityPathway generateFleePathway(WEntity entity, Vector3 entityPos, Vector3 attackerPos,
                                               long currentTime, WorldId worldId) {
        Vector3 fleeDirection = calculateFleeDirection(entityPos, attackerPos);
        double speed = (entity.getSpeed() != null ? entity.getSpeed() : 1.0) * FLEE_SPEED_MULTIPLIER;

        List<Waypoint> waypoints = blockMovement.generatePathway(
                worldId, entityPos, fleeDirection, FLEE_WAYPOINTS, speed, currentTime);

        if (waypoints.isEmpty()) return null;

        // Set pose to RUN for fleeing
        waypoints.forEach(wp -> wp.setPose(ENTITY_POSES.RUN));

        return EntityPathway.builder()
                .entityId(entity.getEntityId())
                .startAt(currentTime)
                .waypoints(waypoints)
                .isLooping(false)
                .idlePose(ENTITY_POSES.IDLE)
                .build();
    }

    /**
     * ATTACK_FLEE: Attack once, then flee.
     */
    private EntityPathway generateAttackFleePathway(WEntity entity, SimulationState state,
                                                     Vector3 entityPos, Vector3 attackerPos,
                                                     long currentTime, WorldId worldId) {
        double attackRange = getServerDouble(entity, "combat_attackRange", DEFAULT_ATTACK_RANGE);
        if (state.getCombatAttackCount() == 0) {
            // First phase: move towards attacker and attack
            double distance = distance(entityPos, attackerPos);
            if (distance <= attackRange) {
                performAttack(entity, state, worldId);
                state.setCombatAttackCount(1);
                return generateAttackInPlacePathway(entity, entityPos, attackerPos, currentTime);
            }
            EntityPathway attackPathway = generateApproachPathway(entity, entityPos, attackerPos, currentTime, worldId);
            return attackPathway;
        } else {
            // Second phase: flee
            return generateFleePathway(entity, entityPos, attackerPos, currentTime, worldId);
        }
    }

    /**
     * ATTACK_REPEAT: Run towards attacker, attack, repeat.
     */
    private EntityPathway generateAttackRepeatPathway(WEntity entity, SimulationState state,
                                                       Vector3 entityPos, Vector3 attackerPos,
                                                       long currentTime, WorldId worldId) {
        double attackRange = getServerDouble(entity, "combat_attackRange", DEFAULT_ATTACK_RANGE);
        double distance = distance(entityPos, attackerPos);

        if (distance > attackRange) {
            // Move towards attacker
            return generateApproachPathway(entity, entityPos, attackerPos, currentTime, worldId);
        } else {
            // In range: attack and stay close
            state.setCombatAttackCount(state.getCombatAttackCount() + 1);
            performAttack(entity, state, worldId);
            return generateAttackInPlacePathway(entity, entityPos, attackerPos, currentTime);
        }
    }

    /**
     * Generate pathway approaching the attacker.
     */
    private EntityPathway generateApproachPathway(WEntity entity, Vector3 entityPos, Vector3 attackerPos,
                                                   long currentTime, WorldId worldId) {
        Vector3 direction = calculateDirectionTowards(entityPos, attackerPos);
        double speed = entity.getSpeed() != null ? entity.getSpeed() : 1.0;

        // Limit approach distance to not overshoot
        double distance = distance(entityPos, attackerPos);
        int waypointCount = Math.min(ATTACK_WAYPOINTS, (int) Math.ceil(distance / 2.5));
        if (waypointCount < 1) waypointCount = 1;

        List<Waypoint> waypoints = blockMovement.generatePathway(
                worldId, entityPos, direction, waypointCount, speed, currentTime);

        if (waypoints.isEmpty()) return null;

        // Set pose to RUN for approaching
        waypoints.forEach(wp -> wp.setPose(ENTITY_POSES.RUN));

        return EntityPathway.builder()
                .entityId(entity.getEntityId())
                .startAt(currentTime)
                .waypoints(waypoints)
                .isLooping(false)
                .idlePose(ENTITY_POSES.IDLE)
                .build();
    }

    /**
     * Generate short attack pathway (entity stays in place, faces attacker, plays attack pose).
     */
    private EntityPathway generateAttackInPlacePathway(WEntity entity, Vector3 entityPos, Vector3 attackerPos,
                                                        long currentTime) {
        Rotation rotation = calculateRotationTowards(entityPos, attackerPos);

        // Attack animation waypoint (stay in place)
        Waypoint attackWp = Waypoint.builder()
                .timestamp(currentTime + 500)
                .target(Vector3.builder().x(entityPos.getX()).y(entityPos.getY()).z(entityPos.getZ()).build())
                .rotation(rotation)
                .pose(ENTITY_POSES.ATTACK)
                .build();

        // Brief idle after attack
        Waypoint idleWp = Waypoint.builder()
                .timestamp(currentTime + 1500)
                .target(Vector3.builder().x(entityPos.getX()).y(entityPos.getY()).z(entityPos.getZ()).build())
                .rotation(rotation)
                .pose(ENTITY_POSES.IDLE)
                .build();

        return EntityPathway.builder()
                .entityId(entity.getEntityId())
                .startAt(currentTime)
                .waypoints(List.of(attackWp, idleWp))
                .isLooping(false)
                .idlePose(ENTITY_POSES.IDLE)
                .build();
    }

    /**
     * Perform an attack against the nearest attacker player.
     * Publishes attack via VitalDeltaPublisher to the player channel.
     */
    private void performAttack(WEntity entity, SimulationState state, WorldId worldId) {
        EntityCombatData combatData = state.getCombatData();
        if (combatData == null) return;

        // Find nearest attacker entityId
        String targetEntityId = findNearestAttackerEntityId(state);
        if (targetEntityId == null) return;

        double physDmg = getEffective(combatData, "physical.damage");
        double physAcc = getEffective(combatData, "physical.accuracy");
        double magDmg = getEffective(combatData, "magical.damage");
        double magAcc = getEffective(combatData, "magical.accuracy");
        double critChance = getEffective(combatData, "critChance");
        double critMult = getEffective(combatData, "critMultiplier");
        String weaponItemId = combatData.getWeaponItemId();

        vitalDeltaPublisher.publishAttack(
                worldId.getId(), targetEntityId, entity.getEntityId(),
                physDmg, physAcc, magDmg, magAcc, critChance, critMult,
                null, weaponItemId);

        log.debug("Entity {} attacked player {} with weapon {} [phys={}/{}, mag={}/{}]",
                entity.getEntityId(), targetEntityId, weaponItemId, physDmg, physAcc, magDmg, magAcc);
    }

    private double getEffective(EntityCombatData data, String statName) {
        CombatStat stat = data.getCombatStat(statName);
        return stat != null ? stat.getEffective() : 0;
    }

    /**
     * Find the entityId of the nearest attacker.
     */
    private String findNearestAttackerEntityId(SimulationState state) {
        Vector3 entityPos = state.getEntity().getPosition();
        if (entityPos == null) return null;

        String nearestId = null;
        double nearestDist = Double.MAX_VALUE;

        for (Map.Entry<String, String> entry : state.getAttackerSessions().entrySet()) {
            String attackerEntityId = entry.getKey();
            String sessionId = entry.getValue();
            var posOpt = sessionService.getPosition(sessionId);
            if (posOpt.isEmpty()) continue;

            WSessionPosition pos = posOpt.get();
            if (pos.getX() == null || pos.getY() == null || pos.getZ() == null) continue;

            double dist = distance(entityPos.getX(), entityPos.getY(), entityPos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ());

            if (dist < nearestDist) {
                nearestDist = dist;
                nearestId = attackerEntityId;
            }
        }

        return nearestId;
    }

    /**
     * Find the position of the nearest attacker by looking up session positions in Redis.
     */
    private Vector3 findNearestAttackerPosition(SimulationState state) {
        Vector3 entityPos = state.getEntity().getPosition();
        if (entityPos == null) return null;

        Vector3 nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Map.Entry<String, String> entry : state.getAttackerSessions().entrySet()) {
            String sessionId = entry.getValue();
            var posOpt = sessionService.getPosition(sessionId);
            if (posOpt.isEmpty()) continue;

            WSessionPosition pos = posOpt.get();
            if (pos.getX() == null || pos.getY() == null || pos.getZ() == null) continue;

            double dist = distance(entityPos.getX(), entityPos.getY(), entityPos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ());

            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = Vector3.builder().x(pos.getX()).y(pos.getY()).z(pos.getZ()).build();
            }
        }

        return nearest;
    }

    private Vector3 calculateFleeDirection(Vector3 from, Vector3 attacker) {
        double dx = from.getX() - attacker.getX();
        double dz = from.getZ() - attacker.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.01) {
            // Attacker is at same position, pick random direction
            return blockMovement.getRandomDirection();
        }
        return Vector3.builder().x(dx / length).y(0.0).z(dz / length).build();
    }

    private Vector3 calculateDirectionTowards(Vector3 from, Vector3 to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.01) {
            return Vector3.builder().x(0.0).y(0.0).z(1.0).build();
        }
        return Vector3.builder().x(dx / length).y(0.0).z(dz / length).build();
    }

    private Rotation calculateRotationTowards(Vector3 from, Vector3 to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double yawRad = Math.atan2(dx, dz);
        Rotation rotation = new Rotation();
        rotation.setY(Math.toDegrees(yawRad));
        rotation.setP(0.0);
        return rotation;
    }

    private static double getServerDouble(WEntity entity, String key, double defaultValue) {
        var server = entity.getServer();
        if (server == null) return defaultValue;
        String val = server.get(key);
        if (val == null || val.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double distance(Vector3 a, Vector3 b) {
        return distance(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    private double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
