package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.team.WTeam;
import de.mhus.nimbus.world.shared.team.WTeamService;
import de.mhus.nimbus.world.shared.team.WTeamStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for player team self-service operations.
 * Players manage their own team membership here.
 * Route: /control/player/team
 *
 * worldId, userId and characterId are extracted from the session cookie via ControlAccessFilter.
 * All player names shown to users are character names only (e.g. "j3sus"),
 * internally stored as "@userId:characterName".
 */
@RestController
@RequestMapping("/control/player/team")
@RequiredArgsConstructor
@Slf4j
public class PlayerTeamController extends BaseEditorController {

    private final WTeamService teamService;
    private final RCharacterService characterService;

    // --- DTOs ---

    public record TeamResponse(
            String teamId,
            String title,
            List<String> members,
            List<String> invitation,
            String status,
            Map<String, String> parameters
    ) {}

    public record InviteResponse(
            String teamId,
            String title,
            String worldId
    ) {}

    public record MyTeamResponse(
            TeamResponse team,
            List<InviteResponse> invitations
    ) {}

    public record CreateTeamRequest(String title) {}

    public record InvitePlayerRequest(String playerName) {}

    public record InviteResultResponse(List<String> invited, List<String> failed) {}

    /**
     * Extract character name from playerId format "@userId:characterName".
     */
    private String toCharacterName(String playerId) {
        if (playerId == null) return null;
        int colonIdx = playerId.indexOf(':');
        if (colonIdx >= 0) {
            return playerId.substring(colonIdx + 1);
        }
        return playerId.startsWith("@") ? playerId.substring(1) : playerId;
    }

    private TeamResponse toTeamResponse(WTeam team) {
        List<String> memberNames = team.getMembers() != null
                ? team.getMembers().stream().map(this::toCharacterName).toList()
                : List.of();
        List<String> invitationNames = team.getInvitation() != null
                ? team.getInvitation().stream().map(this::toCharacterName).toList()
                : List.of();
        return new TeamResponse(
                team.getTeamId(),
                team.getTitle(),
                memberNames,
                invitationNames,
                team.getStatus() != null ? team.getStatus().name() : WTeamStatus.LOBBY.name(),
                team.getParameters() != null ? team.getParameters() : Map.of()
        );
    }

    private InviteResponse toInviteResponse(WTeam team) {
        return new InviteResponse(team.getTeamId(), team.getTitle(), team.getWorldId());
    }

    private String getPlayerName(HttpServletRequest request) {
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (userId == null || characterId == null) return null;
        return "@" + userId + ":" + characterId;
    }

