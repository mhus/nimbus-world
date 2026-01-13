/*
 * Source TS: EntityData.ts
 * Original TS: 'interface EntityModel'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class EntityModel {
    private String id;
    private String type;
    @com.fasterxml.jackson.annotation.JsonProperty("modelPath")
    private String modelPath;
    @com.fasterxml.jackson.annotation.JsonProperty("positionOffset")
    private Vector3 positionOffset;
    @com.fasterxml.jackson.annotation.JsonProperty("rotationOffset")
    private Vector3 rotationOffset;
    private Vector3 scale;
    @com.fasterxml.jackson.annotation.JsonProperty("maxPitch")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Double maxPitch;
    @com.fasterxml.jackson.annotation.JsonProperty("poseMapping")
    private java.util.Map<ENTITY_POSES, PoseAnimation> poseMapping;
    @com.fasterxml.jackson.annotation.JsonProperty("poseType")
    private String poseType;
    @com.fasterxml.jackson.annotation.JsonProperty("modelModifierMapping")
    private java.util.Map<String, String> modelModifierMapping;
    private EntityDimensions dimensions;
    @com.fasterxml.jackson.annotation.JsonProperty("physicsProperties")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private EntityPhysicsProperties physicsProperties;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.List<AudioDefinition> audio;
}
