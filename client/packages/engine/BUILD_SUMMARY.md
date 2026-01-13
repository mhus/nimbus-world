# Build System Summary - Viewer & Editor Variants

## Implementation Date
2025-10-28

## Overview

Successfully implemented conditional compilation for the Nimbus Client, creating two build variants from a single codebase:
- **Viewer**: Read-only 3D engine (~12.47 KB gzipped)
- **Editor**: Full engine with editing features (~12.52 KB gzipped)

## Files Changed

### 1. vite.config.ts
Added mode-based configuration with global constants:
```typescript
define: {
  __EDITOR__: JSON.stringify(mode === 'editor'),
  __VIEWER__: JSON.stringify(mode === 'viewer' || mode === 'development'),
  __BUILD_MODE__: JSON.stringify(mode),
}
```

Output directories:
- Viewer: `dist/viewer/`
- Editor: `dist/editor/`

### 2. package.json
Updated scripts:
```json
{
  "dev": "vite --mode viewer",
  "dev:editor": "vite --mode editor",
  "build": "pnpm build:viewer && pnpm build:editor && tsc",
  "build:viewer": "vite build --mode viewer",
  "build:editor": "vite build --mode editor",
  "preview:editor": "vite preview --outDir dist/editor"
}
```

### 3. src/vite-env.d.ts (NEW)
TypeScript declarations for global constants:
```typescript
declare const __EDITOR__: boolean;
declare const __VIEWER__: boolean;
declare const __BUILD_MODE__: 'viewer' | 'editor' | 'development';
```

### 4. src/NimbusClient.ts
Added build variant detection and conditional code:
```typescript
const buildMode = __EDITOR__ ? 'Editor' : 'Viewer';

if (__EDITOR__) {
  console.log('Editor mode: Initializing editor functions...');
  // TODO: Initialize EditorService, CommandConsole, etc.
}
```

### 5. BUILD_VARIANTS.md (NEW)
Comprehensive documentation (350+ lines) covering:
- How conditional compilation works
- Usage patterns and examples
- Best practices
- Tree-shaking demonstration
- Testing guidelines

### 6. README.md
Updated with:
- Build variant information
- New build commands
- Output structure
- Usage examples

### 7. CLAUDE.md
Enhanced Build Variants section with:
- Conditional compilation explanation
- Implementation example
- All build commands

## Build Output

### Structure
```
dist/
├── viewer/
│   ├── index.html
│   └── assets/
│       └── main-[hash].js       # 39.09 KB (12.47 KB gzipped)
├── editor/
│   ├── index.html
│   └── assets/
│       └── main-[hash].js       # 39.24 KB (12.52 KB gzipped)
├── NimbusClient.d.ts
├── NimbusClient.d.ts.map
└── types/
    ├── ClientBlock.d.ts
    ├── ClientBlock.d.ts.map
    ├── ClientBlockType.d.ts
    ├── ClientBlockType.d.ts.map
    ├── ClientChunk.d.ts
    ├── ClientChunk.d.ts.map
    ├── index.d.ts
    └── index.d.ts.map
```

### Bundle Sizes

| Build   | Uncompressed | Gzipped | Difference |
|---------|--------------|---------|------------|
| Viewer  | 39.09 KB     | 12.47 KB | Baseline   |
| Editor  | 39.24 KB     | 12.52 KB | +0.15 KB / +0.05 KB |

**Note**: Size difference is minimal because editor-specific code is not yet implemented. Once editor features are added, the difference will increase to ~2-5 KB gzipped.

## Usage Examples

### Conditional Code

```typescript
// Editor-only initialization (removed from viewer build)
if (__EDITOR__) {
  const EditorService = await import('./services/EditorService');
  const editor = new EditorService.default();
  editor.initialize();
}

// Viewer-only code (removed from editor build)
if (__VIEWER__) {
  console.log('Read-only viewer mode');
  disableEditingFeatures();
}
```

### Feature Flags

```typescript
export const FEATURES = {
  BLOCK_EDITING: __EDITOR__,
  TERRAIN_EDITING: __EDITOR__,
  CONSOLE_COMMANDS: __EDITOR__,
  WORLD_EXPORT: __EDITOR__,
  READ_ONLY_MODE: __VIEWER__,
} as const;
```

## Commands

### Development
```bash
pnpm dev              # Viewer mode (localhost:3001)
pnpm dev:editor       # Editor mode (localhost:3001)
```

### Building
```bash
pnpm build            # Build both variants + declarations
pnpm build:viewer     # Viewer only
pnpm build:editor     # Editor only
```

### Preview
```bash
pnpm preview          # Preview viewer
pnpm preview:editor   # Preview editor
```

## Verification

All builds tested and working:
- ✅ Viewer build completes successfully
- ✅ Editor build completes successfully
- ✅ TypeScript declarations generated
- ✅ Both variants display correct build mode
- ✅ Global constants accessible
- ✅ Tree-shaking ready (conditional code in place)

## Future Implementation

When adding editor-specific features:

1. **Wrap in conditional**:
```typescript
if (__EDITOR__) {
  // Editor code here
}
```

2. **Lazy load heavy modules**:
```typescript
if (__EDITOR__) {
  const Module = await import('./editor/HeavyModule');
  // Use module
}
```

3. **Create editor-specific directories**:
```
src/
├── editor/           # Editor-only code
│   ├── EditorService.ts
│   ├── CommandConsole.ts
│   └── tools/
└── viewer/           # Viewer-specific code (if any)
```

## Benefits

1. **Single Codebase**: One implementation, two builds
2. **Tree-Shaking**: Unused code automatically removed
3. **Type Safety**: TypeScript support for global constants
4. **Clear Separation**: Editor vs Viewer features clearly marked
5. **Optimal Size**: Each build contains only necessary code
6. **Easy Testing**: Test both variants with single command
7. **Maintainability**: Changes automatically apply to both builds

## Documentation

- [BUILD_VARIANTS.md](./BUILD_VARIANTS.md) - Detailed guide (350+ lines)
- [README.md](./README.md) - Updated with build commands
- [CLAUDE.md](../../../CLAUDE.md) - Architecture overview
- [src/types/README.md](./src/types/README.md) - Client types

## Next Steps

1. Implement EditorService with conditional loading
2. Add CommandConsole (editor-only)
3. Create block editing tools (editor-only)
4. Add terrain modification (editor-only)
5. Implement world export (editor-only)
6. Measure bundle size difference after implementation

## Notes

- Global constants are injected at compile time
- Dead code is eliminated by Rollup
- Development mode defaults to viewer
- Build artifacts separated by variant
- TypeScript declarations shared between variants
