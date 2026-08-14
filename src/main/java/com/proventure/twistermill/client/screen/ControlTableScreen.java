package com.proventure.twistermill.client.screen;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.binaryredstone.message.ControlTableActionMessage;
import com.proventure.twistermill.binaryredstone.message.ControlTableConfigMessage;
import com.proventure.twistermill.blockentity.ControlTableBlockEntity;
import com.proventure.twistermill.menu.ControlTableMenu;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

public class ControlTableScreen extends AbstractSimiContainerScreen<ControlTableMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "textures/gui/control_table.png");

    private static final int IMAGE_WIDTH = 196;
    private static final int IMAGE_HEIGHT = 106;

    private static final int SAVE_X1 = 169;
    private static final int SAVE_Y1 = 83;
    private static final int SAVE_X2 = 187;
    private static final int SAVE_Y2 = 101;

    private static final int CANCEL_X1 = 9;
    private static final int CANCEL_Y1 = 83;
    private static final int CANCEL_X2 = 27;
    private static final int CANCEL_Y2 = 101;

    private static final int INFO_X1 = 37;
    private static final int INFO_Y1 = 83;
    private static final int INFO_X2 = 55;
    private static final int INFO_Y2 = 101;

    private static final int LAUNCH_BUTTON_X1 = 9;
    private static final int LAUNCH_BUTTON_Y1 = 25;
    private static final int LAUNCH_BUTTON_X2 = 121;
    private static final int LAUNCH_BUTTON_Y2 = 43;

    private static final int DISASSEMBLE_BUTTON_X1 = 9;
    private static final int DISASSEMBLE_BUTTON_Y1 = 54;
    private static final int DISASSEMBLE_BUTTON_X2 = 121;
    private static final int DISASSEMBLE_BUTTON_Y2 = 72;

    private static final int LAUNCH_TEXT_X1 = 133;
    private static final int LAUNCH_TEXT_Y1 = 27;
    private static final int LAUNCH_TEXT_X2 = 184;
    private static final int LAUNCH_TEXT_Y2 = 40;
    private static final int LAUNCH_STATUS_ROWS_START_Y = LAUNCH_TEXT_Y2 + 3;
    private static final int LAUNCH_STATUS_ROW_STEP = 10;
    private static final int LAUNCH_STATUS_LABEL_COLOR = 0x3366FF;
    private static final int LAUNCH_STATUS_VALUE_COLOR = 0xFF5555;
    private static final int LAUNCH_MODE_ON_COLOR = 0x55FF55;
    private static final int LAUNCH_MODE_STATE_COLOR = 0xFFAA00;
    private static final int LAUNCH_MODE_OFF_COLOR = 0xFF5555;

    private static final int BIT_START_X = 5;
    private static final int BIT_START_Y = 5;
    private static final int BIT_STEP_X = 16;
    private static final int BIT_CELL_SIZE = 13;
    private static final int BINARY_BITS = 12;

    private static final int INFO_OVERLAY_X1 = -50;
    private static final int INFO_OVERLAY_Y1 = 115;
    private static final int INFO_OVERLAY_X2 = 245;
    private static final int INFO_OVERLAY_Y2 = 200;
    private static final int INFO_OVERLAY_TEXT_HORIZONTAL_PADDING = 4;
    private static final int INFO_OVERLAY_TEXT_VERTICAL_PADDING = 4;
    private static final int INFO_OVERLAY_TEXT_LINE_SPACING = 1;
    private static final int INFO_OVERLAY_TEXT_SECTION_GAP_LINES = 1;
    private static final int INFO_OVERLAY_TEXT_TOP_OFFSET = 16;
    private static final int INFO_OVERLAY_EXTRA_TEXT_LEFT_PADDING = 4;
    private static final int INFO_OVERLAY_TEXT_UP_SHIFT_LINES = 1;
    private static final int INFO_OVERLAY_SECTION_GAP_AFTER_INDEX = 6;
    private static final int INFO_OVERLAY_EXTRA_LINES_START_INDEX = 7;

    private static final int PRESSED_TICKS = 5;
    private static final int PRESSED_OVERLAY = 0x55000000;
    private static final int PRESSED_EDGE = 0xAA000000;

    private boolean sentToServer;
    private int launchMode;
    private boolean showInfoOverlay;
    private PressedButton pressedButton = PressedButton.NONE;
    private int pressedButtonTicks;
    private PendingAction pendingAction = PendingAction.NONE;

    private enum PressedButton {
        NONE,
        CANCEL,
        INFO,
        LAUNCH,
        DISASSEMBLE,
        SAVE
    }

    private enum PendingAction {
        NONE,
        CLOSE,
        SAVE_AND_CLOSE
    }

    public ControlTableScreen(ControlTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        setWindowSize(IMAGE_WIDTH, IMAGE_HEIGHT);
        setWindowOffset(0, 0);
        super.init();
        launchMode = normalizeLaunchMode(menu.getLaunchMode());
        showInfoOverlay = false;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        renderPressedButton(guiGraphics);
        renderButtonLabels(guiGraphics);
        renderLaunchStatus(guiGraphics);
        renderBinaryBits(guiGraphics);
        renderInfoOverlay(guiGraphics);

        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        Vec2 local = toLocal(mouseX, mouseY);
        int x = (int) local.x;
        int y = (int) local.y;

        if (isWithinRect(x, y, CANCEL_X1, CANCEL_Y1, CANCEL_X2, CANCEL_Y2)) {
            setPressed(PressedButton.CANCEL, PendingAction.CLOSE);
            return true;
        }

        if (isWithinRect(x, y, INFO_X1, INFO_Y1, INFO_X2, INFO_Y2)) {
            setPressed(PressedButton.INFO, PendingAction.NONE);
            showInfoOverlay = !showInfoOverlay;
            return true;
        }

        if (isWithinRect(x, y, LAUNCH_BUTTON_X1, LAUNCH_BUTTON_Y1, LAUNCH_BUTTON_X2, LAUNCH_BUTTON_Y2)) {
            setPressed(PressedButton.LAUNCH, PendingAction.NONE);
            launchMode = (launchMode + 1) % 3;
            return true;
        }

        if (isWithinRect(x, y, DISASSEMBLE_BUTTON_X1, DISASSEMBLE_BUTTON_Y1, DISASSEMBLE_BUTTON_X2, DISASSEMBLE_BUTTON_Y2)) {
            setPressed(PressedButton.DISASSEMBLE, PendingAction.NONE);
            sendDisassembleActionPacket();
            return true;
        }

        if (isWithinRect(x, y, SAVE_X1, SAVE_Y1, SAVE_X2, SAVE_Y2)) {
            setPressed(PressedButton.SAVE, PendingAction.SAVE_AND_CLOSE);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (pressedButtonTicks <= 0) {
            return;
        }
        pressedButtonTicks--;
        if (pressedButtonTicks == 0) {
            pressedButton = PressedButton.NONE;
            executePendingAction();
        }
    }

    private void executePendingAction() {
        if (pendingAction == PendingAction.CLOSE) {
            pendingAction = PendingAction.NONE;
            super.onClose();
            return;
        }
        if (pendingAction == PendingAction.SAVE_AND_CLOSE) {
            pendingAction = PendingAction.NONE;
            sendConfigPacket();
            super.onClose();
        }
    }

    private void setPressed(PressedButton button, PendingAction action) {
        pressedButton = button;
        pressedButtonTicks = PRESSED_TICKS;
        pendingAction = action;
    }

    private void renderPressedButton(GuiGraphics guiGraphics) {
        if (pressedButton == PressedButton.NONE || pressedButtonTicks <= 0) {
            return;
        }
        ButtonRect rect = getPressedRect(pressedButton);
        if (rect == null) {
            return;
        }

        int drawX = leftPos + rect.x1;
        int drawY = topPos + rect.y1;
        int width = rect.width();
        int height = rect.height();

        guiGraphics.fill(drawX, drawY, drawX + width, drawY + height, PRESSED_OVERLAY);
        guiGraphics.blit(
                BACKGROUND,
                drawX + 1,
                drawY + 1,
                rect.x1 + 1,
                rect.y1 + 1,
                width - 1,
                height - 1,
                IMAGE_WIDTH,
                IMAGE_HEIGHT
        );
        guiGraphics.fill(drawX, drawY, drawX + width, drawY + 1, PRESSED_EDGE);
        guiGraphics.fill(drawX, drawY, drawX + 1, drawY + height, PRESSED_EDGE);
    }

    private void renderButtonLabels(GuiGraphics guiGraphics) {
        drawScaledCenteredString(
                guiGraphics,
                "Code Launch Mode",
                LAUNCH_BUTTON_X1,
                LAUNCH_BUTTON_Y1,
                LAUNCH_BUTTON_X2,
                LAUNCH_BUTTON_Y2
        );

        drawScaledCenteredString(
                guiGraphics,
                "Dis-/Assemble Ship",
                DISASSEMBLE_BUTTON_X1,
                DISASSEMBLE_BUTTON_Y1,
                DISASSEMBLE_BUTTON_X2,
                DISASSEMBLE_BUTTON_Y2
        );
    }

    private ButtonRect getPressedRect(PressedButton button) {
        return switch (button) {
            case CANCEL -> new ButtonRect(CANCEL_X1, CANCEL_Y1, CANCEL_X2, CANCEL_Y2);
            case INFO -> new ButtonRect(INFO_X1, INFO_Y1, INFO_X2, INFO_Y2);
            case LAUNCH -> new ButtonRect(LAUNCH_BUTTON_X1, LAUNCH_BUTTON_Y1, LAUNCH_BUTTON_X2, LAUNCH_BUTTON_Y2);
            case DISASSEMBLE -> new ButtonRect(DISASSEMBLE_BUTTON_X1, DISASSEMBLE_BUTTON_Y1, DISASSEMBLE_BUTTON_X2, DISASSEMBLE_BUTTON_Y2);
            case SAVE -> new ButtonRect(SAVE_X1, SAVE_Y1, SAVE_X2, SAVE_Y2);
            case NONE -> null;
        };
    }

    private record ButtonRect(int x1, int y1, int x2, int y2) {
        private int width() {
            return x2 - x1;
        }

        private int height() {
            return y2 - y1;
        }
    }

    private boolean isWithinRect(int x, int y, int startXInclusive, int startYInclusive, int endXExclusive, int endYExclusive) {
        return x >= startXInclusive && x < endXExclusive && y >= startYInclusive && y < endYExclusive;
    }

    private Vec2 toLocal(double mouseX, double mouseY) {
        return new Vec2((float) (mouseX - leftPos), (float) (mouseY - topPos));
    }

    private int normalizeLaunchMode(int value) {
        return switch (value) {
            case ControlTableBlockEntity.LAUNCH_MODE_RS_PULSE,
                    ControlTableBlockEntity.LAUNCH_MODE_ON_CHANGE,
                    ControlTableBlockEntity.LAUNCH_MODE_OFF -> value;
            default -> ControlTableBlockEntity.DEFAULT_LAUNCH_MODE;
        };
    }

    private void sendConfigPacket() {
        if (sentToServer) {
            return;
        }
        sentToServer = true;
        int sendControl = normalizeLaunchMode(launchMode);
        PacketDistributor.sendToServer(new ControlTableConfigMessage(
                menu.getBlockPos(),
                menu.getSequenceLengthTicks(),
                menu.getPulseLengthTicks(),
                menu.getPauseLengthTicks(),
                menu.getRepeatIntervalTicks(),
                sendControl
        ));
    }

    private void sendDisassembleActionPacket() {
        PacketDistributor.sendToServer(new ControlTableActionMessage(
                menu.getBlockPos(),
                ControlTableActionMessage.ACTION_TOGGLE_SERVO_ASSEMBLY
        ));
    }

    private void renderLaunchStatus(GuiGraphics guiGraphics) {
        String speedValue = formatSignalValue(menu.getRibobSignal(1));
        String angleValue = formatSignalValue(menu.getRibobSignal(2));
        String modeValue = formatSignalValue(menu.getRibobSignal(0));

        int drawX1 = leftPos + LAUNCH_TEXT_X1;
        int drawY1 = topPos + LAUNCH_TEXT_Y1;
        int drawX2 = leftPos + LAUNCH_TEXT_X2;
        int drawY2 = topPos + LAUNCH_TEXT_Y2;
        guiGraphics.fill(drawX1, drawY1, drawX2, drawY2, 0x3A000000);

        switch (launchMode) {
            case ControlTableBlockEntity.LAUNCH_MODE_RS_PULSE -> drawScaledCenteredColoredSegments(
                    guiGraphics,
                    new String[]{"ON ", "Pulse"},
                    new int[]{LAUNCH_MODE_ON_COLOR, LAUNCH_MODE_STATE_COLOR}
            );
            case ControlTableBlockEntity.LAUNCH_MODE_ON_CHANGE -> drawScaledCenteredColoredSegments(
                    guiGraphics,
                    new String[]{"ON ", "Change"},
                    new int[]{LAUNCH_MODE_ON_COLOR, LAUNCH_MODE_STATE_COLOR}
            );
            default -> drawScaledCenteredColoredSegments(
                    guiGraphics,
                    new String[]{"OFF"},
                    new int[]{LAUNCH_MODE_OFF_COLOR}
            );
        }

        renderLaunchStatusRow(guiGraphics, "Speed:", speedValue, LAUNCH_STATUS_ROWS_START_Y);
        renderLaunchStatusRow(guiGraphics, "Angle:", angleValue, LAUNCH_STATUS_ROWS_START_Y + LAUNCH_STATUS_ROW_STEP);
        renderLaunchStatusRow(guiGraphics, "Mode:", modeValue, LAUNCH_STATUS_ROWS_START_Y + (2 * LAUNCH_STATUS_ROW_STEP));
    }

    private void renderLaunchStatusRow(GuiGraphics guiGraphics, String label, String value, int localY) {
        int labelX = leftPos + LAUNCH_TEXT_X1;
        int valueX = leftPos + LAUNCH_TEXT_X2 - font.width(value) - 2;
        int drawY = topPos + localY;

        guiGraphics.drawString(font, label, labelX, drawY, LAUNCH_STATUS_LABEL_COLOR, false);
        guiGraphics.drawString(font, value, valueX, drawY, LAUNCH_STATUS_VALUE_COLOR, false);
    }

    private String formatSignalValue(int signal) {
        if (signal < 0 || signal > 15) {
            return "-";
        }
        return Integer.toString(signal);
    }

    private void drawScaledCenteredString(GuiGraphics guiGraphics, String text, int areaX1, int areaY1, int areaX2, int areaY2) {
        int areaW = areaX2 - areaX1;
        int areaH = areaY2 - areaY1;
        int rawTextW = font.width(text);
        float scale = rawTextW > areaW ? (float) areaW / (float) rawTextW : 1.0F;

        int scaledTextW = Math.max(1, Math.round(rawTextW * scale));
        int scaledTextH = Math.max(1, Math.round(font.lineHeight * scale));
        int drawX = leftPos + areaX1 + (areaW - scaledTextW) / 2;
        int drawY = topPos + areaY1 + (areaH - scaledTextH) / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(drawX, drawY, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(font, text, 0, 0, 0xF2F2F2, true);
        guiGraphics.pose().popPose();
    }

    private void drawScaledCenteredColoredSegments(
            GuiGraphics guiGraphics,
            String[] segments,
            int[] colors
    ) {
        if (segments.length == 0 || segments.length != colors.length) {
            return;
        }

        int areaW = LAUNCH_TEXT_X2 - LAUNCH_TEXT_X1;
        int areaH = LAUNCH_TEXT_Y2 - LAUNCH_TEXT_Y1;

        int rawTextW = 0;
        for (String segment : segments) {
            rawTextW += font.width(segment);
        }

        float scale = rawTextW > areaW ? (float) areaW / (float) rawTextW : 1.0F;
        int scaledTextW = Math.max(1, Math.round(rawTextW * scale));
        int scaledTextH = Math.max(1, Math.round(font.lineHeight * scale));
        int drawX = leftPos + LAUNCH_TEXT_X1 + (areaW - scaledTextW) / 2;
        int drawY = topPos + LAUNCH_TEXT_Y1 + (areaH - scaledTextH) / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(drawX, drawY, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);

        int xOffset = 0;
        for (int i = 0; i < segments.length; i++) {
            guiGraphics.drawString(font, segments[i], xOffset, 0, colors[i], true);
            xOffset += font.width(segments[i]);
        }

        guiGraphics.pose().popPose();
    }

    private void renderBinaryBits(GuiGraphics guiGraphics) {
        String binary = normalizedBinaryCode(menu.getCurrentCode());
        for (int bitIndex = 0; bitIndex < BINARY_BITS; bitIndex++) {
            String bit = String.valueOf(binary.charAt(bitIndex));
            int cellX = BIT_START_X + (bitIndex * BIT_STEP_X);
            int textX = leftPos + cellX + (BIT_CELL_SIZE - font.width(bit)) / 2;
            int textY = topPos + BIT_START_Y + (BIT_CELL_SIZE - font.lineHeight) / 2;
            guiGraphics.drawString(font, bit, textX, textY, 0x202020, false);
        }
    }

    private String normalizedBinaryCode(String rawCode) {
        if (rawCode == null || rawCode.length() != BINARY_BITS) {
            return "000000000000";
        }

        StringBuilder normalized = new StringBuilder(BINARY_BITS);
        for (int i = 0; i < BINARY_BITS; i++) {
            char c = rawCode.charAt(i);
            normalized.append(c == '1' ? '1' : '0');
        }
        return normalized.toString();
    }

    private void renderInfoOverlay(GuiGraphics guiGraphics) {
        if (!showInfoOverlay) {
            return;
        }

        int x1 = leftPos + INFO_OVERLAY_X1;
        int y1 = topPos + INFO_OVERLAY_Y1;
        int x2 = leftPos + INFO_OVERLAY_X2;
        int y2 = topPos + INFO_OVERLAY_Y2;

        guiGraphics.fill(x1, y1, x2, y2, 0xFF000000);
        guiGraphics.fill(x1, y1, x2, y1 + 1, 0xFFFFFFFF);
        guiGraphics.fill(x1, y2 - 1, x2, y2, 0xFFFFFFFF);
        guiGraphics.fill(x1, y1, x1 + 1, y2, 0xFFFFFFFF);
        guiGraphics.fill(x2 - 1, y1, x2, y2, 0xFFFFFFFF);

        renderCenteredInfoOverlayText(guiGraphics, x1, y1, x2, y2);
    }

    private void renderCenteredInfoOverlayText(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2) {
        Component[] lines = new Component[]{
                Component.translatable("twistermill.gui.control_table.info.line_1"),
                Component.translatable("twistermill.gui.control_table.info.line_blank_1"),
                Component.translatable("twistermill.gui.control_table.info.line_2"),
                Component.translatable("twistermill.gui.control_table.info.line_3"),
                Component.translatable("twistermill.gui.control_table.info.line_4"),
                Component.translatable("twistermill.gui.control_table.info.line_5"),
                Component.translatable("twistermill.gui.control_table.info.line_6"),
                Component.translatable("twistermill.gui.control_table.info.line_7"),
                Component.translatable("twistermill.gui.control_table.info.line_8"),
                Component.translatable("twistermill.gui.control_table.info.line_9")
        };

        int maxRawWidth = 0;
        for (Component line : lines) {
            maxRawWidth = Math.max(maxRawWidth, font.width(line.getVisualOrderText()));
        }

        int lineCount = lines.length;
        int rawLineHeight = font.lineHeight;
        int sectionGapRawHeight = INFO_OVERLAY_TEXT_SECTION_GAP_LINES * (rawLineHeight + INFO_OVERLAY_TEXT_LINE_SPACING);
        int rawBlockHeight = lineCount * rawLineHeight + (lineCount - 1) * INFO_OVERLAY_TEXT_LINE_SPACING + sectionGapRawHeight;

        int availableWidth = Math.max(1, (x2 - x1) - (INFO_OVERLAY_TEXT_HORIZONTAL_PADDING * 2));
        int availableHeight = Math.max(1, (y2 - y1) - (INFO_OVERLAY_TEXT_VERTICAL_PADDING * 2));

        float scaleX = maxRawWidth > 0 ? (float) availableWidth / (float) maxRawWidth : 1.0F;
        float scaleY = (float) availableHeight / (float) rawBlockHeight;
        float scale = Math.min(1.0F, Math.min(scaleX, scaleY));

        float scaledBlockHeight = rawBlockHeight * scale;
        float upShiftRaw = INFO_OVERLAY_TEXT_UP_SHIFT_LINES * (rawLineHeight + INFO_OVERLAY_TEXT_LINE_SPACING);
        float preferredStartY = y1 + INFO_OVERLAY_TEXT_VERTICAL_PADDING + INFO_OVERLAY_TEXT_TOP_OFFSET - (upShiftRaw * scale);
        float maxStartY = y2 - INFO_OVERLAY_TEXT_VERTICAL_PADDING - scaledBlockHeight;
        float minStartY = y1 + INFO_OVERLAY_TEXT_VERTICAL_PADDING;
        float startY = Math.max(minStartY, Math.min(preferredStartY, maxStartY));

        for (int i = 0; i < lineCount; i++) {
            Component line = lines[i];
            int lineRawWidth = font.width(line.getVisualOrderText());
            float scaledLineWidth = lineRawWidth * scale;
            float lineX = i < INFO_OVERLAY_EXTRA_LINES_START_INDEX
                    ? x1 + ((x2 - x1) - scaledLineWidth) / 2.0F
                    : x1 + INFO_OVERLAY_EXTRA_TEXT_LEFT_PADDING;
            int gapLineUnitsBefore = i > INFO_OVERLAY_SECTION_GAP_AFTER_INDEX ? INFO_OVERLAY_TEXT_SECTION_GAP_LINES : 0;
            float lineY = startY + (i * (rawLineHeight + INFO_OVERLAY_TEXT_LINE_SPACING)
                    + gapLineUnitsBefore * (rawLineHeight + INFO_OVERLAY_TEXT_LINE_SPACING)) * scale;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(lineX, lineY, 0.0F);
            guiGraphics.pose().scale(scale, scale, 1.0F);
            guiGraphics.drawString(font, line, 0, 0, 0xEDEDED, false);
            guiGraphics.pose().popPose();
        }
    }
}
