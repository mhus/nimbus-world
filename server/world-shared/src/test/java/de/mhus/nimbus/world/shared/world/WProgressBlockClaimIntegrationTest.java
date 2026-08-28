package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.config.SchemaAwareMongoConfig;
import de.mhus.nimbus.world.shared.redis.BlockStatusPublisher;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Proves the exclusiveness the collect gameplay relies on, against a real MongoDB.
 *
 * The claim of a block is a conditional update on the shared chunk document. Its guarantee only
 * holds while there is exactly one such document per chunk, which is what the unique index created
 * by {@link WProgressIndexInitializer} enforces - mocking MongoDB would prove none of that.
 */
@DataMongoTest
@Testcontainers
@Import({WProgressBlockClaimIntegrationTest.TestConfig.class, SchemaAwareMongoConfig.class,
        WProgressService.class, WProgressIndexInitializer.class})
class WProgressBlockClaimIntegrationTest {

    private static final String WORLD_ID = "w:test";
    private static final String CHUNK = "1:2";
    private static final String BLOCK = "10,64,20";
    private static final String STATUS = "empty";

    @Configuration
    @EnableAutoConfiguration
    @EnableMongoRepositories(basePackageClasses = WProgressRepository.class)
    static class TestConfig {
    }

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0").withExposedPorts(27017);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private WProgressService progressService;

    @Autowired
    private WProgressIndexInitializer indexInitializer;

    @Autowired
    private MongoTemplate mongoTemplate;

    @MockitoBean
    private BlockStatusPublisher blockStatusPublisher;

    @BeforeEach
    void clearCollection() {
        mongoTemplate.remove(new Query(), WProgress.class);
    }

    // --- block status claim ---

    @Test
    void theFirstClaimWinsAndTheSecondFails() {
        assertThat(progressService.claimBlockStatus(WORLD_ID, CHUNK, BLOCK, STATUS)).isTrue();
        assertThat(progressService.claimBlockStatus(WORLD_ID, CHUNK, BLOCK, STATUS)).isFalse();

        verify(blockStatusPublisher).publishStatusChange(WORLD_ID, CHUNK, BLOCK, STATUS);
    }

    @Test
    void theBlockCanBeClaimedAgainAfterItWasReset() {
        progressService.claimBlockStatus(WORLD_ID, CHUNK, BLOCK, STATUS);
        progressService.removeBlockStatus(WORLD_ID, CHUNK, BLOCK);

        assertThat(progressService.claimBlockStatus(WORLD_ID, CHUNK, BLOCK, STATUS)).isTrue();
    }

    /**
     * The interesting case: the chunk document does not exist yet, so every caller races to create
     * it. Without the unique index the upsert can insert one document per caller and each of them
     * wins the claim on its own copy.
     */
    @Test
    void onlyOneOfManyConcurrentClaimsOnAFreshChunkWins() throws Exception {
        int threads = 16;
        List<Boolean> results = runConcurrently(threads,
                () -> progressService.claimBlockStatus(WORLD_ID, CHUNK, BLOCK, STATUS));

        assertThat(results).containsOnlyOnce(true);
        assertThat(countChunkDocuments("block-status")).isOne();
    }

    @Test
    void onlyOneOfManyConcurrentClaimsOnAnExistingChunkWins() throws Exception {
        progressService.setBlockStatus(WORLD_ID, CHUNK, "other,block,key", "open");

        List<Boolean> results = runConcurrently(16,
                () -> progressService.claimBlockStatus(WORLD_ID, CHUNK, BLOCK, STATUS));

        assertThat(results).containsOnlyOnce(true);
    }

