# THIN_INSTANCES - Verwendungsanleitung

## Übersicht

`THIN_INSTANCES` ist ein neuer Shape-Typ für extrem performantes Rendering von Gras, Blättern und ähnlichen Objekten.

**Vorteile:**
- ✅ **Extreme Performance**: 100.000+ Instanzen möglich
- ✅ **Y-Axis Billboard**: Bleibt vertikal (sobald Shader implementiert)
- ✅ **GPU Wind-Animation**: Läuft auf GPU (sobald Shader implementiert)
- ✅ **Konfigurierbare Anzahl**: Via `shaderParameters`

## Verwendung

### 1. BlockType Definition (JSON)

```json
{
  "id": 500,
  "name": "grass_thin",
  "blockType": {
    "visibility": {
      "shape": 25,
      "textures": {
        "0": {
          "path": "grass_blade.png",
          "shaderParameters": "150"
        }
      }
    }
  }
}
```

**Wichtige Properties:**
- `shape: 25` - Das ist `Shape.THIN_INSTANCES`
- `textures[0].path` - Pfad zur Textur (z.B. Grashalm-Bild)
- `textures[0].shaderParameters` - **Anzahl der Instanzen** (z.B. "150" = 150 Grashalme pro Block)

### 2. Beispiel: Spärliches Gras

```json
{
  "id": 501,
  "name": "sparse_grass",
  "blockType": {
    "visibility": {
      "shape": 25,
      "textures": {
        "0": {
          "path": "textures/block/grass_blade.png",
          "shaderParameters": "50"
        }
      }
    }
  }
}
```
→ 50 Grashalme pro Block

### 3. Beispiel: Dichtes Gras

```json
{
  "id": 502,
  "name": "dense_grass",
  "blockType": {
    "visibility": {
      "shape": 25,
      "textures": {
        "0": {
          "path": "textures/block/grass_blade.png",
          "shaderParameters": "300"
        }
      }
    }
  }
}
```
→ 300 Grashalme pro Block

### 4. Beispiel: Blätter

```json
{
  "id": 503,
  "name": "falling_leaves",
  "blockType": {
    "visibility": {
      "shape": 25,
      "textures": {
        "0": {
          "path": "textures/block/leaf.png",
          "shaderParameters": "80"
        }
      }
    }
  }
}
```
→ 80 Blätter pro Block

## Parameter

### shaderParameters (String)
**Format**: Zahl als String (z.B. `"100"`)

**Bedeutung**: Anzahl der Instanzen pro Block

**Empfohlene Werte:**
- `"30-50"` - Sehr spärlich (z.B. einzelne Blumen)
- `"50-100"` - Spärlich (z.B. wildes Gras)
- `"100-200"` - Normal (z.B. Wiese)
- `"200-400"` - Dicht (z.B. dichter Rasen)
- `"400-1000"` - Sehr dicht (z.B. Dschungel) - **Performance testen!**

**Standard**: `100` (wenn nicht angegeben)

## Textur-Anforderungen

### Empfohlene Textur-Eigenschaften:
- **Format**: PNG mit Alpha-Kanal
- **Größe**: 16x16, 32x32, oder 64x64 Pixel
- **Inhalt**: Einzelner Grashalm, Blatt, oder ähnliches Objekt
- **Transparenz**: Alpha-Kanal für Umriss
- **Ausrichtung**: Vertikal (steht nach oben)

### Beispiel-Texturen:
```
textures/block/grass_blade.png     - Einzelner Grashalm
textures/block/leaf_oak.png        - Eichenblatt
textures/block/flower_small.png    - Kleine Blume
textures/block/wheat.png           - Weizenhalm
```

## Positionierung

Die Instanzen werden **zufällig** innerhalb des Block-Bereichs verteilt:
- **X/Z**: Zufällig über 80% der Block-Fläche (Rand frei)
- **Y**: Am Boden des Blocks (`blockY`)
- **Verteilung**: Gleichmäßig zufällig

## Performance

### Vergleich mit SPRITE

