/*
 * Source TS: LoginMessage.ts
 * Original TS: 'interface LoginErrorData'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class LoginErrorData {
    private boolean success;
    @com.fasterxml.jackson.annotation.JsonProperty("errorCode")
    private int errorCode;
    @com.fasterxml.jackson.annotation.JsonProperty("errorMessage")
    private String errorMessage;
}
