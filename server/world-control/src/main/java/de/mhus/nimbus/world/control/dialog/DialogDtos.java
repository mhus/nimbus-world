package de.mhus.nimbus.world.control.dialog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * DTOs for the NPC dialog system.
 * Used for JSON deserialization from WAnything.data and REST API responses.
 */
public final class DialogDtos {

    private DialogDtos() {}

    // --- Playbook structure (stored in WAnything collection="dialogs") ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Playbook(
            String npcEntityId,
            int version,
            Map<String, Situation> situations
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Situation(
            List<Condition> conditions,
            int priority,
            String aiContext,
            List<String> availableTopics,
            String moodOverride,
            Map<String, DialogNode> nodes,
            List<Effect> onEnter,
            List<Effect> onExit
    ) {
        public Situation {
            if (conditions == null) conditions = List.of();
            if (availableTopics == null) availableTopics = List.of();
            if (nodes == null) nodes = Map.of();
            if (onEnter == null) onEnter = List.of();
            if (onExit == null) onExit = List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DialogNode(
            String textPrompt,
            List<String> cacheKeys,
            Boolean freeTextAllowed,
            List<DialogOption> options,
            List<Effect> effects,
            List<Condition> conditions
    ) {
        public DialogNode {
            if (cacheKeys == null) cacheKeys = List.of();
            if (options == null) options = List.of();
            if (effects == null) effects = List.of();
            if (conditions == null) conditions = List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DialogOption(
            String text,
            String textKey,
            String next,
            String intent,
            List<Condition> conditions
    ) {
        public DialogOption {
            if (conditions == null) conditions = List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Condition(
            String type,
            // logic
            String flag,
            // skill
            String skill,
            Integer minLevel,
            // reputation
            String faction,
            Integer minValue,
            // item
            String itemName,
            Integer count,
            // npcState, memory, progress
            String key,
            Object equals,
            Object min,
            Object max,
            // npcFact
            String contains,
            // general
            Boolean negate
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Effect(
            String type,
            // setLogic
            String flag,
            Object value,
            // giveItem, takeItem
            String item,
            Integer count,
            // addReputation
            String faction,
            Integer delta,
            // addSkillXP
            String skill,
            Integer amount,
            // setNpcState, setMemory, setProgress
            String key,
            // addMemory, addNpcFact
            String text,
            String fact,
            // triggerScrawl
            String script,
            String sequence
    ) {}

    // --- NPC Profile (stored in WAnything collection="npc-profiles") ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NpcProfile(
            String personality,
            String background,
            String motivations,
            List<String> secrets,
            String speechStyle,
            String faction,
            List<String> knowledgeTopics,
            String aiModel,
            CacheConfig cacheConfig,
            FreeTextConfig freeText
    ) {
        public NpcProfile {
            if (secrets == null) secrets = List.of();
            if (knowledgeTopics == null) knowledgeTopics = List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CacheConfig(
            int maxVersions,
            int warmUpCount,
            Map<String, Object> buckets
    ) {
        public CacheConfig {
            if (maxVersions <= 0) maxVersions = 10;
            if (warmUpCount <= 0) warmUpCount = 3;
            if (buckets == null) buckets = Map.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FreeTextConfig(
            Boolean enabled,
            String aiModel,
            Integer maxTokens,
            List<String> boundaries,
            List<String> forbiddenTopics,
            List<String> allowedEffects
    ) {
        public FreeTextConfig {
            if (boundaries == null) boundaries = List.of();
            if (forbiddenTopics == null) forbiddenTopics = List.of();
            if (allowedEffects == null) allowedEffects = List.of("addMemory", "setMemory");
        }
    }

    // --- Dialog config (stored in WAnything collection="config", name="dialog-settings") ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DialogSettings(
            boolean freeTextEnabled,
            String freeTextAiModel,
            int freeTextMaxTokens,
            int freeTextMaxHistory,
            RateLimitConfig freeTextRateLimit
    ) {
        public DialogSettings {
            if (freeTextMaxTokens <= 0) freeTextMaxTokens = 200;
            if (freeTextMaxHistory <= 0) freeTextMaxHistory = 10;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RateLimitConfig(
            int maxRequestsPerMinute,
            int maxRequestsPerDialog
    ) {
        public RateLimitConfig {
            if (maxRequestsPerMinute <= 0) maxRequestsPerMinute = 10;
            if (maxRequestsPerDialog <= 0) maxRequestsPerDialog = 50;
        }
    }

    // --- REST API DTOs ---

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DialogNodeResponse(
            String progressId,
            String npcTitle,
            String npcPortrait,
            String text,
            List<OptionView> options,
            boolean freeTextEnabled,
            boolean finished
    ) {}

    public record OptionView(
            int index,
            String text
    ) {}

    public record DialogRequest(
            String progressId,
            Integer optionIndex,
            String freeText
    ) {}
}
