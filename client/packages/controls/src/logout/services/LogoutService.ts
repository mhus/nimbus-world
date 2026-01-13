import { apiService } from '@/services/ApiService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('LogoutService');

export interface LogoutUrls {
  accessUrls: string[];
  loginUrl: string;
}

/**
 * Service for handling logout operations
 */
class LogoutService {
  /**
   * Get logout URLs from status endpoint
   */
  async getLogoutUrls(): Promise<LogoutUrls> {
    try {
      const response = await apiService.post<{
        authenticated: boolean;
        accessUrls: string[];
        loginUrl: string;
      }>('/control/aaa/status', {});

      if (!response.authenticated) {
        throw new Error('Not authenticated');
      }
      if (!response.accessUrls || response.accessUrls.length === 0) {
        throw new Error('No logout URLs received from server');
      }
      return {
        accessUrls: response.accessUrls,
        loginUrl: response.loginUrl || 'dev-login.html',
      };
    } catch (error) {
      logger.error('Failed to get logout URLs', {}, error instanceof Error ? error : undefined);
      throw new Error('Failed to get logout URLs: ' + (error instanceof Error ? error.message : 'Unknown error'));
    }
  }

  /**
   * Perform logout by calling DELETE on all logout URLs
   */
  async logout(accessUrls: string[]): Promise<void> {
    try {
      // Call DELETE on each logout URL to clear cookies
      const logoutPromises = accessUrls.map(url =>
        fetch(url, {
          method: 'DELETE',
          credentials: 'include', // Important: send cookies to be deleted
          mode: 'cors',
        }).then(response => {
          if (!response.ok) {
            logger.warn('Logout request failed', { url, status: response.status });
          } else {
            logger.debug('Logout successful for URL', { url });
          }
          return response;
        })
      );

      await Promise.all(logoutPromises);

      logger.info('All logout requests completed');
    } catch (error) {
      logger.error('Logout failed', {error});
      throw new Error('Logout failed: ' + (error instanceof Error ? error.message : 'Unknown error'));
    }
  }
}

export const logoutService = new LogoutService();
