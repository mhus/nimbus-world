/**
 * useModal - Composable for IFrame modal communication
 *
 * This composable provides functions for nimbus_editors to communicate
 * with the parent modal (Nimbus Client) when running in an IFrame.
 *
 * Usage:
 * ```typescript
 * import { useModal } from '@/composables/useModal';
 *
 * const { isEmbedded, closeModal, changePosition, sendNotification } = useModal();
 *
 * // Check if running embedded
 * if (isEmbedded()) {
 *   console.log('Running in modal');
 * }
 *
 * // Close modal
 * closeModal('user_done');
 *
 * // Change position
 * changePosition(ModalSizePreset.LEFT);
 *
 * // Send notification
 * sendNotification('info', 'Editor', 'Saved successfully');
 * ```
 */

import { IFrameMessageType, ModalSizePreset } from '@nimbus/shared';

/**
 * Get parent window origin (for security)
 * Attempts to determine the parent origin, defaults to '*' if not available
 */
function getParentOrigin(): string {
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
function sendMessage(message: any): void {
  if (window.parent === window) {
    console.warn('useModal: Not running in iframe, cannot send message');
    return;
  }

  const targetOrigin = getParentOrigin();
  window.parent.postMessage(message, targetOrigin);
}

/**
 * useModal composable
 */
export function useModal() {
  /**
   * Check if running inside an iframe with embedded=true
   */
  function isEmbedded(): boolean {
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
  function isInIFrame(): boolean {
    return window.parent !== window;
  }

  /**
   * Close the modal
   *
   * @param reason Optional reason for closing
   */
  function closeModal(reason?: string): void {
    sendMessage({
      type: IFrameMessageType.REQUEST_CLOSE,
      reason,
    });
  }

  /**
   * Change the modal position
   *
   * @param preset Position preset name
   */
  function changePosition(preset: ModalSizePreset): void {
    sendMessage({
      type: IFrameMessageType.REQUEST_POSITION_CHANGE,
      preset,
    });
  }

  /**
   * Send notification to parent (forwarded to NotificationService)
   *
   * @param notificationType Type of notification (e.g., '0' for info, '1' for error)
   * @param from Source of notification
   * @param message Notification message
   */
  function sendNotification(notificationType: string, from: string, message: string): void {
    sendMessage({
      type: IFrameMessageType.NOTIFICATION,
      notificationType,
      from,
      message,
    });
  }

  /**
   * Notify parent that iframe is ready
   */
  function notifyReady(): void {
    sendMessage({
      type: IFrameMessageType.IFRAME_READY,
    });
  }

  return {
    isEmbedded,
    isInIFrame,
    closeModal,
    changePosition,
    sendNotification,
    notifyReady,
  };
}

/**
 * Re-export ModalSizePreset for convenience
 */
export { ModalSizePreset };
