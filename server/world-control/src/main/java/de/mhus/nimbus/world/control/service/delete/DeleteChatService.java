package de.mhus.nimbus.world.control.service.delete;

import de.mhus.nimbus.world.shared.chat.WChat;
import de.mhus.nimbus.world.shared.chat.WChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes chat channels and messages for a world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteChatService implements DeleteWorldResources {

    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "chat";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting chat data for world {}", worldId);
        Query query = new Query(Criteria.where("worldId").is(worldId));

        var messages = mongoTemplate.remove(query, WChatMessage.class);
        var chats = mongoTemplate.remove(new Query(Criteria.where("worldId").is(worldId)), WChat.class);

        log.info("Deleted chat for world {}: {} messages, {} channels",
                worldId, messages.getDeletedCount(), chats.getDeletedCount());
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        Set<String> worldIds = new HashSet<>();
        worldIds.addAll(mongoTemplate.findDistinct(new Query(), "worldId", WChat.class, String.class));
        worldIds.addAll(mongoTemplate.findDistinct(new Query(), "worldId", WChatMessage.class, String.class));
        return worldIds.stream().sorted().toList();
    }
}
