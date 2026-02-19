# World Generator Composer Model - Complete Documentation

## 1. Introduction

### Purpose

Das **Composer Model** ist das zentrale Konfigurationssystem des World Generators. Es ermöglicht die deklarative Definition von Welten durch JSON-Dokumente. Das Model beschreibt geografische Features wie Biome, Dörfer, Flüsse, Straßen und Punkte, die automatisch in 3D-Welten umgewandelt werden.

### Use Cases

- **Prozedurale Weltgenerierung**: Automatische Erzeugung von Landschaften basierend auf Regeln
- **Narrative Weltdesign**: Manuelle Definition spezifischer Orte und Routen
- **Mixed Approach**: Kombination aus automatischer Generierung und manueller Platzierung

### Root Class: HexComposition

Die `HexComposition` ist der Einstiegspunkt jeder Weltdefinition. Sie enthält:
- Eine Liste von **Features** (Biome, Towns, Flows, Points)
- **Continents** für Gap-Filling zwischen Biomes
- Metadaten (name, worldId, version)

## 2. Architecture Overview

### Class Hierarchy

```
Feature (abstract base)
├── Area (abstract, extends Feature)
│   ├── Biome
│   │   ├── MountainBiome
│   │   ├── ForestBiome
│   │   ├── PlainsBiome
│   │   ├── DesertBiome
│   │   ├── SwampBiome
│   │   ├── CoastBiome
│   │   ├── IslandBiome
│   │   └── OceanBiome
│   └── Composite
├── Structure (abstract, extends Area)
│   ├── Town
├── Flow (abstract, extends Feature)
│   ├── River
│   ├── Road
│   ├── Wall
│   └── SideWall
└── Point (abstract, extends Feature)
    ├── PositionPoint (standard)
    ├── EdgePoint
    ├── OceanEdgePoint
    ├── TownConnectionPoint
    ├── VillagePoint
    ├── MountainPoint
    ├── SpikesPoint
    ├── MountainFacePoint
    └── LakesPoint
```

### Jackson Polymorphic Deserialization

