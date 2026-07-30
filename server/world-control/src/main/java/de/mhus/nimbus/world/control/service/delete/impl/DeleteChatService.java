package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.chat.WChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Deletes chat channels and messages for a world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteChatService implements DeleteWorldResources {

    private final WChatService chatService;

    @Override
    public String name() {
        return "chat";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting chat data for world {}", worldId);
        chatService.deleteByWorldId(worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return chatService.findDistinctWorldIds();
    }
}
