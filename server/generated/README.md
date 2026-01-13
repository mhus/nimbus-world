# Generated Module

This module contains Java classes that are **generated** from TypeScript interface, enum, and constant definitions located in:
- `client/packages/shared/src/types` → generates `de.mhus.nimbus.generated.types` package
- `client/packages/shared/src/network/messages` → generates `de.mhus.nimbus.generated.network` package
- `client/packages/shared/src/constants` → generates `de.mhus.nimbus.generated.constants` package
- `client/packages/shared/src/rest` → generates `de.mhus.nimbus.generated.rest` package

## Purpose

The purpose of this module is to maintain a synchronized set of data classes and constants between the TypeScript frontend and the Java backend. Instead of manually maintaining two copies of the same data structures, we generate the Java classes from the TypeScript definitions.

## Directory Structure

```
server/generated/
├── pom.xml
├── README.md
└── src/main/java/de/mhus/nimbus/generated/
    ├── types/                      (generated from client/packages/shared/src/types)
    │   ├── Vector3.java
    │   ├── Rotation.java
    │   ├── Block.java
    │   ├── BlockStatus.java
    │   └── ... (63 generated classes)
    ├── network/                    (generated from client/packages/shared/src/network/messages)
    │   ├── LoginRequestData.java
    │   ├── ChunkDataTransferObject.java
    │   ├── EntityPositionUpdateData.java
    │   └── ... (26 generated classes)
    ├── constants/                  (generated from client/packages/shared/src/constants)
    │   ├── BlockConstants.java
    │   ├── ChunkConstants.java
    │   ├── NetworkConstants.java
    │   └── ... (9 generated classes)
    └── rest/                       (generated from client/packages/shared/src/rest)
        ├── WorldDetailDTO.java
        ├── BlockTypeDTO.java
        ├── BlockMetadataDTO.java
        └── ... (9 generated classes)
```

## Generation Process

### Prerequisites

- **Node.js** must be installed on your system to run the generator script
- The TypeScript source files must be present in both source directories

### How to Generate Java Classes

There are two ways to generate the Java classes:

#### Method 1: Using the Shell Script (Recommended)

Run the generation script directly:

```bash
cd /path/to/nimbus
./scripts/generate-java-from-typescript.sh
```

This will:
1. **First run**: Parse TypeScript files in `client/packages/shared/src/types`
   - Extract interfaces and enums
   - Generate Java classes in `server/generated/src/main/java/de/mhus/nimbus/generated/types`
   - Clean old generated files before creating new ones
2. **Second run**: Parse TypeScript files in `client/packages/shared/src/network/messages`
   - Extract interfaces and enums
   - Generate Java classes in `server/generated/src/main/java/de/mhus/nimbus/generated/network`
   - Automatically adds imports for types from the `types` package
   - Clean old generated files before creating new ones
3. **Third run**: Parse TypeScript files in `client/packages/shared/src/constants`
   - Extract constant objects (exported with `as const`)
   - Generate Java constant classes with static final fields in `server/generated/src/main/java/de/mhus/nimbus/generated/constants`
   - Clean old generated files before creating new ones
4. **Fourth run**: Parse TypeScript files in `client/packages/shared/src/rest`
   - Extract REST API DTO interfaces
   - Generate Java DTO classes in `server/generated/src/main/java/de/mhus/nimbus/generated/rest`
   - Clean old generated files before creating new ones

#### Method 2: Using Maven Profile

Run the generation through Maven:

```bash
cd server/generated
mvn generate-sources -Pgenerate-from-typescript
```

Or from the project root:

```bash
cd /path/to/nimbus
mvn generate-sources -Pgenerate-from-typescript -pl server/generated
```

### After Generation

After generating the Java classes, build the module normally:

```bash
cd server/generated
mvn clean install
```

Or build the entire project:

```bash
cd /path/to/nimbus
mvn clean install
```

## Generated Class Structure

### TypeScript Interfaces → Java Classes

TypeScript interfaces are converted to Java classes with Lombok annotations:

**TypeScript:**
```typescript
export interface Vector3 {
  x: number;
  y: number;
  z: number;
}
```

