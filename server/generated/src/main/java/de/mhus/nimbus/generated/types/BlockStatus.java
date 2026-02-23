/*
 * Source TS: BlockType.ts
 * Original TS: 'enum BlockStatus'
 */
package de.mhus.nimbus.generated.types;

public enum BlockStatus implements de.mhus.nimbus.types.TsEnum {
    DEFAULT("default"),
    OPEN("open"),
    CLOSED("closed"),
    LOCKED("locked"),
    DESTROYED("destroyed"),
    WINTER("winter"),
    SPRING("spring"),
    SUMMER("summer"),
    AUTUMN("autumn");

    @lombok.Getter
    private final String tsIndex;
    BlockStatus(String tsIndex) { this.tsIndex = tsIndex; }
    public String tsString() { return this.tsIndex; }
}
