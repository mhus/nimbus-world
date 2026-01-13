/*
 * Source TS: LoginMessage.ts
 * Original TS: 'interface LoginRequestData'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class LoginRequestData {
    private String username;
    private String password;
    private String token;
    @com.fasterxml.jackson.annotation.JsonProperty("worldId")
    private String worldId;
    @com.fasterxml.jackson.annotation.JsonProperty("clientType")
    private ClientType clientType;
    @com.fasterxml.jackson.annotation.JsonProperty("sessionId")
    private String sessionId;
}
