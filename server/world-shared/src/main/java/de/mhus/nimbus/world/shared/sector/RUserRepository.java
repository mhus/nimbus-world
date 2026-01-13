package de.mhus.nimbus.world.shared.sector;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RUserRepository extends MongoRepository<RUser, String> {
    Optional<RUser> findByUsername(String username);
    Optional<RUser> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
