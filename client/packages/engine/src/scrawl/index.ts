/**
 * Scrawl Framework - Engine Implementation
 *
 * This module provides the implementation of the Scrawl effect scripting framework.
 * It allows for declarative animation and effect sequences in the 3D world.
 */

// Core classes
export { ScrawlService } from './ScrawlService';
export { ScrawlExecutor } from './ScrawlExecutor';
export { ScrawlEffectFactory, ScrawlEffectRegistry } from './ScrawlEffectFactory';
export { ScrawlEffectHandler } from './ScrawlEffectHandler';

// Types
export type { ScrawlExecContext } from './ScrawlExecContext';
export type { EffectDeps, EffectHandlerConstructor } from './ScrawlEffectHandler';
