package de.mhus.nimbus.shared.types;

import de.mhus.nimbus.generated.configs.Settings;
import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;

@GenerateTypeScript("entities")
public record PlayerData(
        @TypeScript(import_ = "PlayerUser",importPath = "./PlayerUser")
        PlayerUser user,
        @TypeScript(import_ = "PlayerCharacter",importPath = "./PlayerCharacter")
        PlayerCharacter character,
        @TypeScript(import_ = "Settings",importPath = "../../configs/EngineConfiguration")
        Settings settings
) {
}
