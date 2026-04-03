package de.mhus.nimbus.world.generator.npc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.Entity;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.AiModelService;
import de.mhus.nimbus.world.shared.world.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Generates NPCs with Entity, NPC-Profile, and Dialog-Playbook using AI.
 * Loads world lore from WDocuments to provide context for generation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NpcGeneratorService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_AI_MODEL = "default:dialog";

    private final WEntityService entityService;
    private final WAnythingService anythingService;
    private final WDocumentService documentService;
    private final AiModelService aiModelService;

    /**
     * Generate a complete NPC: WEntity + NPC-Profile + Dialog-Playbook.
     *
     * @return Map with created resource IDs
     */
    public Map<String, Object> generateNpc(NpcGenerationRequest request) {
        log.info("Generating NPC '{}' in world {}", request.entityId(), request.worldId());

        WorldId worldId = WorldId.of(request.worldId())
                .orElseThrow(() -> new NpcGenerationException("Invalid worldId: " + request.worldId()));
        String mainWorldId = worldId.isInstance() ? worldId.toMainWorld().getId() : worldId.getId();

        // 1. Load NPC description from lore document (if npcDocumentName given)
        String npcDocumentName = request.loreContext() != null
                ? request.loreContext().stream().filter(n -> n.startsWith("npc:")).findFirst().orElse(null)
                : null;
        NpcDescriptionFromLore npcDesc = loadNpcDescription(worldId, npcDocumentName, request);

        // Build effective request with lore-overridden fields
        NpcGenerationRequest effectiveRequest = new NpcGenerationRequest(
                request.worldId(), request.entityId(), request.modelId(), request.gender(),
                request.posX(), request.posY(), request.posZ(),
                npcDesc.environment() != null ? npcDesc.environment() : request.environment(),
                npcDesc.characterDescription() != null ? npcDesc.characterDescription() : request.characterDescription(),
                npcDesc.characterBackground() != null ? npcDesc.characterBackground() : request.characterBackground(),
                request.portraitPath(), request.aiModel(), request.epoches(),
                request.schedule(), request.loreContext()
        );

        // 2. Load general lore context
        String loreContext = loadLoreContext(worldId, request.loreContext());

        // 3. Generate NPC data via AI
        String aiModel = request.aiModel() != null ? request.aiModel() : DEFAULT_AI_MODEL;
        AiGeneratedNpc generated = generateViaAi(effectiveRequest, loreContext, aiModel);

        // 3. Create WEntity
        createEntity(worldId, request, generated);

        // 4. Create NPC Profile (WAnything npc-profiles)
        createProfile(mainWorldId, request.entityId(), generated);

        // 5. Create Dialog Playbook (WAnything dialogs)
        createPlaybook(mainWorldId, request.entityId(), generated);

        log.info("NPC '{}' generated successfully", request.entityId());

        return Map.of(
                "entityId", request.entityId(),
                "worldId", request.worldId(),
                "profileCollection", "npc-profiles",
                "profileName", request.entityId(),
                "playbookCollection", "dialogs",
                "playbookName", request.entityId(),
                "npcTitle", generated.title != null ? generated.title : request.entityId(),
                "status", "created"
        );
    }

    // --- Lore loading ---

    /**
     * Load lore context from WDocument collection="lore".
     * Document names follow the convention:
     * - "lore:*" — general world/region/area lore
     * - "npc:*"  — NPC-specific character descriptions
     *
     * The npcDocumentName (e.g. "npc:farmer_hans") is the primary NPC description.
     * Additional loreContext names provide background (e.g. "lore:farmland_region").
     *
     * All "lore:world" and "lore:region" prefixed documents are auto-loaded as base context.
     */
    private String loadLoreContext(WorldId worldId, List<String> loreNames) {
        var sb = new StringBuilder();
        String npcDescription = null;

        // Auto-load all lore: documents for general context
        try {
            var allLoreDocs = documentService.findByCollection(worldId, "lore");
            for (var doc : allLoreDocs) {
                String name = doc.getName();
                if (name == null) continue;

                // Auto-load world and region lore as base context
                if (name.startsWith("lore:world") || name.startsWith("lore:region")) {
                    sb.append("## ").append(doc.getTitle() != null ? doc.getTitle() : name).append("\n");
                    sb.append(doc.getSummary() != null ? doc.getSummary() : truncate(doc.getContent(), 500)).append("\n\n");
                }
            }
        } catch (Exception e) {
            log.debug("Failed to load lore documents: {}", e.getMessage());
        }

        // Load explicitly referenced documents (lore context + NPC description)
        if (loreNames != null) {
            for (String loreName : loreNames) {
                try {
                    documentService.findByName(worldId, "lore", loreName).ifPresent(doc -> {
                        String header = loreName.startsWith("npc:") ? "NPC-Beschreibung" : "Kontext";
                        sb.append("## ").append(header).append(": ")
                                .append(doc.getTitle() != null ? doc.getTitle() : loreName).append("\n");
                        sb.append(doc.getContent() != null ? doc.getContent() : "").append("\n\n");
                    });
                } catch (Exception e) {
                    log.warn("Lore document '{}' not found in world {}", loreName, worldId);
                }
            }
        }

        return sb.toString();
    }

    /**
     * Extract NPC description from a npc: lore document.
     * Falls back to request parameters if no document found.
     */
    private NpcDescriptionFromLore loadNpcDescription(WorldId worldId, String npcDocumentName,
                                                        NpcGenerationRequest request) {
        String environment = request.environment();
        String characterDescription = request.characterDescription();
        String characterBackground = request.characterBackground();

        if (npcDocumentName != null && !npcDocumentName.isBlank()) {
            try {
                var docOpt = documentService.findByName(worldId, "lore", npcDocumentName);
                if (docOpt.isPresent()) {
                    var doc = docOpt.get();
                    // The document content IS the full NPC description
                    // Override request fields with document content
                    if (doc.getContent() != null && !doc.getContent().isBlank()) {
                        characterDescription = doc.getContent();
                    }
                    if (doc.getSummary() != null && !doc.getSummary().isBlank()) {
                        characterBackground = doc.getSummary();
                    }
                    // Environment from metadata if available
                    if (doc.getMetadata() != null && doc.getMetadata().containsKey("environment")) {
                        environment = doc.getMetadata().get("environment");
                    }
                    log.debug("Loaded NPC description from lore document: {}", npcDocumentName);
                } else {
                    log.warn("NPC lore document '{}' not found, using request parameters", npcDocumentName);
                }
            } catch (Exception e) {
                log.warn("Failed to load NPC lore document '{}': {}", npcDocumentName, e.getMessage());
            }
        }

        return new NpcDescriptionFromLore(environment, characterDescription, characterBackground);
    }

    record NpcDescriptionFromLore(String environment, String characterDescription, String characterBackground) {}

    // --- AI generation ---

    private AiGeneratedNpc generateViaAi(NpcGenerationRequest request, String loreContext, String aiModel) {
        String systemPrompt = buildSystemPrompt(loreContext);
        String userPrompt = buildUserPrompt(request);

        Optional<AiChat> chatOpt = aiModelService.createChat(aiModel, AiChatOptions.builder()
                .systemMessage(systemPrompt)
                .temperature(0.8)
                .maxTokens(2000)
                .timeoutSeconds(60)
                .build());

        if (chatOpt.isEmpty()) {
            throw new NpcGenerationException("AI model not available: " + aiModel);
        }

        try {
            String response = chatOpt.get().ask(userPrompt);
            return parseAiResponse(response);
        } catch (Exception e) {
            throw new NpcGenerationException("AI generation failed: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt(String loreContext) {
        var sb = new StringBuilder();
        sb.append("""
                Du bist ein Game Designer der NPCs fuer eine Fantasy-Welt erstellt.
                Du generierst vollstaendige NPC-Daten im JSON-Format.

                """);

        if (!loreContext.isBlank()) {
            sb.append("# Welt-Kontext (Lore)\n\n");
            sb.append(loreContext);
            sb.append("\nDer NPC MUSS konsistent zu dieser Lore sein.\n\n");
        }

        sb.append("""
                # Ausgabe-Format

                Antworte NUR mit einem JSON-Objekt (keine Markdown-Codeblocks):
                {
                  "title": "Anzeigename des NPC",
                  "personality": "Persoenlichkeits-Beschreibung fuer AI-Textgenerierung",
                  "background": "Ausfuehrlicher Hintergrund",
                  "motivations": "Was treibt den NPC an",
                  "secrets": ["Geheimnis 1", "Geheimnis 2"],
                  "speechStyle": "Beschreibung der Sprechweise",
                  "faction": "Fraktion/Gruppe oder null",
                  "knowledgeTopics": ["thema1", "thema2", "thema3"],
                  "defaultGreeting": "Anweisung fuer AI wie der NPC den Spieler begruesst",
                  "smalltalkTopics": [
                    {
                      "id": "topic_id",
                      "label": "Angezeigter Text fuer die Option",
                      "prompt": "Anweisung fuer AI was der NPC zu diesem Thema sagt"
                    }
                  ]
                }

                Erstelle 3-5 smalltalkTopics passend zum Charakter und seiner Umgebung.
                Alle Texte auf Deutsch.
                """);

        return sb.toString();
    }

    private String buildUserPrompt(NpcGenerationRequest request) {
        var sb = new StringBuilder();
        sb.append("Erstelle einen NPC mit folgenden Vorgaben:\n\n");

        if (request.environment() != null) {
            sb.append("Umgebung: ").append(request.environment()).append("\n");
        }
        if (request.characterDescription() != null) {
            sb.append("Charakter: ").append(request.characterDescription()).append("\n");
        }
        if (request.characterBackground() != null) {
            sb.append("Hintergrund: ").append(request.characterBackground()).append("\n");
        }
        if (request.entityId() != null) {
            sb.append("Entity-ID: ").append(request.entityId()).append("\n");
        }

        return sb.toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiGeneratedNpc(
            String title,
            String personality,
            String background,
            String motivations,
            List<String> secrets,
            String speechStyle,
            String faction,
            List<String> knowledgeTopics,
            String defaultGreeting,
            List<SmallTalkTopic> smalltalkTopics
    ) {
        AiGeneratedNpc {
            if (secrets == null) secrets = List.of();
            if (knowledgeTopics == null) knowledgeTopics = List.of();
            if (smalltalkTopics == null) smalltalkTopics = List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SmallTalkTopic(String id, String label, String prompt) {}

    private AiGeneratedNpc parseAiResponse(String response) {
        try {
            String json = response.trim();
            // Strip markdown code blocks if present
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
            }
            return MAPPER.readValue(json, AiGeneratedNpc.class);
        } catch (Exception e) {
            throw new NpcGenerationException("Failed to parse AI response: " + e.getMessage()
                    + "\nResponse: " + truncate(response, 500), e);
        }
    }

    // --- Entity creation ---

    private void createEntity(WorldId worldId, NpcGenerationRequest request, AiGeneratedNpc generated) {
        String entityId = request.entityId();
        String modelId = request.modelId() != null ? request.modelId() : "human_male_1";

        Entity publicData = Entity.builder()
                .name(entityId)
                .title(generated.title != null ? generated.title : entityId)
                .gender(request.gender() != null ? request.gender() : "D")
                .model(modelId)
                .movementType(null)
                .controlledBy(null)
                .solid(true)
                .interactive(true)
                .healthMax(100)
                .build();

        entityService.save(worldId, entityId, publicData, modelId);

        entityService.update(worldId, entityId, entity -> {
            // Position
            float x = request.posX() != null ? request.posX().floatValue() : 0;
            float y = request.posY() != null ? request.posY().floatValue() : 0;
            float z = request.posZ() != null ? request.posZ().floatValue() : 0;
            entity.setPosition(TypeUtil.vector3(x, y, z));
            entity.setMiddlePoint(TypeUtil.vector3(x, y, z));

            // NPC type
            entity.setType(WEntityType.NPC);
            entity.setSource("npc-generator");
            entity.setSpeed(1.0);

            // Behavior
            if (request.schedule() != null && !request.schedule().isEmpty()) {
                entity.setBehaviorModel("ScheduledBehavior");
                entity.setSchedule(buildSchedule(request.schedule()));
            } else {
                entity.setBehaviorModel("PreyAnimalBehavior");
            }

            // Server params for dialog
            Map<String, String> server = new HashMap<>();
            server.put("action", "dialog");
            server.put("int_playbook", "dialogs/" + entityId);
            server.put("profile", entityId);
            server.put("roam_radius", "5");
            entity.setServer(server);

            // Portrait
            if (request.portraitPath() != null) {
                entity.setPortraitPath(request.portraitPath());
            }

            // Epoches
            if (request.epoches() != null) {
                entity.setEpoches(new ArrayList<>(request.epoches()));
            } else {
                entity.setEpoches(List.of(0));
            }
        });
    }

    private List<EntitySchedulePhase> buildSchedule(List<ScheduleEntry> entries) {
        return entries.stream().map(e -> EntitySchedulePhase.builder()
                .name(e.name())
                .fromHour(e.fromHour())
                .toHour(e.toHour())
                .present(e.present() == null || e.present())
                .point(e.point())
                .behavior(e.behavior())
                .roamRadius(e.roamRadius())
                .speed(e.speed())
                .build()
        ).toList();
    }

    // --- Profile creation ---

    private void createProfile(String mainWorldId, String entityId, AiGeneratedNpc generated) {
        Map<String, Object> profileData = new LinkedHashMap<>();
        profileData.put("personality", generated.personality());
        profileData.put("background", generated.background());
        profileData.put("motivations", generated.motivations());
        profileData.put("secrets", generated.secrets());
        profileData.put("speechStyle", generated.speechStyle());
        profileData.put("faction", generated.faction());
        profileData.put("knowledgeTopics", generated.knowledgeTopics());
        profileData.put("cacheConfig", Map.of(
                "maxVersions", 10,
                "warmUpCount", 3,
                "buckets", Map.of(
                        "memory_conversationCount", Map.of("first", List.of(0, 0), "few", List.of(1, 3), "many", List.of(4, 1000))
                )
        ));
        profileData.put("freeText", Map.of(
                "enabled", true,
                "maxTokens", 300,
                "boundaries", List.of(),
                "forbiddenTopics", List.of("real world", "game mechanics"),
                "allowedEffects", List.of("addMemory", "setMemory")
        ));

        anythingService.create(mainWorldId, "npc-profiles", entityId,
                generated.title(), "Generated NPC profile", "npc-profile", profileData);

        log.debug("Created NPC profile: npc-profiles/{}", entityId);
    }

    // --- Playbook creation ---

    private void createPlaybook(String mainWorldId, String entityId, AiGeneratedNpc generated) {
        // Build smalltalk nodes from AI-generated topics
        Map<String, Object> nodes = new LinkedHashMap<>();

        // Greeting node
        List<Map<String, Object>> greetingOptions = new ArrayList<>();
        for (var topic : generated.smalltalkTopics()) {
            greetingOptions.add(Map.of(
                    "text", topic.label(),
                    "next", "topic_" + topic.id(),
                    "intent", topic.id()
            ));
        }
        greetingOptions.add(Map.of(
                "text", "Auf Wiedersehen.",
                "next", "",  // null in JSON would be better, using empty for Jackson compat
                "intent", "goodbye"
        ));
        // Fix: null next for goodbye
        greetingOptions.set(greetingOptions.size() - 1, new LinkedHashMap<>(Map.of(
                "text", "Auf Wiedersehen.",
                "intent", "goodbye"
        )));

        nodes.put("greeting", Map.of(
                "textPrompt", generated.defaultGreeting() != null
                        ? generated.defaultGreeting()
                        : "Begruesse den Spieler. Beim ersten Besuch sei neugierig. Bei wiederholtem Besuch erkenne ihn wieder und sei vertrauter. Bei haeufigem Besuch behandle ihn wie einen alten Bekannten.",
                "cacheKeys", List.of("memory_conversationCount"),
                "freeTextAllowed", true,
                "options", greetingOptions,
                "effects", List.of(),
                "conditions", List.of()
        ));

        // Topic nodes — each topic shows all options so the player can navigate freely
        for (var topic : generated.smalltalkTopics()) {
            // Reuse the same options as greeting (all topics + goodbye)
            nodes.put("topic_" + topic.id(), Map.of(
                    "textPrompt", topic.prompt(),
                    "cacheKeys", List.of(),
                    "options", greetingOptions,
                    "effects", List.of(),
                    "conditions", List.of()
            ));
        }

        // Build playbook with default situation
        Map<String, Object> defaultSituation = new LinkedHashMap<>();
        defaultSituation.put("conditions", List.of());
        defaultSituation.put("priority", 0);
        defaultSituation.put("aiContext", "Du bist " + (generated.title() != null ? generated.title() : entityId) + ". "
                + (generated.personality() != null ? generated.personality() : "Ein freundlicher NPC."));
        defaultSituation.put("availableTopics", generated.knowledgeTopics());
        defaultSituation.put("nodes", nodes);
        defaultSituation.put("onEnter", List.of());
        defaultSituation.put("onExit", List.of());

        Map<String, Object> playbookData = new LinkedHashMap<>();
        playbookData.put("npcEntityId", entityId);
        playbookData.put("version", 1);
        playbookData.put("situations", Map.of("default", defaultSituation));

        anythingService.create(mainWorldId, "dialogs", entityId,
                generated.title(), "Generated NPC dialog", "npc-dialog", playbookData);

        log.debug("Created NPC playbook: dialogs/{}", entityId);
    }

    // --- Helpers ---

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    // --- Request/response records ---

    public record NpcGenerationRequest(
            String worldId,
            String entityId,
            String modelId,
            String gender,
            Double posX,
            Double posY,
            Double posZ,
            String environment,
            String characterDescription,
            String characterBackground,
            String portraitPath,
            String aiModel,
            List<Integer> epoches,
            List<ScheduleEntry> schedule,
            List<String> loreContext
    ) {}

    public record ScheduleEntry(
            String name,
            int fromHour,
            int toHour,
            Boolean present,
            String point,
            String behavior,
            Double roamRadius,
            Double speed
    ) {}

    public static class NpcGenerationException extends RuntimeException {
        public NpcGenerationException(String message) { super(message); }
        public NpcGenerationException(String message, Throwable cause) { super(message, cause); }
    }
}
