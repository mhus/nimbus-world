package de.mhus.nimbus.shared.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SSettingsRepository extends MongoRepository<SSettings, String> {

    List<SSettings> findByKey(String key);

    boolean existsByKey(String key);

    void deleteByKey(String key);

    List<SSettings> findByType(String type);
}
