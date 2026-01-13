/*
 * Source TS: ScrawlTypes.ts
 * Original TS: 'interface ScrawlSubject'
 */
package de.mhus.nimbus.generated.scrawl;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ScrawlSubject {
    private de.mhus.nimbus.generated.types.Vector3 position;
    @com.fasterxml.jackson.annotation.JsonProperty("entityId")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String entityId;
    @com.fasterxml.jackson.annotation.JsonProperty("blockId")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String blockId;
}
