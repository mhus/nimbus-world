package de.mhus.nimbus.world.shared.workflow;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class NoteRecord implements JournalStringRecord {

    private String note;

    @Override
    public String entryToString() {
        return note;
    }

    @Override
    public void stringToRecord(String data) {
        note = data;
    }
}
