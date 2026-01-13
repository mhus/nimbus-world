/**
 * Color - Color representation and utilities
 *
 * Colors are stored as strings (hex format) for simplicity and network efficiency.
 * ColorRGBA class provides conversion to/from RGBA components.
 */

/**
 * Color as hex string
 * Format: '#RRGGBB' or '#RRGGBBAA'
 */
export type ColorHex = string;

/**
 * RGBA color components
 */
export class ColorRGBA {
  /**
   * Red component (0-255)
   */
  readonly r: number;

  /**
   * Green component (0-255)
   */
  readonly g: number;

  /**
   * Blue component (0-255)
   */
  readonly b: number;

  /**
   * Alpha component (0-1)
   */
  readonly a: number;

  /**
   * Create ColorRGBA from components
   * @param r Red (0-255)
   * @param g Green (0-255)
   * @param b Blue (0-255)
   * @param a Alpha (0-1, default: 1)
   */
  constructor(r: number, g: number, b: number, a: number = 1) {
    this.r = Math.max(0, Math.min(255, Math.round(r)));
    this.g = Math.max(0, Math.min(255, Math.round(g)));
    this.b = Math.max(0, Math.min(255, Math.round(b)));
    this.a = Math.max(0, Math.min(1, a));
  }

  /**
   * Create ColorRGBA from hex string
   * @param hex Hex color string ('#RRGGBB' or '#RRGGBBAA')
   * @returns ColorRGBA instance
   */
  static fromHex(hex: ColorHex): ColorRGBA {
    // Remove # if present
    const cleanHex = hex.replace(/^#/, '');

    // Parse components
    let r = 0,
      g = 0,
      b = 0,
      a = 1;

    if (cleanHex.length === 6) {
      // #RRGGBB
      r = parseInt(cleanHex.substring(0, 2), 16);
      g = parseInt(cleanHex.substring(2, 4), 16);
      b = parseInt(cleanHex.substring(4, 6), 16);
      a = 1;
    } else if (cleanHex.length === 8) {
      // #RRGGBBAA
      r = parseInt(cleanHex.substring(0, 2), 16);
      g = parseInt(cleanHex.substring(2, 4), 16);
      b = parseInt(cleanHex.substring(4, 6), 16);
      a = parseInt(cleanHex.substring(6, 8), 16) / 255;
    } else if (cleanHex.length === 3) {
      // #RGB (shorthand)
      r = parseInt(cleanHex[0] + cleanHex[0], 16);
      g = parseInt(cleanHex[1] + cleanHex[1], 16);
      b = parseInt(cleanHex[2] + cleanHex[2], 16);
      a = 1;
    } else if (cleanHex.length === 4) {
      // #RGBA (shorthand)
      r = parseInt(cleanHex[0] + cleanHex[0], 16);
      g = parseInt(cleanHex[1] + cleanHex[1], 16);
      b = parseInt(cleanHex[2] + cleanHex[2], 16);
      a = parseInt(cleanHex[3] + cleanHex[3], 16) / 255;
    } else {
      // Invalid format, return black
      console.warn(`Invalid hex color: ${hex}`);
      return new ColorRGBA(0, 0, 0, 1);
    }

    return new ColorRGBA(r, g, b, a);
  }

  /**
   * Create ColorRGBA from RGB components (alpha = 1)
   * @param r Red (0-255)
   * @param g Green (0-255)
   * @param b Blue (0-255)
   * @returns ColorRGBA instance
   */
  static fromRGB(r: number, g: number, b: number): ColorRGBA {
    return new ColorRGBA(r, g, b, 1);
  }

  /**
   * Create ColorRGBA from normalized components (0-1)
   * @param r Red (0-1)
   * @param g Green (0-1)
   * @param b Blue (0-1)
   * @param a Alpha (0-1)
   * @returns ColorRGBA instance
   */
  static fromNormalized(r: number, g: number, b: number, a: number = 1): ColorRGBA {
    return new ColorRGBA(r * 255, g * 255, b * 255, a);
  }

  /**
   * Convert to hex string
   * @param includeAlpha Include alpha in output (default: false)
   * @returns Hex color string
   */
  toHex(includeAlpha: boolean = false): ColorHex {
    const r = this.r.toString(16).padStart(2, '0');
    const g = this.g.toString(16).padStart(2, '0');
    const b = this.b.toString(16).padStart(2, '0');

    if (includeAlpha || this.a < 1) {
      const a = Math.round(this.a * 255)
        .toString(16)
        .padStart(2, '0');
      return `#${r}${g}${b}${a}`;
    }

    return `#${r}${g}${b}`;
  }

  /**
   * Get normalized RGB components (0-1)
   * @returns Object with r, g, b, a (0-1)
   */
  toNormalized(): { r: number; g: number; b: number; a: number } {
    return {
      r: this.r / 255,
      g: this.g / 255,
      b: this.b / 255,
      a: this.a,
    };
  }

  /**
   * Get as array [r, g, b, a]
   * @param normalized If true, returns 0-1 range, else 0-255
   * @returns Array of components
   */
  toArray(normalized: boolean = false): [number, number, number, number] {
    if (normalized) {
      return [this.r / 255, this.g / 255, this.b / 255, this.a];
    }
    return [this.r, this.g, this.b, this.a];
  }

  /**
   * Clone color
   * @returns New ColorRGBA with same values
   */
  clone(): ColorRGBA {
    return new ColorRGBA(this.r, this.g, this.b, this.a);
  }

  /**
   * Check if equal to another color
   * @param other Other color
   * @param epsilon Epsilon for comparison (default: 1)
   * @returns True if equal
   */
  equals(other: ColorRGBA, epsilon: number = 1): boolean {
    return (
      Math.abs(this.r - other.r) <= epsilon &&
      Math.abs(this.g - other.g) <= epsilon &&
      Math.abs(this.b - other.b) <= epsilon &&
      Math.abs(this.a - other.a) <= epsilon / 255
    );
  }

  /**
   * Multiply color by scalar (brightness)
   * @param scalar Multiplier
   * @returns New color
   */
  multiply(scalar: number): ColorRGBA {
    return new ColorRGBA(this.r * scalar, this.g * scalar, this.b * scalar, this.a);
  }

  /**
   * Add another color (component-wise)
   * @param other Color to add
   * @returns New color
   */
  add(other: ColorRGBA): ColorRGBA {
    return new ColorRGBA(
      this.r + other.r,
      this.g + other.g,
      this.b + other.b,
      this.a
    );
  }

  /**
   * Linearly interpolate to another color
   * @param other Target color
   * @param t Interpolation factor (0-1)
   * @returns Interpolated color
   */
  lerp(other: ColorRGBA, t: number): ColorRGBA {
    return new ColorRGBA(
      this.r + (other.r - this.r) * t,
      this.g + (other.g - this.g) * t,
      this.b + (other.b - this.b) * t,
      this.a + (other.a - this.a) * t
    );
  }

  /**
   * Convert to grayscale
   * @returns Grayscale color
   */
  toGrayscale(): ColorRGBA {
    // Use luminosity method
    const gray = 0.299 * this.r + 0.587 * this.g + 0.114 * this.b;
    return new ColorRGBA(gray, gray, gray, this.a);
  }

  /**
   * Invert color
   * @returns Inverted color
   */
  invert(): ColorRGBA {
    return new ColorRGBA(255 - this.r, 255 - this.g, 255 - this.b, this.a);
  }

  /**
   * Convert to string representation for debugging
   * @returns String representation
   */
  toString(): string {
    return `ColorRGBA(${this.r}, ${this.g}, ${this.b}, ${this.a.toFixed(2)})`;
  }

  // Predefined colors
  static readonly WHITE = new ColorRGBA(255, 255, 255, 1);
  static readonly BLACK = new ColorRGBA(0, 0, 0, 1);
  static readonly RED = new ColorRGBA(255, 0, 0, 1);
  static readonly GREEN = new ColorRGBA(0, 255, 0, 1);
  static readonly BLUE = new ColorRGBA(0, 0, 255, 1);
  static readonly YELLOW = new ColorRGBA(255, 255, 0, 1);
  static readonly CYAN = new ColorRGBA(0, 255, 255, 1);
  static readonly MAGENTA = new ColorRGBA(255, 0, 255, 1);
  static readonly TRANSPARENT = new ColorRGBA(0, 0, 0, 0);
}

/**
 * Color utility functions for working with hex strings
 */
export namespace ColorUtils {
  /**
   * Convert hex string to ColorRGBA
   * @param hex Hex color string
   * @returns ColorRGBA instance
   */
  export function hexToRGBA(hex: ColorHex): ColorRGBA {
    return ColorRGBA.fromHex(hex);
  }

