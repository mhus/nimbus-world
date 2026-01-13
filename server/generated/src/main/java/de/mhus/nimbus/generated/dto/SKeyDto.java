/*
 * Source TS: SKeyDto.ts
 * Original TS: 'interface SKeyDto'
 */
package de.mhus.nimbus.generated.dto;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class SKeyDto {
    private String id;
    private String type;
    private String kind;
    private String algorithm;
    @com.fasterxml.jackson.annotation.JsonProperty("keyId")
    private String keyId;
    @com.fasterxml.jackson.annotation.JsonProperty("createdAt")
    private String createdAt;
    private String owner;
    private String intent;
}
