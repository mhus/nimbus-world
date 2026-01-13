/*
 * Source TS: EngineConfiguration.ts
 * Original TS: 'interface EngineConfiguration'
 */
package de.mhus.nimbus.generated.configs;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class EngineConfiguration {
    @com.fasterxml.jackson.annotation.JsonProperty("serverInfo")
    private ServerInfo serverInfo;
    @com.fasterxml.jackson.annotation.JsonProperty("worldInfo")
    private de.mhus.nimbus.generated.types.WorldInfo worldInfo;
    @com.fasterxml.jackson.annotation.JsonProperty("playerInfo")
    private de.mhus.nimbus.generated.types.PlayerInfo playerInfo;
    @com.fasterxml.jackson.annotation.JsonProperty("playerBackpack")
    private PlayerBackpack playerBackpack;
    private Settings settings;
}
