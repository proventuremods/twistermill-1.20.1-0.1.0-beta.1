package com.proventure.twistermill.client;

import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.client.model.ConnectedMetalTraverseModel;
import com.proventure.twistermill.config.TwisterMillConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

import java.util.ArrayList;
import java.util.List;

public final class MetalTraverseDebugOverlay {
    private static final int MAX_DEBUG_LINE_LENGTH = 100;

    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || !(minecraft.hitResult instanceof BlockHitResult blockHitResult)
                || blockHitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() != ModBlocks.METAL_TRAVERSE.get()) {
            return;
        }

        if (!TwisterMillConfig.isMetalTraverseDebugOverlayShown()) {
            keepOnlyTargetedBlockId(event.getRight(), "twistermill:metal_traverse");
            return;
        }

        List<String> models = ConnectedMetalTraverseModel.describeVisibleJsonModels(level, pos, state);
        if (!models.isEmpty()) {
            insertModelLines(event.getRight(), "twistermill:metal_traverse", formatModelLines(models));
        }
    }

    private static List<String> formatModelLines(List<String> models) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder("json: ");
        for (String model : models) {
            if (line.length() > "json: ".length()
                    && line.length() + model.length() + 2 > MAX_DEBUG_LINE_LENGTH) {
                lines.add(line.toString());
                line = new StringBuilder("json+: ");
            }
            if (line.length() > "json+: ".length()) {
                line.append(", ");
            }
            line.append(model);
        }
        lines.add(line.toString());
        return lines;
    }

    private static void keepOnlyTargetedBlockId(List<String> rightDebugLines, String blockId) {
        int targetedBlockLine = -1;
        for (int i = 0; i < rightDebugLines.size(); i++) {
            String line = stripFormatting(rightDebugLines.get(i));
            if (line.startsWith("Targeted Block:")) {
                targetedBlockLine = i;
                continue;
            }
            if (targetedBlockLine >= 0 && line.equals(blockId)) {
                removeTargetedBlockDetails(rightDebugLines, i + 1);
                return;
            }
        }
    }

    private static void removeTargetedBlockDetails(List<String> rightDebugLines, int startIndex) {
        int endIndex = startIndex;
        while (endIndex < rightDebugLines.size()) {
            String line = stripFormatting(rightDebugLines.get(endIndex));
            if (line.isEmpty() || line.startsWith("Targeted ")) {
                break;
            }
            endIndex++;
        }
        if (endIndex > startIndex) {
            rightDebugLines.subList(startIndex, endIndex).clear();
        }
    }

    private static void insertModelLines(List<String> rightDebugLines, String blockId, List<String> modelLines) {
        int targetedBlockLine = -1;
        for (int i = 0; i < rightDebugLines.size(); i++) {
            String line = stripFormatting(rightDebugLines.get(i));
            if (line.startsWith("Targeted Block:")) {
                targetedBlockLine = i;
                continue;
            }
            if (targetedBlockLine >= 0 && line.equals(blockId)) {
                rightDebugLines.addAll(i + 1, modelLines);
                return;
            }
        }

        for (int i = 0; i < rightDebugLines.size(); i++) {
            if (stripFormatting(rightDebugLines.get(i)).equals(blockId)) {
                rightDebugLines.addAll(i + 1, modelLines);
                return;
            }
        }

        rightDebugLines.addAll(Math.min(1, rightDebugLines.size()), modelLines);
    }

    private static String stripFormatting(String line) {
        return line.replaceAll("\u00a7.", "");
    }

    private MetalTraverseDebugOverlay() {
    }
}
