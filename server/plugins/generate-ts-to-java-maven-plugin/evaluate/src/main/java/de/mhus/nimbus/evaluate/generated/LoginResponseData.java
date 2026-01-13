/*
 * Source TS: LoginMessage.ts
 * Original TS: 'interface LoginResponseData'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class LoginResponseData {
    private boolean success;
    @com.fasterxml.jackson.annotation.JsonProperty("userId")
    private String userId;
    @com.fasterxml.jackson.annotation.JsonProperty("displayName")
    private String displayName;
    @com.fasterxml.jackson.annotation.JsonProperty("sessionId")
    private String sessionId;
}
