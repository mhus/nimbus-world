/*
 * Source TS: ClientEntity.ts
 * Original TS: 'interface ClientEntity'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ClientEntity {
    private String id;
    private EntityModel model;
    private Entity entity;
    private boolean visible;
    private java.util.List<Object> meshes;
    @com.fasterxml.jackson.annotation.JsonProperty("currentPosition")
    private Vector3 currentPosition;
    @com.fasterxml.jackson.annotation.JsonProperty("currentRotation")
    private Rotation currentRotation;
    @com.fasterxml.jackson.annotation.JsonProperty("currentWaypointIndex")
    private int currentWaypointIndex;
    @com.fasterxml.jackson.annotation.JsonProperty("currentPose")
    private int currentPose;
    @com.fasterxml.jackson.annotation.JsonProperty("currentWaypoints")
    private java.util.List<Waypoint> currentWaypoints;
    @com.fasterxml.jackson.annotation.JsonProperty("lastAccess")
    private long lastAccess;
    @com.fasterxml.jackson.annotation.JsonProperty("lastStepTime")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private long lastStepTime;
}
