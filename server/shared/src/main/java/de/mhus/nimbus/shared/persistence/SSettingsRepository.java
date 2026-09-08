package de.mhus.nimbus.shared.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SSettingsRepository extends MongoRepository<SSettings, String> {

    List<SSettings> findByKey(String key);

    boolean existsByKey(String key);

    void deleteByKey(String key);

    List<SSettings> findByType(String type);
}
