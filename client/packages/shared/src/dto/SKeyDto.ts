// Auto-generated TypeScript interface mirroring Java DTO SKeyDto
// Quelle: shared/src/main/java/de/mhus/nimbus/shared/dto/universe/SKeyDto.java
// Felder:
//  - id: string
//  - type: string
//  - kind: string (z.B. public|private|symmetric)
//  - algorithm: string (z.B. RSA, EC, AES)
//  - keyId: string (Referenz auf physisches Key-Material oder Secret Storage)
//  - createdAt: ISO Zeitstempel (Instant in Java)
//  - owner: string (Owner-Kontext, z.B. WorldId oder RegionId)
//  - intent: string (Verwendungszweck, z.B. MAIN_JWT_TOKEN)
// Hinweis: Diese Datei kann vom ts->java Generator konsumiert werden.

export interface SKeyDto {
  id: string;
  type: string;
  kind: string;
  algorithm: string;
  keyId: string;
  createdAt: string; // ISO-8601 Timestamp
  owner: string;
  intent: string;
}
