/*
 * Source TS: LoginMessage.ts
 * Original TS: 'interface LoginRequestData'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class LoginRequestData {
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String username;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String password;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String token;
    @com.fasterxml.jackson.annotation.JsonProperty("worldId")
    private String worldId;
    @com.fasterxml.jackson.annotation.JsonProperty("clientType")
    private de.mhus.nimbus.generated.network.ClientType clientType;
    @com.fasterxml.jackson.annotation.JsonProperty("sessionId")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String sessionId;
}
