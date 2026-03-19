import {PlayerInfo, WorldInfo} from "../types";

export enum WEARABLE_SLOT {
    HEAD = 0,
    BODY = 1,
    LEGS = 2,
    FEET = 3,
    NECK = 4,
    LEFT_RING = 5,
    RIGHT_RING = 6,
    LEFT_HAND_1 = 7,
    RIGHT_HAND_1 = 8,
    LEFT_HAND_2 = 9,
    RIGHT_HAND_2 = 10,
    ARMS = 11,
}

export enum WEARABLE_GROUP {
    HEAD = 0,
    BODY = 1,
    LEGS = 2,
    FEET = 3,
    NECK = 4,
    RING = 5,
    HAND = 6,
    ARMS = 7,
}

export interface PlayerBackpack {
    itemIds: Record<string, number>; // javaType: java.util.Map<String,Integer>
    wearingItemIds: Record<WEARABLE_SLOT, string>;
}

export interface Settings {
    name: string;
    inputController: string;
    inputMappings: Record<string, string>;
    properties: Record<string, string>;
}

/**
 * Server connection information
 * Provides WebSocket URL for game server connection
 */
export interface ServerInfo {
    /** WebSocket URL for game server connection (e.g., "ws://game-server:9042/ws") */
    websocketUrl: string;
    /** Exit/logout URL override from server (based on login source). Optional. */
    exitUrl?: string;
}

export interface EngineConfiguration {

    /** Server connection information */
    serverInfo: ServerInfo;

    worldInfo: WorldInfo;

    playerInfo: PlayerInfo;
    playerBackpack: PlayerBackpack;

    settings: Settings;

}