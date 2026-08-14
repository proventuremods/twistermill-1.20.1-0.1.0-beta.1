package com.proventure.twistermill.ponder;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.item.ModItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public class ServoOption7AeronauticsScenes {

    private ServoOption7AeronauticsScenes() {
    }

    public static void servoOption7(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("servo_option_7_ponder", "Servo Option 7");
        scene.configureBasePlate(0, 0, 14);
        scene.setSceneOffsetY(0f);
        scene.scaleSceneView(0.65f);

        applyCameraMove(scene, -90f, 6);
        scene.idle(20);

        Selection wrbTopCoreSelection = util.select().position(6, 8, 3)
                .add(util.select().position(6, 8, 2));

        scene.world().showSection(util.select().everywhere(), Direction.DOWN);
        scene.addInstruction(PonderInstruction.simple(ponderScene ->
                ponderScene.addElement(SableSubLevelPonderElement.fromSchematic(
                ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "servo_option_7_simulated")))));
        scene.world().setBlocks(wrbTopCoreSelection, Blocks.AIR.defaultBlockState(), false);
        scene.idle(6);

        scene.overlay().showText(140)
                .text(I18n.get("twistermill.ponder.servo_option_7_ponder.text_1"))
                .pointAt(util.vector().centerOf(6, 2, 7))
                .placeNearTarget();
        scene.idle(145);

        scene.addKeyframe();
        scene.idle(10);

        scene.overlay().showText(160)
                .text(I18n.get("twistermill.ponder.servo_option_7_ponder.text_2"))
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().centerOf(6, 5, 7))
                .placeNearTarget();
        scene.idle(165);

        applyCameraMove(scene, -195f, 20);
        scene.idle(22);

        scene.overlay().showOutline(PonderPalette.BLUE, "servo_top_outline", util.select().position(6, 8, 2), 25);
        scene.idle(20);

        scene.addKeyframe();
        scene.idle(2);

        scene.overlay().showOutlineWithText(util.select().position(6, 8, 2), 100)
                .text(I18n.get("twistermill.ponder.servo_option_7_ponder.text_3"))
                .pointAt(util.vector().centerOf(6, 8, 2))
                .placeNearTarget();
        scene.idle(105);

        scene.overlay().showLine(PonderPalette.GREEN, util.vector().centerOf(6, 8, 2), util.vector().centerOf(8, 8, 2), 70);
        scene.addKeyframe();
        scene.idle(75);

        scene.overlay().showLine(PonderPalette.GREEN, util.vector().centerOf(6, 8, 2), util.vector().centerOf(4, 8, 2), 70);
        scene.idle(75);

        scene.addKeyframe();
        scene.idle(2);

        scene.addInstruction(PonderInstruction.simple(ponderScene ->
                ponderScene.forEach(SableSubLevelPonderElement.class, SableSubLevelPonderElement::startPitchDemo)));
        scene.idle(2);
        scene.overlay().showText(50)
                .text(I18n.get("twistermill.ponder.servo_option_7_ponder.text_4"))
                .pointAt(util.vector().centerOf(4, 8, 2))
                .placeNearTarget();
        scene.idle(55);

        scene.addKeyframe();
        scene.idle(2);

        scene.overlay().showText(90)
                .text(I18n.get("twistermill.ponder.servo_option_7_ponder.text_5"))
                .pointAt(util.vector().centerOf(6, 8, 3))
                .placeNearTarget();
        scene.idle(95);

        scene.overlay().showControls(
                util.vector().blockSurface(util.grid().at(6, 8, 4), Direction.UP),
                Pointing.DOWN,
                60
        ).rightClick().withItem(new ItemStack(ModItems.BINDING_STICK.get()));
        scene.idle(65);

        scene.addKeyframe();
        scene.idle(2);

        Selection wrvbYawStaticSelection = util.select().position(6, 3, 7)
                .add(util.select().position(6, 4, 7))
                .add(util.select().position(6, 5, 7))
                .add(util.select().position(6, 6, 7))
                .add(util.select().position(6, 6, 10))
                .add(util.select().position(6, 6, 11))
                .add(util.select().fromTo(6, 7, 4, 6, 7, 11))
                .add(util.select().fromTo(6, 8, 4, 6, 8, 11))
                .add(util.select().position(6, 9, 10))
                .add(util.select().position(6, 9, 11))
                .add(util.select().position(6, 10, 10))
                .add(util.select().position(6, 10, 11));

        applyCameraMove(scene, -75f, 20);

        Selection wrbKineticOutputSelection = util.select().position(6, 8, 5)
                .add(util.select().position(6, 8, 6))
                .add(util.select().position(6, 8, 7))
                .add(util.select().position(6, 8, 8));

        Selection wrbChildOutlineSelection = util.select().position(6, 8, 4)
                .add(wrbKineticOutputSelection)
                .add(wrbTopCoreSelection);
        scene.overlay().showOutline(PonderPalette.BLUE, "servo_option7_wrvb_outline", wrvbYawStaticSelection, 60);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "servo_option7_wrb_outline", wrbChildOutlineSelection, 40);
        scene.idle(14);
        scene.overlay().chaseBoundingBoxOutline(
                PonderPalette.GREEN,
                "servo_option7_servo_blade_outline",
                new AABB(-2.318748D, 2.725481D, 0.950962D, 15.318748D, 18.0D, 4.049038D),
                40
        );
        scene.idle(50);

        scene.addKeyframe();
        scene.idle(2);

        scene.world().setBlocks(wrvbYawStaticSelection, Blocks.AIR.defaultBlockState(), false);
        scene.addInstruction(PonderInstruction.simple(ponderScene ->
                ponderScene.forEach(SableSubLevelPonderElement.class,
                        SableSubLevelPonderElement::startSynchronizedRotorAndWrvbYawDemo)));
        scene.idle(SableSubLevelPonderElement.synchronizedRotorAndWrvbYawDemoTicks());
    }

    private static void applyCameraMove(SceneBuilder scene, float yRotation, int duration) {
        float step = yRotation / duration;
        for (int i = 0; i < duration; i++) {
            scene.rotateCameraY(step);
            scene.idle(1);
        }
    }
}
