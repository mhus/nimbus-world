import { apiService } from '@/services/ApiService';
import type { RCharacter } from '@nimbus/shared/generated/entities/RCharacter';
import type { PlayerInfo } from '@nimbus/shared/types/PlayerInfo';
import type { PlayerBackpack } from '@nimbus/shared';

export type { RCharacter, PlayerInfo, PlayerBackpack };

// Legacy interface for backwards compatibility
export interface Character {
  id: string;
  userId: string;
  regionId: string;
  name: string;
  display: string;
  createdAt: string;
  skills: Record<string, number>;
}

export interface CharacterRequest {
  userId: string;
  name: string;
  display?: string;
  skills?: Record<string, number>;
  publicData?: PlayerInfo;
  backpack?: PlayerBackpack;
  attributes?: Record<string, string>;
}

export interface SkillRequest {
  skill: string;
  level: number;
}

class CharacterService {
  async listCharacters(regionId: string, userId?: string): Promise<RCharacter[]> {
    const params = userId ? { userId } : {};
    return apiService.get<RCharacter[]>(`/control/regions/${regionId}/characters`, params);
  }

  async getCharacter(regionId: string, characterId: string, userId: string, name: string): Promise<RCharacter> {
    return apiService.get<RCharacter>(`/control/regions/${regionId}/characters/${characterId}`, { userId, name });
  }

  async createCharacter(regionId: string, request: CharacterRequest): Promise<RCharacter> {
    return apiService.post<RCharacter>(`/control/regions/${regionId}/characters`, request);
  }

  async updateCharacter(
    regionId: string,
    characterId: string,
    userId: string,
    name: string,
    request: CharacterRequest
  ): Promise<RCharacter> {
    return apiService.put<RCharacter>(
      `/control/regions/${regionId}/characters/${characterId}?userId=${userId}&name=${name}`,
      request
    );
  }

  async deleteCharacter(regionId: string, characterId: string, userId: string, name: string): Promise<void> {
    return apiService.delete<void>(
      `/control/regions/${regionId}/characters/${characterId}?userId=${userId}&name=${name}`
    );
  }

  async setSkill(
    regionId: string,
    characterId: string,
    userId: string,
    name: string,
    skill: string,
    level: number
  ): Promise<RCharacter> {
    return apiService.put<RCharacter>(
      `/control/regions/${regionId}/characters/${characterId}/skills/${skill}?userId=${userId}&name=${name}`,
      { skill, level }
    );
  }

  async incrementSkill(
    regionId: string,
    characterId: string,
    userId: string,
    name: string,
    skill: string,
    delta: number = 1
  ): Promise<RCharacter> {
    return apiService.post<RCharacter>(
      `/control/regions/${regionId}/characters/${characterId}/skills/${skill}/increment?userId=${userId}&name=${name}&delta=${delta}`
    );
  }
}

export const characterService = new CharacterService();
