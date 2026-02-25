package de.mhus.nimbus.world.generator.fauna;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Definition of a single animal type within a fauna type.
 * Loaded from WAnything collection "fauna".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FaunaAnimalDefinition {

    /** Technical name, e.g. "cow", "sheep" */
    private String name;

    /** WEntityModel modelId reference */
    private String model;

    /** Total amount range */
    @Builder.Default
    private int amountMin = 1;
    @Builder.Default
    private int amountMax = 1;

    /** Number of groups to distribute animals into */
    @Builder.Default
    private int groupsMin = 1;
    @Builder.Default
    private int groupsMax = 1;

    /** Category flags - which terrain categories this animal can spawn on */
    @Builder.Default
    private boolean land = false;
    @Builder.Default
    private boolean water = false;
    @Builder.Default
    private boolean sea = false;
    @Builder.Default
    private boolean aerial = false;

    /** Roaming radius around middle point (blocks) */
    @Builder.Default
    private double radius = 30.0;

    /** Movement speed (blocks per second) */
    @Builder.Default
    private double speed = 1.5;

    /** Height offset above ground level (mainly for aerial) */
    @Builder.Default
    private int height = 0;

    /** Behavior model identifier, e.g. "PreyAnimalBehavior" */
    private String behaviorModel;

    /** Optional behavior-specific configuration */
    private Map<String, Object> behaviorConfig;

    /** SpEL condition expression evaluated against generation context (e.g. "random > 0.5") */
    private String when;

    /** Allowed genders for this animal type. Default: all (M, W, D). */
    @Builder.Default
    private List<FaunaGender> genders = List.of(FaunaGender.M, FaunaGender.W, FaunaGender.D);

    /** Group formation type. Default: MIXED. */
    @Builder.Default
    private FaunaGroupType groupType = FaunaGroupType.MIXED;

    /** Gender distribution weight for male (default 45). */
    @Builder.Default
    private double weightM = 45;

    /** Gender distribution weight for female (default 45). */
    @Builder.Default
    private double weightW = 45;

    /** Gender distribution weight for diverse (default 10). */
    @Builder.Default
    private double weightD = 10;

    /**
     * Check if this animal can spawn at the given category.
     */
    public boolean fitsCategory(FaunaCategory category) {
        return switch (category) {
            case LAND -> land || aerial;
            case WATER -> water;
            case SEA -> sea;
            case AERIAL -> aerial;
        };
    }
}
