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

public class WindRotoVerticalPonderScenes {


    private WindRotoVerticalPonderScenes() {
    }

    public static void windRotoVerticalBlock(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("windvane_ponder", "twistermill.ponder.windvane_ponder.title");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().fromTo(0, 0, 0, 4, 0, 6), Direction.UP);
        scene.idle(20);

        scene.world().showSection(util.select().position(2, 1, 3), Direction.DOWN); // windvane bearing
        scene.idle(20);

        scene.overlay().showText(60)
                .text(I18n.get("twistermill.ponder.windvane_ponder.text_1"))
                .pointAt(util.vector().centerOf(2, 1, 3))
                .placeNearTarget();
        scene.idle(70);

        scene.world().showSection(util.select().position(2, 2, 3), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(2, 3, 3), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(2, 4, 3), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(2, 4, 4), Direction.DOWN);
        scene.world().showSection(util.select().position(2, 4, 2), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(2, 4, 1), Direction.SOUTH);
        scene.idle(5);
        scene.world().showSection(util.select().position(2, 4, 0), Direction.SOUTH);
        scene.idle(5);
        scene.world().showSection(util.select().position(2, 3, 4), Direction.UP);
        scene.world().showSection(util.select().position(2, 5, 4), Direction.DOWN);
        scene.idle(20);
        scene.world().showSection(util.select().position(2, 2, 5), Direction.NORTH);
        scene.world().showSection(util.select().position(2, 2, 6), Direction.NORTH);
        scene.idle(5);
        scene.world().showSection(util.select().position(2, 3, 5), Direction.NORTH);
        scene.world().showSection(util.select().position(2, 3, 6), Direction.NORTH);
        scene.idle(5);
        scene.world().showSection(util.select().position(2, 4, 5), Direction.NORTH);
        scene.world().showSection(util.select().position(2, 4, 6), Direction.NORTH);
        scene.idle(5);
        scene.world().showSection(util.select().position(2, 5, 5), Direction.NORTH);
        scene.world().showSection(util.select().position(2, 5, 6), Direction.NORTH);
        scene.idle(5);
        scene.world().showSection(util.select().position(2, 6, 5), Direction.NORTH);
        scene.world().showSection(util.select().position(2, 6, 6), Direction.NORTH);
        scene.idle(20);

        Selection rotatingContraptionSelection = util.select().position(2, 2, 3)
                .add(util.select().position(2, 3, 3))
                .add(util.select().position(2, 4, 3))
                .add(util.select().position(2, 4, 4))
                .add(util.select().position(2, 4, 2))
                .add(util.select().position(2, 4, 1))
                .add(util.select().position(2, 4, 0))
                .add(util.select().position(2, 5, 4))
                .add(util.select().position(2, 3, 4))
                .add(util.select().position(2, 6, 5))
                .add(util.select().position(2, 5, 5))
                .add(util.select().position(2, 4, 5))
                .add(util.select().position(2, 3, 5))
                .add(util.select().position(2, 2, 5))
                .add(util.select().position(2, 6, 6))
                .add(util.select().position(2, 5, 6))
                .add(util.select().position(2, 4, 6))
                .add(util.select().position(2, 3, 6))
                .add(util.select().position(2, 2, 6));
        scene.idle(20);

        scene.overlay().showOutlineWithText(rotatingContraptionSelection, 80)
                .text("twistermill.ponder.windvane_ponder.text_2")
                .colored(PonderPalette.GREEN)
                .placeNearTarget();
        scene.idle(90);

        scene.world().hideSection(rotatingContraptionSelection, Direction.UP);
        scene.idle(20);

        ElementLink<WorldSectionElement> rotatingContraption =
                scene.world().showIndependentSection(rotatingContraptionSelection, Direction.DOWN);

        BlockPos bearingPos = util.grid().at(2, 1, 3);
        scene.world().configureCenterOfRotation(rotatingContraption, util.vector().centerOf(bearingPos));
        scene.idle(20);

        scene.overlay().showOutlineWithText(util.select().position(2, 1, 2), 70)
                .text("twistermill.ponder.windvane_ponder.text_3")
                .colored(PonderPalette.GREEN)
                .placeNearTarget();
        scene.idle(60);
        scene.world().showSection(util.select().position(2, 1, 2), Direction.SOUTH); // obi
        scene.idle(20);
        scene.overlay().showText(80)
                .text(I18n.get("twistermill.ponder.windvane_ponder.text_2"))
                .pointAt(util.vector().centerOf(2, 1, 2))
                .placeNearTarget();
        scene.idle(90);

