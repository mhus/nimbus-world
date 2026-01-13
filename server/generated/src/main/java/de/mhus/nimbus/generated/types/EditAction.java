/*
 * Source TS: EditAction.ts
 * Original TS: 'enum EditAction'
 */
package de.mhus.nimbus.generated.types;

public enum EditAction implements de.mhus.nimbus.types.TsEnum {
    OPEN_CONFIG_DIALOG("OPEN_CONFIG_DIALOG"),
    OPEN_EDITOR("OPEN_EDITOR"),
    MARK_BLOCK("MARK_BLOCK"),
    PASTE_BLOCK("PASTE_BLOCK"),
    DELETE_BLOCK("DELETE_BLOCK"),
    SMOOTH_BLOCKS("SMOOTH_BLOCKS"),
    ROUGH_BLOCKS("ROUGH_BLOCKS"),
    CLONE_BLOCK("CLONE_BLOCK");

    @lombok.Getter
    private final String tsIndex;
    EditAction(String tsIndex) { this.tsIndex = tsIndex; }
    public String tsString() { return this.tsIndex; }
}
