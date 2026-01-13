/*
 * Source TS: ServerEntitySpawnDefinition.ts
 * Original TS: 'interface ServerEntitySpawnDefinition'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ServerEntitySpawnDefinition {
    @com.fasterxml.jackson.annotation.JsonProperty("entityId")
    private String entityId;
    @com.fasterxml.jackson.annotation.JsonProperty("entityModelId")
    private String entityModelId;
    @com.fasterxml.jackson.annotation.JsonProperty("initialPosition")
    private Vector3 initialPosition;
    @com.fasterxml.jackson.annotation.JsonProperty("initialRotation")
    private Rotation initialRotation;
    @com.fasterxml.jackson.annotation.JsonProperty("middlePoint")
    private Vector3 middlePoint;
    private double radius;
    private double speed;
    @com.fasterxml.jackson.annotation.JsonProperty("behaviorModel")
    private String behaviorModel;
    @com.fasterxml.jackson.annotation.JsonProperty("behaviorConfig")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private BehaviorConfig behaviorConfig;
    @com.fasterxml.jackson.annotation.JsonProperty("currentPathway")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private EntityPathway currentPathway;
    private java.util.List<Vector2> chunks;
    @com.fasterxml.jackson.annotation.JsonProperty("physicsState")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private ServerEntitySpawnDefinitionPhysicsStateDTO physicsState;
}
