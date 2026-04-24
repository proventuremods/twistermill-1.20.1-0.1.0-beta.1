package com.proventure.twistermill.client.ponder;

import com.proventure.twistermill.block.ModBlocks;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

public class TwisterMillPonderScenes {

    private TwisterMillPonderScenes() {
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<Block> blockHelper = helper.withKeyFunction(ForgeRegistries.BLOCKS::getKey);

        blockHelper
                .forComponents(ModBlocks.WIND_ROTO_BLOCK.get())
                .addStoryBoard("twistermill/windroto_ponder", WindRotoPonderScenes::windRotoBlock);

        blockHelper
                .forComponents(ModBlocks.WIND_ROTO_VERTICAL_BLOCK.get())
                .addStoryBoard("twistermill/windvane_ponder", WindRotoVerticalPonderScenes::windRotoVerticalBlock);

        blockHelper
                .forComponents(ModBlocks.SERVO_TWISTER_BLOCK.get())
                .addStoryBoard("twistermill/servo_ponder", ServoPonderScenes::servoTwisterBlock);

        blockHelper
                .forComponents(ModBlocks.INV_SERVO_TWISTER_BLOCK.get())
                .addStoryBoard("twistermill/invservo_ponder", InvServoPonderScenes::invServoTwisterBlock);
    }
}
