# Evaluate Test System

Dieses Modul dient als permanente Testumgebung für das `generate-ts-to-java-maven-plugin`.

## Zweck

- **Kontinuierliche Tests**: Automatisierte Validierung der Plugin-Funktionalität
- **Regressions-Tests**: Sicherstellen, dass Änderungen bestehende Features nicht brechen  
- **Feature-Entwicklung**: Testumgebung für neue Plugin-Features
- **Realistische Tests**: Verwendung echter TypeScript-Strukturen aus dem Projekt

## Struktur

```
evaluate/
├── ts/                           # Test-TypeScript-Dateien (Input)
│   ├── network/
│   │   └── MessageTypes.ts      # Enum-Tests (String, Numeric, Mixed)
│   ├── types/                   # Interface/Class-Tests  
│   ├── configs/                 # Configuration-Tests
│   └── ...
├── src/main/java/               # Generierte Java-Dateien (Output)
│   └── de/mhus/nimbus/evaluate/generated/
└── pom.xml                      # Maven-Konfiguration
```

## Test-Enums (Enum-Wert-Behandlung)

### MessageType (String-Werte)
```typescript
export enum MessageType {
  LOGIN = 'login',
  PING = 'p',
  // ...
}
```
→ Java: `String tsIndex` mit korrekten String-Werten

### Priority (Numerische Werte)  
```typescript
export enum Priority {
  LOW = 0,
  MEDIUM = 1,
  HIGH = 2,
  CRITICAL = 5
}
```
→ Java: `int tsIndex` mit korrekten numerischen Werten

### MixedEnum (Gemischte Typen)
```typescript
export enum MixedEnum {
  STRING_VAL = 'text',
  NUMERIC_VAL = 42,
  ANOTHER_STRING = 'hello'
}
```  
→ Java: `String tsIndex` (Fallback) mit allen Werten als Strings

## Tests ausführen

```bash
# Plugin installieren
cd ../
mvn clean install

# Code regenerieren
cd evaluate/
mvn clean generate-sources

# Generierte Dateien prüfen
ls src/main/java/de/mhus/nimbus/evaluate/generated/

# Integration Tests (aus Plugin-Verzeichnis)
cd ../
mvn test -Dtest=EvaluatePluginIT
```

## Neue Tests hinzufügen

1. **TypeScript hinzufügen**: Neue .ts Dateien in `ts/` erstellen
2. **Erwartungen definieren**: Java-Generierung mental durchdenken
3. **Test erweitern**: `EvaluatePluginIT.validateXXX()` Methoden erweitern
4. **Validieren**: Tests ausführen und Ergebnisse prüfen

## Konfiguration

Die Konfiguration erfolgt über:
- `ts-to-java.yaml` - Haupt-Konfiguration (alle Features)
- `ts-to-java-types.yaml` - Nur Types (für spezielle Tests)
- `ts-to-java-messages.yaml` - Nur Messages (für spezielle Tests)

## Zweck der verschiedenen Konfigurationsdateien

- **Vollständige Tests**: Alle TypeScript-Features gleichzeitig testen
- **Isolierte Tests**: Einzelne Features isoliert testen (Performance, Debugging)
- **Verschiedene Szenarien**: Unterschiedliche Konfigurationen testen

---

*Dieses Test-System wurde im Rahmen der Enum-Wert-Behandlung-Implementierung (Dezember 2025) erweitert.*
