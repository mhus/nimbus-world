/*
 * Source TS: AnimationData.ts
 * Original TS: 'enum AnimationEffectType'
 */
package de.mhus.nimbus.generated.types;

public enum AnimationEffectType implements de.mhus.nimbus.types.TsEnum {
    SCALE("scale"),
    ROTATE("rotate"),
    TRANSLATE("translate"),
    COLOR_CHANGE("colorChange"),
    FADE("fade"),
    FLASH("flash"),
    PROJECTILE("projectile"),
    EXPLOSION("explosion"),
    PARTICLES("particles"),
    SPAWN_ENTITY("spawnEntity"),
    SKY_CHANGE("skyChange"),
    LIGHT_CHANGE("lightChange"),
    CAMERA_SHAKE("cameraShake"),
    PLAY_SOUND("playSound"),
    BLOCK_BREAK("blockBreak"),
    BLOCK_PLACE("blockPlace"),
    BLOCK_CHANGE("blockChange");

    @lombok.Getter
    private final String tsIndex;
    AnimationEffectType(String tsIndex) { this.tsIndex = tsIndex; }
    public String tsString() { return this.tsIndex; }
}
