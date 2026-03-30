package de.mhus.nimbus.world.life.logic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for Logic Machine condition check.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogicConditionResult {

    private boolean result;
    private String error;

    public static LogicConditionResult of(boolean result) {
        return LogicConditionResult.builder().result(result).build();
    }

    public static LogicConditionResult error(String message) {
        return LogicConditionResult.builder().result(false).error(message).build();
    }
}
