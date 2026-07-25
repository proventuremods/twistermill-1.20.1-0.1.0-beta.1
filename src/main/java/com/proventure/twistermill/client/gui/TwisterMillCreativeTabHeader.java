package com.proventure.twistermill.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
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
    private static final String MOD_ID = "twistermill";
    private static final String TITLE = "TwisterMill";
    private static final int TITLE_BACKGROUND = 0xFF896DC2;
    private static final int TITLE_ACCENT = 0xFF6C49B1;
    private static final int TITLE_LIGHT = 0xFFFFFFFF;
    private static final ResourceLocation TAB_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "create_twistermill_tab");
    private static final ResourceLocation SPRITE =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "creative_tab/twistermill_animated_tab");

    private static int currentRow;

    private TwisterMillCreativeTabHeader() {
    }

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
    }

    public static void setCurrentRow(int row) {
        currentRow = Math.max(0, row);
    }

    public static void render(CreativeModeInventoryScreen screen, CreativeModeTab selectedTab, GuiGraphics graphics) {
        if (!isTwisterMillTab(selectedTab) || currentRow != 0) {
            return;
        }

        int headerLeft = screen.getGuiLeft() + HEADER_LEFT_OFFSET;
        int headerTop = screen.getGuiTop() + HEADER_TOP_OFFSET;
        graphics.blitSprite(SPRITE, headerLeft, headerTop, WIDTH, HEIGHT);
        renderTitle(graphics, headerLeft, headerTop);
    }

    private static void renderTitle(GuiGraphics graphics, int headerLeft, int headerTop) {
        Font font = Minecraft.getInstance().font;
        int titleX = headerLeft + 5;
        int titleY = headerTop + 5;
        int textWidth = font.width(TITLE);
        int visualTextWidth = textWidth + 1;
        int backgroundLeft = titleX - 3;
        int backgroundTop = titleY - 3;
        int backgroundRight = titleX + visualTextWidth + 3;
        int backgroundBottom = headerTop + HEIGHT - 2;

        graphics.fill(backgroundLeft + 1, backgroundTop + 1, backgroundRight + 1, backgroundBottom + 1,
                TITLE_ACCENT);
        graphics.fill(backgroundLeft, backgroundTop, backgroundRight, backgroundBottom, TITLE_BACKGROUND);
        graphics.drawString(font, TITLE, titleX + 1, titleY + 1, TITLE_ACCENT, false);
        graphics.drawString(font, TITLE, titleX, titleY, TITLE_LIGHT, false);
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
}
