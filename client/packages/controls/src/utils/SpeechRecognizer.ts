/**
 * Browser Speech-to-Text utility using Web Speech API (SpeechRecognition).
 * Converts voice input to text. Reusable across Dialog Widget, Chat, and other components.
 */

// TypeScript declarations for SpeechRecognition (not in all type libs)
interface SpeechRecognitionEvent extends Event {
  results: SpeechRecognitionResultList;
  resultIndex: number;
}

interface SpeechRecognitionResultList {
  length: number;
  item(index: number): SpeechRecognitionResult;
  [index: number]: SpeechRecognitionResult;
}

interface SpeechRecognitionResult {
  isFinal: boolean;
  length: number;
  item(index: number): SpeechRecognitionAlternative;
  [index: number]: SpeechRecognitionAlternative;
}

interface SpeechRecognitionAlternative {
  transcript: string;
  confidence: number;
}

interface SpeechRecognitionInstance extends EventTarget {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  maxAlternatives: number;
  start(): void;
  stop(): void;
  abort(): void;
  onresult: ((event: SpeechRecognitionEvent) => void) | null;
  onerror: ((event: any) => void) | null;
  onend: (() => void) | null;
  onstart: (() => void) | null;
}

declare global {
  interface Window {
    SpeechRecognition: new () => SpeechRecognitionInstance;
    webkitSpeechRecognition: new () => SpeechRecognitionInstance;
  }
}

export type RecognitionCallback = (text: string, isFinal: boolean) => void;

/**
 * Check if speech recognition is available in this browser.
 */
export function isSpeechRecognitionAvailable(): boolean {
  return typeof window !== 'undefined' &&
    (window.SpeechRecognition !== undefined || window.webkitSpeechRecognition !== undefined);
}

export class SpeechRecognizer {
  private recognition: SpeechRecognitionInstance | null = null;
  private listening = false;
  private lang: string;
  private onResult: RecognitionCallback | null = null;
  private onStateChange: ((listening: boolean) => void) | null = null;
  private onError: ((error: string) => void) | null = null;
  private failed = false;

  constructor(lang: string = 'de-DE') {
    this.lang = lang;
  }

  /** Set the language for recognition */
  setLang(lang: string): void {
    this.lang = lang;
    if (this.recognition) {
      this.recognition.lang = lang;
    }
  }

  /** Check if currently listening */
  isListening(): boolean {
    return this.listening;
  }

  /** Returns true if recognition permanently failed (e.g. blocked by browser) */
  hasFailed(): boolean {
    return this.failed;
  }

  /**
   * Start listening for speech input.
   * @param onResult Called with recognized text (interim and final results)
   * @param onStateChange Called when listening state changes
   * @param onError Called when a fatal error occurs (not-allowed, network, etc.)
   */
  start(onResult: RecognitionCallback, onStateChange?: (listening: boolean) => void,
        onError?: (error: string) => void): void {
    if (this.listening) {
      this.stop();
      return;
    }

    if (this.failed) {
      onError?.('Speech recognition not available');
      return;
    }

    if (!isSpeechRecognitionAvailable()) {
      console.warn('Speech recognition not available in this browser');
      return;
    }

    const SpeechRecognitionCtor = window.SpeechRecognition || window.webkitSpeechRecognition;
    this.recognition = new SpeechRecognitionCtor();
    this.recognition.continuous = true;
    this.recognition.interimResults = true;
    this.recognition.lang = this.lang;
    this.recognition.maxAlternatives = 1;
    this.onResult = onResult;
    this.onStateChange = onStateChange || null;
    this.onError = onError || null;

    this.recognition.onstart = () => {
      this.listening = true;
      this.onStateChange?.(true);
    };

    this.recognition.onresult = (event: SpeechRecognitionEvent) => {
      let interimTranscript = '';
      let finalTranscript = '';

      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        if (result.isFinal) {
          finalTranscript += result[0].transcript;
        } else {
          interimTranscript += result[0].transcript;
        }
      }

      if (finalTranscript) {
        this.onResult?.(finalTranscript, true);
      } else if (interimTranscript) {
        this.onResult?.(interimTranscript, false);
      }
    };

    this.recognition.onerror = (event: any) => {
      const err = event.error;
      console.warn('Speech recognition error:', err);

      // Fatal errors — permanently disable recognition
      if (err === 'not-allowed' || err === 'network' || err === 'service-not-allowed') {
        this.failed = true;
        this.stopInternal();
        this.onError?.(err);
        return;
      }

      // 'no-speech' and 'aborted' are normal — don't stop
      if (err !== 'no-speech' && err !== 'aborted') {
        this.stopInternal();
      }
    };

    this.recognition.onend = () => {
      // Auto-restart if still supposed to be listening (browser may stop after silence)
      if (this.listening) {
        try {
          this.recognition?.start();
        } catch {
          this.stopInternal();
        }
      }
    };

    try {
      this.recognition.start();
    } catch (e) {
      console.warn('Failed to start speech recognition:', e);
      this.stopInternal();
    }
  }

  /** Stop listening */
  stop(): void {
    this.stopInternal();
  }

  /** Toggle listening on/off */
  toggle(onResult: RecognitionCallback, onStateChange?: (listening: boolean) => void,
         onError?: (error: string) => void): void {
    if (this.listening) {
      this.stop();
    } else {
      this.start(onResult, onStateChange, onError);
    }
  }

  private stopInternal(): void {
    this.listening = false;
    try {
      this.recognition?.stop();
    } catch { /* ignore */ }
    this.recognition = null;
    this.onStateChange?.(false);
  }
}
