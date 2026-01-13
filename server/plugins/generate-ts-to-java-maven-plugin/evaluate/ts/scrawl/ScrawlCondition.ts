/**
 * Condition: Variable equals a specific value
 */
export interface CondVarEquals {
  kind: 'VarEquals';
  /** Variable name to check */
  name: string;
  /** Expected value */
  value?: any;
}

/**
 * Condition: Variable exists in context
 */
export interface CondVarExists {
  kind: 'VarExists';
  /** Variable name to check */
  name: string;
}

/**
 * Condition: Random chance (0.0 to 1.0)
 */
export interface CondChance {
  kind: 'Chance';
  /** Probability (0.0 to 1.0) */
  p: number;
}

/**
 * Condition: Check if targets/patients exist
 */
export interface CondHasTargets {
  kind: 'HasTargets';
  /** Minimum number of targets required */
  min?: number;
}

/**
 * Condition: Check if source/actor exists
 */
export interface CondHasSource {
  kind: 'HasSource';
}

/**
 * Condition: Variable is true (or default value if not exists)
 */
export interface CondIsVarTrue {
  kind: 'IsVarTrue';
  /** Variable name to check */
  name: string;
  /** Default value if variable doesn't exist */
  defaultValue?: boolean;
}

/**
 * Condition: Variable is false (or not exists, or default value)
 */
export interface CondIsVarFalse {
  kind: 'IsVarFalse';
  /** Variable name to check */
  name: string;
  /** Default value if variable doesn't exist */
  defaultValue?: boolean;
}

/**
 * Union type of all possible conditions
 */
export type ScrawlCondition =
  | CondVarEquals
  | CondVarExists
  | CondChance
  | CondHasTargets
  | CondHasSource
  | CondIsVarTrue
  | CondIsVarFalse;
