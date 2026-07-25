package com.proventure.twistermill.client;

import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.config.TwisterMillConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TwisterMillMassTooltip {

    private static final DecimalFormat MASS_FORMAT = new DecimalFormat(
            "0.##",
            DecimalFormatSymbols.getInstance(Locale.ROOT)
    );

    private static final Map<Item, Double> KNOWN_MASSES = Map.of(
            ModBlocks.TWISTER_SAIL_BLOCK.get().asItem(), 0.25D,
            ModBlocks.TWISTER_SAIL_FRAME_BLOCK.get().asItem(), 0.25D
    );

    private TwisterMillMassTooltip() {
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!TwisterMillConfig.isMassTooltipShown() || isAeronauticsTooltipProviderLoaded()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem)) {
            return;
        }

        Double mass = KNOWN_MASSES.get(stack.getItem());
        if (mass == null || hasMassTooltip(event.getToolTip())) {
            return;
        }

        event.getToolTip().add(buildMassTooltip(mass));
    }

    private static boolean isAeronauticsTooltipProviderLoaded() {
        ModList modList = ModList.get();
        return modList.isLoaded("simulated") || modList.isLoaded("aeronautics_bundled");
    }

    private static boolean hasMassTooltip(List<Component> tooltip) {
        for (Component component : tooltip) {
            String line = component.getString().toLowerCase(Locale.ROOT);
            if (line.contains(" kpg") || line.contains(" kg")
                    || line.contains("super light") || line.contains("super heavy")
                    || line.contains("absurdly heavy")) {
                return true;
            }
        }
        return false;
    }

    private static Component buildMassTooltip(double mass) {
        return Component.translatable("twistermill.tooltip.mass.properties")
                .withStyle(ChatFormatting.GRAY)
                .append(" ")
                .append(categoryFor(mass))
                .append(Component.translatable(
                                "twistermill.tooltip.mass.unit",
                                MASS_FORMAT.format(mass)
                        )
                        .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent categoryFor(double mass) {
        if (mass <= 0.0D) {
            return Component.translatable("twistermill.tooltip.mass.none")
                    .withStyle(ChatFormatting.GRAY);
        }
        if (mass <= 0.25D) {
            return Component.translatable("twistermill.tooltip.mass.super_light")
                    .withStyle(ChatFormatting.AQUA);
        }
        if (mass <= 0.5D) {
            return Component.translatable("twistermill.tooltip.mass.light")
                    .withStyle(ChatFormatting.GREEN);
        }
        if (mass < 4.0D) {
            return Component.translatable("twistermill.tooltip.mass.heavy")
                    .withStyle(ChatFormatting.YELLOW);
        }
        if (mass < 50.0D) {
            return Component.translatable("twistermill.tooltip.mass.super_heavy")
                    .withStyle(ChatFormatting.RED);
        }
        return Component.translatable("twistermill.tooltip.mass.absurdly_heavy")
                .withStyle(ChatFormatting.RED);
    }
}
