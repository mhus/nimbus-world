package de.mhus.nimbus.world.control.chat;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.chat.WChat;
import de.mhus.nimbus.world.shared.chat.WChatAgent;
import de.mhus.nimbus.world.shared.chat.WChatMessage;
import de.mhus.nimbus.world.shared.chat.WChatService;
import de.mhus.nimbus.world.shared.chat.WChatSessionQueue;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Per-chat runtime state and behavior for Eliza.
 * Created on session start, destroyed on session end.
 * Holds all conversation logic, idle tracking, thinking behavior,
 * and session control (sleep/archive).
 */
@Slf4j
class ElizaActiveChat {

    private static final String AGENT_ID = "eliza-agent";
    private static final int IDLE_NUDGE_THRESHOLD = 3; // ~30s (3 × 10s poll)
    private static final int MAX_NUDGES = 2;

    private final WChatService chatService;
    private final WChatSessionQueue queue;
    private final Random random = new Random();

    // Persisted state
    private int nudgeCount;

    // Transient state
    private int idleTicks;

    private static final String[] GREETINGS = {
            "Hello! How are you feeling today?",
            "Hi there! What brings you here?",
            "Welcome! What's on your mind?"
    };

    private static final String[] QUESTION_RESPONSES = {
            "Why do you ask that?",
            "What would it mean to you if I told you?",
            "What comes to mind when you think about that?",
            "Why is that question important to you?"
    };

    private static final String[] FEELING_RESPONSES = {
            "Tell me more about how you feel.",
            "Why do you feel that way?",
            "How long have you felt like this?",
            "What makes you feel that way?"
    };

    private static final String[] DEFAULT_RESPONSES = {
            "I see. Please go on.",
            "Can you elaborate on that?",
            "Very interesting. Tell me more.",
            "How does that make you feel?",
            "What do you think about that?"
    };

    private static final String[] THINKING_FILLERS = {
            "Hmm...",
            "Let me think about that...",
            "Interesting...",
            "One moment...",
            "Well...",
    };

    private static final String[] NUDGE_MESSAGES = {
            "Are you still there? Everything ok?",
            "You've been quiet... need any help?",
            "Still thinking? I'm here if you need me.",
            "Is there anything else on your mind?",
            "Take your time. I'm listening whenever you're ready.",
    };

    private static final String[] SLEEP_RESPONSES = {
            "Alright, I'll rest now. Talk to you later!",
            "Going to sleep... wake me anytime!",
            "Sweet dreams to us both. See you soon!",
    };

    private static final String[] GOODBYE_RESPONSES = {
            "Goodbye! It was nice talking to you.",
            "Take care! This chat is now closed.",
            "Until next time! Archiving our conversation.",
    };

    ElizaActiveChat(WChatService chatService, WChat chat, WChatSessionQueue queue) {
        this.chatService = chatService;
        this.queue = queue;
        // Restore persisted state
        if (chat != null && chat.getAgentState() != null) {
            Object nc = chat.getAgentState().get("nudgeCount");
            if (nc instanceof Number n) {
                this.nudgeCount = n.intValue();
            }
        }
    }

    void persistState(WChat chat) {
        chat.setAgentState(Map.of("nudgeCount", nudgeCount));
    }

    // ==================== Chat ====================

    List<WChatMessage> chat(WorldId worldId, String chatId, String playerId, String message) {
        log.debug("Eliza processing message from player {}: {}", playerId, message);
        idleTicks = 0;

        // Slash commands
        if (message != null && message.trim().startsWith("/")) {
            String commandName = message.trim().substring(1);
            log.info("Creating command message for: {}", commandName);

            return List.of(WChatMessage.builder()
                    .worldId(worldId.toBaseWorldId().getId())
                    .messageId(UUID.randomUUID().toString())
                    .senderId(AGENT_ID)
                    .message("Click to execute: " + commandName)
                    .type(commandName)
                    .command(true)
                    .createdAt(Instant.now())
                    .build());
        }

        String lower = message != null ? message.toLowerCase().trim() : "";

        // Sleep request
        if (matchesSleep(lower)) {
            String response = randomFrom(SLEEP_RESPONSES);
            log.info("Eliza going to sleep: chatId={}", chatId);
            if (queue != null) queue.requestSleep();
            return List.of(textMessage(worldId, response));
        }

        // Archive request
        if (matchesArchive(lower)) {
            String response = randomFrom(GOODBYE_RESPONSES);
            log.info("Eliza archiving chat: chatId={}", chatId);
            if (queue != null) queue.requestArchive();
            return List.of(textMessage(worldId, response));
        }

        String response = generateResponse(message);

        // ~30% chance to "think" first
        if (random.nextInt(100) < 30 && chatId != null) {
            return respondWithThinking(worldId, chatId, response);
        }

        return List.of(textMessage(worldId, response));
    }

