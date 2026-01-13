/*
 * Source TS: World.ts
 * Original TS: 'interface WorldInfoEntryPointDTO'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class WorldInfoEntryPointDTO {
    private Area area;
    private HexVector2 grid;
}
