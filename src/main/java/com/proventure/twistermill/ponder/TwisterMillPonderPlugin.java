package com.proventure.twistermill.ponder;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.block.ModBlocks;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.NotNull;

public class TwisterMillPonderPlugin implements PonderPlugin {

    @Override
    public @NotNull String getModId() {
        return TwisterMill.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<DeferredHolder<?, ?>> typedHelper =
                helper.withKeyFunction(DeferredHolder::getId);
        boolean useAeronauticsServoOption7Fallback = useAeronauticsServoOption7Fallback();
        String servoOption7Schematic = useAeronauticsServoOption7Fallback
                ? "servo_option_7_simulated"
                : "servo_option_7";
        PonderStoryBoard servoOption7Storyboard = useAeronauticsServoOption7Fallback
                ? ServoOption7AeronauticsScenes::servoOption7
                : ServoOption7Scenes::servoOption7;

        typedHelper.forComponents(ModBlocks.WIND_ROTO_BLOCK)
                .addStoryBoard("wind_roto_block", WindRotoBlockScenes::windRotoBlock)
                .addStoryBoard(servoOption7Schematic, servoOption7Storyboard);

        typedHelper.forComponents(ModBlocks.CONTROL_TABLE_BLOCK)
                .addStoryBoard("windforcedirected", AdcPlusSystemBlocksScenes::adcPlusSystemBlocks)
                .addStoryBoard(servoOption7Schematic, servoOption7Storyboard);

        typedHelper.forComponents(ModBlocks.DIGITAL_SIGNAL_TX_BLOCK)
                .addStoryBoard("windforcedirected", AdcPlusSystemBlocksScenes::adcPlusSystemBlocks)
                .addStoryBoard(servoOption7Schematic, servoOption7Storyboard);

        typedHelper.forComponents(ModBlocks.INV_SERVO_TWISTER_BLOCK)
                .addStoryBoard("inv_servo_basic_blade", InvServoTwisterScenes::invServoTwister)
                .addStoryBoard("windforcedirected", AdcPlusInvServoScenes::adcPlusInvServo)
                .addStoryBoard(servoOption7Schematic, servoOption7Storyboard);

        typedHelper.forComponents(ModBlocks.SERVO_TWISTER_BLOCK)
                .addStoryBoard("servo_basic_blade", ServoTwisterScenes::servoTwister)
                .addStoryBoard("windforcedirected", AdcPlusServoScenes::adcPlusServo)
                .addStoryBoard(servoOption7Schematic, servoOption7Storyboard);

        typedHelper.forComponents(ModBlocks.REDSTONE_IN_BIT_OUT_BLOCK)
                .addStoryBoard("windforcedirected", AdcPlusSystemBlocksScenes::adcPlusSystemBlocks)
                .addStoryBoard(servoOption7Schematic, servoOption7Storyboard);

        typedHelper.forComponents(ModBlocks.WIND_ROTO_VERTICAL_BLOCK)
                .addStoryBoard("vertical_wind_vane", WindRotoVerticalBlockScenes::windRotoVerticalBlock)
                .addStoryBoard(servoOption7Schematic, servoOption7Storyboard);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderTagRegistrationHelper<DeferredHolder<?, ?>> typedHelper =
                helper.withKeyFunction(DeferredHolder::getId);

        typedHelper.addToTag(AllCreatePonderTags.DISPLAY_SOURCES)
                .add(ModBlocks.WIND_ROTO_BLOCK)
                .add(ModBlocks.WIND_ROTO_VERTICAL_BLOCK)
                .add(ModBlocks.SERVO_TWISTER_BLOCK)
                .add(ModBlocks.INV_SERVO_TWISTER_BLOCK);

        typedHelper.addToTag(AllCreatePonderTags.KINETIC_APPLIANCES, AllCreatePonderTags.MOVEMENT_ANCHOR)
                .add(ModBlocks.WIND_ROTO_BLOCK)
                .add(ModBlocks.WIND_ROTO_VERTICAL_BLOCK)
                .add(ModBlocks.SERVO_TWISTER_BLOCK)
                .add(ModBlocks.INV_SERVO_TWISTER_BLOCK);
    }

    private static boolean useAeronauticsServoOption7Fallback() {
        ModList modList = ModList.get();
        return modList.isLoaded("simulated") || modList.isLoaded("aeronautics_bundled");
    }
}
