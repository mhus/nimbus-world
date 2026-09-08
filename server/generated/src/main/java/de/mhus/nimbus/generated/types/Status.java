/*
 * Source TS: ChunkData.ts
 * Original TS: 'type Status'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Status {
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.List<Object> value;
}
