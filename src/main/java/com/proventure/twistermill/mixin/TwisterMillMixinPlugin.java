package com.proventure.twistermill.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public class TwisterMillMixinPlugin implements IMixinConfigPlugin {

    private static final String WEATHER2_MOD_ID = "weather2";
    private static final String WEATHER2_MIXIN_PREFIX = "com.proventure.twistermill.mixin.weather2.";
    private static final String TWISTER_CLICK_TO_LINK_MIXIN_CLASS =
            "com.proventure.twistermill.mixin.create.ClickToLinkBlockItemMixin";
    private static final String TWISTER_REDSTONE_LINK_MIXIN_CLASS =
            "com.proventure.twistermill.mixin.create.RedstoneLinkNetworkHandlerMixin";
    private static final String TWISTER_LINK_RENDERER_MIXIN_CLASS =
            "com.proventure.twistermill.mixin.create.LinkRendererMixin";
    private static final String SABLE_MIXIN_CONFIG_RESOURCE = "sable-neoforge.mixins.json";
    private static final String SABLE_CLICK_TO_LINK_MIXIN_ENTRY =
            "\"compatibility.create.display_link.ClickToLinkBlockItemMixin\"";
    private static final String SABLE_REDSTONE_LINK_MIXIN_ENTRY =
            "\"compatibility.create.redstone_links.RedstoneLinkNetworkHandlerMixin\"";
    private static final String SABLE_LINK_RENDERER_MIXIN_ENTRY =
            "\"compatibility.create.render_fixes.LinkRendererMixin\"";

    private static volatile Boolean applyTwisterClickToLinkMixin;
    private static volatile Boolean applyTwisterRedstoneLinkMixin;
    private static volatile Boolean applyTwisterLinkRendererMixin;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName == null) {
            return true;
        }

        if (mixinClassName.startsWith(WEATHER2_MIXIN_PREFIX)) {
            return isWeather2PresentInLoadingContext();
        }

        return switch (mixinClassName) {
            case TWISTER_CLICK_TO_LINK_MIXIN_CLASS -> shouldApplyTwisterClickToLinkMixin();
            case TWISTER_REDSTONE_LINK_MIXIN_CLASS -> shouldApplyTwisterRedstoneLinkMixin();
            case TWISTER_LINK_RENDERER_MIXIN_CLASS -> shouldApplyTwisterLinkRendererMixin();
            default -> true;
        };
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isWeather2PresentInLoadingContext() {
        try {
            Class<?> loadingModListClass = Class.forName("net.neoforged.fml.loading.LoadingModList");
            Method get = loadingModListClass.getMethod("get");
            Object loadingModList = get.invoke(null);
            if (loadingModList != null) {
                Method getModFileById = loadingModListClass.getMethod("getModFileById", String.class);
                Object modFile = getModFileById.invoke(loadingModList, WEATHER2_MOD_ID);
                if (modFile != null) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
            Method get = modListClass.getMethod("get");
            Object modList = get.invoke(null);
            if (modList != null) {
                Method isLoaded = modListClass.getMethod("isLoaded", String.class);
                Object loaded = isLoaded.invoke(modList, WEATHER2_MOD_ID);
                if (loaded instanceof Boolean present) {
                    return present;
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private static boolean shouldApplyTwisterClickToLinkMixin() {
        return computeApplyCached(
                applyTwisterClickToLinkMixin,
                SABLE_CLICK_TO_LINK_MIXIN_ENTRY
        );
    }

    private static boolean shouldApplyTwisterRedstoneLinkMixin() {
        return computeApplyCached(
                applyTwisterRedstoneLinkMixin,
                SABLE_REDSTONE_LINK_MIXIN_ENTRY
        );
    }

    private static boolean shouldApplyTwisterLinkRendererMixin() {
        return computeApplyCached(
                applyTwisterLinkRendererMixin,
                SABLE_LINK_RENDERER_MIXIN_ENTRY
        );
    }

    private static boolean computeApplyCached(Boolean cachedValue, String sableMixinEntry) {
        if (cachedValue != null) {
            return cachedValue;
        }

        synchronized (TwisterMillMixinPlugin.class) {
            if (SABLE_CLICK_TO_LINK_MIXIN_ENTRY.equals(sableMixinEntry) && applyTwisterClickToLinkMixin != null) {
                return applyTwisterClickToLinkMixin;
            }
            if (SABLE_REDSTONE_LINK_MIXIN_ENTRY.equals(sableMixinEntry) && applyTwisterRedstoneLinkMixin != null) {
                return applyTwisterRedstoneLinkMixin;
            }
            if (SABLE_LINK_RENDERER_MIXIN_ENTRY.equals(sableMixinEntry) && applyTwisterLinkRendererMixin != null) {
                return applyTwisterLinkRendererMixin;
            }

            // If Sable provides the same Create mixin entry, skip Twister's corresponding redirect mixin.
            boolean sableProvidesMixinEntry = hasSableMixinConfigured(sableMixinEntry);
            boolean apply = !sableProvidesMixinEntry;

            if (SABLE_CLICK_TO_LINK_MIXIN_ENTRY.equals(sableMixinEntry)) {
                applyTwisterClickToLinkMixin = apply;
            } else if (SABLE_REDSTONE_LINK_MIXIN_ENTRY.equals(sableMixinEntry)) {
                applyTwisterRedstoneLinkMixin = apply;
            } else if (SABLE_LINK_RENDERER_MIXIN_ENTRY.equals(sableMixinEntry)) {
                applyTwisterLinkRendererMixin = apply;
            }

            return apply;
        }
    }

    private static boolean hasSableMixinConfigured(String mixinEntry) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = TwisterMillMixinPlugin.class.getClassLoader();
        }

        if (classLoader == null) {
            return false;
        }

        try (InputStream stream = classLoader.getResourceAsStream(SABLE_MIXIN_CONFIG_RESOURCE)) {
            if (stream == null) {
                return false;
            }

            String mixinsJson = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return mixinsJson.contains(mixinEntry);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
