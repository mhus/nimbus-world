import {PlayerInfo, WorldInfo} from "../types";

export enum WEARABLE_SLOT {
    HEAD = 0,
    BODY = 1,
    LEGS = 2,
    FEET = 3,
    HANDS = 4,
    NECK = 5,
    LEFT_RING = 6,
    RIGHT_RING = 7,
    LEFT_WEAPON_1 = 8,
    RIGHT_WEAPON_1 = 9,
    LEFT_WEAPON_2 = 10,
    RIGHT_WEAPON_2 = 11,
}

export interface PlayerBackpack {
    itemIds: Record<string, string>;
    wearingItemIds: Record<WEARABLE_SLOT, string>;
}

export interface Settings {
    name: string;
    inputController: string;
    inputMappings: Record<string, string>;
}

/**
 * Server connection information
 * Provides WebSocket URL for game server connection
 */
export interface ServerInfo {
    /** WebSocket URL for game server connection (e.g., "ws://game-server:9042/ws") */
    websocketUrl: string;
}

export interface EngineConfiguration {

    /** Server connection information */
    serverInfo: ServerInfo;

    worldInfo: WorldInfo;

    playerInfo: PlayerInfo;
    playerBackpack: PlayerBackpack;

    settings: Settings;

}