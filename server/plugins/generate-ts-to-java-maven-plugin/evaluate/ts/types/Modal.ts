/**
 * Modal types shared between client and nimbus_editors
 *
 * These types are used for IFrame modal communication and configuration.
 */

/**
 * Size presets for common modal sizes and positions
 */
export enum ModalSizePreset {
  /** Left side panel (50% width, full height) */
  LEFT = 'left',

  /** Right side panel (50% width, full height) */
  RIGHT = 'right',

  /** Top panel (full width, 50% height) */
  TOP = 'top',

  /** Bottom panel (full width, 50% height) */
  BOTTOM = 'bottom',

  /** Center small (600x400px) */
  CENTER_SMALL = 'center_small',

  /** Center medium (800x600px) */
  CENTER_MEDIUM = 'center_medium',

  /** Center large (90vw x 90vh) */
  CENTER_LARGE = 'center_large',

  /** Top-left Region (50% x 50%) */
  LEFT_TOP = 'left_top',

  /** Bottom-left Region (50% x 50%) */
  LEFT_BOTTOM = 'left_bottom',

  /** Top-right Region (50% x 50%) */
  RIGHT_TOP = 'right_top',

  /** Bottom-right Region (50% x 50%) */
  RIGHT_BOTTOM = 'right_bottom',
}

/**
 * Modal flags (bitflags for options)
 */
export enum ModalFlags {
  /** No flags */
  NONE = 0,

  /** Modal can be closed by user */
  CLOSEABLE = 1 << 0, // 1

  /** Minimal borders (no title, no close button, thin border) */
  NO_BORDERS = 1 << 1, // 2

  /** Show break-out button to open in new window */
  BREAK_OUT = 1 << 2, // 4

  /** No background lock (background not grayed out, no close on background click) */
  NO_BACKGROUND_LOCK = 1 << 3, // 8

  /** Modal can be moved by dragging the header */
  MOVEABLE = 1 << 4, // 16

  /** Modal can be resized by dragging edges/corners */
  RESIZEABLE = 1 << 5, // 32
}

/**
 * Message types for IFrame <-> Parent communication
 */
export enum IFrameMessageType {
  /** IFrame is ready */
  IFRAME_READY = 'IFRAME_READY',

  /** IFrame requests to be closed */
  REQUEST_CLOSE = 'REQUEST_CLOSE',

  /** IFrame requests position change */
  REQUEST_POSITION_CHANGE = 'REQUEST_POSITION_CHANGE',

  /** IFrame sends notification */
  NOTIFICATION = 'NOTIFICATION',
}

/**
 * IFrame message from child to parent
 */
export type IFrameMessageFromChild =
  | { type: IFrameMessageType.IFRAME_READY }
  | { type: IFrameMessageType.REQUEST_CLOSE; reason?: string }
  | { type: IFrameMessageType.REQUEST_POSITION_CHANGE; preset: ModalSizePreset }
  | {
      type: IFrameMessageType.NOTIFICATION;
      notificationType: string;
      from: string;
      message: string;
    };
