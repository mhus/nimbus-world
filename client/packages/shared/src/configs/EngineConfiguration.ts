import {PlayerInfo, WorldInfo} from "../types";

export enum WEARABLE_SLOT {
    HEAD = 'HEAD',
    BODY = 'BODY',
    LEGS = 'LEGS',
    FEET = 'FEET',
    NECK = 'NECK',
    LEFT_RING = 'LEFT_RING',
    RIGHT_RING = 'RIGHT_RING',
    LEFT_HAND_1 = 'LEFT_HAND_1',
    RIGHT_HAND_1 = 'RIGHT_HAND_1',
    LEFT_HAND_2 = 'LEFT_HAND_2',
    RIGHT_HAND_2 = 'RIGHT_HAND_2',
    ARMS = 'ARMS',
}

export enum WEARABLE_GROUP {
    HEAD = 'HEAD',
    BODY = 'BODY',
    LEGS = 'LEGS',
    FEET = 'FEET',
    NECK = 'NECK',
    RING = 'RING',
    HAND = 'HAND',
    ARMS = 'ARMS',
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