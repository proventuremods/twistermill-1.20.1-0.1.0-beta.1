package com.proventure.twistermill.ponder;

import com.proventure.twistermill.blockentity.InvServoTwisterBlockEntity;
import com.proventure.twistermill.blockentity.ServoTwisterBlockEntity;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class AdcPlusSystemBlocksScenes {

    private AdcPlusSystemBlocksScenes() {
    }

    public static void adcPlusSystemBlocks(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("adc_plus_system_blocks_ponder", I18n.get("twistermill.ponder.adc_plus_system_blocks_ponder.title"));
        scene.configureBasePlate(0, 0, 10);
        scene.setSceneOffsetY(0f);
        scene.scaleSceneView(1f);
        scene.showBasePlate();
        scene.idle(10);

        scene.world().showSection(util.select().position(6, 1, 7), Direction.DOWN); // twistermill:control_table_block
        scene.idle(10);

        scene.world().showSection(util.select().position(5, 1, 7) // twistermill:redstone_in_bit_out_block
                .add(util.select().position(7, 1, 7)) // twistermill:redstone_in_bit_out_block
                .add(util.select().position(6, 1, 8)), Direction.DOWN); // twistermill:redstone_in_bit_out_block
        scene.idle(10);

        scene.world().showSection(util.select().position(6, 1, 6), Direction.DOWN); // twistermill:digital_signal_tx_block
        scene.idle(10);


        scene.overlay().showText(80)
                .text(I18n.get("twistermill.ponder.adc_plus_system_blocks_ponder.text_1"))
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().centerOf(6, 1, 7));
        scene.addKeyframe();
        scene.idle(90);

        scene.overlay().showText(80)
                .text(I18n.get("twistermill.ponder.adc_plus_system_blocks_ponder.text_2"))
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().centerOf(5, 1, 7));
        scene.idle(100);

        scene.world().showSection(util.select().position(4, 1, 7) // create:redstone_link
                .add(util.select().position(8, 1, 7)) // create:redstone_link
                .add(util.select().position(6, 1, 9)), Direction.DOWN); // create:redstone_link
        scene.idle(20);

        scene.world().showSection(util.select().position(2, 1, 3) // minecraft:oak_sign
                .add(util.select().position(4, 1, 3)) // minecraft:oak_sign
                .add(util.select().position(6, 1, 3)), Direction.DOWN); // minecraft:oak_sign
        scene.idle(10);

        scene.world().showSection(util.select().position(2, 1, 2) // create:redstone_link
                .add(util.select().position(4, 1, 2)) // create:redstone_link
                .add(util.select().position(6, 1, 2)), Direction.DOWN); // create:redstone_link
        scene.idle(15);

        scene.world().showSection(util.select().position(2, 1, 1) // create:analog_lever
                .add(util.select().position(4, 1, 1)) // create:analog_lever
                .add(util.select().position(6, 1, 1)), Direction.DOWN); // create:analog_lever
        scene.idle(10);


        scene.overlay().showLine(PonderPalette.OUTPUT, util.vector().centerOf(2, 1, 2), util.vector().centerOf(4, 1, 7), 80);
        scene.overlay().showLine(PonderPalette.OUTPUT, util.vector().centerOf(4, 1, 2), util.vector().centerOf(6, 1, 9), 80);
        scene.overlay().showLine(PonderPalette.OUTPUT, util.vector().centerOf(6, 1, 2), util.vector().centerOf(8, 1, 7), 80);
        scene.addKeyframe();
        scene.idle(10);
        scene.overlay().showText(80)
                .text(I18n.get("twistermill.ponder.adc_plus_system_blocks_ponder.text_3"))
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().centerOf(6, 1, 8));

        scene.idle(90);

        scene.addKeyframe();
        scene.idle(10);

        scene.overlay().showText(80)
                .text(I18n.get("twistermill.ponder.adc_plus_system_blocks_ponder.text_4"))
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().centerOf(6, 1, 7));
        scene.idle(90);


        scene.world().showSection(util.select().position(6, 2, 6), Direction.DOWN); // create:redstone_link
        scene.addKeyframe();
        scene.idle(10);

        scene.overlay().showText(80)
                .text(I18n.get("twistermill.ponder.adc_plus_system_blocks_ponder.text_5"))
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().centerOf(6, 1, 6));
        scene.idle(90);

        scene.world().showSection(util.select().position(4, 1, 5) // twistermill:signal_steel_block
                .add(util.select().position(4, 2, 5)), Direction.DOWN); // twistermill:signal_steel_block
        scene.idle(5);

        BlockPos servoPos = util.grid().at(4, 3, 5);
        scene.world().showSection(util.select().position(servoPos), Direction.DOWN); // twistermill:servo_twister_block
        scene.idle(10);

        applyCameraMove(scene, 160f, 10);
        scene.addKeyframe();
        scene.idle(20);

        scene.overlay().showText(80)
                .text(I18n.get("twistermill.ponder.adc_plus_system_blocks_ponder.text_6"))
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().centerOf(4, 3, 5));
        scene.idle(90);


        ElementLink<WorldSectionElement> freeTop =
                scene.world().showIndependentSection(util.select().fromTo(0, 4, 5, 4, 9, 5), Direction.DOWN);
        scene.world().configureCenterOfRotation(freeTop, new Vec3(4.5D, 3.5D, 5.5D));
        scene.addKeyframe();
        scene.idle(10);

        scene.overlay().showText(80)
                .text(I18n.get("twistermill.ponder.adc_plus_system_blocks_ponder.text_7"))
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().centerOf(1, 6, 5));
        scene.idle(90);


        rotateServoTop(scene, servoPos, 45, 20);
        scene.world().rotateSection(freeTop, 0.0D, 45.0D, 0.0D, 20);
        scene.idle(20);
        rotateServoTop(scene, servoPos, -15, 20);
        scene.world().rotateSection(freeTop, 0.0D, -15.0D, 0.0D, 20);
        scene.idle(20);
        rotateServoTop(scene, servoPos, -180, 40);
        scene.world().rotateSection(freeTop, 0.0D, -180.0D, 0.0D, 40);
        scene.idle(40);
        rotateServoTop(scene, servoPos, 150, 75);
        scene.world().rotateSection(freeTop, 0.0D, 150.0D, 0.0D, 75);
        scene.idle(95);

        applyCameraMove(scene, 360f, 60);
        scene.idle(1);
        applyCameraMove(scene, -100f, 60);
        scene.idle(10);

        scene.markAsFinished();
    }

    private static void rotateServoTop(SceneBuilder scene, BlockPos pos, float totalDelta, int ticks) {
        scene.addInstruction(new ServoTopAngleInstruction(pos, totalDelta, ticks));
    }

    private static final class ServoTopAngleInstruction extends TickingInstruction {
        private final BlockPos pos;
        private final float totalDelta;
        private float startAngle;
        private float previousAngle;

        private ServoTopAngleInstruction(BlockPos pos, float totalDelta, int ticks) {
            super(false, ticks);
            this.pos = pos;
            this.totalDelta = totalDelta;
        }

        @Override
        protected void firstTick(PonderScene scene) {
            startAngle = readCurrentAngle(scene);
            previousAngle = startAngle;
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);

            float progress = totalTicks <= 0 ? 1.0F : (totalTicks - remainingTicks) / (float) totalTicks;
            float nextAngle = startAngle + totalDelta * progress;
            writeAngle(scene, previousAngle, nextAngle);
            previousAngle = nextAngle;
        }

        private float readCurrentAngle(PonderScene scene) {
            BlockEntity blockEntity = scene.getWorld().getBlockEntity(pos);
            if (blockEntity instanceof ServoTwisterBlockEntity servo) {
                return servo.getInterpolatedAngle(1.0F);
            }
            if (blockEntity instanceof InvServoTwisterBlockEntity invServo) {
                return invServo.getInterpolatedAngle(1.0F);
            }
            return 0.0F;
        }

        private void writeAngle(PonderScene scene, float prevAngle, float angle) {
            BlockEntity blockEntity = scene.getWorld().getBlockEntity(pos);
            if (!(blockEntity instanceof ServoTwisterBlockEntity)
                    && !(blockEntity instanceof InvServoTwisterBlockEntity)) {
                return;
            }

            CompoundTag tag = blockEntity.saveWithFullMetadata(scene.getWorld().registryAccess());
            tag.putFloat("PrevAngle", prevAngle);
            tag.putFloat("Angle", angle);
            blockEntity.loadWithComponents(tag, scene.getWorld().registryAccess());
            blockEntity.setChanged();
        }
    }

    private static void applyCameraMove(SceneBuilder scene, float yRotation, int duration) {
        float step = yRotation / duration;
        for (int i = 0; i < duration; i++) {
            scene.rotateCameraY(step);
            scene.idle(1);
        }
    }
}
