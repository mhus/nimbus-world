/*
 * Source TS: TargetingTypes.ts
 * Original TS: 'interface ClientBlockBlockDTOMetadataDTO'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ClientBlockBlockDTOMetadataDTO {
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String id;
    @com.fasterxml.jackson.annotation.JsonProperty("groupId")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String groupId;
}
