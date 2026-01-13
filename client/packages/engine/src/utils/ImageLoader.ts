/**
 * ImageLoader Utility
 *
 * Provides utilities for loading images and audio with proper credential handling
 * for authenticated asset requests.
 */

import { getLogger } from '@nimbus/shared';

const logger = getLogger('AssetLoader');

/**
 * Load an image from URL with credentials included
 *
 * This function uses fetch with credentials: 'include' to ensure
 * authentication cookies are sent with the request, then converts
 * the response to a blob URL that can be used with the Image API.
 *
 * @param url Image URL to load
 * @returns Promise resolving to HTMLImageElement
 */
export async function loadImageWithCredentials(url: string): Promise<HTMLImageElement> {
  try {
    // Fetch the image with credentials included
    const response = await fetch(url, {
      credentials: 'include',
      mode: 'cors',
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    // Convert response to blob
    const blob = await response.blob();

    // Create object URL from blob
    const blobUrl = URL.createObjectURL(blob);

    // Load image from blob URL
    return new Promise((resolve, reject) => {
      const img = new Image();

      img.onload = () => {
        // Clean up blob URL after image is loaded
        URL.revokeObjectURL(blobUrl);
        resolve(img);
      };

      img.onerror = (e) => {
        // Clean up blob URL on error
        URL.revokeObjectURL(blobUrl);
        const error = e instanceof Error ? e : new Error('Failed to load image');
        reject(error);
      };

      img.src = blobUrl;
    });
  } catch (error) {
    logger.error('Failed to load image with credentials', { url }, error as Error);
    throw new Error(`Failed to load image: ${url}`);
  }
}

/**
 * Load a texture URL for Babylon.js with credentials
 *
 * Similar to loadImageWithCredentials, but returns a blob URL
 * that can be used directly with Babylon.js Texture constructor.
 * The caller is responsible for revoking the blob URL when done.
 *
 * @param url Texture URL to load
 * @returns Promise resolving to blob URL
 */
export async function loadTextureUrlWithCredentials(url: string): Promise<string> {
  try {
    // Fetch the texture with credentials included
    const response = await fetch(url, {
      credentials: 'include',
      mode: 'cors',
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    // Convert response to blob
    const blob = await response.blob();

    // Create and return object URL from blob
    // Note: Caller must revoke this URL when done using URL.revokeObjectURL()
    return URL.createObjectURL(blob);
  } catch (error) {
    logger.error('Failed to load texture URL with credentials', { url }, error as Error);
    throw new Error(`Failed to load texture: ${url}`);
  }
}

/**
 * Load an audio file URL for Babylon.js with credentials
 *
 * Fetches audio with credentials and returns a blob URL that can be used
 * with Babylon.js CreateSoundAsync() function.
 *
 * Note: Blob URLs are automatically managed by the browser and don't need
 * manual revocation for audio files (browser handles memory when Sound is disposed).
 *
 * @param url Audio URL to load
 * @returns Promise resolving to blob URL
 */
export async function loadAudioUrlWithCredentials(url: string): Promise<string> {
  try {
    // Fetch the audio with credentials included
    const response = await fetch(url, {
      credentials: 'include',
      mode: 'cors',
    });

    if (!response.ok) {
      logger.error('Failed to fetch audio - HTTP error', {
        url,
        status: response.status,
        statusText: response.statusText,
        contentType: response.headers.get('content-type')
      });
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    const contentType = response.headers.get('content-type');

    // Convert response to blob - preserve content type if available
    const arrayBuffer = await response.arrayBuffer();

    // Check if response is actually HTML (common error)
    const firstBytes = new Uint8Array(arrayBuffer.slice(0, 100));
    const firstChars = new TextDecoder().decode(firstBytes);
    const isHtml = firstChars.toLowerCase().includes('<html') ||
                   firstChars.toLowerCase().includes('<!doctype');

    if (isHtml) {
      logger.error('Server returned HTML instead of audio file!', {
        url,
        contentType,
        firstChars: firstChars.substring(0, 200)
      });
      throw new Error('Server returned HTML instead of audio file - check authentication or file path');
    }

    const blob = contentType
      ? new Blob([arrayBuffer], { type: contentType })
      : new Blob([arrayBuffer]);

    // Validate blob
    if (blob.size === 0) {
      logger.error('Audio blob is empty', { url });
      throw new Error('Downloaded audio file is empty');
    }

    // Create and return object URL from blob
    // Browser handles cleanup when audio is disposed
    return URL.createObjectURL(blob);
  } catch (error) {
    logger.error('Failed to load audio URL with credentials', { url }, error as Error);
    throw new Error(`Failed to load audio: ${url}`);
  }
}
