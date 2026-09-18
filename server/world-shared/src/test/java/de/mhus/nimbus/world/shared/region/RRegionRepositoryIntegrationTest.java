package de.mhus.nimbus.world.shared.region;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the scalar aggregations of {@link RRegionRepository} against a real MongoDB.
 *
 * findAllIds() returns a scalar value from a single-field projection pipeline,
 * whose mapping to the declared return type cannot be proven with a mock.
 * findByIdAndEnabled() backs RRegionService.getRegionNameById. Both forms
 * (aggregation with a scalar list, and derived entity queries) are what the
 * AOT code generator can express for native images; the previous
 * @Query/derived scalar forms could not.
 */
@DataMongoTest
@Testcontainers
@Import(RRegionRepositoryIntegrationTest.TestConfig.class)
class RRegionRepositoryIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    @EnableMongoRepositories(basePackageClasses = RRegionRepository.class)
    static class TestConfig {}

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0").withExposedPorts(27017);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private RRegionRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clearCollection() {
        mongoTemplate.remove(new Query(), RRegion.class);
    }

    // --- findAllIds ---

    @Test
    void findAllIdsReturnsEveryStoredRegionId() {
        RRegion alpha = repository.save(new RRegion("alpha"));
        RRegion beta = repository.save(new RRegion("beta"));

        List<String> ids = repository.findAllIds();

        assertThat(ids).containsExactlyInAnyOrder(alpha.getId(), beta.getId());
    }

    @Test
    void findAllIdsIsEmptyWithoutRegions() {
        assertThat(repository.findAllIds()).isEmpty();
    }

    // --- findByIdAndEnabled (the base of RRegionService.getRegionNameById) ---

    @Test
    void findByIdAndEnabledReturnsTheEnabledRegion() {
        RRegion alpha = repository.save(new RRegion("alpha"));

        Optional<RRegion> region = repository.findByIdAndEnabled(alpha.getId(), true);

        assertThat(region).contains(alpha);
        assertThat(region.map(RRegion::getName)).contains("alpha");
    }

    @Test
    void findByIdAndEnabledIgnoresDisabledRegions() {
        RRegion alpha = repository.save(new RRegion("alpha"));
        alpha.setEnabled(false);
        repository.save(alpha);

        assertThat(repository.findByIdAndEnabled(alpha.getId(), true)).isEmpty();
    }

    @Test
    void findByIdAndEnabledReturnsADisabledRegionWhenAsked() {
        RRegion alpha = repository.save(new RRegion("alpha"));
        alpha.setEnabled(false);
        repository.save(alpha);

        assertThat(repository.findByIdAndEnabled(alpha.getId(), false)).isPresent();
    }

    @Test
    void findByIdAndEnabledIsEmptyForAnUnknownId() {
        repository.save(new RRegion("alpha"));

        assertThat(repository.findByIdAndEnabled("does-not-exist", true)).isEmpty();
    }
}
