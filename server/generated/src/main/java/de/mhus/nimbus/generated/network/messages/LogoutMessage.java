/*
 * Source TS: LogoutMessage.ts
 * Original TS: 'type LogoutMessage'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class LogoutMessage {
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private de.mhus.nimbus.generated.network.BaseMessage<java.util.Map<String, Object>> value;
}
