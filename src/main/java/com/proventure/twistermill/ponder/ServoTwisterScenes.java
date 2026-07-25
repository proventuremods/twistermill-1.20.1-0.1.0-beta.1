package com.proventure.twistermill.ponder;

import com.simibubi.create.AllItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class ServoTwisterScenes {

    private ServoTwisterScenes() {
    }

    public static void servoTwister(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("servo_ponder", I18n.get("twistermill.ponder.servo_ponder.title"));
        scene.configureBasePlate(-2, 0, 12);
        scene.setSceneOffsetY(0f);
        scene.scaleSceneView(1f);

        scene.world().showSection(util.select().fromTo(1, 0, 0, 5, 0, 10), Direction.UP);
        scene.idle(20);

        applyCameraMove(scene, 90f, 20);
        BlockPos servoPos = util.grid().at(3, 3, 11);
        scene.world().showSection(util.select().position(servoPos), Direction.SOUTH); // twistermill:servo_twister_block
        scene.idle(20);

        scene.overlay().showText(100)
                .text(I18n.get("twistermill.ponder.servo_ponder.text_1"))
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().topOf(3, 3, 11))
                .placeNearTarget();
        scene.idle(120);

        scene.addKeyframe();

        scene.overlay().showText(130)
                .text(I18n.get("twistermill.ponder.servo_ponder.text_2"))
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().topOf(3, 3, 11))
                .placeNearTarget();
        scene.idle(30);

        scene.overlay().showLine(PonderPalette.INPUT, util.vector().centerOf(1, 3, 11), util.vector().centerOf(3, 3, 11), 30);
        scene.overlay().showLine(PonderPalette.INPUT, util.vector().centerOf(5, 3, 11), util.vector().centerOf(3, 3, 11), 30);
        scene.idle(20);

        scene.world().showSection(util.select().position(2, 3, 11), Direction.EAST); // speed analog lever
        scene.world().showSection(util.select().position(4, 3, 11), Direction.WEST); // angle analog lever
        scene.idle(100);

        scene.addKeyframe();

        scene.overlay().showText(150)
                .text(I18n.get("twistermill.ponder.servo_ponder.text_3"))
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().topOf(3, 3, 11))
                .placeNearTarget();
        scene.idle(20);
        scene.world().hideSection(util.select().position(2, 3, 11), Direction.WEST); // speed analog lever
        scene.world().hideSection(util.select().position(4, 3, 11), Direction.EAST); // angle analog lever
        scene.idle(50);

        scene.overlay().showControls(
                util.vector().topOf(servoPos),
                Pointing.DOWN,
                50).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(50);
        ServoTwisterPonderVisuals.showServoAntenna(scene, servoPos);
        scene.idle(50);

        scene.addKeyframe();

        applyCameraMove(scene, -90f, 20);
        scene.idle(20);

        scene.world().showSection(util.select().position(3, 3, 10), Direction.SOUTH); // START SAIL BUILD
        scene.world().showSection(util.select().position(2, 3, 10), Direction.EAST); // shaft
        scene.world().showSection(util.select().position(4, 3, 10), Direction.WEST); // shaft
        showServoSails(scene, util);
        Selection rotorSelection = util.select().position(3, 3, 10)
                .add(util.select().position(2, 3, 10))
                .add(util.select().position(4, 3, 10))
                .add(servoSailSelection(util));
        scene.idle(20);

        scene.overlay().showOutline(PonderPalette.BLUE, "rotor_glue", rotorSelection, 10);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.GREEN, "rotor_glue", rotorSelection, 10);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.BLUE, "rotor_glue", rotorSelection, 10);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.GREEN, "rotor_glue", rotorSelection, 10);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.BLUE, "rotor_glue", rotorSelection, 10);
        scene.world().showSection(util.select().position(3, 2, 11), Direction.UP); // display link
        scene.idle(20);

        scene.overlay().showOutlineWithText(rotorSelection, 80)
                .text(I18n.get("twistermill.ponder.servo_ponder.text_4"))
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().centerOf(3, 2, 11))
                .placeNearTarget();
        scene.idle(100);

        scene.world().hideSection(rotorSelection, Direction.NORTH);
        scene.idle(20);
        ElementLink<WorldSectionElement> rotor =
                scene.world().showIndependentSection(rotorSelection, Direction.SOUTH);
        scene.idle(20);
        scene.world().configureCenterOfRotation(rotor, util.vector().blockSurface(servoPos, Direction.NORTH));
        scene.idle(20);

        scene.addKeyframe();

        scene.overlay().showText(110)
                .text(I18n.get("twistermill.ponder.servo_ponder.text_5"))
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().centerOf(3, 3, 9))
                .placeNearTarget();
        scene.idle(130);

        scene.overlay().showControls(
                util.vector().topOf(3, 3, 11),
                Pointing.DOWN,
                30).rightClick();
        scene.idle(25);

        ServoTwisterPonderVisuals.rotateServoTop(scene, servoPos, -90, 20);
        scene.world().rotateSection(rotor, 0, 0, 90, 20);
        scene.idle(20);
        ServoTwisterPonderVisuals.rotateServoTop(scene, servoPos, 90, 20);
        scene.world().rotateSection(rotor, 0, 0, -90, 20);
        scene.idle(20);
        ServoTwisterPonderVisuals.rotateServoTop(scene, servoPos, -45, 10);
        scene.world().rotateSection(rotor, 0, 0, 45, 10);
        scene.idle(10);
        ServoTwisterPonderVisuals.rotateServoTop(scene, servoPos, 45, 10);
        scene.world().rotateSection(rotor, 0, 0, -45, 10);
        scene.idle(10);

        ServoTwisterPonderVisuals.rotateServoTop(scene, servoPos, 360, 60);
        scene.world().rotateSection(rotor, 0, 0, -360, 60);

        applyCameraMove(scene, -180f, 30);
        scene.idle(30);

        scene.idle(20);

        scene.markAsFinished();
    }


    private static void showServoSails(SceneBuilder scene, SceneBuildingUtil util) {
        scene.world().showSection(servoSailSelection(util), Direction.UP);
    }

    private static Selection servoSailSelection(SceneBuildingUtil util) {
        return util.select().fromTo(1, 3, 1, 5, 3, 9);
    }

    private static void applyCameraMove(SceneBuilder scene, float yRotation, int duration) {
        float step = yRotation / duration;
        for (int i = 0; i < duration; i++) {
            scene.rotateCameraY(step);
            scene.idle(1);
        }
    }
}
