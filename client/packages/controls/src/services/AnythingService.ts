/**
 * Anything Service
 * Manages WAnything CRUD operations.
 * All operations are scoped by worldId. Region scoping uses worldId format "@region:regionId".
 */

import { apiService } from './ApiService';
import type { WAnything } from '@nimbus/shared/generated/entities/WAnything';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('AnythingService');

export interface ListAnythingParams {
  worldId: string;
  collection: string;
  type?: string;
  enabledOnly?: boolean;
  offset?: number;
  limit?: number;
}

export interface ListAnythingResponse {
  entities: WAnything[];
  count: number;
  limit: number;
  offset: number;
}

export interface CreateAnythingRequest {
  worldId: string;
  collection: string;
  name: string;
  title?: string;
  description?: string;
  type?: string;
  data?: any;
}

export interface UpdateAnythingRequest {
  title?: string;
  description?: string;
  type?: string;
  data?: any;
  enabled?: boolean;
}

export interface GetCollectionsResponse {
  collections: string[];
  count: number;
}

export class AnythingService {
  /**
   * Get distinct collection names for a world
   */
  async getCollections(worldId: string): Promise<GetCollectionsResponse> {
    logger.debug('Getting collections', { worldId });

    const queryParams = new URLSearchParams();
    queryParams.append('worldId', worldId);

    const response = await apiService.get<GetCollectionsResponse>(`/control/anything/collections?${queryParams.toString()}`);
    logger.debug('Got collections', { count: response.count });
    return response;
  }

  /**
   * List entities with filtering
   */
  async list(params: ListAnythingParams): Promise<ListAnythingResponse> {
    logger.debug('Listing entities', { params });

    const queryParams = new URLSearchParams();
    queryParams.append('worldId', params.worldId);
    queryParams.append('collection', params.collection);

    if (params.type) queryParams.append('type', params.type);
    if (params.enabledOnly !== undefined) queryParams.append('enabledOnly', String(params.enabledOnly));
    if (params.offset !== undefined) queryParams.append('offset', String(params.offset));
    if (params.limit !== undefined) queryParams.append('limit', String(params.limit));

    const response = await apiService.get<ListAnythingResponse>(`/control/anything/list?${queryParams.toString()}`);
    logger.debug('Listed entities', { count: response.count });
    return response;
  }

  /**
   * Get entity by worldId, collection, and name
   */
  async get(worldId: string, collection: string, name: string): Promise<WAnything> {
    logger.debug('Getting entity', { worldId, collection, name });

    const queryParams = new URLSearchParams();
    queryParams.append('worldId', worldId);
    queryParams.append('collection', collection);
    queryParams.append('name', name);

    const response = await apiService.get<WAnything>(`/control/anything/by-world?${queryParams.toString()}`);
    logger.debug('Got entity', { name: response.name, collection: response.collection });
    return response;
  }

  /**
   * Create new entity
   */
  async create(request: CreateAnythingRequest): Promise<WAnything> {
    logger.debug('Creating entity', { request });

    const response = await apiService.post<WAnything>('/control/anything', request);
    logger.info('Created entity', { collection: response.collection, name: response.name });
    return response;
  }

  /**
   * Update entity by ID
   */
  async update(id: string, request: UpdateAnythingRequest): Promise<WAnything> {
    logger.debug('Updating entity', { id, request });

    const response = await apiService.put<WAnything>(`/control/anything/${id}`, request);
    logger.info('Updated entity', { id, name: response.name });
    return response;
  }

  /**
   * Delete entity by worldId, collection, and name
   */
  async delete(worldId: string, collection: string, name: string): Promise<void> {
    logger.debug('Deleting entity', { worldId, collection, name });

    const queryParams = new URLSearchParams();
    queryParams.append('worldId', worldId);
    queryParams.append('collection', collection);
    queryParams.append('name', name);

    await apiService.delete(`/control/anything/by-world?${queryParams.toString()}`);
    logger.info('Deleted entity', { worldId, collection, name });
  }
}

// Export singleton instance
export const anythingService = new AnythingService();
