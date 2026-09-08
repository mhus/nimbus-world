/*
 * Source TS: LoginMessage.ts
 * Original TS: 'type LoginResponseMessage'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class LoginResponseMessage {
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Object value;
}
