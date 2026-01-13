# Nimbus Voxel Engine - Client 2.0

A modern voxel engine client built with TypeScript, Babylon.js, and Vite.

This is **Version 2.0** - a complete rewrite based on learnings from the prototype in `client_playground`.

## Project Structure

```
client/
├── packages/
│   ├── shared/          # Shared types, protocols, utils
│   ├── engine/          # 3D Engine (Viewer + Editor)
│   └── controls/        # UI controls and components
├── CLAUDE.md            # Claude AI development documentation
├── package.json         # Root workspace config
├── pnpm-workspace.yaml  # pnpm workspace definition
└── tsconfig.json        # TypeScript project references
```

## Technology Stack

- **TypeScript** - Type-safe development
- **Vite** - Build tool and dev server
- **Babylon.js** - 3D rendering engine
- **pnpm** - Fast, disk space efficient package manager
- **Jest** - Unit testing

## Getting Started

### Prerequisites

- Node.js >= 18.0.0
- pnpm >= 8.0.0

### Installation

```bash
# Install pnpm globally if not already installed
npm install -g pnpm

# Install dependencies for all packages
pnpm install
```

### Development

```bash
# Build all packages
pnpm build

# Development servers
pnpm dev:engine      # Engine viewer (port 3001)
pnpm dev:viewer      # Alias for viewer
pnpm dev:editor      # Engine editor (port 3001)
pnpm dev:controls    # UI controls (port 3002)

# Build specific packages
pnpm build:shared      # Shared types only
pnpm build:engine      # Engine (both variants)
pnpm build:viewer      # Viewer variant only
pnpm build:editor      # Editor variant only
pnpm build:controls    # UI controls only

# Utilities
pnpm test           # Run all tests
pnpm lint           # Run all linters
pnpm clean          # Clean all builds
```

**Typical workflow:**
```bash
# Terminal: Start engine
pnpm dev:engine
# OR
pnpm dev:editor
```

See [SCRIPTS.md](./SCRIPTS.md) for detailed script documentation.

### Environment Configuration

Each package has its own `.env.example` file. Copy these to `.env` and configure:

**Engine** (`packages/engine/.env`):
```bash
CLIENT_USERNAME=username
CLIENT_PASSWORD=password
SERVER_WEBSOCKET_URL=ws://localhost:3000
SERVER_API_URL=http://localhost:3000
```

## Package Overview

### @nimbus/shared
Shared types, protocols and utilities used by both client and server.

- Data types (Block, Chunk, Entity, etc.)
- Network protocol messages
- Utility functions

### @nimbus/engine
3D voxel engine with two build variants:

**Viewer** (~12.5 KB gzipped):
- Babylon.js 3D rendering
- WebSocket client
- Chunk loading and rendering
- Camera controls
- Input handling

**Editor** (~15-18 KB gzipped):
- All viewer features
- Block editing tools
- Command console
- Terrain modification
- World export

See [packages/engine/BUILD_VARIANTS.md](./packages/engine/BUILD_VARIANTS.md) for details.

### @nimbus/controls
UI controls and components for Nimbus.

## Architecture

See `CLAUDE.md` for detailed architecture documentation.

### Key Concepts

- **Services Architecture** - Singleton services registered in AppContext
- **Input System** - Abstract controller with action mapping
- **Network Protocol** - JSON-based WebSocket messages
- **Type Safety** - Full TypeScript strict mode

## Development Workflow

This is a **step-by-step migration** from the prototype:

1. Review prototype implementation in `client_playground`
2. Identify improvements and issues
3. Design 2.0 approach
4. Discuss and validate
5. Implement with tests
6. Document decisions

## Build Variants

The client uses **conditional compilation** to produce two builds from a single codebase:

- **viewer**: Read-only 3D engine for viewing worlds
- **editor**: Full 3D engine with editing capabilities and command console

Unreachable code is eliminated by the bundler (tree-shaking).

```typescript
// Editor-only code (removed from viewer build)
if (__EDITOR__) {
  initializeEditor();
}
```

See [packages/client/BUILD_VARIANTS.md](./packages/client/BUILD_VARIANTS.md) for implementation details.

## Contributing

This is the Version 2.0 development. Key principles:

- Build step-by-step, one area at a time
- Incorporate learnings from prototype
- Focus on quality over speed
- Test as you go
- Document decisions

## Documentation

- [SCRIPTS.md](./SCRIPTS.md) - Build and development scripts
- [CLAUDE.md](./CLAUDE.md) - Architecture and development guide
- [packages/engine/BUILD_VARIANTS.md](./packages/engine/BUILD_VARIANTS.md) - Build variants
- [packages/engine/MIGRATION_CLIENT_TYPES.md](./packages/engine/MIGRATION_CLIENT_TYPES.md) - Type migration

### Prototype Resources

- **Prototype**: `../client_playground/`
- **Instructions**: `../client_playground/instructions/client_2.0/`
- **Object Model**: `migration.md`, `object-model-2.0.md`
- **Network Protocol**: `network-model-2.0.md`
- **Architecture**: `migration-concepts.md`

## License

[License information to be added]
