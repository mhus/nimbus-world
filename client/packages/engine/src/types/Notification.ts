/**
 * Notification types and interfaces
 */

/**
 * Notification type enum
 */
export enum NotificationType {
  // System notifications (bottom left)
  SYSTEM_INFO = 0,
  SYSTEM_ERROR = 1,
  COMMAND_RESULT = 3,

  // Chat notifications (bottom right)
  CHAT_INFO = 10,
  CHAT_GROUP = 11,
  CHAT_PRIVATE = 12,

  // Overlay notifications (center)
  OVERLAY_BIG = 20,
  OVERLAY_SMALL = 21,

  // Quest notifications (top right)
  QUEST_NAME = 30,
  QUEST_TARGET = 31,
}

/**
 * Notification display area
 */
export enum NotificationArea {
  SYSTEM = 'system', // Bottom left
  CHAT = 'chat', // Bottom right
  OVERLAY = 'overlay', // Center
  QUEST = 'quest', // Top right
}

/**
 * Notification data structure
 */
export interface Notification {
  id: string;
  type: NotificationType;
  from: string | null;
  message: string;
  timestamp: number;
  area: NotificationArea;
  texturePath?: string | null; // Optional asset path for icon (e.g., 'items/sword.png')
}

/**
 * Notification configuration per area
 */
export interface NotificationAreaConfig {
  area: NotificationArea;
  maxCount: number;
  autoHideMs: number | null; // null = don't auto-hide
  position: 'bottom-left' | 'bottom-right' | 'center' | 'top-right';
}

/**
 * Notification area configuration map
 */
export const NOTIFICATION_AREA_CONFIGS: Record<
  NotificationArea,
  NotificationAreaConfig
> = {
  [NotificationArea.SYSTEM]: {
    area: NotificationArea.SYSTEM,
    maxCount: 5,
    autoHideMs: 5000,
    position: 'bottom-left',
  },
  [NotificationArea.CHAT]: {
    area: NotificationArea.CHAT,
    maxCount: 5,
    autoHideMs: null,
    position: 'bottom-right',
  },
  [NotificationArea.OVERLAY]: {
    area: NotificationArea.OVERLAY,
    maxCount: 1,
    autoHideMs: 2000,
    position: 'center',
  },
  [NotificationArea.QUEST]: {
    area: NotificationArea.QUEST,
    maxCount: 2,
    autoHideMs: null,
    position: 'top-right',
  },
};

/**
 * Notification type to area mapping
 */
export const NOTIFICATION_TYPE_TO_AREA: Record<
  NotificationType,
  NotificationArea
> = {
  [NotificationType.SYSTEM_INFO]: NotificationArea.SYSTEM,
  [NotificationType.SYSTEM_ERROR]: NotificationArea.SYSTEM,
  [NotificationType.COMMAND_RESULT]: NotificationArea.SYSTEM,
  [NotificationType.CHAT_INFO]: NotificationArea.CHAT,
  [NotificationType.CHAT_GROUP]: NotificationArea.CHAT,
  [NotificationType.CHAT_PRIVATE]: NotificationArea.CHAT,
  [NotificationType.OVERLAY_BIG]: NotificationArea.OVERLAY,
  [NotificationType.OVERLAY_SMALL]: NotificationArea.OVERLAY,
  [NotificationType.QUEST_NAME]: NotificationArea.QUEST,
  [NotificationType.QUEST_TARGET]: NotificationArea.QUEST,
};

/**
 * Notification style configuration per type
 */
export interface NotificationStyle {
  color: string;
  size: 'small' | 'medium' | 'large';
}

/**
 * Notification type to style mapping
 */
export const NOTIFICATION_TYPE_STYLES: Record<
  NotificationType,
  NotificationStyle
> = {
  [NotificationType.SYSTEM_INFO]: { color: 'white', size: 'medium' },
  [NotificationType.SYSTEM_ERROR]: { color: 'red', size: 'medium' },
  [NotificationType.COMMAND_RESULT]: { color: 'green', size: 'medium' },
  [NotificationType.CHAT_INFO]: { color: 'white', size: 'medium' },
  [NotificationType.CHAT_GROUP]: { color: 'yellow', size: 'medium' },
  [NotificationType.CHAT_PRIVATE]: { color: 'blue', size: 'medium' },
  [NotificationType.OVERLAY_BIG]: { color: 'white', size: 'large' },
  [NotificationType.OVERLAY_SMALL]: { color: 'white', size: 'small' },
  [NotificationType.QUEST_NAME]: { color: 'white', size: 'large' },
  [NotificationType.QUEST_TARGET]: { color: 'white', size: 'medium' },
};
