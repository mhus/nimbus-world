package de.mhus.nimbus.world.player.gameplay;

import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class AdventureGameplay extends BasicGameplay {

    @Override
    public void onSessionAuthenticated(PlayerSession session) {
        var data = new AdventureData();
        session.setGameplayData(data);
    }

    @Override
    public Map<String, Object> serialize(PlayerSession session) {
        return Map.of();
    }

}