  /**
   * Convert ColorRGBA to hex string
   * @param color ColorRGBA instance
   * @param includeAlpha Include alpha channel
   * @returns Hex color string
   */
  export function rgbaToHex(color: ColorRGBA, includeAlpha: boolean = false): ColorHex {
    return color.toHex(includeAlpha);
  }

  /**
   * Parse color string (supports multiple formats)
   * @param colorStr Color string ('#RGB', '#RRGGBB', '#RRGGBBAA', 'rgb()', etc.)
   * @returns ColorRGBA or null if invalid
   */
  export function parseColor(colorStr: string): ColorRGBA | null {
    // Hex format
    if (colorStr.startsWith('#')) {
      return ColorRGBA.fromHex(colorStr);
    }

    // rgb() or rgba() format
    const rgbMatch = colorStr.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/);
    if (rgbMatch) {
      const r = parseInt(rgbMatch[1], 10);
      const g = parseInt(rgbMatch[2], 10);
      const b = parseInt(rgbMatch[3], 10);
      const a = rgbMatch[4] ? parseFloat(rgbMatch[4]) : 1;
      return new ColorRGBA(r, g, b, a);
    }

    // Named colors (basic set)
    const namedColors: Record<string, ColorHex> = {
      white: '#ffffff',
      black: '#000000',
      red: '#ff0000',
      green: '#00ff00',
      blue: '#0000ff',
      yellow: '#ffff00',
      cyan: '#00ffff',
      magenta: '#ff00ff',
      gray: '#808080',
      grey: '#808080',
    };

