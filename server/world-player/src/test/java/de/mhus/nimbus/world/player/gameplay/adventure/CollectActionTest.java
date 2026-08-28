package de.mhus.nimbus.world.player.gameplay.adventure;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.service.ClientService;
import de.mhus.nimbus.world.player.service.GameplayService;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItemService;
import de.mhus.nimbus.world.shared.world.WProgressService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the exclusiveness and rollback behaviour of collecting from a block.
 *
 * The order of the calls matters: the block is claimed and its cooldown stored before any reward is
 * handed out, because a block that carries the collect status without a cooldown entry would never
 * be reset by world-life again.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollectActionTest {

    private static final String WORLD_ID = "w:test";
    private static final String CHUNK = "1:2";
    private static final String BLOCK = "10,64,20";
    private static final int X = 10;
    private static final int Y = 64;
    private static final int Z = 20;

    @Mock
    private AdventureGameplay adventure;
    @Mock
    private WProgressService progressService;
    @Mock
    private GameplayService gameplayService;
    @Mock
    private ClientService clientService;
    @Mock
    private WWorldService worldService;
    @Mock
    private WItemService itemService;
    @Mock
    private WWorld world;
    @Mock
    private PlayerSession session;

    private AdventureData data;
    private CollectAction action;

    @BeforeEach
    void setUp() {
        data = new AdventureData();
        action = new CollectAction(adventure);

        when(adventure.getProgressService()).thenReturn(progressService);
        when(adventure.getGameplayService()).thenReturn(gameplayService);
        when(adventure.getClientService()).thenReturn(clientService);
        when(adventure.getWorldService()).thenReturn(worldService);
        when(adventure.getItemService()).thenReturn(itemService);

        when(session.getGameplayData()).thenReturn(data);
        when(session.getWorldId()).thenReturn(WorldId.unchecked(WORLD_ID));
        when(session.getEntityId()).thenReturn("w:test/mhus/hero");

        when(worldService.getByWorldId(WORLD_ID)).thenReturn(Optional.of(world));
        when(world.getChunkKey(X, Z)).thenReturn(CHUNK);
        when(itemService.findByItemId(any(), anyString())).thenReturn(Optional.empty());

        when(progressService.claimBlockStatus(WORLD_ID, CHUNK, BLOCK, "empty")).thenReturn(true);
    }

    private boolean collectBlock(Map<String, String> serverInfo) {
        return action.handleBlockAction(session, X, Y, Z, "grass", null, "collect", null, "interact", null, serverInfo);
    }

    private Map<String, String> serverInfo(String reward, String cooldown) {
        return Map.of("collectReward", reward, "collectCooldown", cooldown);
    }

    @Test
    void theCooldownIsStoredBeforeTheRewardIsHandedOut() {
        when(gameplayService.putIntoBackpack(session, "wood", 1)).thenReturn(true);

        assertThat(collectBlock(serverInfo("100:wood:1", "60"))).isTrue();

        InOrder inOrder = inOrder(progressService, gameplayService);
        inOrder.verify(progressService).claimBlockStatus(WORLD_ID, CHUNK, BLOCK, "empty");
        inOrder.verify(progressService).setBlockCooldown(eq(WORLD_ID), eq(CHUNK), eq(BLOCK), anyLong(), eq("empty"));
        inOrder.verify(gameplayService).putIntoBackpack(session, "wood", 1);
    }

    @Test
    void theStoredCooldownMatchesTheConfiguredRegrowTime() {
        when(gameplayService.putIntoBackpack(session, "wood", 1)).thenReturn(true);
        long before = System.currentTimeMillis();

        collectBlock(serverInfo("100:wood:1", "60"));

        verify(progressService).setBlockCooldown(eq(WORLD_ID), eq(CHUNK), eq(BLOCK),
                longThatIsBetween(before + 60_000, System.currentTimeMillis() + 60_000), eq("empty"));
    }

    /**
     * The element is claimed at this point. A reward blowing up must not skip the cooldown entry,
     * otherwise the block stays exhausted forever.
     */
    @Test
    void aFailingRewardLeavesTheCooldownInPlace() {
        when(gameplayService.putIntoBackpack(session, "wood", 1)).thenThrow(new IllegalStateException("boom"));

        assertThat(collectBlock(serverInfo("100:wood:1", "60"))).isTrue();

        verify(progressService).setBlockCooldown(eq(WORLD_ID), eq(CHUNK), eq(BLOCK), anyLong(), eq("empty"));
        verify(progressService, never()).removeBlockCooldown(anyString(), anyString(), anyString());
    }

    @Test
    void aFullBackpackGivesTheElementBack() {
        when(gameplayService.putIntoBackpack(session, "wood", 1)).thenReturn(false);

        assertThat(collectBlock(serverInfo("100:wood:1", "60"))).isTrue();

        verify(progressService).removeBlockCooldown(WORLD_ID, CHUNK, BLOCK);
        verify(progressService).claimRemoveBlockStatus(WORLD_ID, CHUNK, BLOCK, "empty");
        verify(clientService).sendNotification(eq(session), anyInt(), anyString(), eq("Dein Rucksack ist voll"), any());
    }

    @Test
    void aPartlyDeliveredRewardKeepsTheElementExhausted() {
        when(gameplayService.putIntoBackpack(session, "wood", 1)).thenReturn(true);
        when(gameplayService.putIntoBackpack(session, "stone", 1)).thenReturn(false);

        collectBlock(serverInfo("100:wood:1,100:stone:1", "60"));

        verify(progressService, never()).removeBlockCooldown(anyString(), anyString(), anyString());
        verify(progressService, never()).claimRemoveBlockStatus(anyString(), anyString(), anyString(), anyString());
    }

    /** Rolling no reward at all is a normal harvest, the bush stays empty. */
    @Test
    void anEmptyRollKeepsTheElementExhausted() {
        collectBlock(serverInfo("0:wood:1", "60"));

        verify(gameplayService, never()).putIntoBackpack(any(), anyString(), anyInt());
        verify(progressService, never()).removeBlockCooldown(anyString(), anyString(), anyString());
    }

    @Test
    void aClaimedElementBlocksTheNextPlayer() {
        when(progressService.claimBlockStatus(WORLD_ID, CHUNK, BLOCK, "empty")).thenReturn(false);

        assertThat(collectBlock(serverInfo("100:wood:1", "60"))).isTrue();

        verify(gameplayService, never()).putIntoBackpack(any(), anyString(), anyInt());
        verify(progressService, never()).setBlockCooldown(anyString(), anyString(), anyString(), anyLong(), anyString());
        verify(clientService).sendNotification(eq(session), anyInt(), anyString(),
                eq("Hier gibt es nichts mehr zu ernten"), any());
        assertThat(data.getNextCollectAllowed()).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    void theCollectStatusCanBeOverriddenPerBlock() {
        when(progressService.claimBlockStatus(WORLD_ID, CHUNK, BLOCK, "harvested")).thenReturn(true);
        when(gameplayService.putIntoBackpack(session, "wood", 1)).thenReturn(true);

        collectBlock(Map.of("collectReward", "100:wood:1", "collectCooldown", "60", "collectStatus", "harvested"));

        verify(progressService).claimBlockStatus(WORLD_ID, CHUNK, BLOCK, "harvested");
        verify(progressService).setBlockCooldown(eq(WORLD_ID), eq(CHUNK), eq(BLOCK), anyLong(), eq("harvested"));
    }

    /** Without a regrow time the element is not exclusive, only the anti spam cooldown applies. */
    @Test
    void anElementWithoutRegrowTimeIsNotClaimed() {
        when(gameplayService.putIntoBackpack(session, "wood", 1)).thenReturn(true);

        collectBlock(serverInfo("100:wood:1", "0"));

        verify(progressService, never()).claimBlockStatus(anyString(), anyString(), anyString(), anyString());
        verify(progressService, never()).setBlockCooldown(anyString(), anyString(), anyString(), anyLong(), anyString());
        assertThat(data.getNextCollectAllowed()).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    void anEntityKeepsThePerPlayerCooldown() {
        WEntity entity = new WEntity();
        entity.setServer(serverInfo("100:wood:1", "60"));
        when(gameplayService.putIntoBackpack(session, "wood", 1)).thenReturn(true);
        long before = System.currentTimeMillis();

        assertThat(action.handleEntityAction(session, entity, "interact", "collect", null, null)).isTrue();

        verify(progressService, never()).claimBlockStatus(anyString(), anyString(), anyString(), anyString());
        assertThat(data.getNextCollectAllowed()).isGreaterThanOrEqualTo(before + 60_000);
    }

    @Test
    void aPlayerOnCooldownIsRejected() {
        data.setNextCollectAllowed(System.currentTimeMillis() + 10_000);

        assertThat(collectBlock(serverInfo("100:wood:1", "60"))).isFalse();

        verify(progressService, never()).claimBlockStatus(anyString(), anyString(), anyString(), anyString());
    }

    private static long longThatIsBetween(long lowInclusive, long highInclusive) {
        return org.mockito.ArgumentMatchers.longThat(value -> value >= lowInclusive && value <= highInclusive);
    }
}
