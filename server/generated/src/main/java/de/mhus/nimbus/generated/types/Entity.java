/*
 * Source TS: EntityData.ts
 * Original TS: 'interface Entity'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Entity {
    private String id;
    private String name;
    private String model;
    @com.fasterxml.jackson.annotation.JsonProperty("modelModifier")
    private java.util.Map<String, String> modelModifier;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private EntityModifier modifier;
    @com.fasterxml.jackson.annotation.JsonProperty("movementType")
    private String movementType;
    @com.fasterxml.jackson.annotation.JsonProperty("controlledBy")
    private String controlledBy;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Boolean solid;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Boolean interactive;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Boolean physics;
    @com.fasterxml.jackson.annotation.JsonProperty("clientPhysics")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Boolean clientPhysics;
    @com.fasterxml.jackson.annotation.JsonProperty("notifyOnCollision")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Boolean notifyOnCollision;
    @com.fasterxml.jackson.annotation.JsonProperty("notifyOnAttentionRange")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Double notifyOnAttentionRange;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private float health;
    @com.fasterxml.jackson.annotation.JsonProperty("healthMax")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private float healthMax;
}
