import { apiService } from '@/services/ApiService';

// ===== Interfaces =====

export interface EpochMeta {
  epoch: number;
  name: string;
  description: string;
}

export interface World {
  worldId: string;
  name: string;
  description?: string;
  regionId: string;
  enabled: boolean;
  publicFlag: boolean;
  epoches: EpochMeta[];
}

export interface Character {
  id: string;
  userId: string;
  name: string;
  display?: string;
  regionId: string;
}

export interface User {
  id: string;
  username: string;
  email?: string;
  enabled: boolean;
}

export interface WorldInstance {
  instanceId: string;
  worldId: string;
  title: string;
  description?: string;
  creator: string;
  players: string[];
  enabled: boolean;
  accessType?: string;
  durationType?: string;
  createdAt?: string;
}

export type ActorType = 'PLAYER' | 'EDITOR' | 'SUPPORT';

export interface SessionLoginRequest {
  worldId: string;
  agent: false;
  userId: string;
  characterId: string;
  actor: ActorType;
  entryPoint?: string;  // Optional: "last", "grid:q,r", or "world"
  instanceId?: string;  // Optional: existing instance ID for PLAYER rejoining
}

export interface AgentLoginRequest {
  worldId: string;
  agent: true;
  userId: string;
}

export type LoginRequest = SessionLoginRequest | AgentLoginRequest;

export interface LoginResponse {
  accessToken: string;
  accessUrls: string[];
  jumpUrl: string;
  sessionId?: string;
  playerId?: string;
}

// ===== Service Class =====

const ACCESS_KEY_HEADER = 'X-DevLogin-Key';

class DevLoginService {
  private accessKey: string = '';

  setAccessKey(key: string): void {
    this.accessKey = key ?? '';
  }

  private buildConfig(params?: any): any {
    const config: any = {};
    if (params !== undefined) config.params = params;
    if (this.accessKey) {
      config.headers = { [ACCESS_KEY_HEADER]: this.accessKey };
    }
    return config;
  }

  private async get<T>(url: string, params?: any): Promise<T> {
    const response = await apiService.getClient().get<T>(url, this.buildConfig(params));
    return response.data;
  }

  /**
   * Get list of available worlds with optional search filter
   */
  async getWorlds(searchQuery?: string, limit: number = 100): Promise<World[]> {
    const params: any = { limit };
    if (searchQuery) {
      params.search = searchQuery;
    }
    return this.get<World[]>('/control/aaa/devlogin', params);
  }

  /**
   * Get list of users with search filter
   */
  async getUsers(searchQuery?: string, limit: number = 100): Promise<User[]> {
    const params: any = { limit };
    if (searchQuery) {
      params.search = searchQuery;
    }
    return this.get<User[]>('/control/aaa/devlogin/users', params);
  }

  /**
   * Get characters for a user in a world
   */
  async getCharacters(userId: string, worldId: string): Promise<Character[]> {
    const params = { userId, worldId };
    return this.get<Character[]>('/control/aaa/devlogin/characters', params);
  }

  /**
   * Get zones for a main world
   */
  async getZones(worldId: string): Promise<World[]> {
    return this.get<World[]>('/control/aaa/devlogin/zones', { worldId });
  }

  /**
   * Get instances for a player in a world.
   * If all=true, returns all instances (for SUPPORT actors).
   */
  async getInstances(worldId: string, playerId?: string, all: boolean = false): Promise<WorldInstance[]> {
    const params: any = { worldId };
    if (playerId) params.playerId = playerId;
    if (all) params.all = true;
    return this.get<WorldInstance[]>('/control/aaa/devlogin/instances', params);
  }

  /**
   * Perform login (session or agent)
   */
  async login(request: LoginRequest): Promise<LoginResponse> {
    return apiService.post<LoginResponse>('/control/aaa/devlogin', request, this.buildConfig());
  }

  /**
   * Authorize with cookie URLs
   * Makes requests to each URL to set authentication cookies
   * At least one URL must succeed for authorization to succeed
   */
  async authorize(accessUrls: string[], accessToken: string): Promise<void> {
    const authPromises = accessUrls.map(async (url) => {
      try {
        const response = await fetch(`${url}?token=${accessToken}`, {
          method: 'GET',
          credentials: 'include', // Important: allows setting cookies cross-origin
          mode: 'cors',
        });
        return { url, success: response.ok, status: response.status };
      } catch (error) {
        console.warn(`[DevLogin] Failed to authorize with ${url}:`, error);
        return { url, success: false, error };
      }
    });

    const results = await Promise.all(authPromises);

    // Check if at least one URL succeeded
    const successfulResults = results.filter(r => r.success);

    if (successfulResults.length === 0) {
      const failedUrls = results.map(r => `${r.url} (${r.status || 'error'})`).join(', ');
      throw new Error(`Authorization failed: All URLs failed. Failed URLs: ${failedUrls}`);
    }

    console.log(`[DevLogin] Authorization successful: ${successfulResults.length}/${results.length} URLs succeeded`);
  }
}

export const devLoginService = new DevLoginService();
