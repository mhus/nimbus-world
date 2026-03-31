package de.mhus.nimbus.world.control.dialog;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.dialog.DialogDtos.*;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.world.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core dialog service: loads context, selects situations, evaluates nodes, advances dialog.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DialogService {

    private final WProgressService progressService;
    private final WAnythingService anythingService;
    private final WEntityService entityService;
    private final RCharacterService characterService;
    private final DialogConditionEvaluator conditionEvaluator;
    private final DialogEffectExecutor effectExecutor;
    private final DialogTextService dialogTextService;

    /**
     * Load the full dialog context from a progress entry.
     *
     * @throws DialogException if validation fails
     */
    public DialogContext loadDialogContext(String progressId, String worldId, String userId, String characterId) {
        // 1. Load and validate progress
        WProgress progress = progressService.findByProgressId(progressId)
                .orElseThrow(() -> new DialogException("Progress not found"));

        if (!worldId.equals(progress.getWorldId())) {
            throw new DialogException("Progress does not belong to this world");
        }
        if (!userId.equals(progress.getPlayerId())) {
            throw new DialogException("Progress does not belong to this player");
        }

        Map<String, Object> progressData = progress.getProgressData();
        if (progressData == null || !progressData.containsKey("playbook")) {
            throw new DialogException("Progress has no playbook reference");
        }

        // 2. Load playbook
        String playbookRef = String.valueOf(progressData.get("playbook"));
        if (!playbookRef.contains("/")) {
            throw new DialogException("Invalid playbook reference: " + playbookRef);
        }

        String[] parts = playbookRef.split("/", 2);
        String collection = parts[0];
        String playbookName = parts[1];

        WorldId parsedWorldId = WorldId.of(worldId).orElseThrow(() -> new DialogException("Invalid worldId"));
        String mainWorldId = parsedWorldId.isInstance() ? parsedWorldId.toMainWorld().getId() : worldId;

        WAnything playbookAnything = anythingService
                .findByWorldIdAndCollectionAndName(mainWorldId, collection, playbookName)
                .orElseThrow(() -> new DialogException("Playbook not found: " + playbookRef));

        Playbook playbook = playbookAnything.getDataAs(Playbook.class)
                .orElseThrow(() -> new DialogException("Failed to deserialize playbook"));

        // 3. Load NPC entity
        String entityId = playbook.npcEntityId();
        if (Strings.isBlank(entityId)) {
            // Fallback: entityId from progressData (set by DialogAction)
            entityId = String.valueOf(progressData.getOrDefault("entityId", ""));
        }

        WEntity npcEntity = null;
        String npcTitle = entityId;
        String npcPortrait = null;

        if (!Strings.isBlank(entityId)) {
            npcEntity = entityService.findByWorldIdAndEntityId(parsedWorldId, entityId).orElse(null);
            if (npcEntity != null) {
                npcPortrait = npcEntity.getPortraitPath();
                if (npcEntity.getPublicData() != null && npcEntity.getPublicData().getName() != null) {
                    npcTitle = npcEntity.getPublicData().getName();
                }
            }
        }
        // Override from progressData if available (set by DialogAction directly)
        if (progressData.containsKey("portraitPath")) {
            npcPortrait = String.valueOf(progressData.get("portraitPath"));
        }

        // 4. Load NPC profile
        NpcProfile npcProfile = null;
        if (npcEntity != null && npcEntity.getServer() != null) {
            String profileRef = npcEntity.getServer().get("profile");
            if (!Strings.isBlank(profileRef)) {
                npcProfile = anythingService
                        .findByWorldIdAndCollectionAndName(mainWorldId, "npc-profiles", profileRef)
                        .flatMap(a -> a.getDataAs(NpcProfile.class))
                        .orElse(null);
            }
        }

        // 5. Load NPC world-instance state
        String npcProgressPlayerId = "npc:" + entityId;
        WProgress npcStateProgress = progressService
                .findByWorldIdAndPlayerIdAndTypeAndQuest(worldId, npcProgressPlayerId, "npc-state", null)
                .orElse(null);
        Map<String, Object> npcState = npcStateProgress != null && npcStateProgress.getProgressData() != null
                ? new HashMap<>(npcStateProgress.getProgressData())
                : new HashMap<>();

        // 6. Load NPC-player memory
        WProgress playerMemoryProgress = progressService
                .findByWorldIdAndPlayerIdAndTypeAndQuest(worldId, userId, "npc-memory", entityId)
                .orElse(null);
        Map<String, Object> playerMemory = playerMemoryProgress != null && playerMemoryProgress.getProgressData() != null
                ? new HashMap<>(playerMemoryProgress.getProgressData())
                : new HashMap<>();

        // 7. Load character
        var character = characterService.getCharacter(userId, parsedWorldId.getRegionId(), characterId)
                .orElse(null);

        return DialogContext.builder()
                .dialogProgress(progress)
                .playbook(playbook)
                .playbookName(playbookName)
                .npcProfile(npcProfile)
                .npcEntity(npcEntity)
                .npcTitle(npcTitle)
                .npcPortrait(npcPortrait)
                .npcStateProgress(npcStateProgress)
                .npcState(npcState)
                .playerMemoryProgress(playerMemoryProgress)
                .playerMemory(playerMemory)
                .character(character)
                .worldId(worldId)
                .playerId(userId)
                .characterId(characterId)
                .currentNodeId(String.valueOf(progressData.getOrDefault("currentNode", "greeting")))
                .build();
    }

    /**
     * Select the active situation based on conditions and priority.
     * Handles onEnter/onExit transitions.
     */
    public void selectSituation(DialogContext ctx) {
        Playbook playbook = ctx.getPlaybook();
        if (playbook.situations() == null || playbook.situations().isEmpty()) {
            throw new DialogException("Playbook has no situations");
        }

        // Evaluate all situations
        record SituationEntry(String name, Situation situation) {}
        List<SituationEntry> active = new ArrayList<>();

        for (var entry : playbook.situations().entrySet()) {
            Situation sit = entry.getValue();
            if (conditionEvaluator.evaluateAll(sit.conditions(), ctx)) {
                active.add(new SituationEntry(entry.getKey(), sit));
            }
        }

        if (active.isEmpty()) {
            throw new DialogException("No active situation found (missing 'default'?)");
        }

        // Sort by priority descending
        active.sort(Comparator.comparingInt((SituationEntry e) -> e.situation().priority()).reversed());

        SituationEntry primary = active.getFirst();
        ctx.setActiveSituation(primary.situation());
        ctx.setActiveSituationName(primary.name());

        // Background situations (lower priority, for AI context)
        ctx.setBackgroundSituations(
                active.stream()
                        .skip(1)
                        .map(SituationEntry::situation)
                        .collect(Collectors.toList())
        );

        // Handle situation transition (onEnter/onExit)
        String lastSituation = ctx.getPlayerMemory() != null
                ? String.valueOf(ctx.getPlayerMemory().getOrDefault("lastSituation", ""))
                : "";

        if (!primary.name().equals(lastSituation)) {
            // onExit of old situation
            if (!Strings.isBlank(lastSituation) && playbook.situations().containsKey(lastSituation)) {
                Situation oldSit = playbook.situations().get(lastSituation);
                effectExecutor.executeAll(oldSit.onExit(), ctx);
            }
            // onEnter of new situation
            effectExecutor.executeAll(primary.situation().onEnter(), ctx);

            // Update memory with new situation
            ensurePlayerMemory(ctx);
            progressService.setProgressDataValue(
                    ctx.getPlayerMemoryProgress().getProgressId(), "lastSituation", primary.name());
            ctx.getPlayerMemory().put("lastSituation", primary.name());
        }

        // Store situation in dialog progress
        progressService.setProgressDataValue(
                ctx.getDialogProgress().getProgressId(), "situation", primary.name());

        log.debug("Selected situation '{}' (priority {}) for playbook {}",
                primary.name(), primary.situation().priority(), ctx.getPlaybookName());
    }

    /**
     * Evaluate a node: filter options by conditions, resolve text, build response.
     */
    public DialogNodeResponse evaluateNode(DialogContext ctx, String nodeId) {
        Situation situation = ctx.getActiveSituation();
        if (situation == null) {
            throw new DialogException("No active situation");
        }

        DialogNode node = situation.nodes().get(nodeId);
        if (node == null) {
            throw new DialogException("Node not found: " + nodeId + " in situation " + ctx.getActiveSituationName());
        }

        ctx.setCurrentNodeId(nodeId);

        // Filter options by conditions
        List<OptionView> visibleOptions = new ArrayList<>();
        List<DialogOption> allOptions = node.options();
        for (int i = 0; i < allOptions.size(); i++) {
            DialogOption opt = allOptions.get(i);
            if (conditionEvaluator.evaluateAll(opt.conditions(), ctx)) {
                visibleOptions.add(new OptionView(i, opt.text()));
            }
        }

        // Resolve text: AI generation with cache, fallback to textPrompt
        String language = "de"; // TODO: resolve from RUser settings
        String text = dialogTextService.resolveText(node, ctx, language);

        // Determine freeTextEnabled
        boolean freeTextEnabled = isFreeTextEnabled(ctx, node);

        boolean finished = visibleOptions.isEmpty() && !freeTextEnabled;

        return new DialogNodeResponse(
                ctx.getDialogProgress().getProgressId(),
                ctx.getNpcTitle(),
                ctx.getNpcPortrait(),
                text,
                visibleOptions,
                freeTextEnabled,
                finished
        );
    }

    /**
     * Advance dialog by selecting an option.
     */
    public DialogNodeResponse advanceDialog(DialogContext ctx, int optionIndex) {
        Situation situation = ctx.getActiveSituation();
        String currentNodeId = ctx.getCurrentNodeId();
        DialogNode currentNode = situation.nodes().get(currentNodeId);

        if (currentNode == null) {
            throw new DialogException("Current node not found: " + currentNodeId);
        }

        // Build visible options (same filtering as evaluateNode)
        List<DialogOption> visibleOptions = new ArrayList<>();
        for (DialogOption opt : currentNode.options()) {
            if (conditionEvaluator.evaluateAll(opt.conditions(), ctx)) {
                visibleOptions.add(opt);
            }
        }

        if (optionIndex < 0 || optionIndex >= visibleOptions.size()) {
            throw new DialogException("Invalid option index: " + optionIndex);
        }

        DialogOption selected = visibleOptions.get(optionIndex);
        String nextNodeId = selected.next();

        // null next = close dialog
        if (nextNodeId == null) {
            closeDialog(ctx);
            return new DialogNodeResponse(
                    ctx.getDialogProgress().getProgressId(),
                    ctx.getNpcTitle(),
                    ctx.getNpcPortrait(),
                    null,
                    List.of(),
                    false,
                    true
            );
        }

        // Get target node and execute its effects
        DialogNode targetNode = situation.nodes().get(nextNodeId);
        if (targetNode == null) {
            throw new DialogException("Target node not found: " + nextNodeId);
        }

        effectExecutor.executeAll(targetNode.effects(), ctx);

        // Update progress
        progressService.setProgressDataValue(
                ctx.getDialogProgress().getProgressId(), "currentNode", nextNodeId);

        return evaluateNode(ctx, nextNodeId);
    }

    /**
     * Close the dialog and update memory.
     */
    public void closeDialog(DialogContext ctx) {
        ensurePlayerMemory(ctx);
        WProgress memory = ctx.getPlayerMemoryProgress();

        // Increment conversation count
        int count = ctx.getConversationCount() + 1;
        progressService.setProgressDataValues(memory.getProgressId(), Map.of(
                "conversationCount", count,
                "lastVisit", Instant.now().toString()
        ));

        log.debug("Closed dialog for playbook {}, conversation #{}", ctx.getPlaybookName(), count);
    }

    /**
     * Get the current node ID from dialog progress, or "greeting" as default.
     */
    public String getCurrentNodeId(DialogContext ctx) {
        Map<String, Object> data = ctx.getDialogProgress().getProgressData();
        if (data != null && data.containsKey("currentNode")) {
            return String.valueOf(data.get("currentNode"));
        }
        return "greeting";
    }

    /**
     * Check if the dialog is being continued (has a stored situation).
     */
    public boolean isContinuing(DialogContext ctx) {
        Map<String, Object> data = ctx.getDialogProgress().getProgressData();
        return data != null && data.containsKey("situation");
    }

    /**
     * Restore the active situation from stored state (for continuing dialogs).
     */
    public void restoreSituation(DialogContext ctx) {
        Map<String, Object> data = ctx.getDialogProgress().getProgressData();
        String situationName = String.valueOf(data.get("situation"));

        Situation situation = ctx.getPlaybook().situations().get(situationName);
        if (situation == null) {
            // Situation no longer valid, re-select
            selectSituation(ctx);
            return;
        }

        ctx.setActiveSituation(situation);
        ctx.setActiveSituationName(situationName);
    }

    // --- Private helpers ---

    private boolean isFreeTextEnabled(DialogContext ctx, DialogNode node) {
        // Node level override
        if (node.freeTextAllowed() != null) {
            return node.freeTextAllowed();
        }
        // NPC profile level
        if (ctx.getNpcProfile() != null && ctx.getNpcProfile().freeText() != null) {
            Boolean enabled = ctx.getNpcProfile().freeText().enabled();
            if (enabled != null) return enabled;
        }
        // World config level (would need to load dialog-settings from WAnything)
        // For now default to false
        return false;
    }

    private void ensurePlayerMemory(DialogContext ctx) {
        if (ctx.getPlayerMemoryProgress() != null) return;

        String entityId = ctx.getNpcEntity() != null ? ctx.getNpcEntity().getEntityId() : "unknown";
        var saved = progressService.save(
                ctx.getWorldId(), ctx.getPlayerId(), "npc-memory", entityId,
                new HashMap<>(Map.of("conversationCount", 0))
        );
        ctx.setPlayerMemoryProgress(saved);
        ctx.setPlayerMemory(new HashMap<>(saved.getProgressData()));
    }

    /**
     * Exception type for dialog-specific errors.
     */
    public static class DialogException extends RuntimeException {
        public DialogException(String message) {
            super(message);
        }
    }
}
