import { apiService } from '@/services/ApiService';

// RGB Color object
export interface RGBColor {
  r: number;
  g: number;
  b: number;
}

// WorldInfo owner
export interface WorldInfoOwner {
  user: string;
  title: string;
  email?: string;
}

// Environment settings (highly nested)
export interface WorldInfoEnvironment {
  clearColor?: RGBColor;
  cameraMaxZ?: number;
  sunEnabled?: boolean;
  sunTexture?: string;
  sunSize?: number;
  sunAngleY?: number;
  sunElevation?: number;
  sunColor?: RGBColor;
  skyBoxEnabled?: boolean;
  skyBoxMode?: string;
  skyBoxColor?: RGBColor;
  skyBoxTexturePath?: string;
}

// WorldTime settings
export interface WorldInfoWorldTime {
  minuteScaling?: number;
  minutesPerHour?: number;
  hoursPerDay?: number;
  daysPerMonth?: number;
  monthsPerYear?: number;
  currentEra?: number;
  linuxEpocheDeltaMinutes?: number;
}

// WorldInfo settings (nested structure)
export interface WorldInfoSettings {
  maxPlayers?: number;
  allowGuests?: boolean;
  pvpEnabled?: boolean;
  pingInterval?: number;
  allowedMovementModes?: string[];
  defaultMovementMode?: string;
  deadAmbientAudio?: string;
  swimStepAudio?: string;
  environment?: WorldInfoEnvironment;
  worldTime?: WorldInfoWorldTime;
}

// Vector3 position
export interface Vector3 {
  x: number;
  y: number;
  z: number;
}

// Main WorldInfo interface
export interface WorldInfo {
  worldId?: string;
  name?: string;
  description?: string;
  chunkSize?: number;
  hexGridSize?: number;
  worldIcon?: string;
  status?: number;
  seasonStatus?: number;
  seasonProgress?: number;
  createdAt?: string;
  updatedAt?: string;
  editorUrl?: string;
  splashScreen?: string;
  splashScreenAudio?: string;
  start?: Vector3;
  stop?: Vector3;
  owner?: WorldInfoOwner;
  settings?: WorldInfoSettings;
  [key: string]: any;  // Allow additional fields
}

export interface World {
  id: string;
  worldId: string;
  regionId: string;
  name: string;
  description: string;
  publicData: WorldInfo;
  createdAt: string;
  updatedAt: string;
  enabled: boolean;
  parent: string;
  instanceable: boolean;
  groundLevel: number;
  waterLevel: number | null;
  groundBlockType: string;
  waterBlockType: string;
  owner: string[];
  editor: string[];
  supporter: string[];
  player: string[];
  publicFlag: boolean;
}

export interface WorldRequest {
  worldId: string;
  name: string;
  description?: string;
  publicData?: WorldInfo;
  enabled?: boolean;
  parent?: string;
  instanceable?: boolean;
  owner?: string[];
  editor?: string[];
  supporter?: string[];
  player?: string[];
  groundLevel?: number;
  waterLevel?: number;
  groundBlockType?: string;
  waterBlockType?: string;
}

class WorldServiceFrontend {
  async listWorlds(regionId: string): Promise<World[]> {
    return apiService.get<World[]>(`/control/regions/${regionId}/worlds`);
  }

  async getWorld(regionId: string, worldId: string): Promise<World> {
    return apiService.get<World>(`/control/regions/${regionId}/worlds/${worldId}`);
  }

  async createWorld(regionId: string, request: WorldRequest): Promise<World> {
    return apiService.post<World>(`/control/regions/${regionId}/worlds`, request);
  }

  async updateWorld(regionId: string, worldId: string, request: WorldRequest): Promise<World> {
    return apiService.put<World>(`/control/regions/${regionId}/worlds/${worldId}`, request);
  }

  async deleteWorld(regionId: string, worldId: string): Promise<void> {
    return apiService.delete<void>(`/control/regions/${regionId}/worlds/${worldId}`);
  }

  async createZone(regionId: string, sourceWorldId: string, zoneName: string): Promise<World> {
    return apiService.post<World>(
      `/control/regions/${regionId}/worlds/${sourceWorldId}/zones`,
      { zoneName }
    );
  }
}

export const worldServiceFrontend = new WorldServiceFrontend();
