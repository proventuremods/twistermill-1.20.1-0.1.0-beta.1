package com.proventure.twistermill.config;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.Optional;

public final class AdvancementRewardManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation FALLBACK_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath("minecraft", "ancient_debris");
    private static final int FALLBACK_COUNT = 6;
    private static final RewardSpec FALLBACK_REWARD = new RewardSpec(FALLBACK_ITEM_ID, FALLBACK_COUNT);

    private static volatile MinecraftServer activeServer;
    private static volatile RewardSpec runtimeReward = FALLBACK_REWARD;
    private static volatile boolean validationPending = true;

    private AdvancementRewardManager() {
    }

    public static ValidationResult validate(String itemText, String countText, Registry<Item> itemRegistry) {
        String trimmedCount = countText == null ? "" : countText.trim();
        if (trimmedCount.isEmpty()) {
            return ValidationResult.failure(ValidationError.COUNT_MISSING);
        }

        final int count;
        try {
            count = Integer.parseInt(trimmedCount);
        } catch (NumberFormatException ignored) {
            return ValidationResult.failure(ValidationError.COUNT_NOT_INTEGER);
        }
        if (count < 1 || count > 64) {
            return ValidationResult.failure(ValidationError.COUNT_OUT_OF_RANGE);
        }

        String trimmedItem = itemText == null ? "" : itemText.trim();
        if (trimmedItem.isEmpty()) {
            return ValidationResult.failure(ValidationError.ITEM_MISSING);
        }

        int separator = trimmedItem.indexOf(':');
        if (separator <= 0
                || separator == trimmedItem.length() - 1
                || separator != trimmedItem.lastIndexOf(':')) {
            return ValidationResult.failure(ValidationError.ITEM_INVALID_FORMAT);
        }

        ResourceLocation requestedId = ResourceLocation.tryParse(trimmedItem);
        if (requestedId == null) {
            return ValidationResult.failure(ValidationError.ITEM_INVALID_FORMAT);
        }

        Optional<Item> resolvedItem = itemRegistry.getOptional(requestedId);
        if (resolvedItem.isEmpty()) {
            return ValidationResult.failure(ValidationError.ITEM_NOT_REGISTERED);
        }

        Item item = resolvedItem.get();
        if (item == Items.AIR) {
            return ValidationResult.failure(ValidationError.ITEM_AIR);
        }

        ResourceLocation canonicalId = itemRegistry.getKey(item);
        if (canonicalId == null) {
            return ValidationResult.failure(ValidationError.ITEM_NOT_REGISTERED);
        }
        return ValidationResult.success(canonicalId, count);
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == TwisterMillConfig.COMMON_SPEC) {
            validationPending = true;
        }
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != TwisterMillConfig.COMMON_SPEC) {
            return;
        }

        validationPending = true;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> refreshRuntimeReward(server, false));
        }
    }

    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        synchronized (AdvancementRewardManager.class) {
            activeServer = server;
            runtimeReward = FALLBACK_REWARD;
            validationPending = true;
        }
        refreshRuntimeReward(server, true);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        synchronized (AdvancementRewardManager.class) {
            if (activeServer == event.getServer()) {
                activeServer = null;
                runtimeReward = FALLBACK_REWARD;
                validationPending = true;
            }
        }
    }

    public static void awardIfEnabled(ServerPlayer player) {
        MinecraftServer server = player.server;
        refreshRuntimeReward(server, false);
        if (!TwisterMillConfig.isNetheriteAdvancementDropEnabled()) {
            return;
        }

        Registry<Item> itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);
        RewardSpec reward = runtimeReward;
        Item item = resolveRewardItem(itemRegistry, reward.itemId()).orElse(null);
        if (item == null) {
            reward = FALLBACK_REWARD;
            item = resolveRewardItem(itemRegistry, reward.itemId()).orElse(null);
            synchronized (AdvancementRewardManager.class) {
                if (activeServer == server) {
                    runtimeReward = FALLBACK_REWARD;
                }
            }
        }
        if (item == null) {
            LOGGER.error("Unable to resolve advancement reward item or fallback item; reward skipped safely");
            return;
        }

        giveSplitReward(player, item, reward.count());
    }

    private static synchronized void refreshRuntimeReward(MinecraftServer server, boolean force) {
        if (activeServer != server) {
            activeServer = server;
            runtimeReward = FALLBACK_REWARD;
            validationPending = true;
        }
        if (!force && !validationPending) {
            return;
        }

        validationPending = false;
        Registry<Item> itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);
        ValidationResult result = validate(
                TwisterMillConfig.getAdvancementDropItem(),
                Integer.toString(TwisterMillConfig.getAdvancementDropCount()),
                itemRegistry
        );
        if (result.isValid()) {
            runtimeReward = new RewardSpec(result.itemId(), result.count());
        } else {
            LOGGER.warn(
                    "Rejected configured advancement reward pair (item='{}', count={}): {}; retaining {} x {}",
                    TwisterMillConfig.getAdvancementDropItem(),
                    TwisterMillConfig.getAdvancementDropCount(),
                    result.error(),
                    runtimeReward.count(),
                    runtimeReward.itemId()
            );
        }
    }

    private static Optional<Item> resolveRewardItem(Registry<Item> itemRegistry, ResourceLocation itemId) {
        return itemRegistry.getOptional(itemId).filter(item -> item != Items.AIR);
    }

    private static void giveSplitReward(ServerPlayer player, Item item, int totalCount) {
        int maxStackSize = item.getDefaultMaxStackSize();
        if (maxStackSize < 1) {
            LOGGER.error("Advancement reward item {} has invalid max stack size {}; reward skipped safely",
                    item, maxStackSize);
            return;
        }

        boolean insertedAny = false;
        int remaining = totalCount;
        while (remaining > 0) {
            int stackCount = Math.min(maxStackSize, remaining);
            remaining -= stackCount;

            ItemStack rewardStack = new ItemStack(item, stackCount);
            int beforeInsert = rewardStack.getCount();
            player.addItem(rewardStack);
            insertedAny |= rewardStack.getCount() < beforeInsert;

            if (!rewardStack.isEmpty()) {
                ItemEntity droppedReward = player.drop(rewardStack, false);
                if (droppedReward != null) {
                    droppedReward.setNoPickUpDelay();
                    droppedReward.setTarget(player.getUUID());
                }
            }
        }

        if (insertedAny) {
            player.level().playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS,
                    0.2F,
                    ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F
            );
            player.containerMenu.broadcastChanges();
        }
    }

    private record RewardSpec(ResourceLocation itemId, int count) {
    }

    public record ValidationResult(ResourceLocation itemId, int count, ValidationError error) {
        public static ValidationResult success(ResourceLocation itemId, int count) {
            return new ValidationResult(itemId, count, null);
        }

        public static ValidationResult failure(ValidationError error) {
            return new ValidationResult(null, 0, error);
        }

        public boolean isValid() {
            return error == null;
        }
    }

    public enum ValidationError {
        COUNT_MISSING,
        COUNT_NOT_INTEGER,
        COUNT_OUT_OF_RANGE,
        ITEM_MISSING,
        ITEM_INVALID_FORMAT,
        ITEM_NOT_REGISTERED,
        ITEM_AIR
    }
}
