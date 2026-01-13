# Asset Description Generator

JobExecutor that automatically generates AI-powered descriptions for game assets using Google Gemini.

## Features

- **Two Modes**: Bulk processing or single asset
- **Intelligent Filtering**: Only processes images without descriptions (bulk mode)
- **Force Regeneration**: Always regenerates for single asset mode
- **Rate Limiting**: Respects Gemini API limits (15 RPM default)
- **Memory Efficient**: Sequential processing
- **Size Limits**: Configurable maximum file size (default: 5MB)
- **Supported Formats**: PNG, JPG, JPEG, GIF, BMP

## Configuration

### application.yml

```yaml
# Gemini API Configuration
langchain4j:
  gemini:
    api-key: ${GEMINI_API_KEY:}
    # Rate limit for Flash models only (e.g., gemini-1.5-flash)
    # Shared globally across all Flash model chat instances
    flash-rate-limit: 15

# Asset Description Settings
asset:
  description:
    max-size-bytes: 5242880  # 5MB
    ai-model: default:generate

# AI Model Mappings
ai:
  model:
    mappings: chat=openai:gpt-3.5-turbo,generate=gemini:gemini-pro
```

### Environment Variables

```bash
export GEMINI_API_KEY="your-api-key-here"
```

## Usage

### Bulk Mode - Process All Assets

Generates descriptions for all assets in a world that don't have one yet.

**Job Parameters:**
```json
{
  "worldId": "world-123"
}
```

**Example:**
```java
WJob job = WJob.builder()
    .executorName("asset-description-generator")
    .worldId("world-123")
    .parameters(Map.of(
        "worldId", "world-123"
    ))
    .build();
```

**Result:**
```
Processed 150/150 assets: 45 generated, 100 skipped, 5 errors
```

### Single Asset Mode - Force Regenerate

Generates description for a specific asset, **even if it already has one**.

**Job Parameters:**
```json
{
  "worldId": "world-123",
  "assetPath": "textures/items/sword.png"
}
```

**Example:**
```java
WJob job = WJob.builder()
    .executorName("asset-description-generator")
    .worldId("world-123")
    .parameters(Map.of(
        "worldId", "world-123",
        "assetPath", "textures/items/sword.png"
    ))
    .build();
```

**Result:**
```
Generated description for asset: textures/items/sword.png
```

## Processing Logic

### Bulk Mode
1. Loads all assets from world
2. For each asset:
   - ✅ **Skip** if description exists
   - ✅ **Skip** if not an image
   - ✅ **Skip** if > max size
   - ✅ **Generate** if no description

### Single Asset Mode
1. Loads specific asset by path
2. **Always generates** description (force regeneration)
3. Validates file type and size
4. Updates existing or creates new description

## Rate Limiting

### Gemini Flash Models Only

Rate limiting is **only applied to Gemini Flash models** (e.g., `gemini-1.5-flash`).

- **Flash models**: 15 requests per minute (free tier)
- **Pro models**: No rate limiting (higher quota)

The system automatically detects Flash models by checking if "flash" is in the model name.

### Global Rate Limiter

The rate limiter is **shared across all chat instances** from the same provider:

```
GeminiLangchainModel (Provider Instance)
    └── flashRateLimiter (Global, Shared)
         ├── Chat 1 (gemini-1.5-flash)
         ├── Chat 2 (gemini-1.5-flash)
         └── Chat 3 (gemini-1.5-flash)
```

All chats using Flash models share the same rate limit counter.

### Behavior

The executor automatically:
- Waits between requests to stay within limits
- Uses a sliding window algorithm
- Shares rate limit across all Flash model chats
- Logs when rate limiting is active

**Example Logs:**
```
INFO  - Initialized global Flash rate limiter: 15 RPM
INFO  - Created Gemini chat: model=gemini-1.5-flash, rateLimit=15 RPM (shared)
INFO  - Created Gemini chat: model=gemini-pro, no rate limit
DEBUG - Rate limit reached (15/15), waiting 1234ms
```

## Asset Filtering

### Supported File Types
- `.png`
- `.jpg`, `.jpeg`
- `.gif`
- `.bmp`

### Size Limit
Default: **5MB** (5,242,880 bytes)

Configurable via `asset.description.max-size-bytes`

## Description Format

AI-generated descriptions are:
- **Concise**: Max 100 characters
- **Single sentence**: Easy to read
- **Game-focused**: Describes visual appearance and purpose
- **Contextual**: Based on filename and asset location

**Examples:**
```
textures/items/sword.png → "A sharp medieval sword with a silver blade and golden handle."
textures/blocks/stone.png → "Rough gray stone block with natural texture."
models/characters/knight.obj → "Armored knight character model in battle stance."
```

## Error Handling

Errors for individual assets don't stop the job:
- **Invalid Image**: Skipped, logged as warning
- **Load Failure**: Skipped, logged as error
- **AI Timeout**: Retried automatically by rate limiter
- **Empty Response**: Skipped, logged as warning

Final result includes error count.

## Performance

### Bulk Mode
- **Sequential**: One asset at a time (memory-safe)
- **Progress**: Logs every 10 assets
- **Typical Speed**: ~15 assets per minute (rate limit)

### Single Asset Mode
- **Fast**: Only one API call
- **Immediate**: No waiting unless rate limited

## Logging

```
INFO  - Starting bulk asset description generation for world: world-123
INFO  - Found 150 assets in world 150
INFO  - Using AI model: gemini:gemini-pro
INFO  - Progress: 10/150 assets processed
INFO  - Progress: 20/150 assets processed
...
INFO  - Generated description for asset: textures/items/sword.png - "A sharp medieval sword..."
INFO  - Asset description generation completed: Processed 150/150 assets: 45 generated, 100 skipped, 5 errors
```

## Troubleshooting

### No API Key
```
ERROR - Failed to create AI chat: default:generate
```
**Solution**: Set `GEMINI_API_KEY` environment variable

### Rate Limit Exceeded
```
WARN  - Rate limit reached (15/15), waiting 3000ms
```
**Solution**: Normal behavior, executor waits automatically

### Asset Not Found (Single Mode)
```
ERROR - Asset not found: textures/missing.png
```
**Solution**: Verify asset path and worldId are correct

### All Assets Skipped
```
Processed 100/100 assets: 0 generated, 100 skipped, 0 errors
```
**Solution**: All assets already have descriptions (use single mode to regenerate specific ones)

## API Costs

### Gemini Free Tier
- **15 requests per minute**
- **1,500 requests per day**
- **Free for small volumes**

### Estimation
- 100 assets = ~7 minutes
- 1,000 assets = ~67 minutes
- Stay within daily limits for free tier

## Best Practices

1. **Test First**: Run single asset mode on a few assets first
2. **Off-Peak**: Run bulk jobs during off-peak hours
3. **Monitor**: Watch logs for errors and rate limiting
4. **Backup**: Keep original asset files before regenerating
5. **Review**: Check generated descriptions for quality
