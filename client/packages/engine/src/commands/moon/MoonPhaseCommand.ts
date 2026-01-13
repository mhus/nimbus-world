/**
 * MoonPhaseCommand - Set moon phase
 *
 * Usage: moonPhase [moonIndex] [phase]
 * - moonIndex: 0, 1, or 2
 * - phase: 0.0 (new moon) to 1.0 (full moon)
 *   - 0.0 = New moon (dark)
 *   - 0.25 = Waxing crescent
 *   - 0.5 = Half moon
 *   - 0.75 = Waxing gibbous
 *   - 1.0 = Full moon (bright)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('MoonPhaseCommand');

export class MoonPhaseCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'moonPhase';
  }

  description(): string {
    return 'Set moon phase (moonPhase [0-2] [0.0-1.0], 0=new, 0.5=half, 1=full)';
  }

  async execute(parameters: any[]): Promise<string> {
    const moonService = this.appContext.services.moon;

    if (!moonService) {
      return 'MoonService not available';
    }

    if (parameters.length < 2) {
      return 'Usage: moonPhase [moonIndex] [phase]\nExample: moonPhase 0 0.75 (gibbous moon)';
    }

    const moonIndex = toNumber(parameters[0]);
    if (isNaN(moonIndex) || moonIndex < 0 || moonIndex > 2) {
      return 'Invalid moonIndex. Must be 0, 1, or 2.';
    }

    const phase = toNumber(parameters[1]);
    if (isNaN(phase)) {
      return 'Invalid phase. Must be a number between 0.0 and 1.0.';
    }

    if (phase < 0 || phase > 1) {
      return 'Phase must be between 0.0 (new moon) and 1.0 (full moon).';
    }

    moonService.setMoonPhase(moonIndex, phase);

    const phaseName = this.getPhaseName(phase);
    logger.debug(`Moon ${moonIndex} phase set to ${phase.toFixed(2)}`, { phaseName });

    return `Moon ${moonIndex} phase set to ${phase.toFixed(2)} (${phaseName})`;
  }

  private getPhaseName(phase: number): string {
    if (phase < 0.05) return 'New Moon';
    if (phase < 0.2) return 'Waxing Crescent';
    if (phase < 0.3) return 'First Quarter';
    if (phase < 0.45) return 'Waxing Gibbous';
    if (phase < 0.55) return 'Full Moon';
    if (phase < 0.7) return 'Waning Gibbous';
    if (phase < 0.8) return 'Last Quarter';
    if (phase < 0.95) return 'Waning Crescent';
    return 'New Moon';
  }
}
