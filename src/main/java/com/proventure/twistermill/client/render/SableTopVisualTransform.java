package com.proventure.twistermill.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.proventure.twistermill.util.SableLevelWrapper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.UUID;

final class SableTopVisualTransform {
    static final double RENDER_BOUNDS_INFLATE = 4.0D;
    private static final double RENDER_SPACE_EPSILON = 1.0E-6D;

    private SableTopVisualTransform() {
    }

    static boolean renderTop(
            BlockEntity blockEntity,
            @Nullable UUID subLevelId,
            Direction facing,
            float partialTicks,
            PoseStack poseStack,
            Runnable render
    ) {
        Transform transform = resolve(blockEntity, subLevelId, facing, partialTicks);
        if (transform == null) {
            return false;
        }

        poseStack.pushPose();
        try {
            Vector3d center = transform.renderLocalCenter();
            poseStack.translate(center.x, center.y, center.z);
            poseStack.mulPose(toQuaternionf(transform.renderOrientation()));
            poseStack.translate(-0.5D, -0.5D, -0.5D);
            render.run();
        } finally {
            poseStack.popPose();
        }
        return true;
    }

    static AABB renderBounds(BlockPos pos, @Nullable UUID subLevelId) {
        AABB bounds = new AABB(pos);
        return subLevelId == null ? bounds : bounds.inflate(RENDER_BOUNDS_INFLATE);
    }

    @Nullable
    private static Transform resolve(
            BlockEntity blockEntity,
            @Nullable UUID subLevelId,
            Direction facing,
            float partialTicks
    ) {
        if (subLevelId == null) {
            return null;
        }

        Level blockEntityLevel = blockEntity.getLevel();
        ClientLevel rootLevel = Minecraft.getInstance().level;
        if (blockEntityLevel == null || rootLevel == null) {
            return null;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(rootLevel);
        if (container == null) {
            return null;
        }

        SubLevel attachedSubLevel = resolveSubLevel(container, subLevelId);
        if (attachedSubLevel == null) {
            return null;
        }

        Pose3dc attachedPose = resolveRenderPose(attachedSubLevel, partialTicks);
        Vector3d attachedLocalCenter = computeVirtualBearingLocalCenter(attachedSubLevel, facing);
        Vector3d rootWorldCenter = attachedPose.transformPosition(attachedLocalCenter, new Vector3d());
        if (!isFinite(rootWorldCenter)) {
            return null;
        }

        SubLevel containingSubLevel = resolveContainingSubLevelForRender(rootLevel, blockEntityLevel, blockEntity);
        boolean requiresSubLevelPose = requiresSubLevelRenderSpace(rootLevel, blockEntityLevel, blockEntity);
        Vector3d renderSpaceCenter = toBlockEntityRenderSpace(
                blockEntity,
                rootWorldCenter,
                containingSubLevel,
                requiresSubLevelPose,
                partialTicks
        );
        if (!isFinite(renderSpaceCenter)) {
            return null;
        }

        Quaterniond renderOrientation = new Quaterniond(attachedPose.orientation());
        if (containingSubLevel != null) {
            Pose3dc containingPose = resolveRenderPose(containingSubLevel, partialTicks);
            renderOrientation = new Quaterniond(containingPose.orientation())
                    .invert()
                    .mul(renderOrientation);
        }
        renderOrientation.normalize();

        return new Transform(renderSpaceCenter, renderOrientation);
    }

    private static Vector3d computeVirtualBearingLocalCenter(SubLevel attachedSubLevel, Direction facing) {
        BlockPos plotAnchor = attachedSubLevel.getPlot().getCenterBlock();
        return new Vector3d(
                plotAnchor.getX() - facing.getStepX() + 0.5D,
                plotAnchor.getY() - facing.getStepY() + 0.5D,
                plotAnchor.getZ() - facing.getStepZ() + 0.5D
        );
    }

    @Nullable
    private static SubLevel resolveSubLevel(SubLevelContainer container, UUID subLevelId) {
        try {
            SubLevel subLevel = container.getSubLevel(subLevelId);
            return subLevel == null || subLevel.isRemoved() ? null : subLevel;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Pose3dc resolveRenderPose(SubLevel subLevel, float partialTicks) {
        if (subLevel instanceof ClientSubLevelAccess clientSubLevelAccess) {
            return clientSubLevelAccess.renderPose(partialTicks);
        }
        return subLevel.lastPose().lerp(subLevel.logicalPose(), partialTicks, new Pose3d());
    }

    @Nullable
    private static Vector3d toBlockEntityRenderSpace(
            BlockEntity blockEntity,
            Vector3d rootWorldPoint,
            @Nullable SubLevel containingSubLevel,
            boolean requiresSubLevelPose,
            float partialTicks
    ) {
        Vector3d renderSpacePoint;
        if (containingSubLevel != null) {
            renderSpacePoint = resolveRenderPose(containingSubLevel, partialTicks)
                    .transformPositionInverse(rootWorldPoint, new Vector3d());
        } else {
            if (requiresSubLevelPose) {
                return null;
            }
            renderSpacePoint = new Vector3d(rootWorldPoint);
        }

        if (!isFinite(renderSpacePoint)) {
            return null;
        }

        BlockPos pos = blockEntity.getBlockPos();
        return renderSpacePoint.sub(pos.getX(), pos.getY(), pos.getZ());
    }

    @Nullable
    private static SubLevel resolveContainingSubLevelForRender(
            ClientLevel rootLevel,
            Level blockEntityLevel,
            BlockEntity blockEntity
    ) {
        SubLevel directContaining = resolveDirectContainingSubLevel(blockEntity);
        if (directContaining != null) {
            return directContaining;
        }

        Vector3d worldCenter = computeWorldCenter(blockEntityLevel, blockEntity.getBlockPos());
        if (!isFinite(worldCenter)) {
            return null;
        }

        try {
            SubLevel containing = Sable.HELPER.getContaining(rootLevel, worldCenter);
            return containing == null || containing.isRemoved() ? null : containing;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean requiresSubLevelRenderSpace(
            ClientLevel rootLevel,
            Level blockEntityLevel,
            BlockEntity blockEntity
    ) {
        if (resolveDirectContainingSubLevel(blockEntity) != null) {
            return true;
        }

        Vector3d worldCenter = computeWorldCenter(blockEntityLevel, blockEntity.getBlockPos());
        if (!isFinite(worldCenter)) {
            return false;
        }

        try {
            if (Sable.HELPER.getContaining(rootLevel, worldCenter) instanceof SubLevel) {
                return true;
            }
        } catch (RuntimeException ignored) {
        }

        BlockPos pos = blockEntity.getBlockPos();
        Vector3d rootBlockCenter = new Vector3d(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D
        );
        return worldCenter.distanceSquared(rootBlockCenter) > RENDER_SPACE_EPSILON;
    }

    @Nullable
    private static SubLevel resolveDirectContainingSubLevel(BlockEntity blockEntity) {
        try {
            SubLevel containing = Sable.HELPER.getContainingClient(blockEntity);
            return containing == null || containing.isRemoved() ? null : containing;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static Vector3d computeWorldCenter(Level level, BlockPos pos) {
        try {
            return SableLevelWrapper.toWorldCenter(level, pos);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isFinite(@Nullable Vector3d vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    private static Quaternionf toQuaternionf(Quaterniond orientation) {
        return new Quaternionf(
                (float) orientation.x(),
                (float) orientation.y(),
                (float) orientation.z(),
                (float) orientation.w()
        ).normalize();
    }

    private record Transform(Vector3d renderLocalCenter, Quaterniond renderOrientation) {
    }
}
