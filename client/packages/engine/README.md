# @nimbus/engine

Nimbus Voxel Engine - 3D Engine with Viewer and Editor build variants

## Overview

The Nimbus Engine is a 3D voxel engine built with TypeScript, Vite, and Babylon.js. It provides two build variants from a single codebase using conditional compilation:

- **Viewer**: Read-only 3D engine for viewing voxel worlds (~12.5 KB gzipped)
- **Editor**: Full 3D engine with editing capabilities and command console (~15-18 KB gzipped)

## Features

### Viewer Build
- ✅ 3D voxel world rendering with Babylon.js
- ✅ WebSocket connection to server
- ✅ Chunk loading and rendering
- ✅ Camera controls and navigation
- ✅ Block type registry
- ✅ Minimal bundle size

### Editor Build (Additional Features)
- ✅ Block editing tools
- ✅ Terrain modification
- ✅ Command console
- ✅ World export functionality
- ✅ Editor UI components
- ✅ Development utilities

## Structure

```
src/
├── NimbusClient.ts       # Main entry point
├── config/               # Configuration
├── services/             # Core services (singleton)
│   ├── NetworkService.ts
│   ├── InputService.ts
│   ├── ChunkService.ts
│   └── ...
├── engine/               # 3D Engine
│   ├── EngineService.ts
│   ├── RenderService.ts
│   └── ...
├── network/              # Network protocol
│   ├── handlers/         # Message handlers
│   └── actions/          # REST actions
└── ui/                   # UI components
    └── StartScreen.ts
```

## Development

```bash
# Install dependencies
pnpm install

# Start dev server (viewer mode)
pnpm dev

# Start dev server (editor mode)
pnpm dev:editor

# Build both variants + TypeScript declarations
pnpm build

# Build only viewer
pnpm build:viewer

# Build only editor
pnpm build:editor

# Preview builds
pnpm preview         # Viewer
pnpm preview:editor  # Editor

# Clean build artifacts
pnpm clean
```

## Environment Variables

Copy `.env.example` to `.env` and configure:

```bash
CLIENT_USERNAME=your_username
CLIENT_PASSWORD=your_password
SERVER_WEBSOCKET_URL=ws://localhost:3000
SERVER_API_URL=http://localhost:3000
```

## Build Variants

The client uses **conditional compilation** with Vite to produce two builds:

```typescript
// Editor-only code (removed from viewer build)
if (__EDITOR__) {
  initializeEditor();
}

// Viewer-only code (removed from editor build)
if (__VIEWER__) {
  console.log('Read-only mode');
}
```

See [BUILD_VARIANTS.md](./BUILD_VARIANTS.md) for detailed documentation.

## Output Structure

```
dist/
├── viewer/
│   ├── index.html
│   └── assets/main-[hash].js
├── editor/
│   ├── index.html
│   └── assets/main-[hash].js
├── NimbusClient.d.ts
└── types/
    └── *.d.ts
```

## Client Types

Import client-specific types:

```typescript
import { ClientBlock, ClientChunk, ClientBlockType } from '@nimbus/engine/types';
import { Block, BlockType, ChunkData } from '@nimbus/shared';
```

See [src/types/README.md](./src/types/README.md) for documentation.

## Usage

The client will:
1. Show a start screen with login
2. Fetch available worlds from the server
3. Allow world selection
4. Initialize the 3D engine
5. Connect via WebSocket
6. Load and render chunks

## Documentation

- [BUILD_VARIANTS.md](./BUILD_VARIANTS.md) - Build variants documentation
- [MIGRATION_CLIENT_TYPES.md](./MIGRATION_CLIENT_TYPES.md) - Client types migration
- [../../../CLAUDE.md](../../../CLAUDE.md) - Claude AI development guide
- [src/types/README.md](./src/types/README.md) - Client types documentation

## Architecture

See [../../../CLAUDE.md](../../../CLAUDE.md) for detailed architecture documentation.
