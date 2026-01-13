/*
 * Source TS: Modal.ts
 * Original TS: 'enum ModalFlags'
 */
package de.mhus.nimbus.generated.types;

public enum ModalFlags implements de.mhus.nimbus.types.TsEnum {
    NONE("0"),
    CLOSEABLE("1 << 0"),
    NO_BORDERS("1 << 1"),
    BREAK_OUT("1 << 2"),
    NO_BACKGROUND_LOCK("1 << 3"),
    MOVEABLE("1 << 4"),
    RESIZEABLE("1 << 5"),
    MINIMIZABLE("1 << 6");

    @lombok.Getter
    private final String tsIndex;
    ModalFlags(String tsIndex) { this.tsIndex = tsIndex; }
    public String tsString() { return this.tsIndex; }
}
