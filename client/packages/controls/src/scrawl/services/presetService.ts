/**
 * Preset Service
 * Loads and manages effect/command presets from effects.json
 */

import { ApiService } from '../../services/ApiService';

export interface EffectPreset {
  id: string;
  category: string;
  name: string;
  description: string;
  template: any;
  parameters?: any[];
}

export interface CommandPreset {
  id: string;
  category: string;
  name: string;
  description: string;
  template: any;
  parameters?: any[];
}

export interface EffectsLibrary {
  effects?: EffectPreset[];
  commands?: CommandPreset[];
  stepTemplates?: any[];
  exampleScripts?: any[];
}

class PresetService {
  private apiService = new ApiService();
  private library: EffectsLibrary | null = null;
  private loading = false;

  async loadLibrary(): Promise<EffectsLibrary> {
    if (this.library) {
      return this.library;
    }

    if (this.loading) {
      // Wait for ongoing load
      await new Promise(resolve => setTimeout(resolve, 100));
      return this.loadLibrary();
    }

    this.loading = true;

    try {
      const worldId = this.apiService.getCurrentWorldId();
      const url = `${this.apiService.getBaseUrl()}/control/worlds/${worldId}/assets/scrawl/effects.json`;

      const response = await fetch(url, {
        credentials: 'include'
      });
      if (!response.ok) {
        console.warn('Failed to load effects.json, using empty library');
        this.library = { effects: [], commands: [] };
        return this.library;
      }

      this.library = await response.json();
      console.log('Loaded effects library:', this.library);
      return this.library!;
    } catch (error) {
      console.error('Failed to load effects.json:', error);
      this.library = { effects: [], commands: [] };
      return this.library!;
    } finally {
      this.loading = false;
    }
  }

  async getEffectPresets(): Promise<EffectPreset[]> {
    const library = await this.loadLibrary();
    return library.effects || [];
  }

  async getCommandPresets(): Promise<CommandPreset[]> {
    const library = await this.loadLibrary();
    return library.commands || [];
  }

  async getStepTemplates(): Promise<any[]> {
    const library = await this.loadLibrary();
    return library.stepTemplates || [];
  }
}

export const presetService = new PresetService();
