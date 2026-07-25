package com.proventure.twistermill.ponder;

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

public class WindRotoBlockScenes {

    private WindRotoBlockScenes() {
    }

    private static final int MAIN_KINETIC_SPEED = 16;
    private static final int FIRST_RUN_UNTIL_LINK_STOP_TICKS = 340;
    private static final int SECOND_MAIN_ROTATION_TICKS = 170;

    public static void windRotoBlock(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("windroto_ponder", I18n.get("twistermill.ponder.windroto_ponder.title"));
        scene.configureBasePlate(0, 0, 5);

        scene.world().showSection(util.select().fromTo(0, 0, 0, 4, 0, 5), Direction.UP);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(0, 3, 1, 4, 3, 1), Direction.UP);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(0, 3, 2, 4, 3, 2), Direction.UP);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(0, 3, 3, 4, 3, 3), Direction.UP);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(0, 3, 4, 4, 3, 4), Direction.UP);
        scene.idle(5);

        scene.world().showSection(util.select().position(2, 4, 1), Direction.SOUTH); // twistermill:wind_roto_block
        scene.idle(10);

        scene.overlay().showText(60)
                .text(I18n.get("twistermill.ponder.windroto_ponder.text_1"))
                .pointAt(util.vector().centerOf(2, 4, 1))
                .placeNearTarget();
        scene.addKeyframe();
        scene.idle(70);


        scene.overlay().showText(60)
                .text(I18n.get("twistermill.ponder.windroto_ponder.text_2"))
                .pointAt(util.vector().centerOf(2, 4, 1))
                .placeNearTarget();
        scene.idle(70);

        ElementLink<WorldSectionElement> rotor =
                scene.world().showIndependentSection(util.select().position(2, 4, 0), Direction.SOUTH); // hub / contraption center
        scene.addKeyframe();
        scene.idle(10);


        scene.overlay().showText(60)
                .text(I18n.get("twistermill.ponder.windroto_ponder.text_3"))
                .pointAt(util.vector().centerOf(2, 4, 0))
                .placeNearTarget();
        scene.idle(70);

        scene.world().showSectionAndMerge(util.select().position(2, 5, 0), Direction.DOWN, rotor);
        scene.idle(5);
        scene.world().showSectionAndMerge(util.select().position(2, 6, 0), Direction.DOWN, rotor);
        scene.idle(5);
        scene.world().showSectionAndMerge(util.select().position(3, 4, 0), Direction.WEST, rotor);
        scene.idle(5);
        scene.world().showSectionAndMerge(util.select().position(4, 4, 0), Direction.WEST, rotor);
        scene.idle(5);
        scene.world().showSectionAndMerge(util.select().position(2, 3, 0), Direction.UP, rotor);
        scene.idle(5);
        scene.world().showSectionAndMerge(util.select().position(2, 2, 0), Direction.UP, rotor);
        scene.idle(5);
        scene.world().showSectionAndMerge(util.select().position(1, 4, 0), Direction.EAST, rotor);
        scene.idle(5);
        scene.world().showSectionAndMerge(util.select().position(0, 4, 0), Direction.EAST, rotor);
        scene.idle(20);


        Selection rotorSelection = util.select().position(2, 4, 0)
                .add(util.select().position(2, 5, 0))
                .add(util.select().position(2, 6, 0))
                .add(util.select().position(3, 4, 0))
                .add(util.select().position(4, 4, 0))
                .add(util.select().position(2, 3, 0))
                .add(util.select().position(2, 2, 0))
                .add(util.select().position(1, 4, 0))
                .add(util.select().position(0, 4, 0));

        scene.addKeyframe();
        scene.idle(10);
        scene.overlay().showOutlineWithText(rotorSelection, 100)
                .text(I18n.get("twistermill.ponder.windroto_ponder.text_4"))
                .colored(PonderPalette.GREEN)
                .placeNearTarget();
        scene.idle(110);

        applyCameraMove(scene, 165f, 20);
        scene.idle(20);

        scene.world().showSection(util.select().position(2, 4, 2), Direction.DOWN); // shaft
        scene.idle(5);
        scene.world().showSection(util.select().position(2, 4, 3), Direction.DOWN); // shaft
        scene.idle(5);
        scene.world().toggleRedstonePower(util.select().position(2, 4, 4));
        scene.idle(5);
        scene.world().showSection(util.select().position(2, 4, 4), Direction.DOWN); // clutch
        scene.idle(5);
        scene.world().showSection(util.select().position(2, 4, 5), Direction.DOWN); // shaft
        scene.addKeyframe();
        scene.idle(20);


        scene.overlay().showText(70)
                .text(I18n.get("twistermill.ponder.windroto_ponder.text_5"))
                .pointAt(util.vector().centerOf(2, 4, 4))
                .placeNearTarget();
        scene.idle(80);


        BlockPos bearingPos = util.grid().at(2, 4, 1);
        scene.world().configureCenterOfRotation(rotor, util.vector().blockSurface(bearingPos, Direction.SOUTH));
        scene.idle(5);

        scene.overlay().showControls(
                util.vector().topOf(bearingPos).subtract(.5, 0, 0),
                Pointing.DOWN,
                30).rightClick();
        scene.idle(30);

        scene.world().setKineticSpeed(
                util.select().position(2, 4, 2)
                        .add(util.select().position(2, 4, 3))
                        .add(util.select().position(2, 4, 4))
                        .add(util.select().position(2, 4, 5)),
                MAIN_KINETIC_SPEED);
        scene.world().rotateBearing(
                bearingPos,
                angleForMainKineticSpeed(FIRST_RUN_UNTIL_LINK_STOP_TICKS),
                FIRST_RUN_UNTIL_LINK_STOP_TICKS);
        scene.world().rotateSection(
                rotor,
                0,
                0,
                angleForMainKineticSpeed(FIRST_RUN_UNTIL_LINK_STOP_TICKS),
                FIRST_RUN_UNTIL_LINK_STOP_TICKS);
        scene.effects().rotationDirectionIndicator(bearingPos.south());
        scene.idle(20);

        scene.world().toggleRedstonePower(util.select().position(3, 4, 1));
        scene.world().toggleRedstonePower(util.select().position(4, 4, 1));
        scene.world().toggleRedstonePower(util.select().position(4, 4, 2));
        scene.world().toggleRedstonePower(util.select().position(4, 4, 4));
        scene.world().toggleRedstonePower(util.select().position(3, 4, 4));
        scene.world().showSection(util.select().position(3, 4, 1), Direction.DOWN); // comparator
        scene.idle(5);
        scene.world().showSection(util.select().position(4, 4, 1), Direction.DOWN); // redstone
        scene.idle(5);
        scene.world().showSection(util.select().position(4, 4, 2), Direction.DOWN); // repeater
        scene.idle(5);
        scene.world().showSection(util.select().position(4, 4, 3), Direction.DOWN); // block
        scene.idle(5);
        scene.world().showSection(util.select().position(4, 4, 4), Direction.DOWN); // torch
        scene.idle(5);
        scene.world().showSection(util.select().position(3, 4, 4), Direction.DOWN); // redstone
        scene.addKeyframe();
        scene.idle(20);


        scene.overlay().showText(80)
                .text(I18n.get("twistermill.ponder.windroto_ponder.text_6"))
                .pointAt(util.vector().of(3, 4, 1))
                .placeNearTarget();
        scene.idle(90);

        scene.world().toggleRedstonePower(util.select().position(3, 4, 1));
        scene.world().toggleRedstonePower(util.select().position(4, 4, 1));
        scene.world().toggleRedstonePower(util.select().position(4, 4, 2));
        scene.world().toggleRedstonePower(util.select().position(4, 4, 4));
        scene.idle(1);
        scene.world().toggleRedstonePower(util.select().position(3, 4, 4));
        scene.idle(1);
        scene.world().toggleRedstonePower(util.select().position(2, 4, 4));
        scene.idle(1);
        scene.world().setKineticSpeed(
                util.select().position(2, 4, 4)
                        .add(util.select().position(2, 4, 5)),
                0);
        scene.idle(20);

        applyCameraMove(scene, 105f, 10);
        scene.idle(20);

        scene.world().showSection(util.select().position(0, 1, 2), Direction.DOWN); // create:analog_lever
        scene.idle(5);
        scene.world().showSection(util.select().position(0, 1, 1), Direction.DOWN); // redstone link transmitter
        scene.idle(10);
        scene.world().showSection(util.select().position(0, 4, 1), Direction.DOWN); // redstone link receiver
        scene.idle(5);
        scene.world().showSection(util.select().position(1, 4, 1), Direction.DOWN); // redstone
        scene.addKeyframe();
        scene.idle(20);


        scene.overlay().showText(80)
                .text(I18n.get("twistermill.ponder.windroto_ponder.text_7"))
                .pointAt(util.vector().centerOf(0, 4, 1))
                .placeNearTarget();
        scene.idle(90);

        scene.world().toggleRedstonePower(util.select().position(0, 1, 2));
        scene.idle(1);
        scene.world().toggleRedstonePower(util.select().position(0, 1, 1));
        scene.idle(1);
        scene.world().toggleRedstonePower(util.select().position(0, 4, 1));
        scene.world().setKineticSpeed(
                util.select().position(2, 4, 2)
                        .add(util.select().position(2, 4, 3)),
                0);
        scene.idle(1);
        scene.world().toggleRedstonePower(util.select().position(1, 4, 1));
        scene.idle(20);

        scene.world().toggleRedstonePower(util.select().position(0, 1, 2));
        scene.world().toggleRedstonePower(util.select().position(0, 1, 1));
        scene.idle(1);
        scene.world().toggleRedstonePower(util.select().position(0, 4, 1));
        scene.world().setKineticSpeed(
                util.select().position(2, 4, 2)
                        .add(util.select().position(2, 4, 3)),
                MAIN_KINETIC_SPEED);
        scene.world().rotateBearing(
                bearingPos,
                angleForMainKineticSpeed(SECOND_MAIN_ROTATION_TICKS),
                SECOND_MAIN_ROTATION_TICKS);
        scene.world().rotateSection(
                rotor,
                0,
                0,
                angleForMainKineticSpeed(SECOND_MAIN_ROTATION_TICKS),
                SECOND_MAIN_ROTATION_TICKS);
        scene.effects().rotationDirectionIndicator(bearingPos.south());
        scene.world().toggleRedstonePower(util.select().position(1, 4, 1));
        scene.idle(20);

        scene.world().showSection(util.select().position(2, 5, 1), Direction.NORTH); // display link
        scene.addKeyframe();
        scene.idle(20);


        scene.overlay().showText(70)
                .text(I18n.get("twistermill.ponder.windroto_ponder.text_8"))
                .pointAt(util.vector().centerOf(2, 5, 1))
                .placeNearTarget();
        scene.idle(80);

        applyCameraMove(scene, 450f, 50);

        scene.world().setKineticSpeed(
                util.select().position(2, 4, 2)
                        .add(util.select().position(2, 4, 3)),
                0);
        scene.idle(20);

        scene.markAsFinished();
    }

    private static int angleForMainKineticSpeed(int ticks) {
        return Math.round(MAIN_KINETIC_SPEED * 360.0f * ticks / 1200.0f);
    }

    private static void applyCameraMove(CreateSceneBuilder scene, float totalAngle, int steps) {
        if (steps <= 0) {
            throw new IllegalArgumentException("steps must be > 0");
        }

        float stepAngle = totalAngle / steps;
        for (int i = 0; i < steps; i++) {
            scene.rotateCameraY(stepAngle);
            scene.idle(1);
        }
    }
}
