/*
 * Source TS: EntityData.ts
 * Original TS: 'interface EntityPathway'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class EntityPathway {
    @com.fasterxml.jackson.annotation.JsonProperty("entityId")
    private String entityId;
    @com.fasterxml.jackson.annotation.JsonProperty("startAt")
    private long startAt;
    private java.util.List<Waypoint> waypoints;
    @com.fasterxml.jackson.annotation.JsonProperty("isLooping")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Boolean isLooping;
    @com.fasterxml.jackson.annotation.JsonProperty("queryAt")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private long queryAt;
    @com.fasterxml.jackson.annotation.JsonProperty("idlePose")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private ENTITY_POSES idlePose;
    @com.fasterxml.jackson.annotation.JsonProperty("physicsEnabled")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Boolean physicsEnabled;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Vector3 velocity;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Boolean grounded;
}
