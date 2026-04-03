/**
 * i18nText - Inline multilingual text parser
 *
 * Texts starting with ¶ are encoded multilingual strings in query-string format:
 *   ¶en=Trade offer&de=Tauschangebot&fr=Offre d'échange
 *
 * The parser resolves to the best matching language. Texts without ¶ prefix
 * are returned as-is.
 *
 * Language resolution order:
 * 1. Exact match for configured language (e.g. "de")
 * 2. Fallback to "en"
 * 3. First available entry
 * 4. Empty string
 *
 * Usage:
 *   import { i18n, setI18nLanguage } from '@nimbus/shared';
 *
 *   setI18nLanguage('de');  // once at startup
 *   i18n('¶en=Hello&de=Hallo')  // → "Hallo"
 *   i18n('plain text')           // → "plain text"
 */

const I18N_PREFIX = '¶';

let currentLanguage: string = 'en';

/**
 * Set the active language for i18n text resolution.
 * Call once at startup with user preference or browser language.
 *
 * @param lang ISO 639-1 language code (e.g. "en", "de", "fr")
 */
export function setI18nLanguage(lang: string): void {
  currentLanguage = (lang || 'en').toLowerCase().substring(0, 2);
}

/**
 * Get the currently configured language.
 */
export function getI18nLanguage(): string {
  return currentLanguage;
}

/**
 * Detect the best language from browser settings.
 * Returns the 2-letter language code (e.g. "en", "de").
 */
export function detectBrowserLanguage(): string {
  try {
    const nav = navigator.language || (navigator as any).userLanguage || 'en';
    return nav.substring(0, 2).toLowerCase();
  } catch {
    return 'en';
  }
}

/**
 * Resolve a potentially encoded i18n text to the current language.
 * Non-encoded texts are returned unchanged.
 *
 * @param text The text to resolve (may or may not start with ¶)
 * @param lang Optional language override (defaults to current language)
 * @returns The resolved text in the best matching language
 */
export function i18n(text: string | null | undefined, lang?: string): string {
  if (text == null) return '';
  if (!text.startsWith(I18N_PREFIX)) return text;

  const encoded = text.substring(I18N_PREFIX.length);
  const entries = parseEntries(encoded);

  if (entries.size === 0) return '';

  const targetLang = (lang || currentLanguage).toLowerCase().substring(0, 2);

  // 1. Exact match
  if (entries.has(targetLang)) return entries.get(targetLang)!;

  // 2. Fallback to English
  if (targetLang !== 'en' && entries.has('en')) return entries.get('en')!;

  // 3. First available
  return entries.values().next().value!;
}

/**
 * Extract a meta value from an encoded i18n text.
 * Meta keys are any key that is not a 2-letter language code (e.g. "action", "icon", "onclick").
 * Returns null if the text is not encoded or the key is not found.
 *
 * @param text The encoded text
 * @param key The meta key to extract (e.g. "action")
 * @returns The decoded value, or null
 */
export function i18nMeta(text: string | null | undefined, key: string): string | null {
  if (text == null || !text.startsWith(I18N_PREFIX)) return null;
  const entries = parseEntries(text.substring(I18N_PREFIX.length));
  return entries.get(key.toLowerCase()) ?? null;
}

/**
 * Check if a text contains i18n encoding.
 */
export function isI18nText(text: string | null | undefined): boolean {
  return text != null && text.startsWith(I18N_PREFIX);
}

/**
 * Parse query-string format: key=value&key=value
 * Values are URL-decoded.
 */
function parseEntries(encoded: string): Map<string, string> {
  const entries = new Map<string, string>();
  if (!encoded) return entries;

  const parts = encoded.split('&');
  for (const part of parts) {
    const eqIdx = part.indexOf('=');
    if (eqIdx < 0) continue;
    const key = part.substring(0, eqIdx).trim().toLowerCase();
    const value = decodeURIComponent(part.substring(eqIdx + 1));
    if (key) {
      entries.set(key, value);
    }
  }
  return entries;
}
