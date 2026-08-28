package de.mhus.nimbus.world.shared.world;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates the unique index that makes the shared chunk documents of {@link WProgress} unique.
 *
 * Block statuses and block cooldowns store one document per chunk (playerId="world",
 * quest=chunkKey). {@link WProgressService#claimBlockStatus} claims a block with a conditional
 * update on that document and is only exclusive as long as there is exactly one of them - an
 * upsert without a unique index can insert a second document when two pods create the same chunk
 * at the same moment, and both callers would win their claim on their own copy.
 *
 * The index is created here instead of via {@code @CompoundIndex} because automatic index
 * creation is disabled (Spring Data default), so annotations on the entity have no effect.
 * It is partial: only documents of the shared player take part, real player progress is
 * untouched and may keep as many documents per type as it likes.
 *
 * Documents that already violate the index are merged into one before it is created, so an
 * existing database does not keep the index from being applied.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WProgressIndexInitializer implements InitializingBean {

    static final String INDEX_NAME = "world_shared_chunk_unique_idx";

    /** playerId of the shared documents (block status, block cooldown) */
    private static final String SHARED_PLAYER = "world";

    private final MongoTemplate mongoTemplate;

    @Override
    public void afterPropertiesSet() {
        try {
            if (indexExists()) {
                log.debug("Index {} already present", INDEX_NAME);
                return;
            }
            int merged = mergeDuplicateChunkDocuments();
            if (merged > 0) {
                log.info("Merged {} duplicate shared chunk documents before creating index {}", merged, INDEX_NAME);
            }
            createIndex();
            log.info("Created index {} on w_progress", INDEX_NAME);
        } catch (Exception e) {
            // A missing index costs exclusiveness in a rare race, it must not keep the pod from starting
            log.error("Could not create index {} on w_progress. Concurrent block claims are not exclusive"
                    + " until it exists - create it manually if this keeps happening.", INDEX_NAME, e);
        }
    }

    private boolean indexExists() {
        for (IndexInfo info : mongoTemplate.indexOps(WProgress.class).getIndexInfo()) {
            if (INDEX_NAME.equals(info.getName())) return true;
        }
        return false;
    }

    private void createIndex() {
        Index index = new Index()
                .on("worldId", Sort.Direction.ASC)
                .on("playerId", Sort.Direction.ASC)
                .on("type", Sort.Direction.ASC)
                .on("quest", Sort.Direction.ASC)
                .named(INDEX_NAME)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("playerId").is(SHARED_PLAYER)));

        mongoTemplate.indexOps(WProgress.class).createIndex(index);
    }

    /**
     * Merge shared chunk documents that exist more than once for the same (worldId, type, quest)
     * into the oldest of them. On colliding block keys the most recently updated document wins.
     *
     * @return number of removed duplicates
     */
    private int mergeDuplicateChunkDocuments() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("playerId").is(SHARED_PLAYER)),
                Aggregation.group("worldId", "type", "quest").count().as("count"),
                Aggregation.match(Criteria.where("count").gt(1))
        ).withOptions(AggregationOptions.builder().allowDiskUse(true).build());

        List<Document> groups = mongoTemplate
                .aggregate(aggregation, WProgress.class, Document.class)
                .getMappedResults();

        int removed = 0;
        for (Document group : groups) {
            Document key = group.get("_id", Document.class);
            removed += mergeGroup(key.getString("worldId"), key.getString("type"), key.getString("quest"));
        }
        return removed;
    }

    private int mergeGroup(String worldId, String type, String quest) {
        Query query = new Query(Criteria.where("worldId").is(worldId)
                .and("playerId").is(SHARED_PLAYER)
                .and("type").is(type)
                .and("quest").is(quest));

        List<WProgress> documents = new ArrayList<>(mongoTemplate.find(query, WProgress.class));
        if (documents.size() < 2) return 0;

        // Oldest document survives, newer data wins on colliding keys
        documents.sort(Comparator.comparing(WProgress::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
        WProgress target = documents.getFirst();

        List<WProgress> sortedByUpdate = new ArrayList<>(documents);
        sortedByUpdate.sort(Comparator.comparing(WProgress::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder())));

        Map<String, Object> merged = new LinkedHashMap<>();
        for (WProgress document : sortedByUpdate) {
            if (document.getProgressData() != null) merged.putAll(document.getProgressData());
        }

        target.setProgressData(merged);
        target.touchUpdate();
        mongoTemplate.save(target);

        List<String> obsolete = documents.stream().map(WProgress::getId).filter(id -> !id.equals(target.getId())).toList();
        mongoTemplate.remove(new Query(Criteria.where("_id").in(obsolete)), WProgress.class);

        log.warn("Merged {} duplicate chunk documents: worldId={}, type={}, quest={}",
                obsolete.size(), worldId, type, quest);
        return obsolete.size();
    }
}
