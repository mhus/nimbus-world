/**
 * Additional functionality for BlockInstanceEditor to save custom blocks as BlockTypes
 */

import { apiService } from '@/services/ApiService';

/**
 * Saves the current block as a new BlockType template
 */
export async function saveBlockAsBlockType(
  worldId: string,
  newBlockTypeId: string,
  blockData: any
): Promise<{ success: boolean; error?: string; blockId?: string }> {
  try {
    const apiUrl = apiService.getBaseUrl();
    const url = `${apiUrl}/control/worlds/${worldId}/blocktypes/fromBlock/${encodeURIComponent(newBlockTypeId)}`;

    // Send the block data as payload
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(blockData),
      credentials: 'include'
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({ error: response.statusText }));
      return {
        success: false,
        error: errorData.error || `Failed to save BlockType: ${response.statusText}`,
      };
    }

    const result = await response.json();
    return {
      success: true,
      blockId: result.blockId,
    };
  } catch (err) {
    return {
      success: false,
      error: err instanceof Error ? err.message : 'Unknown error occurred',
    };
  }
}

/**
 * Generates URL to BlockType editor for given blockTypeId
 */
export function getBlockTypeEditorUrl(blockTypeId: string, worldId: string, sessionId?: string): string {
  const params = new URLSearchParams();
  params.set('world', worldId);
  if (sessionId) {
    params.set('sessionId', sessionId);
  }
  params.set('id', blockTypeId);

  return `blocktype-editor.html?${params.toString()}`;
}
