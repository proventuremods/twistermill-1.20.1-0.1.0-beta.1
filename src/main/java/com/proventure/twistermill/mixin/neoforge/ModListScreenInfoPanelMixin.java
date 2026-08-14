package com.proventure.twistermill.mixin.neoforge;

import com.proventure.twistermill.client.ModListDescriptionResolver;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.neoforged.neoforge.client.gui.ModListScreen$InfoPanel")
public abstract class ModListScreenInfoPanelMixin {

    @Redirect(
            method = "resizeContent(Ljava/util/List;)Ljava/util/List;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/common/CommonHooks;newChatWithLinks(Ljava/lang/String;Z)Lnet/minecraft/network/chat/Component;"
            )
    )
    private static Component twistermill$resolveDynamicDescription(String line, boolean allowMissingHeader) {
        if (ModListDescriptionResolver.DESCRIPTION_LINE_MARKER.equals(line)) {
            return ModListDescriptionResolver.resolveDescriptionLine();
        }
        if (ModListDescriptionResolver.STATUS_LINE_MARKER.equals(line)) {
            return ModListDescriptionResolver.resolveStatusLine();
        }

        return CommonHooks.newChatWithLinks(line, allowMissingHeader);
    }
}
