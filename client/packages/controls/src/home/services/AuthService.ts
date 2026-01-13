import { apiService } from '@/services/ApiService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('AuthService');

export interface AuthStatus {
  authenticated: boolean;
  agent: boolean;
  worldId: string | null;
  userId: string | null;
  characterId: string | null;
  actor: string | null;
  roles: string[];
  sessionId: string | null;
  accessUrls: string[];
  loginUrl: string;
  logoutUrl: string | null;
}

/**
 * Service for checking authentication status and roles
 */
class AuthService {
  /**
   * Get authentication status and user roles
   */
  async getStatus(): Promise<AuthStatus> {
    try {
      const response = await apiService.post<{
        authenticated: boolean;
        agent?: boolean;
        worldId?: string | null;
        userId?: string | null;
        characterId?: string | null;
        actor?: string | null;
        roles?: string[];
        sessionId?: string | null;
        accessUrls?: string[];
        loginUrl?: string;
        logoutUrl?: string | null;
      }>('/control/aaa/status', {});

      return {
        authenticated: response.authenticated || false,
        agent: response.agent || false,
        worldId: response.worldId || null,
        userId: response.userId || null,
        characterId: response.characterId || null,
        actor: response.actor || null,
        roles: response.roles || [],
        sessionId: response.sessionId || null,
        accessUrls: response.accessUrls || [],
        loginUrl: response.loginUrl || 'dev-login.html',
        logoutUrl: response.logoutUrl || null,
      };
    } catch (error) {
      logger.error('Failed to get auth status', {}, error instanceof Error ? error : undefined);

      // If request fails, assume not authenticated
      return {
        authenticated: false,
        agent: false,
        worldId: null,
        userId: null,
        characterId: null,
        actor: null,
        roles: [],
        sessionId: null,
        accessUrls: [],
        loginUrl: 'dev-login.html',
        logoutUrl: null,
      };
    }
  }
}

export const authService = new AuthService();
