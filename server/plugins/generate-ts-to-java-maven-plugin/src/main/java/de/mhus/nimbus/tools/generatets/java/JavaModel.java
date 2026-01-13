package de.mhus.nimbus.tools.generatets.java;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Java-side model created from the parsed TS model.
 * Step 1: buildItems
 * Step 2: link references between items
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JavaModel {

    private final List<JavaType> types = new ArrayList<>();

    // Convenience index, not serialized necessarily
    private transient Map<String, JavaType> byName;

    public List<JavaType> getTypes() {
        return types;
    }

    public void addType(JavaType t) {
        types.add(t);
        if (byName != null) byName.put(t.getName(), t);
    }

    public Map<String, JavaType> getIndexByName() {
        if (byName == null) {
            byName = new HashMap<>();
            for (JavaType t : types) {
                if (t.getName() != null) byName.put(t.getName(), t);
            }
        }
        return byName;
    }
}