    private String getWorldId(HttpServletRequest request) {
        return (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
    }

    /**
     * Resolve a character name to the full playerId "@userId:characterName"
     * by looking up the character in the region.
     */
    private String resolvePlayerId(String characterName, String regionId) {
        Optional<RCharacter> charOpt = characterService.findByRegionAndName(regionId, characterName);
        if (charOpt.isEmpty()) return null;
        RCharacter c = charOpt.get();
        return "@" + c.getUserId() + ":" + c.getName();
    }

    /**
     * Get current team status: my team + pending invitations.
     * GET /control/player/team
     */
    @GetMapping
    public ResponseEntity<?> getMyTeam(HttpServletRequest request) {
        String playerName = getPlayerName(request);
        String worldIdStr = getWorldId(request);
        if (playerName == null || worldIdStr == null) return bad("Not authenticated");

        try {
            var worldId = WorldId.unchecked(worldIdStr);
            String mainInstanceId = worldId.getFullMainInstance().getId();

            // Find current team
            Optional<WTeam> teamOpt = teamService.findActiveTeamForPlayer(worldIdStr, mainInstanceId, playerName);
            TeamResponse teamResponse = teamOpt.map(this::toTeamResponse).orElse(null);

            // Find invitations for this player (check both exact worldId and main instance)
            List<InviteResponse> invitations = teamService.findInvitationsForPlayer(worldIdStr, mainInstanceId, playerName).stream()
                    .filter(t -> !t.getMembers().contains(playerName))
                    .map(this::toInviteResponse)
                    .toList();

            return ResponseEntity.ok(new MyTeamResponse(teamResponse, invitations));
        } catch (Exception e) {
            log.error("Failed to get team for player {}: {}", playerName, e.getMessage(), e);
            return bad(e.getMessage());
        }
    }

    /**
     * Create a new team (player becomes first member).
     * POST /control/player/team
     */
    @PostMapping
    public ResponseEntity<?> createTeam(HttpServletRequest request, @RequestBody CreateTeamRequest body) {
        String playerName = getPlayerName(request);
        String worldIdStr = getWorldId(request);
        if (playerName == null || worldIdStr == null) return bad("Not authenticated");

        if (body.title() == null || body.title().isBlank()) {
            return bad("title is required");
        }

        try {
            var worldId = WorldId.unchecked(worldIdStr);
            String mainInstanceId = worldId.getFullMainInstance().getId();

            // Check player is not already in a team
            Optional<WTeam> existing = teamService.findActiveTeamForPlayer(worldIdStr, mainInstanceId, playerName);
            if (existing.isPresent()) {
                return bad("Already in a team: " + existing.get().getTitle());
            }

            WTeam team = teamService.createTeam(mainInstanceId, body.title(), playerName);
            // Player is already in a world instance -> set ACTIVE immediately
            if (worldId.isInstance()) {
                teamService.updateStatus(team.getTeamId(), WTeamStatus.ACTIVE);
                team.setStatus(WTeamStatus.ACTIVE);
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(toTeamResponse(team));
        } catch (Exception e) {
            log.error("Failed to create team for player {}: {}", playerName, e.getMessage(), e);
            return bad(e.getMessage());
        }
    }

    /**
     * Leave current team.
     * DELETE /control/player/team/leave
     */
    @DeleteMapping("/leave")
    public ResponseEntity<?> leaveTeam(HttpServletRequest request) {
        String playerName = getPlayerName(request);
        String worldIdStr = getWorldId(request);
        if (playerName == null || worldIdStr == null) return bad("Not authenticated");

        try {
            var worldId = WorldId.unchecked(worldIdStr);
            String mainInstanceId = worldId.getFullMainInstance().getId();

            Optional<WTeam> teamOpt = teamService.findActiveTeamForPlayer(worldIdStr, mainInstanceId, playerName);
            if (teamOpt.isEmpty()) {
                return bad("Not in a team");
            }

            teamService.removeMemberAtomic(teamOpt.get().getTeamId(), playerName);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to leave team for player {}: {}", playerName, e.getMessage(), e);
            return bad(e.getMessage());
        }
    }

    /**
     * Accept an invitation (join the team).
     * POST /control/player/team/accept/{teamId}
     */
    @PostMapping("/accept/{teamId}")
    public ResponseEntity<?> acceptInvitation(HttpServletRequest request, @PathVariable String teamId) {
        String playerName = getPlayerName(request);
        String worldIdStr = getWorldId(request);
        if (playerName == null || worldIdStr == null) return bad("Not authenticated");

        var error = validateId(teamId, "teamId");
        if (error != null) return error;

        try {
            var worldId = WorldId.unchecked(worldIdStr);
            String mainInstanceId = worldId.getFullMainInstance().getId();

            // Check player is not already in a team
            Optional<WTeam> existing = teamService.findActiveTeamForPlayer(worldIdStr, mainInstanceId, playerName);
            if (existing.isPresent()) {
                return bad("Already in a team: " + existing.get().getTitle());
            }

            // Verify invitation exists
            Optional<WTeam> teamOpt = teamService.findByTeamId(teamId);
            if (teamOpt.isEmpty()) return notFound("Team not found");
            WTeam team = teamOpt.get();
            if (team.getInvitation() == null || !team.getInvitation().contains(playerName)) {
                return bad("No invitation for this team");
            }

            // addMemberAtomic also removes from invitation list
            boolean added = teamService.addMemberAtomic(teamId, playerName);
            if (!added) return bad("Failed to join team");

            return teamService.findByTeamId(teamId)
                    .<ResponseEntity<?>>map(t -> ResponseEntity.ok(toTeamResponse(t)))
                    .orElseGet(() -> notFound("Team not found"));
        } catch (Exception e) {
            log.error("Failed to accept invitation for player {}: {}", playerName, e.getMessage(), e);
            return bad(e.getMessage());
        }
    }

    /**
     * Decline an invitation.
     * DELETE /control/player/team/decline/{teamId}
     */
    @DeleteMapping("/decline/{teamId}")
    public ResponseEntity<?> declineInvitation(HttpServletRequest request, @PathVariable String teamId) {
        String playerName = getPlayerName(request);
        if (playerName == null) return bad("Not authenticated");

        var error = validateId(teamId, "teamId");
        if (error != null) return error;

        try {
            boolean removed = teamService.removeInvitationAtomic(teamId, playerName);
            if (!removed) return bad("No invitation found");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to decline invitation for player {}: {}", playerName, e.getMessage(), e);
            return bad(e.getMessage());
        }
    }

    /**
     * Invite one or more players to my team by character name (comma-separated).
     * Each character is looked up in the current region to resolve the full playerId.
     * POST /control/player/team/invite
     */
    @PostMapping("/invite")
    public ResponseEntity<?> invitePlayer(HttpServletRequest request, @RequestBody InvitePlayerRequest body) {
        String playerName = getPlayerName(request);
        String worldIdStr = getWorldId(request);
        if (playerName == null || worldIdStr == null) return bad("Not authenticated");

        if (body.playerName() == null || body.playerName().isBlank()) {
            return bad("playerName is required");
        }

        try {
            var worldId = WorldId.unchecked(worldIdStr);
            String mainInstanceId = worldId.getFullMainInstance().getId();
            String regionId = worldId.getRegionId();

            // Find my team
            Optional<WTeam> teamOpt = teamService.findActiveTeamForPlayer(worldIdStr, mainInstanceId, playerName);
            if (teamOpt.isEmpty()) {
                return bad("Not in a team");
            }
            WTeam team = teamOpt.get();

            // Split comma-separated names
            String[] names = body.playerName().split(",");
            List<String> invited = new java.util.ArrayList<>();
            List<String> failed = new java.util.ArrayList<>();

            for (String rawName : names) {
                String characterName = rawName.trim();
                if (characterName.isEmpty()) continue;

                String targetPlayer = resolvePlayerId(characterName, regionId);
                if (targetPlayer == null) {
                    failed.add(characterName + ": not found");
                    continue;
                }
                if (targetPlayer.equals(playerName)) {
                    failed.add(characterName + ": cannot invite yourself");
                    continue;
                }
                if (team.getMembers().contains(targetPlayer)) {
                    failed.add(characterName + ": already a member");
                    continue;
                }

                boolean ok = teamService.addInvitationAtomic(team.getTeamId(), targetPlayer);
                if (ok) {
                    invited.add(characterName);
                } else {
                    failed.add(characterName + ": already invited");
                }
            }

            return ResponseEntity.ok(new InviteResultResponse(invited, failed));
        } catch (Exception e) {
            log.error("Failed to invite player for {}: {}", playerName, e.getMessage(), e);
            return bad(e.getMessage());
        }
    }

    /**
     * Revoke an invitation by character name.
     * DELETE /control/player/team/uninvite/{characterName}
     */
    @DeleteMapping("/uninvite/{characterName}")
    public ResponseEntity<?> uninvitePlayer(HttpServletRequest request, @PathVariable String characterName) {
        String playerName = getPlayerName(request);
        String worldIdStr = getWorldId(request);
        if (playerName == null || worldIdStr == null) return bad("Not authenticated");

        try {
            var worldId = WorldId.unchecked(worldIdStr);
            String mainInstanceId = worldId.getFullMainInstance().getId();
            String regionId = worldId.getRegionId();

            String targetPlayer = resolvePlayerId(characterName, regionId);
            if (targetPlayer == null) {
                return bad("Character not found: " + characterName);
            }

            Optional<WTeam> teamOpt = teamService.findActiveTeamForPlayer(worldIdStr, mainInstanceId, playerName);
            if (teamOpt.isEmpty()) {
                return bad("Not in a team");
            }

            boolean removed = teamService.removeInvitationAtomic(teamOpt.get().getTeamId(), targetPlayer);
            if (!removed) return bad("No invitation found for this player");

            return teamService.findByTeamId(teamOpt.get().getTeamId())
                    .<ResponseEntity<?>>map(t -> ResponseEntity.ok(toTeamResponse(t)))
                    .orElseGet(() -> notFound("Team not found"));
        } catch (Exception e) {
            log.error("Failed to uninvite player for {}: {}", playerName, e.getMessage(), e);
            return bad(e.getMessage());
        }
    }

    /**
     * Kick a member from my team by character name.
     * DELETE /control/player/team/kick/{characterName}
     */
    @DeleteMapping("/kick/{characterName}")
    public ResponseEntity<?> kickMember(HttpServletRequest request, @PathVariable String characterName) {
        String playerName = getPlayerName(request);
        String worldIdStr = getWorldId(request);
        if (playerName == null || worldIdStr == null) return bad("Not authenticated");

        try {
            var worldId = WorldId.unchecked(worldIdStr);
            String mainInstanceId = worldId.getFullMainInstance().getId();
            String regionId = worldId.getRegionId();

            // Resolve character name to full playerId
            String targetPlayer = resolvePlayerId(characterName, regionId);
            if (targetPlayer == null) {
                return bad("Character not found: " + characterName);
            }

            // Find my team
            Optional<WTeam> teamOpt = teamService.findActiveTeamForPlayer(worldIdStr, mainInstanceId, playerName);
            if (teamOpt.isEmpty()) {
                return bad("Not in a team");
            }

            // Cannot kick yourself (use leave instead)
            if (targetPlayer.equals(playerName)) {
                return bad("Use leave to remove yourself");
            }

            WTeam team = teamOpt.get();
            if (!team.getMembers().contains(targetPlayer)) {
                return bad("Player is not a member of your team");
            }

            teamService.removeMemberAtomic(team.getTeamId(), targetPlayer);

            return teamService.findByTeamId(team.getTeamId())
                    .<ResponseEntity<?>>map(t -> ResponseEntity.ok(toTeamResponse(t)))
                    .orElseGet(() -> notFound("Team not found"));
        } catch (Exception e) {
            log.error("Failed to kick member for player {}: {}", playerName, e.getMessage(), e);
            return bad(e.getMessage());
        }
    }
}
