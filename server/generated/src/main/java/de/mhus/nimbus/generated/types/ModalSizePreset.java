/*
 * Source TS: Modal.ts
 * Original TS: 'enum ModalSizePreset'
 */
package de.mhus.nimbus.generated.types;

public enum ModalSizePreset implements de.mhus.nimbus.types.TsEnum {
    LEFT("left"),
    RIGHT("right"),
    TOP("top"),
    BOTTOM("bottom"),
    CENTER_SMALL("center_small"),
    CENTER_MEDIUM("center_medium"),
    CENTER_LARGE("center_large"),
    LEFT_TOP("left_top"),
    LEFT_BOTTOM("left_bottom"),
    RIGHT_TOP("right_top"),
    RIGHT_BOTTOM("right_bottom");

    @lombok.Getter
    private final String tsIndex;
    ModalSizePreset(String tsIndex) { this.tsIndex = tsIndex; }
    public String tsString() { return this.tsIndex; }
}
