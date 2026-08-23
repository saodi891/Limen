package com.moonlightc.limen.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class LimenClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_ENTITY_ANIMATION_DEBUG;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("client");
        ENABLE_ENTITY_ANIMATION_DEBUG = builder
                .comment("Enable client-side animation debug logging.")
                .define("enableEntityAnimationDebug", false);
        builder.pop();

        SPEC = builder.build();
    }

    private LimenClientConfig() {
    }
}
