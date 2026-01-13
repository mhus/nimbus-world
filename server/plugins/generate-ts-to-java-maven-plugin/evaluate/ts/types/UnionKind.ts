// Test types for union-of-string mapping and field-specific override

export interface PlainReq {
  // Should become String in Java (union of string literals + string)
  kind: 'public' | 'private' | 'symmetric' | string;
}

export interface Req {
  // By default String, but in tests overridden via fieldTypeMappings to ColorHex
  kind: 'public' | 'private' | 'symmetric' | string;
}
