package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.world.shared.access.AccessValidator;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.team.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing Teams.
 * Route: /control/teams
 */
@RestController
@RequestMapping("/control/teams")
@RequiredArgsConstructor
public class WTeamController extends BaseEditorController {

    private final WTeamService teamService;
    private final AccessValidator accessValidator;

    private ResponseEntity<?> checkTeamAccess(String teamId, HttpServletRequest request) {
        var team = teamService.findByTeamId(teamId);
        if (team.isPresent() && !accessValidator.hasEditorAccess(request, team.get().getWorldId()))
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        return null;
    }

    // DTOs
    public record TeamResponse(
            String id,
            String worldId,
            String teamId,
            String title,
            List<String> members,
            List<String> invitation,
            WTeamStatus status,
            Map<String, String> parameters,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record TeamCreateRequest(
            String worldId,
            String title,
            String creatorPlayerName
    ) {}

    public record TeamUpdateRequest(
            String title,
            Map<String, String> parameters
    ) {}

    public record TeamMemberRequest(
            String playerName
    ) {}

    public record TeamEmigrateRequest(
            String instanceWorldId
    ) {}

    private TeamResponse toResponse(WTeam team) {
        return new TeamResponse(
                team.getId(),
                team.getWorldId(),
                team.getTeamId(),
                team.getTitle(),
                team.getMembers() != null ? team.getMembers() : List.of(),
                team.getInvitation() != null ? team.getInvitation() : List.of(),
                team.getStatus() != null ? team.getStatus() : WTeamStatus.LOBBY,
                team.getParameters() != null ? team.getParameters() : Map.of(),
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }

    /**
     * List teams with optional filtering.
     * GET /control/teams?worldId=...&status=...
     */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String worldId,
            @RequestParam(required = false) WTeamStatus status,
            HttpServletRequest request) {

        try {
            List<WTeam> teams;
            if (worldId != null && !worldId.isBlank()) {
                if (!accessValidator.hasEditorAccess(request, worldId))
                    return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
                teams = status != null
                        ? teamService.findByWorldIdAndStatus(worldId, status)
                        : teamService.findByWorldId(worldId);
            } else {
                teams = teamService.findByWorldId("");
            }

            // Filter results to accessible worlds
            return ResponseEntity.ok(teams.stream()
                    .filter(t -> accessValidator.hasEditorAccess(request, t.getWorldId()))
                    .map(this::toResponse).toList());
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Get team by teamId.
     * GET /control/teams/{teamId}
     */
    @GetMapping("/{teamId}")
    public ResponseEntity<?> get(@PathVariable String teamId, HttpServletRequest request) {
        var error = validateId(teamId, "teamId");
        if (error != null) return error;
        var ac = checkTeamAccess(teamId, request); if (ac != null) return ac;

        return teamService.findByTeamId(teamId)
                .<ResponseEntity<?>>map(team -> ResponseEntity.ok(toResponse(team)))
                .orElseGet(() -> notFound("Team not found: " + teamId));
    }

    /**
     * Create a new team.
     * POST /control/teams
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody TeamCreateRequest request, HttpServletRequest httpRequest) {
        if (request.worldId() == null || request.worldId().isBlank()) {
            return bad("worldId is required");
        }
        if (!accessValidator.hasEditorAccess(httpRequest, request.worldId()))
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        if (request.title() == null || request.title().isBlank()) {
            return bad("title is required");
        }
        if (request.creatorPlayerName() == null || request.creatorPlayerName().isBlank()) {
            return bad("creatorPlayerName is required");
        }

        try {
            WTeam team = teamService.createTeam(request.worldId(), request.title(), request.creatorPlayerName());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(team));
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Update team title.
     * PUT /control/teams/{teamId}
     */
    @PutMapping("/{teamId}")
    public ResponseEntity<?> update(
            @PathVariable String teamId,
            @RequestBody TeamUpdateRequest request, HttpServletRequest httpRequest) {

        var error = validateId(teamId, "teamId");
        if (error != null) return error;
        var ac = checkTeamAccess(teamId, httpRequest); if (ac != null) return ac;

        try {
            var teamOpt = teamService.findByTeamId(teamId);
            if (teamOpt.isEmpty()) return notFound("Team not found: " + teamId);

            WTeam team = teamOpt.get();
            if (request.title() != null) team.setTitle(request.title());
            if (request.parameters() != null) team.setParameters(new HashMap<>(request.parameters()));
            team.setUpdatedAt(Instant.now());
            // save via repository through service
            return ResponseEntity.ok(toResponse(teamService.save(team)));
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Delete a team.
     * DELETE /control/teams/{teamId}
     */
    @DeleteMapping("/{teamId}")
    public ResponseEntity<?> delete(@PathVariable String teamId, HttpServletRequest request) {
        var error = validateId(teamId, "teamId");
        if (error != null) return error;
        var ac = checkTeamAccess(teamId, request); if (ac != null) return ac;

        if (teamService.findByTeamId(teamId).isEmpty()) {
            return notFound("Team not found: " + teamId);
        }

        try {
            teamService.deleteTeam(teamId);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Update team status (ACTIVATE / DEACTIVATE).
     * PUT /control/teams/{teamId}/status
     */
    @PutMapping("/{teamId}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable String teamId,
            @RequestBody Map<String, String> body, HttpServletRequest request) {

        var error = validateId(teamId, "teamId");
        if (error != null) return error;
        var ac = checkTeamAccess(teamId, request); if (ac != null) return ac;

        String statusStr = body.get("status");
        if (statusStr == null || statusStr.isBlank()) {
            return bad("status is required");
        }

        try {
            WTeamStatus status = WTeamStatus.valueOf(statusStr);
            WTeam team = teamService.updateStatus(teamId, status);
            return ResponseEntity.ok(toResponse(team));
        } catch (IllegalArgumentException e) {
            return bad("Invalid status: " + statusStr);
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Add a member to a team (atomic).
     * POST /control/teams/{teamId}/members
     */
    @PostMapping("/{teamId}/members")
    public ResponseEntity<?> addMember(
            @PathVariable String teamId,
            @RequestBody TeamMemberRequest request, HttpServletRequest httpRequest) {

        var error = validateId(teamId, "teamId");
        if (error != null) return error;
        var ac = checkTeamAccess(teamId, httpRequest); if (ac != null) return ac;

        if (request.playerName() == null || request.playerName().isBlank()) {
            return bad("playerName is required");
        }

        try {
            boolean updated = teamService.addMemberAtomic(teamId, request.playerName());
            if (!updated) return notFound("Team not found or member already exists: " + teamId);
            return teamService.findByTeamId(teamId)
                    .<ResponseEntity<?>>map(t -> ResponseEntity.ok(toResponse(t)))
                    .orElseGet(() -> notFound("Team not found: " + teamId));
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Remove a member from a team (atomic).
     * DELETE /control/teams/{teamId}/members/{playerName}
     */
    @DeleteMapping("/{teamId}/members/{playerName}")
    public ResponseEntity<?> removeMember(
            @PathVariable String teamId,
            @PathVariable String playerName, HttpServletRequest request) {

        var error = validateId(teamId, "teamId");
        if (error != null) return error;
        var ac = checkTeamAccess(teamId, request); if (ac != null) return ac;

        try {
            boolean updated = teamService.removeMemberAtomic(teamId, playerName);
            if (!updated) return notFound("Team not found or member not present: " + teamId);
            return teamService.findByTeamId(teamId)
                    .<ResponseEntity<?>>map(t -> ResponseEntity.ok(toResponse(t)))
                    .orElseGet(() -> notFound("Team not found: " + teamId));
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Add an invitation to a team (atomic).
     * POST /control/teams/{teamId}/invitations
     */
    @PostMapping("/{teamId}/invitations")
    public ResponseEntity<?> addInvitation(
            @PathVariable String teamId,
            @RequestBody TeamMemberRequest request, HttpServletRequest httpRequest) {

        var error = validateId(teamId, "teamId");
        if (error != null) return error;
        var ac = checkTeamAccess(teamId, httpRequest); if (ac != null) return ac;

        if (request.playerName() == null || request.playerName().isBlank()) {
            return bad("playerName is required");
        }

        try {
            boolean updated = teamService.addInvitationAtomic(teamId, request.playerName());
            if (!updated) return notFound("Team not found or invitation already exists: " + teamId);
            return teamService.findByTeamId(teamId)
                    .<ResponseEntity<?>>map(t -> ResponseEntity.ok(toResponse(t)))
                    .orElseGet(() -> notFound("Team not found: " + teamId));
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Remove an invitation from a team (atomic).
     * DELETE /control/teams/{teamId}/invitations/{playerName}
     */
    @DeleteMapping("/{teamId}/invitations/{playerName}")
    public ResponseEntity<?> removeInvitation(
            @PathVariable String teamId,
            @PathVariable String playerName, HttpServletRequest request) {

        var error = validateId(teamId, "teamId");
        if (error != null) return error;
        var ac = checkTeamAccess(teamId, request); if (ac != null) return ac;

        try {
            boolean updated = teamService.removeInvitationAtomic(teamId, playerName);
            if (!updated) return notFound("Team not found or invitation not present: " + teamId);
            return teamService.findByTeamId(teamId)
                    .<ResponseEntity<?>>map(t -> ResponseEntity.ok(toResponse(t)))
                    .orElseGet(() -> notFound("Team not found: " + teamId));
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Emigrate a LOBBY team to an instance world, setting status to ACTIVE.
     * POST /control/teams/{teamId}/emigrate
     */
    @PostMapping("/{teamId}/emigrate")
    public ResponseEntity<?> emigrate(
            @PathVariable String teamId,
            @RequestBody TeamEmigrateRequest request, HttpServletRequest httpRequest) {

        var error = validateId(teamId, "teamId");
        if (error != null) return error;
        var ac = checkTeamAccess(teamId, httpRequest); if (ac != null) return ac;

        if (request.instanceWorldId() == null || request.instanceWorldId().isBlank()) {
            return bad("instanceWorldId is required");
        }

        try {
            WTeam team = teamService.emigrateToInstance(teamId, request.instanceWorldId());
            return ResponseEntity.ok(toResponse(team));
        } catch (IllegalStateException e) {
            return bad(e.getMessage());
        } catch (IllegalArgumentException e) {
            return notFound(e.getMessage());
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }
}
