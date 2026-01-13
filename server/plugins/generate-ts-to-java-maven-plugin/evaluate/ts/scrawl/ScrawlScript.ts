import { ScrawlStep } from './ScrawlStep';
import { ScrawlParameterType } from './ScrawlTypes';

/**
 * Parameter definition for a scrawl script
 */
export interface ScrawlParameterDefinition {
  /** Parameter name */
  name: string;

  /** Parameter type */
  type: ScrawlParameterType;

  /** Default value (optional) */
  default?: any;

  /** Description of the parameter */
  description?: string;
}

/**
 * Named sequence within a script
 */
export interface ScrawlSequence {
  /** Sequence name */
  name: string;

  /** Root step of the sequence */
  step: ScrawlStep;

  /** Description of the sequence */
  description?: string;
}

/**
 * Complete scrawl script definition
 */
export interface ScrawlScript {
  /** Schema version for compatibility */
  schemaVersion?: number;

  /** Unique script ID */
  id: string;

  /** Script description */
  description?: string;

  /** Imported scripts/templates (paths without .scrawl.json extension) */
  imports?: string[];

  /** Parameter definitions */
  parameters?: ScrawlParameterDefinition[];

  /** Named sequences (key = sequence name, value = sequence definition) */
  sequences?: Record<string, ScrawlSequence>;

  /** Root step (for simple scripts) or reference to main sequence */
  root?: ScrawlStep;
}

/**
 * Script library interface for loading and caching scripts
 */
export interface ScrawlScriptLibrary {
  /**
   * Get a script by ID
   * @param id Script ID (without .scrawl.json extension)
   */
  get(id: string): ScrawlScript | undefined;

  /**
   * Load a script by ID
   * @param id Script ID (without .scrawl.json extension)
   * @returns Promise resolving to the script, or undefined if not found
   */
  load(id: string): Promise<ScrawlScript | undefined>;

  /**
   * Check if a script exists
   * @param id Script ID (without .scrawl.json extension)
   */
  has(id: string): boolean;
}
