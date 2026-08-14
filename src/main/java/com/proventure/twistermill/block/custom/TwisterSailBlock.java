package com.proventure.twistermill.block.custom;

import com.mojang.logging.LogUtils;
import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.config.TwisterMillConfig;
import com.proventure.twistermill.weather.TwisterWeatherService;
import com.proventure.twistermill.weather.WeatherSailForceMath;
import com.proventure.twistermill.weather.WeatherSailForceSmoother;
import com.proventure.twistermill.weather.WeatherSailForceSnapshotServer;
import com.proventure.twistermill.weather.WindSample;
import com.proventure.twistermill.util.SablePlacementHitHelper;
import com.proventure.twistermill.util.TwisterSailPatternPlacementUtil;
import com.proventure.twistermill.util.TwisterSailSurfacePatternUtil;
import com.simibubi.create.content.contraptions.bearing.SailBlock;
import com.simibubi.create.foundation.utility.BlockHelper;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class TwisterSailBlock extends SailBlock implements BlockSubLevelLiftProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final EnumProperty<FrameMaterial> FRAME_MATERIAL =
            EnumProperty.create("frame_material", FrameMaterial.class);
    public static final EnumProperty<DyeColor> SAIL_COLOR =
            EnumProperty.create("sail_color", DyeColor.class);

    private static final int placementHelperId = PlacementHelpers.register(new PlacementHelper());
    private static final Map<UUID, WindObjectDiagnosticsAccumulator> WIND_OBJECT_DIAGNOSTICS = new HashMap<>();
    private static final ThreadLocal<WeatherSailForceMath.Result> WIND_FORCE_RESULT =
            ThreadLocal.withInitial(WeatherSailForceMath.Result::new);
    private static final ThreadLocal<Vector3d> SMOOTHED_WIND_FORCE =
            ThreadLocal.withInitial(Vector3d::new);
    private static long lastDiagnosticsCleanupTick = Long.MIN_VALUE;

    public TwisterSailBlock(Properties properties, boolean frame) {
        super(properties, frame, frame ? null : DyeColor.WHITE);
        BlockState defaultState = defaultBlockState()
                .setValue(FRAME_MATERIAL, FrameMaterial.TWISTER_PALE_PLANKS);

        if (!frame) {
            defaultState = defaultState.setValue(SAIL_COLOR, DyeColor.WHITE);
        }

        registerDefaultState(defaultState);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FRAME_MATERIAL);
        if (!frame) {
            builder.add(SAIL_COLOR);
        }
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context);
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (!player.isShiftKeyDown() && player.mayBuild()) {
            IPlacementHelper helper = PlacementHelpers.get(placementHelperId);
            if (helper.matchesItem(stack)) {
                PlacementOffset offset = helper.getOffset(player, level, state, pos, hit);
                ItemInteractionResult result = TwisterSailPatternPlacementUtil.placeWithPattern(
                        player,
                        level,
                        pos,
                        state,
                        offset,
                        (BlockItem) stack.getItem(),
                        hand,
                        hit
                );
                if (result == ItemInteractionResult.SUCCESS) {
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }

        if (stack.getItem() instanceof ShearsItem) {
            handleShearsPattern(state, level, pos, player);
            return ItemInteractionResult.SUCCESS;
        }

        if (frame) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        DyeColor color = DyeColor.getColor(stack);
        if (!player.isShiftKeyDown() && color != null) {
            applyDyePattern(state, level, pos, color, player);
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void applyDye(BlockState state, Level world, BlockPos pos, Vec3 hit, @Nullable DyeColor color) {
        BlockState newState = (color == null
                ? ModBlocks.TWISTER_SAIL_FRAME_BLOCK.get()
                : ModBlocks.TWISTER_SAIL_BLOCK.get()).defaultBlockState();

        newState = BlockHelper.copyProperties(state, newState);
        if (color != null && newState.hasProperty(SAIL_COLOR)) {
            newState = newState.setValue(SAIL_COLOR, color);
        }

        if (state != newState) {
            world.setBlockAndUpdate(pos, newState);
        }
    }

    private void applyDyePattern(BlockState centerState, Level level, BlockPos centerPos, DyeColor targetColor, Player player) {
        if (centerState.getValue(SAIL_COLOR) != targetColor) {
            applyDye(centerState, level, centerPos, Vec3.atCenterOf(centerPos), targetColor);
            return;
        }

        Direction facing = centerState.getValue(FACING);
        List<BlockPos> targets = TwisterSailSurfacePatternUtil.collectNextPerSide(
                level,
                centerPos,
                player.getDirection(),
                candidate -> TwisterSailSurfacePatternUtil.isSameFacingTwisterSail(candidate, facing)
                        && !((TwisterSailBlock) candidate.getBlock()).isFrame(),
                candidate -> candidate.getValue(SAIL_COLOR) != targetColor
        );

        for (BlockPos targetPos : targets) {
            BlockState targetState = level.getBlockState(targetPos);
            applyDye(targetState, level, targetPos, Vec3.atCenterOf(targetPos), targetColor);
        }
    }

    private void handleShearsPattern(BlockState centerState, Level level, BlockPos centerPos, Player player) {
        if (!frame) {
            applyDye(centerState, level, centerPos, Vec3.atCenterOf(centerPos), null);
            return;
        }

        Direction facing = centerState.getValue(FACING);
        List<BlockPos> targets = TwisterSailSurfacePatternUtil.collectNextPerSide(
                level,
                centerPos,
                player.getDirection(),
                candidate -> TwisterSailSurfacePatternUtil.isSameFacingTwisterSail(candidate, facing),
                candidate -> !((TwisterSailBlock) candidate.getBlock()).isFrame()
        );

        for (BlockPos targetPos : targets) {
            BlockState targetState = level.getBlockState(targetPos);
            applyDye(targetState, level, targetPos, Vec3.atCenterOf(targetPos), null);
        }
    }

    protected boolean twistermill$isWindSailPhysicsEnabledForThisBlock() {
        return !frame;
    }

    @Override
    public @NotNull Direction sable$getNormal(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    public void sable$contributeLiftAndDrag(
            final LiftProviderContext ctx,
            final ServerSubLevel subLevel,
            final @Nullable Pose3d localPose,
            final double timeStep,
            final Vector3dc linearVelocity,
            final Vector3dc angularVelocity,
            final Vector3d linearImpulse,
            final Vector3d angularImpulse,
            final @Nullable LiftProviderGroup group
    ) {
        if (!twistermill$isWindSailPhysicsEnabledForThisBlock()) {
            return;
        }
        if (subLevel == null || subLevel.isRemoved() || subLevel.getLevel() == null) {
            return;
        }

        boolean windForceEnabled = TwisterMillConfig.ENABLE_SAIL_WIND_FORCE.get();
        boolean smoothingEnabled = windForceEnabled && TwisterMillConfig.isSailForceSmoothingEnabled();
        boolean kinematicContraption = localPose != null;
        WeatherSailForceSmoother.updateEnabledState(subLevel, smoothingEnabled);

        Vector3d localCenter = new Vector3d(ctx.pos().getX() + 0.5D, ctx.pos().getY() + 0.5D, ctx.pos().getZ() + 0.5D);
        Vector3d localNormal = new Vector3d(ctx.dir().x, ctx.dir().y, ctx.dir().z);

        if (localPose != null) {
            localPose.transformPosition(localCenter);
            localPose.transformNormal(localNormal);
        }

        Pose3d subLevelPose = subLevel.logicalPose();
        Vector3d worldCenter = subLevelPose.transformPosition(localCenter, new Vector3d());
        Vector3d worldNormal = subLevelPose.transformNormal(localNormal, new Vector3d());
        Vec3 worldCenterVec = new Vec3(worldCenter.x, worldCenter.y, worldCenter.z);
        WindSample windSample = TwisterWeatherService.sampleAtWorldPosition(subLevel.getLevel(), worldCenterVec);
        if (!windSample.valid()) {
            if (smoothingEnabled) {
                WeatherSailForceSmoother.discardContribution(ctx, subLevel, kinematicContraption);
            }
            return;
        }

        BlockPos samplePos = BlockPos.containing(worldCenter.x, worldCenter.y, worldCenter.z);
        float windSpeed = windSample.weather2WindSpeed();
        float windAngle = windSample.windAngleDegrees();

        double rad = Math.toRadians(windAngle);
        Vector3d windDir = new Vector3d(-Math.sin(rad), 0.0D, Math.cos(rad));
        if (windDir.lengthSquared() <= 1.0E-9D
                || worldNormal.lengthSquared() <= 1.0E-9D
                || !Double.isFinite(windSpeed)) {
            if (smoothingEnabled) {
                WeatherSailForceSmoother.discardContribution(ctx, subLevel, kinematicContraption);
            }
            return;
        }
        windDir.normalize();
        worldNormal.normalize();

        double minExposure = Math.max(0.0D, Math.min(1.0D, TwisterMillConfig.SAIL_WIND_MIN_EXPOSURE.get()));
        double coeff = Math.max(0.0D, TwisterMillConfig.SAIL_WIND_FORCE_COEFFICIENT.get());
        double maxPerBlock = Math.max(0.0D, TwisterMillConfig.SAIL_WIND_MAX_FORCE_PER_BLOCK.get());
        double peakPitchEfficiency = TwisterMillConfig.getRotorBladePeakEfficiencyFraction();
        int peakEfficiencyPitchDegrees = TwisterMillConfig.getSailPeakEfficiencyPitchDegrees();

        WeatherSailForceMath.Result forceResult = WIND_FORCE_RESULT.get();
        boolean hasForce = WeatherSailForceMath.compute(
                windDir,
                worldNormal,
                windSpeed,
                coeff,
                minExposure,
                maxPerBlock,
                peakPitchEfficiency,
                peakEfficiencyPitchDegrees,
                forceResult
        );
        double exposure = forceResult.effectiveExposure();
        double forceMagnitude = forceResult.forceMagnitude();
        double forceWorldMagnitude = forceMagnitude;
        if (!hasForce && !smoothingEnabled) {
            collectAndMaybeLogObjectDiagnostics(
                    subLevel,
                    samplePos,
                    windSpeed,
                    windAngle,
                    windDir,
                    worldNormal,
                    exposure,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
            );
            return;
        }

        if (!windForceEnabled) {
            collectAndMaybeLogObjectDiagnostics(subLevel, samplePos, windSpeed, windAngle, windDir, worldNormal, exposure, forceMagnitude,
                    forceWorldMagnitude, 0.0D, 0.0D, 0.0D);
            return;
        }

        Vector3dc forceWorld;
        if (smoothingEnabled) {
            Vector3d smoothedForce = SMOOTHED_WIND_FORCE.get();
            if (!WeatherSailForceSmoother.smooth(
                    ctx,
                    subLevel,
                    kinematicContraption,
                    forceResult.forceWorld(),
                    timeStep,
                    TwisterMillConfig.getSailForceSmoothingStrength(),
                    smoothedForce
            )) {
                return;
            }
            forceWorld = smoothedForce;
            forceWorldMagnitude = smoothedForce.length();
            if (forceWorldMagnitude <= 1.0E-9D) {
                collectAndMaybeLogObjectDiagnostics(
                        subLevel,
                        samplePos,
                        windSpeed,
                        windAngle,
                        windDir,
                        worldNormal,
                        exposure,
                        forceMagnitude,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D
                );
                return;
            }
        } else {
            forceWorld = forceResult.forceWorld();
        }
        Vector3d forceLocal = subLevelPose.transformNormalInverse(forceWorld, new Vector3d());
        double forceLocalMagnitude = forceLocal.length();
        Vector3d impulseLocal = forceLocal.mul(timeStep, new Vector3d());

        Vector3dc centerOfMass = subLevel.getMassTracker().getCenterOfMass();
        if (centerOfMass == null) {
            return;
        }

        linearImpulse.add(impulseLocal);
        Vector3d comLocal = new Vector3d(centerOfMass);
        Vector3d localArm = localCenter.sub(comLocal, new Vector3d());
        Vector3d torqueLocal = localArm.cross(impulseLocal, new Vector3d());
        angularImpulse.add(torqueLocal);

        WeatherSailForceSnapshotServer.recordAppliedForce(
                ctx,
                subLevel,
                localPose != null,
                localCenter,
                localNormal,
                worldCenter,
                windDir,
                forceResult.referenceForceMagnitude(),
                forceWorld
        );

        collectAndMaybeLogObjectDiagnostics(subLevel, samplePos, windSpeed, windAngle, windDir, worldNormal, exposure, forceMagnitude,
                forceWorldMagnitude, forceLocalMagnitude, impulseLocal.length(), torqueLocal.length());
    }

    private static void collectAndMaybeLogObjectDiagnostics(
            ServerSubLevel subLevel,
            BlockPos samplePos,
            float windSpeed,
            float windAngle,
            Vector3d windDir,
            Vector3d worldNormal,
            double exposure,
            double forceMagnitude,
            double forceWorldMagnitude,
            double forceLocalMagnitude,
            double impulseLocalMagnitude,
            double torqueLocalMagnitude
    ) {
        if (!TwisterMillConfig.ENABLE_SAIL_WIND_DIAGNOSTICS.get()) {
            return;
        }
        int interval = Math.max(10, TwisterMillConfig.SAIL_WIND_DIAGNOSTIC_INTERVAL_TICKS.get());
        long now = subLevel.getLevel().getGameTime();
        long currentWindow = Math.floorDiv(now, interval);
        UUID subLevelId = subLevel.getUniqueId();

        WindObjectDiagnosticsAccumulator accumulator = WIND_OBJECT_DIAGNOSTICS.get(subLevelId);
        if (accumulator == null) {
            accumulator = new WindObjectDiagnosticsAccumulator(currentWindow);
            WIND_OBJECT_DIAGNOSTICS.put(subLevelId, accumulator);
        } else if (accumulator.window < currentWindow) {
            flushDiagnosticsAccumulator(subLevelId, accumulator, interval);
            accumulator.resetForWindow(currentWindow);
        } else if (accumulator.window > currentWindow) {
            accumulator.resetForWindow(currentWindow);
        }

        accumulator.lastSeenTick = now;
        accumulator.contributionCount++;
        accumulator.samplePos = samplePos;
        accumulator.lastWindAngle = windAngle;
        accumulator.lastWindDirX = windDir.x;
        accumulator.lastWindDirY = windDir.y;
        accumulator.lastWindDirZ = windDir.z;
        accumulator.lastNormalX = worldNormal.x;
        accumulator.lastNormalY = worldNormal.y;
        accumulator.lastNormalZ = worldNormal.z;
        accumulator.lastWindSpeed = windSpeed;
        accumulator.lastExposure = exposure;
        accumulator.lastForceWorldMagnitude = forceWorldMagnitude;
        accumulator.lastForceLocalMagnitude = forceLocalMagnitude;
        accumulator.lastImpulseLocalMagnitude = impulseLocalMagnitude;
        accumulator.lastTorqueLocalMagnitude = torqueLocalMagnitude;
        accumulator.enabled = TwisterMillConfig.ENABLE_SAIL_WIND_FORCE.get();

        accumulator.totalWindSpeed += windSpeed;
        accumulator.maxWindSpeed = Math.max(accumulator.maxWindSpeed, windSpeed);
        accumulator.totalExposure += exposure;
        accumulator.maxExposure = Math.max(accumulator.maxExposure, exposure);
        accumulator.totalForceMagnitude += forceMagnitude;
        accumulator.maxForceMagnitude = Math.max(accumulator.maxForceMagnitude, forceMagnitude);

        maybeCleanupDiagnostics(now, interval);
    }

    private static void flushDiagnosticsAccumulator(UUID subLevelId, WindObjectDiagnosticsAccumulator accumulator, int interval) {
        if (accumulator.contributionCount <= 0) {
            return;
        }

        long bucketTick = (accumulator.window + 1L) * interval;
        double avgWindSpeed = accumulator.totalWindSpeed / accumulator.contributionCount;
        double avgExposure = accumulator.totalExposure / accumulator.contributionCount;
        BlockPos loggedSamplePos = accumulator.samplePos != null ? accumulator.samplePos : BlockPos.ZERO;

        LOGGER.info(
                "TwisterSailWindObjectDiag subLevel={} tick={} contributionCount={} avgWindSpeed={} maxWindSpeed={} avgExposure={} maxExposure={} totalForce={} maxForce={} samplePos={} windAngle={} windDir=({}, {}, {}) normal=({}, {}, {}) enabled={} lastWindSpeed={} lastExposure={} forceWorldLen={} forceLocalLen={} impulseLocalLen={} torqueLocalLen={}",
                subLevelId,
                bucketTick,
                accumulator.contributionCount,
                avgWindSpeed,
                accumulator.maxWindSpeed,
                avgExposure,
                accumulator.maxExposure,
                accumulator.totalForceMagnitude,
                accumulator.maxForceMagnitude,
                loggedSamplePos,
                accumulator.lastWindAngle,
                accumulator.lastWindDirX, accumulator.lastWindDirY, accumulator.lastWindDirZ,
                accumulator.lastNormalX, accumulator.lastNormalY, accumulator.lastNormalZ,
                accumulator.enabled,
                accumulator.lastWindSpeed,
                accumulator.lastExposure,
                accumulator.lastForceWorldMagnitude,
                accumulator.lastForceLocalMagnitude,
                accumulator.lastImpulseLocalMagnitude,
                accumulator.lastTorqueLocalMagnitude
        );
    }

    private static void maybeCleanupDiagnostics(long now, int interval) {
        long cleanupEvery = Math.max(20L, interval);
        if (lastDiagnosticsCleanupTick != Long.MIN_VALUE && now - lastDiagnosticsCleanupTick < cleanupEvery) {
            return;
        }
        lastDiagnosticsCleanupTick = now;

        long staleThreshold = Math.max(200L, interval * 5L);
        WIND_OBJECT_DIAGNOSTICS.entrySet().removeIf(entry -> now - entry.getValue().lastSeenTick > staleThreshold);
    }

    private static final class WindObjectDiagnosticsAccumulator {
        long window;
        long lastSeenTick;
        int contributionCount;
        double totalWindSpeed;
        double maxWindSpeed;
        double totalExposure;
        double maxExposure;
        double totalForceMagnitude;
        double maxForceMagnitude;
        BlockPos samplePos;
        float lastWindAngle;
        double lastWindDirX;
        double lastWindDirY;
        double lastWindDirZ;
        double lastNormalX;
        double lastNormalY;
        double lastNormalZ;
        float lastWindSpeed;
        double lastExposure;
        double lastForceWorldMagnitude;
        double lastForceLocalMagnitude;
        double lastImpulseLocalMagnitude;
        double lastTorqueLocalMagnitude;
        boolean enabled;

        WindObjectDiagnosticsAccumulator(long window) {
            this.window = window;
            this.lastSeenTick = Long.MIN_VALUE;
        }

        void resetForWindow(long newWindow) {
            this.window = newWindow;
            this.contributionCount = 0;
            this.totalWindSpeed = 0.0D;
            this.maxWindSpeed = 0.0D;
            this.totalExposure = 0.0D;
            this.maxExposure = 0.0D;
            this.totalForceMagnitude = 0.0D;
            this.maxForceMagnitude = 0.0D;
            this.samplePos = null;
            this.lastWindAngle = 0.0F;
            this.lastWindDirX = 0.0D;
            this.lastWindDirY = 0.0D;
            this.lastWindDirZ = 0.0D;
            this.lastNormalX = 0.0D;
            this.lastNormalY = 0.0D;
            this.lastNormalZ = 0.0D;
            this.lastWindSpeed = 0.0F;
            this.lastExposure = 0.0D;
            this.lastForceWorldMagnitude = 0.0D;
            this.lastForceLocalMagnitude = 0.0D;
            this.lastImpulseLocalMagnitude = 0.0D;
            this.lastTorqueLocalMagnitude = 0.0D;
            this.enabled = false;
        }
    }

    public enum FrameMaterial implements StringRepresentable {
        TWISTER_PALE_PLANKS("twister_pale_planks"),
        ACACIA_PLANKS("acacia_planks"),
        BAMBOO_PLANKS("bamboo_planks"),
        BIRCH_PLANKS("birch_planks"),
        CHERRY_PLANKS("cherry_planks"),
        CRIMSON_PLANKS("crimson_planks"),
        DARK_OAK_PLANKS("dark_oak_planks"),
        JUNGLE_PLANKS("jungle_planks"),
        MANGROVE_PLANKS("mangrove_planks"),
        OAK_PLANKS("oak_planks"),
        SPRUCE_PLANKS("spruce_planks"),
        WARPED_PLANKS("warped_planks"),
        ACACIA_LOG("acacia_log"),
        BIRCH_LOG("birch_log"),
        CHERRY_LOG("cherry_log"),
        DARK_OAK_LOG("dark_oak_log"),
        JUNGLE_LOG("jungle_log"),
        MANGROVE_LOG("mangrove_log"),
        OAK_LOG("oak_log"),
        SPRUCE_LOG("spruce_log"),
        CRIMSON_STEM("crimson_stem"),
        WARPED_STEM("warped_stem"),
        STRIPPED_ACACIA_LOG("stripped_acacia_log"),
        STRIPPED_BIRCH_LOG("stripped_birch_log"),
        STRIPPED_CHERRY_LOG("stripped_cherry_log"),
        STRIPPED_DARK_OAK_LOG("stripped_dark_oak_log"),
        STRIPPED_JUNGLE_LOG("stripped_jungle_log"),
        STRIPPED_MANGROVE_LOG("stripped_mangrove_log"),
        STRIPPED_OAK_LOG("stripped_oak_log"),
        STRIPPED_SPRUCE_LOG("stripped_spruce_log"),
        STRIPPED_CRIMSON_STEM("stripped_crimson_stem"),
        STRIPPED_WARPED_STEM("stripped_warped_stem"),
        BLACK_WOOL("black_wool"),
        BLUE_WOOL("blue_wool"),
        BROWN_WOOL("brown_wool"),
        CYAN_WOOL("cyan_wool"),
        GRAY_WOOL("gray_wool"),
        GREEN_WOOL("green_wool"),
        LIGHT_BLUE_WOOL("light_blue_wool"),
        LIGHT_GRAY_WOOL("light_gray_wool"),
        LIME_WOOL("lime_wool"),
        MAGENTA_WOOL("magenta_wool"),
        ORANGE_WOOL("orange_wool"),
        PINK_WOOL("pink_wool"),
        PURPLE_WOOL("purple_wool"),
        RED_WOOL("red_wool"),
        WHITE_WOOL("white_wool"),
        YELLOW_WOOL("yellow_wool");

        private final String serializedName;

        FrameMaterial(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public @NotNull String getSerializedName() {
            return serializedName;
        }

        public static FrameMaterial fromBlock(Block block) {
            if (block == Blocks.ACACIA_PLANKS) return ACACIA_PLANKS;
            if (block == Blocks.BAMBOO_PLANKS) return BAMBOO_PLANKS;
            if (block == Blocks.BIRCH_PLANKS) return BIRCH_PLANKS;
            if (block == Blocks.CHERRY_PLANKS) return CHERRY_PLANKS;
            if (block == Blocks.CRIMSON_PLANKS) return CRIMSON_PLANKS;
            if (block == Blocks.DARK_OAK_PLANKS) return DARK_OAK_PLANKS;
            if (block == Blocks.JUNGLE_PLANKS) return JUNGLE_PLANKS;
            if (block == Blocks.MANGROVE_PLANKS) return MANGROVE_PLANKS;
            if (block == Blocks.OAK_PLANKS) return OAK_PLANKS;
            if (block == Blocks.SPRUCE_PLANKS) return SPRUCE_PLANKS;
            if (block == Blocks.WARPED_PLANKS) return WARPED_PLANKS;
            if (block == Blocks.ACACIA_LOG) return ACACIA_LOG;
            if (block == Blocks.BIRCH_LOG) return BIRCH_LOG;
            if (block == Blocks.CHERRY_LOG) return CHERRY_LOG;
            if (block == Blocks.DARK_OAK_LOG) return DARK_OAK_LOG;
            if (block == Blocks.JUNGLE_LOG) return JUNGLE_LOG;
            if (block == Blocks.MANGROVE_LOG) return MANGROVE_LOG;
            if (block == Blocks.OAK_LOG) return OAK_LOG;
            if (block == Blocks.SPRUCE_LOG) return SPRUCE_LOG;
            if (block == Blocks.CRIMSON_STEM) return CRIMSON_STEM;
            if (block == Blocks.WARPED_STEM) return WARPED_STEM;
            if (block == Blocks.STRIPPED_ACACIA_LOG) return STRIPPED_ACACIA_LOG;
            if (block == Blocks.STRIPPED_BIRCH_LOG) return STRIPPED_BIRCH_LOG;
            if (block == Blocks.STRIPPED_CHERRY_LOG) return STRIPPED_CHERRY_LOG;
            if (block == Blocks.STRIPPED_DARK_OAK_LOG) return STRIPPED_DARK_OAK_LOG;
            if (block == Blocks.STRIPPED_JUNGLE_LOG) return STRIPPED_JUNGLE_LOG;
            if (block == Blocks.STRIPPED_MANGROVE_LOG) return STRIPPED_MANGROVE_LOG;
            if (block == Blocks.STRIPPED_OAK_LOG) return STRIPPED_OAK_LOG;
            if (block == Blocks.STRIPPED_SPRUCE_LOG) return STRIPPED_SPRUCE_LOG;
            if (block == Blocks.STRIPPED_CRIMSON_STEM) return STRIPPED_CRIMSON_STEM;
            if (block == Blocks.STRIPPED_WARPED_STEM) return STRIPPED_WARPED_STEM;
            if (block == Blocks.BLACK_WOOL) return BLACK_WOOL;
            if (block == Blocks.BLUE_WOOL) return BLUE_WOOL;
            if (block == Blocks.BROWN_WOOL) return BROWN_WOOL;
            if (block == Blocks.CYAN_WOOL) return CYAN_WOOL;
            if (block == Blocks.GRAY_WOOL) return GRAY_WOOL;
            if (block == Blocks.GREEN_WOOL) return GREEN_WOOL;
            if (block == Blocks.LIGHT_BLUE_WOOL) return LIGHT_BLUE_WOOL;
            if (block == Blocks.LIGHT_GRAY_WOOL) return LIGHT_GRAY_WOOL;
            if (block == Blocks.LIME_WOOL) return LIME_WOOL;
            if (block == Blocks.MAGENTA_WOOL) return MAGENTA_WOOL;
            if (block == Blocks.ORANGE_WOOL) return ORANGE_WOOL;
            if (block == Blocks.PINK_WOOL) return PINK_WOOL;
            if (block == Blocks.PURPLE_WOOL) return PURPLE_WOOL;
            if (block == Blocks.RED_WOOL) return RED_WOOL;
            if (block == Blocks.WHITE_WOOL) return WHITE_WOOL;
            if (block == Blocks.YELLOW_WOOL) return YELLOW_WOOL;

            return null;
        }
    }

    @MethodsReturnNonnullByDefault
    private static class PlacementHelper implements IPlacementHelper {

        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock() instanceof SailBlock;
        }

        @Override
        public Predicate<BlockState> getStatePredicate() {
            return state -> state.getBlock() instanceof SailBlock;
        }

        @Override
        public PlacementOffset getOffset(
                @NotNull Player player,
                @NotNull Level world,
                @NotNull BlockState state,
                @NotNull BlockPos pos,
                @NotNull BlockHitResult ray
        ) {
            List<Direction> directions = IPlacementHelper.orderedByDistanceExceptAxis(
                    pos,
                    SablePlacementHitHelper.ensureHitLocationInSameSpaceAsPos(world, pos, ray),
                    state.getValue(SailBlock.FACING).getAxis(),
                    direction -> true
            );

            int range = TwisterMillConfig.getResolvedSailPlacementAssistRange();
            for (Direction direction : directions) {
                for (int distance = 1; distance <= range; distance++) {
                    BlockPos candidatePos = pos.relative(direction, distance);
                    BlockState candidateState = world.getBlockState(candidatePos);

                    if (candidateState.canBeReplaced()) {
                        return PlacementOffset.success(
                                candidatePos,
                                placedState -> placedState.setValue(FACING, state.getValue(FACING))
                        );
                    }

                    if (candidateState.getBlock() instanceof SailBlock) {
                        continue;
                    }

                    break;
                }
            }

            return PlacementOffset.fail();
        }
    }
}
