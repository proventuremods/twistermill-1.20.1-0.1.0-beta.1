package com.proventure.twistermill.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.proventure.twistermill.config.TwisterMillConfig;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.jetbrains.annotations.NotNull;

public record ConfigEnabledCondition(String key) implements ICondition {
    public static final MapCodec<ConfigEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("key").forGetter(ConfigEnabledCondition::key)
    ).apply(instance, ConfigEnabledCondition::new));

    @Override
    public boolean test(@NotNull IContext context) {
        return TwisterMillConfig.isContentEnabled(key);
    }

    @Override
    public @NotNull MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
