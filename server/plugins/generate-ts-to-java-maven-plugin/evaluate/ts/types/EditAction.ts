/**
 * EditAction - Actions that can be executed when setting a selected edit block
 */

/**
 * Edit action types for block editing workflow
 */
export enum EditAction {
  /** Opens the config dialog for the block (default action) */
  OPEN_CONFIG_DIALOG = 'OPEN_CONFIG_DIALOG',

  /** Opens the block editor at the selected position */
  OPEN_EDITOR = 'OPEN_EDITOR',

  /** Marks the block for copy/move operations */
  MARK_BLOCK = 'MARK_BLOCK',

  /** Copies the marked block to the selected position */
  COPY_BLOCK = 'COPY_BLOCK',

  /** Deletes the block at the selected position */
  DELETE_BLOCK = 'DELETE_BLOCK',

  /** Moves the marked block to the selected position */
  MOVE_BLOCK = 'MOVE_BLOCK',
}
