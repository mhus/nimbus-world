package de.mhus.nimbus.world.shared.region;

import static org.junit.jupiter.api.Assertions.*;

import de.mhus.nimbus.shared.user.SectorRoles;
import de.mhus.nimbus.world.shared.sector.RUser;
import org.junit.jupiter.api.Test;

import de.mhus.nimbus.shared.user.RegionRoles; // neuer Import
import java.util.List;

public class RUserTest {

    @Test
    void defaultEnabledIsTrue() {
        RUser u = new RUser();
        assertTrue(u.isEnabled(), "Neuer User sollte enabled sein");
        // Standardrolle PLAYER sollte gesetzt werden nach Service-Create, hier Konstruktor alleine setzt keine Rolle
        assertTrue(u.getSectorRoles().isEmpty(), "Direkter Konstruktor setzt keine Rolle");
    }

    @Test
    void disableAndEnableWorks() {
        RUser u = new RUser();
        u.disable();
        assertFalse(u.isEnabled(), "Nach disable sollte enabled=false sein");
        u.enable();
        assertTrue(u.isEnabled(), "Nach enable sollte enabled=true sein");
    }

    @Test
    void rolesRoundtrip() {
        RUser u = new RUser();
        assertTrue(u.getSectorRoles().isEmpty());
        u.addSectorRole(SectorRoles.USER);
        assertTrue(u.hasSectorRole(SectorRoles.USER));
        u.addSectorRole(SectorRoles.ADMIN);
        assertTrue(u.hasSectorRole(SectorRoles.ADMIN));
        assertEquals(2, u.getSectorRoles().size());
        u.removeSectorRole(SectorRoles.USER);
        assertFalse(u.hasSectorRole(SectorRoles.USER));
        assertEquals(1, u.getSectorRoles().size());
    }

    @Test
    void nullEnabledTreatedAsTrue() {
        RUser u = new RUser();
        u.setEnabled(null); // simuliert alten Datensatz ohne Feld
        assertTrue(u.isEnabled(), "Null enabled soll als true interpretiert werden");
    }

    @Test
    void setRolesRawEmptyClearsRoles() {
        RUser u = new RUser();
        u.addSectorRole(SectorRoles.USER);
        assertFalse(u.getSectorRoles().isEmpty());
        u.setSectorRolesRaw(" ");
        assertTrue(u.getSectorRoles().isEmpty());
    }

    @Test
    void setRolesRawParsesValidAndIgnoresInvalid() {
        RUser u = new RUser();
        u.setSectorRolesRaw("ADMIN,INVALID,ADMIN");
        assertTrue(u.hasSectorRole(SectorRoles.ADMIN));
        assertFalse(u.hasSectorRole(SectorRoles.USER));
        assertEquals(1, u.getSectorRoles().size(), "Duplikate und ungültige Rollen sollten entfernt/ignoriert sein");
    }

    @Test
    void regionRolesDefaultFallback() {
        RUser u = new RUser();
        // Bei NULL regionRoles sollte DEFAULT_REGION_ROLE zurückgegeben werden
        assertEquals(RegionRoles.valueOf(RUser.DEFAULT_REGION_ROLE), u.getRegionRole("testRegion"));
    }

    @Test
    void regionRolesEmptyNoFallback() {
        RUser u = new RUser();
        u.setRegionRoles(java.util.Map.of()); // Explizit leer setzen, nicht NULL
        // Bei leerer Map (nicht NULL) sollte auch DEFAULT_REGION_ROLE zurückgegeben werden
        assertEquals(RegionRoles.valueOf(RUser.DEFAULT_REGION_ROLE), u.getRegionRole("testRegion"));
    }

    @Test
    void regionRolesRoundtrip() {
        RUser u = new RUser();
        assertTrue(u.getRegionRoles().isEmpty());

        // Rolle für Region setzen
        assertTrue(u.setRegionRole("region1", RegionRoles.ADMIN));
        assertTrue(u.hasRegionRole("region1", RegionRoles.ADMIN));
        assertEquals(RegionRoles.ADMIN, u.getRegionRole("region1"));

        // Weitere Region hinzufügen
        assertTrue(u.setRegionRole("region2", RegionRoles.EDITOR));
        assertEquals(2, u.getRegionRoles().size());

        // Rolle ändern
        assertTrue(u.setRegionRole("region1", RegionRoles.EDITOR));
        assertTrue(u.hasRegionRole("region1", RegionRoles.EDITOR));
        assertFalse(u.hasRegionRole("region1", RegionRoles.ADMIN));

        // Rolle entfernen - region1 hat keine explizite Rolle mehr -> DEFAULT_REGION_ROLE
        assertTrue(u.removeRegionRole("region1"));
        // Nach Entfernen sollte DEFAULT_REGION_ROLE zurückgegeben werden
        assertEquals(RegionRoles.valueOf(RUser.DEFAULT_REGION_ROLE), u.getRegionRole("region1"));
        // hasRegionRole sollte false für EDITOR sein, aber true für DEFAULT_REGION_ROLE (PLAYER)
        assertFalse(u.hasRegionRole("region1", RegionRoles.EDITOR));
        assertTrue(u.hasRegionRole("region1", RegionRoles.PLAYER)); // DEFAULT_REGION_ROLE

        // region2 sollte noch EDITOR haben
        assertTrue(u.hasRegionRole("region2", RegionRoles.EDITOR));
        assertEquals(RegionRoles.EDITOR, u.getRegionRole("region2"));

        // RegionIds mit bestimmter Rolle finden
        List<String> editorRegions = u.getRegionIdsWithRole(RegionRoles.EDITOR);
        assertEquals(1, editorRegions.size());
        assertTrue(editorRegions.contains("region2"));

        // Alle Regionen entfernen
        assertTrue(u.removeRegionRole("region2"));
        assertEquals(0, u.getRegionRoles().size());
        // Nach dem Entfernen aller Regionen sollte regionRoles null sein
        assertTrue(u.getRegionRoles().isEmpty());
    }

    @Test
    void regionRoleNullHandling() {
        RUser u = new RUser();

        // NULL-Handling
        assertNull(u.getRegionRole(null));
        assertFalse(u.setRegionRole(null, RegionRoles.ADMIN));
        assertFalse(u.hasRegionRole(null, RegionRoles.ADMIN));
        assertFalse(u.hasRegionRole("region1", null));
        assertFalse(u.removeRegionRole(null));

        // Entfernen einer nicht existenten Region
        assertFalse(u.removeRegionRole("nonexistent"));
    }
}
