# AnimationStack Usage Examples

## Overview

AnimationStacks wurden für folgende Environment-Eigenschaften erstellt:
- `ambientLightIntensity` - Ambient light intensity (EnvironmentService)
- `sunPosition` - Sun horizontal position in degrees (SunService)
- `sunElevation` - Sun vertical elevation in degrees (SunService)
- `horizonGradientAlpha` - Horizon gradient transparency (HorizonGradientService)

## Command Usage

### listStacks

Listet alle verfügbaren ModifierStacks mit ihren aktuellen Werten auf.

**Syntax:**
```
listStacks [verbose]
```

**Parameter:**
- `verbose`: Optional - Zeigt detaillierte Informationen inklusive aller Modifier (Standard: false)

**Beispiele:**

```bash
# Basis-Information über alle Stacks
listStacks

# Detaillierte Informationen mit allen Modifiern
listStacks verbose

# Kurzform für verbose
listStacks v
```

**Ausgabe:**
```
================================================================================
Available Modifier Stacks (9 total)
================================================================================

Stack: playerViewMode
  Type: ModifierStack
  Current Value: true
  Default Value: true
  Active Modifiers: 0

Stack: ambientLightIntensity
  Type: AnimationStack ⏱
  Current Value: 0.500
  Default Value: 1.000
  Active Modifiers: 2
  Modifiers:
    [0] Name: "weather", Value: 0.500, Priority: 10, Enabled: true, Sequence: 5
    [1] Name: "time_of_day", Value: 0.800, Priority: 20, Enabled: true, Sequence: 8

...

Summary:
  Total Stacks: 9
  - AnimationStacks: 4
  - ModifierStacks: 5
  Total Active Modifiers: 3
```

### setStackModifier

Setzt oder aktualisiert einen Modifier in einem Stack mit optionaler Animation.

**Syntax:**
```
setStackModifier <stackName> <modifierName> <value> [prio] [waitTime]
```

**Parameter:**
- `stackName`: Name des Stacks (z.B. 'ambientLightIntensity', 'sunPosition')
- `modifierName`: Name/ID für den Modifier (zum Update oder Erstellen)
  - **Verwende leeren String `''` um den Default-Wert direkt zu setzen**
- `value`: Der zu setzende Wert (Typ abhängig vom Stack)
- `prio`: Optional - Priorität (Standard: 50, niedriger = höhere Priorität)
  - *Wird ignoriert wenn modifierName leer ist*
- `waitTime`: Optional - Wartezeit in Millisekunden für AnimationStack (Standard: 100ms)

**Beispiele:**

```bash
# Named Modifier: Ambient light intensity animiert von aktuell auf 0.5 setzen
setStackModifier ambientLightIntensity weather 0.5 10

# Named Modifier: Mit custom wait time (langsamere Animation)
setStackModifier ambientLightIntensity weather 0.8 10 500

# Named Modifier: Sonne auf Sonnenuntergang-Position animieren (West = 270°)
setStackModifier sunPosition sunset 270 20 200

# Named Modifier: Sonne zum Horizont animieren
setStackModifier sunElevation sunset 0 20 300

# Named Modifier: Horizont-Gradient ausblenden (transparent)
setStackModifier horizonGradientAlpha night 0.0 10 200

# Named Modifier: Horizont-Gradient einblenden
setStackModifier horizonGradientAlpha day 0.7 10 200

# ===== DEFAULT-WERT SETZEN (ohne named modifier) =====

# Default-Wert direkt setzen (leerer modifierName)
setStackModifier ambientLightIntensity '' 1.0

# Default-Wert mit Animation (waitTime wird beachtet)
setStackModifier sunPosition '' 90 0 500

# Default-Wert ändern ohne Animation
setStackModifier horizonGradientAlpha '' 0.5
```

### getStackModifierCurrentValue

Holt den aktuellen effektiven Wert eines Stacks.

**Syntax:**
```
getStackModifierCurrentValue <stackName>
```

**Beispiele:**