    @Test
    void theUniqueIndexRejectsASecondChunkDocument() {
        progressService.setBlockStatus(WORLD_ID, CHUNK, BLOCK, STATUS);

        assertThatThrownBy(() -> mongoTemplate.insert(sharedChunkDocument("block-status", CHUNK)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void theUniqueIndexLeavesRealPlayerProgressAlone() {
        WProgress first = WProgress.builder().worldId(WORLD_ID).playerId("player-1").type("achievement").build();
        WProgress second = WProgress.builder().worldId(WORLD_ID).playerId("player-1").type("achievement").build();

        mongoTemplate.insert(first);
        mongoTemplate.insert(second);

        assertThat(mongoTemplate.count(new Query(Criteria.where("playerId").is("player-1")), WProgress.class))
                .isEqualTo(2);
    }

    // --- conditional status removal ---

    @Test
    void aStatusSetBySomebodyElseSurvivesTheReset() {
        progressService.setBlockStatus(WORLD_ID, CHUNK, BLOCK, "open");

        assertThat(progressService.claimRemoveBlockStatus(WORLD_ID, CHUNK, BLOCK, STATUS)).isFalse();
        assertThat(readStatus(BLOCK)).isEqualTo("open");
    }

    @Test
    void theExpectedStatusIsRemovedAndPublished() {
        progressService.claimBlockStatus(WORLD_ID, CHUNK, BLOCK, STATUS);

        assertThat(progressService.claimRemoveBlockStatus(WORLD_ID, CHUNK, BLOCK, STATUS)).isTrue();
        assertThat(readStatus(BLOCK)).isNull();
        verify(blockStatusPublisher).publishStatusChange(WORLD_ID, CHUNK, BLOCK, null);
    }

    // --- cooldowns ---

    @Test
    void anExpiredCooldownIsFoundWithItsStatus() {
        progressService.setBlockCooldown(WORLD_ID, CHUNK, BLOCK, 1_000L, STATUS);

        List<WBlockCooldown> expired = progressService.findExpiredBlockCooldowns(WORLD_ID, 2_000L);

        assertThat(expired).containsExactly(new WBlockCooldown(CHUNK, BLOCK, 1_000L, STATUS));
    }

    @Test
    void aRunningCooldownIsNotReported() {
        progressService.setBlockCooldown(WORLD_ID, CHUNK, BLOCK, 5_000L, STATUS);

        assertThat(progressService.findExpiredBlockCooldowns(WORLD_ID, 2_000L)).isEmpty();
    }

    /** Entries written before the status was stored alongside the expiry are a bare number. */
    @Test
    void aLegacyCooldownValueIsStillUnderstoodAndClaimable() {
        WProgress document = sharedChunkDocument("block-cooldown", CHUNK);
        document.setProgressData(new java.util.LinkedHashMap<>(java.util.Map.of(BLOCK, 1_000L)));
        mongoTemplate.insert(document);

        List<WBlockCooldown> expired = progressService.findExpiredBlockCooldowns(WORLD_ID, 2_000L);
        assertThat(expired).containsExactly(new WBlockCooldown(CHUNK, BLOCK, 1_000L, null));
        assertThat(expired.getFirst().hasStatus()).isFalse();

        assertThat(progressService.claimExpiredBlockCooldown(WORLD_ID, CHUNK, BLOCK, 1_000L)).isTrue();
    }

    @Test
    void anUnreadableCooldownValueIsTreatedAsExpired() {
        WProgress document = sharedChunkDocument("block-cooldown", CHUNK);
        document.setProgressData(new java.util.LinkedHashMap<>(java.util.Map.of(BLOCK, "broken")));
        mongoTemplate.insert(document);

        assertThat(progressService.findExpiredBlockCooldowns(WORLD_ID, 2_000L))
                .containsExactly(new WBlockCooldown(CHUNK, BLOCK, 0L, null));
    }

    @Test
    void onlyOneSweeperClaimsAnExpiredCooldown() throws Exception {
        progressService.setBlockCooldown(WORLD_ID, CHUNK, BLOCK, 1_000L, STATUS);

        List<Boolean> results = runConcurrently(8,
                () -> progressService.claimExpiredBlockCooldown(WORLD_ID, CHUNK, BLOCK, 1_000L));

        assertThat(results).containsOnlyOnce(true);
    }

    @Test
    void aRefreshedCooldownIsNotDroppedByALateSweep() {
        progressService.setBlockCooldown(WORLD_ID, CHUNK, BLOCK, 1_000L, STATUS);
        progressService.setBlockCooldown(WORLD_ID, CHUNK, BLOCK, 9_000L, STATUS);

        assertThat(progressService.claimExpiredBlockCooldown(WORLD_ID, CHUNK, BLOCK, 1_000L)).isFalse();
        assertThat(progressService.findExpiredBlockCooldowns(WORLD_ID, 10_000L))
                .containsExactly(new WBlockCooldown(CHUNK, BLOCK, 9_000L, STATUS));
    }

    @Test
    void aRolledBackCooldownLeavesNothingBehind() {
        progressService.setBlockCooldown(WORLD_ID, CHUNK, BLOCK, 9_000L, STATUS);
        progressService.removeBlockCooldown(WORLD_ID, CHUNK, BLOCK);

        assertThat(progressService.findExpiredBlockCooldowns(WORLD_ID, 10_000L)).isEmpty();
        verifyNoInteractions(blockStatusPublisher);
    }

    // --- cleanup ---

    @Test
    void emptyChunkDocumentsAreDeletedForBothTypes() {
        progressService.claimBlockStatus(WORLD_ID, CHUNK, BLOCK, STATUS);
        progressService.setBlockCooldown(WORLD_ID, CHUNK, BLOCK, 1_000L, STATUS);
        progressService.claimExpiredBlockCooldown(WORLD_ID, CHUNK, BLOCK, 1_000L);
        progressService.claimRemoveBlockStatus(WORLD_ID, CHUNK, BLOCK, STATUS);

        assertThat(progressService.deleteEmptyBlockCooldowns(WORLD_ID)).isOne();
        assertThat(progressService.deleteEmptyBlockStatuses(WORLD_ID)).isOne();
        assertThat(mongoTemplate.count(new Query(), WProgress.class)).isZero();
    }

    @Test
    void aChunkDocumentThatStillHoldsEntriesIsKept() {
        progressService.setBlockStatus(WORLD_ID, CHUNK, BLOCK, "open");

        assertThat(progressService.deleteEmptyBlockStatuses(WORLD_ID)).isZero();
    }

    // --- migration of databases written before the index existed ---

    @Test
    void duplicateChunkDocumentsAreMergedBeforeTheIndexIsCreated() {
        mongoTemplate.indexOps(WProgress.class).dropIndex(WProgressIndexInitializer.INDEX_NAME);

        WProgress older = sharedChunkDocument("block-status", CHUNK);
        older.setCreatedAt(Instant.ofEpochMilli(1_000));
        older.setUpdatedAt(Instant.ofEpochMilli(1_000));
        older.setProgressData(new java.util.LinkedHashMap<>(java.util.Map.of("1,1,1", "open")));
        WProgress newer = sharedChunkDocument("block-status", CHUNK);
        newer.setCreatedAt(Instant.ofEpochMilli(2_000));
        newer.setUpdatedAt(Instant.ofEpochMilli(2_000));
        newer.setProgressData(new java.util.LinkedHashMap<>(java.util.Map.of("2,2,2", "closed")));
        mongoTemplate.insert(older);
        mongoTemplate.insert(newer);

        indexInitializer.afterPropertiesSet();

        assertThat(countChunkDocuments("block-status")).isOne();
        assertThat(readStatus("1,1,1")).isEqualTo("open");
        assertThat(readStatus("2,2,2")).isEqualTo("closed");
        assertThatThrownBy(() -> mongoTemplate.insert(sharedChunkDocument("block-status", CHUNK)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // --- helpers ---

    private WProgress sharedChunkDocument(String type, String chunkKey) {
        return WProgress.builder()
                .worldId(WORLD_ID)
                .playerId("world")
                .type(type)
                .quest(chunkKey)
                .progressId(UUID.randomUUID().toString())
                .build();
    }

    private long countChunkDocuments(String type) {
        return mongoTemplate.count(new Query(Criteria.where("worldId").is(WORLD_ID)
                .and("playerId").is("world")
                .and("type").is(type)), WProgress.class);
    }

    private String readStatus(String blockKey) {
        Document document = mongoTemplate.findOne(new Query(Criteria.where("worldId").is(WORLD_ID)
                .and("playerId").is("world")
                .and("type").is("block-status")), Document.class, "w_progress");
        if (document == null) return null;
        Document progressData = document.get("progressData", Document.class);
        return progressData != null ? progressData.getString(blockKey) : null;
    }

    /**
     * Run the same call on many threads at once, released by a barrier so they really collide.
     */
    private <T> List<T> runConcurrently(int threads, Callable<T> call) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(threads);
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<T>> futures = IntStream.range(0, threads)
                    .mapToObj(i -> executor.submit(() -> {
                        barrier.await();
                        return call.call();
                    }))
                    .toList();

            List<T> results = new java.util.ArrayList<>();
            for (Future<T> future : futures) results.add(future.get());
            return results;
        }
    }
}
