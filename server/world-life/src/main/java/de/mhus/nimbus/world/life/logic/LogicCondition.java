package de.mhus.nimbus.world.life.logic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Logic Machine condition check.
 * Read-only SpEL boolean evaluation, no state changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogicCondition {

    private String worldId;

    /**
     * SpEL boolean expression to evaluate.
     * Example: "flags.hasKey == true && flags.doorOpen"
     */
    private String spelExpression;
}
