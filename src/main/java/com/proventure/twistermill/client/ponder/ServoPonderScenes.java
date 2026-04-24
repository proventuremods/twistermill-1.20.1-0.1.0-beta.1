package com.proventure.twistermill.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
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


public class ServoPonderScenes {

    private ServoPonderScenes() {
    }

    public static void servoTwisterBlock(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder createScene = new CreateSceneBuilder(scene);
        scene.title("servo_ponder", "twistermill.ponder.servo_ponder.title");
        scene.configureBasePlate(0, 0, 9);

        scene.world().showSection(util.select().fromTo(2, 0, 0, 6, 0, 2), Direction.UP);
        scene.idle(10);

        scene.world().showSection(util.select().position(1, 3, 3), Direction.DOWN);//servo
        scene.idle(20);

        scene.overlay().showText(100)
                .text(I18n.get("twistermill.ponder.servo_ponder.text_1"))
                .colored(PonderPalette.RED)
                .pointAt(util.vector().centerOf(1, 3, 3))
                .placeNearTarget();
        scene.idle(110);
        scene.overlay().showText(100)
                .text(I18n.get("twistermill.ponder.servo_ponder.text_2"))
                .pointAt(util.vector().centerOf(1, 3, 3))
                .placeNearTarget();
        scene.idle(110);

        scene.world().showSection(util.select().position(1, 3, 2), Direction.DOWN);//lever
        scene.idle(10);
        scene.world().showSection(util.select().position(1, 3, 4), Direction.DOWN);//rx1
        scene.idle(10);
        scene.world().showSection(util.select().position(1, 4, 3), Direction.DOWN);//rx2
        scene.idle(10);
        scene.world().showSection(util.select().position(1, 2, 3), Direction.UP);//link
        scene.idle(10);

        scene.overlay().showText(90)
                .text(I18n.get("twistermill.ponder.servo_ponder.text_3"))
                .pointAt(util.vector().centerOf(1, 2, 3))
                .placeNearTarget();
        scene.idle(100);

        scene.world().showSection(util.select().position(0, 3, 3), Direction.EAST);//girder
        scene.world().showSection(util.select().position(2, 3, 3), Direction.WEST);//block
        scene.idle(10);
        scene.world().showSection(util.select().position(2, 3, 2), Direction.SOUTH);//mount l
        scene.world().showSection(util.select().position(2, 3, 4), Direction.NORTH);//mount r
        scene.idle(10);
        scene.world().showSection(util.select().position(3, 3, 2), Direction.UP);//1
        scene.idle(5);
        scene.world().showSection(util.select().position(3, 3, 3), Direction.DOWN);//2
        scene.idle(5);
        scene.world().showSection(util.select().position(3, 3, 4), Direction.UP);//3
        scene.idle(5);
        scene.world().showSection(util.select().position(4, 3, 2), Direction.DOWN);//4
        scene.idle(5);
        scene.world().showSection(util.select().position(4, 3, 3), Direction.UP);//5
        scene.idle(5);
        scene.world().showSection(util.select().position(4, 3, 4), Direction.DOWN);//6
        scene.idle(5);
        scene.world().showSection(util.select().position(5, 3, 2), Direction.UP);//7
        scene.idle(5);
        scene.world().showSection(util.select().position(5, 3, 3), Direction.DOWN);//8
        scene.idle(5);
        scene.world().showSection(util.select().position(5, 3, 4), Direction.UP);//9
        scene.idle(5);
        scene.world().showSection(util.select().position(6, 3, 2), Direction.DOWN);//10
        scene.idle(5);
        scene.world().showSection(util.select().position(6, 3, 3), Direction.UP);//11
        scene.idle(5);
        scene.world().showSection(util.select().position(6, 3, 4), Direction.DOWN);//12
        scene.idle(5);
        scene.world().showSection(util.select().position(7, 3, 2), Direction.UP);//13
        scene.idle(5);
        scene.world().showSection(util.select().position(7, 3, 3), Direction.DOWN);//14
        scene.idle(5);
        scene.world().showSection(util.select().position(7, 3, 4), Direction.UP);//15
        scene.idle(5);
        scene.world().showSection(util.select().position(8, 3, 2), Direction.DOWN);//16
        scene.idle(5);
        scene.world().showSection(util.select().position(8, 3, 3), Direction.UP);//17
        scene.idle(5);
        scene.world().showSection(util.select().position(8, 3, 4), Direction.DOWN);//18
        scene.idle(40);

        applyCameraMove(scene, 100f, 20, 1);
        scene.idle(1);

        Selection rotorSelection = util.select().position(2, 3, 2)
                .add(util.select().position(2, 3, 3))
                .add(util.select().position(2, 3, 4))
                .add(util.select().fromTo(3, 3, 2, 8, 3, 4));
        scene.idle(20);

        scene.overlay().showOutline(PonderPalette.GREEN, "rotor_glue", rotorSelection, 40);
        scene.idle(1);
        scene.overlay().showOutlineWithText(rotorSelection, 90)
                .text("twistermill.ponder.servo_ponder.text_4")
                .colored(PonderPalette.GREEN)
                .placeNearTarget();
        scene.idle(100);



        scene.world().hideSection(rotorSelection, Direction.UP);
        scene.idle(20);

        ElementLink<WorldSectionElement> rotor =
                scene.world().showIndependentSection(rotorSelection, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(120)
                .text(I18n.get("twistermill.ponder.servo_ponder.text_5"))
                .colored(PonderPalette.RED)
                .pointAt(util.vector().centerOf(1, 3, 3));
        scene.idle(130);

        scene.world().showSection(util.select().position(2, 1, 0), Direction.DOWN);//lever1base
        scene.idle(10);
        scene.world().showSection(util.select().position(2, 1, 1), Direction.DOWN);//tx2
        scene.idle(10);
        scene.world().showSection(util.select().position(4, 1, 0), Direction.DOWN);//lever2base
        scene.idle(10);
        scene.world().showSection(util.select().position(4, 1, 1), Direction.DOWN);//tx2
        scene.idle(10);
        scene.world().showSection(util.select().position(6, 1, 0), Direction.DOWN);//lever3base
        scene.idle(10);
        scene.world().showSection(util.select().position(6, 1, 1), Direction.DOWN);//tx3
        scene.idle(50);

        BlockPos bearingPos = util.grid().at(1, 3, 3);
        scene.world().configureCenterOfRotation(rotor, util.vector().blockSurface(bearingPos, Direction.EAST));
        scene.overlay().showControls(
                util.vector().topOf(bearingPos).subtract(.5, 0, 0),
                Pointing.DOWN,
                30).rightClick();
        scene.idle(10);
        createScene.world().rotateBearing(bearingPos, 720, 120);
        scene.world().rotateSection(rotor, 720, 0, 0, 120);
        scene.idle(1);
        applyCameraMove(scene, 360f, 60, 1);
        scene.idle(1);
        applyCameraMove(scene, -100f, 60, 1);
        scene.idle(10);


        scene.markAsFinished();
    }

    private static void applyCameraMove(SceneBuilder scene, float yRotation, int duration, int idlePerStep) {
        float step = yRotation / duration;
        for (int i = 0; i < duration; i++) {
            scene.rotateCameraY(step);
            scene.idle(idlePerStep);
        }
    }
}