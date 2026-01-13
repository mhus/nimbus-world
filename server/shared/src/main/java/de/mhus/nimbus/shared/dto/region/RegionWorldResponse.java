/*
 * Source TS: RegionWorldResponse.ts
 * Original TS: 'interface RegionWorldResponse'
 */
package de.mhus.nimbus.shared.dto.region;

@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RegionWorldResponse {
    private String id;
    private String worldId;
    private String name;
    private String description;
    private String worldApiUrl;
    private String regionId;
    private String createdAt;
}
