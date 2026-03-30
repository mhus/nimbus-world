package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Logic Machine rule definition.
 * Rules are defined at the World level and evaluated against World-Instance state.
 *
 * When flags listed in {@link #affected} change, the {@link #spelCondition} is evaluated.
 * If true, the {@link #effects} are executed in order.
 */
@Document(collection = "w_logic_rules")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_affected_idx", def = "{ 'worldId': 1, 'affected': 1, 'enabled': 1 }"),
        @CompoundIndex(name = "world_priority_idx", def = "{ 'worldId': 1, 'priority': 1 }"),
        @CompoundIndex(name = "world_epoches_idx", def = "{ 'worldId': 1, 'epoches': 1, 'enabled': 1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WLogicRule implements Identifiable, EpochEntity {

    @Id
    private String id;

    @Indexed
    private String worldId;

    /**
     * Technical name, unique per world.
     */
    private String name;

    private String description;

    /**
     * Optional package name for organizing rules (e.g. "puzzle_door", "quest_forest").
     */
    @Indexed
    private String rulePackage;

    /**
     * Flag names this rule depends on. When any of these flags change,
     * the rule's spelCondition is evaluated.
     */
    private List<String> affected;

    /**
     * SpEL boolean expression evaluated against the current state.
     * Example: "state.hasKey == true && state.doorOpen == false"
     */
    private String spelCondition;

    /**
     * Ordered list of effects to execute when spelCondition is true.
     */
    private List<LogicEffect> effects;

    /**
     * Epoch assignment. Rule is only active in these epochs.
     * Empty list means not visible in any epoch.
     */
    private List<Integer> epoches;

    /**
     * Whether this rule is active. Disabled rules are skipped during evaluation.
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * Execution priority. Lower values execute first.
     */
    @Builder.Default
    private int priority = 100;

    /**
     * Test fixture: saved simulate flags JSON for the rule editor.
     * Stored as a raw JSON string, not interpreted by the Logic Machine.
     */
    private String testFlags;

    private Instant createdAt;
    private Instant updatedAt;
}
