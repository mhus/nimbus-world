/*
 * Source TS: LoginMessage.ts
 * Original TS: 'type LoginMessage'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class LoginMessage {
    private RequestMessage<LoginRequestData> value;
}
