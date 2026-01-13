/*
 * Source TS: TeamData.ts
 * Original TS: 'interface TeamData'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TeamData {
    private String name;
    private String id;
    private java.util.List<TeamMember> members;
}
