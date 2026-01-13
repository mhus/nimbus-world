/**
 * Test interface for javaType hints in inline objects
 */
export interface TestJavaTypeHints {
  name: string;

  /** Test inline object with javaType hints */
  config?: {
    /** Integer field */
    maxValue?: number; // javaType: Integer

    /** Long field */
    timestamp?: number; // javaType: Long

    /** String field */
    mode?: 'fast' | 'slow'; // javaType: String

    /** Boolean without hint */
    enabled?: boolean;
  };
}
