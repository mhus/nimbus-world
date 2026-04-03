/*
 * Source TS: Modal.ts
 * Original TS: 'enum IFrameParentMessageType'
 */
package de.mhus.nimbus.generated.types;

public enum IFrameParentMessageType implements de.mhus.nimbus.types.TsEnum {
    CLOSING("PARENT_CLOSING");

    @lombok.Getter
    private final String tsIndex;
    IFrameParentMessageType(String tsIndex) { this.tsIndex = tsIndex; }
    public String tsString() { return this.tsIndex; }
}
