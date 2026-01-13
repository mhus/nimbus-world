/**
 * Formatting utilities
 */

/**
 * Format file size to human readable format with exact value in parentheses
 * @param bytes - Size in bytes
 * @returns Formatted string like "312KB (312000)" or "1.5MB (1500000)"
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0B (0)';

  const k = 1000;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const value = bytes / Math.pow(k, i);

  // Format with up to 2 decimal places, removing trailing zeros
  const formatted = value % 1 === 0
    ? value.toFixed(0)
    : value.toFixed(2).replace(/\.?0+$/, '');

  return `${formatted}${sizes[i]} (${bytes})`;
}
