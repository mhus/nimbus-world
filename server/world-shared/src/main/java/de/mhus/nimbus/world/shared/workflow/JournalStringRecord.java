package de.mhus.nimbus.world.shared.workflow;

/**
 * Marker interface for journal entry data.
 * Implementations can define specific journal entry structures.
 */
public interface JournalStringRecord extends JournalRecord {

    String entryToString();
    void stringToRecord(String data);

}
