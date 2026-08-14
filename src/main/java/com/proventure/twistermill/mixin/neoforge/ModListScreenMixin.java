package com.proventure.twistermill.mixin.neoforge;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.client.ModListDescriptionResolver;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforge.client.gui.widget.ModListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;

@Mixin(ModListScreen.class)
public abstract class ModListScreenMixin {

    @Shadow
    private ModListWidget.ModEntry selected;

    @ModifyArg(
            method = "updateCache()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/client/gui/ModListScreen$InfoPanel;setInfo(Ljava/util/List;Lnet/minecraft/resources/ResourceLocation;Lnet/neoforged/neoforge/common/util/Size2i;)V"
            ),
            index = 0
    )
    private List<String> twistermill$markDynamicDescription(List<String> lines) {
        if (selected == null
                || !TwisterMill.MOD_ID.equals(selected.getInfo().getModId())
                || !ModListDescriptionResolver.hasExactlyOneActiveBackend()) {
            return lines;
        }

        String description = FMLTranslations.getPattern(
                "fml.menu.mods.info.description." + TwisterMill.MOD_ID,
                selected.getInfo()::getDescription
        );
        int descriptionIndex = twistermill$findDescriptionIndex(lines, description);
        if (descriptionIndex < 0) {
            return lines;
        }

        List<String> replacementLines = new ArrayList<>(lines);
        replacementLines.set(descriptionIndex, ModListDescriptionResolver.DESCRIPTION_LINE_MARKER);
        replacementLines.add(descriptionIndex + 1, ModListDescriptionResolver.STATUS_LINE_MARKER);
        return replacementLines;
    }

    private static int twistermill$findDescriptionIndex(List<String> lines, String description) {
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index - 1) == null && description.equals(lines.get(index))) {
                return index;
            }
        }
        return -1;
    }
}