```bash
# Aktuellen Wert der ambient light intensity abrufen
getStackModifierCurrentValue ambientLightIntensity

# Aktuelle Sonnenposition abrufen
getStackModifierCurrentValue sunPosition

# Aktuelle Sonnenelevation abrufen
getStackModifierCurrentValue sunElevation

# Aktuelle Horizont-Gradient Alpha abrufen
getStackModifierCurrentValue horizonGradientAlpha
```

## Scenario Examples

### Tag/Nacht-Übergang

```bash
# Tagesanbruch (Dawn)
setStackModifier ambientLightIntensity time_of_day 0.6 5 1000
setStackModifier sunPosition time_of_day 90 5 2000    # Osten
setStackModifier sunElevation time_of_day 15 5 2000   # Niedrig über Horizont
setStackModifier horizonGradientAlpha time_of_day 0.8 5 1000

# Mittag (Noon)
setStackModifier ambientLightIntensity time_of_day 1.0 5 1000
setStackModifier sunPosition time_of_day 180 5 2000   # Süden
setStackModifier sunElevation time_of_day 80 5 2000   # Hoch am Himmel
setStackModifier horizonGradientAlpha time_of_day 0.3 5 1000

# Sonnenuntergang (Sunset)
setStackModifier ambientLightIntensity time_of_day 0.4 5 1000
setStackModifier sunPosition time_of_day 270 5 2000   # Westen
setStackModifier sunElevation time_of_day 5 5 2000    # Knapp über Horizont
setStackModifier horizonGradientAlpha time_of_day 0.9 5 1000

# Nacht (Night)
setStackModifier ambientLightIntensity time_of_day 0.1 5 1000
setStackModifier sunElevation time_of_day -20 5 2000  # Unter Horizont
setStackModifier horizonGradientAlpha time_of_day 0.2 5 1000
```

### Wetter-Effekte

```bash
# Sturm (dunkler, dramatisch)
setStackModifier ambientLightIntensity weather 0.3 10 500
setStackModifier horizonGradientAlpha weather 0.9 10 500

# Klarer Tag (hell, klar)
setStackModifier ambientLightIntensity weather 1.2 10 500
setStackModifier horizonGradientAlpha weather 0.4 10 500

# Nebel (gedämpft)
setStackModifier ambientLightIntensity weather 0.6 10 500
setStackModifier horizonGradientAlpha weather 0.95 10 500
```

### Dynamische Anpassungen

```bash
# Schnelle Anpassung (kurze waitTime)
setStackModifier ambientLightIntensity emergency 0.2 1 50

# Langsame, sanfte Anpassung (lange waitTime)
setStackModifier sunElevation cinematic 45 5 2000

# Sofortige Änderung (waitTime 0 oder sehr klein)
setStackModifier ambientLightIntensity instant 1.0 1 1
```

## Animation Characteristics

Die AnimationStacks verwenden **lineare Interpolation** für gleichmäßige Übergänge:

- **Ambient Light Intensity**: 0.01 pro Schritt (ca. 10 Sekunden für 0.0 → 1.0 bei 100ms Intervall)
- **Sun Position**: 1.0° pro Schritt (ca. 36 Sekunden für 0° → 360° bei 100ms Intervall)
- **Sun Elevation**: 0.5° pro Schritt (ca. 36 Sekunden für -90° → 90° bei 100ms Intervall)
- **Horizon Gradient Alpha**: 0.01 pro Schritt (ca. 10 Sekunden für 0.0 → 1.0 bei 100ms Intervall)

**Lineare Animation bedeutet:**
- Konstante Geschwindigkeit während der gesamten Animation
- Vorhersagbare Animationsdauer
- Gleichmäßige Bewegung ohne Beschleunigung/Verlangsamung
- Ideal für kontinuierliche Änderungen wie Tag/Nacht-Zyklen

**Wichtig:** Neue AnimationModifier starten immer vom **aktuellen Stack-Wert** und animieren dann zum Zielwert. Das sorgt für smooth Übergänge ohne Sprünge.

