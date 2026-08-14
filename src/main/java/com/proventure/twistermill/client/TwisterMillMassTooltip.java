package com.proventure.twistermill.client;

import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.config.TwisterMillConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

public final class TwisterMillMassTooltip {

    private static final DecimalFormat MASS_FORMAT = new DecimalFormat(
            "0.##",
            DecimalFormatSymbols.getInstance(Locale.ROOT)
    );

    private TwisterMillMassTooltip() {
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!TwisterMillConfig.isMassTooltipShown()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem)) {
            return;
        }

        Double mass = resolveKnownMass(stack);
        if (mass == null
                || (isAeronauticsTooltipProviderLoaded() && !isConfiguredBladeArm(stack))
                || hasMassTooltip(event.getToolTip())) {
            return;
        }

        event.getToolTip().add(buildMassTooltip(mass));
    }

    private static Double resolveKnownMass(ItemStack stack) {
        if (stack.getItem() == ModBlocks.TWISTER_SAIL_BLOCK.get().asItem()
                || stack.getItem() == ModBlocks.TWISTER_SAIL_FRAME_BLOCK.get().asItem()
                || stack.getItem() == ModBlocks.METAL_TRAVERSE.get().asItem()) {
            return 0.25D;
        }
        if (stack.getItem() == ModBlocks.BLADE_ARM_BLOCK.get().asItem()) {
            return TwisterMillConfig.getBladeArmBlockMass();
        }
        if (stack.getItem() == ModBlocks.BLADE_ARM_EASTFACE_BLOCK.get().asItem()) {
            return TwisterMillConfig.getBladeArmEastfaceBlockMass();
        }
        if (stack.getItem() == ModBlocks.BLADE_ARM_WESTFACE_BLOCK.get().asItem()) {
            return TwisterMillConfig.getBladeArmWestfaceBlockMass();
        }
        return null;
    }

    private static boolean isConfiguredBladeArm(ItemStack stack) {
        return stack.getItem() == ModBlocks.BLADE_ARM_BLOCK.get().asItem()
                || stack.getItem() == ModBlocks.BLADE_ARM_EASTFACE_BLOCK.get().asItem()
                || stack.getItem() == ModBlocks.BLADE_ARM_WESTFACE_BLOCK.get().asItem();
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
