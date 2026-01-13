// Auto-generated TypeScript interface mirroring Java DTO CreateSKeyRequest
// Quelle: shared/src/main/java/de/mhus/nimbus/shared/dto/universe/CreateSKeyRequest.java
// Felder:
//  - type (max 64)
//  - kind ("public" | "private" | "symmetric", max 16)
//  - algorithm (max 64)
//  - name (max 128)
//  - key (Base64, variable Länge)
// Hinweis: Diese Datei dient auch als Input für den ts->java Generator.

export interface CreateSKeyRequest {
  type: string;
  kind: 'public' | 'private' | 'symmetric' | string; // string fallback erlaubt
  algorithm: string;
  name: string;
  key: string; // Base64 kodierter Schlüsselinhalt
}
