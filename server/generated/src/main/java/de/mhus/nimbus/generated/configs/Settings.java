/*
 * Source TS: EngineConfiguration.ts
 * Original TS: 'interface Settings'
 */
package de.mhus.nimbus.generated.configs;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Settings {
    private String name;
    @com.fasterxml.jackson.annotation.JsonProperty("inputController")
    private String inputController;
    @com.fasterxml.jackson.annotation.JsonProperty("inputMappings")
    private java.util.Map<String, String> inputMappings;
}
