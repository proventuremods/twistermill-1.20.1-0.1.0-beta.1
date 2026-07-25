package com.proventure.twistermill.ponder;

import com.proventure.twistermill.blockentity.ServoTwisterBlockEntity;
import com.proventure.twistermill.item.ModItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class ServoOption7Scenes {

    private ServoOption7Scenes() {
    }

    public static void servoOption7(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder createScene = new CreateSceneBuilder(scene);
        scene.title("servo_option_7_ponder", "Servo Option 7");
        scene.configureBasePlate(0, 0, 14);
        scene.setSceneOffsetY(0f);
        scene.scaleSceneView(0.75f);

        applyCameraMove(scene, -90f, 6);
        scene.idle(20);

        // initial visible partial baseplate, not full showBasePlate()
        scene.world().showSection(
                util.select().position(4, 0, 5) // minecraft:snow_block
                        .add(util.select().position(5, 0, 5)) // minecraft:snow_block
                        .add(util.select().position(6, 0, 5)) // minecraft:snow_block
                        .add(util.select().position(7, 0, 5)) // minecraft:snow_block
                        .add(util.select().position(8, 0, 5)) // minecraft:snow_block
                        .add(util.select().position(4, 0, 6)) // minecraft:snow_block
                        .add(util.select().position(8, 0, 6)) // minecraft:snow_block
                        .add(util.select().position(4, 0, 7)) // minecraft:snow_block
                        .add(util.select().position(8, 0, 7)) // minecraft:snow_block
                        .add(util.select().position(4, 0, 8)) // minecraft:snow_block
                        .add(util.select().position(8, 0, 8)) // minecraft:snow_block
                        .add(util.select().position(4, 0, 9)) // minecraft:snow_block
                        .add(util.select().position(5, 0, 9)) // minecraft:snow_block
                        .add(util.select().position(6, 0, 9)) // minecraft:snow_block
                        .add(util.select().position(7, 0, 9)) // minecraft:snow_block
                        .add(util.select().position(8, 0, 9)) // minecraft:snow_block
                        .add(util.select().position(3, 0, 6)) // minecraft:white_concrete
                        .add(util.select().position(5, 0, 6)) // minecraft:white_concrete
                        .add(util.select().position(6, 0, 6)) // minecraft:white_concrete
                        .add(util.select().position(7, 0, 6)) // minecraft:white_concrete
                        .add(util.select().position(9, 0, 6)) // minecraft:white_concrete
                        .add(util.select().position(2, 0, 7)) // minecraft:white_concrete
                        .add(util.select().position(3, 0, 7)) // minecraft:white_concrete
                        .add(util.select().position(5, 0, 7)) // minecraft:white_concrete
                        .add(util.select().position(7, 0, 7)) // minecraft:white_concrete
                        .add(util.select().position(9, 0, 7)) // minecraft:white_concrete
                        .add(util.select().position(10, 0, 7)) // minecraft:white_concrete
                        .add(util.select().position(2, 0, 8)) // minecraft:white_concrete
                        .add(util.select().position(3, 0, 8)) // minecraft:white_concrete
                        .add(util.select().position(5, 0, 8)) // minecraft:white_concrete
                        .add(util.select().position(6, 0, 8)) // minecraft:white_concrete
                        .add(util.select().position(7, 0, 8)) // minecraft:white_concrete
                        .add(util.select().position(9, 0, 8)) // minecraft:white_concrete
                        .add(util.select().position(10, 0, 8)) // minecraft:white_concrete
                        .add(util.select().position(2, 0, 9)) // minecraft:white_concrete
                        .add(util.select().position(3, 0, 9)) // minecraft:white_concrete
                        .add(util.select().position(9, 0, 9)) // minecraft:white_concrete
                        .add(util.select().position(10, 0, 9)) // minecraft:white_concrete
                        .add(util.select().position(2, 0, 10)) // minecraft:white_concrete
                        .add(util.select().position(3, 0, 10)) // minecraft:white_concrete
                        .add(util.select().position(4, 0, 10)) // minecraft:white_concrete
                        .add(util.select().position(5, 0, 10)) // minecraft:white_concrete
                        .add(util.select().position(6, 0, 10)) // minecraft:white_concrete
                        .add(util.select().position(7, 0, 10)) // minecraft:white_concrete
                        .add(util.select().position(8, 0, 10)) // minecraft:white_concrete
                        .add(util.select().position(10, 0, 10)) // minecraft:white_concrete
                        .add(util.select().position(2, 0, 11)) // minecraft:white_concrete
                        .add(util.select().position(3, 0, 11)) // minecraft:white_concrete
                        .add(util.select().position(4, 0, 11)) // minecraft:white_concrete
                        .add(util.select().position(5, 0, 11)) // minecraft:white_concrete
                        .add(util.select().position(6, 0, 11)) // minecraft:white_concrete
                        .add(util.select().position(7, 0, 11)) // minecraft:white_concrete
                        .add(util.select().position(8, 0, 11)) // minecraft:white_concrete
                        .add(util.select().position(9, 0, 11)) // minecraft:white_concrete
                        .add(util.select().position(10, 0, 11)) // minecraft:white_concrete
                        .add(util.select().position(2, 0, 12)) // twistermill:signal_steel_block
                        .add(util.select().position(3, 0, 12)) // twistermill:signal_steel_block
                        .add(util.select().position(4, 0, 12)) // twistermill:signal_steel_block
                        .add(util.select().position(5, 0, 12)) // twistermill:signal_steel_block
                        .add(util.select().position(6, 0, 12)) // twistermill:signal_steel_block
                        .add(util.select().position(7, 0, 12)) // twistermill:signal_steel_block
                        .add(util.select().position(8, 0, 12)) // twistermill:signal_steel_block
                        .add(util.select().position(9, 0, 12)) // twistermill:signal_steel_block
                        .add(util.select().position(10, 0, 12)) // twistermill:signal_steel_block
                        .add(util.select().position(6, 0, 7)) // twistermill:signal_steel_block
                        .add(util.select().position(9, 0, 10)), // twistermill:signal_steel_block
                Direction.DOWN
        );
        scene.idle(10);

        scene.world().showSection(
                util.select().position(5, 1, 6)
                        .add(util.select().position(5, 1, 7))
                        .add(util.select().position(5, 1, 8))
                        .add(util.select().position(6, 1, 6))
                        .add(util.select().position(6, 1, 7))
                        .add(util.select().position(6, 1, 8))
                        .add(util.select().position(7, 1, 6))
                        .add(util.select().position(7, 1, 7))
                        .add(util.select().position(7, 1, 8)),
                Direction.DOWN
        );
        scene.idle(10);

        scene.world().showSection(util.select().position(9, 1, 10), Direction.DOWN); // twistermill:control_table_block
        scene.idle(6);

        scene.world().showSection(util.select().position(9, 1, 11), Direction.NORTH); // twistermill:redstone_in_bit_out_block
        scene.world().showSection(util.select().position(10, 1, 10), Direction.WEST); // twistermill:redstone_in_bit_out_block
        scene.world().showSection(util.select().position(8, 1, 10), Direction.EAST); // twistermill:redstone_in_bit_out_bloc
        scene.world().showSection(util.select().position(9, 1, 9), Direction.SOUTH); // twistermill:digital_signal_tx_block
        scene.idle(6);
        scene.world().showSection(util.select().position(7, 1, 10), Direction.EAST); // create:analog_lever
        scene.world().showSection(util.select().position(11, 1, 10), Direction.WEST); // create:analog_lever
        scene.world().showSection(util.select().position(9, 1, 12), Direction.NORTH); // create:analog_lever
        scene.world().showSection(util.select().position(9, 2, 9), Direction.DOWN); // create:redstone_link
        scene.idle(6);



        scene.world().showSection(util.select().position(6, 2, 7), Direction.DOWN); // twistermill:wind_roto_vertical_block
        scene.world().showSection(util.select().position(6, 2, 8), Direction.DOWN); // button
        scene.idle(6);


        scene.world().showSection(util.select().position(6, 2, 6), Direction.SOUTH); // minecraft:smooth_stone_slab
        scene.idle(6);

        scene.overlay().showText(140)
                .text(I18n.get("twistermill.ponder.servo_option_7_ponder.text_1"))
                .pointAt(util.vector().centerOf(6, 2, 7))
                .placeNearTarget();
        scene.idle(145);

        scene.world().showSection(util.select().position(6, 3, 7), Direction.DOWN); // minecraft:iron_block placeholder support block
        scene.world().showSection(util.select().position(6, 4, 7), Direction.DOWN); // minecraft:iron_block placeholder support block
        scene.world().showSection(util.select().position(6, 5, 7), Direction.DOWN); // minecraft:iron_block placeholder support block
        scene.world().showSection(util.select().position(6, 6, 7), Direction.DOWN); // minecraft:iron_block placeholder support block
        scene.world().showSection(util.select().position(6, 7, 4), Direction.DOWN); // minecraft:iron_block placeholder support block
        scene.world().showSection(util.select().position(6, 7, 5), Direction.DOWN); // minecraft:iron_block placeholder support block
        scene.world().showSection(util.select().position(6, 7, 6), Direction.DOWN); // minecraft:iron_block placeholder support block
        scene.world().showSection(util.select().position(6, 7, 7), Direction.DOWN); // minecraft:iron_block placeholder support block
        scene.world().showSection(util.select().position(6, 7, 8), Direction.DOWN); // minecraft:iron_block placeholder support block
        scene.world().showSection(util.select().position(6, 7, 9), Direction.DOWN); // minecraft:iron_block placeholder support block
        scene.world().showSection(util.select().position(6, 6, 10), Direction.UP); // twistermill:twister_sail_block
        scene.world().showSection(util.select().position(6, 6, 11), Direction.UP); // twistermill:twister_sail_block
        scene.world().showSection(util.select().position(6, 7, 10), Direction.WEST); // twistermill:twister_sail_block
        scene.world().showSection(util.select().position(6, 7, 11), Direction.NORTH); // twistermill:twister_sail_block
        scene.world().showSection(util.select().position(6, 8, 9), Direction.SOUTH); // twistermill:twister_sail_block
        scene.world().showSection(util.select().position(6, 8, 10), Direction.EAST); // twistermill:twister_sail_block
        scene.world().showSection(util.select().position(6, 8, 11), Direction.NORTH); // twistermill:twister_sail_block
        scene.world().showSection(util.select().position(6, 9, 10), Direction.WEST); // twistermill:twister_sail_block
        scene.world().showSection(util.select().position(6, 9, 11), Direction.NORTH); // twistermill:twister_sail_blo
        scene.world().showSection(util.select().position(6, 10, 10), Direction.DOWN); // twistermill:twister_sail_block
        scene.world().showSection(util.select().position(6, 10, 11), Direction.DOWN); // twistermill:twister_sail_block// ;
        scene.idle(20);
        scene.world().showSection(util.select().position(6, 8, 5), Direction.DOWN); // create:shaft
        scene.world().showSection(util.select().position(6, 8, 6), Direction.DOWN); // create:clutch
        scene.world().showSection(util.select().position(6, 8, 7), Direction.DOWN); // create:shaft
        scene.world().showSection(util.select().position(6, 8, 8), Direction.DOWN); // create:stressometer
        scene.world().showSection(util.select().position(6, 8, 4), Direction.DOWN); // twistermill:wind_roto_block
        scene.idle(20);
        scene.addKeyframe();
        scene.idle(5);

        scene.overlay().showText(160)
                .text(I18n.get("twistermill.ponder.servo_option_7_ponder.text_2"))
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().centerOf(6, 5, 7));
        scene.idle(165);


        applyCameraMove(scene, -195f, 20);
        scene.idle(22);

        scene.world().showSection(util.select().position(6, 8, 3), Direction.SOUTH); // twistermill:servo_twister_block
        scene.world().showSection(util.select().position(6, 8, 2), Direction.SOUTH); // twistermill:blade_arm_block
        scene.idle(10);


        scene.overlay().showOutline(PonderPalette.BLUE, "servo_top_outline", util.select().position(6, 8, 2), 25);
        scene.idle(20);

        scene.addKeyframe();
        scene.idle(2);

        Selection bladeAFixedSelection = util.select().position(2, 3, 11) // twistermill:servo_twister_block
                .add(util.select().position(2, 3, 12));
        ElementLink<WorldSectionElement> bladeAFixed =
                scene.world().showIndependentSection(bladeAFixedSelection, Direction.UP);
        scene.world().configureCenterOfRotation(bladeAFixed, new Vec3(2.5D, 3.5D, 12.5D));
        scene.world().rotateSection(bladeAFixed, 90.0D, 0.0D, 0.0D, 0);
        scene.world().moveSection(bladeAFixed, new Vec3(4.0D, 7.0D, -10.0D), 0);
        ElementLink<WorldSectionElement> bladeA =
                scene.world().showIndependentSection(util.select().fromTo(1, 3, 5, 3, 3, 10), Direction.UP); // blade sails + copycat steps + rotating top + shaft
        scene.world().configureCenterOfRotation(bladeA, new Vec3(2.5D, 3.5D, 10.5D));
        scene.world().rotateSection(bladeA, 90.0D, 0.0D, 0.0D, 0);
        scene.world().moveSection(bladeA, new Vec3(4.0D, 9.0D, -8.0D), 0);

        scene.addKeyframe();
        scene.idle(2);

        scene.overlay().showOutlineWithText(util.select().position(6, 8, 2), 100)
                .text(I18n.get("twistermill.ponder.servo_option_7_ponder.text_3"))
                .pointAt(util.vector().centerOf(6, 8, 2))
                .placeNearTarget();
        scene.idle(105);

        Selection bladeBFixedSelection = util.select().position(2, 6, 11) // twistermill:servo_twister_block / fixed slot servo
                .add(util.select().position(2, 6, 12)); // minecraft:iron_block / placeholder support block
        ElementLink<WorldSectionElement> bladeBFixed =
                scene.world().showIndependentSection(bladeBFixedSelection, Direction.UP);
        scene.world().configureCenterOfRotation(bladeBFixed, new Vec3(2.5D, 6.5D, 12.5D));
        scene.world().rotateSection(bladeBFixed, -90.0D, -60.0D, 180.0D, 0);
        // keep fixed slot/servo at preview position
        scene.world().moveSection(bladeBFixed, new Vec3(5.732051D, 1.0D, -10.0D), 0);
        ElementLink<WorldSectionElement> bladeB =
                scene.world().showIndependentSection(util.select().fromTo(1, 6, 5, 3, 6, 10), Direction.UP); // blade sails + copycat steps + rotating top + shaft
        // rotating blade uses corrected Servo-top pivot
        scene.world().configureCenterOfRotation(bladeB, new Vec3(2.5D, 6.5D, 10.5D));
        scene.world().rotateSection(bladeB, -90.0D, -60.0D, 180.0D, 0);
        scene.world().moveSection(bladeB, new Vec3(7.464102D, 0.0D, -8.0D), 0);
        scene.overlay().showLine(PonderPalette.GREEN, util.vector().centerOf(6, 8, 2), util.vector().centerOf(8, 8, 2), 70);
        scene.idle(10);

        Selection bladeCFixedSelection = util.select().position(2, 9, 11) // twistermill:servo_twister_block / fixed slot servo
                .add(util.select().position(2, 9, 12)); // minecraft:iron_block / placeholder support block
        ElementLink<WorldSectionElement> bladeCFixed =
                scene.world().showIndependentSection(bladeCFixedSelection, Direction.UP);
        scene.world().configureCenterOfRotation(bladeCFixed, new Vec3(2.5D, 9.5D, 12.5D));
        scene.world().rotateSection(bladeCFixed, -90.0D, 60.0D, 180.0D, 0);
        // keep fixed slot/servo at preview position
        scene.world().moveSection(bladeCFixed, new Vec3(2.267949D, -2.0D, -10.0D), 0);
        ElementLink<WorldSectionElement> bladeC =
                scene.world().showIndependentSection(util.select().fromTo(1, 9, 5, 3, 9, 10), Direction.UP); // blade sails + copycat steps + rotating top + shaft
        // rotating blade uses corrected Servo-top pivot
        scene.world().configureCenterOfRotation(bladeC, new Vec3(2.5D, 9.5D, 10.5D));
        scene.world().rotateSection(bladeC, -90.0D, 60.0D, 180.0D, 0);
        scene.world().moveSection(bladeC, new Vec3(0.535898D, -3.0D, -8.0D), 0);
        scene.overlay().showLine(PonderPalette.GREEN, util.vector().centerOf(6, 8, 2), util.vector().centerOf(4, 8, 2), 70);

        scene.addKeyframe();
        scene.idle(2);

        scene.overlay().showText(50)
                .text(I18n.get("twistermill.ponder.servo_option_7_ponder.text_4"))
                .pointAt(util.vector().centerOf(4, 8, 2))
                .placeNearTarget();
        scene.idle(55);

        // final 120 degree preview spin only rotates top/blade sections
        scene.world().rotateSection(bladeA, 0.0D, 0.0D, 120.0D, 60);
        scene.world().rotateSection(bladeB, 0.0D, 0.0D, 120.0D, 60);
        scene.world().rotateSection(bladeC, 0.0D, 0.0D, 120.0D, 60);

        scene.addKeyframe();
        scene.idle(2);

        scene.overlay().showText(90)
                .text(I18n.get("twistermill.ponder.servo_option_7_ponder.text_5"))
                .pointAt(util.vector().centerOf(6, 8, 3))
                .placeNearTarget();
        scene.idle(95);

        Selection bladePitchServoSelection = util.select().position(2, 3, 11)
                .add(util.select().position(2, 6, 11))
                .add(util.select().position(2, 9, 11));
        scene.world().modifyBlockEntityNBT(
                bladePitchServoSelection,
                ServoTwisterBlockEntity.class,
                tag -> {
                    tag.putInt("ScrollValue", ServoTwisterBlockEntity.MaxAngleOption.DEG_60_LINK.ordinal());
                    tag.putBoolean("InternalRedstoneLinkActive", true);
                    tag.putInt("InternalRedstoneLinkReceivedSignal", 0);
                },
                true
        );

        scene.idle(5);
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
        ElementLink<WorldSectionElement> wrvbYawStatic =
                scene.world().makeSectionIndependent(wrvbYawStaticSelection);

        Selection wrbTopCoreSelection = util.select().position(6, 8, 3)
                .add(util.select().position(6, 8, 2));
        ElementLink<WorldSectionElement> wrbTopCore =
                scene.world().makeSectionIndependent(wrbTopCoreSelection);

        applyCameraMove(scene, -75f, 20);
        scene.idle(22);

        Selection wrbKineticOutputSelection = util.select().position(6, 8, 5) // create:shaft / WRB kinetic output
                .add(util.select().position(6, 8, 6)) // create:clutch / WRB kinetic output
                .add(util.select().position(6, 8, 7)) // create:shaft / WRB kinetic output
                .add(util.select().position(6, 8, 8)); // create:stressometer / WRB kinetic output

        Selection wrbChildOutlineSelection = util.select().position(6, 8, 4) // twistermill:wind_roto_block
                .add(wrbKineticOutputSelection)
                .add(wrbTopCoreSelection);

        scene.idle(2);
        scene.overlay().showOutline(PonderPalette.BLUE, "servo_option7_wrvb_outline", wrvbYawStaticSelection, 80);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "servo_option7_wrb_outline", wrbChildOutlineSelection, 60);
        scene.idle(10);
        scene.overlay().chaseBoundingBoxOutline(
                PonderPalette.GREEN,
                "servo_option7_servo_blade_outline",
                new AABB(-2.318748D, 2.725481D, 0.950962D, 15.318748D, 18.0D, 4.049038D),
                40
        );
        scene.idle(50);

        scene.addKeyframe();
        scene.idle(2);

        scene.addInstruction(NestedTurbineHierarchyInstruction.pitchReset(
                wrvbYawStatic,
                wrbTopCore,
                bladeAFixed,
                bladeBFixed,
                bladeCFixed,
                bladeA,
                bladeB,
                bladeC
        ));
        createScene.world().setKineticSpeed(wrbKineticOutputSelection, 16.0F);


        scene.addInstruction(NestedTurbineHierarchyInstruction.running(
                wrvbYawStatic,
                wrbTopCore,
                bladeAFixed,
                bladeBFixed,
                bladeCFixed,
                bladeA,
                bladeB,
                bladeC
        ));
        createScene.world().setKineticSpeed(wrbKineticOutputSelection, 0.0F);
        scene.idle(10);
    }

    private static int angleForWrbRpm(int ticks) {
        return Math.round(NestedTurbineHierarchyInstruction.WRB_RPM * 360.0f * ticks / 1200.0f);
    }

    private static void applyCameraMove(SceneBuilder scene, float yRotation, int duration) {
        float step = yRotation / duration;
        for (int i = 0; i < duration; i++) {
            scene.rotateCameraY(step);
            scene.idle(1);
        }
    }

    private static final class NestedTurbineHierarchyInstruction extends PonderInstruction {
        private static final int WRB_RPM = 16;
        private static final int PITCH_RESET_TICKS = 60;
        private static final int PITCH_RESET_HOLD_TICKS = 20;
        private static final int PITCH_RESET_TOTAL_TICKS = PITCH_RESET_TICKS + PITCH_RESET_HOLD_TICKS;
        private static final int RUNNING_TOTAL_TICKS = 600;
        private static final int WRVB_TO_POSITIVE_TICKS = 40;
        private static final int HOLD_POSITIVE_TICKS = 60;
        private static final int WRVB_TO_NEGATIVE_TICKS = 70;
        private static final int PITCH_SEQUENCE_TICKS = 330;
        private static final int WRVB_TO_ZERO_TICKS = 60;
        private static final double FINAL_WRVB_YAW_DEGREES = -15.0D;

        private static final int WRVB_TO_POSITIVE_START = 0;
        private static final int HOLD_POSITIVE_START = WRVB_TO_POSITIVE_START + WRVB_TO_POSITIVE_TICKS;
        private static final int WRVB_TO_NEGATIVE_START = HOLD_POSITIVE_START + HOLD_POSITIVE_TICKS;
        private static final int PITCH_SEQUENCE_START = WRVB_TO_NEGATIVE_START + WRVB_TO_NEGATIVE_TICKS;
        private static final int WRVB_TO_ZERO_START = PITCH_SEQUENCE_START + PITCH_SEQUENCE_TICKS;
        private static final int END_HOLD_START = WRVB_TO_ZERO_START + WRVB_TO_ZERO_TICKS;

        private static final Vec3 ZERO_CENTER = Vec3.ZERO;
        private static final Vec3 WRVB_YAW_PIVOT = new Vec3(6.5D, 2.5D, 7.5D);
        private static final Vec3 WRB_SPIN_PIVOT = new Vec3(6.5D, 8.5D, 4.0D);

        private static final SectionPose BLADE_A_FIXED_POSE = new SectionPose(
                new Vec3(2.5D, 3.5D, 12.5D),
                new Vec3(90.0D, 0.0D, 0.0D),
                new Vec3(4.0D, 7.0D, -10.0D)
        );
        private static final SectionPose BLADE_B_FIXED_POSE = new SectionPose(
                new Vec3(2.5D, 6.5D, 12.5D),
                new Vec3(-90.0D, -60.0D, 180.0D),
                new Vec3(5.732051D, 1.0D, -10.0D)
        );
        private static final SectionPose BLADE_C_FIXED_POSE = new SectionPose(
                new Vec3(2.5D, 9.5D, 12.5D),
                new Vec3(-90.0D, 60.0D, 180.0D),
                new Vec3(2.267949D, -2.0D, -10.0D)
        );
        private static final SectionPose BLADE_A_POSE = new SectionPose(
                new Vec3(2.5D, 3.5D, 10.5D),
                new Vec3(90.0D, 0.0D, 0.0D),
                new Vec3(4.0D, 9.0D, -8.0D)
        );
        private static final SectionPose BLADE_B_POSE = new SectionPose(
                new Vec3(2.5D, 6.5D, 10.5D),
                new Vec3(-90.0D, -60.0D, 180.0D),
                new Vec3(7.464102D, 0.0D, -8.0D)
        );
        private static final SectionPose BLADE_C_POSE = new SectionPose(
                new Vec3(2.5D, 9.5D, 10.5D),
                new Vec3(-90.0D, 60.0D, 180.0D),
                new Vec3(0.535898D, -3.0D, -8.0D)
        );

        private enum Mode {
            PITCH_RESET,
            RUNNING
        }

        private final ElementLink<WorldSectionElement> wrvbYawStaticLink;
        private final ElementLink<WorldSectionElement> wrbTopCoreLink;
        private final ElementLink<WorldSectionElement> bladeAFixedLink;
        private final ElementLink<WorldSectionElement> bladeBFixedLink;
        private final ElementLink<WorldSectionElement> bladeCFixedLink;
        private final ElementLink<WorldSectionElement> bladeALink;
        private final ElementLink<WorldSectionElement> bladeBLink;
        private final ElementLink<WorldSectionElement> bladeCLink;
        private final Mode mode;

        private final SectionState wrvbYawStaticState = new SectionState();
        private final SectionState wrbTopCoreState = new SectionState();
        private final SectionState bladeAFixedState = new SectionState();
        private final SectionState bladeBFixedState = new SectionState();
        private final SectionState bladeCFixedState = new SectionState();
        private final SectionState bladeAState = new SectionState();
        private final SectionState bladeBState = new SectionState();
        private final SectionState bladeCState = new SectionState();

        private WorldSectionElement wrvbYawStatic;
        private WorldSectionElement wrbTopCore;
        private WorldSectionElement bladeAFixed;
        private WorldSectionElement bladeBFixed;
        private WorldSectionElement bladeCFixed;
        private WorldSectionElement bladeA;
        private WorldSectionElement bladeB;
        private WorldSectionElement bladeC;

        private int elapsed;
        private boolean initialized;

        private static NestedTurbineHierarchyInstruction pitchReset(
                ElementLink<WorldSectionElement> wrvbYawStaticLink,
                ElementLink<WorldSectionElement> wrbTopCoreLink,
                ElementLink<WorldSectionElement> bladeAFixedLink,
                ElementLink<WorldSectionElement> bladeBFixedLink,
                ElementLink<WorldSectionElement> bladeCFixedLink,
                ElementLink<WorldSectionElement> bladeALink,
                ElementLink<WorldSectionElement> bladeBLink,
                ElementLink<WorldSectionElement> bladeCLink
        ) {
            return new NestedTurbineHierarchyInstruction(
                    wrvbYawStaticLink,
                    wrbTopCoreLink,
                    bladeAFixedLink,
                    bladeBFixedLink,
                    bladeCFixedLink,
                    bladeALink,
                    bladeBLink,
                    bladeCLink,
                    Mode.PITCH_RESET
            );
        }



        private static NestedTurbineHierarchyInstruction running(
                ElementLink<WorldSectionElement> wrvbYawStaticLink,
                ElementLink<WorldSectionElement> wrbTopCoreLink,
                ElementLink<WorldSectionElement> bladeAFixedLink,
                ElementLink<WorldSectionElement> bladeBFixedLink,
                ElementLink<WorldSectionElement> bladeCFixedLink,
                ElementLink<WorldSectionElement> bladeALink,
                ElementLink<WorldSectionElement> bladeBLink,
                ElementLink<WorldSectionElement> bladeCLink
        ) {
            return new NestedTurbineHierarchyInstruction(
                    wrvbYawStaticLink,
                    wrbTopCoreLink,
                    bladeAFixedLink,
                    bladeBFixedLink,
                    bladeCFixedLink,
                    bladeALink,
                    bladeBLink,
                    bladeCLink,
                    Mode.RUNNING
            );
        }

        private NestedTurbineHierarchyInstruction(
                ElementLink<WorldSectionElement> wrvbYawStaticLink,
                ElementLink<WorldSectionElement> wrbTopCoreLink,
                ElementLink<WorldSectionElement> bladeAFixedLink,
                ElementLink<WorldSectionElement> bladeBFixedLink,
                ElementLink<WorldSectionElement> bladeCFixedLink,
                ElementLink<WorldSectionElement> bladeALink,
                ElementLink<WorldSectionElement> bladeBLink,
                ElementLink<WorldSectionElement> bladeCLink,
                Mode mode
        ) {
            this.wrvbYawStaticLink = wrvbYawStaticLink;
            this.wrbTopCoreLink = wrbTopCoreLink;
            this.bladeAFixedLink = bladeAFixedLink;
            this.bladeBFixedLink = bladeBFixedLink;
            this.bladeCFixedLink = bladeCFixedLink;
            this.bladeALink = bladeALink;
            this.bladeBLink = bladeBLink;
            this.bladeCLink = bladeCLink;
            this.mode = mode;
        }

        @Override
        public boolean isBlocking() {
            return true;
        }

        @Override
        public void reset(PonderScene scene) {
            elapsed = 0;
            initialized = false;
            wrvbYawStatic = null;
            wrbTopCore = null;
            bladeAFixed = null;
            bladeBFixed = null;
            bladeCFixed = null;
            bladeA = null;
            bladeB = null;
            bladeC = null;
            wrvbYawStaticState.reset();
            wrbTopCoreState.reset();
            bladeAFixedState.reset();
            bladeBFixedState.reset();
            bladeCFixedState.reset();
            bladeAState.reset();
            bladeBState.reset();
            bladeCState.reset();
        }

        @Override
        public void onScheduled(PonderScene scene) {
            scene.addToSceneTime(totalTicks());
        }

        @Override
        public boolean isComplete() {
            return elapsed > totalTicks();
        }

        @Override
        public void tick(PonderScene scene) {
            if (!initialized) {
                resolveSections(scene);
                initialized = true;
            }

            int tick = Math.min(elapsed, totalTicks());
            boolean force = elapsed == 0;
            updateSections(tick, force);
            elapsed++;
        }

        private int totalTicks() {
            return mode == Mode.PITCH_RESET ? PITCH_RESET_TOTAL_TICKS : RUNNING_TOTAL_TICKS;
        }

        private void resolveSections(PonderScene scene) {
            wrvbYawStatic = scene.resolveOptional(wrvbYawStaticLink).orElse(null);
            wrbTopCore = scene.resolveOptional(wrbTopCoreLink).orElse(null);
            bladeAFixed = scene.resolveOptional(bladeAFixedLink).orElse(null);
            bladeBFixed = scene.resolveOptional(bladeBFixedLink).orElse(null);
            bladeCFixed = scene.resolveOptional(bladeCFixedLink).orElse(null);
            bladeA = scene.resolveOptional(bladeALink).orElse(null);
            bladeB = scene.resolveOptional(bladeBLink).orElse(null);
            bladeC = scene.resolveOptional(bladeCLink).orElse(null);
        }

        private void updateSections(int tick, boolean force) {
            double yawDegrees = mode == Mode.RUNNING ? wrvbYawDegrees(tick) : 0.0D;
            double wrbSpinDegrees = mode == Mode.RUNNING ? angleForWrbRpm(tick) : 0.0D;
            double pitchDegrees = mode == Mode.RUNNING ? runningBladePitchDegrees(tick) : pitchResetDegrees(tick);

            Matrix4f wrvbYawMatrix = rotationAround(WRVB_YAW_PIVOT, yawDegrees, 0.0D);
            Matrix4f wrbSpinMatrix = rotationAround(WRB_SPIN_PIVOT, 0.0D, wrbSpinDegrees);
            Matrix4f wrbHierarchyMatrix = new Matrix4f(wrvbYawMatrix).mul(wrbSpinMatrix);

            applyMatrix(wrvbYawStatic, wrvbYawMatrix, wrvbYawStaticState, force);
            applyMatrix(wrbTopCore, wrbHierarchyMatrix, wrbTopCoreState, force);
            applyMatrix(bladeAFixed, new Matrix4f(wrbHierarchyMatrix).mul(sectionMatrix(BLADE_A_FIXED_POSE, 0.0D)), bladeAFixedState, force);
            applyMatrix(bladeBFixed, new Matrix4f(wrbHierarchyMatrix).mul(sectionMatrix(BLADE_B_FIXED_POSE, 0.0D)), bladeBFixedState, force);
            applyMatrix(bladeCFixed, new Matrix4f(wrbHierarchyMatrix).mul(sectionMatrix(BLADE_C_FIXED_POSE, 0.0D)), bladeCFixedState, force);
            applyMatrix(bladeA, new Matrix4f(wrbHierarchyMatrix).mul(sectionMatrix(BLADE_A_POSE, pitchDegrees)), bladeAState, force);
            applyMatrix(bladeB, new Matrix4f(wrbHierarchyMatrix).mul(sectionMatrix(BLADE_B_POSE, pitchDegrees)), bladeBState, force);
            applyMatrix(bladeC, new Matrix4f(wrbHierarchyMatrix).mul(sectionMatrix(BLADE_C_POSE, pitchDegrees)), bladeCState, force);
        }

        private static double wrvbYawDegrees(int tick) {
            if (tick < WRVB_TO_POSITIVE_START) {
                return 0.0D;
            }
            if (tick < HOLD_POSITIVE_START) {
                return interpolate(0.0D, 45.0D, tick - WRVB_TO_POSITIVE_START, WRVB_TO_POSITIVE_TICKS);
            }
            if (tick < WRVB_TO_NEGATIVE_START) {
                return 45.0D;
            }
            if (tick < PITCH_SEQUENCE_START) {
                return interpolate(45.0D, -75.0D, tick - WRVB_TO_NEGATIVE_START, WRVB_TO_NEGATIVE_TICKS);
            }
            if (tick < WRVB_TO_ZERO_START) {
                return -75.0D;
            }
            if (tick < END_HOLD_START) {
                return interpolate(-75.0D, FINAL_WRVB_YAW_DEGREES, tick - WRVB_TO_ZERO_START, WRVB_TO_ZERO_TICKS);
            }
            return FINAL_WRVB_YAW_DEGREES;
        }

        private static double pitchResetDegrees(int tick) {
            if (tick < PITCH_RESET_TICKS) {
                return interpolate(120.0D, 0.0D, tick, PITCH_RESET_TICKS);
            }
            return 0.0D;
        }

        private static double runningBladePitchDegrees(int tick) {
            if (tick < PITCH_SEQUENCE_START) {
                return 0.0D;
            }

            int pitchTick = tick - PITCH_SEQUENCE_START;
            if (pitchTick < 30) {
                return interpolate(0.0D, 30.0D, pitchTick, 30);
            }
            if (pitchTick < 50) {
                return 30.0D;
            }
            if (pitchTick < 80) {
                return interpolate(30.0D, 60.0D, pitchTick - 50, 30);
            }
            if (pitchTick < 100) {
                return 60.0D;
            }
            if (pitchTick < 180) {
                return interpolate(60.0D, 20.0D, pitchTick - 100, 80);
            }
            if (pitchTick < 200) {
                return 20.0D;
            }
            if (pitchTick < 235) {
                return interpolate(20.0D, 90.0D, pitchTick - 200, 35);
            }
            if (pitchTick < 255) {
                return 90.0D;
            }
            if (pitchTick < 310) {
                return interpolate(90.0D, 0.0D, pitchTick - 255, 55);
            }
            return 0.0D;
        }

        private static double interpolate(double start, double end, int tick, int duration) {
            return start + (end - start) * Math.min(Math.max(tick, 0), duration) / duration;
        }

        private static Matrix4f rotationAround(Vec3 center, double yDegrees, double zDegrees) {
            return new Matrix4f()
                    .translate((float) center.x, (float) center.y, (float) center.z)
                    .rotateX(radians(0.0D))
                    .rotateY(radians(yDegrees))
                    .rotateZ(radians(zDegrees))
                    .translate((float) -center.x, (float) -center.y, (float) -center.z);
        }

        private static Matrix4f sectionMatrix(SectionPose pose, double zRotationOffsetDegrees) {
            Vec3 center = pose.center();
            Vec3 rotation = pose.rotationDegrees();
            Vec3 offset = pose.offset();
            return new Matrix4f()
                    .translate((float) offset.x, (float) offset.y, (float) offset.z)
                    .translate((float) center.x, (float) center.y, (float) center.z)
                    .rotateX(radians(rotation.x))
                    .rotateY(radians(rotation.y))
                    .rotateZ(radians(rotation.z + zRotationOffsetDegrees))
                    .translate((float) -center.x, (float) -center.y, (float) -center.z);
        }

        private static void applyMatrix(WorldSectionElement section, Matrix4f matrix, SectionState state, boolean force) {
            if (section == null) {
                return;
            }

            Vector3f offset = matrix.transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
            Vector3f eulerRadians = matrix.getEulerAnglesXYZ(new Vector3f());
            Vec3 eulerDegrees = new Vec3(
                    Math.toDegrees(eulerRadians.x()),
                    Math.toDegrees(eulerRadians.y()),
                    Math.toDegrees(eulerRadians.z())
            );
            if (!force && state.previousEulerDegrees != null) {
                eulerDegrees = new Vec3(
                        unwrapDegrees(state.previousEulerDegrees.x, eulerDegrees.x),
                        unwrapDegrees(state.previousEulerDegrees.y, eulerDegrees.y),
                        unwrapDegrees(state.previousEulerDegrees.z, eulerDegrees.z)
                );
            }

            section.setCenterOfRotation(ZERO_CENTER);
            section.setAnimatedOffset(new Vec3(offset.x(), offset.y(), offset.z()), force);
            section.setAnimatedRotation(eulerDegrees, force);
            state.previousEulerDegrees = eulerDegrees;
        }

        private static double unwrapDegrees(double previous, double current) {
            while (current - previous > 180.0D) {
                current -= 360.0D;
            }
            while (current - previous < -180.0D) {
                current += 360.0D;
            }
            return current;
        }

        private static float radians(double degrees) {
            return (float) Math.toRadians(degrees);
        }

        private record SectionPose(Vec3 center, Vec3 rotationDegrees, Vec3 offset) {
        }

        private static final class SectionState {
            private Vec3 previousEulerDegrees;

            private void reset() {
                previousEulerDegrees = null;
            }
        }
    }
}
