/*
 * Source TS: CreateSKeyRequest.ts
 * Original TS: 'interface mirroring'
 */
package de.mhus.nimbus.generated.dto;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class mirroring {
    private String type;
    private String kind;
    private String algorithm;
    private String name;
    private String key;
}
