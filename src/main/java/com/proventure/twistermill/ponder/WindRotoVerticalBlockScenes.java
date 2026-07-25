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
import net.minecraft.core.Direction;


public class WindRotoVerticalBlockScenes {

    private WindRotoVerticalBlockScenes() {
    }

    public static void windRotoVerticalBlock(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("windvane_ponder", I18n.get("twistermill.ponder.windvane_ponder.title"));
        scene.configureBasePlate(0, 0, 5);

        scene.world().showSection(util.select().fromTo(0, 0, 1, 4, 0, 5), Direction.UP);
        scene.idle(20);

        scene.world().showSection(util.select().position(2, 1, 3), Direction.DOWN); // wind vane bearing
        scene.idle(20);

        scene.addKeyframe();
        scene.idle(2);

        scene.overlay().showOutlineWithText(util.select().position(2, 1, 2), 110)
                .text(I18n.get("twistermill.ponder.windvane_ponder.text_1"))
                .colored(PonderPalette.GREEN)
                .placeNearTarget();
        scene.idle(20);
        scene.world().showSection(util.select().position(2, 1, 2), Direction.SOUTH); // smooth stone slab north marker
        scene.idle(110);

        applyCameraMove(scene, 180f, 20);
        scene.idle(20);

        scene.addKeyframe();
        scene.idle(2);

        scene.overlay().showText(130)
                .text(I18n.get("twistermill.ponder.windvane_ponder.text_2"))
                .pointAt(util.vector().centerOf(2, 1, 3));
        scene.idle(45);
        scene.world().showSection(util.select().position(2, 1, 4), Direction.NORTH);
        scene.idle(45);
        ElementLink<WorldSectionElement> topBlock = scene.world()
                .showIndependentSection(util.select().position(2, 2, 3), Direction.DOWN);
        scene.world().configureCenterOfRotation(topBlock, util.vector().centerOf(util.grid().at(2, 1, 3)));
        scene.idle(60);

        scene.overlay().showControls(
                util.vector().topOf(2, 1, 4).subtract(0, 0, 0.5),
                Pointing.DOWN,
                20
        ).rightClick();
        scene.idle(23);

        scene.world().rotateBearing(util.grid().at(2, 1, 3), 60, 40);
        scene.world().rotateSection(topBlock, 0, 60, 0, 40);
        scene.idle(50);
        scene.world().toggleRedstonePower(util.select().position(2, 1, 4));
        scene.idle(12);
        scene.world().toggleRedstonePower(util.select().position(2, 1, 4));
        scene.idle(2);
        scene.world().rotateBearing(util.grid().at(2, 1, 3), -60, 40);
        scene.world().rotateSection(topBlock, 0, -60, 0, 40);
        scene.idle(40);

        ElementLink<WorldSectionElement> poleLower = scene.world()
                .showIndependentSection(util.select().position(2, 3, 3), Direction.DOWN); // pole
        scene.world().configureCenterOfRotation(poleLower, util.vector().centerOf(util.grid().at(2, 1, 3)));
        scene.idle(6);
        ElementLink<WorldSectionElement> poleUpper = scene.world()
                .showIndependentSection(util.select().position(2, 4, 3), Direction.DOWN);
        scene.world().configureCenterOfRotation(poleUpper, util.vector().centerOf(util.grid().at(2, 1, 3)));
        scene.idle(6);
        ElementLink<WorldSectionElement> rearArm = scene.world()
                .showIndependentSection(util.select().position(2, 4, 4), Direction.NORTH);
        scene.world().configureCenterOfRotation(rearArm, util.vector().centerOf(util.grid().at(2, 1, 3)));
        scene.idle(8);
        ElementLink<WorldSectionElement> mountSection = scene.world()
                .showIndependentSection(util.select().position(2, 3, 4)
                        .add(util.select().position(2, 5, 4)), Direction.NORTH); // mounts
        scene.world().configureCenterOfRotation(mountSection, util.vector().centerOf(util.grid().at(2, 1, 3)));
        scene.idle(8);
        ElementLink<WorldSectionElement> sailSectionA = scene.world()
                .showIndependentSection(util.select().position(2, 2, 5)
                        .add(util.select().position(2, 3, 5))
                        .add(util.select().position(2, 4, 5))
                        .add(util.select().position(2, 5, 5))
                        .add(util.select().position(2, 6, 5)), Direction.NORTH); // sails
        scene.world().configureCenterOfRotation(sailSectionA, util.vector().centerOf(util.grid().at(2, 1, 3)));
        scene.idle(8);
        ElementLink<WorldSectionElement> sailSectionB = scene.world()
                .showIndependentSection(util.select().position(2, 5, 6)
                        .add(util.select().position(2, 2, 6))
                        .add(util.select().position(2, 3, 6))
                        .add(util.select().position(2, 4, 6))
                        .add(util.select().position(2, 6, 6)), Direction.NORTH);
        scene.world().configureCenterOfRotation(sailSectionB, util.vector().centerOf(util.grid().at(2, 1, 3)));

        applyCameraMove(scene, 180f, 20);

        ElementLink<WorldSectionElement> frontSectionA = scene.world()
                .showIndependentSection(util.select().position(2, 4, 2), Direction.SOUTH); // front
        scene.world().configureCenterOfRotation(frontSectionA, util.vector().centerOf(util.grid().at(2, 1, 3)));
        scene.idle(8);
        ElementLink<WorldSectionElement> frontSectionB = scene.world()
                .showIndependentSection(util.select().position(2, 4, 1), Direction.SOUTH);
        scene.world().configureCenterOfRotation(frontSectionB, util.vector().centerOf(util.grid().at(2, 1, 3)));
        scene.idle(8);
        ElementLink<WorldSectionElement> frontSectionC = scene.world()
                .showIndependentSection(util.select().position(2, 4, 0), Direction.SOUTH);
        scene.world().configureCenterOfRotation(frontSectionC, util.vector().centerOf(util.grid().at(2, 1, 3)));

        Selection rotatingContraptionOutlineSelection = util.select().position(2, 2, 3)
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

        scene.addKeyframe();
        scene.idle(2);

        scene.overlay().showOutlineWithText(rotatingContraptionOutlineSelection, 130)
                .text(I18n.get("twistermill.ponder.windvane_ponder.text_3"))
                .colored(PonderPalette.GREEN)
                .placeNearTarget();
        scene.idle(150);

        scene.addKeyframe();
        scene.idle(2);

        scene.overlay().showText(80)
                .text(I18n.get("twistermill.ponder.windvane_ponder.text_4"))
                .pointAt(util.vector().centerOf(2, 4, 0))
                .placeNearTarget();
        scene.idle(100);

        scene.world().rotateBearing(util.grid().at(2, 1, 3), 60, 40);
        scene.world().rotateSection(topBlock, 0, 60, 0, 40);
        scene.world().rotateSection(poleLower, 0, 60, 0, 40);
        scene.world().rotateSection(poleUpper, 0, 60, 0, 40);
        scene.world().rotateSection(rearArm, 0, 60, 0, 40);
        scene.world().rotateSection(mountSection, 0, 60, 0, 40);
        scene.world().rotateSection(sailSectionA, 0, 60, 0, 40);
        scene.world().rotateSection(sailSectionB, 0, 60, 0, 40);
        scene.world().rotateSection(frontSectionA, 0, 60, 0, 40);
        scene.world().rotateSection(frontSectionB, 0, 60, 0, 40);
        scene.world().rotateSection(frontSectionC, 0, 60, 0, 40);
        scene.idle(50);

        applyCameraMove(scene, -90f, 10);
        scene.idle(20);

        scene.world().showSection(util.select().position(1, 1, 3), Direction.NORTH); // receiver
        scene.idle(6);
        scene.world().showSection(util.select().position(0, 1, 5), Direction.DOWN); // transmitter
        scene.idle(6);
        scene.world().showSection(util.select().position(0, 1, 4), Direction.DOWN); // floor button
        scene.idle(10);

        scene.addKeyframe();
        scene.idle(2);

        scene.overlay().showText(50)
                .text(I18n.get("twistermill.ponder.windvane_ponder.text_5"))
                .pointAt(util.vector().centerOf(1, 1, 3))
                .placeNearTarget();
        scene.idle(70);

        scene.world().toggleRedstonePower(util.select().position(0, 1, 4));
        scene.idle(3);
        scene.world().toggleRedstonePower(util.select().position(0, 1, 5));
        scene.world().toggleRedstonePower(util.select().position(1, 1, 3));
        scene.idle(12);
        scene.world().toggleRedstonePower(util.select().position(0, 1, 4));
        scene.idle(3);
        scene.world().toggleRedstonePower(util.select().position(0, 1, 5));
        scene.world().toggleRedstonePower(util.select().position(1, 1, 3));
        scene.idle(1);


        scene.world().rotateBearing(util.grid().at(2, 1, 3), -60, 40);
        scene.world().rotateSection(topBlock, 0, -60, 0, 40);
        scene.world().rotateSection(poleLower, 0, -60, 0, 40);
        scene.world().rotateSection(poleUpper, 0, -60, 0, 40);
        scene.world().rotateSection(rearArm, 0, -60, 0, 40);
        scene.world().rotateSection(mountSection, 0, -60, 0, 40);
        scene.world().rotateSection(sailSectionA, 0, -60, 0, 40);
        scene.world().rotateSection(sailSectionB, 0, -60, 0, 40);
        scene.world().rotateSection(frontSectionA, 0, -60, 0, 40);
        scene.world().rotateSection(frontSectionB, 0, -60, 0, 40);
        scene.world().rotateSection(frontSectionC, 0, -60, 0, 40);
        applyCameraMove(scene, 180f, 20);
        scene.idle(50);

        scene.world().showSection(util.select().position(3, 1, 3), Direction.WEST); // display link
        scene.idle(20);

        scene.addKeyframe();
        scene.idle(2);

        scene.overlay().showText(90)
                .text(I18n.get("twistermill.ponder.windvane_ponder.text_6"))
                .pointAt(util.vector().centerOf(3, 1, 3))
                .placeNearTarget();
        scene.idle(110);

        applyCameraMove(scene, -90f, 10);
        scene.idle(10);

        scene.world().toggleRedstonePower(util.select().position(0, 1, 4));
        scene.idle(3);
        scene.world().toggleRedstonePower(util.select().position(0, 1, 5));
        scene.world().toggleRedstonePower(util.select().position(1, 1, 3));

        scene.idle(12);
        scene.world().toggleRedstonePower(util.select().position(0, 1, 4));
        scene.idle(3);
        scene.world().toggleRedstonePower(util.select().position(0, 1, 5));
        scene.world().toggleRedstonePower(util.select().position(1, 1, 3));
        scene.idle(1);


        scene.world().rotateBearing(util.grid().at(2, 1, 3), 60, 40);
        scene.world().rotateSection(topBlock, 0, 60, 0, 40);
        scene.world().rotateSection(poleLower, 0, 60, 0, 40);
        scene.world().rotateSection(poleUpper, 0, 60, 0, 40);
        scene.world().rotateSection(rearArm, 0, 60, 0, 40);
        scene.world().rotateSection(mountSection, 0, 60, 0, 40);
        scene.world().rotateSection(sailSectionA, 0, 60, 0, 40);
        scene.world().rotateSection(sailSectionB, 0, 60, 0, 40);
        scene.world().rotateSection(frontSectionA, 0, 60, 0, 40);
        scene.world().rotateSection(frontSectionB, 0, 60, 0, 40);
        scene.world().rotateSection(frontSectionC, 0, 60, 0, 40);
        scene.idle(5);

        scene.markAsFinished();
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
