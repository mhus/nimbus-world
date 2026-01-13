/**
 * ShortcutDefinition - Defines an action bound to a shortcut key or click
 *
 * Shortcuts can be bound to:
 * - Number keys: key0...key9 (0 = key '0', 1-9 = keys '1'-'9')
 * - Mouse clicks: click0, click1, click2 (0 = left, 1 = middle, 2 = right)
 * - Inventory slots: slot0...slotN
 */

/**
 * Shortcut action type
 */
export type ShortcutActionType = 'block' | 'attack' | 'use' | 'none';

/**
 * Shortcut key type
 * - key0...key9: Number keys (key '0' through '9')
 * - click0, click1, click2: Mouse buttons (left, middle, right)
 * - slot0...slotN: Inventory slots
 */
export type ShortcutKey = string; // Format: 'key0'-'key9', 'click0'-'click2', 'slot0'-'slotN'

/**
 * Shortcut definition
 *
 * Defines what item should be used when a shortcut is triggered.
 * The item itself (ItemData) contains the action parameters (pose, wait, duration).
 */
export interface ShortcutDefinition {
  /**
   * Action type
   * - 'block': Place/use a block
   * - 'attack': Attack action
   * - 'use': Use item action
   * - 'none': No action (default)
   */
  type: ShortcutActionType; // javaType: String

  /**
   * Item ID to use (for block, attack, or use actions)
   * Optional - if not specified, uses currently selected item
   *
   * The referenced item's ItemData contains:
   * - pose: Animation to play
   * - wait: Delay before activation
   * - duration: How long the action lasts
   */
  itemId?: string;

  /**
   * Display name for UI
   * Optional - if not specified, uses item's display name from metadata
   */
  name?: string;

  /**
   * Description for UI tooltips
   * Optional - if not specified, uses item's description from ItemData
   */
  description?: string;

  /**
   * How long to wait after activation before allowing next action (in milliseconds)
   */
  wait: number;  // javaType: int

  /**
   * Optional command to execute when the shortcut is triggered
   * this will be executed instead of the item action
   */
  command?: string; // Optional command to execute
  commandArgs?: any[]; // Optional command arguments
  iconPath?: string; // Optional icon path for UI

}

/**
 * Default shortcut (no action)
 */
export const DEFAULT_SHORTCUT: ShortcutDefinition = {
  type: 'none',
  wait: 100
};

/**
 * Shortcuts map type
 */
export type ShortcutsMap = Map<ShortcutKey, ShortcutDefinition>;
