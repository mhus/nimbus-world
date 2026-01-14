/**
 * useDocuments Composable
 * Manages document list and operations
 */

import { ref } from 'vue';
import type { DocumentMetadata, Document } from '../services/DocumentService';
import { documentService } from '../services/DocumentService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('useDocuments');

export function useDocuments(worldId: string) {
  const documents = ref<DocumentMetadata[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  /**
   * Load documents by collection (optional filter)
   * If no collection is specified, loads all documents
   */
  const loadDocuments = async (collection?: string) => {
    logger.info('loadDocuments: START', {
      worldId,
      collection: collection || 'undefined',
      loading: loading.value
    });

    loading.value = true;
    error.value = null;

    try {
      const params: any = {};
      if (collection) {
        params.collection = collection;
      }

      logger.info('loadDocuments: calling documentService.getDocuments', {
        worldId,
        params
      });

      const response = await documentService.getDocuments(worldId, params);

      logger.info('loadDocuments: received response', {
        count: response.documents.length,
        totalCount: response.count
      });

      documents.value = response.documents;

      logger.info('Loaded documents', {
        count: documents.value.length,
        collection: collection || 'all',
        worldId,
      });
    } catch (err) {
      error.value = 'Failed to load documents';
      logger.error('Failed to load documents', { worldId, collection }, err as Error);
    } finally {
      loading.value = false;
      logger.info('loadDocuments: END (loading=false)');
    }
  };

  /**
   * Create new document
   */
  const createDocument = async (data: {
    collection: string;
    title?: string;
    name?: string;
    type?: string;
    language?: string;
    format?: string;
    content?: string;
    summary?: string;
    isMain?: boolean;
  }) => {
    try {
      loading.value = true;
      error.value = null;

      await documentService.createDocument(worldId, data);

      logger.info('Created document', { worldId, collection: data.collection });

      // Reload documents
      await loadDocuments(data.collection);
    } catch (err) {
      error.value = 'Failed to create document';
      logger.error('Failed to create document', { worldId }, err as Error);
      throw err;
    } finally {
      loading.value = false;
    }
  };

  /**
   * Update document
   */
  const updateDocument = async (
    collection: string,
    documentId: string,
    data: {
      title?: string;
      name?: string;
      type?: string;
      language?: string;
      format?: string;
      content?: string;
      summary?: string;
      isMain?: boolean;
    }
  ) => {
    try {
      loading.value = true;
      error.value = null;

      await documentService.updateDocument(worldId, collection, documentId, data);

      logger.info('Updated document', { worldId, collection, documentId });

      // Reload documents
      await loadDocuments(collection);
    } catch (err) {
      error.value = 'Failed to update document';
      logger.error('Failed to update document', { worldId, documentId }, err as Error);
      throw err;
    } finally {
      loading.value = false;
    }
  };

  /**
   * Delete document
   */
  const deleteDocument = async (collection: string, documentId: string) => {
    try {
      loading.value = true;
      error.value = null;

      await documentService.deleteDocument(worldId, collection, documentId);

      logger.info('Deleted document', { worldId, collection, documentId });

      // Reload documents
      await loadDocuments(collection);
    } catch (err) {
      error.value = 'Failed to delete document';
      logger.error('Failed to delete document', { worldId, documentId }, err as Error);
      throw err;
    } finally {
      loading.value = false;
    }
  };

  /**
   * Get single document
   */
  const getDocument = async (collection: string, documentId: string): Promise<Document | null> => {
    try {
      return await documentService.getDocument(worldId, collection, documentId);
    } catch (err) {
      logger.error('Failed to get document', { worldId, collection, documentId }, err as Error);
      return null;
    }
  };

  return {
    documents,
    loading,
    error,
    loadDocuments,
    createDocument,
    updateDocument,
    deleteDocument,
    getDocument,
  };
}
