package de.mhus.nimbus.tools.generatets.ts;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Root model of all parsed TypeScript files.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TsModel {

    private List<TsSourceFile> files = new ArrayList<>();

    public List<TsSourceFile> getFiles() {
        return files;
    }

    public void setFiles(List<TsSourceFile> files) {
        this.files = files;
    }

    public void addFile(TsSourceFile f) {
        this.files.add(f);
    }
}
