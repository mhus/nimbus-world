/**
 * Logic Rule Service
 * Manages Logic Machine rule CRUD operations
 */

import { apiService } from './ApiService';

export interface LogicEffect {
  type: string;
  parameters: Record<string, string>;
}

export interface LogicRuleDto {
  id: string;
  worldId: string;
  name: string;
  description?: string;
  rulePackage?: string;
  affected: string[];
  spelCondition: string;
  effects: LogicEffect[];
  epoches: number[];
  enabled: boolean;
  priority: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface LogicRuleListResponse {
  rules: LogicRuleDto[];
  count: number;
  limit: number;
  offset: number;
  packages: string[];
}

export interface LogicRulePagingParams {
  query?: string;
  epoch?: number;
  rulePackage?: string;
  limit?: number;
  offset?: number;
}

export interface CreateLogicRuleRequest {
  name: string;
  description?: string;
  rulePackage?: string;
  spelCondition: string;
  effects: LogicEffect[];
  epoches: number[];
  enabled?: boolean;
  priority?: number;
}

export interface UpdateLogicRuleRequest {
  name?: string;
  description?: string;
  rulePackage?: string;
  spelCondition?: string;
  effects?: LogicEffect[];
  epoches?: number[];
  enabled?: boolean;
  priority?: number;
}

export class LogicRuleService {

  async getRules(
    worldId: string,
    params?: LogicRulePagingParams
  ): Promise<LogicRuleListResponse> {
    return apiService.get<LogicRuleListResponse>(
      `/control/worlds/${worldId}/logic-rules`,
      params
    );
  }

  async getRule(worldId: string, id: string): Promise<LogicRuleDto> {
    return apiService.get<LogicRuleDto>(`/control/worlds/${worldId}/logic-rules/${id}`);
  }

  async createRule(worldId: string, rule: CreateLogicRuleRequest): Promise<string> {
    const response = await apiService.post<{ id: string }>(
      `/control/worlds/${worldId}/logic-rules`,
      rule
    );
    return response.id;
  }

  async updateRule(worldId: string, id: string, rule: UpdateLogicRuleRequest): Promise<LogicRuleDto> {
    return apiService.put<LogicRuleDto>(
      `/control/worlds/${worldId}/logic-rules/${id}`,
      rule
    );
  }

  async deleteRule(worldId: string, id: string): Promise<void> {
    return apiService.delete<void>(`/control/worlds/${worldId}/logic-rules/${id}`);
  }
}

export const logicRuleService = new LogicRuleService();