Das Composer Model nutzt Jackson `@JsonTypeInfo` und `@JsonSubTypes` für polymorphe Deserialisierung:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "featureType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Biome.class, name = "biome"),
    @JsonSubTypes.Type(value = Town.class, name = "town"),
    @JsonSubTypes.Type(value = River.class, name = "river"),
    // ... etc
})
public abstract class Feature { ... }
```

Der **discriminator** `featureType` bestimmt die konkrete Klasse beim Deserialisieren.

### Feature Type Discriminators

| Discriminator | Java Class | Category |
|--------------|------------|----------|
| `biome` | Biome | Area |
| `mountain-biome` | MountainBiome | Area |
| `forest-biome` | ForestBiome | Area |
| `plains-biome` | PlainsBiome | Area |
| `desert-biome` | DesertBiome | Area |
| `swamp-biome` | SwampBiome | Area |
| `coast-biome` | CoastBiome | Area |
| `island-biome` | IslandBiome | Area |
| `ocean-biome` | OceanBiome | Area |
| `composite` | Composite | Area |
| `town` | Town | Structure |
| `river` | River | Flow |
| `road` | Road | Flow |
| `wall` | Wall | Flow |
| `sidewall` | SideWall | Flow |
| `point` | PositionPoint | Point |
| `edge` | EdgePoint | Point |
| `ocean-edge` | OceanEdgePoint | Point |
| `town-connection` | TownConnectionPoint | Point |
| `village-point` | VillagePoint | Point |
| `mountain-point` | MountainPoint | Point |
| `spikes-point` | SpikesPoint | Point |
| `mountain-face-point` | MountainFacePoint | Point |
| `lakes-point` | LakesPoint | Point |

## 3. Root Class: HexComposition

### Purpose

`HexComposition` ist der Container für eine komplette Weltdefinition. Sie orchestriert Features und Continents.

### Main Fields

```java
public class HexComposition {
    private String compositionId;      // Unique ID (auto-generated if null)
    private String name;               // Technical name
    private String title;              // Display name
    private String worldId;            // Target world ID
    private List<Feature> features;    // All features (biomes, towns, flows, points)
    private List<Continent> continents; // Continent definitions for gap-filling
    private String version;            // Schema version (default: "1.0.0")
    private String description;        // Human-readable description
    private Map<String, String> metadata; // Custom metadata
    private FeatureStatus status;      // Composition status
}
```

### Example JSON Structure

```json
{
  "name": "middle-earth",
  "worldId": "world-01",
  "title": "Middle Earth",
  "description": "Fantasy world inspired by Tolkien",
  "version": "1.0.0",
  "continents": [
    {
      "continentId": "main-continent",
      "name": "Main Continent",
      "biomeType": "MOUNTAINS",
      "minNeighbors": 2
    }
  ],
  "features": [
    { "featureType": "biome", ... },
    { "featureType": "town", ... },
    { "featureType": "river", ... }
  ]
}
```

## 4. Feature Types Reference

### 4.1 Common Feature Fields

Alle Feature-Typen erben diese Basisfelder von `Feature`:

```java
private String featureId;           // Unique ID (auto-generated)
private String name;                // Technical name (unique within world)
private String title;               // Display name
private FeatureStatus status;       // NEW, COMPOSED, CREATED
private Boolean enabled;            // Enable/disable this feature
private String description;         // Human-readable description
private Map<String, String> metadata; // Custom metadata
```

### 4.2 Area-Based Features

#### Biome (Base Class)

**Feature Type**: `biome`
**Java Class**: `de.mhus.nimbus.world.generator.composer.biome.Biome`
**Parent**: `Area`

**Purpose**: Definiert geografische Regionen mit spezifischem Terrain-Typ.

**Key Properties**:
```java
private BiomeType type;           // PLAINS, FOREST, MOUNTAINS, DESERT, SWAMP, COAST, OCEAN
private Map<String, String> parameters; // Terrain generation parameters
```

Erbt von `Area`:
```java
private AreaShape shape;          // CIRCLE, LINE, RECTANGLE
private AreaSize size;            // SMALL, MEDIUM, LARGE, WIDE
private Integer sizeFrom;         // Explicit size min (overrides size enum)
private Integer sizeTo;           // Explicit size max (overrides size enum)
private List<RelativePosition> positions; // Positioning rules
private String continentId;       // Continent this biome belongs to
```

**Example JSON**:
```json
{
  "featureType": "biome",
  "name": "west-region",
  "title": "Western Plains",
  "type": "PLAINS",
  "shape": "CIRCLE",
  "size": "LARGE",
  "continentId": "main-continent",
  "positions": [
    {
      "direction": "W",
      "distanceFrom": 0,
      "anchor": "origin",
      "priority": 10
    }
  ],
  "parameters": {
    "g_asl": "10",
    "g_roughness": "0.5"
  }
}
```

#### MountainBiome

**Feature Type**: `mountain-biome`
**Java Class**: `de.mhus.nimbus.world.generator.composer.biome.MountainBiome`
**Parent**: `Biome`

**Purpose**: Spezialisiertes Gebirgs-Biome mit Höhenkonfiguration.

**Additional Properties**:
```java
private MountainHeight height;    // LOW_HILLS, MEDIUM_PEAKS, HIGH_PEAKS, EXTREME_PEAKS
```

**Example JSON**:
```json
{
  "featureType": "mountain-biome",
  "name": "central-mountains",
  "title": "Central Mountains",
  "type": "MOUNTAINS",
  "height": "MEDIUM_PEAKS",
  "shape": "CIRCLE",
  "size": "MEDIUM",
  "positions": [
    {
      "direction": "E",
      "distanceFrom": 3,
      "distanceTo": 3,
      "anchor": "west-region"
    }
  ]
}
```

#### ForestBiome

**Feature Type**: `forest-biome`
**Java Class**: `de.mhus.nimbus.world.generator.composer.biome.ForestBiome`
**Parent**: `Biome`

**Purpose**: Waldgebiete mit Baum-Dichte-Konfiguration. Nutzt ForestBuilder für sanft hügeliges Terrain mit GRASS/DIRT Mischung.

**Additional Properties**:
```java
private ForestDensity density;    // SPARSE, LIGHT, DENSE, OLD_GROWTH
private GroundType groundType;    // Material type (DEFAULT, SNOWY, SANDY, etc.)
```

**ForestDensity Presets**:
- `SPARSE` (0.4): Lichter Wald, flora_density=0.4, g_offset=3, 10% DIRT
- `LIGHT` (0.6): Normaler Wald, flora_density=0.6, g_offset=5, 20% DIRT
- `DENSE` (0.8): Dichter Wald, flora_density=0.8, g_offset=5, 30% DIRT [Default]
- `OLD_GROWTH` (0.9): Urwald, flora_density=0.9, g_offset=7, 40% DIRT

**Example JSON**:
```json
{
  "featureType": "forest-biome",
  "name": "ancient-forest",
  "type": "FOREST",
  "density": "OLD_GROWTH",
  "groundType": "DEFAULT",
  "shape": "CIRCLE",
  "size": "LARGE"
}
```

#### PlainsBiome

**Feature Type**: `plains-biome`
**Java Class**: `de.mhus.nimbus.world.generator.composer.biome.PlainsBiome`
**Parent**: `Biome`

**Purpose**: Ebenes Grasland mit optionalen Seen. Nutzt PlainsBuilder für sehr flaches Terrain mit GRASS/DIRT Oberfläche.

**Additional Properties**:
```java
private PlainsVariation variation; // FLAT, ROLLING, MEADOW, STEPPE
private GroundType groundType;     // Material type (DEFAULT, GRASSY, etc.)
```

**PlainsVariation Presets**:
- `FLAT` (g_offset=2): Fast flach, 5% DIRT, keine Seen
- `ROLLING` (g_offset=5): Sanft hügelig, 10% DIRT, Seen aktiv [Default]
- `MEADOW` (g_offset=7): Variierte Wiesen, 15% DIRT, Seen aktiv, tiefer
- `STEPPE` (g_offset=4): Trockenes Grasland, 20% DIRT, keine Seen, höher gelegen

**Example JSON**:
```json
{
  "featureType": "plains-biome",
  "name": "green-meadows",
  "type": "PLAINS",
  "variation": "MEADOW",
  "groundType": "GRASSY",
  "shape": "CIRCLE",
  "size": "LARGE",
  "parameters": {
    "lakeDepth": "5"
  }
}
```

#### DesertBiome

**Feature Type**: `desert-biome`
**Java Class**: `de.mhus.nimbus.world.generator.composer.biome.DesertBiome`
**Parent**: `Biome`

**Purpose**: Wüsten und trockene Gebiete. Nutzt DesertBuilder für sandiges Terrain mit gelegentlichen Felsformationen.

**Additional Properties**:
```java
private DesertTerrain terrain;    // FLAT, DUNES, ROCKY, BADLANDS
private GroundType groundType;    // Material type (DEFAULT, SANDY, etc.)
```

**DesertTerrain Presets**:
- `FLAT` (g_offset=5): Flache Wüstenebene, 10% Stein
- `DUNES` (g_offset=15): Sanddünen, 30% Stein [Default]
- `ROCKY` (g_offset=18): Felsige Wüste, 50% Stein
- `BADLANDS` (g_offset=20): Erosionslandschaft, 70% Stein

**Example JSON**:
```json
{
  "featureType": "desert-biome",
  "name": "red-badlands",
  "type": "DESERT",
  "terrain": "BADLANDS",
  "groundType": "SANDY",
  "shape": "CIRCLE",
  "size": "LARGE"
}
```

#### SwampBiome

**Feature Type**: `swamp-biome`
**Java Class**: `de.mhus.nimbus.world.generator.composer.biome.SwampBiome`
**Parent**: `Biome`

**Purpose**: Sumpfgebiete mit wassergefüllten Tälern. Nutzt SwampBuilder für niedriges Terrain mit automatischer Wasser-Füllung in abgeschlossenen Tälern.

**Additional Properties**:
```java
private SwampDepth depth;         // SHALLOW, MEDIUM, DEEP, BOG
private GroundType groundType;    // Material type (SWAMPY, etc.)
```

**SwampDepth Presets**:
- `SHALLOW` (swampDepth=2): Flache Pfützen, g_offset=8
- `MEDIUM` (swampDepth=3): Mittlere Pools, g_offset=10 [Default]
- `DEEP` (swampDepth=5): Tiefe Wasserbecken, g_offset=12
- `BOG` (swampDepth=4): Moor, sehr niedrig (g_asl=3), g_offset=6

**Example JSON**:
```json
{
  "featureType": "swamp-biome",
  "name": "deep-marshlands",
  "type": "SWAMP",
  "depth": "DEEP",
  "groundType": "SWAMPY",
  "shape": "CIRCLE",
  "size": "MEDIUM"
}
```

#### MarshBiome

**Feature Type**: `marsh-biome`
**Java Class**: `de.mhus.nimbus.world.generator.composer.biome.MarshBiome`
**Parent**: `Biome`

**Purpose**: Flaches Marschland nahe Meeresspiegel. Nutzt SwampBuilder mit niedrigerer Höhe und offeneren Wasserflächen als Swamp.

**Additional Properties**:
```java
private MarshWaterLevel waterLevel; // TIDAL, COASTAL, INLAND, WETLAND
private GroundType groundType;      // Material type (SWAMPY, etc.)
```

**MarshWaterLevel Presets**:
- `TIDAL` (swampDepth=2): Fast auf Meereshöhe, g_asl=1, g_offset=4
- `COASTAL` (swampDepth=3): Nahe Meeresspiegel, g_asl=2, g_offset=5 [Default]
- `INLAND` (swampDepth=4): Leicht erhöht, g_asl=4, g_offset=7
- `WETLAND` (swampDepth=5): Höher gelegen, g_asl=6, g_offset=9

**Example JSON**:
```json
{
  "featureType": "marsh-biome",
  "name": "tidal-flats",
  "type": "MARSH",
  "waterLevel": "TIDAL",
  "groundType": "SWAMPY",
  "shape": "RECTANGLE",
  "size": "LARGE"
}
```

#### CoastBiome

**Feature Type**: `coast-biome`
**Java Class**: `de.mhus.nimbus.world.generator.composer.biome.CoastBiome`

**Purpose**: Küstenregionen zwischen Land und Ozean.

#### IslandBiome

**Feature Type**: `island-biome`
**Java Class**: `de.mhus.nimbus.world.generator.composer.biome.IslandBiome`

**Purpose**: Inseln im Ozean.

#### OceanBiome

**Feature Type**: `ocean-biome`
**Java Class**: `de.mhus.nimbus.world.generator.composer.biome.OceanBiome`

**Purpose**: Ozean-Gebiete.

#### Composite

**Feature Type**: `composite`
**Java Class**: `de.mhus.nimbus.world.generator.composer.area.Composite`
**Parent**: `Area`

**Purpose**: Container für verschachtelte Features (Biomes, Flows).

**Key Properties**:
```java
private List<Feature> features;   // Nested features
```

**Example JSON**:
```json
{
  "featureType": "composite",
  "name": "shire-region",
  "title": "The Shire",
  "shape": "CIRCLE",
  "size": "MEDIUM",
  "positions": [...],
  "features": [
    { "featureType": "biome", "name": "shire-green", ... },
    { "featureType": "town", "name": "hobbiton", ... }
  ]
}
```

### 4.3 Structure Features

#### Town

**Feature Type**: `town`
**Java Class**: `de.mhus.nimbus.world.generator.composer.town.Town`
**Parent**: `Structure` (extends `Area`)

**Purpose**: Dörfer mit Gebäuden, Straßen und Distrikten.

**Key Properties**:
```java
private String style;                           // "medieval", "modern", "fantasy"
private BiomeType biomeType;                    // Optional: controls builder + terrain parameters (e.g., PLAINS, FOREST)
private List<District> districts;               // District definitions
private List<TownConnectionPoint> externalConnectionPoints; // External roads
private int baseLevel;                          // Terrain base level (default: 95)
private boolean fillEmptySlots;                 // Auto-fill empty slots (default: true)
private double buildingTendency;                // 0.0-1.0, tendency towards buildings vs free places (default: 0.7)
private double fillRate;                        // 0.0-1.0, target occupancy rate (default: 0.75)
private boolean debug;                          // Draw debug markers (default: false)
private Map<String, String> parameters;         // Custom parameters
```

**District Structure**:
```java
public class District {
    private String name;                // District name
    private String title;               // Display name
    private Direction direction;        // Direction from anchor (N, NE, E, SE, S, SW, W, NW)
    private String anchorDistrict;      // Anchor district name (null for origin)
    private TownSize slots;          // HAMLET, SMALL_VILLAGE, VILLAGE, TOWN, LARGE_TOWN
    private List<Place> places;         // Places in this district
}
```

**Place Types**:
- `BuildingPlace`: Buildings (houses, tavern, shop, town_hall, workshop, etc.)
- `FreePlace`: Open spaces (PLAZA, GARDEN, PARK, FIELD)
- `RoadPlace`: Streets (STREET, TRAIL, ALLEY)
- `RiverPlace`: Water features (STREAM, CANAL)
- `WallPlace`: Barriers (WOODEN_FENCE, STONE_WALL, HEDGE)

**Example JSON**:
```json
{
  "featureType": "town",
  "name": "small-town",
  "title": "Small Town",
  "style": "medieval",
  "biomeType": "PLAINS",
  "baseLevel": 95,
  "fillEmptySlots": true,
  "buildingTendency": 0.7,
  "fillRate": 0.75,
  "debug": false,
  "districts": [
    {
      "name": "center",
      "title": "Town Center",
      "slots": "MEDIUM",
      "places": [
        {
          "placeType": "building",
          "name": "town-hall",
          "kind": "town_hall",
          "levelOffset": 2,
          "connectionPoint": false
        },
        {
          "placeType": "free",
          "name": "market-square",
          "kind": "PLAZA",
          "connectionPoint": true
        },
        {
          "placeType": "road",
          "name": "main-street",
          "kind": "STREET",
          "connectionPoint": true
        }
      ]
    },
    {
      "name": "north",
      "title": "Northern Quarter",
      "direction": "N",
      "anchorDistrict": "center",
      "slots": "SMALL",
      "places": [
        {
          "placeType": "building",
          "name": "house-1",
          "kind": "house"
        }
      ]
    }
  ],
  "shape": "CIRCLE",
  "size": "SMALL",
  "positions": [
    {"direction": "S", "anchor": "west-region"}
  ]
}
```

### 4.4 Flow Features

#### River

**Feature Type**: `river`
**Java Class**: `de.mhus.nimbus.world.generator.composer.flow.River`
**Parent**: `Flow`

**Purpose**: Flüsse, die zwischen zwei Punkten fließen.

**Key Properties**:
```java
private FlowType type;                  // RIVER
private String startPointId;            // Start point name
private String endPointId;              // End point name (or merge point)
private List<String> waypointIds;       // Optional waypoints
private FlowWidth width;                // SMALL, MEDIUM, LARGE
private Integer widthBlocks;            // Explicit width (overrides enum)
private LevelMode levelMode;            // FIXED, ADJUST_MEAN, ADJUST_MINIMUM, ADJUST_MAXIMUM
private Integer meanLevelOffset;        // Offset for ADJUST modes
private DeviationTendency tendLeft;     // NONE, SLIGHT, MODERATE, STRONG
private DeviationTendency tendRight;    // NONE, SLIGHT, MODERATE, STRONG
private Integer depth;                  // River depth
private Boolean force;                  // Force through obstacles
private Map<String, String> parameters; // Custom parameters
```

**Example JSON**:
```json
{
  "featureType": "river",
  "name": "anduin-great-river",
  "type": "RIVER",
  "startPointId": "river-source",
  "endPointId": "river-mouth",
  "depth": 4,
  "levelMode": "ADJUST_MEAN",
  "meanLevelOffset": -2,
  "widthBlocks": 10,
  "tendLeft": "SLIGHT",
  "tendRight": "MODERATE",
  "force": false
}
```

#### Road

**Feature Type**: `road`
**Java Class**: `de.mhus.nimbus.world.generator.composer.flow.Road`
**Parent**: `Flow`

**Purpose**: Straßen zwischen zwei Punkten.

**Additional Properties**:
```java
private String roadType;                // "street", "highway", "trail"
```

**Example JSON**:
```json
{
  "featureType": "road",
  "name": "long-road",
  "type": "ROAD",
  "startPointId": "rocky-hills",
  "endPointId": "coastline-west",
  "levelMode": "ADJUST_MEAN",
  "meanLevelOffset": 1,
  "widthBlocks": 1,
  "roadType": "street",
  "tendLeft": "NONE",
  "tendRight": "NONE"
}
```

#### Wall

**Feature Type**: `wall`
**Java Class**: `de.mhus.nimbus.world.generator.composer.flow.Wall`
**Parent**: `Flow`

**Purpose**: Mauern oder Zäune.

**Closed Loop Support**: Walls können geschlossene Loops bilden:
```json
{
  "featureType": "wall",
  "name": "city-wall",
  "startPointId": "gate-south",
  "endPointId": "gate-south",
  "closedLoop": true,
  "shapeHint": "CIRCLE",
  "size": "MEDIUM",
  "widthBlocks": 2
}
```

#### SideWall

**Feature Type**: `sidewall`
**Java Class**: `de.mhus.nimbus.world.generator.composer.flow.SideWall`
**Parent**: `Wall`

**Purpose**: Seitliche Mauern entlang von Flows.

### 4.5 Point Features

#### PositionPoint (Standard Point)

**Feature Type**: `point`
**Java Class**: `de.mhus.nimbus.world.generator.composer.point.PositionPoint`
**Parent**: `Point`

**Purpose**: Markiert spezifische Positionen (Landmarks, Quest Marker, Connection Points).

**Key Properties**:
```java
private String biomeId;                 // Biome this point belongs to
private Direction direction;            // Direction from biome center (N, NE, E, SE, S, SW, W, NW)
private BiomeDistance biomeDistance;    // CENTER, NEAR, NORMAL, FAR, VERY_FAR
private Direction biomeSide;            // Side of biome (overrides direction/distance)
private Double sideOffset;              // Offset along side (0.0-1.0)
private Map<String, String> parameters; // Custom parameters
private boolean precomposed;            // Skip composition (for synthetic points)
```

**Legacy Properties** (deprecated):
```java
@Deprecated
private List<RelativePosition> positions; // Use biomeId + direction instead
@Deprecated
private SnapConfig snap;                  // Use biomeId + direction instead
```

**Example JSON** (new syntax):
```json
{
  "featureType": "point",
  "name": "mountain-peak",
  "title": "Mountain Peak Landmark",
  "biomeId": "central-mountains",
  "direction": "N",
  "biomeDistance": "FAR"
}
```

**Example JSON** (legacy syntax with snap):
```json
{
  "featureType": "point",
  "name": "rocky-hills",
  "title": "Road Start at Rocky Hills",
  "snap": {
    "mode": "INSIDE",
    "target": "central-mountains"
  }
}
```

**Example JSON** (side positioning):
```json
{
  "featureType": "point",
  "name": "coastline-west",
  "title": "Western Coastline Point",
  "biomeId": "west-region",
  "biomeSide": "SW",
  "sideOffset": 0.5
}
```

#### EdgePoint

**Feature Type**: `edge`
**Java Class**: `de.mhus.nimbus.world.generator.composer.point.EdgePoint`
**Parent**: `Point`

**Purpose**: Punkt am Rand eines Biomes.

**Example JSON**:
```json
{
  "featureType": "edge",
  "name": "forest-edge-east",
  "biomeId": "eastern-forest",
  "biomeSide": "E"
}
```

#### OceanEdgePoint

**Feature Type**: `ocean-edge`
**Java Class**: `de.mhus.nimbus.world.generator.composer.point.OceanEdgePoint`
**Parent**: `Point`

**Purpose**: Punkt an der Ozean-Kante (für Flüsse, die ins Meer münden).

**Additional Properties**:
```java
private Direction oceanDirection;       // Direction to ocean
```

**Example JSON**:
```json
{
  "featureType": "ocean-edge",
  "name": "river-mouth",
  "title": "River Mouth at Ocean",
  "snap": {
    "mode": "INSIDE",
    "target": "west-region"
  },
  "oceanDirection": "W"
}
```

#### TownConnectionPoint

**Feature Type**: `town-connection`
**Java Class**: `de.mhus.nimbus.world.generator.composer.town.TownConnectionPoint`
**Parent**: `Point`

**Purpose**: Externe Connection Points für Towns (automatisch generiert).

**Special Behavior**: Diese Points werden automatisch vom System generiert und sollten nicht manuell definiert werden.

#### VillagePoint

**Feature Type**: `village-point`
**Java Class**: `de.mhus.nimbus.world.generator.composer.point.VillagePoint`
**Parent**: `Point`

**Purpose**: Platziert ein kleines Dorf an einem bestimmten Punkt mit konfigurierbarem Stil und Größe.

**Key Properties**:
```java
private String biomeId;                 // Biome, in dem das Dorf platziert wird
private BiomeDistance biomeDistance;    // Abstand vom Biome-Zentrum
private Direction direction;            // Richtung vom Biome-Zentrum
private TownSize villageSize;           // HAMLET, SMALL_VILLAGE, VILLAGE, TOWN, LARGE_TOWN
private String villageStyle;            // Baustil (z.B. "medieval", "fantasy")
private Integer baseLevel;              // Terrain-Basishöhe (default: 95)
```

**Example JSON**:
```json
{
  "featureType": "village-point",
  "name": "small-hamlet",
  "title": "Small Hamlet in the Valley",
  "biomeId": "green-hills",
  "direction": "N",
  "biomeDistance": "NORMAL",
  "villageSize": "HAMLET",
  "villageStyle": "medieval",
  "baseLevel": 95
}
```

#### MountainPoint

**Feature Type**: `mountain-point`
**Java Class**: `de.mhus.nimbus.world.generator.composer.point.MountainPoint`
**Parent**: `Point`

**Purpose**: Erstellt einen einzelnen Berggipfel mit radialem Höhenverlauf an einem bestimmten Punkt.

**Key Properties**:
```java
private String biomeId;                 // Biome, in dem der Berg platziert wird
private BiomeDistance biomeDistance;    // Abstand vom Biome-Zentrum
private Direction direction;            // Richtung vom Biome-Zentrum
private Integer radius;                 // Radius des Berges in Blöcken (default: 150)
private Integer peakHeight;             // Höhe der Bergspitze über baseHeight (default: 100)
private Integer baseHeight;             // Basishöhe des Berges (default: 64)
private Long seed;                      // Seed für Zufallsgenerierung
private String material;                // Material-ID (z.B. "stone", "granite")
private Double roughness;               // Rauheit des Geländes 0.0-1.0 (default: 0.5)
private Boolean crater;                 // Krater am Gipfel (default: false)
```

**Example JSON**:
```json
{
  "featureType": "mountain-point",
  "name": "lonely-mountain",
  "title": "The Lonely Mountain",
  "biomeId": "central-plains",
  "direction": "E",
  "biomeDistance": "FAR",
  "radius": 200,
  "peakHeight": 120,
  "baseHeight": 70,
  "material": "stone",
  "roughness": 0.7,
  "crater": false
}
```

#### SpikesPoint

**Feature Type**: `spikes-point`
**Java Class**: `de.mhus.nimbus.world.generator.composer.point.SpikesPoint`
**Parent**: `Point`

**Purpose**: Erstellt ein Feld von spitzen Formationen (Kristalle, Eiszapfen, etc.) mit konfigurierbarer Dichte.

**Key Properties**:
```java
private String biomeId;                 // Biome, in dem die Spikes platziert werden
private BiomeDistance biomeDistance;    // Abstand vom Biome-Zentrum
private Direction direction;            // Richtung vom Biome-Zentrum
private Density density;                // LOW, MEDIUM, HIGH - Minimaler Abstand zwischen Spikes
private Amount amount;                  // FEW, NORMAL, MANY - Anzahl der Spikes
private Integer minHeight;              // Minimale Spike-Höhe (default: 10)
private Integer maxHeight;              // Maximale Spike-Höhe (default: 50)
private Integer minWidth;               // Minimale Spike-Breite (default: 1)
private Integer maxWidth;               // Maximale Spike-Breite (default: 3)
private Integer distributionRadius;     // Verteilungsradius in Blöcken (default: 100)
private String material;                // Material-ID (z.B. "ice", "crystal")
private Double taperFactor;             // Verjüngungsfaktor 0.0-1.0 (default: 0.5)
```

**Density Enum**:
- `LOW`: minDistance = 20 Blöcke
- `MEDIUM`: minDistance = 12 Blöcke [Default]
- `HIGH`: minDistance = 8 Blöcke

**Amount Enum**:
- `FEW`: spikesCount = distributionRadius / 25
- `NORMAL`: spikesCount = distributionRadius / 15 [Default]
- `MANY`: spikesCount = distributionRadius / 8

**Example JSON**:
```json
{
  "featureType": "spikes-point",
  "name": "crystal-field",
  "title": "Crystal Spike Field",
  "biomeId": "frozen-wastes",
  "direction": "NW",
  "biomeDistance": "CENTER",
  "density": "MEDIUM",
  "amount": "NORMAL",
  "minHeight": 15,
  "maxHeight": 40,
  "minWidth": 1,
  "maxWidth": 3,
  "distributionRadius": 80,
  "material": "ice",
  "taperFactor": 0.7
}
```

#### MountainFacePoint

**Feature Type**: `mountain-face-point`
**Java Class**: `de.mhus.nimbus.world.generator.composer.point.MountainFacePoint`
**Parent**: `Point`

**Purpose**: Erstellt eine Klippenwand mit verzweigten Graten (Spider Pattern) an einem bestimmten Punkt.

**Key Properties**:
```java
private String biomeId;                 // Biome, in dem die Klippenwand platziert wird
private BiomeDistance biomeDistance;    // Abstand vom Biome-Zentrum
private Direction direction;            // Richtung vom Biome-Zentrum
private Dimension dimension;            // SMALL, MEDIUM, LARGE - Größe der Klippenwand
private Integer baseHeight;             // Basishöhe der Klippenwand (default: 64)
private Integer faceHeight;             // Höhe der Klippenwand (default: 40)
private Integer recursionDepth;         // Rekursionstiefe für Verzweigungen (default: 2)
private String material;                // Material-ID (z.B. "stone", "granite")
private Integer branches;               // Anzahl der Hauptäste (auto-calculated from dimension)
private Integer branchLength;           // Länge der Hauptäste (auto-calculated from dimension)
private Integer subBranches;            // Anzahl der Unteräste (auto-calculated from dimension)
```

**Dimension Enum**:
- `SMALL`: branches=3-4, branchLength=30-40, subBranches=2-3
- `MEDIUM`: branches=5-6, branchLength=50-70, subBranches=3-4 [Default]
- `LARGE`: branches=7-9, branchLength=80-120, subBranches=4-5

**Example JSON**:
```json
{
  "featureType": "mountain-face-point",
  "name": "cliff-wall",
  "title": "Great Cliff Face",
  "biomeId": "highlands",
  "direction": "S",
  "biomeDistance": "NORMAL",
  "dimension": "LARGE",
  "baseHeight": 70,
  "faceHeight": 60,
  "recursionDepth": 3,
  "material": "stone"
}
```

#### LakesPoint

**Feature Type**: `lakes-point`
**Java Class**: `de.mhus.nimbus.world.generator.composer.point.LakesPoint`
**Parent**: `Point`

**Purpose**: Erstellt ein Seen-System mit einem Hauptsee und mehreren kleineren Seen an einem bestimmten Punkt.

**Key Properties**:
```java
private String biomeId;                 // Biome, in dem die Seen platziert werden
private BiomeDistance biomeDistance;    // Abstand vom Biome-Zentrum
private Direction direction;            // Richtung vom Biome-Zentrum
private Integer mainLakeRadius;         // Radius des Hauptsees in Blöcken (default: 35)
private Integer mainLakeDepth;          // Tiefe des Hauptsees in Blöcken (default: 25)
private Integer smallLakes;             // Anzahl kleiner Seen (default: 6)
private Integer smallLakeMinRadius;     // Min. Radius kleiner Seen (default: 8)
private Integer smallLakeMaxRadius;     // Max. Radius kleiner Seen (default: 15)
private Integer scatterDistance;        // Verteilungsabstand vom Hauptsee (default: 50)
```

**Behavior**:
- Findet den niedrigsten Punkt im Gebiet als Wasser-Oberfläche
- Prüft, ob über Meeresspiegel (überspringt Seen unterhalb)
- Erstellt Vertiefungen vom Wasser-Niveau nach unten
- Platziert Wasser-Extra-Blocks auf Oberflächen-Niveau

**Example JSON**:
```json
{
  "featureType": "lakes-point",
  "name": "twin-lakes",
  "title": "Twin Lakes",
  "biomeId": "mountain-valley",
  "direction": "CENTER",
  "biomeDistance": "NEAR",
  "mainLakeRadius": 45,
  "mainLakeDepth": 30,
  "smallLakes": 8,
  "smallLakeMinRadius": 10,
  "smallLakeMaxRadius": 20,
  "scatterDistance": 60
}
```

## 5. Common Concepts

### 5.1 AreaShape

Defines the shape of area features (biomes, towns, composites).

**Values**:
- `CIRCLE`: Circular area expanding from center
- `LINE`: Linear area along a direction
- `RECTANGLE`: Rectangular area

**Usage**:
```json
{
  "shape": "CIRCLE",
  "size": "LARGE"
}
```

### 5.2 AreaSize

Defines size categories for areas.

**Values**:

| Size | From (hexes) | To (hexes) |
|------|--------------|------------|
| `SMALL` | 1 | 3 |
| `MEDIUM` | 3 | 7 |
| `LARGE` | 7 | 15 |
| `WIDE` | 15 | 30 |

**Override with explicit values**:
```json
{
  "size": "MEDIUM",
  "sizeFrom": 5,
  "sizeTo": 10
}
```

### 5.3 RelativePosition

Defines positioning of features relative to anchors.

**Structure**:
```java
public class RelativePosition {
    private Direction direction;        // N, NE, E, SE, S, SW, W, NW
    private DistanceRange distance;     // DIRECT_BEHIND, NEAR, NORMAL, FAR
    private String anchor;              // Name of anchor feature (or "origin")
    private int priority;               // Priority (higher = more important, default: 5)
    private Integer distanceFrom;       // Explicit distance min
    private Integer distanceTo;         // Explicit distance max
}
```

**Example**:
```json
{
  "positions": [
    {
      "direction": "E",
      "distanceFrom": 3,
      "distanceTo": 3,
      "anchor": "west-region",
      "priority": 10
    }
  ]
}
```

### 5.4 Direction Enum

**Values**: `N`, `NE`, `E`, `SE`, `S`, `SW`, `W`, `NW`

**Hex Grid Mapping** (pointy-top hexes):
- `NE`: 0° (top-right side)
- `E`: 60° (right side)
- `SE`: 120° (bottom-right side)
- `SW`: 180° (bottom-left side)
- `W`: 240° (left side)
- `NW`: 300° (top-left side)
- `N`: 330° (top spike, rounds to NW/NE)
- `S`: 150° (bottom spike, rounds to SE/SW)

### 5.5 FlowWidth

Defines width categories for flows (rivers, roads).

**Values**:

| Width | From (blocks) | To (blocks) |
|-------|---------------|-------------|
| `SMALL` | 2 | 4 |
| `MEDIUM` | 4 | 6 |
| `LARGE` | 6 | 10 |

**Override with explicit value**:
```json
{
  "width": "MEDIUM",
  "widthBlocks": 5
}
```

### 5.6 LevelMode

Determines how flow height is calculated.

**Values**:
- `FIXED`: Use fixed `level` parameter (default if not specified)
- `ADJUST_MEAN`: Adapt to terrain with half offset: `meanHeight + offset/2`
- `ADJUST_MINIMUM`: Adapt to terrain without offset: `meanHeight`
- `ADJUST_MAXIMUM`: Adapt to terrain with full offset: `meanHeight + offset`

Where `meanHeight = landLevel + landOffset/2`

**Example**:
```json
{
  "levelMode": "ADJUST_MEAN",
  "meanLevelOffset": -2
}
```

### 5.7 DeviationTendency

Controls how flows deviate from straight lines (creates curves).

**Values**:
- `NONE`: No deviation (straight line)
- `SLIGHT`: Subtle curves (probability: 0.2)
- `MODERATE`: Natural curves (probability: 0.4)
- `STRONG`: Pronounced curves (probability: 0.6)

**Usage**:
```json
{
  "tendLeft": "SLIGHT",
  "tendRight": "MODERATE"
}
```

### 5.8 GroundType (Material Presets)

**Purpose**: `GroundType` definiert Material-Presets für verschiedene Bodentypen. Jedes Preset legt fest, welche Materialien für unterschiedliche Höhenbereiche verwendet werden.

**Enum Values**:

| GroundType | Description | Surface Materials |
|------------|-------------|-------------------|
| `DEFAULT` | Standard materials | SAND, GRASS, DIRT, STONE, SNOW |
| `SNOWY` | Snow-covered terrain | All elevations covered in SNOW |
| `SANDY` | Desert-like terrain | DESERT_SAND everywhere, STONE at peaks |
| `GRASSY` | Lush grasslands | GRASS at all elevations |
| `STONY` | Rocky terrain | STONE everywhere |
| `SWAMPY` | Muddy wetlands | SWAMP material (muddy/wet) |
| `VOLCANIC` | Dark volcanic rock | BEDROCK and STONE |
| `ICY` | Frozen landscape | ICE material with SNOW peaks |

**Material Zones** (from low to high elevation):
1. **sandMaterial**: At/below ocean level
2. **grassMaterial**: Low elevations above ocean
3. **dirtMaterial**: Medium elevations
4. **stoneMaterial**: High elevations
5. **snowMaterial**: Peaks/highest points

**Usage in Biome**:
```json
{
  "featureType": "mountain-biome",
  "name": "snowy-peaks",
  "height": "HIGH_PEAKS",
  "groundType": "SNOWY"
}
```

**Usage in Builder Parameters**:
```json
{
  "parameters": {
    "groundType": "VOLCANIC",
    "g_asl": "100"
  }
}
```

**Override Individual Materials**:
You can still override individual materials even with a groundType:
```json
{
  "parameters": {
    "groundType": "SANDY",
    "stoneMaterial": "BEDROCK"
  }
}
```

**Available in Builders**: MountainBuilder, ForestBuilder, DesertBuilder, PlainsBuilder, SwampBuilder

### 5.9 Parameters Map

Custom key-value parameters für Terrain Generation.

**Common Parameters**:
- `g_builder`: Builder type ("plains", "forest", "mountains", "desert", "swamp")
- `g_asl`: Above sea level (base height)
- `g_offset`: Height variation
- `g_roughness`: Terrain roughness (0.0-1.0)
- `g_frequency`: Noise frequency (0.0-1.0)
- `groundType`: Material preset (DEFAULT, SNOWY, SANDY, GRASSY, STONY, SWAMPY, VOLCANIC, ICY)

**Material Parameters** (overrides groundType):
- `sandMaterial`: Material for ocean level (SAND, DESERT_SAND, etc.)
- `grassMaterial`: Material for low elevation (GRASS, DIRT, SWAMP, ICE, etc.)
- `dirtMaterial`: Material for medium elevation
- `stoneMaterial`: Material for high elevation (STONE, BEDROCK, etc.)
- `snowMaterial`: Material for peaks (SNOW, STONE, etc.)

**Biome-Specific Parameters**:
- **Forest**: `dirtRatio` (0.0-1.0, default: 0.3)
- **Desert**: `dirtRatio` (0.0-1.0, default: 0.05), `stoneRatio` (0.0-1.0, default: 0.3)
- **Plains**: `dirtRatio` (0.0-1.0, default: 0.1), `enableLakes` (true/false), `lakeDepth` (blocks)
- **Swamp**: `swampDepth` (blocks, default: 3)
- **Mountain**: `stoneOffset` (blocks, default: 20), `snowOffset` (blocks, default: 50)

**Example**:
```json
{
  "parameters": {
    "g_asl": "10",
    "g_offset": "15",
    "g_frequency": "0.8",
    "groundType": "VOLCANIC",
    "dirtRatio": "0.2"
  }
}
```

## 6. Town System Deep Dive

### 6.1 Town Structure

Hierarchie: **Town → Districts → Places**

```
Town "small-town"
├── District "center" (MEDIUM slots)
│   ├── Place "town-hall" (BuildingPlace)
│   ├── Place "market-square" (FreePlace)
│   └── Place "main-street" (RoadPlace)
├── District "north" (SMALL slots)
│   ├── Place "house-1" (BuildingPlace)
│   └── Place "north-road" (RoadPlace)
└── District "south" (SMALL slots)
    ├── Place "tavern" (BuildingPlace)
    └── Place "stream" (RiverPlace)
