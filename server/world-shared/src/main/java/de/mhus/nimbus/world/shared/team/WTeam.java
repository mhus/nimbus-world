package de.mhus.nimbus.world.shared.team;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "w_teams")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_team_idx", def = "{ 'worldId': 1, 'teamId': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WTeam implements Identifiable {

    @Id
    private String id;

    @Indexed
    private String worldId;

    @Indexed(unique = true)
    private String teamId;

    private String title;

    @Builder.Default
    private List<String> members = new ArrayList<>();

    @Builder.Default
    private List<String> invitation = new ArrayList<>();

    private Instant createdAt;

    private Instant updatedAt;

    @Builder.Default
    private WTeamStatus status = WTeamStatus.LOBBY;

    @Builder.Default
    private Map<String, String> parameters = new HashMap<>();
}
