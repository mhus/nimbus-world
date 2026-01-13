import {AppContext} from "../AppContext";
import {CommandService} from "../services/CommandService";
import {HelpCommand} from "./HelpCommand";
import {InfoCommand} from "./InfoCommand";
import {WorldInfoCommand} from "./WorldInfoCommand";
import {ClearCommand} from "./ClearCommand";
import {ReloadConfigCommand} from "./ReloadConfigCommand";
import {ReloadWorldConfigCommand} from "./ReloadWorldConfigCommand";
import {RedrawChunkCommand} from "./RedrawChunkCommand";
import {SendCommand} from "./SendCommand";
import {AudioCommand} from "./AudioCommand";
import {TestAudioCommand} from "./TestAudioCommand";
import {StepVolumeCommand} from "./StepVolumeCommand";
import {PlayAmbientAudioCommand} from "./PlayAmbientAudioCommand";
import {EnvironmentAmbientAudioCommand} from "./EnvironmentAmbientAudioCommand";
import {SetAmbientVolumeCommand} from "./SetAmbientVolumeCommand";
import {PlaySoundCommand} from "./PlaySoundCommand";
import {PlaySoundAtPositionCommand} from "./PlaySoundAtPositionCommand";
import {PlayEntityAudioCommand} from "./PlayEntityAudioCommand";
import {SpeakCommand} from "./SpeakCommand";
import {SetSpeechVolumeCommand} from "./SetSpeechVolumeCommand";
import {RegisterFlashSoundsCommand} from "./RegisterFlashSoundsCommand";
import {NotificationCommand} from "./NotificationCommand";
import {SplashScreenCommand} from "./SplashScreenCommand";
import {ShowTeamCommand} from "./ShowTeamCommand";
import {SetPlayerInfoCommand} from "./SetPlayerInfoCommand";
import {SetShortcutCommand} from "./SetShortcutCommand";
import {StatusEffectCommand} from "./StatusEffectCommand";
import {VitalsCommand} from "./VitalsCommand";
import {OpenComponentCommand} from "./OpenComponentCommand";
import {SetSelectedEditBlockCommand} from "./SetSelectedEditBlockCommand";
import {GetSelectedEditBlockCommand} from "./GetSelectedEditBlockCommand";
import {PlayerPositionInfoCommand} from "./PlayerPositionInfoCommand";
import {SelectedBlockInfoCommand} from "./SelectedBlockInfoCommand";
import {ShortcutInfoCommand} from "./ShortcutInfoCommand";
import {MaterialInfoCommand} from "./MaterialInfoCommand";
import {WireframeCommand} from "./WireframeCommand";
import {UnderwaterCommand} from "./UnderwaterCommand";
import {FogCommand} from "./FogCommand";
import {FlashImageCommand} from "./FlashImageCommand";
import {CenterTextCommand} from "./CenterTextCommand";
import {LogLevelCommand} from "./LogLevelCommand";
import {RedirectCommand} from "./RedirectCommand";
import {BlockInfoCommand} from "./BlockInfoCommand";
import {BlockTypeInfoCommand} from "./BlockTypeInfoCommand";
import {TeleportCommand} from "./TeleportCommand";
import {WebGLCheckCommand} from "./WebGLCheckCommand";
import {ListEntitiesCommand} from "./ListEntitiesCommand";
import {EntityInfoCommand} from "./EntityInfoCommand";
import {SpawnEntityCommand} from "./SpawnEntityCommand";
import {SetEntityStatusCommand} from "./SetEntityStatusCommand";
import {ToggleEntityPathwaysCommand} from "./ToggleEntityPathwaysCommand";
import {WindDirectionCommand, WindGustStrengthCommand, WindStrengthCommand, WindSwayFactorCommand} from "./wind";
import {
    AmbientLightDiffuseCommand,
    AmbientLightGroundColorCommand,
    AmbientLightIntensityCommand,
    AmbientLightSpecularCommand
} from "./ambientLight";
import {
    SunLightDiffuseCommand,
    SunLightDirectionCommand,
    SunLightIntensityCommand,
    SunLightSpecularCommand
} from "./sunLight";
import {
    ShadowsDebugCommand,
    ShadowsDistanceCommand,
    ShadowsEnableCommand, ShadowsEngineInfoCommand,
    ShadowsInfoCommand,
    ShadowsIntensityCommand,
    ShadowsQualityCommand,
    ShadowsRefreshCommand
} from "./shadows";
import {
    AmbientLightIntensityMultiplierCommand,
    AutomaticSunAdjustmentCommand,
    SunColorCommand,
    SunElevationCommand,
    SunEnableCommand,
    SunLensFlareColorCommand,
    SunLensFlareEnableCommand,
    SunLensFlareIntensityCommand,
    SunLightIntensityMultiplierCommand,
    SunPositionCommand,
    SunSizeCommand,
    SunTextureCommand
} from "./sun";
import {
    SkyBoxColorCommand,
    SkyBoxEnableCommand,
    SkyBoxRotationCommand,
    SkyBoxSizeCommand, SkyBoxStartCommand,
    SkyBoxTextureCommand
} from "./skybox";
import {
    MoonDistanceCommand,
    MoonElevationCommand,
    MoonEnableCommand, MoonPhaseCommand,
    MoonPositionCommand,
    MoonSizeCommand, MoonTextureCommand
} from "./moon";
import {
    CloudAddCommand,
    CloudClearCommand,
    CloudDirectionCommand,
    CloudEnableCommand, CloudListCommand, CloudPositionCommand,
    CloudRemoveCommand, CloudsAnimationStartCommand, CloudsAnimationStopCommand, CloudSizeCommand,
    CloudSpeedCommand
} from "./clouds";
import {
    HorizonGradientAlphaCommand,
    HorizonGradientColor0Command, HorizonGradientColor1Command,
    HorizonGradientDistanceCommand,
    HorizonGradientEnableCommand, HorizonGradientHeightCommand,
    HorizonGradientPositionCommand,
    HorizonGradientIlluminationColorCommand,
    HorizonGradientIlluminationStrengthCommand
} from "./horizonGradient";
import {
    LightningCommand,
    PrecipitationEnableCommand, PrecipitationIntensityCommand,
    PrecipitationStartCommand,
    PrecipitationStopCommand, PrecipitationTypeCommand
} from "./precipitation";
import {
    ScrawlActionCommand,
    ScrawlListCommand, ScrawlPauseCommand, ScrawlResumeCommand,
    ScrawlScriptCommand,
    ScrawlSelectedActionCommand,
    ScrawlStartCommand, ScrawlStatusCommand, ScrawlStopCommand
} from "./scrawl";
import {GetStackModifierCurrentValueCommand, ListStacksCommand, SetStackModifierCommand} from "./stack";
import {CreateEnvironmentScriptCommand} from "./CreateEnvironmentScriptCommand";
import {DeleteEnvironmentScriptCommand} from "./DeleteEnvironmentScriptCommand";
import {StartEnvironmentScriptCommand} from "./StartEnvironmentScriptCommand";
import {StopEnvironmentScriptCommand} from "./StopEnvironmentScriptCommand";
import {GetCurrentEnvironmentScriptCommand} from "./GetCurrentEnvironmentScriptCommand";
import {ListEnvironmentScriptsCommand} from "./ListEnvironmentScriptsCommand";
import {ResetEnvironmentCommand} from "./ResetEnvironmentCommand";
import {StartEnvironmentCommand} from "./StartEnvironmentCommand";
import {WorldTimeConfigCommand} from "./WorldTimeConfigCommand";
import {WorldTimeStartCommand} from "./WorldTimeStartCommand";
import {WorldTimeStopCommand} from "./WorldTimeStopCommand";
import {WorldTimeInfoCommand} from "./WorldTimeInfoCommand";
import {getLogger} from "@nimbus/shared";
import {ClearBlockTypeCacheCommand} from "./ClearBlockTypeCacheCommand";
import {SetAutoSelectCommand} from "./SetAutoSelectCommand";
import {ModelSelectorCommand} from "./ModelSelectorCommand";
import {
    CameraLightEnableCommand,
    CameraLightIntensityCommand,
    CameraLightRangeCommand,
    CameraLightInfoCommand
} from "./camera";

