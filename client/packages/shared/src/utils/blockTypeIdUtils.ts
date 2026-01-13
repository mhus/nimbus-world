/**
 * Utility functions for BlockType ID handling
 *
 * BlockType IDs have the format: {blockTypeGroup}:{blockTypeName}
 * - If no ':' is present, default group 'w' is used
 * - All IDs are normalized to lowercase
 * - Valid characters: a-z0-9_-
 *
 * Examples:
 * - "core:stone" -> group: "core", name: "stone"
 * - "stone" -> group: "w", name: "stone"
 * - "CORE:Stone" -> normalized to "core:stone"
 */

const DEFAULT_GROUP = 'w';
const VALID_GROUP_PATTERN = /^[a-z0-9_-]+$/;

/**
 * Normalize a blockTypeId to string format
 * Converts legacy number IDs to strings automatically and ensures lowercase
 *
 * @param id - BlockType ID (can be number or string)
 * @returns Normalized string ID in lowercase
 */
export function normalizeBlockTypeId(id: number | string): string {
  if (typeof id === 'number') {
    return "w/" + String(id).toLowerCase();
  }
  return id.toLowerCase();
}

/**
 * Normalize an array of blockTypeIds to string format
 * Converts legacy number IDs to strings automatically
 * 
 * @param ids - Array of BlockType IDs (can be numbers or strings)
 * @returns Array of normalized string IDs
 */
export function normalizeBlockTypeIds(ids: (number | string)[]): string[] {
  return ids.map(normalizeBlockTypeId);
}

/**
 * Check if a blockTypeId represents "air" (empty block)
 *
 * @param id - BlockType ID
 * @returns true if the ID represents air/empty
 */
export function isAirBlockTypeId(id: number | string): boolean {
  const normalized = normalizeBlockTypeId(id);
  return normalized === '0' || normalized === 'w/0' || normalized === 'w:air' || normalized === 'air' || normalized === '';
}

/**
 * Parse a blockTypeId into group and name components
 *
 * @param id - BlockType ID (e.g., "core:stone" or "stone")
 * @returns Object with group and name, or null if invalid
 */
export function parseBlockTypeId(id: string | number): { group: string; name: string } | null {
  const normalized = normalizeBlockTypeId(id);

  // Check if ID contains '/'
  const colonIndex = normalized.indexOf('/');

  if (colonIndex === -1) {
    // No group specified, use default 'w'
    return {
      group: DEFAULT_GROUP,
      name: normalized,
    };
  }

  // Split by ':'
  const group = normalized.substring(0, colonIndex);
  const name = normalized.substring(colonIndex + 1);

  // Validate group format (only a-z0-9_- allowed)
  if (!VALID_GROUP_PATTERN.test(group)) {
    return null;
  }

  return { group, name };
}

/**
 * Extract the group from a blockTypeId
 *
 * @param id - BlockType ID
 * @returns Group name, or default 'w' if no group specified
 */
export function getBlockTypeGroup(id: string | number): string {
  const parsed = parseBlockTypeId(id);
  return parsed?.group || DEFAULT_GROUP;
}

/**
 * Extract the name from a blockTypeId
 *
 * @param id - BlockType ID
 * @returns Block type name
 */
export function getBlockTypeName(id: string | number): string {
  const parsed = parseBlockTypeId(id);
  return parsed?.name || normalizeBlockTypeId(id);
}

/**
 * Validate if a group name is valid
 * Valid characters: a-z0-9_-
 *
 * @param group - Group name to validate
 * @returns true if valid
 */
export function isValidBlockTypeGroup(group: string): boolean {
  return VALID_GROUP_PATTERN.test(group.toLowerCase());
}

/**
 * Build a blockTypeId from group and name
 *
 * @param group - Group name
 * @param name - Block type name
 * @returns Formatted blockTypeId string
 */
export function buildBlockTypeId(group: string, name: string): string {
  const normalizedGroup = group.toLowerCase();
  const normalizedName = name.toLowerCase();
  return `${normalizedGroup}/${normalizedName}`;
}
