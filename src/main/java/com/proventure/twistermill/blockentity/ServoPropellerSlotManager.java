package com.proventure.twistermill.blockentity;

import com.mojang.logging.LogUtils;
import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.block.custom.MetalTraverseBlock;
import com.proventure.twistermill.diagnostics.TwisterMillDiagnostics;
import com.proventure.twistermill.util.SableLevelWrapper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext.SchematicMapping;
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
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;

import java.util.Arrays;
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
    private static final int[][] SLOT_TRIANGLE_EDGES = {
            {0, 1},
            {1, 2},
            {2, 0}
    };
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
    private static final int DIAGNOSTIC_UNKNOWN_RUNTIME_ID = Integer.MIN_VALUE;
    private static final String SLOT_SUBLEVEL_NAME_PREFIX = "twistermill_servo_propeller_slot_";
    private static final Set<ServoPropellerSlotManager> ACTIVE_MANAGERS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

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
    private final PhysicsConstraintHandle[] slotConstraintHandles = new PhysicsConstraintHandle[SLOT_COUNT];
    private final ConstraintParentKind[] slotConstraintParentKinds = new ConstraintParentKind[SLOT_COUNT];
    private final UUID[] slotConstraintParentSubLevelIds = new UUID[SLOT_COUNT];
    private final Vector3d[] slotConstraintFrame1Positions = new Vector3d[SLOT_COUNT];
    private final Quaterniond[] slotConstraintFrame1Orientations = new Quaterniond[SLOT_COUNT];
    private final UUID[] slotConstraintTopSubLevelIds = new UUID[SLOT_COUNT];
    private final boolean[] slotConstraintAttachedToTop = new boolean[SLOT_COUNT];
    private final int[] slotConstraintParentRuntimeIds = new int[SLOT_COUNT];
    private final int[] slotConstraintChildRuntimeIds = new int[SLOT_COUNT];
    private final int[] slotConstraintParentObjectIdentities = new int[SLOT_COUNT];
    private final int[] slotConstraintChildObjectIdentities = new int[SLOT_COUNT];
    private final FixedConstraintHandle[] triangleConstraintHandles =
            new FixedConstraintHandle[SLOT_TRIANGLE_EDGES.length];
    private final UUID[] triangleFirstSubLevelIds = new UUID[SLOT_TRIANGLE_EDGES.length];
    private final UUID[] triangleSecondSubLevelIds = new UUID[SLOT_TRIANGLE_EDGES.length];
    private final int[] triangleFirstRuntimeIds = new int[SLOT_TRIANGLE_EDGES.length];
    private final int[] triangleSecondRuntimeIds = new int[SLOT_TRIANGLE_EDGES.length];
    private final int[] triangleFirstObjectIdentities = new int[SLOT_TRIANGLE_EDGES.length];
    private final int[] triangleSecondObjectIdentities = new int[SLOT_TRIANGLE_EDGES.length];
    private UUID activeTopSubLevelId;
    private Vector3d activeTopAnchorLocalCenter;
    private Direction slotFacing = Direction.UP;
    private boolean topFollowActive;
    private boolean activeManagerRegistered;
    private transient SubLevelPhysicsSystem activePhysicsSystem;
    private BlockPos supportAssemblyAnchorPos;
    private boolean supportBindingInitialized;
    private boolean supportBindingRootWorld;
    private UUID supportSubLevelId;
    private BlockPos diagnosticServoWorldPos;
    private transient boolean freeBearingFailClosedTopFollow;
    private transient boolean freeBearingAllowTopRebind;
    @Nullable
    private transient UUID freeBearingExpectedTopSubLevelId;
    private transient boolean canonicalRebindDiagnosticsArmed;
    private transient boolean canonicalRebindPostSnapshotPending;
    private final transient ConstraintFrameDiagnostics[] canonicalTopPreAddDiagnostics =
            new ConstraintFrameDiagnostics[SLOT_COUNT];
    private final transient ConstraintFrameDiagnostics[] canonicalTrianglePreAddDiagnostics =
            new ConstraintFrameDiagnostics[SLOT_TRIANGLE_EDGES.length];

    ServoPropellerSlotManager() {
        Arrays.fill(slotConstraintParentKinds, ConstraintParentKind.NONE);
        Arrays.fill(slotConstraintParentRuntimeIds, DIAGNOSTIC_UNKNOWN_RUNTIME_ID);
        Arrays.fill(slotConstraintChildRuntimeIds, DIAGNOSTIC_UNKNOWN_RUNTIME_ID);
        Arrays.fill(slotConstraintParentObjectIdentities, DIAGNOSTIC_UNKNOWN_RUNTIME_ID);
        Arrays.fill(slotConstraintChildObjectIdentities, DIAGNOSTIC_UNKNOWN_RUNTIME_ID);
        Arrays.fill(triangleFirstRuntimeIds, DIAGNOSTIC_UNKNOWN_RUNTIME_ID);
        Arrays.fill(triangleSecondRuntimeIds, DIAGNOSTIC_UNKNOWN_RUNTIME_ID);
        Arrays.fill(triangleFirstObjectIdentities, DIAGNOSTIC_UNKNOWN_RUNTIME_ID);
        Arrays.fill(triangleSecondObjectIdentities, DIAGNOSTIC_UNKNOWN_RUNTIME_ID);
    }

    private record ConstraintFrameDiagnostics(
            Vector3d frame1WorldPosition,
            Quaterniond frame1WorldOrientation,
            Vector3d frame2WorldPosition,
            Quaterniond frame2WorldOrientation,
            double positionError,
            double orientationErrorDegrees
    ) {
    }

    private enum ConstraintParentKind {
        NONE,
        ROOT_WORLD,
        SUPPORT_BODY,
        TOP_BODY
    }

    private enum ConstraintEnsureResult {
        TARGET_ACTIVE,
        PREVIOUS_RETAINED,
        UNSECURED
    }

    enum TopFollowReadiness {
        READY,
        RETRYABLE_UNRESOLVED,
        RETRYABLE_REBIND,
        INVALID
    }

    private record ConstraintTarget(
            ConstraintParentKind kind,
            @Nullable ServerSubLevel body,
            Vector3d framePosition,
            Quaterniond frameOrientation
    ) {
    }

    private record SupportBinding(boolean available, @Nullable ServerSubLevel body) {
    }

    private record MotorTuning(
            double linearStiffness,
            double linearDamping,
            double angularStiffness,
            double angularDamping
    ) {
    }

    static boolean isPropellerSlotSubLevel(@Nullable SubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved()) {
            return false;
        }

        String name = subLevel.getName();
        return name != null && name.startsWith(SLOT_SUBLEVEL_NAME_PREFIX);
    }

    @Nullable
    public static Direction getMode7SlotPlacementHelperOutward(
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        if (!state.is(ModBlocks.METAL_TRAVERSE.get())) {
            return null;
        }

        SubLevel containingSubLevel = Sable.HELPER.getContaining(level, pos);
        if (getExactPropellerSlotIndex(containingSubLevel) < 0
                || !(level.getBlockEntity(pos) instanceof WrenchSideCycleBlockEntity sideCycle)) {
            return null;
        }

        return sideCycle.getServoMode7SlotOutward();
    }

    private static int getExactPropellerSlotIndex(@Nullable SubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved()) {
            return -1;
        }

        String name = subLevel.getName();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if ((SLOT_SUBLEVEL_NAME_PREFIX + slot).equals(name)) {
                return slot;
            }
        }
        return -1;
    }

    public static void onPrePhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        stabilizeActiveManagers(event.getPhysicsSystem(), false);
    }

    public static void onPostPhysicsTick(ForgeSablePostPhysicsTickEvent event) {
        capturePendingCanonicalRebindPostSnapshots(event.getPhysicsSystem());
        stabilizeActiveManagers(event.getPhysicsSystem(), true);
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
    }

    private static void stabilizeActiveManagers(SubLevelPhysicsSystem physicsSystem, boolean recomputeFromTopPose) {
        ServerLevel rootLevel = physicsSystem.getLevel();
        PhysicsPipeline pipeline = physicsSystem.getPipeline();
        List<ServoPropellerSlotManager> managers;
        synchronized (ACTIVE_MANAGERS) {
            managers = List.copyOf(ACTIVE_MANAGERS);
        }

        for (ServoPropellerSlotManager manager : managers) {
            if (!manager.isBoundTo(physicsSystem)) {
                continue;
            }
            if (!manager.hasAnySlot()) {
                manager.unregisterActiveManager();
                continue;
            }
            manager.stabilizeTopFollowingSlots(rootLevel, pipeline, recomputeFromTopPose);
        }
    }

    private static void capturePendingCanonicalRebindPostSnapshots(SubLevelPhysicsSystem physicsSystem) {
        ServerLevel rootLevel = physicsSystem.getLevel();
        List<ServoPropellerSlotManager> managers;
        synchronized (ACTIVE_MANAGERS) {
            managers = List.copyOf(ACTIVE_MANAGERS);
        }

        for (ServoPropellerSlotManager manager : managers) {
            if (manager.isBoundTo(physicsSystem)) {
                manager.captureCanonicalRebindPostSnapshot(rootLevel);
            }
        }
    }

    private boolean registerActiveManager(ServerLevel rootLevel) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(rootLevel);
        if (container == null) {
            return false;
        }
        SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        if (physicsSystem == null) {
            return false;
        }

        restoreMissingSlotPlacementHelperMetadata(rootLevel);

        activePhysicsSystem = physicsSystem;
        if (activeManagerRegistered) {
            return true;
        }

        ACTIVE_MANAGERS.add(this);
        activeManagerRegistered = true;
        return true;
    }

    void unregisterActiveManager() {
        resetCanonicalRebindDiagnostics();
        if (!activeManagerRegistered) {
            return;
        }

        ACTIVE_MANAGERS.remove(this);
        activeManagerRegistered = false;
        activePhysicsSystem = null;
    }

    private boolean isBoundTo(SubLevelPhysicsSystem physicsSystem) {
        return activeManagerRegistered && activePhysicsSystem == physicsSystem;
    }

    boolean tryPlaceNextSlot(
            ServoTwisterBlockEntity owner,
            ServerLevel rootLevel,
            Player player,
            ItemStack stack,
            BlockItem blockItem,
            Direction servoFacing
    ) {
        if (!isSupportedSlotFacing(servoFacing)) {
            return false;
        }
        slotFacing = servoFacing;
        updateDiagnosticServoWorldPos(owner.getLevel(), owner.getBlockPos(), "place-slot");

        int slot = findNextAvailableSlot(rootLevel);
        if (slot < 0) {
            return false;
        }

        BlockState blockState = resolveSlotPlacementState(blockItem, servoFacing);
        if (!isPhaseASupportedBlock(blockState)) {
            return false;
        }

        ServerSubLevel subLevel = createSlotSubLevel(owner, rootLevel, slot, blockState, servoFacing);
        if (subLevel == null) {
            return false;
        }

        slotSubLevelIds[slot] = subLevel.getUniqueId();
        registerActiveManager(rootLevel);
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

    boolean hasCompleteDistinctSlotSet() {
        if (!hasCompleteSlotSet()) {
            return false;
        }
        Set<UUID> uniqueSlotIds = new HashSet<>();
        Collections.addAll(uniqueSlotIds, slotSubLevelIds);
        return uniqueSlotIds.size() == SLOT_COUNT;
    }

    void setFreeBearingTopFollowPolicy(
            boolean failClosed,
            @Nullable UUID expectedTopSubLevelId,
            boolean allowTopRebind
    ) {
        freeBearingFailClosedTopFollow = failClosed;
        freeBearingExpectedTopSubLevelId = expectedTopSubLevelId;
        freeBearingAllowTopRebind = failClosed && allowTopRebind;
    }

    TopFollowReadiness inspectTopFollowReadiness(ServerLevel rootLevel, @Nullable UUID expectedTopSubLevelId) {
        if (expectedTopSubLevelId == null || !hasCompleteSlotSet()) {
            return TopFollowReadiness.INVALID;
        }

        Set<UUID> uniqueBodyIds = new HashSet<>();
        uniqueBodyIds.add(expectedTopSubLevelId);
        for (UUID slotSubLevelId : slotSubLevelIds) {
            if (slotSubLevelId == null || !uniqueBodyIds.add(slotSubLevelId)) {
                return TopFollowReadiness.INVALID;
            }
        }
        if (activeTopSubLevelId != null && !expectedTopSubLevelId.equals(activeTopSubLevelId)) {
            return TopFollowReadiness.INVALID;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(rootLevel);
        if (container == null) {
            return TopFollowReadiness.RETRYABLE_UNRESOLVED;
        }

        SubLevel rawTopSubLevel = container.getSubLevel(expectedTopSubLevelId);
        if (rawTopSubLevel == null) {
            return TopFollowReadiness.RETRYABLE_UNRESOLVED;
        }
        if (!(rawTopSubLevel instanceof ServerSubLevel topSubLevel) || topSubLevel.isRemoved()) {
            return TopFollowReadiness.INVALID;
        }
        if (isPropellerSlotSubLevel(topSubLevel)) {
            return TopFollowReadiness.INVALID;
        }

        ServerSubLevel[] resolvedSlots = new ServerSubLevel[SLOT_COUNT];
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            SubLevel rawSlotSubLevel = container.getSubLevel(slotSubLevelIds[slot]);
            if (rawSlotSubLevel == null) {
                return TopFollowReadiness.RETRYABLE_UNRESOLVED;
            }
            if (!(rawSlotSubLevel instanceof ServerSubLevel slotSubLevel) || slotSubLevel.isRemoved()) {
                return TopFollowReadiness.INVALID;
            }
            if (!slotSubLevelIds[slot].equals(slotSubLevel.getUniqueId())) {
                return TopFollowReadiness.INVALID;
            }
            if (!(SLOT_SUBLEVEL_NAME_PREFIX + slot).equals(slotSubLevel.getName())) {
                return TopFollowReadiness.INVALID;
            }
            resolvedSlots[slot] = slotSubLevel;
        }

        if (!topFollowActive
                || activeTopSubLevelId == null
                || activeTopAnchorLocalCenter == null) {
            return TopFollowReadiness.RETRYABLE_REBIND;
        }

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!hasValidConstraint(slot)
                    || !constraintHandleMatchesKind(slotConstraintHandles[slot], ConstraintParentKind.TOP_BODY)) {
                return TopFollowReadiness.RETRYABLE_REBIND;
            }

            UUID storedParentId = slotConstraintParentSubLevelIds[slot];
            UUID storedTopId = slotConstraintTopSubLevelIds[slot];
            if (slotConstraintParentKinds[slot] != ConstraintParentKind.TOP_BODY
                    || !slotConstraintAttachedToTop[slot]) {
                return TopFollowReadiness.RETRYABLE_REBIND;
            }
            if (storedParentId == null || storedTopId == null) {
                return TopFollowReadiness.RETRYABLE_REBIND;
            }
            if (!expectedTopSubLevelId.equals(storedParentId)
                    || !expectedTopSubLevelId.equals(storedTopId)) {
                return TopFollowReadiness.INVALID;
            }

            int expectedParentRuntimeId = getRuntimeIdForDiagnostics(topSubLevel);
            int expectedParentObjectIdentity = getObjectIdentityForDiagnostics(topSubLevel);
            int expectedChildRuntimeId = getRuntimeIdForDiagnostics(resolvedSlots[slot]);
            int expectedChildObjectIdentity = getObjectIdentityForDiagnostics(resolvedSlots[slot]);
            if ((slotConstraintParentRuntimeIds[slot] != DIAGNOSTIC_UNKNOWN_RUNTIME_ID
                    && slotConstraintParentRuntimeIds[slot] != expectedParentRuntimeId)
                    || (slotConstraintParentObjectIdentities[slot] != DIAGNOSTIC_UNKNOWN_RUNTIME_ID
                    && slotConstraintParentObjectIdentities[slot] != expectedParentObjectIdentity)
                    || (slotConstraintChildRuntimeIds[slot] != DIAGNOSTIC_UNKNOWN_RUNTIME_ID
                    && slotConstraintChildRuntimeIds[slot] != expectedChildRuntimeId)
                    || (slotConstraintChildObjectIdentities[slot] != DIAGNOSTIC_UNKNOWN_RUNTIME_ID
                    && slotConstraintChildObjectIdentities[slot] != expectedChildObjectIdentity)) {
                return TopFollowReadiness.INVALID;
            }
        }

        for (int edge = 0; edge < SLOT_TRIANGLE_EDGES.length; edge++) {
            FixedConstraintHandle handle = triangleConstraintHandles[edge];
            if (!isConstraintHandleValid(handle)) {
                return TopFollowReadiness.RETRYABLE_REBIND;
            }

            int firstSlot = SLOT_TRIANGLE_EDGES[edge][0];
            int secondSlot = SLOT_TRIANGLE_EDGES[edge][1];
            ServerSubLevel firstSubLevel = resolvedSlots[firstSlot];
            ServerSubLevel secondSubLevel = resolvedSlots[secondSlot];
            UUID expectedFirstId = slotSubLevelIds[firstSlot];
            UUID expectedSecondId = slotSubLevelIds[secondSlot];
            if (triangleFirstSubLevelIds[edge] == null || triangleSecondSubLevelIds[edge] == null) {
                return TopFollowReadiness.RETRYABLE_REBIND;
            }
            if (!expectedFirstId.equals(triangleFirstSubLevelIds[edge])
                    || !expectedSecondId.equals(triangleSecondSubLevelIds[edge])) {
                return TopFollowReadiness.INVALID;
            }
            if (triangleFirstRuntimeIds[edge] != getRuntimeIdForDiagnostics(firstSubLevel)
                    || triangleSecondRuntimeIds[edge] != getRuntimeIdForDiagnostics(secondSubLevel)
                    || triangleFirstObjectIdentities[edge] != getObjectIdentityForDiagnostics(firstSubLevel)
                    || triangleSecondObjectIdentities[edge] != getObjectIdentityForDiagnostics(secondSubLevel)) {
                return TopFollowReadiness.INVALID;
            }
        }

        return TopFollowReadiness.READY;
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

    static boolean remapSchematicData(
            CompoundTag tag,
            SubLevelSchematicSerializationContext context,
            @Nullable UUID sourceTopId,
            @Nullable UUID mappedTopId
    ) {
        if (!tag.contains(TAG_PROPELLER_SLOTS)) {
            return true;
        }
        if (!tag.contains(TAG_PROPELLER_SLOTS, Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag slotsTag = tag.getCompound(TAG_PROPELLER_SLOTS);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            String idKey = TAG_SLOT_ID_PREFIX + slot;
            String anchorXKey = TAG_ANCHOR_X_PREFIX + slot;
            String anchorYKey = TAG_ANCHOR_Y_PREFIX + slot;
            String anchorZKey = TAG_ANCHOR_Z_PREFIX + slot;
            boolean hasIdKey = slotsTag.contains(idKey);
            boolean hasAnyAnchor = slotsTag.contains(anchorXKey)
                    || slotsTag.contains(anchorYKey)
                    || slotsTag.contains(anchorZKey);

            if (!hasIdKey) {
                if (hasAnyAnchor) {
                    return false;
                }
                continue;
            }
            if (!slotsTag.hasUUID(idKey)
                    || !slotsTag.contains(anchorXKey)
                    || !slotsTag.contains(anchorYKey)
                    || !slotsTag.contains(anchorZKey)) {
                return false;
            }

            UUID sourceSlotId = slotsTag.getUUID(idKey);
            SchematicMapping mapping = TwisterMillSableSchematicRemapper.getValidMapping(context, sourceSlotId);
            if (mapping == null || !remapAnchor(slotsTag, anchorXKey, anchorYKey, anchorZKey, mapping)) {
                return false;
            }
            slotsTag.putUUID(idKey, mapping.newUUID());
        }

        boolean topFollowActive = slotsTag.getBoolean(TAG_TOP_FOLLOW_ACTIVE);
        boolean hasTopIdKey = slotsTag.contains(TAG_ACTIVE_TOP_ID);
        boolean hasAnyTopAnchor = slotsTag.contains(TAG_ACTIVE_TOP_ANCHOR_X)
                || slotsTag.contains(TAG_ACTIVE_TOP_ANCHOR_Y)
                || slotsTag.contains(TAG_ACTIVE_TOP_ANCHOR_Z);
        if (!topFollowActive) {
            return !hasTopIdKey && !hasAnyTopAnchor;
        }
        if (!hasTopIdKey
                || !slotsTag.hasUUID(TAG_ACTIVE_TOP_ID)
                || !slotsTag.contains(TAG_ACTIVE_TOP_ANCHOR_X)
                || !slotsTag.contains(TAG_ACTIVE_TOP_ANCHOR_Y)
                || !slotsTag.contains(TAG_ACTIVE_TOP_ANCHOR_Z)) {
            return false;
        }

        UUID activeTopId = slotsTag.getUUID(TAG_ACTIVE_TOP_ID);
        if (sourceTopId == null || mappedTopId == null || !sourceTopId.equals(activeTopId)) {
            return false;
        }
        SchematicMapping topMapping = TwisterMillSableSchematicRemapper.getValidMapping(context, activeTopId);
        if (topMapping == null
                || !mappedTopId.equals(topMapping.newUUID())
                || !remapAnchor(
                slotsTag,
                TAG_ACTIVE_TOP_ANCHOR_X,
                TAG_ACTIVE_TOP_ANCHOR_Y,
                TAG_ACTIVE_TOP_ANCHOR_Z,
                topMapping
        )) {
            return false;
        }
        slotsTag.putUUID(TAG_ACTIVE_TOP_ID, topMapping.newUUID());
        return true;
    }

    static void clearSchematicData(CompoundTag tag) {
        tag.remove(TAG_PROPELLER_SLOTS);
    }

    private static boolean remapAnchor(
            CompoundTag slotsTag,
            String xKey,
            String yKey,
            String zKey,
            SchematicMapping mapping
    ) {
        if (mapping.transform() == null) {
            return false;
        }

        double anchorX = slotsTag.getDouble(xKey);
        double anchorY = slotsTag.getDouble(yKey);
        double anchorZ = slotsTag.getDouble(zKey);
        if (!Double.isFinite(anchorX) || !Double.isFinite(anchorY) || !Double.isFinite(anchorZ)) {
            return false;
        }

        BlockPos sourceBlock = BlockPos.containing(anchorX, anchorY, anchorZ);
        BlockPos mappedBlock = mapping.transform().get(sourceBlock);
        if (mappedBlock == null) {
            return false;
        }

        slotsTag.putDouble(xKey, anchorX + mappedBlock.getX() - sourceBlock.getX());
        slotsTag.putDouble(yKey, anchorY + mappedBlock.getY() - sourceBlock.getY());
        slotsTag.putDouble(zKey, anchorZ + mappedBlock.getZ() - sourceBlock.getZ());
        return true;
    }

    void read(CompoundTag tag) {
        resetCanonicalRebindDiagnostics();
        for (int i = 0; i < SLOT_COUNT; i++) {
            clearSlot(i, "nbt-read-reset");
        }
        clearTopFollowState();
        clearSupportBinding();
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
        canonicalRebindDiagnosticsArmed = TwisterMillDiagnostics.isServoLoggingEnabled()
                && topFollowActive
                && hasCompleteDistinctSlotSet();
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
        Vector3d basisCos = axisFromFacing(computeSlotLocalOutward(facing));
        Vector3d basisSin = new Vector3d(axis).cross(basisCos).normalize();
        double radius = getSlotRadius(facing);
        Vector3d offset = new Vector3d(basisSin).mul(Math.sin(theta) * radius)
                .add(new Vector3d(basisCos).mul(Math.cos(theta) * radius));
        Quaterniond rotation = new Quaterniond().rotationAxis(theta, axis.x, axis.y, axis.z);
        return new SlotFrame(offset, rotation);
    }

    private static Direction computeSlotLocalOutward(Direction facing) {
        return facing.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
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
                clearSlot(i, "available-slot-sublevel-missing");
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

        BlockPos assemblyAnchorPos = owner.getBlockPos().relative(servoFacing);
        if (!refreshSupportBinding(rootLevel, assemblyAnchorPos, "create-slot")) {
            return null;
        }
        SlotFrame slotFrame = computeSlotFrame(slot, servoFacing);
        ConstraintTarget supportTarget = createCanonicalSupportTarget(rootLevel, slotFrame, assemblyAnchorPos);
        if (supportTarget == null) {
            return null;
        }
        Vector3d slotCenter = targetWorldPosition(supportTarget);
        Quaterniond orientation = targetWorldOrientation(supportTarget);

        BlockPos sourceWorldPos = SableLevelWrapper.toWorldPos(owner.getLevel(), owner.getBlockPos().relative(servoFacing));
        if (!rootLevel.getBlockState(sourceWorldPos).isAir()) {
            return null;
        }

        if (!blockState.canSurvive(rootLevel, sourceWorldPos)) {
            return null;
        }

        int temporaryBlockUpdateFlags = getTemporaryBlockUpdateFlags(blockState);
        if (!rootLevel.setBlock(sourceWorldPos, blockState, temporaryBlockUpdateFlags)) {
            return null;
        }

        if (!rootLevel.getBlockState(sourceWorldPos).equals(blockState)) {
            clearTemporaryBlock(rootLevel, sourceWorldPos, blockState);
            return null;
        }

        List<BlockPos> capturedBlocks = List.of(sourceWorldPos);
        BoundingBox3i bounds = BoundingBox3i.from(capturedBlocks);
        if (bounds == null) {
            clearTemporaryBlock(rootLevel, sourceWorldPos, blockState);
            return null;
        }
        bounds.expand(1, 1, 1);

        ServerSubLevel serverSubLevel = null;
        try {
            if (isTemporaryMetalTraverse(blockState)) {
                serverSubLevel = MetalTraverseBlock.runWithoutTraverseHideCornerBreakHistory(
                        rootLevel,
                        sourceWorldPos,
                        () -> SubLevelAssemblyHelper.assembleBlocks(rootLevel, sourceWorldPos, capturedBlocks, bounds)
                );
            } else {
                serverSubLevel = SubLevelAssemblyHelper.assembleBlocks(
                        rootLevel, sourceWorldPos, capturedBlocks, bounds);
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to assemble servo propeller slot {} at {}", slot, sourceWorldPos, exception);
        }

        if (serverSubLevel == null || serverSubLevel.isRemoved() || serverSubLevel.getMassTracker().isInvalid()) {
            clearTemporaryBlock(rootLevel, sourceWorldPos, blockState);
            if (serverSubLevel != null && !serverSubLevel.isRemoved()) {
                container.removeSubLevel(serverSubLevel, SubLevelRemovalReason.REMOVED);
            }
            return null;
        }

        Vector3d anchorLocalCenter = SableInteractiveContraptionBackend.computeAnchorLocalCenter(serverSubLevel, sourceWorldPos);

        serverSubLevel.setName(SLOT_SUBLEVEL_NAME_PREFIX + slot);
        if (isTemporaryMetalTraverse(blockState)
                && !ensureSlotPlacementHelperMetadata(
                rootLevel, serverSubLevel, slot, anchorLocalCenter, servoFacing)) {
            container.removeSubLevel(serverSubLevel, SubLevelRemovalReason.REMOVED);
            return null;
        }
        slotAnchorLocalCenters[slot] = new Vector3d(anchorLocalCenter);
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        snapSlotPose(serverSubLevel, anchorLocalCenter, slotCenter, orientation, pipeline);
        if (ensureSlotConstraint(
                pipeline,
                supportTarget,
                serverSubLevel,
                slot,
                anchorLocalCenter,
                "create-root"
        ) != ConstraintEnsureResult.TARGET_ACTIVE) {
            container.removeSubLevel(serverSubLevel, SubLevelRemovalReason.REMOVED);
            clearSlot(slot, "create-root-constraint-failed");
            return null;
        }

        return serverSubLevel;
    }

    boolean lockRootSlotPoses(ServerLevel rootLevel) {
        if (!hasAnySlot()) {
            unregisterActiveManager();
            return false;
        }

        if (!registerActiveManager(rootLevel)) {
            return false;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(rootLevel);
        if (container == null) {
            return false;
        }

        if (container.physicsSystem() != activePhysicsSystem) {
            return false;
        }
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        SupportBinding supportBinding = resolveRememberedSupport(rootLevel);
        if (!supportBinding.available()) {
            logSupportUnavailable("lock-support-unavailable", rootLevel);
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < SLOT_COUNT; i++) {
            UUID subLevelId = slotSubLevelIds[i];
            if (subLevelId == null) {
                continue;
            }

            ServerSubLevel subLevel = resolveSubLevel(rootLevel, subLevelId);
            if (subLevel == null) {
                clearSlot(i, "lock-root-slot-sublevel-missing");
                changed = true;
                continue;
            }

            Vector3d anchorLocalCenter = getOrCreateAnchorLocalCenter(subLevel, i);
            ConstraintTarget supportTarget = createSupportTargetAtCurrentPose(supportBinding, subLevel, i, anchorLocalCenter);
            ConstraintEnsureResult result = ensureSlotConstraint(
                    pipeline,
                    supportTarget,
                    subLevel,
                    i,
                    anchorLocalCenter,
                    "lock-support"
            );
            if (result == ConstraintEnsureResult.UNSECURED) {
                logSupportUnavailable("lock-support-constraint-failed", rootLevel);
            }
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
        if (!isSupportedSlotFacing(servoFacing)) {
            return false;
        }
        slotFacing = servoFacing;
        if (!refreshSupportBinding(rootLevel, assemblyAnchorPos, "update-slot-motion")) {
            logSupportUnavailable("update-support-unavailable", rootLevel);
        }
        updateDiagnosticServoWorldPos(
                rootLevel,
                assemblyAnchorPos.relative(servoFacing.getOpposite()),
                "update-slot-motion"
        );

        if (!registerActiveManager(rootLevel)) {
            return false;
        }
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
        if (TwisterMillDiagnostics.isServoLoggingEnabled() && topFollowChanged) {
            LOGGER.info("[PropellerSlotConstraintDiag] event=top-follow-start managerRegistered={} previousTopSubLevelId={} activeTopSubLevelId={} slotFacing={} activeTopAnchorLocalCenter={}",
                    activeManagerRegistered,
                    previousTopSubLevelId,
                    activeTopSubLevelId,
                    slotFacing,
                    formatVector(activeTopAnchorLocalCenter));
        }
        boolean changed = false;
        boolean topConstraintsReady = hasCompleteDistinctSlotSet();
        ServerSubLevel[] resolvedSlots = new ServerSubLevel[SLOT_COUNT];

        for (int i = 0; i < SLOT_COUNT; i++) {
            UUID subLevelId = slotSubLevelIds[i];
            if (subLevelId == null) {
                topConstraintsReady = false;
                continue;
            }

            ServerSubLevel slotSubLevel = resolveSubLevel(rootLevel, subLevelId);
            if (slotSubLevel == null) {
                clearSlot(i, "top-follow-slot-sublevel-missing");
                changed = true;
                topConstraintsReady = false;
                continue;
            }
            resolvedSlots[i] = slotSubLevel;

            Vector3d anchorLocalCenter = getOrCreateAnchorLocalCenter(slotSubLevel, i);
            ConstraintEnsureResult result = ensureTopSlotConstraint(
                    pipeline,
                    topSubLevel,
                    slotSubLevel,
                    i,
                    anchorLocalCenter,
                    "update-slot-motion-top"
            );
            if (result == ConstraintEnsureResult.UNSECURED) {
                secureSupportSlotAtCurrentPose(rootLevel, pipeline, slotSubLevel, i, anchorLocalCenter,
                        "update-slot-motion-top-fallback");
            }
            if (result != ConstraintEnsureResult.TARGET_ACTIVE
                    || !isCurrentTopSlotConstraint(topSubLevel, slotSubLevel, i)) {
                topConstraintsReady = false;
            }
        }

        if (topConstraintsReady) {
            ensureSlotTriangle(pipeline, topSubLevel, resolvedSlots, "update-slot-motion-triangle");
        } else {
            removeTriangleConstraints("update-slot-motion-triangle-not-ready");
        }

        return changed;
    }

    boolean updateFreeBearingSlotMotionFailClosed(
            ServerLevel rootLevel,
            UUID expectedTopSubLevelId,
            BlockPos assemblyAnchorPos,
            Direction servoFacing,
            boolean allowTopRebind
    ) {
        TopFollowReadiness readiness = inspectTopFollowReadiness(rootLevel, expectedTopSubLevelId);
        if (readiness == TopFollowReadiness.RETRYABLE_UNRESOLVED
                || readiness == TopFollowReadiness.INVALID
                || (readiness == TopFollowReadiness.RETRYABLE_REBIND && !allowTopRebind)) {
            return false;
        }

        ServerSubLevel topSubLevel = resolveSubLevel(rootLevel, expectedTopSubLevelId);
        if (topSubLevel == null) {
            return false;
        }
        return updateSlotMotion(rootLevel, topSubLevel, assemblyAnchorPos, servoFacing);
    }

    void clearRuntimeConstraints() {
        resetCanonicalRebindDiagnostics();
        removeTriangleConstraints("runtime-cleanup");
        for (int i = 0; i < SLOT_COUNT; i++) {
            removeSlotConstraint(i, "runtime-cleanup");
        }
    }

    private ConstraintEnsureResult ensureTopSlotConstraint(
            PhysicsPipeline pipeline,
            ServerSubLevel topSubLevel,
            ServerSubLevel slotSubLevel,
            int slot,
            Vector3d anchorLocalCenter,
            String reason
    ) {
        ConstraintTarget target = createCanonicalTopTarget(topSubLevel, slot);

        return ensureSlotConstraint(
                pipeline,
                target,
                slotSubLevel,
                slot,
                anchorLocalCenter,
                reason
        );
    }

    private ConstraintEnsureResult ensureSlotConstraint(
            PhysicsPipeline pipeline,
            ConstraintTarget target,
            ServerSubLevel slotSubLevel,
            int slot,
            Vector3d anchorLocalCenter,
            String reason
    ) {
        if (slotConstraintMatchesParent(slot, target, slotSubLevel)) {
            return updateSlotConstraint(
                    pipeline,
                    target,
                    slotSubLevel,
                    slot,
                    anchorLocalCenter,
                    reason + ":existing-handle"
            ) ? ConstraintEnsureResult.TARGET_ACTIVE : constraintFailureResult(slot);
        }

        logConstraintState(
                "ensure-parent-mismatch",
                reason,
                slot,
                target.body(),
                slotSubLevel,
                target.framePosition(),
                target.frameOrientation(),
                anchorLocalCenter,
                new Quaterniond(),
                "targetKind=" + target.kind() + ", previous handle state is included in handle fields"
        );
        PhysicsConstraintHandle previousHandle = slotConstraintHandles[slot];
        PhysicsConstraintHandle candidate;
        try {
            if (target.kind() == ConstraintParentKind.TOP_BODY) {
                FixedConstraintConfiguration configuration = new FixedConstraintConfiguration(
                        target.framePosition(),
                        anchorLocalCenter,
                        target.frameOrientation()
                );
                if (!validateFixedConstraintConfiguration(
                        configuration,
                        target.body(),
                        slotSubLevel,
                        slot,
                        reason
                )) {
                    return constraintFailureResult(slot);
                }
                captureCanonicalTopPreAddDiagnostics(
                        slot,
                        target.body(),
                        slotSubLevel,
                        configuration
                );
                candidate = pipeline.addConstraint(target.body(), slotSubLevel, configuration);
            } else {
                GenericConstraintConfiguration configuration = new GenericConstraintConfiguration(
                        target.framePosition(),
                        anchorLocalCenter,
                        target.frameOrientation(),
                        new Quaterniond(),
                        LOCKED_AXES
                );
                if (!validateConstraintConfiguration(
                        configuration,
                        target.body(),
                        slotSubLevel,
                        slot,
                        reason
                )) {
                    return constraintFailureResult(slot);
                }
                candidate = pipeline.addConstraint(target.body(), slotSubLevel, configuration);
            }
            logConstraintState(
                    "candidate-add-result",
                    reason,
                    slot,
                    target.body(),
                    slotSubLevel,
                    target.framePosition(),
                    target.frameOrientation(),
                    anchorLocalCenter,
                    new Quaterniond(),
                    "targetKind=" + target.kind() + ", candidate=" + describeConstraintHandle(candidate)
            );
        } catch (Exception exception) {
            logConstraintException("addConstraint(candidate)", reason, slot, target.body(), slotSubLevel, exception);
            return constraintFailureResult(slot);
        }

        if (!isConstraintHandleValid(candidate) || !constraintHandleMatchesKind(candidate, target.kind())) {
            removeConstraintHandle(candidate, slot, reason + ":candidate-add-invalid");
            return constraintFailureResult(slot);
        }

        boolean configured;
        if (target.kind() == ConstraintParentKind.TOP_BODY) {
            configured = configureFixedConstraintHandle(
                    pipeline,
                    candidate,
                    target,
                    slotSubLevel,
                    slot,
                    reason + ":candidate"
            );
        } else {
            configured = configureGenericConstraintHandle(
                    pipeline,
                    (GenericConstraintHandle) candidate,
                    target,
                    slotSubLevel,
                    slot,
                    anchorLocalCenter,
                    reason + ":candidate"
            ) != null;
        }
        if (!configured || !isConstraintHandleValid(candidate)) {
            removeConstraintHandle(candidate, slot, reason + ":candidate-configuration-failed");
            return constraintFailureResult(slot);
        }

        if (isConstraintHandleValid(previousHandle)
                && !removeConstraintHandle(previousHandle, slot, reason + ":previous-remove-before-commit")) {
            removeConstraintHandle(candidate, slot, reason + ":candidate-rollback-previous-still-valid");
            logConstraintTransition("candidate-rollback", reason, slot, target, slotSubLevel, previousHandle, candidate);
            return ConstraintEnsureResult.PREVIOUS_RETAINED;
        }

        commitConstraint(slot, target, slotSubLevel, candidate);
        logConstraintState(
                "candidate-commit",
                reason,
                slot,
                target.body(),
                slotSubLevel,
                target.framePosition(),
                target.frameOrientation(),
                anchorLocalCenter,
                new Quaterniond(),
                "targetKind=" + target.kind() + ", previous=" + describeConstraintHandle(previousHandle)
                        + ", candidate=" + describeConstraintHandle(candidate)
        );
        return ConstraintEnsureResult.TARGET_ACTIVE;
    }

    private boolean updateSlotConstraint(
            PhysicsPipeline pipeline,
            ConstraintTarget target,
            ServerSubLevel slotSubLevel,
            int slot,
            Vector3d anchorLocalCenter,
            String reason
    ) {
        PhysicsConstraintHandle handle = slotConstraintHandles[slot];
        if (!isConstraintHandleValid(handle) || !constraintHandleMatchesKind(handle, target.kind())) {
            logConstraintState(
                    "update-skipped-invalid-handle",
                    reason,
                    slot,
                    target.body(),
                    slotSubLevel,
                    target.framePosition(),
                    target.frameOrientation(),
                    anchorLocalCenter,
                    new Quaterniond(),
                    "handle failed validity check"
            );
            return false;
        }

        if (target.kind() == ConstraintParentKind.TOP_BODY) {
            return true;
        }

        MotorTuning tuning = configureGenericConstraintHandle(
                pipeline,
                (GenericConstraintHandle) handle,
                target,
                slotSubLevel,
                slot,
                anchorLocalCenter,
                reason
        );
        if (tuning == null || !isConstraintHandleValid(handle)) {
            return false;
        }
        commitConstraint(slot, target, slotSubLevel, handle);
        return true;
    }

    private boolean slotConstraintMatchesParent(int slot, ConstraintTarget target, ServerSubLevel slotSubLevel) {
        return hasValidConstraint(slot) && storedConstraintParentMatches(slot, target, slotSubLevel);
    }

    private boolean storedConstraintParentMatches(int slot, ConstraintTarget target, ServerSubLevel slotSubLevel) {
        UUID expectedParentId = target.body() == null ? null : target.body().getUniqueId();
        return slotConstraintParentKinds[slot] == target.kind()
                && constraintHandleMatchesKind(slotConstraintHandles[slot], target.kind())
                && java.util.Objects.equals(slotConstraintParentSubLevelIds[slot], expectedParentId)
                && slotConstraintParentRuntimeIds[slot] == getRuntimeIdForDiagnostics(target.body())
                && slotConstraintParentObjectIdentities[slot] == getObjectIdentityForDiagnostics(target.body())
                && slotConstraintChildRuntimeIds[slot] == getRuntimeIdForDiagnostics(slotSubLevel)
                && slotConstraintChildObjectIdentities[slot] == getObjectIdentityForDiagnostics(slotSubLevel)
                && slotSubLevel.getUniqueId().equals(slotSubLevelIds[slot]);
    }

    private ConstraintEnsureResult constraintFailureResult(int slot) {
        return hasValidConstraint(slot) ? ConstraintEnsureResult.PREVIOUS_RETAINED : ConstraintEnsureResult.UNSECURED;
    }

    private void secureSupportSlotAtCurrentPose(
            ServerLevel rootLevel,
            PhysicsPipeline pipeline,
            ServerSubLevel slotSubLevel,
            int slot,
            Vector3d anchorLocalCenter,
            String reason
    ) {
        SupportBinding supportBinding = resolveRememberedSupport(rootLevel);
        if (!supportBinding.available()) {
            logSupportUnavailable(reason + ":support-unavailable", rootLevel);
            return;
        }
        ConstraintTarget target = createSupportTargetAtCurrentPose(supportBinding, slotSubLevel, slot, anchorLocalCenter);
        ensureSlotConstraint(
                pipeline,
                target,
                slotSubLevel,
                slot,
                anchorLocalCenter,
                reason
        );
    }

    @Nullable
    private MotorTuning configureGenericConstraintHandle(
            PhysicsPipeline pipeline,
            GenericConstraintHandle handle,
            ConstraintTarget target,
            ServerSubLevel slotSubLevel,
            int slot,
            Vector3d anchorLocalCenter,
            String reason
    ) {
        MotorTuning tuning = computeMotorTuning(slotSubLevel);
        String operation = "setFrame1";
        try {
            handle.setFrame1(target.framePosition(), target.frameOrientation());
            operation = "setFrame2";
            handle.setFrame2(anchorLocalCenter, new Quaterniond());
            operation = "setContactsEnabled(false)";
            handle.setContactsEnabled(false);
            operation = "configure-motors";
            applySlotConstraintMotors(handle, tuning);
            if (target.body() != null) {
                operation = "wakeUp(parent)";
                pipeline.wakeUp(target.body());
            }
            operation = "wakeUp(child)";
            pipeline.wakeUp(slotSubLevel);
            return tuning;
        } catch (Exception exception) {
            logConstraintHandleException(operation, reason, slot, target.body(), slotSubLevel, handle, exception);
            return null;
        }
    }

    private MotorTuning computeMotorTuning(ServerSubLevel slotSubLevel) {
        double mass = sanitizedPositive(slotSubLevel.getMassTracker().getMass(), MIN_EFFECTIVE_SLOT_LOAD);
        double angularLoadRaw = computeAngularLoad(slotSubLevel, mass);
        double linearLoad = clampEffectiveSlotLoad(mass);
        double angularLoad = clampEffectiveSlotLoad(angularLoadRaw);

        double linearStiffness = BASE_STIFFNESS_PER_INERTIA * SLOT_LOCK_MULTIPLIER * linearLoad;
        double linearDamping = BASE_DAMPING_PER_INERTIA * SLOT_LOCK_MULTIPLIER * linearLoad;
        double angularStiffness = BASE_STIFFNESS_PER_INERTIA * SLOT_LOCK_MULTIPLIER * angularLoad;
        double angularDamping = BASE_DAMPING_PER_INERTIA * SLOT_LOCK_MULTIPLIER * angularLoad;

        return new MotorTuning(
                linearStiffness,
                linearDamping,
                angularStiffness,
                angularDamping
        );
    }

    private void applySlotConstraintMotors(
            GenericConstraintHandle handle,
            MotorTuning tuning
    ) {
        for (ConstraintJointAxis axis : LINEAR_MOTOR_AXES) {
            handle.setMotor(axis, 0.0D, tuning.linearStiffness(), tuning.linearDamping(), false, 0.0D);
        }
        for (ConstraintJointAxis axis : ANGULAR_MOTOR_AXES) {
            handle.setMotor(axis, 0.0D, tuning.angularStiffness(), tuning.angularDamping(), false, 0.0D);
        }
    }

    private void commitConstraint(
            int slot,
            ConstraintTarget target,
            ServerSubLevel slotSubLevel,
            PhysicsConstraintHandle handle
    ) {
        slotConstraintHandles[slot] = handle;
        slotConstraintParentKinds[slot] = target.kind();
        slotConstraintParentSubLevelIds[slot] = target.body() == null ? null : target.body().getUniqueId();
        slotConstraintFrame1Positions[slot] = new Vector3d(target.framePosition());
        slotConstraintFrame1Orientations[slot] = new Quaterniond(target.frameOrientation());
        slotConstraintAttachedToTop[slot] = target.kind() == ConstraintParentKind.TOP_BODY;
        slotConstraintTopSubLevelIds[slot] = slotConstraintAttachedToTop[slot]
                ? target.body().getUniqueId()
                : null;
        recordConstraintRuntimeIdentities(slot, target.body(), slotSubLevel);
    }

    private boolean refreshSupportBinding(ServerLevel rootLevel, BlockPos assemblyAnchorPos, String reason) {
        BlockPos previousAnchor = supportAssemblyAnchorPos;
        supportAssemblyAnchorPos = assemblyAnchorPos.immutable();
        try {
            SubLevel containing = Sable.HELPER.getContaining(rootLevel, assemblyAnchorPos);
            if (containing instanceof ServerSubLevel supportSubLevel && !supportSubLevel.isRemoved()) {
                supportBindingInitialized = true;
                supportBindingRootWorld = false;
                supportSubLevelId = supportSubLevel.getUniqueId();
                logSupportBinding("support-body-resolved", reason, rootLevel, supportSubLevel);
                return true;
            }
            if (containing == null) {
                supportBindingInitialized = true;
                supportBindingRootWorld = true;
                supportSubLevelId = null;
                logSupportBinding("root-world-resolved", reason, rootLevel, null);
                return true;
            }
            LOGGER.warn("[PropellerSlotConstraintDiag] event=support-resolution-failed reason={} dimension={} assemblyAnchorPos={} containingType={}",
                    reason,
                    rootLevel.dimension().location(),
                    assemblyAnchorPos,
                    containing.getClass().getName());
            supportAssemblyAnchorPos = previousAnchor;
            return false;
        } catch (Exception exception) {
            LOGGER.warn("[PropellerSlotConstraintDiag] event=support-resolution-failed reason={} dimension={} assemblyAnchorPos={}",
                    reason,
                    rootLevel.dimension().location(),
                    assemblyAnchorPos,
                    exception);
            supportAssemblyAnchorPos = previousAnchor;
            return false;
        }
    }

    private SupportBinding resolveRememberedSupport(ServerLevel rootLevel) {
        if (!supportBindingInitialized || supportAssemblyAnchorPos == null) {
            return new SupportBinding(false, null);
        }
        if (supportBindingRootWorld) {
            try {
                SubLevel containing = Sable.HELPER.getContaining(rootLevel, supportAssemblyAnchorPos);
                if (containing instanceof ServerSubLevel supportSubLevel && !supportSubLevel.isRemoved()) {
                    supportBindingRootWorld = false;
                    supportSubLevelId = supportSubLevel.getUniqueId();
                    return new SupportBinding(true, supportSubLevel);
                }
                return new SupportBinding(containing == null, null);
            } catch (Exception exception) {
                LOGGER.warn("[PropellerSlotConstraintDiag] event=support-reresolution-failed dimension={} assemblyAnchorPos={}",
                        rootLevel.dimension().location(),
                        supportAssemblyAnchorPos,
                        exception);
                return new SupportBinding(false, null);
            }
        }
        if (supportSubLevelId == null) {
            return new SupportBinding(false, null);
        }
        ServerSubLevel supportSubLevel = resolveSubLevel(rootLevel, supportSubLevelId);
        return new SupportBinding(supportSubLevel != null, supportSubLevel);
    }

    @Nullable
    private ConstraintTarget createCanonicalSupportTarget(
            ServerLevel rootLevel,
            SlotFrame slotFrame,
            BlockPos assemblyAnchorPos
    ) {
        SupportBinding supportBinding = resolveRememberedSupport(rootLevel);
        if (!supportBinding.available()) {
            return null;
        }
        Vector3d framePosition = JOMLConversion.atCenterOf(assemblyAnchorPos).add(slotFrame.offset());
        return new ConstraintTarget(
                supportBinding.body() == null ? ConstraintParentKind.ROOT_WORLD : ConstraintParentKind.SUPPORT_BODY,
                supportBinding.body(),
                framePosition,
                new Quaterniond(slotFrame.rotation())
        );
    }

    private ConstraintTarget createSupportTargetAtCurrentPose(
            SupportBinding supportBinding,
            ServerSubLevel slotSubLevel,
            int slot,
            Vector3d anchorLocalCenter
    ) {
        ConstraintParentKind kind = supportBinding.body() == null
                ? ConstraintParentKind.ROOT_WORLD
                : ConstraintParentKind.SUPPORT_BODY;
        ConstraintTarget identityTarget = new ConstraintTarget(kind, supportBinding.body(), new Vector3d(), new Quaterniond());
        if (storedConstraintParentMatches(slot, identityTarget, slotSubLevel)
                && slotConstraintFrame1Positions[slot] != null
                && slotConstraintFrame1Orientations[slot] != null) {
            return new ConstraintTarget(
                    kind,
                    supportBinding.body(),
                    new Vector3d(slotConstraintFrame1Positions[slot]),
                    new Quaterniond(slotConstraintFrame1Orientations[slot])
            );
        }

        Pose3dc slotPose = slotSubLevel.logicalPose();
        Vector3d worldPosition = slotPose.transformPosition(anchorLocalCenter, new Vector3d());
        Quaterniond worldOrientation = new Quaterniond(slotPose.orientation());
        if (supportBinding.body() == null) {
            return new ConstraintTarget(kind, null, worldPosition, worldOrientation);
        }

        Pose3dc supportPose = supportBinding.body().logicalPose();
        Vector3d localPosition = supportPose.transformPositionInverse(worldPosition, new Vector3d());
        Quaterniond localOrientation = new Quaterniond(supportPose.orientation())
                .conjugate()
                .mul(worldOrientation)
                .normalize();
        return new ConstraintTarget(kind, supportBinding.body(), localPosition, localOrientation);
    }

    private ConstraintTarget createCanonicalTopTarget(ServerSubLevel topSubLevel, int slot) {
        SlotFrame slotFrame = computeSlotFrame(slot, slotFacing);
        Vector3d topAnchorLocal = new Vector3d(activeTopAnchorLocalCenter).add(slotFrame.offset());
        return new ConstraintTarget(
                ConstraintParentKind.TOP_BODY,
                topSubLevel,
                topAnchorLocal,
                new Quaterniond(slotFrame.rotation())
        );
    }

    private boolean isCurrentTopSlotConstraint(
            ServerSubLevel topSubLevel,
            ServerSubLevel slotSubLevel,
            int slot
    ) {
        return hasValidConstraint(slot)
                && slotConstraintHandles[slot] instanceof FixedConstraintHandle
                && slotConstraintParentKinds[slot] == ConstraintParentKind.TOP_BODY
                && slotConstraintAttachedToTop[slot]
                && topSubLevel.getUniqueId().equals(slotConstraintParentSubLevelIds[slot])
                && topSubLevel.getUniqueId().equals(slotConstraintTopSubLevelIds[slot])
                && slotSubLevel.getUniqueId().equals(slotSubLevelIds[slot])
                && slotConstraintParentRuntimeIds[slot] == getRuntimeIdForDiagnostics(topSubLevel)
                && slotConstraintChildRuntimeIds[slot] == getRuntimeIdForDiagnostics(slotSubLevel)
                && slotConstraintParentObjectIdentities[slot] == getObjectIdentityForDiagnostics(topSubLevel)
                && slotConstraintChildObjectIdentities[slot] == getObjectIdentityForDiagnostics(slotSubLevel);
    }

    private boolean ensureSlotTriangle(
            PhysicsPipeline pipeline,
            ServerSubLevel topSubLevel,
            ServerSubLevel[] resolvedSlots,
            String reason
    ) {
        if (!hasCompleteDistinctSlotSet() || resolvedSlots.length != SLOT_COUNT) {
            removeTriangleConstraints(reason + ":invalid-slot-set");
            return false;
        }

        Set<UUID> resolvedIds = new HashSet<>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ServerSubLevel resolved = resolvedSlots[slot];
            if (resolved == null
                    || resolved.isRemoved()
                    || !slotSubLevelIds[slot].equals(resolved.getUniqueId())
                    || !resolvedIds.add(resolved.getUniqueId())) {
                removeTriangleConstraints(reason + ":invalid-resolved-slot");
                return false;
            }
        }

        FixedConstraintHandle[] candidates = new FixedConstraintHandle[SLOT_TRIANGLE_EDGES.length];
        for (int edge = 0; edge < SLOT_TRIANGLE_EDGES.length; edge++) {
            int firstSlot = SLOT_TRIANGLE_EDGES[edge][0];
            int secondSlot = SLOT_TRIANGLE_EDGES[edge][1];
            ServerSubLevel firstSubLevel = resolvedSlots[firstSlot];
            ServerSubLevel secondSubLevel = resolvedSlots[secondSlot];
            if (triangleConstraintMatches(edge, firstSubLevel, secondSubLevel)) {
                continue;
            }

            FixedConstraintConfiguration configuration = createTriangleConfiguration(
                    firstSlot,
                    secondSlot,
                    getOrCreateAnchorLocalCenter(firstSubLevel, firstSlot),
                    getOrCreateAnchorLocalCenter(secondSubLevel, secondSlot)
            );
            if (configuration == null
                    || !validateFixedConstraintConfiguration(
                    configuration,
                    firstSubLevel,
                    secondSubLevel,
                    firstSlot,
                    reason + ":edge-" + edge
            )) {
                removeTriangleCandidates(candidates, reason + ":configuration-failed");
                return false;
            }

            PhysicsConstraintHandle added;
            try {
                captureCanonicalTrianglePreAddDiagnostics(
                        edge,
                        firstSubLevel,
                        secondSubLevel,
                        configuration
                );
                added = pipeline.addConstraint(firstSubLevel, secondSubLevel, configuration);
            } catch (Exception exception) {
                logTriangleException("addConstraint", reason, edge, firstSubLevel, secondSubLevel, exception);
                removeTriangleCandidates(candidates, reason + ":candidate-add-failed");
                return false;
            }
            if (!(added instanceof FixedConstraintHandle candidate)
                    || !isConstraintHandleValid(candidate)) {
                removeConstraintHandle(added, firstSlot, reason + ":candidate-invalid-edge-" + edge);
                removeTriangleCandidates(candidates, reason + ":candidate-invalid");
                return false;
            }
            if (!configureTriangleConstraintHandle(
                    pipeline,
                    candidate,
                    firstSubLevel,
                    secondSubLevel,
                    edge,
                    reason
            )) {
                removeConstraintHandle(candidate, firstSlot, reason + ":candidate-config-failed-edge-" + edge);
                removeTriangleCandidates(candidates, reason + ":candidate-config-failed");
                return false;
            }
            candidates[edge] = candidate;
            logTriangleState("candidate-ready", reason, edge, firstSubLevel, secondSubLevel, configuration, candidate);
        }

        boolean allCommitted = true;
        for (int edge = 0; edge < SLOT_TRIANGLE_EDGES.length; edge++) {
            FixedConstraintHandle candidate = candidates[edge];
            if (candidate == null) {
                continue;
            }
            int firstSlot = SLOT_TRIANGLE_EDGES[edge][0];
            ServerSubLevel firstSubLevel = resolvedSlots[firstSlot];
            ServerSubLevel secondSubLevel = resolvedSlots[SLOT_TRIANGLE_EDGES[edge][1]];
            FixedConstraintHandle previous = triangleConstraintHandles[edge];
            if (isConstraintHandleValid(previous)
                    && !removeConstraintHandle(previous, firstSlot, reason + ":previous-edge-" + edge)) {
                removeConstraintHandle(candidate, firstSlot, reason + ":candidate-rollback-edge-" + edge);
                allCommitted = false;
                continue;
            }
            commitTriangleConstraint(edge, firstSubLevel, secondSubLevel, candidate);
            logTriangleState("candidate-committed", reason, edge, firstSubLevel, secondSubLevel, null, candidate);
        }
        boolean complete = allCommitted && hasCompleteCurrentTriangle(resolvedSlots);
        if (complete) {
            completeCanonicalRebindPreAddDiagnostics(topSubLevel, resolvedSlots);
        }
        return complete;
    }

    @Nullable
    private FixedConstraintConfiguration createTriangleConfiguration(
            int firstSlot,
            int secondSlot,
            Vector3d firstAnchorLocalCenter,
            Vector3d secondAnchorLocalCenter
    ) {
        if (activeTopAnchorLocalCenter == null
                || firstSlot < 0
                || firstSlot >= SLOT_COUNT
                || secondSlot < 0
                || secondSlot >= SLOT_COUNT
                || firstSlot == secondSlot) {
            return null;
        }

        SlotFrame firstFrame = computeSlotFrame(firstSlot, slotFacing);
        SlotFrame secondFrame = computeSlotFrame(secondSlot, slotFacing);
        Vector3d firstCenterTopLocal = new Vector3d(activeTopAnchorLocalCenter).add(firstFrame.offset());
        Vector3d secondCenterTopLocal = new Vector3d(activeTopAnchorLocalCenter).add(secondFrame.offset());
        Vector3d jointTopLocal = new Vector3d(firstCenterTopLocal).add(secondCenterTopLocal).mul(0.5D);
        Vector3d firstJointLocal = new Vector3d(jointTopLocal).sub(firstCenterTopLocal);
        new Quaterniond(firstFrame.rotation()).conjugate().transform(firstJointLocal);
        firstJointLocal.add(firstAnchorLocalCenter);
        Vector3d secondJointLocal = new Vector3d(jointTopLocal).sub(secondCenterTopLocal);
        new Quaterniond(secondFrame.rotation()).conjugate().transform(secondJointLocal);
        secondJointLocal.add(secondAnchorLocalCenter);
        Quaterniond relativeOrientation = new Quaterniond(firstFrame.rotation())
                .conjugate()
                .mul(secondFrame.rotation())
                .normalize();
        if (!isFinite(firstCenterTopLocal)
                || !isFinite(secondCenterTopLocal)
                || !isFinite(jointTopLocal)
                || !isFinite(firstJointLocal)
                || !isFinite(secondJointLocal)
                || !isFinite(relativeOrientation)) {
            return null;
        }
        return new FixedConstraintConfiguration(firstJointLocal, secondJointLocal, relativeOrientation);
    }

    private boolean configureTriangleConstraintHandle(
            PhysicsPipeline pipeline,
            FixedConstraintHandle handle,
            ServerSubLevel firstSubLevel,
            ServerSubLevel secondSubLevel,
            int edge,
            String reason
    ) {
        try {
            handle.setContactsEnabled(false);
            pipeline.wakeUp(firstSubLevel);
            pipeline.wakeUp(secondSubLevel);
            return handle.isValid();
        } catch (Exception exception) {
            logTriangleException("configure", reason, edge, firstSubLevel, secondSubLevel, exception);
            return false;
        }
    }

    private boolean triangleConstraintMatches(
            int edge,
            ServerSubLevel firstSubLevel,
            ServerSubLevel secondSubLevel
    ) {
        return isConstraintHandleValid(triangleConstraintHandles[edge])
                && firstSubLevel.getUniqueId().equals(triangleFirstSubLevelIds[edge])
                && secondSubLevel.getUniqueId().equals(triangleSecondSubLevelIds[edge])
                && triangleFirstRuntimeIds[edge] == getRuntimeIdForDiagnostics(firstSubLevel)
                && triangleSecondRuntimeIds[edge] == getRuntimeIdForDiagnostics(secondSubLevel)
                && triangleFirstObjectIdentities[edge] == getObjectIdentityForDiagnostics(firstSubLevel)
                && triangleSecondObjectIdentities[edge] == getObjectIdentityForDiagnostics(secondSubLevel);
    }

    private boolean hasCompleteCurrentTriangle(ServerSubLevel[] resolvedSlots) {
        for (int edge = 0; edge < SLOT_TRIANGLE_EDGES.length; edge++) {
            if (!triangleConstraintMatches(
                    edge,
                    resolvedSlots[SLOT_TRIANGLE_EDGES[edge][0]],
                    resolvedSlots[SLOT_TRIANGLE_EDGES[edge][1]]
            )) {
                return false;
            }
        }
        return true;
    }

    private void commitTriangleConstraint(
            int edge,
            ServerSubLevel firstSubLevel,
            ServerSubLevel secondSubLevel,
            FixedConstraintHandle handle
    ) {
        triangleConstraintHandles[edge] = handle;
        triangleFirstSubLevelIds[edge] = firstSubLevel.getUniqueId();
        triangleSecondSubLevelIds[edge] = secondSubLevel.getUniqueId();
        triangleFirstRuntimeIds[edge] = getRuntimeIdForDiagnostics(firstSubLevel);
        triangleSecondRuntimeIds[edge] = getRuntimeIdForDiagnostics(secondSubLevel);
        triangleFirstObjectIdentities[edge] = getObjectIdentityForDiagnostics(firstSubLevel);
        triangleSecondObjectIdentities[edge] = getObjectIdentityForDiagnostics(secondSubLevel);
    }

    private void removeTriangleCandidates(FixedConstraintHandle[] candidates, String reason) {
        for (int edge = 0; edge < candidates.length; edge++) {
            FixedConstraintHandle candidate = candidates[edge];
            if (candidate != null) {
                removeConstraintHandle(
                        candidate,
                        SLOT_TRIANGLE_EDGES[edge][0],
                        reason + ":edge-" + edge
                );
            }
        }
    }

    private void removeTriangleConstraints(String reason) {
        for (int edge = 0; edge < SLOT_TRIANGLE_EDGES.length; edge++) {
            FixedConstraintHandle handle = triangleConstraintHandles[edge];
            if (handle != null) {
                removeConstraintHandle(handle, SLOT_TRIANGLE_EDGES[edge][0], reason + ":edge-" + edge);
            }
            clearTriangleConstraintMetadata(edge);
        }
    }

    private void clearTriangleConstraintMetadata(int edge) {
        triangleConstraintHandles[edge] = null;
        triangleFirstSubLevelIds[edge] = null;
        triangleSecondSubLevelIds[edge] = null;
        triangleFirstRuntimeIds[edge] = DIAGNOSTIC_UNKNOWN_RUNTIME_ID;
        triangleSecondRuntimeIds[edge] = DIAGNOSTIC_UNKNOWN_RUNTIME_ID;
        triangleFirstObjectIdentities[edge] = DIAGNOSTIC_UNKNOWN_RUNTIME_ID;
        triangleSecondObjectIdentities[edge] = DIAGNOSTIC_UNKNOWN_RUNTIME_ID;
    }

    private void logTriangleState(
            String event,
            String reason,
            int edge,
            ServerSubLevel firstSubLevel,
            ServerSubLevel secondSubLevel,
            @Nullable FixedConstraintConfiguration configuration,
            @Nullable PhysicsConstraintHandle handle
    ) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }
        Vector3d firstJointWorld = configuration == null
                ? null
                : firstSubLevel.logicalPose().transformPosition(configuration.pos1(), new Vector3d());
        Vector3d secondJointWorld = configuration == null
                ? null
                : secondSubLevel.logicalPose().transformPosition(configuration.pos2(), new Vector3d());
        Vector3d firstBlockCenterWorld = slotAnchorLocalCenters[SLOT_TRIANGLE_EDGES[edge][0]] == null
                ? null
                : firstSubLevel.logicalPose().transformPosition(
                slotAnchorLocalCenters[SLOT_TRIANGLE_EDGES[edge][0]],
                new Vector3d()
        );
        Vector3d secondBlockCenterWorld = slotAnchorLocalCenters[SLOT_TRIANGLE_EDGES[edge][1]] == null
                ? null
                : secondSubLevel.logicalPose().transformPosition(
                slotAnchorLocalCenters[SLOT_TRIANGLE_EDGES[edge][1]],
                new Vector3d()
        );
        Vector3d selectedCentersMidpointWorld = firstBlockCenterWorld == null || secondBlockCenterWorld == null
                ? null
                : new Vector3d(firstBlockCenterWorld).add(secondBlockCenterWorld).mul(0.5D);
        double anchorError = firstJointWorld == null || secondJointWorld == null
                ? Double.NaN
                : firstJointWorld.distance(secondJointWorld);
        LOGGER.info(
                "[PropellerSlotTriangleDiag] event={} reason={} edge={} firstSlot={} secondSlot={} firstBody={} secondBody={} firstBlockCenterWorld={} secondBlockCenterWorld={} selectedCentersMidpointWorld={} pos1={} pos2={} orientation={} firstJointWorld={} secondJointWorld={} anchorErrorBlocks={} handle={}",
                event,
                reason,
                edge,
                SLOT_TRIANGLE_EDGES[edge][0],
                SLOT_TRIANGLE_EDGES[edge][1],
                describeBody(firstSubLevel),
                describeBody(secondSubLevel),
                formatVector(firstBlockCenterWorld),
                formatVector(secondBlockCenterWorld),
                formatVector(selectedCentersMidpointWorld),
                configuration == null ? "<retained>" : formatVector(configuration.pos1()),
                configuration == null ? "<retained>" : formatVector(configuration.pos2()),
                configuration == null ? "<retained>" : formatQuaternion(configuration.orientation()),
                formatVector(firstJointWorld),
                formatVector(secondJointWorld),
                formatDouble(anchorError),
                describeConstraintHandle(handle)
        );
    }

    private void logTriangleException(
            String operation,
            String reason,
            int edge,
            ServerSubLevel firstSubLevel,
            ServerSubLevel secondSubLevel,
            Exception exception
    ) {
        LOGGER.warn(
                "[PropellerSlotTriangleDiag] event=exception operation={} reason={} edge={} firstSlot={} secondSlot={} firstBody={} secondBody={}",
                operation,
                reason,
                edge,
                SLOT_TRIANGLE_EDGES[edge][0],
                SLOT_TRIANGLE_EDGES[edge][1],
                describeBody(firstSubLevel),
                describeBody(secondSubLevel),
                exception
        );
    }

    private void captureCanonicalTopPreAddDiagnostics(
            int slot,
            @Nullable ServerSubLevel topSubLevel,
            ServerSubLevel slotSubLevel,
            FixedConstraintConfiguration configuration
    ) {
        if (!canonicalRebindDiagnosticsArmed) {
            return;
        }
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            resetCanonicalRebindDiagnostics();
            return;
        }

        try {
            canonicalTopPreAddDiagnostics[slot] = computeConstraintFrameDiagnostics(
                    topSubLevel,
                    slotSubLevel,
                    configuration.pos1(),
                    configuration.orientation(),
                    configuration.pos2(),
                    new Quaterniond()
            );
        } catch (RuntimeException exception) {
            abortCanonicalRebindDiagnostics("top-pre-add", slot, exception);
        }
    }

    private void captureCanonicalTrianglePreAddDiagnostics(
            int edge,
            ServerSubLevel firstSubLevel,
            ServerSubLevel secondSubLevel,
            FixedConstraintConfiguration configuration
    ) {
        if (!canonicalRebindDiagnosticsArmed) {
            return;
        }
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            resetCanonicalRebindDiagnostics();
            return;
        }

        try {
            canonicalTrianglePreAddDiagnostics[edge] = computeConstraintFrameDiagnostics(
                    firstSubLevel,
                    secondSubLevel,
                    configuration.pos1(),
                    configuration.orientation(),
                    configuration.pos2(),
                    new Quaterniond()
            );
        } catch (RuntimeException exception) {
            abortCanonicalRebindDiagnostics("triangle-pre-add", edge, exception);
        }
    }

    private void completeCanonicalRebindPreAddDiagnostics(
            ServerSubLevel topSubLevel,
            ServerSubLevel[] resolvedSlots
    ) {
        if (!canonicalRebindDiagnosticsArmed) {
            return;
        }
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            resetCanonicalRebindDiagnostics();
            return;
        }
        if (Arrays.stream(canonicalTopPreAddDiagnostics).anyMatch(java.util.Objects::isNull)
                || Arrays.stream(canonicalTrianglePreAddDiagnostics).anyMatch(java.util.Objects::isNull)) {
            return;
        }

        int validTopHandles = countCurrentTopConstraintHandles(topSubLevel, resolvedSlots);
        int validTriangleHandles = countCurrentTriangleConstraintHandles(resolvedSlots);
        if (validTopHandles != SLOT_COUNT || validTriangleHandles != SLOT_TRIANGLE_EDGES.length) {
            return;
        }

        logCanonicalRebindSnapshot(
                "canonical-rebind-pre-add",
                canonicalTopPreAddDiagnostics,
                canonicalTrianglePreAddDiagnostics,
                validTopHandles,
                validTriangleHandles
        );
        canonicalRebindDiagnosticsArmed = false;
        canonicalRebindPostSnapshotPending = true;
    }

    private void captureCanonicalRebindPostSnapshot(ServerLevel rootLevel) {
        if (!canonicalRebindPostSnapshotPending) {
            return;
        }
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            resetCanonicalRebindDiagnostics();
            return;
        }
        if (!topFollowActive
                || activeTopSubLevelId == null
                || activeTopAnchorLocalCenter == null
                || !hasCompleteDistinctSlotSet()) {
            resetCanonicalRebindDiagnostics();
            return;
        }

        ServerSubLevel topSubLevel = resolveSubLevel(rootLevel, activeTopSubLevelId);
        if (topSubLevel == null) {
            return;
        }
        ServerSubLevel[] resolvedSlots = new ServerSubLevel[SLOT_COUNT];
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            resolvedSlots[slot] = resolveSubLevel(rootLevel, slotSubLevelIds[slot]);
            if (resolvedSlots[slot] == null || slotAnchorLocalCenters[slot] == null) {
                return;
            }
        }

        ConstraintFrameDiagnostics[] topDiagnostics = new ConstraintFrameDiagnostics[SLOT_COUNT];
        ConstraintFrameDiagnostics[] triangleDiagnostics =
                new ConstraintFrameDiagnostics[SLOT_TRIANGLE_EDGES.length];
        try {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                ConstraintTarget target = createCanonicalTopTarget(topSubLevel, slot);
                FixedConstraintConfiguration configuration = new FixedConstraintConfiguration(
                        target.framePosition(),
                        slotAnchorLocalCenters[slot],
                        target.frameOrientation()
                );
                topDiagnostics[slot] = computeConstraintFrameDiagnostics(
                        topSubLevel,
                        resolvedSlots[slot],
                        configuration.pos1(),
                        configuration.orientation(),
                        configuration.pos2(),
                        new Quaterniond()
                );
            }
            for (int edge = 0; edge < SLOT_TRIANGLE_EDGES.length; edge++) {
                int firstSlot = SLOT_TRIANGLE_EDGES[edge][0];
                int secondSlot = SLOT_TRIANGLE_EDGES[edge][1];
                FixedConstraintConfiguration configuration = createTriangleConfiguration(
                        firstSlot,
                        secondSlot,
                        slotAnchorLocalCenters[firstSlot],
                        slotAnchorLocalCenters[secondSlot]
                );
                if (configuration == null) {
                    throw new IllegalStateException("Canonical triangle configuration unavailable for edge " + edge);
                }
                triangleDiagnostics[edge] = computeConstraintFrameDiagnostics(
                        resolvedSlots[firstSlot],
                        resolvedSlots[secondSlot],
                        configuration.pos1(),
                        configuration.orientation(),
                        configuration.pos2(),
                        new Quaterniond()
                );
            }
        } catch (RuntimeException exception) {
            abortCanonicalRebindDiagnostics("first-postphysics", -1, exception);
            return;
        }

        logCanonicalRebindSnapshot(
                "canonical-rebind-first-postphysics",
                topDiagnostics,
                triangleDiagnostics,
                countCurrentTopConstraintHandles(topSubLevel, resolvedSlots),
                countCurrentTriangleConstraintHandles(resolvedSlots)
        );
        resetCanonicalRebindDiagnostics();
    }

    private int countCurrentTopConstraintHandles(
            ServerSubLevel topSubLevel,
            ServerSubLevel[] resolvedSlots
    ) {
        int count = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (isCurrentTopSlotConstraint(topSubLevel, resolvedSlots[slot], slot)) {
                count++;
            }
        }
        return count;
    }

    private int countCurrentTriangleConstraintHandles(ServerSubLevel[] resolvedSlots) {
        int count = 0;
        for (int edge = 0; edge < SLOT_TRIANGLE_EDGES.length; edge++) {
            if (triangleConstraintMatches(
                    edge,
                    resolvedSlots[SLOT_TRIANGLE_EDGES[edge][0]],
                    resolvedSlots[SLOT_TRIANGLE_EDGES[edge][1]]
            )) {
                count++;
            }
        }
        return count;
    }

    private void logCanonicalRebindSnapshot(
            String event,
            ConstraintFrameDiagnostics[] topDiagnostics,
            ConstraintFrameDiagnostics[] triangleDiagnostics,
            int validTopHandles,
            int validTriangleHandles
    ) {
        LOGGER.info(
                "[PropellerSlotCanonicalRebindDiag] event={} topSubLevelId={} slotFacing={} topTranslationErrorsBlocks={} topQuaternionErrorsDeg={} triangleAnchorErrorsBlocks={} triangleRelativeQuaternionErrorsDeg={} validTopFixedHandles={} validTriangleFixedHandles={}",
                event,
                activeTopSubLevelId,
                slotFacing,
                formatDiagnosticErrors(topDiagnostics, false),
                formatDiagnosticErrors(topDiagnostics, true),
                formatDiagnosticErrors(triangleDiagnostics, false),
                formatDiagnosticErrors(triangleDiagnostics, true),
                validTopHandles,
                validTriangleHandles
        );
    }

    private String formatDiagnosticErrors(
            ConstraintFrameDiagnostics[] diagnostics,
            boolean orientation
    ) {
        StringBuilder formatted = new StringBuilder("[");
        for (int index = 0; index < diagnostics.length; index++) {
            if (index > 0) {
                formatted.append(',');
            }
            ConstraintFrameDiagnostics diagnostic = diagnostics[index];
            formatted.append(index).append('=');
            formatted.append(diagnostic == null
                    ? "unavailable"
                    : formatDouble(orientation
                    ? diagnostic.orientationErrorDegrees()
                    : diagnostic.positionError()));
        }
        return formatted.append(']').toString();
    }

    private void abortCanonicalRebindDiagnostics(String phase, int index, RuntimeException exception) {
        LOGGER.warn(
                "[PropellerSlotCanonicalRebindDiag] event=diagnostic-capture-failed phase={} index={} topSubLevelId={} slotFacing={}",
                phase,
                index,
                activeTopSubLevelId,
                slotFacing,
                exception
        );
        resetCanonicalRebindDiagnostics();
    }

    private void resetCanonicalRebindDiagnostics() {
        canonicalRebindDiagnosticsArmed = false;
        canonicalRebindPostSnapshotPending = false;
        Arrays.fill(canonicalTopPreAddDiagnostics, null);
        Arrays.fill(canonicalTrianglePreAddDiagnostics, null);
    }

    private Vector3d targetWorldPosition(ConstraintTarget target) {
        return target.body() == null
                ? new Vector3d(target.framePosition())
                : target.body().logicalPose().transformPosition(target.framePosition(), new Vector3d());
    }

    private Quaterniond targetWorldOrientation(ConstraintTarget target) {
        return target.body() == null
                ? new Quaterniond(target.frameOrientation())
                : new Quaterniond(target.body().logicalPose().orientation()).mul(target.frameOrientation()).normalize();
    }

    private boolean removeConstraintHandle(@Nullable PhysicsConstraintHandle handle, int slot, String reason) {
        logConstraintRemoval("transaction-remove-attempt", reason, slot, handle, null);
        if (handle == null) {
            return true;
        }
        try {
            if (handle.isValid()) {
                handle.remove();
            }
            boolean removed = !handle.isValid();
            logConstraintRemoval(removed ? "transaction-remove-confirmed" : "transaction-remove-still-valid",
                    reason,
                    slot,
                    handle,
                    null);
            return removed;
        } catch (Exception exception) {
            logConstraintRemoval("transaction-remove-failed", reason, slot, handle, exception);
            return false;
        }
    }

    private void logConstraintTransition(
            String event,
            String reason,
            int slot,
            ConstraintTarget target,
            ServerSubLevel child,
            @Nullable PhysicsConstraintHandle previous,
            @Nullable PhysicsConstraintHandle candidate
    ) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }
        LOGGER.info("[PropellerSlotConstraintDiag] event={} reason={} slot={} targetKind={} parent={} child={} previousHandle={} candidateHandle={}",
                event,
                reason,
                slot,
                target.kind(),
                describeBody(target.body()),
                describeBody(child),
                describeConstraintHandle(previous),
                describeConstraintHandle(candidate));
    }

    private void logSupportBinding(
            String event,
            String reason,
            ServerLevel rootLevel,
            @Nullable ServerSubLevel supportSubLevel
    ) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }
        LOGGER.info("[PropellerSlotConstraintDiag] event={} reason={} dimension={} assemblyAnchorPos={} support={}",
                event,
                reason,
                rootLevel.dimension().location(),
                supportAssemblyAnchorPos,
                describeBody(supportSubLevel));
    }

    private void logSupportUnavailable(String reason, ServerLevel rootLevel) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }
        LOGGER.info("[PropellerSlotConstraintDiag] event=support-unavailable reason={} dimension={} assemblyAnchorPos={} rememberedRoot={} rememberedSupportId={}",
                reason,
                rootLevel.dimension().location(),
                supportAssemblyAnchorPos,
                supportBindingRootWorld,
                supportSubLevelId);
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

    private static double clampEffectiveSlotLoad(double value) {
        return Math.max(MIN_EFFECTIVE_SLOT_LOAD, Math.min(MAX_EFFECTIVE_SLOT_LOAD, value));
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
    }

    private void carryMountedServoTopsWithParentDelta(ServerSubLevel parentSubLevel, Pose3dc oldParentPose, Pose3dc newParentPose, PhysicsPipeline pipeline) {
        if (!isPropellerSlotSubLevel(parentSubLevel)) {
            return;
        }

        ServerLevel rootLevel = parentSubLevel.getLevel();
        BoundingBox3ic bounds = parentSubLevel.getPlot().getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosedStream(bounds.toMojang()).map(BlockPos::immutable).toList()) {
            if (rootLevel.getBlockEntity(pos) instanceof ServoTwisterBlockEntity servo
                    && isBlockInSubLevel(rootLevel, parentSubLevel, pos)) {
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
        if (freeBearingFailClosedTopFollow) {
            TopFollowReadiness readiness = inspectTopFollowReadiness(rootLevel, freeBearingExpectedTopSubLevelId);
            if (readiness == TopFollowReadiness.RETRYABLE_UNRESOLVED
                    || readiness == TopFollowReadiness.INVALID
                    || (readiness == TopFollowReadiness.RETRYABLE_REBIND && !freeBearingAllowTopRebind)) {
                return;
            }
        }

        if (!topFollowActive || activeTopSubLevelId == null || activeTopAnchorLocalCenter == null) {
            return;
        }

        ServerSubLevel topSubLevel = resolveSubLevel(rootLevel, activeTopSubLevelId);
        if (topSubLevel == null) {
            if (freeBearingFailClosedTopFollow) {
                return;
            }
            logTopFollowState("stabilize-top-missing", recomputeFromTopPose);
            clearTopFollowState();
            lockRootSlotPoses(rootLevel);
            return;
        }

        boolean topConstraintsReady = hasCompleteDistinctSlotSet();
        ServerSubLevel[] resolvedSlots = new ServerSubLevel[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            UUID subLevelId = slotSubLevelIds[i];
            if (subLevelId == null) {
                topConstraintsReady = false;
                continue;
            }

            ServerSubLevel slotSubLevel = resolveSubLevel(rootLevel, subLevelId);
            if (slotSubLevel == null) {
                if (freeBearingFailClosedTopFollow) {
                    return;
                }
                clearSlot(i, "stabilize-slot-sublevel-missing");
                topConstraintsReady = false;
                continue;
            }
            resolvedSlots[i] = slotSubLevel;

            Vector3d anchorLocalCenter = getOrCreateAnchorLocalCenter(slotSubLevel, i);
            ConstraintEnsureResult result = ensureTopSlotConstraint(
                    pipeline,
                    topSubLevel,
                    slotSubLevel,
                    i,
                    anchorLocalCenter,
                    recomputeFromTopPose ? "post-physics-top-ensure" : "pre-physics-top-ensure"
            );
            if (result == ConstraintEnsureResult.UNSECURED) {
                secureSupportSlotAtCurrentPose(
                        rootLevel,
                        pipeline,
                        slotSubLevel,
                        i,
                        anchorLocalCenter,
                        recomputeFromTopPose ? "post-physics-top-fallback" : "pre-physics-top-fallback"
                );
            }
            if (result != ConstraintEnsureResult.TARGET_ACTIVE
                    || !isCurrentTopSlotConstraint(topSubLevel, slotSubLevel, i)) {
                topConstraintsReady = false;
            }
        }

        if (topConstraintsReady) {
            ensureSlotTriangle(
                    pipeline,
                    topSubLevel,
                    resolvedSlots,
                    recomputeFromTopPose ? "post-physics-triangle-ensure" : "pre-physics-triangle-ensure"
            );
        } else if (!freeBearingFailClosedTopFollow) {
            removeTriangleConstraints(
                    recomputeFromTopPose ? "post-physics-triangle-not-ready" : "pre-physics-triangle-not-ready"
            );
        }
    }

    private void clearTopFollowState() {
        resetCanonicalRebindDiagnostics();
        removeTriangleConstraints("top-follow-cleared");
        topFollowActive = false;
        activeTopSubLevelId = null;
        activeTopAnchorLocalCenter = null;
    }

    private void clearSlot(int slot, String reason) {
        removeTriangleConstraints(reason + ":triangle-cleanup");
        removeSlotConstraint(slot, reason + ":constraint-cleanup");
        slotSubLevelIds[slot] = null;
        slotAnchorLocalCenters[slot] = null;
        clearTopFollowStateIfEmpty();
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

    private void removeSlotConstraint(int slot, String reason) {
        PhysicsConstraintHandle handle = slotConstraintHandles[slot];
        logConstraintRemoval("remove-attempt", reason, slot, handle, null);
        if (handle != null) {
            try {
                if (handle.isValid()) {
                    handle.remove();
                }
                logConstraintRemoval("remove-result", reason, slot, handle, null);
            } catch (Exception exception) {
                logConstraintRemoval("remove-failed", reason, slot, handle, exception);
            }
        }

        slotConstraintHandles[slot] = null;
        slotConstraintParentKinds[slot] = ConstraintParentKind.NONE;
        slotConstraintParentSubLevelIds[slot] = null;
        slotConstraintFrame1Positions[slot] = null;
        slotConstraintFrame1Orientations[slot] = null;
        slotConstraintAttachedToTop[slot] = false;
        slotConstraintTopSubLevelIds[slot] = null;
        slotConstraintParentRuntimeIds[slot] = DIAGNOSTIC_UNKNOWN_RUNTIME_ID;
        slotConstraintChildRuntimeIds[slot] = DIAGNOSTIC_UNKNOWN_RUNTIME_ID;
        slotConstraintParentObjectIdentities[slot] = DIAGNOSTIC_UNKNOWN_RUNTIME_ID;
        slotConstraintChildObjectIdentities[slot] = DIAGNOSTIC_UNKNOWN_RUNTIME_ID;
    }

    private void clearSupportBinding() {
        supportAssemblyAnchorPos = null;
        supportBindingInitialized = false;
        supportBindingRootWorld = false;
        supportSubLevelId = null;
    }

    private void updateDiagnosticServoWorldPos(@Nullable Level level, BlockPos servoPos, String reason) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }
        if (level == null) {
            LOGGER.info("[PropellerSlotConstraintDiag] event=servo-world-pos-unavailable reason={} servoPos={}",
                    reason,
                    servoPos);
            return;
        }

        try {
            diagnosticServoWorldPos = SableLevelWrapper.toWorldPos(level, servoPos);
            LOGGER.info("[PropellerSlotConstraintDiag] event=servo-world-pos reason={} localPos={} worldPos={}",
                    reason,
                    servoPos,
                    diagnosticServoWorldPos);
        } catch (Exception exception) {
            LOGGER.warn("[PropellerSlotConstraintDiag] event=servo-world-pos-failed reason={} servoPos={}",
                    reason,
                    servoPos,
                    exception);
        }
    }

    private void recordConstraintRuntimeIdentities(
            int slot,
            @Nullable ServerSubLevel parentSubLevel,
            ServerSubLevel slotSubLevel
    ) {
        slotConstraintParentRuntimeIds[slot] = getRuntimeIdForDiagnostics(parentSubLevel);
        slotConstraintChildRuntimeIds[slot] = getRuntimeIdForDiagnostics(slotSubLevel);
        slotConstraintParentObjectIdentities[slot] = getObjectIdentityForDiagnostics(parentSubLevel);
        slotConstraintChildObjectIdentities[slot] = getObjectIdentityForDiagnostics(slotSubLevel);
    }

    private boolean validateConstraintConfiguration(
            GenericConstraintConfiguration configuration,
            @Nullable ServerSubLevel parentSubLevel,
            ServerSubLevel slotSubLevel,
            int slot,
            String reason
    ) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(slotSubLevel.getLevel());
        if (container == null) {
            logConstraintState(
                    "configuration-validate-skipped",
                    reason,
                    slot,
                    parentSubLevel,
                    slotSubLevel,
                    configuration.pos1(),
                    configuration.orientation1(),
                    configuration.pos2(),
                    configuration.orientation2(),
                    "result=container-null"
            );
            LOGGER.warn("[PropellerSlotConstraintDiag] event=configuration-validation-failed reason={} slot={} cause=container-null",
                    reason,
                    slot);
            return false;
        }

        try {
            configuration.validate(container, parentSubLevel, slotSubLevel);
            logConstraintState(
                    "configuration-validate-result",
                    reason,
                    slot,
                    parentSubLevel,
                    slotSubLevel,
                    configuration.pos1(),
                    configuration.orientation1(),
                    configuration.pos2(),
                    configuration.orientation2(),
                    "result=success"
            );
            return true;
        } catch (Exception exception) {
            logConstraintException(
                    "GenericConstraintConfiguration.validate",
                    reason,
                    slot,
                    parentSubLevel,
                    slotSubLevel,
                    exception
            );
            return false;
        }
    }

    private void logConstraintState(
            String event,
            String reason,
            int slot,
            @Nullable ServerSubLevel parentSubLevel,
            ServerSubLevel slotSubLevel,
            Vector3dc frame1Position,
            Quaterniondc frame1Orientation,
            Vector3dc frame2Position,
            Quaterniondc frame2Orientation,
            String detail
    ) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }

        try {
            ConstraintFrameDiagnostics frames = computeConstraintFrameDiagnostics(
                    parentSubLevel,
                    slotSubLevel,
                    frame1Position,
                    frame1Orientation,
                    frame2Position,
                    frame2Orientation
            );
            LOGGER.info(
                    "[PropellerSlotConstraintDiag] event={} reason={} slot={} target={} servoWorldPos={} actualSupport={} parent={} child={} handle={} storedParentUuid={} storedParentRuntimeId={} storedChildRuntimeId={} constraintContract={} frame1LocalPos={} frame1LocalRot={} frame2LocalPos={} frame2LocalRot={} frame1WorldPos={} frame1WorldRot={} frame2WorldPos={} frame2WorldRot={} framePositionError={} frameOrientationErrorDeg={} detail={}",
                    event,
                    reason,
                    slot,
                    constraintTargetKindForDiagnostics(parentSubLevel),
                    diagnosticServoWorldPos,
                    describeActualSupport(slotSubLevel.getLevel()),
                    describeBody(parentSubLevel),
                    describeBody(slotSubLevel),
                    describeConstraintHandle(slot),
                    slotConstraintParentSubLevelIds[slot],
                    slotConstraintParentRuntimeIds[slot],
                    slotConstraintChildRuntimeIds[slot],
                    constraintContractForDiagnostics(parentSubLevel),
                    formatVector(frame1Position),
                    formatQuaternion(frame1Orientation),
                    formatVector(frame2Position),
                    formatQuaternion(frame2Orientation),
                    formatVector(frames.frame1WorldPosition()),
                    formatQuaternion(frames.frame1WorldOrientation()),
                    formatVector(frames.frame2WorldPosition()),
                    formatQuaternion(frames.frame2WorldOrientation()),
                    formatDouble(frames.positionError()),
                    formatDouble(frames.orientationErrorDegrees()),
                    detail
            );
        } catch (Exception exception) {
            LOGGER.warn("[PropellerSlotConstraintDiag] event=state-log-failed originalEvent={} reason={} slot={}",
                    event,
                    reason,
                    slot,
                    exception);
        }
    }

    private void logConstraintException(
            String operation,
            String reason,
            int slot,
            @Nullable ServerSubLevel parentSubLevel,
            @Nullable ServerSubLevel slotSubLevel,
            Exception exception
    ) {
        logConstraintHandleException(
                operation,
                reason,
                slot,
                parentSubLevel,
                slotSubLevel,
                slotConstraintHandles[slot],
                exception
        );
    }

    private void logConstraintHandleException(
            String operation,
            String reason,
            int slot,
            @Nullable ServerSubLevel parentSubLevel,
            @Nullable ServerSubLevel slotSubLevel,
            @Nullable PhysicsConstraintHandle handle,
            Exception exception
    ) {
        LOGGER.warn(
                "[PropellerSlotConstraintDiag] event=operation-failed operation={} reason={} slot={} servoWorldPos={} actualSupport={} parent={} child={} handle={} storedParentUuid={} storedParentRuntimeId={} storedChildRuntimeId={}",
                operation,
                reason,
                slot,
                diagnosticServoWorldPos,
                slotSubLevel == null ? "unknown" : describeActualSupport(slotSubLevel.getLevel()),
                describeBody(parentSubLevel),
                describeBody(slotSubLevel),
                describeConstraintHandle(handle),
                slotConstraintParentSubLevelIds[slot],
                slotConstraintParentRuntimeIds[slot],
                slotConstraintChildRuntimeIds[slot],
                exception
        );
    }

    private void logConstraintRemoval(
            String event,
            String reason,
            int slot,
            @Nullable PhysicsConstraintHandle handle,
            @Nullable Exception exception
    ) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            if (exception != null) {
                LOGGER.warn("[PropellerSlotConstraintDiag] event={} reason={} slot={} handle={}",
                        event,
                        reason,
                        slot,
                        describeConstraintHandle(handle),
                        exception);
            }
            return;
        }

        String message = "[PropellerSlotConstraintDiag] event={} reason={} slot={} servoWorldPos={} handle={} attachedToTop={} storedParentUuid={} storedParentRuntimeId={} storedChildRuntimeId={}";
        if (exception == null) {
            LOGGER.info(
                    message,
                    event,
                    reason,
                    slot,
                    diagnosticServoWorldPos,
                    describeConstraintHandle(handle),
                    slotConstraintAttachedToTop[slot],
                    slotConstraintParentSubLevelIds[slot],
                    slotConstraintParentRuntimeIds[slot],
                    slotConstraintChildRuntimeIds[slot]
            );
        } else {
            LOGGER.warn(
                    message,
                    event,
                    reason,
                    slot,
                    diagnosticServoWorldPos,
                    describeConstraintHandle(handle),
                    slotConstraintAttachedToTop[slot],
                    slotConstraintParentSubLevelIds[slot],
                    slotConstraintParentRuntimeIds[slot],
                    slotConstraintChildRuntimeIds[slot],
                    exception
            );
        }
    }

    private void logTopFollowState(String event, boolean recomputeFromTopPose) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }
        LOGGER.info(
                "[PropellerSlotConstraintDiag] event={} physicsPhase={} servoWorldPos={} topFollowActive={} activeTopSubLevelId={} activeTopAnchorLocalCenter={}",
                event,
                recomputeFromTopPose ? "post" : "pre",
                diagnosticServoWorldPos,
                topFollowActive,
                activeTopSubLevelId,
                formatVector(activeTopAnchorLocalCenter)
        );
    }

    private ConstraintFrameDiagnostics computeConstraintFrameDiagnostics(
            @Nullable ServerSubLevel parentSubLevel,
            ServerSubLevel slotSubLevel,
            Vector3dc frame1Position,
            Quaterniondc frame1Orientation,
            Vector3dc frame2Position,
            Quaterniondc frame2Orientation
    ) {
        Vector3d frame1WorldPosition = parentSubLevel == null
                ? new Vector3d(frame1Position)
                : parentSubLevel.logicalPose().transformPosition(frame1Position, new Vector3d());
        Quaterniond frame1WorldOrientation = parentSubLevel == null
                ? new Quaterniond(frame1Orientation)
                : new Quaterniond(parentSubLevel.logicalPose().orientation()).mul(frame1Orientation);
        Vector3d frame2WorldPosition = slotSubLevel.logicalPose().transformPosition(frame2Position, new Vector3d());
        Quaterniond frame2WorldOrientation = new Quaterniond(slotSubLevel.logicalPose().orientation()).mul(frame2Orientation);
        double positionError = frame1WorldPosition.distance(frame2WorldPosition);
        double orientationErrorDegrees = quaternionDifferenceDegrees(frame1WorldOrientation, frame2WorldOrientation);
        return new ConstraintFrameDiagnostics(
                frame1WorldPosition,
                frame1WorldOrientation,
                frame2WorldPosition,
                frame2WorldOrientation,
                positionError,
                orientationErrorDegrees
        );
    }

    private String describeActualSupport(ServerLevel rootLevel) {
        if (supportAssemblyAnchorPos == null) {
            return "{kind=unknown,reason=support-pivot-unavailable}";
        }

        try {
            SubLevel containing = Sable.HELPER.getContaining(rootLevel, supportAssemblyAnchorPos);
            if (containing instanceof ServerSubLevel supportSubLevel) {
                return describeBody(supportSubLevel);
            }
            if (containing == null) {
                return "{kind=root-world,runtimeId=" + PhysicsPipelineBody.NULL_RUNTIME_ID + "}";
            }
            return "{kind=" + containing.getClass().getName()
                    + ",uuid=" + containing.getUniqueId() + "}";
        } catch (Exception exception) {
            return "{kind=error,type=" + exception.getClass().getName()
                    + ",message=" + exception.getMessage() + "}";
        }
    }

    private String constraintTargetKindForDiagnostics(@Nullable ServerSubLevel parentSubLevel) {
        if (parentSubLevel == null) {
            return ConstraintParentKind.ROOT_WORLD.name().toLowerCase(Locale.ROOT);
        }
        if (activeTopSubLevelId != null && activeTopSubLevelId.equals(parentSubLevel.getUniqueId())) {
            return ConstraintParentKind.TOP_BODY.name().toLowerCase(Locale.ROOT);
        }
        return ConstraintParentKind.SUPPORT_BODY.name().toLowerCase(Locale.ROOT);
    }

    private String constraintContractForDiagnostics(@Nullable ServerSubLevel parentSubLevel) {
        return parentSubLevel != null
                && activeTopSubLevelId != null
                && activeTopSubLevelId.equals(parentSubLevel.getUniqueId())
                ? "fixed-all-six-dofs"
                : "generic-locked-axes=" + LOCKED_AXES;
    }

    private String describeBody(@Nullable PhysicsPipelineBody body) {
        if (body == null) {
            return "{kind=root-world,runtimeId=" + PhysicsPipelineBody.NULL_RUNTIME_ID + ",removed=false}";
        }

        UUID uniqueId = body instanceof ServerSubLevel subLevel ? subLevel.getUniqueId() : null;
        return "{kind=" + body.getClass().getName()
                + ",uuid=" + uniqueId
                + ",runtimeId=" + getRuntimeIdForDiagnostics(body)
                + ",removed=" + isRemovedForDiagnostics(body) + "}";
    }

    private String describeConstraintHandle(int slot) {
        return describeConstraintHandle(slotConstraintHandles[slot]);
    }

    private String describeConstraintHandle(@Nullable PhysicsConstraintHandle handle) {
        if (handle == null) {
            return "{valid=false}";
        }

        String valid;
        try {
            valid = Boolean.toString(handle.isValid());
        } catch (Exception exception) {
            valid = "error:" + exception.getClass().getName() + ":" + exception.getMessage();
        }
        String type = handle instanceof FixedConstraintHandle
                ? "fixed"
                : handle instanceof GenericConstraintHandle ? "generic" : handle.getClass().getName();
        return "{type=" + type + ",valid=" + valid + "}";
    }

    private int getRuntimeIdForDiagnostics(@Nullable PhysicsPipelineBody body) {
        if (body == null) {
            return PhysicsPipelineBody.NULL_RUNTIME_ID;
        }
        try {
            return body.getRuntimeId();
        } catch (Exception ignored) {
            return DIAGNOSTIC_UNKNOWN_RUNTIME_ID;
        }
    }

    private int getObjectIdentityForDiagnostics(@Nullable Object object) {
        return object == null ? 0 : System.identityHashCode(object);
    }

    private String isRemovedForDiagnostics(PhysicsPipelineBody body) {
        try {
            return Boolean.toString(body.isRemoved());
        } catch (Exception exception) {
            return "error:" + exception.getClass().getName() + ":" + exception.getMessage();
        }
    }

    private double quaternionDifferenceDegrees(Quaterniondc first, Quaterniondc second) {
        Quaterniond difference = new Quaterniond(first).normalize().conjugate()
                .mul(new Quaterniond(second).normalize())
                .normalize();
        double absoluteW = Math.min(1.0D, Math.abs(difference.w));
        return Math.toDegrees(2.0D * Math.acos(absoluteW));
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

    private void restoreMissingSlotPlacementHelperMetadata(ServerLevel rootLevel) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            UUID slotSubLevelId = slotSubLevelIds[slot];
            if (slotSubLevelId == null) {
                continue;
            }

            ServerSubLevel slotSubLevel = resolveSubLevel(rootLevel, slotSubLevelId);
            if (slotSubLevel == null || !(SLOT_SUBLEVEL_NAME_PREFIX + slot).equals(slotSubLevel.getName())) {
                continue;
            }

            Vector3d anchorLocalCenter = getOrCreateAnchorLocalCenter(slotSubLevel, slot);
            ensureSlotPlacementHelperMetadata(
                    rootLevel, slotSubLevel, slot, anchorLocalCenter, slotFacing);
        }
    }

    private boolean configureFixedConstraintHandle(
            PhysicsPipeline pipeline,
            PhysicsConstraintHandle handle,
            ConstraintTarget target,
            ServerSubLevel slotSubLevel,
            int slot,
            String reason
    ) {
        if (!(handle instanceof FixedConstraintHandle)) {
            return false;
        }

        String operation = "setContactsEnabled(false)";
        try {
            handle.setContactsEnabled(false);
            if (target.body() != null) {
                operation = "wakeUp(parent)";
                pipeline.wakeUp(target.body());
            }
            operation = "wakeUp(child)";
            pipeline.wakeUp(slotSubLevel);
            return true;
        } catch (Exception exception) {
            logConstraintHandleException(operation, reason, slot, target.body(), slotSubLevel, handle, exception);
            return false;
        }
    }

    private boolean validateFixedConstraintConfiguration(
            FixedConstraintConfiguration configuration,
            @Nullable ServerSubLevel parentSubLevel,
            ServerSubLevel slotSubLevel,
            int slot,
            String reason
    ) {
        boolean invalidBodies = parentSubLevel == null
                || parentSubLevel.isRemoved()
                || slotSubLevel.isRemoved()
                || parentSubLevel == slotSubLevel
                || parentSubLevel.getUniqueId().equals(slotSubLevel.getUniqueId());
        boolean invalidFrame = !isFinite(configuration.pos1())
                || !isFinite(configuration.pos2())
                || !isFinite(configuration.orientation());
        if (invalidBodies || invalidFrame) {
            logConstraintState(
                    "fixed-configuration-validation-failed",
                    reason,
                    slot,
                    parentSubLevel,
                    slotSubLevel,
                    configuration.pos1(),
                    configuration.orientation(),
                    configuration.pos2(),
                    new Quaterniond(),
                    "invalidBodies=" + invalidBodies + ", invalidFrame=" + invalidFrame
            );
            return false;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(slotSubLevel.getLevel());
        if (container == null) {
            logConstraintState(
                    "fixed-configuration-validate-skipped",
                    reason,
                    slot,
                    parentSubLevel,
                    slotSubLevel,
                    configuration.pos1(),
                    configuration.orientation(),
                    configuration.pos2(),
                    new Quaterniond(),
                    "result=container-null"
            );
            return false;
        }

        try {
            configuration.validate(container, parentSubLevel, slotSubLevel);
            logConstraintState(
                    "fixed-configuration-validate-result",
                    reason,
                    slot,
                    parentSubLevel,
                    slotSubLevel,
                    configuration.pos1(),
                    configuration.orientation(),
                    configuration.pos2(),
                    new Quaterniond(),
                    "result=success"
            );
            return true;
        } catch (Exception exception) {
            logConstraintException(
                    "FixedConstraintConfiguration.validate",
                    reason,
                    slot,
                    parentSubLevel,
                    slotSubLevel,
                    exception
            );
            return false;
        }
    }

    private boolean ensureSlotPlacementHelperMetadata(
            ServerLevel rootLevel,
            ServerSubLevel slotSubLevel,
            int slot,
            Vector3d anchorLocalCenter,
            Direction facing
    ) {
        if (slot < 0 || slot >= SLOT_COUNT
                || slotSubLevel.isRemoved()
                || !(SLOT_SUBLEVEL_NAME_PREFIX + slot).equals(slotSubLevel.getName())
                || !isSupportedSlotFacing(facing)) {
            return false;
        }

        BlockPos anchorPos = BlockPos.containing(
                anchorLocalCenter.x, anchorLocalCenter.y, anchorLocalCenter.z);
        BlockState anchorState = rootLevel.getBlockState(anchorPos);
        if (!anchorState.is(ModBlocks.METAL_TRAVERSE.get())
                || anchorState.getValue(MetalTraverseBlock.AXIS) != facing.getAxis()
                || !(rootLevel.getBlockEntity(anchorPos) instanceof WrenchSideCycleBlockEntity sideCycle)) {
            return false;
        }

        if (sideCycle.setServoMode7SlotOutward(computeSlotLocalOutward(facing))) {
            sideCycle.markChangedAndSync();
        }
        return true;
    }

    private boolean hasValidConstraint(int slot) {
        return isConstraintHandleValid(slotConstraintHandles[slot]);
    }

    private boolean constraintHandleMatchesKind(
            @Nullable PhysicsConstraintHandle handle,
            ConstraintParentKind kind
    ) {
        return switch (kind) {
            case TOP_BODY -> handle instanceof FixedConstraintHandle;
            case ROOT_WORLD, SUPPORT_BODY -> handle instanceof GenericConstraintHandle;
            case NONE -> false;
        };
    }

    private boolean isConstraintHandleValid(@Nullable PhysicsConstraintHandle handle) {
        if (handle == null) {
            return false;
        }

        try {
            return handle.isValid();
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isFinite(@Nullable Vector3dc vector) {
        return vector != null
                && Double.isFinite(vector.x())
                && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    private boolean isFinite(@Nullable Quaterniondc quaternion) {
        return quaternion != null
                && Double.isFinite(quaternion.x())
                && Double.isFinite(quaternion.y())
                && Double.isFinite(quaternion.z())
                && Double.isFinite(quaternion.w());
    }

    private String formatVector(@Nullable Vector3dc vector) {
        if (vector == null) {
            return "null";
        }
        return "(" + formatDouble(vector.x()) + "," + formatDouble(vector.y()) + "," + formatDouble(vector.z()) + ")";
    }

    private String formatQuaternion(@Nullable Quaterniondc quaternion) {
        if (quaternion == null) {
            return "null";
        }
        return "(" + formatDouble(quaternion.x())
                + "," + formatDouble(quaternion.y())
                + "," + formatDouble(quaternion.z())
                + "," + formatDouble(quaternion.w()) + ")";
    }

    private String formatDouble(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private BlockState resolveSlotPlacementState(BlockItem blockItem, Direction servoFacing) {
        Block block = blockItem.getBlock();
        return block == ModBlocks.METAL_TRAVERSE.get()
                ? MetalTraverseBlock.getStateForIsolatedPlacement(servoFacing)
                : block.defaultBlockState();
    }

    private boolean isPhaseASupportedBlock(BlockState blockState) {
        Block block = blockState.getBlock();
        if (block == ModBlocks.METAL_TRAVERSE.get()) {
            return true;
        }
        return !(block instanceof EntityBlock) && !(block instanceof FallingBlock);
    }

    private int getTemporaryBlockUpdateFlags(BlockState blockState) {
        return isTemporaryMetalTraverse(blockState)
                ? Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
                : Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE;
    }

    private boolean isTemporaryMetalTraverse(BlockState blockState) {
        return blockState.is(ModBlocks.METAL_TRAVERSE.get());
    }

    private void clearTemporaryBlock(
            ServerLevel rootLevel,
            BlockPos sourceWorldPos,
            BlockState temporaryBlockState
    ) {
        BlockState currentState = rootLevel.getBlockState(sourceWorldPos);
        if (currentState.isAir()) {
            return;
        }

        int updateFlags = getTemporaryBlockUpdateFlags(temporaryBlockState);
        if (isTemporaryMetalTraverse(temporaryBlockState)
                && currentState.is(ModBlocks.METAL_TRAVERSE.get())) {
            MetalTraverseBlock.runWithoutTraverseHideCornerBreakHistory(
                    rootLevel,
                    sourceWorldPos,
                    () -> rootLevel.setBlock(sourceWorldPos, Blocks.AIR.defaultBlockState(), updateFlags)
            );
        } else {
            rootLevel.setBlock(sourceWorldPos, Blocks.AIR.defaultBlockState(), updateFlags);
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

}
