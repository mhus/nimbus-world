/**
 * Modal types and interfaces
 */

// Import and re-export shared modal types
import type { IFrameMessageFromChild } from '@nimbus/shared';
import { ModalSizePreset, ModalFlags, IFrameMessageType } from '@nimbus/shared';

export { ModalSizePreset, ModalFlags, IFrameMessageType };
export type { IFrameMessageFromChild };

/**
 * Modal size configuration
 */
export interface ModalSize {
  /** Width in CSS units (px, %, em, etc.) */
  width: string;

  /** Height in CSS units (px, %, em, etc.) */
  height: string;
}

/**
 * Modal position configuration
 */
export interface ModalPosition {
  /** Horizontal position ('center' or CSS value like '100px', '10%') */
  x: string | 'center';

  /** Vertical position ('center' or CSS value like '50px', '5%') */
  y: string | 'center';
}

/**
 * Size and position preset (combined)
 */
export interface ModalSizePositionPreset {
  /** Size configuration */
  size: ModalSize;

  /** Position configuration */
  position: ModalPosition;
}

/**
 * Modal options
 */
export interface ModalOptions {
  /** Modal size (preset or custom) */
  size?: ModalSizePreset | ModalSize;

  /** Modal position ('center' or custom coordinates) */
  position?: 'center' | ModalPosition;

  /** Modal behavior flags (bitflags) */
  flags?: number;

  /** Close modal when clicking backdrop (default: true, overridden by flags) */
  closeOnBackdrop?: boolean;

  /** Close modal when pressing ESC key (default: true, overridden by flags) */
  closeOnEsc?: boolean;

  /** Reference key for this modal (reuse existing modal with same key) */
  referenceKey?: string;
}

/**
 * Modal reference returned from openModal()
 */
export interface ModalReference {
  /** Unique modal ID */
  id: string;

  /** Reference key (if provided) */
  referenceKey?: string;

  /** Root DOM element of the modal */
  element: HTMLElement;

  /** IFrame element */
  iframe: HTMLIFrameElement;

  /** Close this modal */
  close: (reason?: string) => void;

  /** Change position of this modal */
  changePosition: (preset: ModalSizePreset) => void;

  /** Optional callback executed when modal closes */
  onClose?: (reason?: string) => void;
}
