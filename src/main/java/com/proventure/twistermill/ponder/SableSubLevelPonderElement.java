package com.proventure.twistermill.ponder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.client.TwisterMillPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.StructureTemplateExtension;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.ponder.api.element.PonderSceneElement;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.PonderElementBase;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SableSubLevelPonderElement extends PonderElementBase implements PonderSceneElement {

    private static final int PITCH_TO_POSITIVE_TICKS = 40;
    private static final int PITCH_HOLD_TICKS = 20;
    private static final int PITCH_TO_NEGATIVE_TICKS = 40;
    private static final int PITCH_TO_ZERO_TICKS = 40;
    private static final int PITCH_FINAL_HOLD_TICKS = 20;
    private static final int PITCH_TOTAL_TICKS = PITCH_TO_POSITIVE_TICKS + PITCH_HOLD_TICKS
            + PITCH_TO_NEGATIVE_TICKS + PITCH_TO_ZERO_TICKS + PITCH_FINAL_HOLD_TICKS;
    private static final int ROTOR_DEMO_TICKS = 140;
    private static final int WRVB_YAW_DEMO_TICKS = 60;
    private static final int SYNCHRONIZED_WRVB_YAW_DEMO_TICKS = 120;
    private static final float PITCH_POSITIVE_DEGREES = 45.0F;
    private static final float PITCH_NEGATIVE_DEGREES = -45.0F;
    private static final float WRVB_YAW_DEGREES = 360.0F;
    private static final float SYNCHRONIZED_ROTOR_DEGREES = WRVB_YAW_DEGREES * 4.0F;
    private static final float ROTOR_DEGREES_PER_TICK = 16.0F * 360.0F / (60.0F * 20.0F);
    private static final float[] BLADE_ARM_SLOT_DEGREES = {0.0F, 120.0F, 240.0F};
    private static final double AXIS_EPSILON = 1.0E-6D;
    private static final double ANTENNA_DOWN_OFFSET = 14.0D / 16.0D;
    private static final BlockPos ROOT_WRB_POS = new BlockPos(6, 8, 4);
    private static final BlockPos ROOT_WRB_SHAFT_A_POS = new BlockPos(6, 8, 5);
    private static final BlockPos ROOT_WRB_SHAFT_B_POS = new BlockPos(6, 8, 7);

    private final ResourceLocation schematicLocation;
    private RenderableSchematic renderableSchematic;
    private boolean pitchDemoActive;
    private int pitchDemoTicks;
    private float previousPitchDegrees;
    private float pitchDegrees;
    private boolean rotorDemoActive;
    private boolean synchronizedRotorAndWrvbYawDemoActive;
    private int rotorDemoTicks;
    private int rotorDemoTickLimit = ROTOR_DEMO_TICKS;
    private float rotorDemoDegreesPerTick = ROTOR_DEGREES_PER_TICK;
    private float previousRotorDegrees;
    private float rotorDegrees;
    private boolean wrvbYawDemoActive;
    private int wrvbYawDemoTicks;
    private int wrvbYawDemoTickLimit = WRVB_YAW_DEMO_TICKS;
    private float previousWrvbYawDegrees;
    private float wrvbYawDegrees;

    private SableSubLevelPonderElement(ResourceLocation schematicLocation) {
        this.schematicLocation = schematicLocation;
    }

    static SableSubLevelPonderElement fromSchematic(ResourceLocation schematicLocation) {
        return new SableSubLevelPonderElement(schematicLocation);
    }

    void startPitchDemo() {
        synchronizedRotorAndWrvbYawDemoActive = false;
        pitchDemoActive = true;
        pitchDemoTicks = 0;
        previousPitchDegrees = 0.0F;
        pitchDegrees = 0.0F;
    }

    void startRotorDemo() {
        pitchDemoActive = false;
        pitchDemoTicks = PITCH_TOTAL_TICKS;
        previousPitchDegrees = 0.0F;
        pitchDegrees = 0.0F;
        rotorDemoActive = true;
        synchronizedRotorAndWrvbYawDemoActive = false;
        rotorDemoTicks = 0;
        rotorDemoTickLimit = ROTOR_DEMO_TICKS;
        rotorDemoDegreesPerTick = ROTOR_DEGREES_PER_TICK;
        previousRotorDegrees = 0.0F;
        rotorDegrees = 0.0F;
    }

    static int synchronizedRotorAndWrvbYawDemoTicks() {
        return SYNCHRONIZED_WRVB_YAW_DEMO_TICKS;
    }

    void startSynchronizedRotorAndWrvbYawDemo() {
        pitchDemoActive = false;
        pitchDemoTicks = PITCH_TOTAL_TICKS;
        previousPitchDegrees = 0.0F;
        pitchDegrees = 0.0F;
        rotorDemoActive = true;
        synchronizedRotorAndWrvbYawDemoActive = true;
        rotorDemoTicks = 0;
        rotorDemoTickLimit = SYNCHRONIZED_WRVB_YAW_DEMO_TICKS;
        rotorDemoDegreesPerTick = SYNCHRONIZED_ROTOR_DEGREES / SYNCHRONIZED_WRVB_YAW_DEMO_TICKS;
        previousRotorDegrees = 0.0F;
        rotorDegrees = 0.0F;
        wrvbYawDemoActive = true;
        wrvbYawDemoTicks = 0;
        wrvbYawDemoTickLimit = SYNCHRONIZED_WRVB_YAW_DEMO_TICKS;
        previousWrvbYawDegrees = 0.0F;
        wrvbYawDegrees = 0.0F;
    }

    void startWrvbYawDemo() {
        pitchDemoActive = false;
        pitchDemoTicks = PITCH_TOTAL_TICKS;
        previousPitchDegrees = 0.0F;
        pitchDegrees = 0.0F;
        rotorDemoActive = true;
        synchronizedRotorAndWrvbYawDemoActive = false;
        rotorDemoTicks = ROTOR_DEMO_TICKS;
        rotorDemoTickLimit = ROTOR_DEMO_TICKS;
        rotorDemoDegreesPerTick = ROTOR_DEGREES_PER_TICK;
        previousRotorDegrees = computeRotorDegrees(ROTOR_DEMO_TICKS);
        rotorDegrees = computeRotorDegrees(ROTOR_DEMO_TICKS);
        wrvbYawDemoActive = true;
        wrvbYawDemoTicks = 0;
        wrvbYawDemoTickLimit = WRVB_YAW_DEMO_TICKS;
        previousWrvbYawDegrees = 0.0F;
        wrvbYawDegrees = 0.0F;
    }

    @Override
    public void tick(PonderScene scene) {
        if (pitchDemoActive) {
            previousPitchDegrees = pitchDegrees;
            if (pitchDemoTicks < PITCH_TOTAL_TICKS) {
                pitchDemoTicks++;
            }
            pitchDegrees = computePitchDegrees(pitchDemoTicks);
        }

        if (rotorDemoActive) {
            previousRotorDegrees = rotorDegrees;
            if (rotorDemoTicks < rotorDemoTickLimit) {
                rotorDemoTicks++;
            }
            rotorDegrees = computeRotorDegrees(rotorDemoTicks, rotorDemoTickLimit, rotorDemoDegreesPerTick,
                    synchronizedRotorAndWrvbYawDemoActive);
        }

        if (wrvbYawDemoActive) {
            previousWrvbYawDegrees = wrvbYawDegrees;
            if (wrvbYawDemoTicks < wrvbYawDemoTickLimit) {
                wrvbYawDemoTicks++;
            }
            wrvbYawDegrees = computeWrvbYawDegrees(wrvbYawDemoTicks, wrvbYawDemoTickLimit,
                    synchronizedRotorAndWrvbYawDemoActive);
        }
    }

    @Override
    public void renderFirst(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float pt) {
    }

    @Override
    public void renderLayer(PonderLevel world, MultiBufferSource buffer, RenderType type, GuiGraphics graphics, float pt) {
        RenderableSchematic schematic = getRenderableSchematic(world);
        List<RenderableSubLevel> renderableSubLevels = schematic.subLevels();
        List<RenderableRootRotorPart> rootRotorParts = schematic.rootRotorParts();
        List<RenderableWrvbYawRootPart> wrvbYawRootParts = schematic.wrvbYawRootParts();
        if (renderableSubLevels.isEmpty() && rootRotorParts.isEmpty() && wrvbYawRootParts.isEmpty()) {
            return;
        }

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        ModelBlockRenderer renderer = dispatcher.getModelRenderer();
        RandomSource random = RandomSource.create();
        PoseStack poseStack = graphics.pose();
        float renderedPitchDegrees = Mth.lerp(pt, previousPitchDegrees, pitchDegrees);
        float renderedRotorDegrees = Mth.lerp(pt, previousRotorDegrees, rotorDegrees);
        float renderedWrvbYawDegrees = Mth.lerp(pt, previousWrvbYawDegrees, wrvbYawDegrees);
        Vector3d rotorPivot = resolveRotorPivot(renderableSubLevels);
        WrvbYawGeometry wrvbYawGeometry = schematic.wrvbYawGeometry();

        for (RenderableSubLevel subLevel : renderableSubLevels) {
            SchematicLevel renderWorld = subLevel.level();
            BoundingBox bounds = renderWorld.getBounds();
            renderWorld.renderMode = true;

            poseStack.pushPose();
            applyWrvbYaw(poseStack, wrvbYawGeometry, renderedWrvbYawDegrees);
            applyRotor(poseStack, rotorPivot, renderedRotorDegrees);
            poseStack.translate(subLevel.position().x, subLevel.position().y, subLevel.position().z);
            poseStack.mulPose(new Quaternionf(subLevel.orientation()));

            for (BlockPos localPos : BlockPos.betweenClosed(
                    bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
                BlockState state = renderWorld.getBlockState(localPos);
                if (state.is(ModBlocks.SERVO_TWISTER_BLOCK.get()) && type == RenderType.cutout()) {
                    poseStack.pushPose();
                    poseStack.translate(localPos.getX(), localPos.getY(), localPos.getZ());
                    DirectionalServoAntenna.render(state, poseStack, buffer);
                    poseStack.popPose();

                    poseStack.pushPose();
                    applyPitch(poseStack, subLevel.pitchGeometry(), renderedPitchDegrees);
                    poseStack.translate(localPos.getX(), localPos.getY(), localPos.getZ());
                    DirectionalServoTop.render(state, poseStack, buffer);
                    poseStack.popPose();
                }

                if (state.getRenderShape() != RenderShape.MODEL) {
                    continue;
                }

                BakedModel model = dispatcher.getBlockModel(state);
                BlockEntity blockEntity = renderWorld.getBlockEntity(localPos);
                ModelData modelData = blockEntity != null ? blockEntity.getModelData() : ModelData.EMPTY;
                modelData = model.getModelData(renderWorld, localPos, state, modelData);

                long seed = state.getSeed(localPos);
                random.setSeed(seed);
                if (!model.getRenderTypes(state, random, modelData).contains(type)) {
                    continue;
                }

                poseStack.pushPose();
                if (isPitchRotatingBlock(state)) {
                    applyPitch(poseStack, subLevel.pitchGeometry(), renderedPitchDegrees);
                }
                poseStack.translate(localPos.getX(), localPos.getY(), localPos.getZ());
                renderer.tesselateBlock(renderWorld, model, state, localPos, poseStack, buffer.getBuffer(type), true,
                        random, seed, OverlayTexture.NO_OVERLAY, modelData, type);
                poseStack.popPose();
            }

            poseStack.popPose();
            renderWorld.renderMode = false;
        }

        if (rotorPivot != null && !rootRotorParts.isEmpty()) {
            renderRootRotorParts(world, buffer, type, dispatcher, renderer, random, poseStack,
                    rootRotorParts, wrvbYawGeometry, renderedWrvbYawDegrees, rotorPivot,
                    renderedRotorDegrees, rotorDemoActive, synchronizedRotorAndWrvbYawDemoActive);
        }

        if (wrvbYawDemoActive && wrvbYawGeometry != null && !wrvbYawRootParts.isEmpty()) {
            renderWrvbYawRootParts(world, buffer, type, dispatcher, renderer, random, poseStack,
                    wrvbYawRootParts, wrvbYawGeometry, renderedWrvbYawDegrees);
        }
    }

    @Override
    public void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float pt) {
    }

    private RenderableSchematic getRenderableSchematic(PonderLevel world) {
        if (renderableSchematic == null) {
            renderableSchematic = loadRenderableSchematic(world);
        }
        return renderableSchematic;
    }

    private RenderableSchematic loadRenderableSchematic(PonderLevel world) {
        StructureTemplate template = PonderSceneRegistry.loadSchematic(schematicLocation);
        List<RenderableRootRotorPart> rootRotorParts = loadRootRotorParts(template);
        WrvbYawGeometry wrvbYawGeometry = resolveWrvbYawGeometry(template);
        List<RenderableWrvbYawRootPart> wrvbYawRootParts = loadWrvbYawRootParts(template, wrvbYawGeometry, rootRotorParts);
        if (!(template instanceof StructureTemplateExtension extension)) {
            return new RenderableSchematic(List.of(), rootRotorParts, wrvbYawRootParts, wrvbYawGeometry);
        }

        List<RenderableSubLevel> renderableSubLevels = new ArrayList<>();
        for (StructureTemplateExtension.SubLevelTemplate subLevelTemplate : extension.sable$getSubLevels()) {
            SchematicLevel subLevel = new SchematicLevel(world);
            subLevelTemplate.template().placeInWorld(subLevel, BlockPos.ZERO, BlockPos.ZERO,
                    new StructurePlaceSettings(), world.getRandom(), Block.UPDATE_CLIENTS);
            renderableSubLevels.add(new RenderableSubLevel(
                    subLevelTemplate.position(),
                    subLevelTemplate.orientation(),
                    subLevel,
                    resolvePitchGeometry(subLevel)));
        }
        return new RenderableSchematic(List.copyOf(renderableSubLevels), rootRotorParts, wrvbYawRootParts, wrvbYawGeometry);
    }

    private static List<RenderableRootRotorPart> loadRootRotorParts(StructureTemplate template) {
        List<RenderableRootRotorPart> parts = new ArrayList<>();

        addBladeArmParts(parts, template, ModBlocks.BLADE_ARM_BLOCK.get());
        addBladeArmParts(parts, template, ModBlocks.BLADE_ARM_EASTFACE_BLOCK.get());
        addBladeArmParts(parts, template, ModBlocks.BLADE_ARM_WESTFACE_BLOCK.get());
        addRootRotorPartAt(parts, template, ROOT_WRB_POS, ModBlocks.WIND_ROTO_BLOCK.get(), RootRotorPartRole.WIND_ROTO_TOP);
        addRootRotorPartAt(parts, template, ROOT_WRB_SHAFT_A_POS, "create", "shaft", RootRotorPartRole.KINETIC_SHAFT);
        addRootRotorPartAt(parts, template, ROOT_WRB_SHAFT_B_POS, "create", "shaft", RootRotorPartRole.KINETIC_SHAFT);

        List<BlockPos> windRotoPositions = filterRootBlocks(template, ModBlocks.WIND_ROTO_BLOCK.get())
                .stream()
                .map(StructureTemplate.StructureBlockInfo::pos)
                .toList();
        for (StructureTemplate.StructureBlockInfo info : filterRootBlocks(template, ModBlocks.SERVO_TWISTER_BLOCK.get())) {
            Direction facing = info.state().hasProperty(BlockStateProperties.FACING)
                    ? info.state().getValue(BlockStateProperties.FACING)
                    : Direction.NORTH;
            if (windRotoPositions.contains(info.pos().relative(facing.getOpposite()))) {
                parts.add(new RenderableRootRotorPart(info.pos(), info.state(), RootRotorPartRole.CENTRAL_SERVO));
            }
        }

        parts.sort(Comparator.comparing(RenderableRootRotorPart::pos, SableSubLevelPonderElement::compareBlockPos));
        return List.copyOf(parts);
    }

    private static void addBladeArmParts(List<RenderableRootRotorPart> parts, StructureTemplate template, Block bladeArmBlock) {
        addRootRotorParts(parts, template, bladeArmBlock, RootRotorPartRole.BLADE_ARM);
    }

    private static void addRootRotorParts(List<RenderableRootRotorPart> parts, StructureTemplate template,
                                          Block block, RootRotorPartRole role) {
        for (StructureTemplate.StructureBlockInfo info : filterRootBlocks(template, block)) {
            parts.add(new RenderableRootRotorPart(info.pos(), info.state(), role));
        }
    }

    private static void addRootRotorPartAt(List<RenderableRootRotorPart> parts, StructureTemplate template,
                                           BlockPos pos, String namespace, String path, RootRotorPartRole role) {
        BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(namespace, path))
                .ifPresent(block -> addRootRotorPartAt(parts, template, pos, block, role));
    }

    private static void addRootRotorPartAt(List<RenderableRootRotorPart> parts, StructureTemplate template,
                                           BlockPos pos, Block block, RootRotorPartRole role) {
        for (StructureTemplate.StructureBlockInfo info : filterRootBlocks(template, block)) {
            if (info.pos().equals(pos)) {
                parts.add(new RenderableRootRotorPart(info.pos(), info.state(), role));
                return;
            }
        }
    }

    private static WrvbYawGeometry resolveWrvbYawGeometry(StructureTemplate template) {
        List<StructureTemplate.StructureBlockInfo> wrvbBlocks = filterRootBlocks(template, ModBlocks.WIND_ROTO_VERTICAL_BLOCK.get());
        if (wrvbBlocks.isEmpty()) {
            return null;
        }

        BlockPos wrvbPos = wrvbBlocks.stream()
                .map(StructureTemplate.StructureBlockInfo::pos)
                .min(SableSubLevelPonderElement::compareBlockPos)
                .orElse(null);
        if (wrvbPos == null) {
            return null;
        }

        Set<BlockPos> traversePositions = new HashSet<>();
        for (StructureTemplate.StructureBlockInfo info : filterRootBlocks(template, ModBlocks.METAL_TRAVERSE.get())) {
            traversePositions.add(info.pos().immutable());
        }
        for (StructureTemplate.StructureBlockInfo info : filterRootBlocks(
                template, ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get())) {
            traversePositions.add(info.pos().immutable());
        }

        Direction bestDirection = null;
        List<BlockPos> bestChain = List.of();
        for (Direction direction : Direction.values()) {
            List<BlockPos> chain = new ArrayList<>();
            BlockPos cursor = wrvbPos.relative(direction);
            while (traversePositions.contains(cursor)) {
                chain.add(cursor.immutable());
                cursor = cursor.relative(direction);
            }
            if (chain.size() > bestChain.size()) {
                bestDirection = direction;
                bestChain = chain;
            }
        }

        if (bestDirection == null || bestChain.isEmpty()) {
            return null;
        }

        Vector3d axisDirection = new Vector3d(
                bestDirection.getStepX(),
                bestDirection.getStepY(),
                bestDirection.getStepZ());
        if (axisDirection.lengthSquared() < AXIS_EPSILON) {
            return null;
        }

        axisDirection.normalize();
        return new WrvbYawGeometry(wrvbPos.immutable(), centerOf(wrvbPos), axisDirection, List.copyOf(bestChain));
    }

    private static List<RenderableWrvbYawRootPart> loadWrvbYawRootParts(StructureTemplate template,
                                                                         WrvbYawGeometry geometry,
                                                                         List<RenderableRootRotorPart> rootRotorParts) {
        if (geometry == null) {
            return List.of();
        }

        Map<BlockPos, StructureTemplate.StructureBlockInfo> candidates = new HashMap<>();
        addRootBlockCandidates(candidates, template, ModBlocks.METAL_TRAVERSE.get());
        addRootBlockCandidates(candidates, template, ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get());
        addRootBlockCandidates(candidates, template, ModBlocks.WIND_ROTO_BLOCK.get());
        addRootBlockCandidates(candidates, template, ModBlocks.TWISTER_SAIL_BLOCK.get());
        addRootBlockCandidates(candidates, template, ModBlocks.TWISTER_SAIL_FRAME_BLOCK.get());
        addRootBlockCandidates(candidates, template, "create", "shaft");
        addRootBlockCandidates(candidates, template, "create", "clutch");
        addRootBlockCandidates(candidates, template, "create", "stressometer");

        for (RenderableRootRotorPart rootRotorPart : rootRotorParts) {
            if (rootRotorPart.role() != RootRotorPartRole.WIND_ROTO_TOP) {
                candidates.remove(rootRotorPart.pos());
            }
        }
        candidates.remove(geometry.wrvbPos());

        List<RenderableWrvbYawRootPart> parts = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(geometry.axisChainPositions());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos)) {
                continue;
            }

            StructureTemplate.StructureBlockInfo info = candidates.get(pos);
            if (info == null) {
                continue;
            }

            parts.add(new RenderableWrvbYawRootPart(info.pos(), info.state()));
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!visited.contains(next) && candidates.containsKey(next)) {
                    queue.addLast(next.immutable());
                }
            }
        }

        parts.sort(Comparator.comparing(RenderableWrvbYawRootPart::pos, SableSubLevelPonderElement::compareBlockPos));
        return List.copyOf(parts);
    }

    private static void addRootBlockCandidates(Map<BlockPos, StructureTemplate.StructureBlockInfo> candidates,
                                               StructureTemplate template, String namespace, String path) {
        BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(namespace, path))
                .ifPresent(block -> addRootBlockCandidates(candidates, template, block));
    }

    private static void addRootBlockCandidates(Map<BlockPos, StructureTemplate.StructureBlockInfo> candidates,
                                               StructureTemplate template, Block block) {
        for (StructureTemplate.StructureBlockInfo info : filterRootBlocks(template, block)) {
            candidates.putIfAbsent(info.pos().immutable(), info);
        }
    }

    private static List<StructureTemplate.StructureBlockInfo> filterRootBlocks(StructureTemplate template, Block block) {
        return template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block, false);
    }

    private static float computePitchDegrees(int ticks) {
        if (ticks <= 0) {
            return 0.0F;
        }
        if (ticks <= PITCH_TO_POSITIVE_TICKS) {
            return Mth.lerp(ticks / (float) PITCH_TO_POSITIVE_TICKS, 0.0F, PITCH_POSITIVE_DEGREES);
        }
        if (ticks <= PITCH_TO_POSITIVE_TICKS + PITCH_HOLD_TICKS) {
            return PITCH_POSITIVE_DEGREES;
        }
        int negativeEndTick = PITCH_TO_POSITIVE_TICKS + PITCH_HOLD_TICKS + PITCH_TO_NEGATIVE_TICKS;
        if (ticks <= negativeEndTick) {
            float phase = (ticks - PITCH_TO_POSITIVE_TICKS - PITCH_HOLD_TICKS) / (float) PITCH_TO_NEGATIVE_TICKS;
            return Mth.lerp(phase, PITCH_POSITIVE_DEGREES, PITCH_NEGATIVE_DEGREES);
        }
        int zeroEndTick = negativeEndTick + PITCH_TO_ZERO_TICKS;
        if (ticks <= zeroEndTick) {
            float phase = (ticks - negativeEndTick) / (float) PITCH_TO_ZERO_TICKS;
            return Mth.lerp(phase, PITCH_NEGATIVE_DEGREES, 0.0F);
        }
        return 0.0F;
    }

    private static float computeRotorDegrees(int ticks) {
        return computeRotorDegrees(ticks, ROTOR_DEMO_TICKS, ROTOR_DEGREES_PER_TICK, false);
    }

    private static float computeRotorDegrees(int ticks, int tickLimit, float degreesPerTick, boolean eased) {
        int safeTickLimit = Math.max(1, tickLimit);
        float totalDegrees = safeTickLimit * degreesPerTick;
        return computeDemoProgress(ticks, safeTickLimit, eased) * totalDegrees;
    }

    private static float computeWrvbYawDegrees(int ticks) {
        return computeWrvbYawDegrees(ticks, WRVB_YAW_DEMO_TICKS, false);
    }

    private static float computeWrvbYawDegrees(int ticks, int tickLimit, boolean eased) {
        int safeTickLimit = Math.max(1, tickLimit);
        return computeDemoProgress(ticks, safeTickLimit, eased) * WRVB_YAW_DEGREES;
    }

    private static float computeDemoProgress(int ticks, int tickLimit, boolean eased) {
        float progress = Mth.clamp(ticks / (float) tickLimit, 0.0F, 1.0F);
        if (!eased) {
            return progress;
        }
        return progress * progress * (3.0F - 2.0F * progress);
    }

    private static boolean isPitchRotatingBlock(BlockState state) {
        return state.is(ModBlocks.TWISTER_SAIL_BLOCK.get())
                || state.is(ModBlocks.TWISTER_SAIL_FRAME_BLOCK.get());
    }

    private static PitchGeometry resolvePitchGeometry(SchematicLevel level) {
        BoundingBox bounds = level.getBounds();
        BlockPos blackWoolPos = null;
        BlockPos servoPos = null;
        List<BlockPos> sailPositions = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            BlockPos immutablePos = pos.immutable();
            BlockState state = level.getBlockState(immutablePos);
            if (state.is(Blocks.BLACK_WOOL) && (blackWoolPos == null || compareBlockPos(immutablePos, blackWoolPos) < 0)) {
                blackWoolPos = immutablePos;
            } else if (state.is(ModBlocks.SERVO_TWISTER_BLOCK.get()) && (servoPos == null || compareBlockPos(immutablePos, servoPos) < 0)) {
                servoPos = immutablePos;
            } else if (state.is(ModBlocks.TWISTER_SAIL_BLOCK.get())) {
                sailPositions.add(immutablePos);
            }
        }

        if (blackWoolPos == null || servoPos == null || sailPositions.isEmpty()) {
            return null;
        }

        Vector3d axisStart = centerOf(blackWoolPos);
        Vector3d roughAxis = centerOf(servoPos).sub(axisStart, new Vector3d());
        if (roughAxis.lengthSquared() < AXIS_EPSILON) {
            return null;
        }
        roughAxis.normalize();

        BlockPos endSailPos = null;
        double bestProjection = Double.NEGATIVE_INFINITY;
        double bestPerpendicularDistance = Double.POSITIVE_INFINITY;
        for (BlockPos sailPos : sailPositions) {
            Vector3d sailOffset = centerOf(sailPos).sub(axisStart, new Vector3d());
            double projection = sailOffset.dot(roughAxis);
            if (projection <= AXIS_EPSILON) {
                continue;
            }

            Vector3d projected = new Vector3d(roughAxis).mul(projection);
            double perpendicularDistance = sailOffset.sub(projected).lengthSquared();
            if (projection > bestProjection + AXIS_EPSILON
                    || (Math.abs(projection - bestProjection) <= AXIS_EPSILON
                    && (perpendicularDistance < bestPerpendicularDistance - AXIS_EPSILON
                    || (Math.abs(perpendicularDistance - bestPerpendicularDistance) <= AXIS_EPSILON
                    && (endSailPos == null || compareBlockPos(sailPos, endSailPos) < 0))))) {
                endSailPos = sailPos;
                bestProjection = projection;
                bestPerpendicularDistance = perpendicularDistance;
            }
        }

        if (endSailPos == null) {
            return null;
        }

        Vector3d axisDirection = centerOf(endSailPos).sub(axisStart, new Vector3d());
        if (axisDirection.lengthSquared() < AXIS_EPSILON) {
            return null;
        }
        axisDirection.normalize();
        return new PitchGeometry(axisStart, axisDirection, centerOf(servoPos));
    }

    private static Vector3d resolveRotorPivot(List<RenderableSubLevel> subLevels) {
        Vector3d sum = new Vector3d();
        int count = 0;

        for (RenderableSubLevel subLevel : subLevels) {
            PitchGeometry geometry = subLevel.pitchGeometry();
            if (geometry == null) {
                continue;
            }

            sum.add(toRootSpace(subLevel, geometry.axisStart()));
            sum.add(toRootSpace(subLevel, geometry.servoCenter()));
            count += 2;
        }

        if (count == 0) {
            return null;
        }
        return sum.div(count);
    }

    private static Vector3d toRootSpace(RenderableSubLevel subLevel, Vector3d local) {
        Vector3d root = new Vector3d(local);
        subLevel.orientation().transform(root);
        return root.add(subLevel.position());
    }

    private static Vector3d centerOf(BlockPos pos) {
        return new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    private static int compareBlockPos(BlockPos first, BlockPos second) {
        int x = Integer.compare(first.getX(), second.getX());
        if (x != 0) {
            return x;
        }
        int y = Integer.compare(first.getY(), second.getY());
        if (y != 0) {
            return y;
        }
        return Integer.compare(first.getZ(), second.getZ());
    }

    private static void applyPitch(PoseStack poseStack, PitchGeometry pitchGeometry, float pitchDegrees) {
        if (pitchGeometry == null || Math.abs(pitchDegrees) < 1.0E-4F) {
            return;
        }

        Vector3d start = pitchGeometry.axisStart();
        Vector3d axis = pitchGeometry.axisDirection();
        poseStack.translate(start.x, start.y, start.z);
        poseStack.mulPose(new Quaternionf().rotationAxis(
                (float) Math.toRadians(pitchDegrees),
                (float) axis.x,
                (float) axis.y,
                (float) axis.z));
        poseStack.translate(-start.x, -start.y, -start.z);
    }

    private static void applyRotor(PoseStack poseStack, Vector3d rotorPivot, float rotorDegrees) {
        if (rotorPivot == null || Math.abs(rotorDegrees) < 1.0E-4F) {
            return;
        }

        poseStack.translate(rotorPivot.x, rotorPivot.y, rotorPivot.z);
        poseStack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(rotorDegrees), 0.0F, 0.0F, 1.0F));
        poseStack.translate(-rotorPivot.x, -rotorPivot.y, -rotorPivot.z);
    }

    private static void applyWrvbYaw(PoseStack poseStack, WrvbYawGeometry geometry, float yawDegrees) {
        if (geometry == null || Math.abs(yawDegrees) < 1.0E-4F) {
            return;
        }

        Vector3d pivot = geometry.pivot();
        Vector3d axis = geometry.axisDirection();
        poseStack.translate(pivot.x, pivot.y, pivot.z);
        poseStack.mulPose(new Quaternionf().rotationAxis(
                (float) Math.toRadians(yawDegrees),
                (float) axis.x,
                (float) axis.y,
                (float) axis.z));
        poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
    }

    private static void renderRootRotorParts(PonderLevel world, MultiBufferSource buffer, RenderType type,
                                             BlockRenderDispatcher dispatcher, ModelBlockRenderer renderer,
                                             RandomSource random, PoseStack poseStack,
                                             List<RenderableRootRotorPart> rootRotorParts,
                                             WrvbYawGeometry wrvbYawGeometry, float renderedWrvbYawDegrees,
                                             Vector3d rotorPivot, float renderedRotorDegrees,
                                             boolean rotorDemoActive, boolean renderRotorOutputParts) {
        for (RenderableRootRotorPart rootPart : rootRotorParts) {
            if (rootPart.role() == RootRotorPartRole.BLADE_ARM) {
                renderBladeArmTripletPart(world, buffer, type, dispatcher, renderer, random, poseStack,
                        rootPart, wrvbYawGeometry, renderedWrvbYawDegrees, rotorPivot,
                        renderedRotorDegrees, rotorDemoActive);
            } else if (rootPart.role() == RootRotorPartRole.CENTRAL_SERVO) {
                renderCentralServoRootPart(world, buffer, type, dispatcher, renderer, random, poseStack,
                        rootPart, wrvbYawGeometry, renderedWrvbYawDegrees, rotorPivot,
                        rotorDemoActive ? renderedRotorDegrees : 0.0F);
            } else if (rootPart.role() == RootRotorPartRole.WIND_ROTO_TOP) {
                if (renderRotorOutputParts) {
                    renderWindRotoTopPart(buffer, type, poseStack, rootPart, wrvbYawGeometry,
                            renderedWrvbYawDegrees, rotorPivot, renderedRotorDegrees);
                }
            } else if (rootPart.role() == RootRotorPartRole.KINETIC_SHAFT && renderRotorOutputParts) {
                renderKineticShaftRootPart(buffer, type, poseStack, rootPart, wrvbYawGeometry,
                        renderedWrvbYawDegrees, rotorPivot, renderedRotorDegrees);
            }
        }
    }

    private static void renderBladeArmTripletPart(PonderLevel world, MultiBufferSource buffer, RenderType type,
                                                  BlockRenderDispatcher dispatcher, ModelBlockRenderer renderer,
                                                  RandomSource random, PoseStack poseStack,
                                                  RenderableRootRotorPart rootPart,
                                                  WrvbYawGeometry wrvbYawGeometry, float renderedWrvbYawDegrees,
                                                  Vector3d rotorPivot, float renderedRotorDegrees,
                                                  boolean rotorDemoActive) {
        for (int slot = 0; slot < BLADE_ARM_SLOT_DEGREES.length; slot++) {
            float angle = BLADE_ARM_SLOT_DEGREES[slot] + (rotorDemoActive ? renderedRotorDegrees : 0.0F);
            renderRootBlockModel(world, buffer, type, dispatcher, renderer, random, poseStack,
                    rootPart.pos(), rootPart.state(), wrvbYawGeometry, renderedWrvbYawDegrees, rotorPivot, angle);
        }
    }

    private static void renderCentralServoRootPart(PonderLevel world, MultiBufferSource buffer, RenderType type,
                                                   BlockRenderDispatcher dispatcher, ModelBlockRenderer renderer,
                                                   RandomSource random, PoseStack poseStack,
                                                   RenderableRootRotorPart rootPart,
                                                   WrvbYawGeometry wrvbYawGeometry, float renderedWrvbYawDegrees,
                                                   Vector3d rotorPivot, float renderedRotorDegrees) {
        BlockPos pos = rootPart.pos();
        BlockState state = rootPart.state();

        if (type == RenderType.cutout()) {
            poseStack.pushPose();
            applyWrvbYaw(poseStack, wrvbYawGeometry, renderedWrvbYawDegrees);
            applyRotor(poseStack, rotorPivot, renderedRotorDegrees);
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            DirectionalServoTop.render(state, poseStack, buffer);
            poseStack.popPose();
        }

        renderRootBlockModel(world, buffer, type, dispatcher, renderer, random, poseStack,
                pos, state, wrvbYawGeometry, renderedWrvbYawDegrees, rotorPivot, renderedRotorDegrees);
    }

    private static void renderWindRotoTopPart(MultiBufferSource buffer, RenderType type, PoseStack poseStack,
                                              RenderableRootRotorPart rootPart,
                                              WrvbYawGeometry wrvbYawGeometry, float renderedWrvbYawDegrees,
                                              Vector3d rotorPivot, float renderedRotorDegrees) {
        if (type == RenderType.cutout()) {
            BlockPos pos = rootPart.pos();
            poseStack.pushPose();
            applyWrvbYaw(poseStack, wrvbYawGeometry, renderedWrvbYawDegrees);
            applyRotor(poseStack, rotorPivot, renderedRotorDegrees);
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            DirectionalWindRotoTop.render(rootPart.state(), poseStack, buffer);
            poseStack.popPose();
        }
    }

    private static void renderKineticShaftRootPart(MultiBufferSource buffer, RenderType type, PoseStack poseStack,
                                                   RenderableRootRotorPart rootPart,
                                                   WrvbYawGeometry wrvbYawGeometry, float renderedWrvbYawDegrees,
                                                   Vector3d rotorPivot, float renderedRotorDegrees) {
        if (type != RenderType.solid()) {
            return;
        }

        BlockState state = rootPart.state();
        Direction.Axis axis = state.hasProperty(BlockStateProperties.AXIS)
                ? state.getValue(BlockStateProperties.AXIS)
                : Direction.Axis.Z;
        BlockState renderedState = KineticBlockEntityRenderer.shaft(axis);
        SuperByteBuffer shaft = CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK, renderedState);
        BlockPos pos = rootPart.pos();

        poseStack.pushPose();
        applyWrvbYaw(poseStack, wrvbYawGeometry, renderedWrvbYawDegrees);
        applyRotor(poseStack, rotorPivot, renderedRotorDegrees);
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        shaft.light(0xF000F0)
                .renderInto(poseStack, buffer.getBuffer(type));
        poseStack.popPose();
    }

    private static void renderRootBlockModel(PonderLevel world, MultiBufferSource buffer, RenderType type,
                                             BlockRenderDispatcher dispatcher, ModelBlockRenderer renderer,
                                             RandomSource random, PoseStack poseStack, BlockPos pos,
                                             BlockState state, WrvbYawGeometry wrvbYawGeometry, float wrvbYawDegrees,
                                             Vector3d rotorPivot, float rotorDegrees) {
        if (state.getRenderShape() != RenderShape.MODEL) {
            return;
        }

        BakedModel model = dispatcher.getBlockModel(state);
        ModelData modelData = model.getModelData(world, pos, state, ModelData.EMPTY);
        long seed = state.getSeed(pos);
        random.setSeed(seed);
        if (!model.getRenderTypes(state, random, modelData).contains(type)) {
            return;
        }

        poseStack.pushPose();
        applyWrvbYaw(poseStack, wrvbYawGeometry, wrvbYawDegrees);
        applyRotor(poseStack, rotorPivot, rotorDegrees);
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        renderer.tesselateBlock(world, model, state, pos, poseStack, buffer.getBuffer(type), true,
                random, seed, OverlayTexture.NO_OVERLAY, modelData, type);
        poseStack.popPose();
    }

    private static void renderWrvbYawRootParts(PonderLevel world, MultiBufferSource buffer, RenderType type,
                                               BlockRenderDispatcher dispatcher, ModelBlockRenderer renderer,
                                               RandomSource random, PoseStack poseStack,
                                               List<RenderableWrvbYawRootPart> rootParts,
                                               WrvbYawGeometry wrvbYawGeometry,
                                               float renderedWrvbYawDegrees) {
        for (RenderableWrvbYawRootPart rootPart : rootParts) {
            renderRootBlockModel(world, buffer, type, dispatcher, renderer, random, poseStack,
                    rootPart.pos(), rootPart.state(), wrvbYawGeometry, renderedWrvbYawDegrees, null, 0.0F);
        }
    }

    private static final class DirectionalWindRotoTop {
        private DirectionalWindRotoTop() {
        }

        private static void render(BlockState state, PoseStack poseStack, MultiBufferSource buffer) {
            SuperByteBuffer top = CachedBuffers.partial(TwisterMillPartialModels.WIND_ROTO_TOP, state);
            rotateTopToFacing(top, state.hasProperty(BlockStateProperties.FACING)
                    ? state.getValue(BlockStateProperties.FACING)
                    : Direction.NORTH);
            top.light(0xF000F0)
                    .renderInto(poseStack, buffer.getBuffer(RenderType.cutout()));
        }

        private static void rotateTopToFacing(SuperByteBuffer buf, Direction facing) {
            float modelYawFixDeg = 0f;

            switch (facing) {
                case NORTH -> {
                    buf.rotateCentered(0f, Direction.UP);
                    modelYawFixDeg = 0f;
                }
                case SOUTH -> {
                    buf.rotateCentered((float) Math.toRadians(180), Direction.UP);
                    modelYawFixDeg = 0f;
                }
                case EAST -> {
                    buf.rotateCentered((float) Math.toRadians(90), Direction.UP);
                    modelYawFixDeg = 180f;
                }
                case WEST -> {
                    buf.rotateCentered((float) Math.toRadians(-90), Direction.UP);
                    modelYawFixDeg = 180f;
                }
                case UP -> {
                    buf.rotateCentered((float) Math.toRadians(-90), Direction.EAST);
                    buf.rotateCentered((float) Math.toRadians(180), Direction.UP);
                    modelYawFixDeg = 0f;
                }
                case DOWN -> {
                    buf.rotateCentered((float) Math.toRadians(90), Direction.EAST);
                    buf.rotateCentered((float) Math.toRadians(180), Direction.UP);
                    modelYawFixDeg = 0f;
                }
            }

            if (modelYawFixDeg != 0f) {
                buf.rotateCentered((float) Math.toRadians(modelYawFixDeg), Direction.UP);
            }
        }
    }

    private static final class DirectionalServoTop {
        private DirectionalServoTop() {
        }

        private static void render(BlockState state, PoseStack poseStack, MultiBufferSource buffer) {
            SuperByteBuffer top = CachedBuffers.partial(TwisterMillPartialModels.SERVO_TWISTER_TOP, state);
            rotateTopToFacing(top, state.hasProperty(BlockStateProperties.FACING)
                    ? state.getValue(BlockStateProperties.FACING)
                    : net.minecraft.core.Direction.NORTH);
            top.light(0xF000F0)
                    .renderInto(poseStack, buffer.getBuffer(RenderType.cutout()));
        }

        private static void rotateTopToFacing(SuperByteBuffer buf, net.minecraft.core.Direction facing) {
            float modelYawFixDeg = 0f;

            switch (facing) {
                case NORTH -> {
                    buf.rotateCentered(0f, net.minecraft.core.Direction.UP);
                    modelYawFixDeg = 0f;
                }
                case SOUTH -> {
                    buf.rotateCentered((float) Math.toRadians(180), net.minecraft.core.Direction.UP);
                    modelYawFixDeg = 0f;
                }
                case EAST -> {
                    buf.rotateCentered((float) Math.toRadians(90), net.minecraft.core.Direction.UP);
                    modelYawFixDeg = 180f;
                }
                case WEST -> {
                    buf.rotateCentered((float) Math.toRadians(-90), net.minecraft.core.Direction.UP);
                    modelYawFixDeg = 180f;
                }
                case UP -> {
                    buf.rotateCentered((float) Math.toRadians(-90), net.minecraft.core.Direction.EAST);
                    buf.rotateCentered((float) Math.toRadians(180), net.minecraft.core.Direction.UP);
                    modelYawFixDeg = 0f;
                }
                case DOWN -> {
                    buf.rotateCentered((float) Math.toRadians(90), net.minecraft.core.Direction.EAST);
                    buf.rotateCentered((float) Math.toRadians(180), net.minecraft.core.Direction.UP);
                    modelYawFixDeg = 0f;
                }
            }

            if (modelYawFixDeg != 0f) {
                buf.rotateCentered((float) Math.toRadians(modelYawFixDeg), net.minecraft.core.Direction.UP);
            }
        }
    }

    private static final class DirectionalServoAntenna {
        private DirectionalServoAntenna() {
        }

        private static void render(BlockState state, PoseStack poseStack, MultiBufferSource buffer) {
            Direction facing = state.hasProperty(BlockStateProperties.FACING)
                    ? state.getValue(BlockStateProperties.FACING)
                    : Direction.NORTH;
            SuperByteBuffer antenna = CachedBuffers.partial(TwisterMillPartialModels.SERVO_TWISTER_ANTENNA, state);
            rotateHousingFixedPartialToBlockstateFacing(antenna, facing);

            if (facing == Direction.DOWN) {
                poseStack.pushPose();
                poseStack.translate(0.0D, ANTENNA_DOWN_OFFSET, 0.0D);
                renderAntenna(antenna, poseStack, buffer);
                poseStack.popPose();
                return;
            }

            renderAntenna(antenna, poseStack, buffer);
        }

        private static void renderAntenna(SuperByteBuffer antenna, PoseStack poseStack, MultiBufferSource buffer) {
            antenna.light(0xF000F0)
                    .renderInto(poseStack, buffer.getBuffer(RenderType.cutout()));
        }

        private static void rotateHousingFixedPartialToBlockstateFacing(SuperByteBuffer buf, Direction facing) {
            switch (facing) {
                case NORTH -> {
                }
                case SOUTH -> buf.rotateCentered((float) Math.toRadians(180), Direction.UP);
                case EAST -> buf.rotateCentered((float) Math.toRadians(90), Direction.UP);
                case WEST -> buf.rotateCentered((float) Math.toRadians(-90), Direction.UP);
                case UP -> {
                    buf.rotateCentered((float) Math.toRadians(-90), Direction.EAST);
                    buf.rotateCentered((float) Math.toRadians(180), Direction.Axis.Z);
                }
                case DOWN -> buf.rotateCentered((float) Math.toRadians(90), Direction.EAST);
            }
        }
    }

    private record RenderableSubLevel(Vector3d position, Quaterniond orientation, SchematicLevel level,
                                      PitchGeometry pitchGeometry) {
    }

    private enum RootRotorPartRole {
        BLADE_ARM,
        CENTRAL_SERVO,
        WIND_ROTO_TOP,
        KINETIC_SHAFT
    }

    private record RenderableRootRotorPart(BlockPos pos, BlockState state, RootRotorPartRole role) {
    }

    private record RenderableWrvbYawRootPart(BlockPos pos, BlockState state) {
    }

    private record RenderableSchematic(List<RenderableSubLevel> subLevels,
                                       List<RenderableRootRotorPart> rootRotorParts,
                                       List<RenderableWrvbYawRootPart> wrvbYawRootParts,
                                       WrvbYawGeometry wrvbYawGeometry) {
    }

    private record PitchGeometry(Vector3d axisStart, Vector3d axisDirection, Vector3d servoCenter) {
    }

    private record WrvbYawGeometry(BlockPos wrvbPos, Vector3d pivot, Vector3d axisDirection,
                                   List<BlockPos> axisChainPositions) {
    }
}