| Feature | SPRITE | THIN_INSTANCES |
|---------|--------|----------------|
| Max Instanzen | ~10.000 | 100.000+ |
| Performance | CPU-basiert | GPU-basiert |
| Billboard Mode | Full (alle Achsen) | Y-axis only* |
| Wind Animation | CPU | GPU* |
| Memory | Höher | Niedriger |

*Sobald Shader implementiert ist

### Performance-Tipps

1. **Starten Sie mit niedrigen Werten** (50-100) und erhöhen Sie schrittweise
2. **Testen Sie die FPS** mit unterschiedlichen Werten
3. **Verwenden Sie kleinere Texturen** (16x16 statt 64x64) für bessere Performance
4. **Kombinieren Sie mit LOD**: Ferne Chunks weniger Instanzen

## Debugging

### Console Commands

```javascript
// Statistik abrufen
const stats = appContext.services.thinInstances.getStats();
console.log('ThinInstances:', stats);
// Output: { chunkCount: 5, totalInstances: 15000, groupCount: 30 }

// Wireframe-Modus (um Geometrie zu sehen)
wireframe(true);
```

### Was zu überprüfen:

1. **Werden Instanzen erstellt?**
   - Suchen Sie in Console-Logs nach: `[ThinInstancesService] Thin instances created`

2. **Wie viele Instanzen?**
   - Log zeigt: `count: 150` (Ihre shaderParameters)

3. **Ist die Textur geladen?**
   - Log zeigt: `Created fallback material for thin instances`

## Aktuelle Limitationen

### ⏳ Noch nicht implementiert (TODO):

1. **Y-Axis Billboard Shader**
   - Aktuell: Instanzen drehen sich nicht zur Kamera
   - TODO: NodeMaterial Shader implementieren
   - Referenz: https://nme.babylonjs.com/#8WH2KS#22

2. **GPU Wind-Animation**
   - Aktuell: Keine Wind-Animation
   - TODO: Vertex Shader Displacement

3. **Per-Instance Variations**
   - Aktuell: Alle Instanzen gleich groß
   - TODO: Random Size/Rotation im Shader

### ✅ Aktuell verfügbar:

- Thin Instance Rendering (extrem performant)
- Konfigurierbare Anzahl via `shaderParameters`
- Random Positioning
- Standard Material mit Textur
- Automatic Disposal bei Chunk Unload

## Nächste Schritte

1. ✅ **Basis-System implementiert** - THIN_INSTANCES Shape funktioniert
2. ⏳ **Y-Axis Billboard Shader erstellen** (NodeMaterial)
3. ⏳ **Wind-Animation im Shader** (GPU-basiert)
4. ⏳ **Weitere Features**: Size Variation, Rotation, LOD

## Beispiel-Workflow

### 1. Block erstellen
```json
{
  "id": 500,
  "name": "test_grass",
  "blockType": {
    "visibility": {
      "shape": 25,
      "textures": {
        "0": {
          "path": "grass_blade.png",
          "shaderParameters": "100"
        }
      }
    }
  }
}
```

### 2. Block platzieren
Im Editor: Block mit ID 500 platzieren

### 3. Erwartetes Verhalten
- 100 vertikale Quads werden erstellt
- Zufällig über die Block-Fläche verteilt
- Textur `grass_blade.png` wird geladen
- Instanzen stehen am Boden des Blocks

### 4. Testen & Optimieren
- FPS messen
- `shaderParameters` anpassen (50, 100, 200, etc.)
- Verschiedene Texturen testen

## Zusammenfassung

**THIN_INSTANCES** ist perfekt für:
- 🌿 Gras und Pflanzen
- 🍂 Blätter und Laub
- 🌾 Getreide und Feldfrüchte
- ✨ Partikel-artige Dekorationen

**Verwenden Sie stattdessen:**
- `SPRITE` - Für vollständige Billboards mit CPU Wind-Animation
- `BILLBOARD` - Für einzelne Y-Axis Billboards
- `CUBE` - Für feste 3D-Objekte