```bash
# Beispiel: Stack ist aktuell bei 0.5
# Neuer Modifier mit Ziel 1.0 startet bei 0.5 und animiert zu 1.0
setStackModifier ambientLightIntensity weather 1.0 10 200
# → Animiert von 0.5 zu 1.0 (nicht von 1.0 zu 1.0!)
```

## Priority System

Niedrigere Prioritätswerte = höhere Priorität:

- **0-10**: System-kritische Modifiers (z.B. Cutscenes, Story-Events)
- **11-30**: Spezielle Events (z.B. Zauber, Effekte)
- **31-50**: Normale Modifiers (z.B. Zeit des Tages, Wetter)
- **51-100**: Niedrige Priorität (z.B. Ambient-Effekte)

Wenn mehrere Modifier dieselbe Priorität haben, gewinnt der neueste.

## Default-Wert vs Named Modifier

### Wann Default-Wert verwenden?

**Default-Wert (`modifierName = ''`):**
- Ändert den Basis-/Fallback-Wert des Stacks
- Gilt, wenn **keine anderen Modifier aktiv** sind
- Ideal für permanente Basiswerte
- Keine Priorität nötig (wird nur verwendet wenn Stack leer ist)

```bash
# Beispiel: Basis-Intensität permanent auf 0.8 setzen
setStackModifier ambientLightIntensity '' 0.8
```

**Named Modifier:**
- Temporäre Überschreibung des Default-Werts
- Mit Priorität steuerbar
- Kann jederzeit entfernt werden
- Ideal für temporäre Effekte (Wetter, Events, etc.)

```bash
# Beispiel: Temporärer Wetter-Effekt überschreibt den Default
setStackModifier ambientLightIntensity weather 0.3 10
```

### Beispiel-Szenario:

```bash
# 1. Setze Basis-Licht auf 0.8 (Default)
setStackModifier ambientLightIntensity '' 0.8

# 2. Wetter-System setzt dunkleres Licht (überschreibt Default)
setStackModifier ambientLightIntensity weather 0.3 30

# Aktueller Wert: 0.3 (weather gewinnt)

# 3. Wetter-Modifier entfernen
# (würde zurück zu Default 0.8 fallen, nicht zu ursprünglich 1.0!)

# 4. Event mit höherer Priorität
setStackModifier ambientLightIntensity boss_fight 0.1 5

# Aktueller Wert: 0.1 (boss_fight hat höchste Priorität)
```

## Modifier Names Tracking

Mit `listStacks verbose` kannst du sehen, welche named modifiers aktiv sind:

```bash
# Mehrere Modifier setzen
setStackModifier ambientLightIntensity weather 0.3 30
setStackModifier ambientLightIntensity time_of_day 0.8 10
setStackModifier ambientLightIntensity spell_effect 0.1 5

# Liste mit verbose anzeigen
listStacks verbose

# Ausgabe zeigt alle modifier Namen:
# Stack: ambientLightIntensity
#   Type: AnimationStack ⏱
#   Current Value: 0.100  (spell_effect gewinnt wegen Priorität 5)
#   Default Value: 1.000
#   Active Modifiers: 3
#   Modifiers:
#     [0] Name: "spell_effect", Value: 0.100, Priority: 5, Enabled: true, Sequence: 3
#     [1] Name: "time_of_day", Value: 0.800, Priority: 10, Enabled: true, Sequence: 2
#     [2] Name: "weather", Value: 0.300, Priority: 30, Enabled: true, Sequence: 1
```

**Wichtig:** Modifier ohne Namen (z.B. direkt über ModifierService erstellt) werden als "(unnamed)" angezeigt.

## Integration mit Scrawl Scripts

AnimationStacks können perfekt in Scrawl Scripts integriert werden:

```json
{
  "actions": [
    {
      "command": "setStackModifier",
      "params": ["ambientLightIntensity", "script_dawn", "0.6", "5", "1000"]
    },
    {
      "command": "wait",
      "params": ["2000"]
    },
    {
      "command": "setStackModifier",
      "params": ["sunElevation", "script_dawn", "15", "5", "2000"]
    }
  ]
}
```
