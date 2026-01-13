/*
 * Source TS: EntityData.ts
 * Original TS: 'interface EntityStatusUpdate'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class EntityStatusUpdate {
    @com.fasterxml.jackson.annotation.JsonProperty("entityId")
    private String entityId;
    private java.util.Map<String, Object> status;
}
