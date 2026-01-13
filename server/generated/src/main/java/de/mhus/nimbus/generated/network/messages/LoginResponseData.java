/*
 * Source TS: LoginMessage.ts
 * Original TS: 'interface LoginResponseData'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class LoginResponseData {
    private boolean success;
    @com.fasterxml.jackson.annotation.JsonProperty("userId")
    private String userId;
    private String title;
    @com.fasterxml.jackson.annotation.JsonProperty("sessionId")
    private String sessionId;
}
