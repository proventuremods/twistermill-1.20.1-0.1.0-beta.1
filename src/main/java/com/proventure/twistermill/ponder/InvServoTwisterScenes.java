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

public class InvServoTwisterScenes {

    private InvServoTwisterScenes() {
    }

    public static void invServoTwister(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("invservo_ponder", I18n.get("twistermill.ponder.invservo_ponder.title"));
        scene.configureBasePlate(-3, 1, 12);
        scene.setSceneOffsetY(0f);
        scene.scaleSceneView(1f);

        scene.world().showSection(util.select().fromTo(1, 0, 2, 5, 0, 12), Direction.UP);
        scene.idle(20);

        applyCameraMove(scene, -90f, 20);
        BlockPos servoPos = util.grid().at(3, 3, 1);
        scene.world().showSection(util.select().position(servoPos), Direction.NORTH); // twistermill:inv_servo_twister_block
        scene.idle(20);

        scene.overlay().showText(100)
                .text(I18n.get("twistermill.ponder.invservo_ponder.text_1"))
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().topOf(3, 3, 1))
                .placeNearTarget();
        scene.idle(120);

        scene.addKeyframe();


        scene.overlay().showText(130)
                .text(I18n.get("twistermill.ponder.invservo_ponder.text_2"))
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().topOf(3, 3, 1))
                .placeNearTarget();
        scene.idle(30);

        scene.overlay().showLine(PonderPalette.INPUT, util.vector().centerOf(1, 3, 1), util.vector().centerOf(3, 3, 1), 30);
        scene.overlay().showLine(PonderPalette.INPUT, util.vector().centerOf(5, 3, 1), util.vector().centerOf(3, 3, 1), 30);
        scene.idle(20);

        scene.world().showSection(util.select().position(2, 3, 1), Direction.EAST); // speed analog lever
        scene.world().showSection(util.select().position(4, 3, 1), Direction.WEST); // angle analog lever
        scene.idle(100);

        scene.addKeyframe();


        scene.overlay().showText(150)
                .text(I18n.get("twistermill.ponder.invservo_ponder.text_3"))
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().topOf(3, 3, 1))
                .placeNearTarget();
        scene.idle(20);
        scene.world().hideSection(util.select().position(2, 3, 1), Direction.WEST); // speed analog lever
        scene.world().hideSection(util.select().position(4, 3, 1), Direction.EAST); // angle analog lever
        scene.idle(50);

        scene.overlay().showControls(
                util.vector().topOf(servoPos),
                Pointing.DOWN,
                50).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(50);
        ServoTwisterPonderVisuals.showInvServoAntenna(scene, servoPos);
        scene.idle(50);

        scene.addKeyframe();

        applyCameraMove(scene, 90f, 20);
        scene.idle(20);

        scene.world().showSection(util.select().position(3, 3, 2), Direction.NORTH); // START SAIL BUILD
        scene.world().showSection(util.select().position(2, 3, 2), Direction.EAST); // shaft
        scene.world().showSection(util.select().position(4, 3, 2), Direction.WEST); // shaft
        showInvServoSails(scene, util);
        Selection rotorSelection = util.select().position(3, 3, 2)
                .add(util.select().position(2, 3, 2))
                .add(util.select().position(4, 3, 2))
                .add(invServoSailSelection(util));
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
        scene.world().showSection(util.select().position(3, 2, 1), Direction.UP); // display link
        scene.idle(20);


        scene.overlay().showOutlineWithText(rotorSelection, 80)
                .text(I18n.get("twistermill.ponder.invservo_ponder.text_4"))
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().centerOf(3, 2, 1))
                .placeNearTarget();
        scene.idle(100);
        scene.world().hideSection(rotorSelection, Direction.SOUTH);
        scene.idle(20);
        ElementLink<WorldSectionElement> rotor =
                scene.world().showIndependentSection(rotorSelection, Direction.NORTH);
        scene.idle(20);
        scene.world().configureCenterOfRotation(rotor, util.vector().blockSurface(servoPos, Direction.SOUTH));
        scene.idle(20);

        scene.addKeyframe();

        scene.overlay().showText(110)
                .text(I18n.get("twistermill.ponder.invservo_ponder.text_5"))
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().centerOf(3, 3, 3))
                .placeNearTarget();
        scene.idle(130);


        scene.overlay().showControls(
                util.vector().topOf(3, 3, 1),
                Pointing.DOWN,
                30).rightClick();
        scene.idle(25);

        ServoTwisterPonderVisuals.rotateServoTop(scene, servoPos, 90, 20);
        scene.world().rotateSection(rotor, 0, 0, -90, 20);
        scene.idle(20);
        ServoTwisterPonderVisuals.rotateServoTop(scene, servoPos, -90, 20);
        scene.world().rotateSection(rotor, 0, 0, 90, 20);
        scene.idle(20);
        ServoTwisterPonderVisuals.rotateServoTop(scene, servoPos, 45, 10);
        scene.world().rotateSection(rotor, 0, 0, -45, 10);
        scene.idle(10);
        ServoTwisterPonderVisuals.rotateServoTop(scene, servoPos, -45, 10);
        scene.world().rotateSection(rotor, 0, 0, 45, 10);
        scene.idle(10);

        ServoTwisterPonderVisuals.rotateServoTop(scene, servoPos, -360, 60);
        scene.world().rotateSection(rotor, 0, 0, 360, 60);

        applyCameraMove(scene, -180f, 30);
        scene.idle(30);


        scene.idle(20);

        scene.markAsFinished();
    }

    private static void showInvServoSails(SceneBuilder scene, SceneBuildingUtil util) {
        scene.world().showSection(invServoSailSelection(util), Direction.UP);
    }

    private static Selection invServoSailSelection(SceneBuildingUtil util) {
        return util.select().fromTo(1, 3, 3, 5, 3, 11);
    }

    private static void applyCameraMove(SceneBuilder scene, float yRotation, int duration) {
        float step = yRotation / duration;
        for (int i = 0; i < duration; i++) {
            scene.rotateCameraY(step);
            scene.idle(1);
        }
    }
}
