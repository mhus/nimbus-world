package de.mhus.nimbus.tools.generatets.java;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JavaProperty {

    private String name;
    private String type; // Java type name
    private boolean optional;
    private String visibility; // public | private | protected | null

    public JavaProperty() {}

    public JavaProperty(String name, String type, boolean optional, String visibility) {
        this.name = name;
        this.type = type;
        this.optional = optional;
        this.visibility = visibility;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isOptional() { return optional; }
    public void setOptional(boolean optional) { this.optional = optional; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
}
