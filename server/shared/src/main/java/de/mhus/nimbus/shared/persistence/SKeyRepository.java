package de.mhus.nimbus.shared.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SKeyRepository extends MongoRepository<SKey, String> {

    Optional<SKey> findByTypeAndKindAndKeyId(String type, String kind, String keyId);
    void deleteByTypeAndKindAndKeyId(String type, String kind, String keyId);

    Optional<SKey> findByTypeAndKindAndOwnerAndKeyId(String type, String kind, String owner, String keyId);
    void deleteByTypeAndKindAndOwnerAndKeyId(String type, String kind, String owner, String keyId);

    List<SKey> findAllByTypeAndKindAndOwnerAndIntentOrderByCreatedAtDesc(String type, String kind, String owner, String intent);

    List<SKey> findTop1ByTypeAndKindAndOwnerOrderByCreatedAtDesc(String type, String kind, String owner);

    void deleteAllByTypeAndKindAndOwner(String name, String name1, String owner);

    void deleteAllByTypeAndKindAndOwnerAndIntent(String name, String name1, String owner, String intent);

    Optional<SKey> findTop1ByTypeAndKindAndOwnerAndIntentOrderByCreatedAtDesc(String name, String kindPrivate, String owner, String intent);

    boolean existsByTypeAndKindAndOwnerAndIntent(String type, String kind, String owner, String intent);
}
