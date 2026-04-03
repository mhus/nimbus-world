import { describe, it, expect, beforeEach } from 'vitest';
import { i18n, setI18nLanguage, getI18nLanguage, isI18nText, detectBrowserLanguage } from './i18nText';

describe('i18nText', () => {

  beforeEach(() => {
    setI18nLanguage('en');
  });

  it('returns plain text unchanged', () => {
    expect(i18n('Hello World')).toBe('Hello World');
    expect(i18n('§ paragraph sign is fine')).toBe('§ paragraph sign is fine');
  });

  it('returns empty string for null/undefined', () => {
    expect(i18n(null)).toBe('');
    expect(i18n(undefined)).toBe('');
  });

  it('resolves encoded text to current language', () => {
    setI18nLanguage('de');
    expect(i18n('¶en=Trade offer&de=Tauschangebot')).toBe('Tauschangebot');
  });

  it('falls back to English if language not found', () => {
    setI18nLanguage('fr');
    expect(i18n('¶en=Trade offer&de=Tauschangebot')).toBe('Trade offer');
  });

  it('falls back to first available if no English', () => {
    setI18nLanguage('fr');
    expect(i18n('¶de=Hallo&es=Hola')).toBe('Hallo');
  });

  it('handles URL-encoded values', () => {
    setI18nLanguage('en');
    expect(i18n('¶en=Buy%20%26%20Sell&de=Kaufen%20%26%20Verkaufen')).toBe('Buy & Sell');
  });

  it('allows language override parameter', () => {
    setI18nLanguage('en');
    expect(i18n('¶en=Hello&de=Hallo', 'de')).toBe('Hallo');
  });

  it('isI18nText detects encoded texts', () => {
    expect(isI18nText('¶en=Hello')).toBe(true);
    expect(isI18nText('Hello')).toBe(false);
    expect(isI18nText(null)).toBe(false);
    expect(isI18nText('§1 BGB')).toBe(false);
  });

  it('handles empty encoded text', () => {
    expect(i18n('¶')).toBe('');
  });

  it('handles single language', () => {
    setI18nLanguage('en');
    expect(i18n('¶en=Only English')).toBe('Only English');
  });
});
