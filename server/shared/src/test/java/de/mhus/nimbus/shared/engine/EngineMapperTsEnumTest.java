package de.mhus.nimbus.shared.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.ENTITY_POSES;
import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.generated.types.Rotation;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Waypoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that EngineMapper correctly serializes TsEnum values (e.g., ENTITY_POSES)
 * as their numeric tsString values, not as Java enum names.
 *
 * The TypeScript client expects numeric pose values: IDLE=0, WALK=1, RUN=2, etc.
 * Spring's default ObjectMapper serializes enums as their name ("IDLE", "WALK"),
 * which the client cannot parse to the expected numeric enum values.
 */
class EngineMapperTsEnumTest {

    private final EngineMapper engineMapper = new EngineMapper();
    private final ObjectMapper defaultObjectMapper = new ObjectMapper();

    @Test
    void engineMapper_serializesWalkPoseAsNumericString() throws Exception {
        Waypoint waypoint = createWaypoint(ENTITY_POSES.WALK);
        String json = engineMapper.writeValueAsString(waypoint);

        // EngineMapper should serialize WALK as "1" (tsIndex), not "WALK"
        assertThat(json).contains("\"pose\":\"1\"");
        assertThat(json).doesNotContain("\"pose\":\"WALK\"");
    }

    @Test
    void engineMapper_serializesIdlePoseAsNumericString() throws Exception {
        Waypoint waypoint = createWaypoint(ENTITY_POSES.IDLE);
        String json = engineMapper.writeValueAsString(waypoint);

        assertThat(json).contains("\"pose\":\"0\"");
        assertThat(json).doesNotContain("\"pose\":\"IDLE\"");
    }

    @Test
    void defaultObjectMapper_serializesPoseAsEnumName() throws Exception {
        Waypoint waypoint = createWaypoint(ENTITY_POSES.WALK);
        String json = defaultObjectMapper.writeValueAsString(waypoint);

        // Spring's default ObjectMapper serializes as enum name — this is the bug
        assertThat(json).contains("\"WALK\"");
        assertThat(json).doesNotContain("\"pose\":\"1\"");
    }

    @Test
    void engineMapper_roundTripsWaypointWithPose() throws Exception {
        Waypoint original = createWaypoint(ENTITY_POSES.WALK);
        String json = engineMapper.writeValueAsString(original);
        Waypoint deserialized = engineMapper.readValue(json, Waypoint.class);

        assertThat(deserialized.getPose()).isEqualTo(ENTITY_POSES.WALK);
    }

    @Test
    void engineMapper_roundTripsEntityPathwayWithMixedPoses() throws Exception {
        Waypoint walkWaypoint = createWaypoint(ENTITY_POSES.WALK);
        Waypoint idleWaypoint = createWaypoint(ENTITY_POSES.IDLE);

        EntityPathway pathway = EntityPathway.builder()
                .entityId("test-entity")
                .startAt(1000L)
                .waypoints(List.of(walkWaypoint, idleWaypoint))
                .isLooping(false)
                .idlePose(ENTITY_POSES.IDLE)
                .build();

        String json = engineMapper.writeValueAsString(pathway);

        // Verify poses are numeric in serialized JSON
        assertThat(json).contains("\"pose\":\"1\"");   // WALK
        assertThat(json).contains("\"pose\":\"0\"");   // IDLE
        assertThat(json).contains("\"idlePose\":\"0\""); // idlePose

        // Verify round-trip
        EntityPathway deserialized = engineMapper.readValue(json, EntityPathway.class);
        assertThat(deserialized.getWaypoints()).hasSize(2);
        assertThat(deserialized.getWaypoints().get(0).getPose()).isEqualTo(ENTITY_POSES.WALK);
        assertThat(deserialized.getWaypoints().get(1).getPose()).isEqualTo(ENTITY_POSES.IDLE);
        assertThat(deserialized.getIdlePose()).isEqualTo(ENTITY_POSES.IDLE);
    }

    @Test
    void engineMapper_deserializesNumericStringToPose() throws Exception {
        String json = """
                {"timestamp":1000,"target":{"x":10.0,"y":20.0,"z":30.0},"rotation":{"y":0.0,"p":0.0},"pose":"1"}
                """;
        Waypoint waypoint = engineMapper.readValue(json, Waypoint.class);
        assertThat(waypoint.getPose()).isEqualTo(ENTITY_POSES.WALK);
    }

    @Test
    void engineMapper_deserializesEnumNameToPose() throws Exception {
        // EngineMapper should also handle enum name as fallback
        String json = """
                {"timestamp":1000,"target":{"x":10.0,"y":20.0,"z":30.0},"rotation":{"y":0.0,"p":0.0},"pose":"WALK"}
                """;
        Waypoint waypoint = engineMapper.readValue(json, Waypoint.class);
        assertThat(waypoint.getPose()).isEqualTo(ENTITY_POSES.WALK);
    }

    private Waypoint createWaypoint(ENTITY_POSES pose) {
        return Waypoint.builder()
                .timestamp(1000L)
                .target(Vector3.builder().x(10.0).y(20.0).z(30.0).build())
                .rotation(Rotation.builder().y(0.0).p(0.0).build())
                .pose(pose)
                .build();
    }
}
