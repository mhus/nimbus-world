/*
 * Source TS: ScriptActionDefinition.ts
 * Original TS: 'interface ScriptActionDefinition'
 */
package de.mhus.nimbus.generated.scrawl;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ScriptActionDefinition {
    @com.fasterxml.jackson.annotation.JsonProperty("scriptId")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String scriptId;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.Map<String, Object> parameters;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private ScrawlScript script;
    @com.fasterxml.jackson.annotation.JsonProperty("sendToServer")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Boolean sendToServer;
}
