# Database upgrades

One-off migrations for the `world` database, run manually. Each script is
idempotent: running it again on an already-migrated database changes nothing and
reports zeros.

## Usage

```bash
./backup_all.sh                                   # always first - full NDJSON export
./execute.sh 002_migrate_wearing_slot.js          # then the migration
./restore_all.sh exports/backup_20260814_161145   # rollback if needed
```

Every script has a `DRY_RUN` constant at the top. Set it to `true` to see exactly
what would change without writing - the preview lists the same records the write
path would touch.

Requires `mongodb-database-tools` for backup/restore:
`brew install mongodb/brew/mongodb-database-tools`

## Scripts

| Script | What | Scope when it ran |
|---|---|---|
| `001_update_asset_path.js` | strips the leading path segment from `s_assets.path` | — |
| `002_migrate_wearing_slot.js` | legacy item parameter `wearableSlots` → `wearingSlot` | 115 items: 49 set, 66 cleaned |
| `003_normalize_block_effect.js` | numeric `BlockEffect` values → enum name | 2 textures (`bamboo`, `bamboo_top`) |

### 002 - why it mattered

`PlayerWearingController` reads `parameters.wearingSlot` and only falls back to
`parameters.wearableSlots` when that is a **List**. In the data it was always a
**String**, so the fallback never applied: items carrying only the legacy value
received no slot validation at all and could be worn in any slot. The value is a
`WEARABLE_GROUP` name; `HANDS` was a legacy spelling of `HAND`.

### 003 - why it was harmless but inconsistent

`BlockEffect` is deliberately numeric in TypeScript (`NONE=0, UNDULATION=1,
WIND=2`), so imported assets could store the raw number while anything written
through Java stores the name. Both are readable - Spring converts Integer to enum
by ordinal, and the client's `normalizeEffect()` accepts number, numeric string
and name - so this only removed the inconsistency.

## One-off fixes without a script

Recorded here because they are single documents with no repeat value:

- **2026-08-14** - deleted one `w_chests` document with `type: "USER"`
  (`_id 69451be019f7e06fc1a9f1e3`, `name: "Yo"`, `regionId: earth616`,
  `worldId: ""`, one item "Iron Sword", created 2025-12-19). `USER` was a valid
  `ChestType` until commit `ae16bb6c` (2026-03-03) renamed it to `PLAYER` without
  migrating the data. The document predates the repository history, had an empty
  `worldId` and was unreachable through the API, which addresses chests via
  `/control/worlds/{worldId}/chests`.