    // ==================== Idle ====================

    WChatAgent.IdleResult onIdle(WorldId worldId, String chatId, WChatSessionQueue queue) {
        idleTicks++;

        if (idleTicks >= IDLE_NUDGE_THRESHOLD && nudgeCount < MAX_NUDGES) {
            String nudge = randomFrom(NUDGE_MESSAGES);
            chatService.saveMessage(WChatMessage.builder()
                    .worldId(worldId.toBaseWorldId().getId())
                    .chatId(chatId)
                    .messageId(UUID.randomUUID().toString())
                    .senderId(AGENT_ID)
                    .message(nudge)
                    .type("text")
                    .createdAt(Instant.now())
                    .build());
            nudgeCount++;
            idleTicks = 0;
            log.debug("Eliza nudge #{}: chatId={}, '{}'", nudgeCount, chatId, nudge);
        }

        return WChatAgent.IdleResult.IDLE;
    }

    // ==================== Session control matching ====================

    private boolean matchesSleep(String lower) {
        return lower.contains("schlafe") || lower.contains("schlaf jetzt")
                || lower.contains("go to sleep") || lower.contains("sleep now")
                || lower.contains("pause") || lower.contains("ruh dich aus");
    }

    private boolean matchesArchive(String lower) {
        return lower.contains("es ist zuende") || lower.contains("es ist zu ende")
                || lower.contains("chat beenden") || lower.contains("tschüss")
                || lower.contains("auf wiedersehen") || lower.contains("goodbye")
                || lower.contains("end chat") || lower.contains("close chat")
                || lower.contains("it's over") || lower.contains("we're done");
    }

    // ==================== Response generation ====================

    private List<WChatMessage> respondWithThinking(WorldId worldId, String chatId, String actualResponse) {
        String filler = randomFrom(THINKING_FILLERS);

        chatService.saveMessage(WChatMessage.builder()
                .worldId(worldId.toBaseWorldId().getId())
                .chatId(chatId)
                .messageId(UUID.randomUUID().toString())
                .senderId(AGENT_ID)
                .message(filler)
                .type("text")
                .createdAt(Instant.now())
                .build());
        log.debug("Eliza thinking: '{}'", filler);

        try {
            Thread.sleep(1000 + random.nextInt(2000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return List.of(textMessage(worldId, actualResponse));
    }

    private String generateResponse(String message) {
        if (message == null || message.isBlank()) {
            return randomFrom(DEFAULT_RESPONSES);
        }

        String lower = message.toLowerCase().trim();

        if (lower.matches("^(hi|hello|hey|greetings).*"))
            return randomFrom(GREETINGS);
        if (lower.endsWith("?"))
            return randomFrom(QUESTION_RESPONSES);
        if (lower.contains("feel") || lower.contains("felt") ||
                lower.contains("sad") || lower.contains("happy") ||
                lower.contains("angry") || lower.contains("worried"))
            return randomFrom(FEELING_RESPONSES);
        if (lower.matches("^(i am|i'm) .*"))
            return "Why are you " + lower.replaceFirst("^(i am|i'm) ", "") + "?";
        if (lower.matches("^(i want|i need) .*"))
            return "What would it mean to you to get " + lower.replaceFirst("^(i want|i need) ", "") + "?";
        if (lower.matches("^i can't .*"))
            return "Why do you think you can't " + lower.replaceFirst("^i can't ", "") + "?";

        return randomFrom(DEFAULT_RESPONSES);
    }

    private WChatMessage textMessage(WorldId worldId, String text) {
        return WChatMessage.builder()
                .worldId(worldId.toBaseWorldId().getId())
                .messageId(UUID.randomUUID().toString())
                .senderId(AGENT_ID)
                .message(text)
                .type("text")
                .createdAt(Instant.now())
                .build();
    }

    private String randomFrom(String[] arr) {
        return arr[random.nextInt(arr.length)];
    }
}
