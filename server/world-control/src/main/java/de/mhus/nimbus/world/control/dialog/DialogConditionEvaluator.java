package de.mhus.nimbus.world.control.dialog;

import de.mhus.nimbus.world.control.dialog.DialogDtos.Condition;
import de.mhus.nimbus.world.shared.world.LogicConditionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Evaluates dialog conditions against the current dialog context.
 * All conditions within a list are AND-combined.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DialogConditionEvaluator {

    private static final String LOGIC_PACKAGE = "dialog";

    private final LogicConditionService logicConditionService;

    /**
     * Evaluate all conditions (AND). Empty list = true.
     */
    public boolean evaluateAll(List<Condition> conditions, DialogContext ctx) {
        if (conditions == null || conditions.isEmpty()) return true;
        for (Condition condition : conditions) {
            if (!evaluate(condition, ctx)) return false;
        }
        return true;
    }

    /**
     * Evaluate a single condition against the context.
     */
    public boolean evaluate(Condition condition, DialogContext ctx) {
        boolean result = switch (condition.type()) {
            case "logic" -> evaluateLogic(condition, ctx);
            case "skill" -> evaluateSkill(condition, ctx);
            case "reputation" -> evaluateReputation(condition, ctx);
            case "item" -> evaluateItem(condition, ctx);
            case "npcState" -> evaluateNpcState(condition, ctx);
            case "npcFact" -> evaluateNpcFact(condition, ctx);
            case "memory" -> evaluateMemory(condition, ctx);
            case "conversationCount" -> evaluateConversationCount(condition, ctx);
            case "progress" -> evaluateProgress(condition, ctx);
            default -> {
                log.warn("Unknown condition type: {}", condition.type());
                yield false;
            }
        };

        if (Boolean.TRUE.equals(condition.negate())) {
            result = !result;
        }
        return result;
    }

    private boolean evaluateLogic(Condition condition, DialogContext ctx) {
        String flag = condition.flag();
        if (flag == null || flag.isBlank()) return true;

        boolean negated = flag.startsWith("!");
        String cleanFlag = negated ? flag.substring(1) : flag;

        String spel = "state." + LOGIC_PACKAGE + "." + cleanFlag + " == true";
        boolean result = logicConditionService.checkCondition(ctx.getWorldId(), spel);

        return negated ? !result : result;
    }

    private boolean evaluateSkill(Condition condition, DialogContext ctx) {
        if (ctx.getCharacter() == null || ctx.getCharacter().getSkills() == null) return false;
        int level = ctx.getCharacter().getSkills().getOrDefault(condition.skill(), 0);
        return condition.minLevel() == null || level >= condition.minLevel();
    }

    private boolean evaluateReputation(Condition condition, DialogContext ctx) {
        if (ctx.getCharacter() == null || ctx.getCharacter().getReputation() == null) return false;
        int rep = ctx.getCharacter().getReputation().getOrDefault(condition.faction(), 0);
        return condition.minValue() == null || rep >= condition.minValue();
    }

    private boolean evaluateItem(Condition condition, DialogContext ctx) {
        if (ctx.getCharacter() == null || ctx.getCharacter().getBackpack() == null) return false;
        Map<String, Integer> items = ctx.getCharacter().getBackpack().getItemIds();
        if (items == null) return false;
        int have = items.getOrDefault(condition.itemName(), 0);
        int need = condition.count() != null ? condition.count() : 1;
        return have >= need;
    }

    private boolean evaluateNpcState(Condition condition, DialogContext ctx) {
        Object value = ctx.getNpcStateValue(condition.key());
        return matchValue(value, condition.equals(), condition.min(), condition.max());
    }

    private boolean evaluateNpcFact(Condition condition, DialogContext ctx) {
        List<String> facts = ctx.getNpcKnownFacts();
        return condition.contains() != null && facts.contains(condition.contains());
    }

    private boolean evaluateMemory(Condition condition, DialogContext ctx) {
        Object value = ctx.getMemoryValue(condition.key());
        return matchValue(value, condition.equals(), condition.min(), condition.max());
    }

    private boolean evaluateConversationCount(Condition condition, DialogContext ctx) {
        int count = ctx.getConversationCount();
        if (condition.min() != null && count < toInt(condition.min())) return false;
        if (condition.max() != null && count > toInt(condition.max())) return false;
        return true;
    }

    private boolean evaluateProgress(Condition condition, DialogContext ctx) {
        if (ctx.getDialogLease() == null || ctx.getDialogLease().getLeaseData() == null) return false;
        Object value = ctx.getDialogLease().getLeaseData().get(condition.key());
        return matchValue(value, condition.equals(), condition.min(), condition.max());
    }

    // --- Helpers ---

    private boolean matchValue(Object value, Object expectedEquals, Object expectedMin, Object expectedMax) {
        if (expectedEquals != null) {
            if (value == null) return false;
            return value.toString().equals(expectedEquals.toString());
        }
        if (expectedMin != null || expectedMax != null) {
            double numValue = toDouble(value);
            if (expectedMin != null && numValue < toDouble(expectedMin)) return false;
            if (expectedMax != null && numValue > toDouble(expectedMax)) return false;
        }
        // No constraints = value just needs to exist (non-null)
        if (expectedEquals == null && expectedMin == null && expectedMax == null) {
            return value != null;
        }
        return true;
    }

    private int toInt(Object obj) {
        if (obj instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
