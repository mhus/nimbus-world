/*
 * Source TS: Modal.ts
 * Original TS: 'enum IFrameMessageType'
 */
package de.mhus.nimbus.generated.types;

public enum IFrameMessageType implements de.mhus.nimbus.types.TsEnum {
    IFRAME_READY("IFRAME_READY"),
    REQUEST_CLOSE("REQUEST_CLOSE"),
    REQUEST_POSITION_CHANGE("REQUEST_POSITION_CHANGE"),
    NOTIFICATION("NOTIFICATION");

    @lombok.Getter
    private final String tsIndex;
    IFrameMessageType(String tsIndex) { this.tsIndex = tsIndex; }
    public String tsString() { return this.tsIndex; }
}
