package com.proventure.twistermill.blockentity;

import com.mojang.logging.LogUtils;
import com.proventure.twistermill.diagnostics.TwisterMillDiagnostics;
import com.proventure.twistermill.util.SableLevelWrapper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class ServoPropellerSlotManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SLOT_COUNT = 3;
    private static final double DEFAULT_SLOT_RADIUS = 2.0D;
    private static final double UP_SLOT_RADIUS = 2.0D;
    private static final double[] SLOT_ANGLES_DEGREES = {0.0D, 120.0D, 240.0D};
    private static final Set<ConstraintJointAxis> LOCKED_AXES = EnumSet.allOf(ConstraintJointAxis.class);
    private static final ConstraintJointAxis[] LINEAR_MOTOR_AXES = {
            ConstraintJointAxis.LINEAR_X,
            ConstraintJointAxis.LINEAR_Y,
            ConstraintJointAxis.LINEAR_Z
    };
    private static final ConstraintJointAxis[] ANGULAR_MOTOR_AXES = {
            ConstraintJointAxis.ANGULAR_X,
            ConstraintJointAxis.ANGULAR_Y,
            ConstraintJointAxis.ANGULAR_Z
    };
    private static final double BASE_STIFFNESS_PER_INERTIA = 1600.0D;
    private static final double BASE_DAMPING_PER_INERTIA = 40.0D;
    private static final double SLOT_LOCK_MULTIPLIER = 4.0D;
    private static final double MIN_EFFECTIVE_SLOT_LOAD = 10.0D;
    private static final double MAX_EFFECTIVE_SLOT_LOAD = 250.0D;
    private static final double LOAD_CHANGE_EPSILON = 1.0E-4D;
    private static final int DIAGNOSTIC_REJOIN_TICKS = 40;
    private static final double DIAGNOSTIC_ANCHOR_ERROR_THRESHOLD = 0.125D;
    private static final double DIAGNOSTIC_NORMAL_ERROR_THRESHOLD = 0.01D;
    private static final String SLOT_SUBLEVEL_NAME_PREFIX = "twistermill_servo_propeller_slot_";
    private static final Set<ServoPropellerSlotManager> ACTIVE_MANAGERS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<UUID> SLOT_FRAME_DIAG_LOGGED_SUBLEVELS =
            Collections.synchronizedSet(new HashSet<>());

    private static final String TAG_PROPELLER_SLOTS = "PropellerSlots";
    private static final String TAG_VIRTUAL_TOP_OCCUPIED = "VirtualTopOccupied";
    private static final String TAG_SLOT_ID_PREFIX = "Slot";
    private static final String TAG_SLOT_ANGLE_PREFIX = "Angle";
    private static final String TAG_SLOT_FACING = "SlotFacing";
    private static final String TAG_ANCHOR_X_PREFIX = "AnchorX";
    private static final String TAG_ANCHOR_Y_PREFIX = "AnchorY";
    private static final String TAG_ANCHOR_Z_PREFIX = "AnchorZ";
    private static final String TAG_TOP_FOLLOW_ACTIVE = "TopFollowActive";
    private static final String TAG_ACTIVE_TOP_ID = "ActiveTopId";
    private static final String TAG_ACTIVE_TOP_ANCHOR_X = "ActiveTopAnchorX";
    private static final String TAG_ACTIVE_TOP_ANCHOR_Y = "ActiveTopAnchorY";
    private static final String TAG_ACTIVE_TOP_ANCHOR_Z = "ActiveTopAnchorZ";

    private final UUID[] slotSubLevelIds = new UUID[SLOT_COUNT];
    private final Vector3d[] slotAnchorLocalCenters = new Vector3d[SLOT_COUNT];
    private final Vector3d[] lastLockedWorldCenters = new Vector3d[SLOT_COUNT];
    private final Quaterniond[] lastLockedOrientations = new Quaterniond[SLOT_COUNT];
    private final GenericConstraintHandle[] slotConstraintHandles = new GenericConstraintHandle[SLOT_COUNT];
    private final UUID[] slotConstraintTopSubLevelIds = new UUID[SLOT_COUNT];
    private final boolean[] slotConstraintAttachedToTop = new boolean[SLOT_COUNT];
    private final double[] lastTunedMasses = new double[SLOT_COUNT];
    private final double[] lastTunedAngularLoads = new double[SLOT_COUNT];
    private final boolean[] pendingDiagnosticLogs = new boolean[SLOT_COUNT];
    private final String[] pendingDiagnosticEvents = new String[SLOT_COUNT];
    private final BlockPos[] pendingDiagnosticPositions = new BlockPos[SLOT_COUNT];
    private UUID activeTopSubLevelId;
    private Vector3d activeTopAnchorLocalCenter;
    private Direction slotFacing = Direction.UP;
    private boolean topFollowActive;
    private boolean activeManagerRegistered;
    private int diagnosticRejoinTicksRemaining;
    private boolean diagnosticReadPending;
    private final Set<CarriedServoTopKey> loggedCarriedServoTopKeys = new HashSet<>();
    private boolean carriedServoTopDiagnosticsWasEnabled;

    ServoPropellerSlotManager() {
    }

    private record CarriedServoTopKey(UUID parentSubLevelId, BlockPos servoPos, @Nullable UUID childTopId) {
    }

    static boolean isPropellerSlotSubLevel(@Nullable SubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved()) {
            return false;
        }

        String name = subLevel.getName();
        return name != null && name.startsWith(SLOT_SUBLEVEL_NAME_PREFIX);
    }

    public static void onPrePhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        stabilizeActiveManagers(event.getPhysicsSystem(), false);
    }

    public static void onPostPhysicsTick(ForgeSablePostPhysicsTickEvent event) {
        stabilizeActiveManagers(event.getPhysicsSystem(), true);
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }
        markChangedSlotForDiagnostics(event.getLevel(), event.getPos(), "place");
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }
        markChangedSlotForDiagnostics(event.getLevel(), event.getPos(), "break");
    }

    private static void stabilizeActiveManagers(SubLevelPhysicsSystem physicsSystem, boolean recomputeFromTopPose) {
        ServerLevel rootLevel = physicsSystem.getLevel();
        PhysicsPipeline pipeline = physicsSystem.getPipeline();
        List<ServoPropellerSlotManager> managers;
        synchronized (ACTIVE_MANAGERS) {
            managers = List.copyOf(ACTIVE_MANAGERS);
        }

        for (ServoPropellerSlotManager manager : managers) {
            if (!manager.hasAnySlot()) {
                manager.unregisterActiveManager();
                continue;
            }
            if (!recomputeFromTopPose && TwisterMillDiagnostics.isServoLoggingEnabled()) {
                manager.logPendingDiagnostics(rootLevel, pipeline, "pre-before-follow", false);
                manager.logRejoinDiagnostics(rootLevel, pipeline, "pre-before-follow", false);
            }
            manager.stabilizeTopFollowingSlots(rootLevel, pipeline, recomputeFromTopPose);
            if (recomputeFromTopPose && TwisterMillDiagnostics.isServoLoggingEnabled()) {
                manager.logRejoinDiagnostics(rootLevel, pipeline, "post-after-follow", true);
                manager.logPendingDiagnostics(rootLevel, pipeline, "post-after-follow", true);
            }
        }
    }

    private static void markChangedSlotForDiagnostics(LevelAccessor level, BlockPos pos, String eventType) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }
        if (!(level instanceof ServerLevel rootLevel)) {
            return;
        }

        List<ServoPropellerSlotManager> managers;
        synchronized (ACTIVE_MANAGERS) {
            managers = List.copyOf(ACTIVE_MANAGERS);
        }

        for (ServoPropellerSlotManager manager : managers) {
            if (!manager.hasAnySlot()) {
                continue;
            }
            manager.markChangedSlot(rootLevel, pos, eventType);
        }
    }

    private void registerActiveManager() {
        if (activeManagerRegistered) {
            return;
        }

        ACTIVE_MANAGERS.add(this);
        activeManagerRegistered = true;
        if (TwisterMillDiagnostics.isServoLoggingEnabled()) {
            diagnosticRejoinTicksRemaining = Math.max(diagnosticRejoinTicksRemaining, DIAGNOSTIC_REJOIN_TICKS);
            LOGGER.info("[PropellerSlotRejoinDiag] event=manager-registered managerRegistered={} hasAnySlot={} topFollowActive={} activeTopSubLevelId={}",
                    activeManagerRegistered,
                    hasAnySlot(),
                    topFollowActive,
                    activeTopSubLevelId);
        }
    }

    void unregisterActiveManager() {
        if (!activeManagerRegistered) {
            return;
        }

        ACTIVE_MANAGERS.remove(this);
        activeManagerRegistered = false;
        diagnosticRejoinTicksRemaining = 0;
        diagnosticReadPending = false;
        resetCarriedServoTopDiagnostics();
    }

    boolean tryPlaceNextSlot(
            ServoTwisterBlockEntity owner,
            ServerLevel rootLevel,
            Player player,
            ItemStack stack,
            BlockItem blockItem,
            Direction servoFacing
    ) {
        if (!setSlotFacing(servoFacing)) {
            return false;
        }

        int slot = findNextAvailableSlot(rootLevel);
        if (slot < 0) {
            return false;
        }

        BlockState blockState = blockItem.getBlock().defaultBlockState();
        if (!isPhaseASupportedBlock(blockState)) {
            return false;
        }

        ServerSubLevel subLevel = createSlotSubLevel(owner, rootLevel, slot, blockState, servoFacing);
        if (subLevel == null) {
            return false;
        }

        slotSubLevelIds[slot] = subLevel.getUniqueId();
        registerActiveManager();
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return true;
    }

    boolean hasAnySlot() {
        for (UUID slotSubLevelId : slotSubLevelIds) {
            if (slotSubLevelId != null) {
                return true;
            }
        }
        return false;
    }

    boolean hasCompleteSlotSet() {
        for (UUID slotSubLevelId : slotSubLevelIds) {
            if (slotSubLevelId == null) {
                return false;
            }
        }
        return true;
    }

    boolean hasActiveTopFollowForPreview() {
        return topFollowActive && activeTopSubLevelId != null && activeTopAnchorLocalCenter != null;
    }

    boolean hasPreviewDataForClient(boolean requiresActiveTopFollow) {
        return hasAnySlot() && (!requiresActiveTopFollow || hasActiveTopFollowForPreview());
    }

    boolean hasSlotForPreview(int slot) {
        return slot >= 0 && slot < SLOT_COUNT && slotSubLevelIds[slot] != null;
    }

    @Nullable
    UUID getSlotSubLevelIdForPreview(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? slotSubLevelIds[slot] : null;
    }

    @Nullable
    Vector3d getSlotAnchorLocalCenterForPreview(int slot) {
        Vector3d anchorLocalCenter = slot >= 0 && slot < SLOT_COUNT ? slotAnchorLocalCenters[slot] : null;
        return anchorLocalCenter == null ? null : new Vector3d(anchorLocalCenter);
    }

    @Nullable
    Vector3d getActiveTopAnchorLocalCenterForPreview() {
        return activeTopAnchorLocalCenter == null ? null : new Vector3d(activeTopAnchorLocalCenter);
    }

    public static int getPreviewSlotCount() {
        return SLOT_COUNT;
    }

    public static Vector3d computePreviewSlotOffset(int slot, Direction facing) {
        if (slot < 0 || slot >= SLOT_COUNT || !isSupportedSlotFacing(facing)) {
            return new Vector3d();
        }
        return new Vector3d(computeSlotFrame(slot, facing).offset());
    }

    public static Quaterniond computePreviewSlotOrientation(int slot, Direction facing) {
        if (slot < 0 || slot >= SLOT_COUNT || !isSupportedSlotFacing(facing)) {
            return new Quaterniond();
        }
        return new Quaterniond(computeSlotFrame(slot, facing).rotation());
    }

    boolean hasRecordedOpenSlot() {
        for (UUID slotSubLevelId : slotSubLevelIds) {
            if (slotSubLevelId == null) {
                return true;
            }
        }
        return false;
    }

    void collectActiveSlotSubLevelIds(Collection<UUID> target) {
        if (target == null) {
            return;
        }

        for (UUID slotSubLevelId : slotSubLevelIds) {
            if (slotSubLevelId != null) {
                target.add(slotSubLevelId);
            }
        }
    }

    void write(CompoundTag tag) {
        CompoundTag slotsTag = new CompoundTag();
        slotsTag.putBoolean(TAG_VIRTUAL_TOP_OCCUPIED, hasAnySlot());
        slotsTag.putString(TAG_SLOT_FACING, slotFacing.getName());

        for (int i = 0; i < SLOT_COUNT; i++) {
            UUID subLevelId = slotSubLevelIds[i];
            if (subLevelId != null) {
                slotsTag.putUUID(TAG_SLOT_ID_PREFIX + i, subLevelId);
                Vector3d anchorLocalCenter = slotAnchorLocalCenters[i];
                if (anchorLocalCenter != null) {
                    slotsTag.putDouble(TAG_ANCHOR_X_PREFIX + i, anchorLocalCenter.x);
                    slotsTag.putDouble(TAG_ANCHOR_Y_PREFIX + i, anchorLocalCenter.y);
                    slotsTag.putDouble(TAG_ANCHOR_Z_PREFIX + i, anchorLocalCenter.z);
                }
            }
            slotsTag.putDouble(TAG_SLOT_ANGLE_PREFIX + i, SLOT_ANGLES_DEGREES[i]);
        }

        if (hasActiveTopFollowForPreview()) {
            slotsTag.putBoolean(TAG_TOP_FOLLOW_ACTIVE, true);
            slotsTag.putUUID(TAG_ACTIVE_TOP_ID, activeTopSubLevelId);
            slotsTag.putDouble(TAG_ACTIVE_TOP_ANCHOR_X, activeTopAnchorLocalCenter.x);
            slotsTag.putDouble(TAG_ACTIVE_TOP_ANCHOR_Y, activeTopAnchorLocalCenter.y);
            slotsTag.putDouble(TAG_ACTIVE_TOP_ANCHOR_Z, activeTopAnchorLocalCenter.z);
        }

        tag.put(TAG_PROPELLER_SLOTS, slotsTag);
    }

    void read(CompoundTag tag) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            clearSlot(i);
        }
        clearTopFollowState();
        resetCarriedServoTopDiagnostics();
        slotFacing = Direction.UP;

        if (!tag.contains(TAG_PROPELLER_SLOTS)) {
            return;
        }

        CompoundTag slotsTag = tag.getCompound(TAG_PROPELLER_SLOTS);
        slotFacing = readSlotFacing(slotsTag);
        for (int i = 0; i < SLOT_COUNT; i++) {
            String key = TAG_SLOT_ID_PREFIX + i;
            if (slotsTag.hasUUID(key)) {
                slotSubLevelIds[i] = slotsTag.getUUID(key);
                String anchorXKey = TAG_ANCHOR_X_PREFIX + i;
                String anchorYKey = TAG_ANCHOR_Y_PREFIX + i;
                String anchorZKey = TAG_ANCHOR_Z_PREFIX + i;
                if (slotsTag.contains(anchorXKey) && slotsTag.contains(anchorYKey) && slotsTag.contains(anchorZKey)) {
                    slotAnchorLocalCenters[i] = new Vector3d(
                            slotsTag.getDouble(anchorXKey),
                            slotsTag.getDouble(anchorYKey),
                            slotsTag.getDouble(anchorZKey)
                    );
                }
            }
        }
        if (slotsTag.getBoolean(TAG_TOP_FOLLOW_ACTIVE)
                && slotsTag.hasUUID(TAG_ACTIVE_TOP_ID)
                && slotsTag.contains(TAG_ACTIVE_TOP_ANCHOR_X)
                && slotsTag.contains(TAG_ACTIVE_TOP_ANCHOR_Y)
                && slotsTag.contains(TAG_ACTIVE_TOP_ANCHOR_Z)) {
            topFollowActive = true;
            activeTopSubLevelId = slotsTag.getUUID(TAG_ACTIVE_TOP_ID);
            activeTopAnchorLocalCenter = new Vector3d(
                    slotsTag.getDouble(TAG_ACTIVE_TOP_ANCHOR_X),
                    slotsTag.getDouble(TAG_ACTIVE_TOP_ANCHOR_Y),
                    slotsTag.getDouble(TAG_ACTIVE_TOP_ANCHOR_Z)
            );
        }
        if (TwisterMillDiagnostics.isServoLoggingEnabled() && hasAnySlot()) {
            diagnosticReadPending = true;
            diagnosticRejoinTicksRemaining = Math.max(diagnosticRejoinTicksRemaining, DIAGNOSTIC_REJOIN_TICKS);
        }
    }

    private boolean setSlotFacing(Direction facing) {
        if (!isSupportedSlotFacing(facing)) {
            return false;
        }
        slotFacing = facing;
        return true;
    }

    private static Direction readSlotFacing(CompoundTag slotsTag) {
        Direction facing = Direction.byName(slotsTag.getString(TAG_SLOT_FACING));
        return isSupportedSlotFacing(facing) ? facing : Direction.UP;
    }

    private static boolean isSupportedSlotFacing(@Nullable Direction facing) {
        return facing == Direction.UP || facing == Direction.DOWN || facing == Direction.NORTH || facing == Direction.SOUTH
                || facing == Direction.EAST || facing == Direction.WEST;
    }

    private static SlotFrame computeSlotFrame(int slot, Direction facing) {
        double theta = Math.toRadians(SLOT_ANGLES_DEGREES[slot]);
        Vector3d axis = axisFromFacing(facing);
        Vector3d basisCos = facing.getAxis() == Direction.Axis.Y
                ? new Vector3d(0.0D, 0.0D, 1.0D)
                : new Vector3d(0.0D, 1.0D, 0.0D);
        Vector3d basisSin = new Vector3d(axis).cross(basisCos).normalize();
        double radius = getSlotRadius(facing);
        Vector3d offset = new Vector3d(basisSin).mul(Math.sin(theta) * radius)
                .add(new Vector3d(basisCos).mul(Math.cos(theta) * radius));
        Quaterniond rotation = new Quaterniond().rotationAxis(theta, axis.x, axis.y, axis.z);
        return new SlotFrame(offset, rotation);
    }

    private static double getSlotRadius(Direction facing) {
        return facing == Direction.UP ? UP_SLOT_RADIUS : DEFAULT_SLOT_RADIUS;
    }

    private static Vector3d axisFromFacing(Direction facing) {
        return new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ()).normalize();
    }

    private int findNextAvailableSlot(ServerLevel rootLevel) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            UUID subLevelId = slotSubLevelIds[i];
            if (subLevelId == null) {
                return i;
            }
            if (resolveSubLevel(rootLevel, subLevelId) == null) {
                clearSlot(i);
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private ServerSubLevel createSlotSubLevel(
            ServoTwisterBlockEntity owner,
            ServerLevel rootLevel,
            int slot,
            BlockState blockState,
            Direction servoFacing
    ) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(rootLevel);
        if (container == null) {
            return null;
        }

        SlotFrame slotFrame = computeSlotFrame(slot, servoFacing);
        Vector3d slotCenter = computeSlotCenter(owner, slotFrame.offset(), servoFacing);
        Quaterniond orientation = new Quaterniond(slotFrame.rotation());

        BlockPos sourceWorldPos = SableLevelWrapper.toWorldPos(owner.getLevel(), owner.getBlockPos().relative(servoFacing));
        if (!rootLevel.getBlockState(sourceWorldPos).isAir()) {
            return null;
        }

        if (!blockState.canSurvive(rootLevel, sourceWorldPos)) {
            return null;
        }

        if (!rootLevel.setBlock(sourceWorldPos, blockState, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE)) {
            return null;
        }

        if (!rootLevel.getBlockState(sourceWorldPos).equals(blockState)) {
            clearTemporaryBlock(rootLevel, sourceWorldPos);
            return null;
        }

        List<BlockPos> capturedBlocks = List.of(sourceWorldPos);
        BoundingBox3i bounds = BoundingBox3i.from(capturedBlocks);
        if (bounds == null) {
            clearTemporaryBlock(rootLevel, sourceWorldPos);
            return null;
        }
        bounds.expand(1, 1, 1);

        ServerSubLevel serverSubLevel = null;
        try {
            serverSubLevel = SubLevelAssemblyHelper.assembleBlocks(rootLevel, sourceWorldPos, capturedBlocks, bounds);
        } catch (Exception ignored) {
        }

        if (serverSubLevel == null || serverSubLevel.isRemoved() || serverSubLevel.getMassTracker().isInvalid()) {
            clearTemporaryBlock(rootLevel, sourceWorldPos);
            if (serverSubLevel != null && !serverSubLevel.isRemoved()) {
                container.removeSubLevel(serverSubLevel, SubLevelRemovalReason.REMOVED);
            }
            return null;
        }

        Vector3d anchorLocalCenter = SableInteractiveContraptionBackend.computeAnchorLocalCenter(serverSubLevel, sourceWorldPos);

        serverSubLevel.setName(SLOT_SUBLEVEL_NAME_PREFIX + slot);
        slotAnchorLocalCenters[slot] = new Vector3d(anchorLocalCenter);
        lastLockedWorldCenters[slot] = new Vector3d(slotCenter);
        lastLockedOrientations[slot] = new Quaterniond(orientation);
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        snapSlotPose(serverSubLevel, anchorLocalCenter, slotCenter, orientation, pipeline);
        if (!ensureRootSlotConstraint(pipeline, serverSubLevel, slot, anchorLocalCenter, slotCenter, orientation)) {
            container.removeSubLevel(serverSubLevel, SubLevelRemovalReason.REMOVED);
            clearSlot(slot);
            return null;
        }

        return serverSubLevel;
    }

    boolean lockRootSlotPoses(ServerLevel rootLevel) {
        if (!hasAnySlot()) {
            unregisterActiveManager();
            return false;
        }

        registerActiveManager();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(rootLevel);
        if (container == null) {
            return false;
        }

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        boolean changed = false;
        for (int i = 0; i < SLOT_COUNT; i++) {
            UUID subLevelId = slotSubLevelIds[i];
            if (subLevelId == null) {
                continue;
            }

            ServerSubLevel subLevel = resolveSubLevel(rootLevel, subLevelId);
            if (subLevel == null) {
                clearSlot(i);
                changed = true;
                continue;
            }

            Vector3d anchorLocalCenter = getOrCreateAnchorLocalCenter(subLevel, i);
            Pose3d pose = subLevel.logicalPose();

            Vector3d worldCenter = lastLockedWorldCenters[i];
            if (worldCenter == null) {
                worldCenter = pose.transformPosition(anchorLocalCenter, new Vector3d());
                lastLockedWorldCenters[i] = new Vector3d(worldCenter);
            }

            Quaterniond orientation = lastLockedOrientations[i];
            if (orientation == null) {
                orientation = new Quaterniond(pose.orientation());
                lastLockedOrientations[i] = new Quaterniond(orientation);
            }

            if (slotConstraintAttachedToTop[i]) {
                worldCenter = pose.transformPosition(anchorLocalCenter, new Vector3d());
                orientation = new Quaterniond(pose.orientation());
                lastLockedWorldCenters[i] = new Vector3d(worldCenter);
                lastLockedOrientations[i] = new Quaterniond(orientation);
            }

            ensureRootSlotConstraint(pipeline, subLevel, i, anchorLocalCenter, worldCenter, orientation);
        }
        return changed;
    }

    boolean updateSlotMotion(
            ServerLevel rootLevel,
            @Nullable ServerSubLevel topSubLevel,
            BlockPos assemblyAnchorPos,
            Direction servoFacing
    ) {
        if (!hasAnySlot()) {
            unregisterActiveManager();
            return false;
        }
        if (!setSlotFacing(servoFacing)) {
            return false;
        }

        registerActiveManager();
        if (topSubLevel == null || topSubLevel.isRemoved()) {
            clearTopFollowState();
            return lockRootSlotPoses(rootLevel);
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(rootLevel);
        if (container == null) {
            clearTopFollowState();
            return lockRootSlotPoses(rootLevel);
        }

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        Vector3d topAnchorLocal = SableInteractiveContraptionBackend.computeAnchorLocalCenter(topSubLevel, assemblyAnchorPos);
        UUID previousTopSubLevelId = activeTopSubLevelId;
        topFollowActive = true;
        activeTopSubLevelId = topSubLevel.getUniqueId();
        activeTopAnchorLocalCenter = new Vector3d(topAnchorLocal);
        boolean topFollowChanged = previousTopSubLevelId == null || !previousTopSubLevelId.equals(activeTopSubLevelId);
        if (topFollowChanged) {
            loggedCarriedServoTopKeys.clear();
        }
        if (TwisterMillDiagnostics.isServoLoggingEnabled() && topFollowChanged) {
            diagnosticRejoinTicksRemaining = Math.max(diagnosticRejoinTicksRemaining, DIAGNOSTIC_REJOIN_TICKS);
            LOGGER.info("[PropellerSlotRejoinDiag] event=top-follow-start managerRegistered={} previousTopSubLevelId={} activeTopSubLevelId={} slotFacing={} activeTopAnchorLocalCenter={}",
                    activeManagerRegistered,
                    previousTopSubLevelId,
                    activeTopSubLevelId,
                    slotFacing,
                    formatVector(activeTopAnchorLocalCenter));
        }
        boolean changed = false;

        for (int i = 0; i < SLOT_COUNT; i++) {
            UUID subLevelId = slotSubLevelIds[i];
            if (subLevelId == null) {
                continue;
            }

            ServerSubLevel slotSubLevel = resolveSubLevel(rootLevel, subLevelId);
            if (slotSubLevel == null) {
                clearSlot(i);
                changed = true;
                continue;
            }

            Vector3d anchorLocalCenter = getOrCreateAnchorLocalCenter(slotSubLevel, i);
            removeSlotConstraint(i);
            applyTopPoseToSlot(pipeline, topSubLevel, slotSubLevel, i, anchorLocalCenter, topAnchorLocal);
        }

        return changed;
    }

    void clearRuntimeConstraints() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            removeSlotConstraint(i);
        }
    }

    private boolean ensureRootSlotConstraint(
            PhysicsPipeline pipeline,
            ServerSubLevel slotSubLevel,
            int slot,
            Vector3d anchorLocalCenter,
            Vector3d worldCenter,
            Quaterniond orientation
    ) {
        if (slotConstraintHandles[slot] != null
                && slotConstraintHandles[slot].isValid()
                && !slotConstraintAttachedToTop[slot]) {
            return updateRootSlotConstraint(pipeline, slotSubLevel, slot, anchorLocalCenter, worldCenter, orientation);
        }

        removeSlotConstraint(slot);
        GenericConstraintConfiguration configuration = new GenericConstraintConfiguration(
                worldCenter,
                anchorLocalCenter,
                orientation,
                new Quaterniond(),
                LOCKED_AXES
        );
        try {
            slotConstraintHandles[slot] = pipeline.addConstraint(null, slotSubLevel, configuration);
        } catch (Exception ignored) {
            slotConstraintHandles[slot] = null;
        }
        slotConstraintAttachedToTop[slot] = false;
        slotConstraintTopSubLevelIds[slot] = null;
        if (slotConstraintHandles[slot] != null && slotConstraintHandles[slot].isValid()) {
            return updateRootSlotConstraint(pipeline, slotSubLevel, slot, anchorLocalCenter, worldCenter, orientation);
        }
        return false;
    }

    private boolean updateRootSlotConstraint(
            PhysicsPipeline pipeline,
            ServerSubLevel slotSubLevel,
            int slot,
            Vector3d anchorLocalCenter,
            Vector3d worldCenter,
            Quaterniond orientation
    ) {
        GenericConstraintHandle handle = slotConstraintHandles[slot];
        if (handle == null || !handle.isValid()) {
            return false;
        }

        try {
            handle.setFrame1(worldCenter, orientation);
            handle.setFrame2(anchorLocalCenter, new Quaterniond());
            handle.setContactsEnabled(false);
            boolean massChanged = configureSlotConstraintMotors(handle, slotSubLevel, slot);
            if (massChanged) {
                pipeline.resetVelocity(slotSubLevel);
            }
            pipeline.wakeUp(slotSubLevel);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean configureSlotConstraintMotors(GenericConstraintHandle handle, ServerSubLevel slotSubLevel, int slot) {
        double mass = sanitizedPositive(slotSubLevel.getMassTracker().getMass(), MIN_EFFECTIVE_SLOT_LOAD);
        double angularLoadRaw = computeAngularLoad(slotSubLevel, mass);
        double linearLoad = clamp(mass, MIN_EFFECTIVE_SLOT_LOAD, MAX_EFFECTIVE_SLOT_LOAD);
        double angularLoad = clamp(angularLoadRaw, MIN_EFFECTIVE_SLOT_LOAD, MAX_EFFECTIVE_SLOT_LOAD);

        double linearStiffness = BASE_STIFFNESS_PER_INERTIA * SLOT_LOCK_MULTIPLIER * linearLoad;
        double linearDamping = BASE_DAMPING_PER_INERTIA * SLOT_LOCK_MULTIPLIER * linearLoad;
        double angularStiffness = BASE_STIFFNESS_PER_INERTIA * SLOT_LOCK_MULTIPLIER * angularLoad;
        double angularDamping = BASE_DAMPING_PER_INERTIA * SLOT_LOCK_MULTIPLIER * angularLoad;

        for (ConstraintJointAxis axis : LINEAR_MOTOR_AXES) {
            handle.setMotor(axis, 0.0D, linearStiffness, linearDamping, false, 0.0D);
        }
        for (ConstraintJointAxis axis : ANGULAR_MOTOR_AXES) {
            handle.setMotor(axis, 0.0D, angularStiffness, angularDamping, false, 0.0D);
        }

        boolean changed = Math.abs(lastTunedMasses[slot] - mass) > LOAD_CHANGE_EPSILON
                || Math.abs(lastTunedAngularLoads[slot] - angularLoadRaw) > LOAD_CHANGE_EPSILON;
        lastTunedMasses[slot] = mass;
        lastTunedAngularLoads[slot] = angularLoadRaw;
        return changed;
    }

    private double computeAngularLoad(ServerSubLevel slotSubLevel, double fallback) {
        Matrix3dc inertia = slotSubLevel.getMassTracker().getInertiaTensor();
        double angularLoad = Math.max(
                Math.max(Math.abs(inertia.m00()), Math.abs(inertia.m11())),
                Math.abs(inertia.m22())
        );
        return sanitizedPositive(angularLoad, fallback);
    }

    private double sanitizedPositive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void snapSlotPose(ServerSubLevel subLevel, Vector3d anchorLocalCenter, Vector3d desiredAnchorWorld, Quaterniond orientation, PhysicsPipeline pipeline) {
        Pose3d oldParentPose = new Pose3d(subLevel.logicalPose());
        Vector3dc centerOfMass = subLevel.getMassTracker().getCenterOfMass();
        Vector3d rotationPoint = isFinite(centerOfMass) ? new Vector3d(centerOfMass) : new Vector3d(anchorLocalCenter);
        Vector3d anchorOffsetFromRotationPoint = new Vector3d(anchorLocalCenter).sub(rotationPoint);
        orientation.transform(anchorOffsetFromRotationPoint);
        Vector3d posePosition = new Vector3d(desiredAnchorWorld).sub(anchorOffsetFromRotationPoint);

        Pose3d pose = subLevel.logicalPose();
        pose.position().set(posePosition);
        pose.orientation().set(orientation);
        pose.rotationPoint().set(rotationPoint);
        pose.scale().set(1.0D);

        pipeline.teleport(subLevel, pose.position(), pose.orientation());
        pose.rotationPoint().set(rotationPoint);
        pose.scale().set(1.0D);
        pipeline.resetVelocity(subLevel);
        subLevel.updateLastPose();
        carryMountedServoTopsWithParentDelta(subLevel, oldParentPose, subLevel.logicalPose(), pipeline);
        logSlotFrameDiagnosticsIfServoParent(
                pipeline,
                subLevel,
                anchorLocalCenter,
                desiredAnchorWorld,
                orientation,
                posePosition,
                rotationPoint,
                centerOfMass
        );
    }

    private void logSlotFrameDiagnosticsIfServoParent(
            PhysicsPipeline pipeline,
            ServerSubLevel subLevel,
            Vector3d anchorLocalCenter,
            Vector3d desiredAnchorWorld,
            Quaterniond orientation,
            Vector3d posePosition,
            Vector3d rotationPoint,
            Vector3dc centerOfMass
    ) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }
        if (!isPropellerSlotSubLevel(subLevel)) {
            return;
        }

        UUID subLevelId = subLevel.getUniqueId();
        if (SLOT_FRAME_DIAG_LOGGED_SUBLEVELS.contains(subLevelId) || !containsServoBlockEntity(subLevel)) {
            return;
        }
        if (!SLOT_FRAME_DIAG_LOGGED_SUBLEVELS.add(subLevelId)) {
            return;
        }

        Vector3d logicalAnchorWorld = subLevel.logicalPose().transformPosition(anchorLocalCenter, new Vector3d());
        LOGGER.info(
                "[PropellerSlotFrameDiag] subLevelName={} subLevelId={} desiredAnchorWorld={} anchorLocalCenter={} centerOfMass={} posePosition={} rotationPoint={} orientation={} logicalPose={} lastPose={} logicalAnchorWorld={} logicalAnchorError={} linearVelocity={} angularVelocity={} velocityResetDuringSnap=true updateLastPoseDuringSnap=true",
                subLevel.getName(),
                subLevelId,
                formatVector(desiredAnchorWorld),
                formatVector(anchorLocalCenter),
                formatVector(centerOfMass),
                formatVector(posePosition),
                formatVector(rotationPoint),
                orientation,
                formatPose(subLevel.logicalPose()),
                formatPose(subLevel.lastPose()),
                formatVector(logicalAnchorWorld),
                formatDouble(distance(desiredAnchorWorld, logicalAnchorWorld)),
                formatVector(readLinearVelocity(pipeline, subLevel)),
                formatVector(readAngularVelocity(pipeline, subLevel))
        );
    }

    private boolean containsServoBlockEntity(ServerSubLevel subLevel) {
        ServerLevel rootLevel = subLevel.getLevel();
        BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosedStream(bounds.toMojang()).map(BlockPos::immutable).toList()) {
            if (rootLevel.getBlockEntity(pos) instanceof ServoTwisterBlockEntity && isBlockInSubLevel(rootLevel, subLevel, pos)) {
                return true;
            }
        }
        return false;
    }

    private void carryMountedServoTopsWithParentDelta(ServerSubLevel parentSubLevel, Pose3dc oldParentPose, Pose3dc newParentPose, PhysicsPipeline pipeline) {
        if (!isPropellerSlotSubLevel(parentSubLevel)) {
            return;
        }

        ServerLevel rootLevel = parentSubLevel.getLevel();
        BoundingBox3ic bounds = parentSubLevel.getPlot().getBoundingBox();
        boolean diagnosticsEnabled = TwisterMillDiagnostics.isServoLoggingEnabled();
        UUID parentSubLevelId = null;
        if (diagnosticsEnabled) {
            updateCarriedServoTopDiagnosticsWindow();
            parentSubLevelId = parentSubLevel.getUniqueId();
        } else {
            clearCarriedServoTopDiagnosticsWindowIfNeeded();
        }
        for (BlockPos pos : BlockPos.betweenClosedStream(bounds.toMojang()).map(BlockPos::immutable).toList()) {
            if (rootLevel.getBlockEntity(pos) instanceof ServoTwisterBlockEntity servo
                    && isBlockInSubLevel(rootLevel, parentSubLevel, pos)) {
                if (diagnosticsEnabled) {
                    UUID childTopId = servo.getActiveServoTopSubLevelIdForPreview();
                    CarriedServoTopKey key = new CarriedServoTopKey(parentSubLevelId, pos.immutable(), childTopId);
                    if (loggedCarriedServoTopKeys.add(key)) {
                        LOGGER.info("[PropellerSlotRejoinDiag] event=carry-mounted-servo-top parentSubLevelId={} servoPos={} childTopId={}",
                                parentSubLevelId,
                                pos,
                                childTopId);
                    }
                }
                servo.carryActiveSableTopWithParentDelta(rootLevel, pipeline, oldParentPose, newParentPose);
            }
        }
    }

    private boolean isBlockInSubLevel(ServerLevel rootLevel, ServerSubLevel expectedSubLevel, BlockPos pos) {
        try {
            SubLevel containing = Sable.HELPER.getContaining(rootLevel, pos);
            return containing instanceof ServerSubLevel serverSubLevel
                    && expectedSubLevel.getUniqueId().equals(serverSubLevel.getUniqueId());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void stabilizeTopFollowingSlots(ServerLevel rootLevel, PhysicsPipeline pipeline, boolean recomputeFromTopPose) {
        if (!topFollowActive || activeTopSubLevelId == null || activeTopAnchorLocalCenter == null) {
            return;
        }

        ServerSubLevel topSubLevel = resolveSubLevel(rootLevel, activeTopSubLevelId);
        if (topSubLevel == null) {
            return;
        }

        for (int i = 0; i < SLOT_COUNT; i++) {
            UUID subLevelId = slotSubLevelIds[i];
            if (subLevelId == null) {
                continue;
            }

            ServerSubLevel slotSubLevel = resolveSubLevel(rootLevel, subLevelId);
            if (slotSubLevel == null) {
                continue;
            }

            Vector3d anchorLocalCenter = getOrCreateAnchorLocalCenter(slotSubLevel, i);
            if (recomputeFromTopPose) {
                applyTopPoseToSlot(pipeline, topSubLevel, slotSubLevel, i, anchorLocalCenter, activeTopAnchorLocalCenter);
                continue;
            }

            Vector3d worldCenter = lastLockedWorldCenters[i];
            Quaterniond orientation = lastLockedOrientations[i];
            if (worldCenter != null && orientation != null) {
                snapSlotPose(slotSubLevel, anchorLocalCenter, worldCenter, orientation, pipeline);
            }
        }
    }

    private void applyTopPoseToSlot(
            PhysicsPipeline pipeline,
            ServerSubLevel topSubLevel,
            ServerSubLevel slotSubLevel,
            int slot,
            Vector3d anchorLocalCenter,
            Vector3d topAnchorLocalCenter
    ) {
        Pose3dc topPose = topSubLevel.logicalPose();
        SlotFrame slotFrame = computeSlotFrame(slot, slotFacing);
        Vector3d localCenter = new Vector3d(topAnchorLocalCenter)
                .add(slotFrame.offset());
        Vector3d worldCenter = topPose.transformPosition(localCenter, new Vector3d());
        Quaterniond orientation = new Quaterniond(topPose.orientation()).mul(slotFrame.rotation());

        lastLockedWorldCenters[slot] = new Vector3d(worldCenter);
        lastLockedOrientations[slot] = new Quaterniond(orientation);
        snapSlotPose(slotSubLevel, anchorLocalCenter, worldCenter, orientation, pipeline);
    }

    private void clearTopFollowState() {
        topFollowActive = false;
        activeTopSubLevelId = null;
        activeTopAnchorLocalCenter = null;
        resetCarriedServoTopDiagnostics();
    }

    private void clearSlot(int slot) {
        removeSlotConstraint(slot);
        slotSubLevelIds[slot] = null;
        slotAnchorLocalCenters[slot] = null;
        lastLockedWorldCenters[slot] = null;
        lastLockedOrientations[slot] = null;
        lastTunedMasses[slot] = 0.0D;
        lastTunedAngularLoads[slot] = 0.0D;
        pendingDiagnosticLogs[slot] = false;
        pendingDiagnosticEvents[slot] = null;
        pendingDiagnosticPositions[slot] = null;
        resetCarriedServoTopDiagnostics();
        clearTopFollowStateIfEmpty();
    }

    private void updateCarriedServoTopDiagnosticsWindow() {
        if (!carriedServoTopDiagnosticsWasEnabled) {
            loggedCarriedServoTopKeys.clear();
            carriedServoTopDiagnosticsWasEnabled = true;
        }
    }

    private void clearCarriedServoTopDiagnosticsWindowIfNeeded() {
        if (carriedServoTopDiagnosticsWasEnabled) {
            loggedCarriedServoTopKeys.clear();
            carriedServoTopDiagnosticsWasEnabled = false;
        }
    }

    private void resetCarriedServoTopDiagnostics() {
        loggedCarriedServoTopKeys.clear();
        carriedServoTopDiagnosticsWasEnabled = false;
    }

    private void clearTopFollowStateIfEmpty() {
        for (UUID slotSubLevelId : slotSubLevelIds) {
            if (slotSubLevelId != null) {
                return;
            }
        }
        clearTopFollowState();
        unregisterActiveManager();
    }

    private void removeSlotConstraint(int slot) {
        GenericConstraintHandle handle = slotConstraintHandles[slot];
        if (handle != null) {
            try {
                if (handle.isValid()) {
                    handle.remove();
                }
            } catch (Exception ignored) {
            }
        }

        slotConstraintHandles[slot] = null;
        slotConstraintAttachedToTop[slot] = false;
        slotConstraintTopSubLevelIds[slot] = null;
    }

    private Vector3d getOrCreateAnchorLocalCenter(ServerSubLevel subLevel, int slot) {
        Vector3d existing = slotAnchorLocalCenters[slot];
        if (existing != null) {
            return existing;
        }

        Vector3d fallback = JOMLConversion.atCenterOf(subLevel.getPlot().getCenterBlock());
        slotAnchorLocalCenters[slot] = fallback;
        return fallback;
    }

    private Vector3d computeSlotCenter(ServoTwisterBlockEntity owner, Vector3d slotOffset, Direction servoFacing) {
        BlockPos topPos = owner.getBlockPos().relative(servoFacing);
        Vector3d topCenter = SableLevelWrapper.toWorldCenter(owner.getLevel(), topPos);
        return topCenter.add(slotOffset, new Vector3d());
    }

    private void markChangedSlot(ServerLevel rootLevel, BlockPos changedPos, String eventType) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            UUID subLevelId = slotSubLevelIds[i];
            if (subLevelId == null) {
                continue;
            }

            ServerSubLevel slotSubLevel = resolveSubLevel(rootLevel, subLevelId);
            if (slotSubLevel == null || !isPosInSlotSubLevel(rootLevel, slotSubLevel, changedPos)) {
                continue;
            }

            pendingDiagnosticLogs[i] = true;
            pendingDiagnosticEvents[i] = eventType;
            pendingDiagnosticPositions[i] = changedPos.immutable();
        }
    }

    private boolean isPosInSlotSubLevel(ServerLevel rootLevel, ServerSubLevel slotSubLevel, BlockPos pos) {
        try {
            SubLevel containing = Sable.HELPER.getContaining(rootLevel, pos);
            if (containing != null && slotSubLevel.getUniqueId().equals(containing.getUniqueId())) {
                return true;
            }
        } catch (Exception ignored) {
        }

        BoundingBox3ic bounds = slotSubLevel.getPlot().getBoundingBox();
        return pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
                && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
                && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ();
    }

    private void logPendingDiagnostics(ServerLevel rootLevel, PhysicsPipeline pipeline, String phase, boolean clearAfterLog) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!pendingDiagnosticLogs[i]) {
                continue;
            }

            UUID subLevelId = slotSubLevelIds[i];
            ServerSubLevel slotSubLevel = subLevelId == null ? null : resolveSubLevel(rootLevel, subLevelId);
            if (slotSubLevel != null) {
                logSlotDiagnostics(rootLevel, pipeline, slotSubLevel, i, phase);
            }

            if (clearAfterLog) {
                pendingDiagnosticLogs[i] = false;
                pendingDiagnosticEvents[i] = null;
                pendingDiagnosticPositions[i] = null;
            }
        }
    }

    private void logRejoinDiagnostics(ServerLevel rootLevel, PhysicsPipeline pipeline, String phase, boolean decrementWindow) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }

        boolean windowActive = diagnosticReadPending || diagnosticRejoinTicksRemaining > 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            UUID subLevelId = slotSubLevelIds[i];
            if (subLevelId == null) {
                continue;
            }

            ServerSubLevel slotSubLevel = resolveSubLevel(rootLevel, subLevelId);
            if (slotSubLevel == null) {
                if (windowActive) {
                    LOGGER.info("[PropellerSlotRejoinDiag] phase={} event=slot-sublevel-missing slot={} managerRegistered={} topFollowActive={} activeTopSubLevelId={} slotSubLevelId={}",
                            phase,
                            i,
                            activeManagerRegistered,
                            topFollowActive,
                            activeTopSubLevelId,
                            subLevelId);
                }
                continue;
            }

            Vector3d anchorLocalCenter = getOrCreateAnchorLocalCenter(slotSubLevel, i);
            Pose3d logicalPose = slotSubLevel.logicalPose();
            Pose3d rapierPose = readRapierPose(pipeline, slotSubLevel);
            Vector3d desiredAnchorWorld = computeDesiredAnchorWorld(rootLevel, i, anchorLocalCenter);
            Vector3d logicalAnchorWorld = logicalPose.transformPosition(anchorLocalCenter, new Vector3d());
            Vector3d rapierAnchorWorld = rapierPose.transformPosition(anchorLocalCenter, new Vector3d());
            double logicalAnchorError = distance(desiredAnchorWorld, logicalAnchorWorld);
            double rapierAnchorError = distance(desiredAnchorWorld, rapierAnchorWorld);
            double normalWorldError = computeNormalWorldError(logicalPose, i);
            boolean thresholdBreach = logicalAnchorError > DIAGNOSTIC_ANCHOR_ERROR_THRESHOLD
                    || rapierAnchorError > DIAGNOSTIC_ANCHOR_ERROR_THRESHOLD
                    || normalWorldError > DIAGNOSTIC_NORMAL_ERROR_THRESHOLD;
            if (!windowActive && !thresholdBreach) {
                continue;
            }

            MassData massData = slotSubLevel.getMassTracker();
            LOGGER.info(
                    "[PropellerSlotRejoinDiag] phase={} event=slot-frame managerRegistered={} readPending={} windowTicks={} topFollowActive={} activeTopSubLevelId={} slot={} slotSubLevelId={} desiredSlotAnchorWorld={} logicalAnchorWorld={} rapierAnchorWorld={} logicalAnchorError={} rapierAnchorError={} normalWorldError={} remainingConstraint={} attachedToTop={} slotConstraintTopSubLevelId={} centerOfMass={} rotationPoint={} linearVelocity={} angularVelocity={}",
                    phase,
                    activeManagerRegistered,
                    diagnosticReadPending,
                    diagnosticRejoinTicksRemaining,
                    topFollowActive,
                    activeTopSubLevelId,
                    i,
                    subLevelId,
                    formatVector(desiredAnchorWorld),
                    formatVector(logicalAnchorWorld),
                    formatVector(rapierAnchorWorld),
                    formatDouble(logicalAnchorError),
                    formatDouble(rapierAnchorError),
                    formatDouble(normalWorldError),
                    hasValidConstraint(i),
                    slotConstraintAttachedToTop[i],
                    slotConstraintTopSubLevelIds[i],
                    formatVector(massData.getCenterOfMass()),
                    formatVector(logicalPose.rotationPoint()),
                    formatVector(readLinearVelocity(pipeline, slotSubLevel)),
                    formatVector(readAngularVelocity(pipeline, slotSubLevel))
            );
        }

        if (decrementWindow && diagnosticRejoinTicksRemaining > 0) {
            diagnosticRejoinTicksRemaining--;
            if (diagnosticRejoinTicksRemaining <= 0) {
                diagnosticReadPending = false;
            }
        }
    }

    private void logSlotDiagnostics(ServerLevel rootLevel, PhysicsPipeline pipeline, ServerSubLevel slotSubLevel, int slot, String phase) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }
        Vector3d anchorLocalCenter = getOrCreateAnchorLocalCenter(slotSubLevel, slot);
        Pose3d logicalPose = slotSubLevel.logicalPose();
        Pose3d rapierPose = readRapierPose(pipeline, slotSubLevel);
        MassData massData = slotSubLevel.getMassTracker();
        Matrix3dc inertia = massData.getInertiaTensor();
        BoundingBox3ic bounds = slotSubLevel.getPlot().getBoundingBox();
        SlotGeometry geometry = measureSlotGeometry(rootLevel, bounds, anchorLocalCenter);
        Vector3d linearVelocity = readLinearVelocity(pipeline, slotSubLevel);
        Vector3d angularVelocity = readAngularVelocity(pipeline, slotSubLevel);
        Vector3d desiredAnchorWorld = computeDesiredAnchorWorld(rootLevel, slot, anchorLocalCenter);
        Vector3d logicalAnchorWorld = logicalPose.transformPosition(anchorLocalCenter, new Vector3d());
        Vector3d rapierAnchorWorld = rapierPose.transformPosition(anchorLocalCenter, new Vector3d());
        double logicalAnchorError = distance(desiredAnchorWorld, logicalAnchorWorld);
        double rapierAnchorError = distance(desiredAnchorWorld, rapierAnchorWorld);
        double normalWorldError = computeNormalWorldError(logicalPose, slot);
        boolean remainingConstraint = hasValidConstraint(slot);
        Vector3dc centerOfMass = massData.getCenterOfMass();
        Vector3d anchorMinusCom = centerOfMass == null
                ? new Vector3d(Double.NaN, Double.NaN, Double.NaN)
                : new Vector3d(anchorLocalCenter).sub(centerOfMass);

        LOGGER.info(
                "[ServoPropellerSlotDiag] phase={} event={} eventPos={} slot={} blocks={} bounds={} lengthMaxAxis={} lengthMaxDistance={} mass={} centerOfMass={} inertiaDiag={} anchorLocalCenter={} rotationPoint={} anchorMinusCom={} logicalPose={} rapierPose={} linearVelocity={} angularVelocity={} managerRegistered={} rejoinWindowTicks={} topFollowActive={} activeTop={} activeTopSubLevelId={} remainingConstraint={} attachedToTop={} slotConstraintTopSubLevelId={} desiredAnchor={} logicalAnchor={} rapierAnchor={} logicalAnchorError={} rapierAnchorError={} normalWorldError={}",
                phase,
                pendingDiagnosticEvents[slot],
                pendingDiagnosticPositions[slot],
                slot,
                geometry.blockCount(),
                formatBounds(bounds),
                formatDouble(geometry.maxAxisOffset()),
                formatDouble(geometry.maxDistance()),
                formatDouble(massData.getMass()),
                formatVector(centerOfMass),
                formatInertiaDiagonal(inertia),
                formatVector(anchorLocalCenter),
                formatVector(logicalPose.rotationPoint()),
                formatVector(anchorMinusCom),
                formatPose(logicalPose),
                formatPose(rapierPose),
                formatVector(linearVelocity),
                formatVector(angularVelocity),
                activeManagerRegistered,
                diagnosticRejoinTicksRemaining,
                topFollowActive,
                activeTopSubLevelId != null,
                activeTopSubLevelId,
                remainingConstraint,
                slotConstraintAttachedToTop[slot],
                slotConstraintTopSubLevelIds[slot],
                formatVector(desiredAnchorWorld),
                formatVector(logicalAnchorWorld),
                formatVector(rapierAnchorWorld),
                formatDouble(logicalAnchorError),
                formatDouble(rapierAnchorError),
                formatDouble(normalWorldError)
        );
    }

    private double computeNormalWorldError(Pose3dc slotPose, int slot) {
        Quaterniond desiredOrientation = lastLockedOrientations[slot];
        if (desiredOrientation == null) {
            return Double.NaN;
        }

        Vector3d desiredNormal = new Vector3d(0.0D, 0.0D, 1.0D);
        desiredOrientation.transform(desiredNormal);
        Vector3d logicalNormal = slotPose.transformNormal(new Vector3d(0.0D, 0.0D, 1.0D), new Vector3d());
        if (desiredNormal.lengthSquared() <= 1.0E-12 || logicalNormal.lengthSquared() <= 1.0E-12) {
            return Double.NaN;
        }
        desiredNormal.normalize();
        logicalNormal.normalize();
        return distance(desiredNormal, logicalNormal);
    }

    private Pose3d readRapierPose(PhysicsPipeline pipeline, ServerSubLevel slotSubLevel) {
        Pose3d rapierPose = new Pose3d(slotSubLevel.logicalPose());
        try {
            pipeline.readPose(slotSubLevel, rapierPose);
        } catch (Exception ignored) {
        }
        return rapierPose;
    }

    private Vector3d readLinearVelocity(PhysicsPipeline pipeline, ServerSubLevel slotSubLevel) {
        try {
            return pipeline.getLinearVelocity(slotSubLevel, new Vector3d());
        } catch (Exception ignored) {
            return new Vector3d(slotSubLevel.latestLinearVelocity);
        }
    }

    private Vector3d readAngularVelocity(PhysicsPipeline pipeline, ServerSubLevel slotSubLevel) {
        try {
            return pipeline.getAngularVelocity(slotSubLevel, new Vector3d());
        } catch (Exception ignored) {
            return new Vector3d(slotSubLevel.latestAngularVelocity);
        }
    }

    private Vector3d computeDesiredAnchorWorld(ServerLevel rootLevel, int slot, Vector3d anchorLocalCenter) {
        if (topFollowActive && activeTopSubLevelId != null && activeTopAnchorLocalCenter != null) {
            ServerSubLevel topSubLevel = resolveSubLevel(rootLevel, activeTopSubLevelId);
            if (topSubLevel != null) {
                SlotFrame slotFrame = computeSlotFrame(slot, slotFacing);
                Vector3d localCenter = new Vector3d(activeTopAnchorLocalCenter)
                        .add(slotFrame.offset());
                return topSubLevel.logicalPose().transformPosition(localCenter, new Vector3d());
            }
        }

        Vector3d lockedWorldCenter = lastLockedWorldCenters[slot];
        return lockedWorldCenter == null ? new Vector3d(Double.NaN, Double.NaN, Double.NaN) : new Vector3d(lockedWorldCenter);
    }

    private SlotGeometry measureSlotGeometry(ServerLevel rootLevel, BoundingBox3ic bounds, Vector3d anchorLocalCenter) {
        int blockCount = 0;
        double maxAxisOffset = 0.0D;
        double maxDistance = 0.0D;

        for (BlockPos pos : BlockPos.betweenClosedStream(bounds.toMojang()).map(BlockPos::immutable).toList()) {
            if (rootLevel.getBlockState(pos).isAir()) {
                continue;
            }

            blockCount++;
            Vector3d delta = JOMLConversion.atCenterOf(pos).sub(anchorLocalCenter);
            maxAxisOffset = Math.max(maxAxisOffset, Math.max(Math.max(Math.abs(delta.x), Math.abs(delta.y)), Math.abs(delta.z)));
            maxDistance = Math.max(maxDistance, delta.length());
        }

        return new SlotGeometry(blockCount, maxAxisOffset, maxDistance);
    }

    private boolean hasValidConstraint(int slot) {
        GenericConstraintHandle handle = slotConstraintHandles[slot];
        if (handle == null) {
            return false;
        }

        try {
            return handle.isValid();
        } catch (Exception ignored) {
            return false;
        }
    }

    private double distance(Vector3dc a, Vector3dc b) {
        if (a == null || b == null) {
            return Double.NaN;
        }
        return new Vector3d(a).sub(b).length();
    }

    private boolean isFinite(@Nullable Vector3dc vector) {
        return vector != null
                && Double.isFinite(vector.x())
                && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    private String formatBounds(BoundingBox3ic bounds) {
        return "min(" + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ() + ")"
                + " max(" + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ() + ")";
    }

    private String formatInertiaDiagonal(Matrix3dc inertia) {
        return "(" + formatDouble(inertia.m00()) + "," + formatDouble(inertia.m11()) + "," + formatDouble(inertia.m22()) + ")";
    }

    private String formatPose(Pose3dc pose) {
        return "{pos=" + formatVector(pose.position()) + ",rot=" + pose.orientation() + "}";
    }

    private String formatVector(@Nullable Vector3dc vector) {
        if (vector == null) {
            return "null";
        }
        return "(" + formatDouble(vector.x()) + "," + formatDouble(vector.y()) + "," + formatDouble(vector.z()) + ")";
    }

    private String formatDouble(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private boolean isPhaseASupportedBlock(BlockState blockState) {
        Block block = blockState.getBlock();
        return !(block instanceof EntityBlock) && !(block instanceof FallingBlock);
    }

    private void clearTemporaryBlock(ServerLevel rootLevel, BlockPos sourceWorldPos) {
        if (!rootLevel.getBlockState(sourceWorldPos).isAir()) {
            rootLevel.setBlock(sourceWorldPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    @Nullable
    private ServerSubLevel resolveSubLevel(ServerLevel rootLevel, UUID subLevelId) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(rootLevel);
        if (container == null) {
            return null;
        }

        SubLevel subLevel = container.getSubLevel(subLevelId);
        if (subLevel instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
            return serverSubLevel;
        }
        return null;
    }

    private record SlotFrame(Vector3d offset, Quaterniond rotation) {
    }

    private record SlotGeometry(int blockCount, double maxAxisOffset, double maxDistance) {
    }
}
