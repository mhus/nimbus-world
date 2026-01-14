- Wenn debuging im focus ausfgegeben werden soll, dann immer log.info() nutzen, da log.debug() zu viel ausgabe ist und so besser im focus debugt werden kann.
- Alle Kommentare und variablen in source dateien in englischer Sprache halten.
- Spreche sonst deutsch mit mir

## Type Conversion in Commands

- **WICHTIG**: Alle Commands müssen Parameter mit CastUtil konvertieren (aus @nimbus/shared)
- Commands können Parameter in jedem Typ erhalten (string, number, boolean, object, etc.)
- Verfügbare Conversion-Funktionen:
  - `toBoolean(any)`: Konvertiert zu boolean (versteht 'true', 'false', 1, 0, 'yes', 'no', etc.)
  - `toString(any)`: Konvertiert zu string (funktioniert mit allen Typen)
  - `toNumber(any)`: Konvertiert zu number (versteht strings, booleans, null, etc.)
  - `toObject(any)`: Konvertiert zu object (versucht JSON.parse() bei strings, gibt {} bei Fehlern zurück)

### Beispiel Command Implementation:

```typescript
import { CommandHandler } from '../CommandHandler';
import { toBoolean, toNumber, toString, toObject } from '@nimbus/shared';

export class ExampleCommand extends CommandHandler {
  async execute(parameters: any[]): Promise<string> {
    // Boolean parameter
    const enabled = toBoolean(parameters[0]);

    // Number parameter
    const value = toNumber(parameters[1]);

    // String parameter
    const name = toString(parameters[2]);

    // Object parameter (parses JSON strings automatically)
    const config = toObject(parameters[3]);

    // Use converted values...
  }
}
```

### Warum CastUtil verwenden?

- Scripte können Parameter in verschiedenen Typen übergeben (JSON: boolean, number, string)
- Commands sollen flexibel sein und alle Typen akzeptieren
- CastUtil stellt konsistente Konvertierung sicher
- NIEMALS `.toLowerCase()` oder andere String-Methoden direkt auf parameters[0] aufrufen!
