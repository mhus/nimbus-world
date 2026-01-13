/*
 * Source TS: RegionWorldRequest.ts
 * Original TS: 'interface RegionWorldRequest'
 */
package de.mhus.nimbus.shared.dto.region;

@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RegionWorldRequest {
    private String name;
    private String description;
    private String worldApiUrl;
}