        applyCameraMove(scene, 90f, 10, 1);
        scene.idle(20);

        scene.overlay().showControls(
                util.vector().topOf(3, 1, 3).subtract(.5, 0, 0),
                Pointing.DOWN,
                30
        ).rightClick();
        scene.idle(30);

        scene.world().rotateBearing(bearingPos, 60, 40);
        scene.world().rotateSection(rotatingContraption, 0, 60, 0, 40);
        scene.idle(45);

        applyCameraMove(scene, 180f, 10, 1);
        scene.idle(30);

        scene.world().showSection(util.select().position(2, 1, 4), Direction.NORTH); // RX
        scene.idle(20);
        scene.world().showSection(util.select().position(0, 1, 2), Direction.DOWN); // tx
        scene.idle(5);
        scene.world().showSection(util.select().position(0, 1, 3), Direction.DOWN); // rs
        scene.idle(5);
        scene.world().showSection(util.select().position(0, 1, 4), Direction.DOWN); // knob
        scene.idle(40);

        scene.overlay().showText(80)
                .text(I18n.get("twistermill.ponder.windvane_ponder.text_5"))
                .pointAt(util.vector().centerOf(2, 1, 4))
                .placeNearTarget();
        scene.idle(90);

        scene.world().toggleRedstonePower(util.select().position(0, 1, 4));
        scene.idle(3);
        scene.world().toggleRedstonePower(util.select().position(0, 1, 3));
        scene.idle(1);
        scene.world().toggleRedstonePower(util.select().position(0, 1, 4));
        scene.idle(1);
        scene.world().toggleRedstonePower(util.select().position(2, 1, 4));
        scene.idle(12);
        scene.world().showSection(util.select().position(0, 1, 4), Direction.UP);
        scene.idle(1);
        scene.world().toggleRedstonePower(util.select().position(0, 1, 3));
        scene.idle(3);
        scene.world().toggleRedstonePower(util.select().position(4, 4, 2));
        scene.idle(1);
        scene.world().toggleRedstonePower(util.select().position(2, 1, 4));
        scene.idle(1);
        scene.world().rotateBearing(bearingPos, -60, 40);
        scene.world().rotateSection(rotatingContraption, 0, -60, 0, 40);
        scene.idle(45);

        scene.world().showSection(util.select().position(1, 1, 3), Direction.EAST); // link
        scene.idle(40);
        scene.overlay().showText(90)
                .text(I18n.get("twistermill.ponder.windvane_ponder.text_6"))
                .pointAt(util.vector().centerOf(1, 1, 3))
                .placeNearTarget();
        scene.idle(100);

        scene.world().toggleRedstonePower(util.select().position(0, 1, 4));
        scene.idle(3);
        scene.world().toggleRedstonePower(util.select().position(0, 1, 3));
        scene.idle(1);
        scene.world().toggleRedstonePower(util.select().position(0, 1, 4));
        scene.idle(1);
        scene.world().toggleRedstonePower(util.select().position(2, 1, 4));
        scene.idle(11);
        scene.world().showSection(util.select().position(0, 1, 4), Direction.UP);
        scene.idle(3);
        scene.world().toggleRedstonePower(util.select().position(0, 1, 3));
        scene.idle(1);
        scene.world().toggleRedstonePower(util.select().position(4, 4, 2));
        scene.idle(1);
        scene.world().toggleRedstonePower(util.select().position(2, 1, 4));
        scene.idle(1);
        scene.world().rotateBearing(bearingPos, 60, 40);
        scene.world().rotateSection(rotatingContraption, 0, 60, 0, 40);
        scene.idle(5);
        applyCameraMove(scene, 360f, 50, 1);
        scene.idle(75);

        scene.markAsFinished();
    }

    private static void applyCameraMove(CreateSceneBuilder scene, float totalAngle, int steps, int idlePerStep) {
        if (steps <= 0) {
            throw new IllegalArgumentException("steps must be > 0");
        }

        float stepAngle = totalAngle / steps;
        for (int i = 0; i < steps; i++) {
            scene.rotateCameraY(stepAngle);
            scene.idle(idlePerStep);
        }
    }
}