package de.mhus.nimbus.world.shared.world;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static utility for Copy-on-Write merging of world entities.
 * Merges base world entities with instance-specific overrides.
 *
 * <p>Merge rules:
 * <ul>
 *   <li>Instance entries override base entries with the same cowId</li>
 *   <li>Instance entries with enabled=false (tombstones) remove the base entry</li>
 *   <li>Instance entries with enabled=true that have no base counterpart are new additions</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CowUtil {

    /**
     * Merge base and instance entity lists.
     * Instance entries override base entries by cowId.
     * Tombstones (enabled=false) remove the corresponding base entry from the result.
     *
     * @param baseList     entities from the base world
     * @param instanceList entities from the instance (overrides + tombstones)
     * @return merged list with instance overrides applied
     */
    public static <T extends CowEntity> List<T> merge(List<T> baseList, List<T> instanceList) {
        if (instanceList == null || instanceList.isEmpty()) return baseList;
        if (baseList == null || baseList.isEmpty()) {
            return instanceList.stream()
                    .filter(CowEntity::isCowEnabled)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }

        Map<String, T> merged = new LinkedHashMap<>(baseList.size() + instanceList.size());
        for (T e : baseList) {
            merged.put(e.getCowId(), e);
        }
        for (T e : instanceList) {
            merged.put(e.getCowId(), e);
        }
        merged.values().removeIf(e -> !e.isCowEnabled());
        return new ArrayList<>(merged.values());
    }

    /**
     * Resolve a single entity with COW semantics.
     * Instance entry takes precedence; if it's a tombstone, returns null.
     *
     * @param instanceEntry entity from instance (may be null if not overridden)
     * @param baseEntry     entity from base world (may be null)
     * @return the resolved entity, or null if deleted via tombstone
     */
    public static <T extends CowEntity> T findOne(T instanceEntry, T baseEntry) {
        if (instanceEntry != null) {
            return instanceEntry.isCowEnabled() ? instanceEntry : null;
        }
        return baseEntry;
    }

}
