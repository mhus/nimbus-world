/*
 * Source TS: ServerEntitySpawnDefinition.ts
 * Original TS: 'interface ServerEntitySpawnDefinitionPhysicsStateDTO'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ServerEntitySpawnDefinitionPhysicsStateDTO {
    private Vector3 position;
    private Vector3 velocity;
    private Rotation rotation;
    private boolean grounded;
}
