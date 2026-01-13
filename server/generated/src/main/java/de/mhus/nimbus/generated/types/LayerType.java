/*
 * Source TS: WLayer.ts
 * Original TS: 'enum LayerType'
 */
package de.mhus.nimbus.generated.types;

public enum LayerType implements de.mhus.nimbus.types.TsEnum {
    TERRAIN("TERRAIN"),
    MODEL("MODEL");

    @lombok.Getter
    private final String tsIndex;
    LayerType(String tsIndex) { this.tsIndex = tsIndex; }
    public String tsString() { return this.tsIndex; }
}
