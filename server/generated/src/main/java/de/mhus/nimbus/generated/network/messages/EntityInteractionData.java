/*
 * Source TS: EntityMessage.ts
 * Original TS: 'interface EntityInteractionData'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class EntityInteractionData {
    @com.fasterxml.jackson.annotation.JsonProperty("entityId")
    private String entityId;
    private long ts;
    private String ac;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.Map<String, Object> pa;
}
