# Composer Model - Lessons Learned

This document contains common errors and corrections that have been learned from past translation attempts.
Use this as a reference to avoid making the same mistakes.

---

## Common Field Errors

### ForestBiome - NO "density" Field!

**WRONG:**
```json
{
  "featureType": "forest-biome",
  "type": "FOREST",
  "density": "DENSE_FOREST"
}
```

**Error message:**
> Unrecognized field "density" (class ForestBiome), not marked as ignorable

**CORRECT - Use parameters instead:**
```json
{
  "featureType": "forest-biome",
  "type": "FOREST",
  "parameters": {
    "flora_density": "0.9"
  }
}
```

**Valid values for flora_density parameter:**
- `"0.2"` - Very sparse trees
- `"0.4"` - Sparse trees
- `"0.6"` - Medium tree density (default if not specified)
- `"0.8"` - Dense forest
- `"0.9"` - Very dense forest

**Key points:**
- ForestBiome does NOT have a `density` field!
- Tree density is controlled via the `flora_density` parameter in the `parameters` object
- The value is a string representing a float between 0 and 1

---

## General Guidelines

1. **Check field existence** - Not all fields you might expect exist in the model!
2. **Use parameters for configuration** - Many biome properties are controlled via the `parameters` object, not direct fields
3. **Check field names carefully** - Use exact field names from the model description
4. **Required vs Optional** - Pay attention to which fields are required
5. **Valid references** - Ensure all referenced IDs (biomeId, continentId, etc.) exist in the model

---

*This document is automatically updated with new lessons learned from translation errors.*