**Generated Java:**
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vector3 {
    private double x;
    private double y;
    private double z;
}
```

### TypeScript Enums → Java Enums

TypeScript enums are converted to Java enums with value getters:

**TypeScript:**
```typescript
export enum BlockStatus {
  DEFAULT = 0,
  OPEN = 1,
  CLOSED = 2
}
```

**Generated Java:**
```java
public enum BlockStatus {
    DEFAULT(0),
    OPEN(1),
    CLOSED(2);

    private final int value;

    BlockStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
```

### Optional Fields

TypeScript optional fields (marked with `?`) are converted to wrapper types in Java:

**TypeScript:**
```typescript
export interface Rotation {
  y: number;
  p: number;
  r?: number;  // optional
}
```

**Generated Java:**
```java
public class Rotation {
    private double y;
    private double p;
    private Double r;  // Optional field uses wrapper type
}
```

### TypeScript Constant Objects → Java Constants Classes

TypeScript constant objects (declared with `export const ... as const`) are converted to Java classes with static final fields:

**TypeScript:**
```typescript
export const BlockConstants = {
  AIR_BLOCK_ID: 0,
  MAX_BLOCK_TYPE_ID: 65535,
  MIN_BLOCK_TYPE_ID: 0,
  DEFAULT_STATUS: 0,
  MAX_STATUS: 255,
} as const;
```

**Generated Java:**
```java
public final class BlockConstants {

    // Private constructor to prevent instantiation
    private BlockConstants() {
        throw new UnsupportedOperationException("This is a constants class and cannot be instantiated");
    }

    /**
     * AIR_BLOCK_ID
     */
    public static final int AIR_BLOCK_ID = 0;

    /**
     * MAX_BLOCK_TYPE_ID
     */
    public static final int MAX_BLOCK_TYPE_ID = 65535;

    /**
     * MIN_BLOCK_TYPE_ID
     */
    public static final int MIN_BLOCK_TYPE_ID = 0;

    /**
     * DEFAULT_STATUS
     */
    public static final int DEFAULT_STATUS = 0;

