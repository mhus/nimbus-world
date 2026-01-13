// Auto-generated TypeScript interface mirroring Java DTO RegionCharacterResponse
// Quelle: shared/src/main/java/de/mhus/nimbus/shared/dto/region/RegionCharacterResponse.java
// Felder:
//  - id
//  - userId
//  - name
//  - display
//  - backpack: Map<String, RegionItemInfo>
//  - wearing: Map<Integer, RegionItemInfo>
//  - skills: Map<String, Integer>
//  - regionId

import { RegionItemInfo } from '../types/RegionItemInfo';

export interface RegionCharacterResponse {
  id: string;
  userId: string;
  regionId: string;
  name: string;
  display: string;
  backpack: Record<string, RegionItemInfo>;
  wearing: Record<number, RegionItemInfo>; // javaType: java.util.Map<Integer,RegionItemInfo>
  skills: Record<string, number>; // javaType: java.util.Map<String,Integer>
}
