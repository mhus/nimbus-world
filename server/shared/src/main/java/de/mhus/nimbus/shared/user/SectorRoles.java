package de.mhus.nimbus.shared.user;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;

/**
 * Region-spezifische Rollen (umgezogen aus dem Modul 'region').
 * USER aus UniverseRoles wird hier als PLAYER abgebildet.
 */
@GenerateTypeScript("entities")
public enum SectorRoles {
    USER,
    ADMIN,
    PLAYER // deprecated -> use USER
}

