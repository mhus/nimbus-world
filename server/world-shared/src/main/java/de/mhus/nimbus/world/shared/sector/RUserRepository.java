package de.mhus.nimbus.world.shared.sector;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RUserRepository extends MongoRepository<RUser, String> {
    Optional<RUser> findByName(String name);
    Optional<RUser> findByEmail(String email);
    boolean existsByName(String name);
    boolean existsByEmail(String email);
}
