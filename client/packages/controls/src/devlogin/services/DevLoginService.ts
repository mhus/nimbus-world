import { apiService } from '@/services/ApiService';

// ===== Interfaces =====

export interface World {
  worldId: string;
  name: string;
  description?: string;
  regionId: string;
  enabled: boolean;
  publicFlag: boolean;
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

export type ActorType = 'PLAYER' | 'EDITOR' | 'SUPPORT';

export interface SessionLoginRequest {
  worldId: string;
  agent: false;
  userId: string;
  characterId: string;
  actor: ActorType;
  entryPoint?: string;  // Optional: "last", "grid:q,r", or "world"
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

class DevLoginService {
  /**
   * Get list of available worlds with optional search filter
   */
  async getWorlds(searchQuery?: string, limit: number = 100): Promise<World[]> {
    const params: any = { limit };
    if (searchQuery) {
      params.search = searchQuery;
    }
    return apiService.get<World[]>('/control/aaa/devlogin', params);
  }

  /**
   * Get list of users with search filter
   */
  async getUsers(searchQuery?: string, limit: number = 100): Promise<User[]> {
    const params: any = { limit };
    if (searchQuery) {
      params.search = searchQuery;
    }
    return apiService.get<User[]>('/control/aaa/devlogin/users', params);
  }

  /**
   * Get characters for a user in a world
   */
  async getCharacters(userId: string, worldId: string): Promise<Character[]> {
    const params = { userId, worldId };
    return apiService.get<Character[]>('/control/aaa/devlogin/characters', params);
  }

  /**
   * Perform login (session or agent)
   */
  async login(request: LoginRequest): Promise<LoginResponse> {
    return apiService.post<LoginResponse>('/control/aaa/devlogin', request);
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
