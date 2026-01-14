/**
 * Document Service
 * Manages document CRUD operations
 */

import { apiService } from './ApiService';
import type { WDocumentMetadata } from '@nimbus/shared/generated/dto/WDocumentMetadata';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('DocumentService');

export interface Document {
  documentId: string;
  name?: string;
  title?: string;
  collection: string;
  language?: string;
  format?: string;
  content?: string;
  summary?: string;
  metadata?: Record<string, string>;
  parentDocumentId?: string;
  isMain: boolean;
  hash?: string;
  type?: string;
  childType?: string;
  worldId: string;
  createdAt: string;
  updatedAt: string;
}

// DocumentMetadata is Document without the large content field
export type DocumentMetadata = WDocumentMetadata;

export interface DocumentListResponse {
  documents: DocumentMetadata[];
  count: number;
  limit: number;
  offset: number;
}

export interface DocumentCreateRequest {
  collection: string;
  name?: string;
  title?: string;
  language?: string;
  format?: string;
  content?: string;
  summary?: string;
  metadata?: Record<string, string>;
  parentDocumentId?: string;
  isMain?: boolean;
  type?: string;
  childType?: string;
}

export interface DocumentUpdateRequest {
  name?: string;
  title?: string;
  language?: string;
  format?: string;
  content?: string;
  summary?: string;
  metadata?: Record<string, string>;
  parentDocumentId?: string;
  isMain?: boolean;
  hash?: string;
  type?: string;
  childType?: string;
}

export interface DocumentPagingParams {
  collection?: string;
  type?: string;
  offset?: number;
  limit?: number;
}

export class DocumentService {
  /**
   * Get all documents or search with pagination
   */
  async getDocuments(
    worldId: string,
    params?: DocumentPagingParams
  ): Promise<DocumentListResponse> {
    const url = `/control/worlds/${worldId}/documents`;
    logger.info('getDocuments: calling API', { url, worldId, params });

    const response = await apiService.get<DocumentListResponse>(url, params);

    logger.info('getDocuments: received response', {
      url,
      documentsCount: response.documents?.length || 0,
      totalCount: response.count
    });

    return response;
  }

  /**
   * Get single document by ID
   */
  async getDocument(worldId: string, collection: string, documentId: string): Promise<Document> {
    return apiService.get<Document>(
      `/control/worlds/${worldId}/documents/${collection}/${documentId}`
    );
  }

  /**
   * Create new document
   */
  async createDocument(worldId: string, request: DocumentCreateRequest): Promise<{ documentId: string; message: string }> {
    return apiService.post(
      `/control/worlds/${worldId}/documents`,
      request
    );
  }

  /**
   * Update existing document
   */
  async updateDocument(
    worldId: string,
    collection: string,
    documentId: string,
    request: DocumentUpdateRequest
  ): Promise<Document> {
    return apiService.put(
      `/control/worlds/${worldId}/documents/${collection}/${documentId}`,
      request
    );
  }

  /**
   * Delete document
   */
  async deleteDocument(worldId: string, collection: string, documentId: string): Promise<void> {
    return apiService.delete(
      `/control/worlds/${worldId}/documents/${collection}/${documentId}`
    );
  }

  /**
   * Lookup documents from multiple sources
   */
  async lookupDocuments(worldId: string, collection: string): Promise<{ documents: Document[]; count: number }> {
    return apiService.get(
      `/control/worlds/${worldId}/documents/lookup/${collection}`
    );
  }
}

// Singleton instance
export const documentService = new DocumentService();
