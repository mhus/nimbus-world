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

  /** Marks the block for paste operations */
  MARK_BLOCK = 'MARK_BLOCK',

  /** Pastes the marked block to the selected position */
  PASTE_BLOCK = 'PASTE_BLOCK',

  /** Deletes the block at the selected position */
  DELETE_BLOCK = 'DELETE_BLOCK',

  /** Smooths blocks (applies smoothing to geometry) */
  SMOOTH_BLOCKS = 'SMOOTH_BLOCKS',

  /** Roughens blocks (applies rough geometry) */
  ROUGH_BLOCKS = 'ROUGH_BLOCKS',

  /** Clones an existing block from the layer to the selected position */
  CLONE_BLOCK = 'CLONE_BLOCK',
}
