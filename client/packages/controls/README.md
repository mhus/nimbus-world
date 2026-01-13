# Nimbus Editors

Multi-page editor suite for Nimbus - Comprehensive web applications for managing Block Types, Assets, Block Instances, and more.

## Tech Stack

- **Vue 3** - Progressive JavaScript framework
- **Vite** - Fast build tool with Multi-Page support
- **TypeScript** - Type-safe development
- **Tailwind CSS** - Utility-first CSS framework
- **DaisyUI** - Tailwind CSS component library
- **Headless UI** - Unstyled, accessible UI components
- **Axios** - HTTP client for REST API

## Editors

This package contains multiple editors in one Vite Multi-Page application:

### Material Editor
- **URL**: `http://localhost:3002/material-editor.html`
- **Purpose**: Manage Block Types and Assets for the entire world
- **Features**: Block Type CRUD, Asset Upload, Modifier Editor

### Block Editor
- **URL**: `http://localhost:3002/block-editor.html?world=main&block=10,64,5`
- **Purpose**: Edit individual block instances at specific coordinates
- **Features**: Block metadata, instance-specific modifiers (coming soon)

## Development

```bash
# Install dependencies
pnpm install

# Start dev server (http://localhost:3002)
# Opens Material Editor by default
pnpm dev

# Access different editors:
# - Material Editor: http://localhost:3002/material-editor.html
# - Block Editor:    http://localhost:3002/block-editor.html?world=main&block=10,64,5

# Build for production (builds ALL editors)
pnpm build

# Preview production build
pnpm preview

# Lint code
pnpm lint

# Clean build artifacts
pnpm clean
```

## Configuration

Copy `.env.example` to `.env.local` and configure:

```env
VITE_API_URL=http://localhost:3011
VITE_WORLD_ID=main
```

## Features

### Material Editor

**Block Type Editor:**
- List and search all block types
- Create, edit, and delete block types
- Multi-status support with comprehensive modifier editing:
  - **Visibility**: Shape selection, Textures, Geometry Offsets, Scaling, Rotation
  - **Physics**: Solid, Interactive, Resistance, Climbable, Gate Direction
  - **Wind**: Leafiness, Stability, Lever arms
  - **Effects**: Force Ego View, Sky effects
  - **Illumination**: Light color and strength
  - **Sound**: Walk, Ambient, and Status Change sounds
  - **Additional**: Sprite Count, Alpha transparency
- **Asset Picker** with magic wand button for easy texture selection
- **Geometry Offsets Editor** (shape-specific):
  - Cube/Hash/Cross: 8 corners × XYZ
  - Column: Radius and displacement offsets
  - Sphere: Radius offset and displacement
  - Flat/Glass_Flat: 4 corners × XYZ
- **Status ID Management**: Custom status IDs, change existing IDs
- Collapsible sections with enable/disable (saves as undefined when disabled)

**Asset Editor:**
- List and search all assets
- Preview image assets (PNG, JPG, etc.)
- Upload new assets with drag & drop
- Delete existing assets
- Grid view with thumbnails

### Block Editor (Skeleton)

- URL-parameter driven: `?world={worldId}&block={x},{y},{z}`
- Ready for block instance editing implementation
- Will use same Modifier editors as Material Editor

## Project Structure (Multi-Page Architecture)

```
├── material-editor.html    # Entry: Material Editor
├── block-editor.html       # Entry: Block Editor
└── src/
    ├── components/         # Shared components (SearchInput, LoadingSpinner, etc.)
    ├── editors/            # Modifier editors (Visibility, Physics, Wind, etc.)
    ├── services/           # API services (shared across editors)
    ├── composables/        # Vue composables (shared state management)
    ├── material/           # Material Editor app
    │   ├── MaterialApp.vue
    │   ├── main.ts
    │   ├── views/          # BlockTypeEditor, AssetEditor
    │   └── components/     # Material-specific components
    ├── block/              # Block Editor app
    │   ├── BlockApp.vue
    │   ├── main.ts
    │   ├── views/          # BlockInstanceEditor
    │   └── components/     # Block-specific components
    └── style.css           # Global Tailwind styles
```

## Production Build

Running `pnpm build` creates separate optimized builds for each editor:

```
dist/
├── material-editor.html
├── block-editor.html
└── assets/
    ├── material-editor-[hash].js   (~196 KB gzipped: 56 KB)
    ├── block-editor-[hash].js      (~2 KB gzipped: 1 KB)
    └── style-[hash].css            (~80 KB gzipped: 12 KB)
```

### Deployment

Deploy each editor to separate paths on your webserver:

```bash
# Copy dist to webserver
scp -r dist/* user@server:/var/www/nimbus/

# Or deploy individually
scp -r dist/material-editor.html dist/assets/ user@server:/var/www/nimbus/material-editor/
scp -r dist/block-editor.html dist/assets/ user@server:/var/www/nimbus/block-editor/
```

**Production URLs:**
- `http://yourserver.com/material-editor.html`
- `http://yourserver.com/block-editor.html?world=main&block=10,64,5`

## Architecture Benefits

✅ **One Dev Server** - Start only one process (`pnpm dev`) for all editors
✅ **No if/else** - Each editor is a completely separate Vue app
✅ **Code Sharing** - Shared components in `src/components/` and `src/editors/`
✅ **Tree-Shaking** - Each editor bundle only includes what it imports
✅ **Independent Builds** - Each editor builds separately (small bundles)
✅ **Easy Extension** - Add new editor = new HTML file + new src/{name}/ folder

## REST API

See `/client/instructions/client_2.0/server_rest_api.md` for complete API documentation.
