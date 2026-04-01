/**
 * Browser Text-to-Speech utility using Web Speech API.
 * Selects voice based on language, gender, and voiceIndex.
 * Reusable across Dialog Widget, Chat, and other components.
 */

export interface VoiceInfo {
  lang: string;        // "de", "en" — from RUser.language
  gender: string;      // "M", "W", "D" — from Entity.publicData.gender
  voiceIndex: number;  // 0+ — voice number, modulo available voices
  rate: number;        // 0.5 - 2.0
  pitch: number;       // 0.5 - 2.0
}

/** Known male voice name patterns */
const MALE_NAMES = [
  'daniel', 'thomas', 'alex', 'david', 'james', 'mark', 'gordon', 'reed',
  'jorge', 'rishi', 'aaron', 'albert', 'arthur', 'bruce', 'charles', 'eddy',
  'evan', 'fred', 'grandpa', 'jacques', 'ralph', 'oliver', 'martin', 'hans',
  'yannick', 'markus', 'florian'
];

/** Known female voice name patterns */
const FEMALE_NAMES = [
  'victoria', 'samantha', 'karen', 'helena', 'anna', 'alice', 'fiona',
  'catherine', 'ava', 'allison', 'ellen', 'kate', 'moira', 'nicky',
  'sandy', 'sara', 'shelley', 'susan', 'tessa', 'zoe', 'grandma',
  'amelie', 'petra', 'marlene', 'vicki'
];

let cachedVoices: SpeechSynthesisVoice[] | null = null;
let voicesReady = false;

function loadVoices(): SpeechSynthesisVoice[] {
  if (cachedVoices && voicesReady) return cachedVoices;
  if (!window.speechSynthesis) return [];

  const voices = window.speechSynthesis.getVoices();
  if (voices.length > 0) {
    cachedVoices = voices;
    voicesReady = true;
  }
  return voices;
}

// Chrome loads voices asynchronously
if (typeof window !== 'undefined' && window.speechSynthesis) {
  window.speechSynthesis.onvoiceschanged = () => {
    cachedVoices = window.speechSynthesis.getVoices();
    voicesReady = true;
  };
  // Try synchronous load (Firefox/Safari)
  loadVoices();
}

/**
 * Guess gender from voice name using known name patterns and keywords.
 * Returns 'M', 'W', or null (unknown).
 */
function guessGender(voiceName: string): string | null {
  const lower = voiceName.toLowerCase();

  // Check explicit keywords (Google/Microsoft voices)
  if (lower.includes('male') && !lower.includes('female')) return 'M';
  if (lower.includes('female')) return 'W';

  // Check known name patterns
  for (const name of MALE_NAMES) {
    if (lower.includes(name)) return 'M';
  }
  for (const name of FEMALE_NAMES) {
    if (lower.includes(name)) return 'W';
  }

  return null;
}

/**
 * Select the best matching voice from available browser voices.
 */
export function selectVoice(info: VoiceInfo): SpeechSynthesisVoice | null {
  const voices = loadVoices();
  if (voices.length === 0) return null;

  const lang = (info.lang || 'de').toLowerCase();
  const gender = (info.gender || 'D').toUpperCase();

  // 1. Filter by language (voice.lang starts with lang, e.g. "de" matches "de-DE")
  let matching = voices.filter(v =>
    v.lang.toLowerCase().startsWith(lang)
  );

  // Fallback: try without region (e.g. "de" from "de-DE")
  if (matching.length === 0) {
    const langBase = lang.split('-')[0];
    matching = voices.filter(v =>
      v.lang.toLowerCase().startsWith(langBase)
    );
  }

  if (matching.length === 0) return voices[0]; // Last fallback

  // 2. Filter by gender if not neutral
  if (gender === 'M' || gender === 'W' || gender === 'F') {
    const targetGender = gender === 'F' ? 'W' : gender;
    const genderMatched = matching.filter(v => guessGender(v.name) === targetGender);
    if (genderMatched.length > 0) {
      matching = genderMatched;
    }
    // If no gender match found, keep all language-matched voices
  }

  // 3. Sort alphabetically by name for consistent selection across sessions
  matching.sort((a, b) => a.name.localeCompare(b.name));

  // 4. Select by voiceIndex modulo
  const index = (info.voiceIndex || 0) % matching.length;
  return matching[index];
}

