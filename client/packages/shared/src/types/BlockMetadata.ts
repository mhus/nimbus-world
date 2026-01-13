/**
 * BlockMetadata - Additional metadata for block instances
 *
 * Contains instance-specific data that is not part of the block type definition.
 * This includes organizational data like group membership and display names.
 *
 * Note: Block modifiers have been moved to Block.modifiers for better structure.
 */

export interface BlockMetadata {
  /**
   * Unique identifier for this block instance (e.g., for items)
   */
  id?: string;

  /**
   * Title for this block instance
   * Additional descriptive text for the block
   * Title for this block instance (e.g., "Hacke", "Schwert")
   * Used for items and special blocks that need custom names
   */
  title?: string;

  /**
   * Group ID for organization/categorization
   * Deprecated?
   */
  groupId?: string;

    /**
     * Metadata for server
     */
  server?: Record<string, string>;
    /**
     * Metadata for client
     *
     * Known fields:
     * - confirm: Text for confirmation dialog when interacting with block (Space key)
     */
  client?: Record<string, string>;

}
