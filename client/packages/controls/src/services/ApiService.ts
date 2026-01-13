/**
 * Base API Service
 * Provides HTTP client with axios and authentication handling
 */

import axios, { type AxiosInstance, type AxiosError } from 'axios';
import { getLogger } from '@nimbus/shared';
import { showErrorToast } from '@/utils/toast';
import { configService } from './ConfigService';

const logger = getLogger('ApiService');

export class ApiService {
  private client: AxiosInstance;
  private apiUrl: string;
  private initialized: boolean = false;

  constructor(apiUrl?: string) {
    // Use provided URL or fallback to .env (will be updated after config loads)
    this.apiUrl = apiUrl || import.meta.env.VITE_CONTROL_API_URL || 'http://localhost:9043';

    this.client = axios.create({
      baseURL: this.apiUrl,
      timeout: 30000,
      headers: {
        'Content-Type': 'application/json',
      },
      withCredentials: true, // Enable sending/receiving cookies for cross-origin requests
    });

    // Request interceptor for authentication
    this.client.interceptors.request.use(
      (config) => {
        // Add authentication if needed

        return config;
      },
      (error) => {
        logger.error('Request error', {}, error);
        return Promise.reject(error);
      }
    );

    // Response interceptor for error handling
    this.client.interceptors.response.use(
      (response) => response,
      (error: AxiosError) => {
        const status = error.response?.status;
        const method = error.config?.method?.toUpperCase();

        // Handle network errors (no response from server)
        if (!error.response) {
          logger.error('Network error', {
            url: error.config?.url,
            method: method,
            message: error.message
          }, error);

          // Check if it might be an authentication issue (CORS often fails when not authenticated)
          // Skip redirect if we're on dev-login.html page
          const isDevLogin = window.location.pathname.includes('dev-login.html');

          if (error.message === 'Network Error' && method === 'GET' && !isDevLogin) {
            logger.info('Network error on GET request - might be authentication issue, redirecting to login', {
              url: error.config?.url
            });

            showErrorToast('Cannot connect to server or session expired. Redirecting to login page...', 5000);
            setTimeout(() => {
              window.location.href = './index.html';
            }, 2000);
            return Promise.reject(error);
          }

          return Promise.reject(error);
        }

        logger.error('Response error', {
          url: error.config?.url,
          status: status,
          method: method,
          data: error.response?.data
        }, error);

        // Handle 401 Unauthorized
        // Skip redirect if we're on dev-login.html page
        const isDevLogin = window.location.pathname.includes('dev-login.html');

        if (status === 401) {
          if (method === 'GET' && !isDevLogin) {
            // For GET requests: show info and redirect to index.html (except on dev-login page)
            logger.info('Unauthorized GET request - redirecting to login', {
              url: error.config?.url
            });

            // Show toast before redirect
            showErrorToast('Session expired or not authenticated. Redirecting to login page...', 5000);

            // Redirect to index.html after a short delay
            setTimeout(() => {
              window.location.href = './index.html';
            }, 2000);
          } else {
            // For other methods or dev-login page: show Access Denied
            logger.warn('Access Denied', {
              url: error.config?.url,
              method: method
            });

            // Modify error message
            const accessError: any = error;
            accessError.message = 'Access Denied';
            return Promise.reject(accessError);
          }
        }

        return Promise.reject(error);
      }
    );

    logger.info('ApiService initialized', { apiUrl: this.apiUrl });
  }

  /**
   * Initialize with runtime config
   * Call this after loading config.json
   */
  async initialize(): Promise<void> {
    if (this.initialized) {
      return;
    }

    try {
      const config = await configService.loadConfig();
      this.apiUrl = config.apiUrl;

      // Update axios baseURL
      this.client.defaults.baseURL = this.apiUrl;

      this.initialized = true;
      logger.info('ApiService initialized with runtime config', { apiUrl: this.apiUrl });
    } catch (error) {
      logger.error('Failed to initialize ApiService with runtime config', {}, error as Error);
      // Continue with fallback URL from constructor
      this.initialized = true;
    }
  }

  /**
   * Get axios instance for direct use
   */
  getClient(): AxiosInstance {
    return this.client;
  }

  /**
   * Get API base URL
   */
  getBaseUrl(): string {
    return this.apiUrl;
  }

  /**
   * Get current world ID from URL query parameter
   */
  getCurrentWorldId(): string {
    const params = new URLSearchParams(window.location.search);
    return params.get('world') || '';
  }

  /**
   * Generic GET request
   */
  async get<T>(url: string, params?: any): Promise<T> {
    console.log('[ApiService] GET request', { url, params, fullUrl: `${this.apiUrl}${url}` });
    const response = await this.client.get<T>(url, { params });
    console.log('[ApiService] GET response', { url, status: response.status, data: response.data });
    return response.data;
  }

  /**
   * Generic POST request
   */
  async post<T>(url: string, data?: any, config?: any): Promise<T> {
    const response = await this.client.post<T>(url, data, config);
    return response.data;
  }

  /**
   * Generic PUT request
   */
  async put<T>(url: string, data?: any, config?: any): Promise<T> {
    const response = await this.client.put<T>(url, data, config);
    return response.data;
  }

  /**
   * Generic PATCH request
   */
  async patch<T>(url: string, data?: any, config?: any): Promise<T> {
    const response = await this.client.patch<T>(url, data, config);
    return response.data;
  }

  /**
   * Generic DELETE request
   */
  async delete<T>(url: string): Promise<T> {
    const response = await this.client.delete<T>(url);
    return response.data;
  }

  /**
   * Upload binary data (for assets)
   */
  async uploadBinary<T>(url: string, data: ArrayBuffer | Blob, mimeType?: string): Promise<T> {
    const response = await this.client.post<T>(url, data, {
      headers: {
        'Content-Type': mimeType || 'application/octet-stream',
      },
    });
    return response.data;
  }

  /**
   * Update binary data (for assets)
   */
  async updateBinary<T>(url: string, data: ArrayBuffer | Blob, mimeType?: string): Promise<T> {
    const response = await this.client.put<T>(url, data, {
      headers: {
        'Content-Type': mimeType || 'application/octet-stream',
      },
    });
    return response.data;
  }
}

// Singleton instance
export const apiService = new ApiService();
