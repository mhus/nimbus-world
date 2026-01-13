/**
 * Rendering Groups Configuration
 *
 * Centralized definitions for BabylonJS rendering groups in the Nimbus client.
 *
 * In BabylonJS: Niedrigere IDs rendern zuerst (hinten), höhere IDs rendern später (vorne)
 * - Lower rendering group IDs render first (background/behind)
 * - Higher rendering group IDs render last (foreground/on top)
 */

export const RENDERING_GROUPS = {
  /** Background environment (sun, sky) - renders first/behind everything */
  ENVIRONMENT: 0,

  /** Backdrop (pseudo-walls at chunk boundaries) - renders after environment, before world */
  BACKDROP: 1,

  /** Main world content with depth testing (blocks, fog, entities) */
  WORLD: 2,

  /** Precipitation (rain, snow) - renders after world */
  PRECIPITATION: 3,

  /** Selection overlays (highlights) - renders on top */
  SELECTION_OVERLAY: 4,

  /** Camera decorators (underwater/fog sphere effects) - renders with world */
  CAM_DECORATORS: 2,

  // Future extensions:
  // PARTICLES: 5    - Particle effects (fire, smoke, etc.)
} as const;
