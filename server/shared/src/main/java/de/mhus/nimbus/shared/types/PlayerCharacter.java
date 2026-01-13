package de.mhus.nimbus.shared.types;

import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.types.PlayerInfo;
import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@GenerateTypeScript("entities")
public class PlayerCharacter {

    @TypeScript(import_ = "PlayerInfo",importPath = "../../types/PlayerInfo")
    private PlayerInfo publicData;
    @TypeScript(import_ = "PlayerBackpack",importPath = "../../configs/EngineConfiguration")
    private PlayerBackpack backpack;

}
