/**
 * Chunk-related messages
 *
 * Chunks are always columns with X and Z coordinates.
 * Y-direction is always delivered and rendered completely.
 */

import type { BaseMessage } from '../BaseMessage';
import type { Block } from '../../types/Block';
import type { AreaData } from '../../types/AreaData';
import type { HeightData } from '../../types/ChunkData';
import type { Backdrop } from '../../types/Backdrop';
import {ItemBlockRef} from "../../types";

/**
 * Chunk coordinates (XZ only, Y is complete column)
 */
export interface ChunkCoordinate {
  cx: number; // javaType: int
  cz: number; // javaType: int
}

/**
 * Chunk data transfer object
 */
export interface ChunkDataTransferObject {
  /** Chunk X coordinate */
  cx: number; // javaType: int

  /** Chunk Z coordinate */
  cz: number; // javaType: int

  /** Block data */
  b: Block[];

  /** Item data - Item instances with position, itemType reference, and modifiers */
  i?: ItemBlockRef[];

  /** Height data, maximum height */
  h?: HeightData[]; // javaType: int[][]

  /** Area data with effects */
  a?: AreaData[];

  /** Backdrop data for chunk edges */
  backdrop?: {
    /** North side backdrop items */
    n?: Array<Backdrop>;
    /** East side backdrop items */
    e?: Array<Backdrop>;
    /** South side backdrop items */
    s?: Array<Backdrop>;
    /** West side backdrop items */
    w?: Array<Backdrop>;
  };

  c?: Uint8Array; // javaType: byte[]
}

/**
 * Chunk registration (Client -> Server)
 * Client registers chunks it wants to receive
 * All non-listed chunks will no longer be sent by server
 */
export interface ChunkRegisterData {
    cx: number; // javaType: int
    cz: number; // javaType: int
    hr: number; // javaType: int
    lr: number; // javaType: int
}

export type ChunkRegisterMessage = BaseMessage<ChunkRegisterData>;

/**
 * Chunk query/request (Client -> Server)
 * Client explicitly requests specific chunks
 */
export interface ChunkQueryData {
  c: ChunkCoordinate[];
}

export type ChunkQueryMessage = BaseMessage<ChunkQueryData>;

/**
 * Chunk update (Server -> Client)
 * Server sends requested or updated chunks
 */
export type ChunkUpdateMessage = BaseMessage<ChunkDataTransferObject[]>;
