/*
 * Source TS: EngineConfiguration.ts
 * Original TS: 'interface ServerInfo'
 */
package de.mhus.nimbus.generated.configs;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ServerInfo {
    @com.fasterxml.jackson.annotation.JsonProperty("websocketUrl")
    private String websocketUrl;
}
