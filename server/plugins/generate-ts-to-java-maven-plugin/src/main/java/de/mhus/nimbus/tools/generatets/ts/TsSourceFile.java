package de.mhus.nimbus.tools.generatets.ts;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TsSourceFile {
    private String path;
    private List<String> imports = new ArrayList<>();
    private List<TsDeclarations.TsInterface> interfaces = new ArrayList<>();
    private List<TsDeclarations.TsEnum> enums = new ArrayList<>();
    private List<TsDeclarations.TsClass> classes = new ArrayList<>();
    private List<TsDeclarations.TsTypeAlias> typeAliases = new ArrayList<>();

    public TsSourceFile() {}

    public TsSourceFile(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<String> getImports() {
        return imports;
    }

    public List<TsDeclarations.TsInterface> getInterfaces() {
        return interfaces;
    }

    public List<TsDeclarations.TsEnum> getEnums() {
        return enums;
    }

    public List<TsDeclarations.TsClass> getClasses() {
        return classes;
    }

    public List<TsDeclarations.TsTypeAlias> getTypeAliases() {
        return typeAliases;
    }
}
