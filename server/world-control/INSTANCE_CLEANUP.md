# Automatic Instance Cleanup

## Overview

The world-control service includes an automatic cleanup scheduler that periodically deletes unused world instances to prevent database bloat and resource waste.

## How It Works

1. **Scheduled Task**: Runs periodically (default: every hour)
2. **Age Check**: Identifies instances with `updatedAt` older than configured maximum age (default: 48 hours)
3. **Deletion**: Deletes old instances (with safety limits and dry-run option)

## Configuration

Add to your `application.yml`:

```yaml
nimbus:
  instance:
    cleanup:
      # Enable/disable automatic cleanup
      enabled: true

      # Maximum age of unused instances in hours
      max-age-hours: 48

      # How often cleanup runs (milliseconds)
      cleanup-interval-ms: 3600000  # 1 hour

      # Max instances to delete per run
      max-deletes-per-run: 100

      # Test mode: log what would be deleted without actually deleting
      dry-run: false
```

## Safety Features

### 1. Only Runs in world-control
The scheduler automatically detects its environment using `LocationService.isWorldControl()` and only runs when deployed as world-control service.

### 2. Batch Limits
The `max-deletes-per-run` setting prevents deleting too many instances at once, which could overload the system.

### 3. Dry-Run Mode
Enable `dry-run: true` to test the cleanup logic without actually deleting anything:

```yaml
nimbus:
  instance:
    cleanup:
      dry-run: true
```

The logs will show what would be deleted:
```
DRY-RUN: Would delete instance: instanceId=world123!abc, updatedAt=..., age=72h
```

### 4. Detailed Logging
Every cleanup run logs:
- Total instances found
- Instances deleted
- Instances skipped
- Errors encountered

## When Instances Are Updated

The `updatedAt` timestamp is refreshed when:
- A player joins the instance (via `addActivePlayer()`)
- A player leaves the instance (via `removeActivePlayer()`)
- The instance is manually updated via `instanceService.save()`

This ensures active instances are never deleted.

## Monitoring

### Log Messages

**Normal operation:**
```
[INFO] Starting automatic instance cleanup (maxAge=48h, dryRun=false)
[INFO] Found 150 total instances
[INFO] Deleting unused instance: instanceId=world123!abc, updatedAt=..., age=72h
[INFO] Instance deleted successfully: instanceId=world123!abc
[INFO] Instance cleanup completed: deleted=3, skipped=0, total=150, dryRun=false
```

**When disabled:**
```
[DEBUG] Instance cleanup is disabled
```

**When not running in world-control:**
```
[DEBUG] Instance cleanup skipped - not running in world-control
```

### Metrics to Track

Monitor these in production:
1. **Cleanup frequency**: Should run every hour (or configured interval)
2. **Deletion count**: How many instances are deleted per run
3. **Error count**: Any errors during cleanup
4. **Total instance count**: Trend over time

## Troubleshooting

### Cleanup Not Running

**Check 1: Is scheduling enabled?**
```java
@EnableScheduling  // Should be in WorldControlApplication
```

**Check 2: Is cleanup enabled in config?**
```yaml
nimbus.instance.cleanup.enabled: true
```

**Check 3: Are you running in world-control?**
The scheduler only runs when `LocationService.isWorldControl()` returns true.

### Too Many Instances Being Deleted

**Reduce batch size:**
```yaml
nimbus.instance.cleanup.max-deletes-per-run: 50
```

**Increase max age:**
```yaml
nimbus.instance.cleanup.max-age-hours: 72  # 3 days
```

### Instances Deleted Too Quickly

**Increase max age:**
```yaml
nimbus.instance.cleanup.max-age-hours: 96  # 4 days
```

## Development & Testing

### Testing the Scheduler

1. **Enable dry-run mode:**
```yaml
nimbus.instance.cleanup.dry-run: true
```

2. **Reduce interval for testing:**
```yaml
nimbus.instance.cleanup.cleanup-interval-ms: 60000  # 1 minute
```

3. **Reduce max age for testing:**
```yaml
nimbus.instance.cleanup.max-age-hours: 1  # 1 hour
```

4. **Check logs:**
Look for `DRY-RUN` messages showing what would be deleted.

5. **Disable dry-run and verify:**
```yaml
nimbus.instance.cleanup.dry-run: false
```

### Manual Cleanup

If you need to manually trigger cleanup, you can call the scheduler method directly or use the REST API to delete instances via `WWorldInstanceController`.

## Production Recommendations

- **Start with dry-run**: Test in production with `dry-run: true` for a few days
- **Monitor deletion counts**: Ensure they're reasonable
- **Adjust max age**: Based on your usage patterns (48h default is a good starting point)
- **Keep batch limits**: Prevents database overload
- **Watch logs**: Set up alerts for cleanup errors

## Related Components

- `InstanceCleanupScheduler`: Main scheduler service
- `InstanceCleanupProperties`: Configuration properties
- `WWorldInstanceService`: Instance management service
- `SessionLifecycleController`: Handles session-closed events
- `WWorldInstance.updatedAt`: Timestamp used for cleanup decisions
