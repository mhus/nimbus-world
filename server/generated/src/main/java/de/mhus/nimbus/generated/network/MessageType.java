/*
 * Source TS: MessageTypes.ts
 * Original TS: 'enum MessageType'
 */
package de.mhus.nimbus.generated.network;

public enum MessageType implements de.mhus.nimbus.types.TsEnum {
    LOGIN("login"),
    LOGIN_RESPONSE("loginResponse"),
    LOGOUT("logout"),
    PING("p"),
    WORLD_STATUS_UPDATE("w.su"),
    CHUNK_REGISTER("c.r"),
    CHUNK_QUERY("c.q"),
    CHUNK_UPDATE("c.u"),
    BLOCK_UPDATE("b.u"),
    BLOCK_STATUS_UPDATE("b.s.u"),
    ITEM_BLOCK_UPDATE("b.iu"),
    BLOCK_INTERACTION("b.int"),
    ENTITY_UPDATE("e.u"),
    ENTITY_CHUNK_PATHWAY("e.p"),
    ENTITY_POSITION_UPDATE("e.p.u"),
    ENTITY_INTERACTION("e.int.r"),
    ENTITY_STATUS_UPDATE("e.s.u"),
    EFFECT_TRIGGER("s.t"),
    EFFECT_PARAMETER_UPDATE("s.u"),
    USER_MOVEMENT("u.m"),
    PLAYER_TELEPORT("p.t"),
    INTERACTION_REQUEST("int.r"),
    INTERACTION_RESPONSE("int.rs"),
    CMD("cmd"),
    CMD_MESSAGE("cmd.msg"),
    CMD_RESULT("cmd.rs"),
    SCMD("scmd"),
    SCMD_RESULT("scmd.rs"),
    TEAM_DATA("t.d"),
    TEAM_STATUS("t.s");

    @lombok.Getter
    private final String tsIndex;
    MessageType(String tsIndex) { this.tsIndex = tsIndex; }
    public String tsString() { return this.tsIndex; }
}
