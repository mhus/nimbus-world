import { apiService } from '@/services/ApiService';

export interface Collection {
  id: string;
  worldId: string;
  title: string;
  description: string;
  createdAt: string;
  updatedAt: string;
  enabled: boolean;
}

export interface CollectionRequest {
  worldId: string;
  title: string;
  description?: string;
}

class CollectionServiceFrontend {
  async listCollections(): Promise<Collection[]> {
    return apiService.get<Collection[]>('/control/collections');
  }

  async getCollection(worldId: string): Promise<Collection> {
    return apiService.get<Collection>(`/control/collections/${worldId}`);
  }

  async createCollection(request: CollectionRequest): Promise<Collection> {
    return apiService.post<Collection>('/control/collections', request);
  }

  async updateCollection(worldId: string, request: CollectionRequest): Promise<Collection> {
    return apiService.put<Collection>(`/control/collections/${worldId}`, request);
  }

  async deleteCollection(worldId: string): Promise<void> {
    return apiService.delete<void>(`/control/collections/${worldId}`);
  }
}

export const collectionServiceFrontend = new CollectionServiceFrontend();