// Initialize logger (basic setup before ClientService)
const logger = getLogger('CommandsFactory');

export class CommandsFactory {


    static createCommands(appContext:AppContext) {

        // Initialize CommandService (available in both EDITOR and VIEWER modes)
        logger.debug('Initializing CommandService...');
        const commandService = new CommandService(appContext);
        appContext.services.command = commandService;

        // Register command handlers
        commandService.registerHandler(new HelpCommand(commandService));
        commandService.registerHandler(new InfoCommand(appContext));
        commandService.registerHandler(new WorldInfoCommand(appContext));
        commandService.registerHandler(new ClearCommand());
        commandService.registerHandler(new ReloadConfigCommand(appContext));
        commandService.registerHandler(new ReloadWorldConfigCommand(appContext));
        commandService.registerHandler(new RedrawChunkCommand(appContext));
        commandService.registerHandler(new SendCommand(commandService));
        commandService.registerHandler(new AudioCommand(appContext));
        commandService.registerHandler(new TestAudioCommand(appContext));
        commandService.registerHandler(new StepVolumeCommand(appContext));
        commandService.registerHandler(new PlayAmbientAudioCommand(appContext));
        commandService.registerHandler(new EnvironmentAmbientAudioCommand(appContext));
        commandService.registerHandler(new SetAmbientVolumeCommand(appContext));
        commandService.registerHandler(new PlaySoundCommand(appContext));
        commandService.registerHandler(new PlaySoundAtPositionCommand(appContext));
        commandService.registerHandler(new PlayEntityAudioCommand(appContext));
        commandService.registerHandler(new SpeakCommand(appContext));
        commandService.registerHandler(new SetSpeechVolumeCommand(appContext));
        commandService.registerHandler(new RegisterFlashSoundsCommand(appContext));
        commandService.registerHandler(new NotificationCommand(appContext));
        commandService.registerHandler(new SplashScreenCommand(appContext));
        commandService.registerHandler(new ShowTeamCommand(appContext));
        commandService.registerHandler(new SetPlayerInfoCommand(appContext));
        commandService.registerHandler(new SetShortcutCommand(appContext));
        commandService.registerHandler(new StatusEffectCommand(appContext));
        commandService.registerHandler(new VitalsCommand(appContext));
        commandService.registerHandler(new OpenComponentCommand(appContext));
        commandService.registerHandler(new SetSelectedEditBlockCommand(appContext));
        commandService.registerHandler(new GetSelectedEditBlockCommand(appContext));
        commandService.registerHandler(new PlayerPositionInfoCommand(appContext));
        commandService.registerHandler(new SelectedBlockInfoCommand(appContext));
        commandService.registerHandler(new ShortcutInfoCommand(appContext));
        commandService.registerHandler(new MaterialInfoCommand(appContext));
        commandService.registerHandler(new WireframeCommand(appContext));
        commandService.registerHandler(new UnderwaterCommand(appContext));
        commandService.registerHandler(new FogCommand(appContext));
        commandService.registerHandler(new FlashImageCommand(appContext));
        commandService.registerHandler(new CenterTextCommand(appContext));
        commandService.registerHandler(new LogLevelCommand());
        commandService.registerHandler(new RedirectCommand());
        commandService.registerHandler(new BlockInfoCommand(appContext));
        commandService.registerHandler(new BlockTypeInfoCommand(appContext));
        commandService.registerHandler(new TeleportCommand(appContext));
        commandService.registerHandler(new WebGLCheckCommand(appContext));

        // Register entity commands
        commandService.registerHandler(new ListEntitiesCommand(appContext));
        commandService.registerHandler(new EntityInfoCommand(appContext));
        commandService.registerHandler(new SpawnEntityCommand(appContext));
        commandService.registerHandler(new SetEntityStatusCommand(appContext));
        commandService.registerHandler(new ToggleEntityPathwaysCommand(appContext));

        // Register wind commands
        commandService.registerHandler(new WindDirectionCommand(appContext));
        commandService.registerHandler(new WindStrengthCommand(appContext));
        commandService.registerHandler(new WindGustStrengthCommand(appContext));
        commandService.registerHandler(new WindSwayFactorCommand(appContext));

        // Register ambient light commands
        commandService.registerHandler(new AmbientLightIntensityCommand(appContext));
        commandService.registerHandler(new AmbientLightDiffuseCommand(appContext));
        commandService.registerHandler(new AmbientLightSpecularCommand(appContext));
        commandService.registerHandler(new AmbientLightGroundColorCommand(appContext));

        // Register sun light commands
        commandService.registerHandler(new SunLightIntensityCommand(appContext));
        commandService.registerHandler(new SunLightDirectionCommand(appContext));
        commandService.registerHandler(new SunLightDiffuseCommand(appContext));
        commandService.registerHandler(new SunLightSpecularCommand(appContext));

        // Register shadow commands
        commandService.registerHandler(new ShadowsEnableCommand(appContext));
        commandService.registerHandler(new ShadowsIntensityCommand(appContext));
        commandService.registerHandler(new ShadowsQualityCommand(appContext));
        commandService.registerHandler(new ShadowsInfoCommand(appContext));
        commandService.registerHandler(new ShadowsRefreshCommand(appContext));
        commandService.registerHandler(new ShadowsDistanceCommand(appContext));
        commandService.registerHandler(new ShadowsDebugCommand(appContext));
        commandService.registerHandler(new ShadowsEngineInfoCommand(appContext));

        // Register sun visualization commands
        commandService.registerHandler(new SunEnableCommand(appContext));
        commandService.registerHandler(new SunPositionCommand(appContext));
        commandService.registerHandler(new SunElevationCommand(appContext));
        commandService.registerHandler(new SunColorCommand(appContext));
        commandService.registerHandler(new SunTextureCommand(appContext));
        commandService.registerHandler(new SunSizeCommand(appContext));
        commandService.registerHandler(new SunLensFlareEnableCommand(appContext));
        commandService.registerHandler(new SunLensFlareIntensityCommand(appContext));
        commandService.registerHandler(new SunLensFlareColorCommand(appContext));
        commandService.registerHandler(new AutomaticSunAdjustmentCommand(appContext));
        commandService.registerHandler(new SunLightIntensityMultiplierCommand(appContext));
        commandService.registerHandler(new AmbientLightIntensityMultiplierCommand(appContext));

        // Register skybox commands
        commandService.registerHandler(new SkyBoxEnableCommand(appContext));
        commandService.registerHandler(new SkyBoxColorCommand(appContext));
        commandService.registerHandler(new SkyBoxTextureCommand(appContext));
        commandService.registerHandler(new SkyBoxSizeCommand(appContext));
        commandService.registerHandler(new SkyBoxRotationCommand(appContext));
        commandService.registerHandler(new SkyBoxStartCommand(appContext));

        // Register moon commands
        commandService.registerHandler(new MoonEnableCommand(appContext));
        commandService.registerHandler(new MoonSizeCommand(appContext));
        commandService.registerHandler(new MoonPositionCommand(appContext));
        commandService.registerHandler(new MoonElevationCommand(appContext));
        commandService.registerHandler(new MoonDistanceCommand(appContext));
        commandService.registerHandler(new MoonPhaseCommand(appContext));
        commandService.registerHandler(new MoonTextureCommand(appContext));

        // Register cloud visualization commands
        commandService.registerHandler(new CloudAddCommand(appContext));
        commandService.registerHandler(new CloudRemoveCommand(appContext));
        commandService.registerHandler(new CloudClearCommand(appContext));
        commandService.registerHandler(new CloudEnableCommand(appContext));
        commandService.registerHandler(new CloudSpeedCommand(appContext));
        commandService.registerHandler(new CloudDirectionCommand(appContext));
        commandService.registerHandler(new CloudPositionCommand(appContext));
        commandService.registerHandler(new CloudSizeCommand(appContext));
        commandService.registerHandler(new CloudListCommand(appContext));
        commandService.registerHandler(new CloudsAnimationStartCommand(appContext));
        commandService.registerHandler(new CloudsAnimationStopCommand(appContext));

        // Register horizon gradient commands
        commandService.registerHandler(new HorizonGradientEnableCommand(appContext));
        commandService.registerHandler(new HorizonGradientDistanceCommand(appContext));
        commandService.registerHandler(new HorizonGradientPositionCommand(appContext));
        commandService.registerHandler(new HorizonGradientHeightCommand(appContext));
        commandService.registerHandler(new HorizonGradientColor0Command(appContext));
        commandService.registerHandler(new HorizonGradientColor1Command(appContext));
        commandService.registerHandler(new HorizonGradientAlphaCommand(appContext));
        commandService.registerHandler(new HorizonGradientIlluminationColorCommand(appContext));
        commandService.registerHandler(new HorizonGradientIlluminationStrengthCommand(appContext));

        // Register precipitation commands
        commandService.registerHandler(new PrecipitationStartCommand(appContext));
        commandService.registerHandler(new PrecipitationStopCommand(appContext));
        commandService.registerHandler(new LightningCommand(appContext));
        commandService.registerHandler(new PrecipitationEnableCommand(appContext));
        commandService.registerHandler(new PrecipitationIntensityCommand(appContext));
        commandService.registerHandler(new PrecipitationTypeCommand(appContext));

        // Register scrawl commands
        commandService.registerHandler(new ScrawlListCommand(appContext));
        commandService.registerHandler(new ScrawlStartCommand(appContext));
        commandService.registerHandler(new ScrawlActionCommand(appContext));
        commandService.registerHandler(new ScrawlScriptCommand(appContext));
        commandService.registerHandler(new ScrawlSelectedActionCommand(appContext));
        commandService.registerHandler(new ScrawlStopCommand(appContext));

        // Register stack modifier commands
        commandService.registerHandler(new SetStackModifierCommand(appContext));
        commandService.registerHandler(new GetStackModifierCurrentValueCommand(appContext));
        commandService.registerHandler(new ListStacksCommand(appContext));
        commandService.registerHandler(new ScrawlStatusCommand(appContext));
        commandService.registerHandler(new ScrawlPauseCommand(appContext));
        commandService.registerHandler(new ScrawlResumeCommand(appContext));

        // Register environment script commands
        commandService.registerHandler(new CreateEnvironmentScriptCommand(appContext));
        commandService.registerHandler(new DeleteEnvironmentScriptCommand(appContext));
        commandService.registerHandler(new StartEnvironmentScriptCommand(appContext));
        commandService.registerHandler(new StopEnvironmentScriptCommand(appContext));
        commandService.registerHandler(new GetCurrentEnvironmentScriptCommand(appContext));
        commandService.registerHandler(new ListEnvironmentScriptsCommand(appContext));
        commandService.registerHandler(new ResetEnvironmentCommand(appContext));
        commandService.registerHandler(new StartEnvironmentCommand(appContext));

        // Register World Time commands
        commandService.registerHandler(new WorldTimeConfigCommand(appContext));
        commandService.registerHandler(new WorldTimeStartCommand(appContext));
        commandService.registerHandler(new WorldTimeStopCommand(appContext));
        commandService.registerHandler(new WorldTimeInfoCommand(appContext));

        // Register block type cache clearing command
        commandService.registerHandler(new ClearBlockTypeCacheCommand(appContext));

        // Register auto-select mode command
        commandService.registerHandler(new SetAutoSelectCommand(appContext));

        // Register model selector command
        commandService.registerHandler(new ModelSelectorCommand(appContext));

        // Register camera light commands
        commandService.registerHandler(new CameraLightEnableCommand(appContext));
        commandService.registerHandler(new CameraLightIntensityCommand(appContext));
        commandService.registerHandler(new CameraLightRangeCommand(appContext));
        commandService.registerHandler(new CameraLightInfoCommand(appContext));

        logger.debug('CommandService initialized with commands');

    }
}