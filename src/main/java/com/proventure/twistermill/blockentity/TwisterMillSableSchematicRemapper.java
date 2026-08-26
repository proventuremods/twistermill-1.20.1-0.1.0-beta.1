package com.proventure.twistermill.blockentity;

import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext.SchematicMapping;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

final class TwisterMillSableSchematicRemapper {

    private static final String TAG_SABLE_ACTIVE = "SableActive";
    private static final String TAG_SABLE_SUBLEVEL_ID = "SableSubLevelId";
    private static final String TAG_RUNNING = "Running";
    private static final String TAG_ASSEMBLE_NEXT_TICK = "AssembleNextTick";
    private static final String TAG_PENDING_DISASSEMBLE_AFTER_ZERO = "PendingDisassembleAfterZero";
    private static final String TAG_PENDING_MODE3_DISASSEMBLY_RETURN = "PendingMode3DisassemblyReturn";
    private static final String TAG_PENDING_EXTENDED_BINARY_DISASSEMBLY_RETURN =
            "PendingExtendedBinaryDisassemblyReturn";
    private static final String TAG_PENDING_LATER_GUI_ROTATION_PROFILE = "PendingLaterGuiRotationProfile";
    private static final String TAG_FREE_BEARING_LIFECYCLE_PHASE = "Mode8LifecyclePhase";
    private static final String TAG_PENDING_DISASSEMBLE_BRAKE = "PendingDisassembleBrake";
    private static final String TAG_PENDING_DISASSEMBLE_CREEP_TO_ZERO = "PendingDisassembleCreepToZero";

    private static final String TAG_BOUND_TO_WIND_ROTO = "BoundToWindRoto";
    private static final String TAG_BOUND_WIND_ROTO_DIMENSION = "BoundWindRotoDimension";
    private static final String TAG_BOUND_WIND_ROTO_POS = "BoundWindRotoPos";
    private static final String TAG_BOUND_SERVO_ORIGINAL_POS = "BoundServoOriginalPos";

    private static final String TAG_BOUND_SERVO_COUNT = "BoundServoCount";
    private static final List<String> BOUND_SERVO_PREFIXES = List.of(
            "BoundServoPos",
            "BoundServoInv",
            "BoundServoAngle",
            "BoundServoBlocks"
    );

    enum OwnerType {
        WIND_ROTO,
        WIND_ROTO_VERTICAL,
        SERVO,
        INV_SERVO
    }

    private TwisterMillSableSchematicRemapper() {
    }

    static void remapForWrite(CompoundTag tag, boolean clientPacket, OwnerType ownerType) {
        SubLevelSchematicSerializationContext context = currentContext(
                clientPacket,
                SubLevelSchematicSerializationContext.Type.SAVE
        );
        if (context != null) {
            remapSerializedTag(tag, ownerType, context);
        }
    }

    static CompoundTag prepareForRead(CompoundTag tag, boolean clientPacket, OwnerType ownerType) {
        SubLevelSchematicSerializationContext context = currentContext(
                clientPacket,
                SubLevelSchematicSerializationContext.Type.PLACE
        );
        if (context == null) {
            return tag;
        }

        CompoundTag remappedTag = tag.copy();
        remapSerializedTag(remappedTag, ownerType, context);
        return remappedTag;
    }

    static Iterable<SubLevel> resolveConnectionDependencies(
            @Nullable Level level,
            Consumer<Collection<UUID>> dependencyCollector
    ) {
        if (level == null) {
            return List.of();
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return List.of();
        }

        Collection<UUID> dependencyIds = new ArrayList<>();
        dependencyCollector.accept(dependencyIds);

        LinkedHashSet<SubLevel> dependencies = new LinkedHashSet<>();
        for (UUID dependencyId : dependencyIds) {
            if (dependencyId == null) {
                continue;
            }
            SubLevel dependency = container.getSubLevel(dependencyId);
            if (dependency != null) {
                dependencies.add(dependency);
            }
        }
        return List.copyOf(dependencies);
    }

    @Nullable
    static SchematicMapping getValidMapping(
            SubLevelSchematicSerializationContext context,
            UUID sourceId
    ) {
        SchematicMapping mapping = context.getMapping(sourceId);
        if (mapping == null || mapping.newUUID() == null) {
            return null;
        }
        return mapping;
    }

