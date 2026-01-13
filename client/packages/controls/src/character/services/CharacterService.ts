import { apiService } from '@/services/ApiService';

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
}

export interface SkillRequest {
  skill: string;
  level: number;
}

class CharacterService {
  async listCharacters(regionId: string, userId?: string): Promise<Character[]> {
    const params = userId ? { userId } : {};
    return apiService.get<Character[]>(`/control/regions/${regionId}/characters`, params);
  }

  async getCharacter(regionId: string, characterId: string, userId: string, name: string): Promise<Character> {
    return apiService.get<Character>(`/control/regions/${regionId}/characters/${characterId}`, { userId, name });
  }

  async createCharacter(regionId: string, request: CharacterRequest): Promise<Character> {
    return apiService.post<Character>(`/control/regions/${regionId}/characters`, request);
  }

  async updateCharacter(
    regionId: string,
    characterId: string,
    userId: string,
    name: string,
    request: CharacterRequest
  ): Promise<Character> {
    return apiService.put<Character>(
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
  ): Promise<Character> {
    return apiService.put<Character>(
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
  ): Promise<Character> {
    return apiService.post<Character>(
      `/control/regions/${regionId}/characters/${characterId}/skills/${skill}/increment?userId=${userId}&name=${name}&delta=${delta}`
    );
  }
}

export const characterService = new CharacterService();
