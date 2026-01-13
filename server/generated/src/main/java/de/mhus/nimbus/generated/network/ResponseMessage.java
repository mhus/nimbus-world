/*
 * Source TS: BaseMessage.ts
 * Original TS: 'interface ResponseMessage'
 */
package de.mhus.nimbus.generated.network;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ResponseMessage extends BaseMessage {
    private String r;
}
