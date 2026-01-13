/**
 * EffectData - Environmental effect definition
 */

export interface EffectData {
  /** Effect name (e.g., 'rain', 'fog', 'particles') */
  n: string;

  /** Effect parameters */
  p: {
    /** Effect intensity */
    intensity?: number;

    /** Effect color */
    color?: string;

    /** Additional effect-specific parameters */
    [key: string]: any;
  };
}
