/**
 * IFrameHelper - Helper functions for IFrame content to communicate with parent
 *
 * This file provides functions that can be used from within an IFrame
 * to communicate with the parent ModalService.
 *
 * Usage in IFrame:
 * ```typescript
 * import { IFrameHelper } from '@nimbus/engine';
 *
 * // Notify parent that iframe is ready
 * IFrameHelper.notifyReady();
 *
 * // Request to close this modal
 * IFrameHelper.requestClose('user_done');
 *
 * // Request to change position
 * IFrameHelper.requestPositionChange('left');
 *
 * // Send notification
 * IFrameHelper.sendNotification('info', 'MyApp', 'Data saved successfully');
 * ```
 */

import {  IFrameMessageType, ModalSizePreset , getLogger } from '@nimbus/shared';

const logger = getLogger('IFrameHelper');

/**
 * IFrameHelper class
 */
export class IFrameHelper {
  /**
   * Get parent window origin (for security)
   * Attempts to determine the parent origin, defaults to '*' if not available
   */
  private static getParentOrigin(): string {
    try {
      // Try to get parent origin from referrer
      if (document.referrer) {
        const referrerUrl = new URL(document.referrer);
        return referrerUrl.origin;
      }

      // Fallback to wildcard (less secure)
      return '*';
    } catch (error) {
      // If error, use wildcard
      return '*';
    }
  }

  /**
   * Send message to parent window
   */
  private static sendMessage(message: any): void {
    if (window.parent === window) {
      logger.warn('IFrameHelper: Not running in iframe, cannot send message');
      return;
    }

    const targetOrigin = this.getParentOrigin();
    window.parent.postMessage(message, targetOrigin);
  }

  /**
   * Notify parent that IFrame is ready
   */
  static notifyReady(): void {
    this.sendMessage({
      type: IFrameMessageType.IFRAME_READY,
    });
  }

  /**
   * Request parent to close this modal
   *
   * @param reason Optional reason for closing
   */
  static requestClose(reason?: string): void {
    this.sendMessage({
      type: IFrameMessageType.REQUEST_CLOSE,
      reason,
    });
  }

  /**
   * Request parent to change position of this modal
   *
   * @param preset Position preset name
   */
  static requestPositionChange(preset: ModalSizePreset): void {
    this.sendMessage({
      type: IFrameMessageType.REQUEST_POSITION_CHANGE,
      preset,
    });
  }

  /**
   * Send notification to parent (forwarded to NotificationService)
   *
   * @param notificationType Type of notification (e.g., 'info', 'warning', 'error')
   * @param from Source of notification
   * @param message Notification message
   */
  static sendNotification(notificationType: string, from: string, message: string): void {
    this.sendMessage({
      type: IFrameMessageType.NOTIFICATION,
      notificationType,
      from,
      message,
    });
  }

  /**
   * Check if running inside an iframe with embedded=true
   */
  static isEmbedded(): boolean {
    try {
      const params = new URLSearchParams(window.location.search);
      return params.get('embedded') === 'true';
    } catch (error) {
      return false;
    }
  }

  /**
   * Check if running inside an iframe
   */
  static isInIFrame(): boolean {
    return window.parent !== window;
  }
}
