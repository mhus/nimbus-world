/*
 * Source TS: Block.ts
 * Original TS: 'interface IBlockWithModifier'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class IBlockWithModifier {
    private Block block;
    @com.fasterxml.jackson.annotation.JsonProperty("currentModifier")
    private BlockModifier currentModifier;
}