    private static void remapSerializedTag(
            CompoundTag tag,
            OwnerType ownerType,
            SubLevelSchematicSerializationContext context
    ) {
        neutralizeRootBindings(tag, ownerType);

        UUID sourceMainId = null;
        UUID mappedMainId = null;
        boolean mappingValid = true;

        if (tag.contains(TAG_SABLE_SUBLEVEL_ID)) {
            if (!tag.hasUUID(TAG_SABLE_SUBLEVEL_ID)) {
                mappingValid = false;
            } else {
                sourceMainId = tag.getUUID(TAG_SABLE_SUBLEVEL_ID);
                SchematicMapping mapping = getValidMapping(context, sourceMainId);
                if (mapping == null) {
                    mappingValid = false;
                } else {
                    mappedMainId = mapping.newUUID();
                    tag.putUUID(TAG_SABLE_SUBLEVEL_ID, mappedMainId);
                }
            }
        } else if (tag.getBoolean(TAG_SABLE_ACTIVE)) {
            mappingValid = false;
        }

        if (mappingValid && ownerType == OwnerType.SERVO) {
            mappingValid = ServoPropellerSlotManager.remapSchematicData(
                    tag,
                    context,
                    sourceMainId,
                    mappedMainId
            );
        }

        if (!mappingValid) {
            failClosed(tag, ownerType);
        }
    }

    @Nullable
    private static SubLevelSchematicSerializationContext currentContext(
            boolean clientPacket,
            SubLevelSchematicSerializationContext.Type requiredType
    ) {
        if (clientPacket) {
            return null;
        }
        SubLevelSchematicSerializationContext context =
                SubLevelSchematicSerializationContext.getCurrentContext();
        return context != null && context.getType() == requiredType ? context : null;
    }

    private static void neutralizeRootBindings(CompoundTag tag, OwnerType ownerType) {
        if (ownerType == OwnerType.SERVO || ownerType == OwnerType.INV_SERVO) {
            tag.putBoolean(TAG_BOUND_TO_WIND_ROTO, false);
            tag.remove(TAG_BOUND_WIND_ROTO_DIMENSION);
            tag.remove(TAG_BOUND_WIND_ROTO_POS);
            tag.remove(TAG_BOUND_SERVO_ORIGINAL_POS);
            return;
        }

        if (ownerType == OwnerType.WIND_ROTO) {
            tag.putInt(TAG_BOUND_SERVO_COUNT, 0);
            for (String key : new ArrayList<>(tag.getAllKeys())) {
                for (String prefix : BOUND_SERVO_PREFIXES) {
                    if (key.startsWith(prefix)) {
                        tag.remove(key);
                        break;
                    }
                }
            }
        }
    }

    private static void failClosed(CompoundTag tag, OwnerType ownerType) {
        tag.putBoolean(TAG_SABLE_ACTIVE, false);
        tag.remove(TAG_SABLE_SUBLEVEL_ID);
        tag.putBoolean(TAG_RUNNING, false);
        tag.putBoolean(TAG_ASSEMBLE_NEXT_TICK, false);

        if (ownerType == OwnerType.WIND_ROTO) {
            tag.putBoolean(TAG_PENDING_DISASSEMBLE_BRAKE, false);
            tag.putBoolean(TAG_PENDING_DISASSEMBLE_CREEP_TO_ZERO, false);
            return;
        }

        tag.putBoolean(TAG_PENDING_DISASSEMBLE_AFTER_ZERO, false);
        if (ownerType == OwnerType.SERVO || ownerType == OwnerType.INV_SERVO) {
            tag.putBoolean(TAG_PENDING_MODE3_DISASSEMBLY_RETURN, false);
            tag.putBoolean(TAG_PENDING_EXTENDED_BINARY_DISASSEMBLY_RETURN, false);
            tag.remove(TAG_PENDING_LATER_GUI_ROTATION_PROFILE);
            tag.remove(TAG_FREE_BEARING_LIFECYCLE_PHASE);
        }
        if (ownerType == OwnerType.SERVO) {
            ServoPropellerSlotManager.clearSchematicData(tag);
        }
    }
}
