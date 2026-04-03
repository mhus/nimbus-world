package de.mhus.nimbus.shared.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import lombok.Data;

@Data
@GenerateTypeScript("entities")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerUser {
    private String name;
    private String title;
    /** Leer (unbekannt), M (Male), F (Female), D (Diverse) */
    private String gender;
    private String portraitPath;
    private String thirdPersonModelId;
}
