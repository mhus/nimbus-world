/**
 * RedirectCommand - Redirect browser to a different URL
 * Typically used for teleportation flow.
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('RedirectCommand');

/**
 * Redirect command - Redirects the browser to a specified URL
 *
 * Usage:
 *   redirect <url>
 *
 * Parameters:
 *   url - Target URL to redirect to
 *
 * Examples:
 *   redirect http://localhost:3002/controls/teleport-login.html
 *   redirect /controls/teleport-login.html
 */
export class RedirectCommand extends CommandHandler {
  name(): string {
    return 'redirect';
  }

  description(): string {
    return 'Redirect browser to specified URL';
  }

  execute(parameters: any[]): any {
    // Validate parameters
    if (parameters.length < 1) {
      logger.error('Usage: redirect <url>');
      return { error: 'Missing URL parameter. Usage: redirect <url>' };
    }

    const url = parameters[0];

    // Validate URL
    if (typeof url !== 'string' || url.length === 0) {
      logger.error('URL must be a non-empty string');
      return { error: 'URL must be a non-empty string' };
    }

    logger.info(`Redirecting to: ${url}`);

    // Perform redirect
    try {
      window.location.href = url;
      return {
        url,
        message: `Redirecting to ${url}`,
      };
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Unknown error';
      logger.error(`Failed to redirect to ${url}:`, errorMessage);
      return {
        error: `Failed to redirect: ${errorMessage}`,
      };
    }
  }
}
