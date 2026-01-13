# Build Variants - Viewer and Editor

The Nimbus Client uses **conditional compilation** to build two separate variants from a single codebase:

1. **Viewer** - Read-only 3D engine for viewing worlds
2. **Editor** - Full 3D engine with editor functions and console

## How It Works

Vite injects global boolean constants at compile time. The bundler (Rollup) performs **tree-shaking** to eliminate unreachable code based on these constants.

### Global Constants

```typescript
// vite-env.d.ts
declare const __EDITOR__: boolean;   // true in editor, false in viewer
declare const __VIEWER__: boolean;   // true in viewer, false in editor
declare const __BUILD_MODE__: 'viewer' | 'editor' | 'development';
```

### Vite Configuration

```typescript
// vite.config.ts
export default defineConfig(({ mode }) => {
  const isEditor = mode === 'editor';
  const isViewer = mode === 'viewer' || mode === 'development';

  return {
    build: {
      outDir: isEditor ? 'dist/editor' : 'dist/viewer',
    },
    define: {
      __EDITOR__: JSON.stringify(isEditor),
      __VIEWER__: JSON.stringify(isViewer),
      __BUILD_MODE__: JSON.stringify(mode),
    },
  };
});
```

## Usage in Code

### Conditional Code Blocks

```typescript
// Editor-only code (removed from viewer build)
if (__EDITOR__) {
  console.log('Editor mode active');
  initializeEditorService();
  initializeCommandConsole();
  loadEditorUI();
}

// Viewer-only code (removed from editor build)
if (__VIEWER__) {
  console.log('Viewer mode - read-only');
  disableEditingFeatures();
}
```

### Lazy Loading Editor Components

```typescript
// EditorPanel is only loaded in editor build
let EditorPanel: any = null;

if (__EDITOR__) {
  EditorPanel = await import('./editor/EditorPanel');
}

// Usage
if (__EDITOR__ && EditorPanel) {
  const panel = new EditorPanel();
  panel.show();
}
```

### Feature Flags

```typescript
// Feature availability checks
export const FEATURES = {
  BLOCK_EDITING: __EDITOR__,
  TERRAIN_EDITING: __EDITOR__,
  CONSOLE_COMMANDS: __EDITOR__,
  WORLD_EXPORT: __EDITOR__,
  READ_ONLY_MODE: __VIEWER__,
} as const;

// Usage
if (FEATURES.BLOCK_EDITING) {
  addBlockEditingHandlers();
}
```

### Service Initialization

```typescript
// AppContext setup
const services: Services = {
  network: new NetworkService(config),
  input: new InputService(),
  render: new RenderService(engine),
  // Editor-only services
  ...((__EDITOR__) && {
    editor: new EditorService(),
    console: new CommandConsole(),
    blockEditor: new BlockEditorService(),
  }),
};
```

## Build Commands

### Development

```bash
# Viewer mode (default)
pnpm dev

# Editor mode
pnpm dev:editor
```

### Production Build

```bash
# Build both variants
pnpm build

# Build only viewer
pnpm build:viewer

# Build only editor
pnpm build:editor
```

### Preview Builds

```bash
# Preview viewer build
pnpm preview

# Preview editor build
pnpm preview:editor
```

## Output Structure

```
packages/engine/
├── dist/
│   ├── viewer/
│   │   ├── index.html
│   │   └── assets/
│   │       └── main-[hash].js     # Viewer bundle (~39 KB)
│   ├── editor/
│   │   ├── index.html
│   │   └── assets/
│   │       └── main-[hash].js     # Editor bundle (~50-60 KB)
│   ├── NimbusClient.d.ts          # TypeScript declarations
│   └── types/
│       └── *.d.ts
```

## Bundle Size Comparison

| Build   | Size (gzipped) | Description                        |
|---------|---------------|------------------------------------|
| Viewer  | ~12.5 KB      | Minimal, read-only features        |
| Editor  | ~15-18 KB     | Includes editor UI and commands    |

The editor build is slightly larger because it includes:
- Editor UI components
- Command console
- Block editing tools
- World export functionality
- Additional keyboard/mouse handlers

## Best Practices

### ✅ DO

```typescript
// Conditional initialization
if (__EDITOR__) {
  initializeEditor();
}

// Feature-specific imports
if (__EDITOR__) {
  const { EditorUI } = await import('./editor/EditorUI');
  new EditorUI().mount();
}

// Build-time constants
const EDIT_MODE = __EDITOR__;
```

### ❌ DON'T

```typescript
// Don't use runtime checks for build variants
const isEditor = process.env.MODE === 'editor'; // ❌ Won't tree-shake

// Don't mix build-time and runtime conditions
if (__EDITOR__ || runtimeFlag) { // ❌ Confusing, won't optimize
  doSomething();
}

// Don't use for user permissions (use runtime checks)
if (__EDITOR__) { // ❌ Wrong - this is build-time
  if (user.hasPermission('edit')) { // ✅ Use runtime check instead
    allowEditing();
  }
}
```

## Tree-Shaking Example

### Source Code

```typescript
if (__EDITOR__) {
  console.log('Editor features loaded');
  import('./editor/heavy-module').then(m => m.init());
}

if (__VIEWER__) {
  console.log('Viewer mode active');
}
```

### Viewer Build Output

```javascript
// Editor code completely removed
console.log('Viewer mode active');
```

### Editor Build Output

```javascript
// Viewer-only code removed
console.log('Editor features loaded');
import('./editor/heavy-module').then(m => m.init());
```

## Type Safety

TypeScript knows about the global constants:

```typescript
// vite-env.d.ts provides types
if (__EDITOR__) {
  // TypeScript knows this code only runs in editor
  const editor = new EditorService(); // No error
}
```

## Future Extensions

When adding new features:

1. **Decide build variant**: Should it be in viewer, editor, or both?
2. **Wrap in conditional**: Use `if (__EDITOR__)` or `if (__VIEWER__)`
3. **Lazy load if large**: Use dynamic imports for heavy modules
4. **Document the decision**: Add comments explaining the reasoning

Example:
```typescript
// Heavy 3D model editor - only in editor build
if (__EDITOR__) {
  const ModelEditor = await import('./editor/ModelEditor');
  registerTool(new ModelEditor.ModelEditorTool());
}
```

## Testing

Test both variants:

```bash
# Test viewer build
pnpm build:viewer && pnpm preview

# Test editor build
pnpm build:editor && pnpm preview:editor
```

Verify:
- [ ] Viewer bundle doesn't include editor code
- [ ] Editor bundle includes all necessary features
- [ ] Both builds work correctly
- [ ] Bundle sizes are as expected

## Debugging

Enable source maps for debugging:

```typescript
// vite.config.ts
export default defineConfig({
  build: {
    sourcemap: true,  // Enable for debugging
  },
});
```

Check which code is included:

```bash
# Analyze bundle
pnpm build:viewer
ls -lh dist/viewer/assets/*.js

pnpm build:editor
ls -lh dist/editor/assets/*.js
```

## Summary

- **One codebase** → Multiple build variants
- **Compile-time constants** → Tree-shaking optimization
- **Clear separation** → Viewer vs Editor features
- **Type-safe** → TypeScript support for global constants
- **Small bundles** → Dead code elimination