    const named = namedColors[colorStr.toLowerCase()];
    if (named) {
      return ColorRGBA.fromHex(named);
    }

    console.warn(`Invalid color string: ${colorStr}`);
    return null;
  }

  /**
   * Validate hex color string
   * @param hex Hex color string
   * @returns True if valid
   */
  export function isValidHex(hex: ColorHex): boolean {
    const cleanHex = hex.replace(/^#/, '');
    return /^([0-9A-Fa-f]{3}|[0-9A-Fa-f]{4}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$/.test(
      cleanHex
    );
  }

  /**
   * Ensure hex string has # prefix
   * @param hex Hex color string
   * @returns Hex with # prefix
   */
  export function ensureHash(hex: string): ColorHex {
    return hex.startsWith('#') ? hex : `#${hex}`;
  }

  /**
   * Convert between color formats
   * @param color Color in any format
   * @param outputFormat Target format
   * @returns Converted color string
   */
  export function convert(
    color: string,
    outputFormat: 'hex' | 'hexa' | 'rgb' | 'rgba'
  ): string {
    const rgba = parseColor(color);
    if (!rgba) {
      return color; // Return original if invalid
    }

    switch (outputFormat) {
      case 'hex':
        return rgba.toHex(false);
      case 'hexa':
        return rgba.toHex(true);
      case 'rgb':
        return `rgb(${rgba.r}, ${rgba.g}, ${rgba.b})`;
      case 'rgba':
        return `rgba(${rgba.r}, ${rgba.g}, ${rgba.b}, ${rgba.a})`;
      default:
        return color;
    }
  }

  /**
   * Lighten color by percentage
   * @param color Color string
   * @param percent Percentage (0-1)
   * @returns Lightened color hex
   */
  export function lighten(color: ColorHex, percent: number): ColorHex {
    const rgba = ColorRGBA.fromHex(color);
    const white = ColorRGBA.WHITE;
    return rgba.lerp(white, percent).toHex();
  }

  /**
   * Darken color by percentage
   * @param color Color string
   * @param percent Percentage (0-1)
   * @returns Darkened color hex
   */
  export function darken(color: ColorHex, percent: number): ColorHex {
    const rgba = ColorRGBA.fromHex(color);
    const black = ColorRGBA.BLACK;
    return rgba.lerp(black, percent).toHex();
  }

  /**
   * Set alpha on color
   * @param color Color string
   * @param alpha Alpha value (0-1)
   * @returns Color with new alpha
   */
  export function setAlpha(color: ColorHex, alpha: number): ColorHex {
    const rgba = ColorRGBA.fromHex(color);
    return new ColorRGBA(rgba.r, rgba.g, rgba.b, alpha).toHex(true);
  }

  /**
   * Interpolate between two colors
   * @param from Start color
   * @param to End color
   * @param t Interpolation factor (0-1)
   * @returns Interpolated color hex
   */
  export function lerp(from: ColorHex, to: ColorHex, t: number): ColorHex {
    const fromRGBA = ColorRGBA.fromHex(from);
    const toRGBA = ColorRGBA.fromHex(to);
    return fromRGBA.lerp(toRGBA, t).toHex();
  }
}