/**
 * Parse voice definition string "voiceIndex:rate:pitch" into partial VoiceInfo.
 */
export function parseVoiceDef(voiceDef: string | null | undefined): { voiceIndex: number; rate: number; pitch: number } {
  if (!voiceDef || voiceDef.trim() === '') {
    return { voiceIndex: 0, rate: 1.0, pitch: 1.0 };
  }
  const parts = voiceDef.split(':');
  return {
    voiceIndex: parts.length > 0 ? parseInt(parts[0]) || 0 : 0,
    rate: parts.length > 1 ? parseFloat(parts[1]) || 1.0 : 1.0,
    pitch: parts.length > 2 ? parseFloat(parts[2]) || 1.0 : 1.0,
  };
}

export class VoiceSpeaker {
  private currentUtterance: SpeechSynthesisUtterance | null = null;
  private speaking = false;
  private chunks: string[] = [];
  private chunkIndex = 0;
  private currentVoiceInfo: VoiceInfo | null = null;

  private volume = 1.0;

  /**
   * Speak text with the given voice configuration.
   * Splits long text into sentences to avoid Chrome's 15s timeout.
   * @param volume 0.0-1.0 (default 1.0)
   */
  async speak(text: string, voice: VoiceInfo, volume: number = 1.0): Promise<void> {
    if (!window.speechSynthesis || !text) return;

    this.stop();
    this.currentVoiceInfo = voice;
    this.volume = clamp(volume, 0, 1);

    // Split into sentences for Chrome 15s workaround
    this.chunks = splitIntoSentences(text);
    this.chunkIndex = 0;
    this.speaking = true;

    await this.speakNextChunk();
  }

  /** Stop current speech */
  stop(): void {
    this.speaking = false;
    this.chunks = [];
    this.chunkIndex = 0;
    this.currentUtterance = null;
    if (window.speechSynthesis) {
      window.speechSynthesis.cancel();
    }
  }

  /** Check if currently speaking */
  isSpeaking(): boolean {
    return this.speaking;
  }

  private async speakNextChunk(): Promise<void> {
    if (!this.speaking || this.chunkIndex >= this.chunks.length || !this.currentVoiceInfo) {
      this.speaking = false;
      return;
    }

    const text = this.chunks[this.chunkIndex];
    this.chunkIndex++;

    return new Promise<void>((resolve) => {
      const utterance = new SpeechSynthesisUtterance(text);
      this.currentUtterance = utterance;

      // Set voice
      const selectedVoice = selectVoice(this.currentVoiceInfo!);
      if (selectedVoice) {
        utterance.voice = selectedVoice;
        utterance.lang = selectedVoice.lang;
      }

      // Set parameters
      utterance.rate = clamp(this.currentVoiceInfo!.rate || 1.0, 0.1, 10);
      utterance.pitch = clamp(this.currentVoiceInfo!.pitch || 1.0, 0.5, 2.0);
      utterance.volume = this.volume;

      utterance.onend = () => {
        if (this.speaking) {
          this.speakNextChunk().then(resolve);
        } else {
          resolve();
        }
      };

      utterance.onerror = (e) => {
        console.warn('Speech synthesis error:', e.error);
        this.speaking = false;
        resolve();
      };

      window.speechSynthesis.speak(utterance);
    });
  }
}

/** Split text into sentences, keeping chunks under ~200 chars to stay within Chrome limits */
function splitIntoSentences(text: string): string[] {
  // Split on sentence-ending punctuation followed by space
  const raw = text.match(/[^.!?]+[.!?]+[\s]*/g) || [text];

  // Merge very short chunks, split very long ones
  const chunks: string[] = [];
  let current = '';

  for (const segment of raw) {
    if (current.length + segment.length > 200 && current.length > 0) {
      chunks.push(current.trim());
      current = segment;
    } else {
      current += segment;
    }
  }
  if (current.trim()) {
    chunks.push(current.trim());
  }

  return chunks.length > 0 ? chunks : [text];
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}
