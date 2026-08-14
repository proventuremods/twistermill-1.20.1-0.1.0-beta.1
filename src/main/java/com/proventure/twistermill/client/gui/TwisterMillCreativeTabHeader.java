package com.proventure.twistermill.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public final class TwisterMillCreativeTabHeader {
    public static final int WIDTH = 162;
    public static final int HEIGHT = 18;
    private static final int HEADER_SLOT_COUNT = 9;
    private static final int HEADER_LEFT_OFFSET = 8;
    private static final int HEADER_TOP_OFFSET = 17;
    private static final int FRAME_COUNT = 24;
    private static final long DAY_LENGTH_TICKS = 24_000L;
    private static final long DAY_TICKS_PER_FRAME = 1_000L;
    private static final long FAST_FRAME_TICKS = 7L;
    private static final String MOD_ID = "twistermill";
    private static final String TITLE = "Twistermill";
    private static final int BANNER_BORDER_DARK = 0xFF373737;
    private static final int BANNER_BORDER_LIGHT = 0xFF8B8B8B;
    private static final int TITLE_BACKGROUND = 0xFF896DC2;
    private static final int TITLE_SHADOW_FIRST = 0xFF6B48B0;
    private static final int TITLE_SHADOW_SECOND = 0xFF5C32AF;
    private static final int TITLE_TEXT = 0xFFFCFCFC;
    private static final ResourceLocation TAB_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "create_twistermill_tab");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    MOD_ID, "textures/gui/sprites/creative_tab/twistermill_animated_tab.png");

    private static int currentRow;
    private static BannerAnimationMode animationMode = BannerAnimationMode.FOLLOW_DAY_TIME;
    private static ClientLevel animationLevel;
    private static boolean animationInitialized;
    private static int displayedFrame;
    private static long nextFastFrameTick = Long.MIN_VALUE;

    private TwisterMillCreativeTabHeader() {
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isTwisterMillTab(CreativeModeTab tab) {
        if (tab == null) {
            return false;
        }
        return BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(tab)
                .map(key -> key.location().equals(TAB_ID))
                .orElse(false);
    }

    public static Collection<ItemStack> withHeaderSlots(CreativeModeTab tab, Collection<ItemStack> displayItems) {
        if (!isTwisterMillTab(tab) || hasHeaderSlots(displayItems)) {
            return displayItems;
        }

        List<ItemStack> result = new ArrayList<>(displayItems.size() + HEADER_SLOT_COUNT);
        for (int i = 0; i < HEADER_SLOT_COUNT; i++) {
            result.add(ItemStack.EMPTY);
        }
        result.addAll(displayItems);
        return result;
    }

    public static void resetCurrentRow() {
        currentRow = 0;
        resetBannerAnimation();
    }

    public static void setCurrentRow(int row) {
        int normalizedRow = Math.max(0, row);
        if (normalizedRow != currentRow) {
            resetBannerAnimation();
        }
        currentRow = normalizedRow;
    }

    public static void render(CreativeModeInventoryScreen screen, CreativeModeTab selectedTab, GuiGraphics graphics,
                              int mouseX, int mouseY) {
        if (!isTwisterMillTab(selectedTab) || currentRow != 0) {
            return;
        }

        int headerLeft = screen.getGuiLeft() + HEADER_LEFT_OFFSET;
        int headerTop = screen.getGuiTop() + HEADER_TOP_OFFSET;
        renderBanner(graphics, headerLeft, headerTop, mouseX, mouseY);
        renderBannerBorder(graphics, headerLeft, headerTop);
        renderTitle(graphics, headerLeft, headerTop);
    }

    private static void renderBanner(GuiGraphics graphics, int headerLeft, int headerTop, int mouseX, int mouseY) {
        ClientLevel level = Minecraft.getInstance().level;
        boolean hovered = isBannerHovered(headerLeft, headerTop, mouseX, mouseY);
        int frame = updateBannerAnimation(level, hovered);
        graphics.blit(TEXTURE, headerLeft, headerTop, WIDTH, HEIGHT,
                0.0F, (float) (frame * HEIGHT), WIDTH, HEIGHT, WIDTH, FRAME_COUNT * HEIGHT);
    }

    private static boolean isBannerHovered(int headerLeft, int headerTop, int mouseX, int mouseY) {
        return mouseX >= headerLeft && mouseX < headerLeft + WIDTH
                && mouseY >= headerTop && mouseY < headerTop + HEIGHT;
    }

    private static int updateBannerAnimation(ClientLevel level, boolean hovered) {
        if (level == null) {
            resetBannerAnimation();
            return 0;
        }

        long currentGameTick = level.getGameTime();
        int targetFrame = getDayTimeFrame(level);
        if (!animationInitialized || animationLevel != level) {
            initializeBannerAnimation(level, targetFrame);
            return displayedFrame;
        }

        if (animationMode != BannerAnimationMode.FOLLOW_DAY_TIME
                && nextFastFrameTick != Long.MIN_VALUE
                && currentGameTick < nextFastFrameTick - FAST_FRAME_TICKS) {
            initializeBannerAnimation(level, targetFrame);
            return displayedFrame;
        }

        switch (animationMode) {
            case FOLLOW_DAY_TIME -> updateFollowDayTime(hovered, currentGameTick, targetFrame);
            case HOVER_FAST_FORWARD -> {
                if (!hovered) {
                    if (displayedFrame == targetFrame) {
                        stopFastAnimation();
                        return displayedFrame;
                    }
                    transitionFastMode(BannerAnimationMode.CATCH_UP_FORWARD);
                }
                processFastSteps(currentGameTick, targetFrame);
            }
            case CATCH_UP_FORWARD -> {
                if (hovered) {
                    transitionFastMode(BannerAnimationMode.HOVER_FAST_FORWARD);
                } else if (displayedFrame == targetFrame) {
                    stopFastAnimation();
                    return displayedFrame;
                }
                processFastSteps(currentGameTick, targetFrame);
            }
        }
        return displayedFrame;
    }

    private static void initializeBannerAnimation(ClientLevel level, int targetFrame) {
        animationLevel = level;
        animationInitialized = true;
        animationMode = BannerAnimationMode.FOLLOW_DAY_TIME;
        displayedFrame = targetFrame;
        nextFastFrameTick = Long.MIN_VALUE;
    }

    private static void updateFollowDayTime(boolean hovered, long currentGameTick, int targetFrame) {
        if (hovered) {
            beginFastAnimation(BannerAnimationMode.HOVER_FAST_FORWARD, currentGameTick);
            return;
        }

        int distance = forwardDistance(displayedFrame, targetFrame);
        if (distance == 1) {
            displayedFrame = targetFrame;
        } else if (distance > 1) {
            beginFastAnimation(BannerAnimationMode.CATCH_UP_FORWARD, currentGameTick);
        }
    }

    private static void beginFastAnimation(BannerAnimationMode mode, long currentGameTick) {
        animationMode = mode;
        nextFastFrameTick = currentGameTick + FAST_FRAME_TICKS;
    }

    private static void transitionFastMode(BannerAnimationMode mode) {
        animationMode = mode;
    }

    private static void processFastSteps(long currentGameTick, int targetFrame) {
        long availableSteps = getAvailableFastSteps(currentGameTick);
        if (availableSteps <= 0L) {
            return;
        }

        if (animationMode == BannerAnimationMode.HOVER_FAST_FORWARD) {
            processHoverSteps(availableSteps);
        } else if (animationMode == BannerAnimationMode.CATCH_UP_FORWARD) {
            processCatchUpSteps(availableSteps, targetFrame);
        }
    }

    private static void processHoverSteps(long availableSteps) {
        int frameAdvance = (int) (availableSteps % FRAME_COUNT);
        displayedFrame = Math.floorMod(displayedFrame + frameAdvance, FRAME_COUNT);
        nextFastFrameTick += availableSteps * FAST_FRAME_TICKS;
    }

    private static void processCatchUpSteps(long availableSteps, int targetFrame) {
        int distance = forwardDistance(displayedFrame, targetFrame);
        if (distance == 0) {
            stopFastAnimation();
            return;
        }

        long consumedSteps = Math.min(availableSteps, (long) distance);
        displayedFrame = Math.floorMod(displayedFrame + (int) consumedSteps, FRAME_COUNT);
        nextFastFrameTick += consumedSteps * FAST_FRAME_TICKS;
        if (displayedFrame == targetFrame) {
            stopFastAnimation();
        }
    }

    private static long getAvailableFastSteps(long currentGameTick) {
        if (nextFastFrameTick == Long.MIN_VALUE || currentGameTick < nextFastFrameTick) {
            return 0L;
        }
        return 1L + (currentGameTick - nextFastFrameTick) / FAST_FRAME_TICKS;
    }

    private static int forwardDistance(int fromFrame, int toFrame) {
        return Math.floorMod(toFrame - fromFrame, FRAME_COUNT);
    }

    private static int getDayTimeFrame(ClientLevel level) {
        long dayTime = Math.floorMod(level.getDayTime(), DAY_LENGTH_TICKS);
        return (int) (dayTime / DAY_TICKS_PER_FRAME);
    }

    private static void stopFastAnimation() {
        animationMode = BannerAnimationMode.FOLLOW_DAY_TIME;
        nextFastFrameTick = Long.MIN_VALUE;
    }

    private static void resetBannerAnimation() {
        animationMode = BannerAnimationMode.FOLLOW_DAY_TIME;
        animationLevel = null;
        animationInitialized = false;
        displayedFrame = 0;
        nextFastFrameTick = Long.MIN_VALUE;
    }

    private static void renderBannerBorder(GuiGraphics graphics, int headerLeft, int headerTop) {
        graphics.fill(headerLeft, headerTop, headerLeft + WIDTH - 1, headerTop + 1, BANNER_BORDER_DARK);
        graphics.fill(headerLeft + WIDTH - 1, headerTop, headerLeft + WIDTH, headerTop + 1,
                BANNER_BORDER_LIGHT);
        graphics.fill(headerLeft, headerTop + 1, headerLeft + 1, headerTop + HEIGHT - 1, BANNER_BORDER_DARK);
    }

    private static void renderTitle(GuiGraphics graphics, int headerLeft, int headerTop) {
        Font font = Minecraft.getInstance().font;
        int titleX = headerLeft + 5;
        int titleY = headerTop + 5;
        int textWidth = font.width(TITLE);
        int visualTextWidth = textWidth + 1;
        int backgroundLeft = titleX - 3;
        int backgroundTop = headerTop + 2;
        int mainWidthInHalfPixels = (visualTextWidth + 6) * 2;
        int totalWidthInHalfPixels = mainWidthInHalfPixels + 2;
        int mainHeightInHalfPixels = 26;

        graphics.pose().pushPose();
        graphics.pose().translate(backgroundLeft, backgroundTop, 0.0F);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.fill(0, 0, mainWidthInHalfPixels, mainHeightInHalfPixels, TITLE_BACKGROUND);
        graphics.fill(mainWidthInHalfPixels, 0, mainWidthInHalfPixels + 1, mainHeightInHalfPixels,
                TITLE_SHADOW_FIRST);
        graphics.fill(mainWidthInHalfPixels + 1, 0, totalWidthInHalfPixels, mainHeightInHalfPixels,
                TITLE_SHADOW_SECOND);
        graphics.fill(0, mainHeightInHalfPixels, totalWidthInHalfPixels, mainHeightInHalfPixels + 1,
                TITLE_SHADOW_FIRST);
        graphics.fill(0, mainHeightInHalfPixels + 1, totalWidthInHalfPixels, mainHeightInHalfPixels + 2,
                TITLE_SHADOW_SECOND);
        graphics.pose().popPose();

        graphics.drawString(font, TITLE, titleX + 1, titleY + 1, TITLE_SHADOW_FIRST, false);
        graphics.drawString(font, TITLE, titleX, titleY, TITLE_TEXT, false);
    }

    private static boolean hasHeaderSlots(Collection<ItemStack> displayItems) {
        if (displayItems.size() < HEADER_SLOT_COUNT) {
            return false;
        }

        Iterator<ItemStack> iterator = displayItems.iterator();
        for (int i = 0; i < HEADER_SLOT_COUNT; i++) {
            if (!iterator.hasNext() || !iterator.next().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private enum BannerAnimationMode {
        FOLLOW_DAY_TIME,
        HOVER_FAST_FORWARD,
        CATCH_UP_FORWARD
    }
}
