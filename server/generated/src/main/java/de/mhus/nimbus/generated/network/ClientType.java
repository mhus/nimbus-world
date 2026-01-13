/*
 * Source TS: MessageTypes.ts
 * Original TS: 'enum ClientType'
 */
package de.mhus.nimbus.generated.network;

public enum ClientType implements de.mhus.nimbus.types.TsEnum {
    WEB("web"),
    XBOX("xbox"),
    MOBILE("mobile"),
    DESKTOP("desktop");

    @lombok.Getter
    private final String tsIndex;
    ClientType(String tsIndex) { this.tsIndex = tsIndex; }
    public String tsString() { return this.tsIndex; }
}
