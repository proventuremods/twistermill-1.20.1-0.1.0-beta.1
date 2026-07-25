package com.proventure.twistermill.bearing;

import com.google.common.collect.ImmutableList;
import com.proventure.twistermill.blockentity.WindRotoBlockEntity;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter.ScrollOptionSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.function.Supplier;

public final class WindRotoMovementBehaviour {

    private WindRotoMovementBehaviour() {
    }

    public static void addBehaviours(WindRotoBlockEntity be, List<BlockEntityBehaviour> behaviours) {
        be.prepareWindRotoMovementBehaviour(behaviours);

        ScrollOptionBehaviour<WindmillBearingBlockEntity.RotationDirection> movementDirection =
                new DirectionDisplayBehaviour(
                        CreateLang.translateDirect("contraptions.windmill.rotation_direction"),
                        be,
                        be.getWindRotoMovementModeSlot(),
                        be::getFacingDirectionForMovementGui
                );

        movementDirection.withCallback($ -> be.onWindRotoMovementDirectionChanged());

        be.setWindRotoMovementDirectionBehaviour(movementDirection);
        behaviours.add(movementDirection);
    }

    private static class DirectionDisplayBehaviour
            extends ScrollOptionBehaviour<WindmillBearingBlockEntity.RotationDirection> {

        private static final WindmillBearingBlockEntity.RotationDirection[] NORMAL_LABELS =
                WindmillBearingBlockEntity.RotationDirection.values();

        private static final WindmillBearingBlockEntity.RotationDirection[] SWAPPED_LABELS = {
                WindmillBearingBlockEntity.RotationDirection.COUNTER_CLOCKWISE,
                WindmillBearingBlockEntity.RotationDirection.CLOCKWISE
        };

        private final Supplier<Direction> facingSupplier;

        private DirectionDisplayBehaviour(
                Component label,
                WindRotoBlockEntity be,
                ValueBoxTransform slot,
                Supplier<Direction> facingSupplier
        ) {
            super(WindmillBearingBlockEntity.RotationDirection.class, label, be, slot);
            this.facingSupplier = facingSupplier;
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            WindmillBearingBlockEntity.RotationDirection[] labels =
                    shouldSwapDisplayedDirectionLabels(facingSupplier.get())
                            ? SWAPPED_LABELS
                            : NORMAL_LABELS;

            return new ValueSettingsBoard(
                    label,
                    max,
                    1,
                    ImmutableList.of(Component.literal("Select")),
                    new ScrollOptionSettingsFormatter(labels)
            );
        }

        private static boolean shouldSwapDisplayedDirectionLabels(Direction facing) {
            return facing == Direction.SOUTH
                    || facing == Direction.EAST
                    || facing == Direction.UP;
        }
    }
}