```

### 6.2 District Configuration

**Properties**:
```java
public class District {
    private String name;                // Unique district name
    private String title;               // Display name
    private Direction direction;        // Direction from anchor (N, NE, E, SE, S, SW, W, NW)
    private String anchorDistrict;      // Anchor district name (null for origin/center)
    private DistrictSlotSize slots;     // Slot configuration (TINY, SMALL, MEDIUM, LARGE)
    private List<Place> places;         // Places in this district
}
```

**DistrictSlotSize (Slot Configuration)**:

| Size | Slot Grid |
|------|-----------|
| `TINY` | 3x3 slots |
| `SMALL` | 5x5 slots |
| `MEDIUM` | 7x7 slots |
| `LARGE` | 9x9 slots |

**District Positioning**:
- First district (origin): No `direction` or `anchorDistrict` needed
- Subsequent districts: Use `direction` and `anchorDistrict` to position relative to other districts

**Example**:
```json
{
  "name": "north",
  "title": "Northern Quarter",
  "direction": "N",
  "anchorDistrict": "center",
  "slots": "SMALL",
  "places": [...]
}
```

### 6.3 Place Types

**Common Place Fields** (inherited by all place types):
```java
private String name;                // Technical unique name
private boolean connectionPoint;    // Is this a connection point? (default: false)
private int levelOffset;            // Level offset relative to baseLevel (default: 0)
```

`levelOffset` allows individual places to be raised or lowered relative to the town's `baseLevel`.
The actual place level is computed as: `baseLevel + levelOffset`.
- Positive values raise the place (e.g., castle on a hill: `levelOffset: 3`)
- Negative values lower it (e.g., canal: `levelOffset: -2`)
- Default: 0 (same level as baseLevel)

#### BuildingPlace

**Type**: `building`
**Purpose**: Gebäude (houses, taverns, shops, workshops)

**Properties**:
```java
private String name;                // Place name
private String kind;                // Building kind ("house", "tavern", "shop", "town_hall", "workshop", etc.)
private String style;               // Optional style override
private boolean connectionPoint;    // Is this a connection point for external roads?
```

**Example**:
```json
{
  "placeType": "building",
  "name": "town-hall",
  "kind": "town_hall",
  "style": "medieval",
  "levelOffset": 2,
  "connectionPoint": false
}
```

#### FreePlace

**Type**: `free`
**Purpose**: Offene Flächen (plazas, gardens, parks, fields)

**Kind Values**:
- `PLAZA`: Market square, town square
- `GARDEN`: Small garden
- `PARK`: Larger park
- `FIELD`: Agricultural field

**Example**:
```json
{
  "placeType": "free",
  "name": "market-square",
  "kind": "PLAZA",
  "connectionPoint": true
}
```

#### RoadPlace

**Type**: `road`
**Purpose**: Straßen innerhalb des Dorfes

**Kind Values**:
- `STREET`: Main street
- `TRAIL`: Small path
- `ALLEY`: Narrow alley

**Example**:
```json
{
  "placeType": "road",
  "name": "main-street",
  "kind": "STREET",
  "connectionPoint": true
}
```

#### RiverPlace

**Type**: `river`
**Purpose**: Gewässer durch das Dorf

**Kind Values**:
- `STREAM`: Small stream
- `CANAL`: Canal

**Example**:
```json
{
  "placeType": "river",
  "name": "stream",
  "kind": "STREAM",
  "connectionPoint": true
}
```

#### WallPlace

**Type**: `wall`
**Purpose**: Mauern oder Zäune

**Kind Values**:
- `WOODEN_FENCE`: Wooden fence
- `STONE_WALL`: Stone wall
- `HEDGE`: Living hedge

**Example**:
```json
{
  "placeType": "wall",
  "name": "fence",
  "kind": "WOODEN_FENCE",
  "connectionPoint": false
}
```

### 6.4 Connection Points

**Purpose**: Connection Points markieren Orte, an denen externe Straßen das Dorf betreten/verlassen können.

**Automatic External Connection Generation**:
- System generiert automatisch `TownConnectionPoint` Features in Nachbar-Grids
- Diese externen Points können als `endPointId` für Roads verwendet werden
- Naming convention: `{town-name}-{direction}` (z.B. `small-town-e` für östlichen Connection Point)

**Example**:
```json
{
  "featureType": "road",
  "name": "approach-road",
  "startPointId": "rocky-hills",
  "endPointId": "small-town-e",
  "roadType": "street"
}
```

### 6.5 Town Configuration Options

**biomeType**: Controls the terrain builder and default parameters for the town (optional)
- When set, overrides the builder from StructureType and applies biome-specific terrain parameters
- When null, falls back to StructureType defaults (mountain builder)
- Valid values: Any `BiomeType` enum value (e.g., `PLAINS`, `FOREST`, `MOUNTAINS`)
```json
{ "biomeType": "PLAINS" }
```

**baseLevel**: Terrain base level (default: 95)
```json
{ "baseLevel": 95 }
```

**fillEmptySlots**: Auto-fill empty slots with buildings/free places (default: true)
```json
{ "fillEmptySlots": true }
```

**buildingTendency**: Tendency towards buildings vs free places when filling (0.0-1.0, default: 0.7)
- 0.0 = all free places (parks, gardens)
- 0.5 = 50% buildings, 50% free places
- 1.0 = all buildings
```json
{ "buildingTendency": 0.7 }
```

**fillRate**: Target occupancy rate (0.0-1.0, default: 0.75)
- 0.0 = no auto-filling (only explicit places)
- 0.75 = district should be 75% occupied
- 1.0 = district should be 100% filled
```json
{ "fillRate": 0.75 }
```

**debug**: Draw debug markers at place centers (default: false)
```json
{ "debug": true }
```

## 7. Best Practices

### 7.1 Naming Conventions

**name vs title vs id**:
- `name`: Technical name, unique within scope, used for references
- `title`: Display name, nicht eindeutig, für UI
- `*Id`: Suffix für IDs, die auf andere Features verweisen (e.g., `biomeId`, `startPointId`)

**Examples**:
```json
{
  "name": "west-region",          // Technical name
  "title": "Western Plains",      // Display name
  "biomeId": "west-region"        // Reference to biome
}
```

### 7.2 Positioning Strategies

**Anchoring Features**:
1. Start with origin-anchored features
2. Build outward using previously placed features as anchors
3. Use priorities to control placement order

**Example**:
```json
{
  "features": [
    {
      "name": "center-biome",
      "positions": [{"anchor": "origin", "priority": 10}]
    },
    {
      "name": "north-biome",
      "positions": [{"anchor": "center-biome", "direction": "N", "priority": 9}]
    }
  ]
}
```

### 7.3 Continent Usage

**Purpose**: Continents füllen Lücken zwischen Biomes mit einem spezifischen Terrain-Typ statt Ozean.

**Configuration**:
```json
{
  "continents": [
    {
      "continentId": "main-continent",
      "name": "Main Continent",
      "biomeType": "MOUNTAINS",
      "minNeighbors": 2,
      "parameters": {
        "g_offset": "10",
        "g_roughness": "0.8"
      }
    }
  ]
}
```

**Assign Biomes to Continents**:
```json
{
  "featureType": "biome",
  "name": "west-region",
  "continentId": "main-continent"
}
```

### 7.4 Flow Routing

**Point-to-Point Flows**:
1. Define start and end Points first
2. Reference Points by name in Flow definition
3. Use waypoints for complex routes

**Example**:
```json
{
  "features": [
    {"featureType": "point", "name": "source", ...},
    {"featureType": "point", "name": "dest", ...},
    {
      "featureType": "river",
      "startPointId": "source",
      "endPointId": "dest"
    }
  ]
}
```

### 7.5 Town Integration

**Connecting Towns to Roads**:
1. Define Town with connection points in Districts
2. System auto-generates external connection points (e.g., `town-e`, `town-w`)
3. Reference external connection points in Road definitions

**Example**:
```json
{
  "features": [
    {
      "featureType": "town",
      "name": "small-town",
      "districts": [
        {
          "places": [
            {"placeType": "road", "name": "main-road", "connectionPoint": true}
          ]
        }
      ]
    },
    {
      "featureType": "road",
      "startPointId": "mountain-pass",
      "endPointId": "small-town-e"
    }
  ]
}
```

## 8. Complete Example

```json
{
  "name": "middle-earth-shire",
  "worldId": "world-01",
  "title": "The Shire Region",
  "description": "Peaceful countryside with rolling hills and towns",
  "version": "1.0.0",

  "continents": [
    {
      "continentId": "shire-continent",
      "name": "Shire Landmass",
      "biomeType": "PLAINS",
      "minNeighbors": 2,
      "parameters": {
        "g_asl": "5",
        "g_offset": "8",
        "g_roughness": "0.3"
      }
    }
  ],

  "features": [
    {
      "featureType": "plains-biome",
      "name": "green-hills",
      "title": "Green Hill Country",
      "type": "PLAINS",
      "shape": "CIRCLE",
      "size": "LARGE",
      "continentId": "shire-continent",
      "positions": [
        {"direction": "W", "anchor": "origin", "priority": 10}
      ],
      "parameters": {
        "g_asl": "10",
        "g_roughness": "0.4"
      }
    },

    {
      "featureType": "forest-biome",
      "name": "woody-end",
      "title": "Woody End Forest",
      "type": "FOREST",
      "density": "DENSE",
      "shape": "CIRCLE",
      "size": "MEDIUM",
      "continentId": "shire-continent",
      "positions": [
        {"direction": "E", "distanceFrom": 4, "anchor": "green-hills", "priority": 9}
      ],
      "parameters": {
        "g_treeType": "oak",
        "g_treeDensity": "0.8"
      }
    },

    {
      "featureType": "point",
      "name": "brandywine-source",
      "title": "Brandywine River Source",
      "biomeId": "woody-end",
      "direction": "N",
      "biomeDistance": "FAR"
    },

    {
      "featureType": "ocean-edge",
      "name": "brandywine-mouth",
      "title": "Brandywine River Mouth",
      "biomeId": "green-hills",
      "oceanDirection": "W"
    },

    {
      "featureType": "river",
      "name": "brandywine-river",
      "title": "Brandywine River",
      "type": "RIVER",
      "startPointId": "brandywine-source",
      "endPointId": "brandywine-mouth",
      "depth": 3,
      "levelMode": "ADJUST_MEAN",
      "meanLevelOffset": -1,
      "widthBlocks": 6,
      "tendLeft": "MODERATE",
      "tendRight": "MODERATE",
      "parameters": {
        "waterType": "clear"
      }
    },

    {
      "featureType": "town",
      "name": "hobbiton",
      "title": "Hobbiton",
      "style": "medieval",
      "baseLevel": 95,
      "fillEmptySlots": true,
      "buildingTendency": 0.8,
      "fillRate": 0.9,
      "debug": false,
      "districts": [
        {
          "name": "center",
          "title": "Town Green",
          "slots": "MEDIUM",
          "places": [
            {
              "placeType": "free",
              "name": "town-green",
              "kind": "PLAZA",
              "connectionPoint": true
            },
            {
              "placeType": "building",
              "name": "green-dragon",
              "kind": "tavern",
              "connectionPoint": false
            },
            {
              "placeType": "road",
              "name": "hill-road",
              "kind": "STREET",
              "connectionPoint": true
            }
          ]
        },
        {
          "name": "west-side",
          "title": "Western Hobbit Holes",
          "direction": "W",
          "anchorDistrict": "center",
          "slots": "SMALL",
          "places": [
            {
              "placeType": "building",
              "name": "bagend",
              "kind": "house",
              "connectionPoint": false
            },
            {
              "placeType": "building",
              "name": "hobbit-hole-1",
              "kind": "house"
            },
            {
              "placeType": "free",
              "name": "garden-1",
              "kind": "GARDEN"
            }
          ]
        },
        {
          "name": "east-side",
          "title": "Eastern Fields",
          "direction": "E",
          "anchorDistrict": "center",
          "slots": "SMALL",
          "places": [
            {
              "placeType": "free",
              "name": "vegetable-field",
              "kind": "FIELD"
            },
            {
              "placeType": "building",
              "name": "mill",
              "kind": "workshop"
            }
          ]
        }
      ],
      "shape": "CIRCLE",
      "size": "SMALL",
      "continentId": "shire-continent",
      "positions": [
        {"direction": "SW", "distanceFrom": 2, "anchor": "green-hills", "priority": 8}
      ]
    },

    {
      "featureType": "point",
      "name": "bywater-crossroads",
      "title": "Bywater Crossroads",
      "biomeId": "green-hills",
      "direction": "S",
      "biomeDistance": "NORMAL"
    },

    {
      "featureType": "road",
      "name": "east-road",
      "title": "The East Road",
      "type": "ROAD",
      "startPointId": "bywater-crossroads",
      "endPointId": "hobbiton-s",
      "levelMode": "ADJUST_MEAN",
      "meanLevelOffset": 1,
      "widthBlocks": 2,
      "roadType": "street",
      "tendLeft": "SLIGHT",
      "tendRight": "SLIGHT",
      "parameters": {
        "roadMaterial": "dirt"
      }
    }
  ]
}
```

## 9. Appendix: Enum Reference

### BiomeType
`PLAINS`, `FOREST`, `MOUNTAINS`, `DESERT`, `SWAMP`, `MARSH`, `COAST`, `OCEAN`, `ISLAND`, `TOWN`

### MountainHeight
- `HIGH_PEAKS`: landLevel=120, landOffset=40, ridgeOffset=20, frequency=0.8
- `MEDIUM_PEAKS`: landLevel=100, landOffset=30, ridgeOffset=15, frequency=0.8
- `LOW_PEAKS`: landLevel=80, landOffset=20, ridgeOffset=10, frequency=0.7
- `MEADOW`: landLevel=60, landOffset=10, ridgeOffset=5, frequency=0.6

### ForestDensity
- `SPARSE`: floraDensity=0.4, landOffset=3, dirtRatio=0.1
- `LIGHT`: floraDensity=0.6, landOffset=5, dirtRatio=0.2
- `DENSE`: floraDensity=0.8, landOffset=5, dirtRatio=0.3
- `OLD_GROWTH`: floraDensity=0.9, landOffset=7, dirtRatio=0.4

### PlainsVariation
- `FLAT`: landOffset=2, enableLakes=false, dirtRatio=0.05
- `ROLLING`: landOffset=5, enableLakes=true, dirtRatio=0.1
- `MEADOW`: landOffset=7, enableLakes=true, lakeDepth=5, dirtRatio=0.15
- `STEPPE`: landOffset=4, enableLakes=false, dirtRatio=0.2, asl=20

### DesertTerrain
- `FLAT`: landOffset=5, stoneRatio=0.1, dirtRatio=0.05
- `DUNES`: landOffset=15, stoneRatio=0.3, dirtRatio=0.05
- `ROCKY`: landOffset=18, stoneRatio=0.5, dirtRatio=0.1
- `BADLANDS`: landOffset=20, stoneRatio=0.7, dirtRatio=0.15

### SwampDepth
- `SHALLOW`: swampDepth=2, asl=5, landOffset=8
- `MEDIUM`: swampDepth=3, asl=5, landOffset=10
- `DEEP`: swampDepth=5, asl=5, landOffset=12
- `BOG`: swampDepth=4, asl=3, landOffset=6

### MarshWaterLevel
- `TIDAL`: swampDepth=2, asl=1, landOffset=4
- `COASTAL`: swampDepth=3, asl=2, landOffset=5
- `INLAND`: swampDepth=4, asl=4, landOffset=7
- `WETLAND`: swampDepth=5, asl=6, landOffset=9

### GroundType (Material Presets)
- `DEFAULT`: Standard materials (SAND, GRASS, DIRT, STONE, SNOW)
- `SNOWY`: All snow-covered (SNOW everywhere)
- `SANDY`: Desert terrain (DESERT_SAND, STONE)
- `GRASSY`: Lush grassland (GRASS everywhere)
- `STONY`: Rocky terrain (STONE everywhere)
- `SWAMPY`: Muddy wetland (SWAMP material)
- `VOLCANIC`: Volcanic rock (BEDROCK, STONE)
- `ICY`: Frozen landscape (ICE, SNOW)

### AreaShape
`CIRCLE`, `LINE`, `RECTANGLE`

### AreaSize
`SMALL` (1-3), `MEDIUM` (3-7), `LARGE` (7-15), `WIDE` (15-30)

### Direction
`N`, `NE`, `E`, `SE`, `S`, `SW`, `W`, `NW`

### DistanceRange
DIRECT_BEHIND (1, 1), NEAR (1, 5), NORMAL(5, 10), FAR (10, 20);

### BiomeDistance
`CENTER` (0), `NEAR` (1), `NORMAL` (2), `FAR` (3), `VERY_FAR` (4)

### FlowType
`RIVER`, `ROAD`, `WALL`

### FlowWidth
`SMALL` (2-4), `MEDIUM` (4-6), `LARGE` (6-10)

### LevelMode
`FIXED`, `ADJUST_MEAN`, `ADJUST_MINIMUM`, `ADJUST_MAXIMUM`

### DeviationTendency
`NONE` (0.0), `SLIGHT` (0.2), `MODERATE` (0.4), `STRONG` (0.6)

### DistrictSlotSize (Slots)
`TINY` (3x3), `SMALL` (5x5), `MEDIUM` (7x7), `LARGE` (9x9)

### FeatureStatus
`NEW`, `COMPOSED`, `CREATED`

### TownSize (für Village/Town Größen)
- `HAMLET`: 1x1 slots (sehr klein)
- `SMALL_VILLAGE`: 3x3 slots
- `VILLAGE`: 5x5 slots
- `TOWN`: 7x7 slots
- `LARGE_TOWN`: 9x9 slots

### Density (SpikesPoint)
- `LOW`: minDistance = 20 Blöcke
- `MEDIUM`: minDistance = 12 Blöcke
- `HIGH`: minDistance = 8 Blöcke

### Amount (SpikesPoint)
- `FEW`: spikesCount = distributionRadius / 25
- `NORMAL`: spikesCount = distributionRadius / 15
- `MANY`: spikesCount = distributionRadius / 8

### Dimension (MountainFacePoint)
- `SMALL`: branches=3-4, branchLength=30-40, subBranches=2-3
- `MEDIUM`: branches=5-6, branchLength=50-70, subBranches=3-4
- `LARGE`: branches=7-9, branchLength=80-120, subBranches=4-5

## 10. Migration Notes for AI Translators

### Key Considerations

1. **featureType is mandatory**: Every feature MUST have a `featureType` field for polymorphic deserialization
2. **name is unique**: Feature names must be unique within a composition for referencing
3. **Positioning flexibility**: Use either enum values (`size: "LARGE"`) or explicit values (`sizeFrom: 10, sizeTo: 15`)
4. **Points before Flows**: Always define Points before Flows that reference them
5. **Towns auto-generate connection points**: Reference them as `{town-name}-{direction}`
6. **Deprecated fields**: Prefer new syntax over deprecated fields (e.g., `biomeId` + `direction` over `snap`)

### Common Pitfalls

❌ **Wrong**: Missing featureType
```json
{
  "name": "my-biome",
  "type": "PLAINS"
}
```

✅ **Correct**: Always include featureType
```json
{
  "featureType": "biome",
  "name": "my-biome",
  "type": "PLAINS"
}
```

❌ **Wrong**: Flow references non-existent point
```json
{
  "featureType": "river",
  "startPointId": "undefined-point"
}
```

✅ **Correct**: Define point first, then reference it
```json
{
  "features": [
    {"featureType": "point", "name": "river-source", ...},
    {"featureType": "river", "startPointId": "river-source", ...}
  ]
}
```

### Validation Checklist

- [ ] All features have `featureType`
- [ ] All features have unique `name`
- [ ] All referenced IDs exist (`startPointId`, `endPointId`, `biomeId`, `anchorDistrict`)
- [ ] Positioning is defined (`positions`, `biomeId`, or `snap`)
- [ ] Flow points are defined before flows
- [ ] Town districts have valid `slots` enum
- [ ] Continent IDs match between continents and biomes
- [ ] Direction values are valid enum values

---

**Version**: 1.1.0
**Last Updated**: 2026-02-13
**Author**: Generated from source code analysis
**Changelog**:
- Added 5 new Point types: VillagePoint, MountainPoint, SpikesPoint, MountainFacePoint, LakesPoint
- Added Density, Amount, and Dimension enums
- Added TownSize enum reference