    /**
     * MAX_STATUS
     */
    public static final int MAX_STATUS = 255;
}
```

**Features:**
- Final class to prevent inheritance
- Private constructor to prevent instantiation
- Public static final fields for constants
- Automatic type detection (int, double, String, boolean)
- Expression evaluation (e.g., `10 * 1024 * 1024` → `10485760`)
- Nested objects and arrays are skipped (not generated)

## Package Structure

The generated classes are organized into four packages:

### `de.mhus.nimbus.generated.types` Package

Contains core data types and domain models generated from `client/packages/shared/src/types`:
- Basic types: `Vector3`, `Rotation`, `Vector2`, `Color`
- Block-related: `Block`, `BlockType`, `BlockModifier`, `BlockMetadata`, `BlockStatus`
- Entity-related: `Entity`, `EntityModel`, `EntityData`, `ClientEntity`
- Chunk-related: `ChunkData`, `Backdrop`
- Player-related: `PlayerInfo`, `PlayerMovementState`
- Other: `AnimationData`, `AreaData`, `WorldInfo`, `VitalsData`, etc.

### `de.mhus.nimbus.generated.network` Package

Contains network message data classes generated from `client/packages/shared/src/network/messages`:
- Login/Logout: `LoginRequestData`, `LoginResponseData`, `LoginErrorData`
- Chunks: `ChunkDataTransferObject`, `ChunkCoordinate`, `ChunkQueryData`
- Entities: `EntityPositionUpdateData`, `EntityInteractionData`
- Blocks: `BlockStatusUpdate`, `BlockInteractionData`
- User: `UserMovementData`, `PlayerTeleportData`
- Commands: `CommandData`, `ServerCommandData`
- Other: `PingData`, `PongData`, `InteractionRequestData`, etc.

### `de.mhus.nimbus.generated.constants` Package

Contains constant classes with static final fields generated from `client/packages/shared/src/constants`:
- `ChunkConstants`: Chunk size limits and defaults
- `WorldConstants`: World dimensions, sea level, ground level
- `BlockConstants`: Block IDs, status values, offset limits
- `NetworkConstants`: Network timeouts, message sizes, reconnect settings
- `EntityConstants`: Player speeds, jump height, entity limits
- `RenderConstants`: Render distance, FPS targets, LOD thresholds
- `AnimationConstants`: Animation durations and limits
- `PhysicsConstants`: Gravity, drag, friction values
- `LimitConstants`: Collection size limits for messages and queues

### `de.mhus.nimbus.generated.rest` Package

Contains REST API Data Transfer Objects (DTOs) generated from `client/packages/shared/src/rest`:
- World DTOs: `WorldDetailDTO`, `WorldListItemDTO`, `WorldSettingsDTO`, `UserDTO`, `Position3D`
- BlockType DTOs: `BlockTypeDTO`, `BlockTypeOptionsDTO`, `BlockTypeListResponseDTO`
- Block Metadata DTOs: `BlockMetadataDTO`

These DTOs are used for REST API communication between client and server, providing a clean separation from the internal domain models in the `types` package.

### Cross-Package Dependencies

The `network` package classes often reference types from the `types` package. The generator automatically adds the necessary imports:

```java
package de.mhus.nimbus.generated.network;

import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Rotation;
import de.mhus.nimbus.generated.types.Block;
// ... other imports

public class EntityPositionUpdateData {
    private Vector3 p;      // Position
    private Rotation r;     // Rotation
    // ... other fields
}
```

## Type Mapping

The generator uses the following type mappings:

| TypeScript Type | Java Type |
|----------------|-----------|
| `number` | `double` |
| `string` | `String` |
| `boolean` | `boolean` |
| `any` | `Object` |
| `Date` | `java.time.Instant` |
| `Type[]` | `java.util.List<Type>` |
| `Record<K, V>` | `java.util.Map<K, V>` |
| Custom types | Same name (assumed to be defined) |

## When to Regenerate

You should regenerate the Java classes whenever:

1. New TypeScript interfaces or enums are added
2. Existing TypeScript interfaces or enums are modified
3. TypeScript interfaces or enums are removed

## Important Notes

1. **DO NOT EDIT** the generated Java files manually. All changes will be lost on the next generation.
2. The generated files contain a header comment indicating they are auto-generated.
3. If you need custom behavior, create separate utility classes or extend the generated classes in a different package.
4. The generator script is located in `scripts/ts-to-java-generator.js`
5. The shell wrapper script is located in `scripts/generate-java-from-typescript.sh`

## Troubleshooting

### "Node.js not found" Error

If you see this error, install Node.js:
- macOS: `brew install node`
- Linux: `sudo apt-get install nodejs` or `sudo yum install nodejs`
- Windows: Download from https://nodejs.org/

### Generation Script Fails

1. Check that the TypeScript source files exist in `client/packages/shared/src/types`
2. Verify the script has execute permissions: `chmod +x scripts/generate-java-from-typescript.sh`
3. Check the Node.js script for syntax errors: `node scripts/ts-to-java-generator.js`

### Compilation Errors After Generation

1. Ensure all referenced types are also generated or exist in other modules
2. Check that Lombok is properly configured in the parent POM
3. Verify the generated code syntax by reviewing a few generated files

## Integration with Other Modules

Other server modules can depend on the `generated` module to use the generated classes:

```xml
<dependency>
    <groupId>de.mhus.nimbus</groupId>
    <artifactId>generated</artifactId>
    <version>${project.version}</version>
</dependency>
```

## Example Usage

```java
import de.mhus.nimbus.generated.Vector3;
import de.mhus.nimbus.generated.Rotation;
import de.mhus.nimbus.generated.BlockStatus;

// Using generated classes
Vector3 position = Vector3.builder()
    .x(10.0)
    .y(20.0)
    .z(30.0)
    .build();

Rotation rotation = Rotation.builder()
    .y(90.0)
    .p(45.0)
    .build();

BlockStatus status = BlockStatus.OPEN;
int statusValue = status.getValue(); // returns 1
```
