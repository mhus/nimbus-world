/*
 * Source TS: BlockModifier.ts
 * Original TS: 'enum AudioType'
 */
package de.mhus.nimbus.generated.types;

public enum AudioType implements de.mhus.nimbus.types.TsEnum {
    STEPS("steps"),
    PERMANENT("permanent"),
    COLLISION("collision");

    @lombok.Getter
    private final String tsIndex;
    AudioType(String tsIndex) { this.tsIndex = tsIndex; }
    public String tsString() { return this.tsIndex; }
}
