/*
 * Source TS: ChunkMessage.ts
 * Original TS: 'interface ChunkRegisterData'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ChunkRegisterData {
    private int cx;
    private int cz;
    private int hr;
    private int lr;
}
