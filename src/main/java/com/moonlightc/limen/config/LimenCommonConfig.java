package com.moonlightc.limen.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class LimenCommonConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_PAMA_DETECTOR;
    public static final ForgeConfigSpec.DoubleValue PAMA_DETECTOR_HEALTH;
    public static final ForgeConfigSpec.DoubleValue PAMA_DETECTOR_FOLLOW_RANGE;
    public static final ForgeConfigSpec.DoubleValue PAMA_DETECTOR_IDLE_SPEED;
    public static final ForgeConfigSpec.DoubleValue PAMA_DETECTOR_LOCK_SPEED;
    public static final ForgeConfigSpec.DoubleValue PAMA_DETECTOR_LOCK_RADIUS;
    public static final ForgeConfigSpec.IntValue PAMA_DETECTOR_LOCK_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue PAMA_DETECTOR_GLOWING_TICKS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ZOMBIE_CHIPPED;
    public static final ForgeConfigSpec.DoubleValue ZOMBIE_CHIPPED_FOLLOW_RANGE;
    public static final ForgeConfigSpec.DoubleValue ZOMBIE_CHIPPED_CHIP_DROP_CHANCE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SKELETON_CHIPPED;
    public static final ForgeConfigSpec.DoubleValue SKELETON_CHIPPED_CHIP_DROP_CHANCE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTONOMOUS_SPIDER;
    public static final ForgeConfigSpec.DoubleValue AUTONOMOUS_SPIDER_HEALTH;
    public static final ForgeConfigSpec.DoubleValue AUTONOMOUS_SPIDER_ARMOR;
    public static final ForgeConfigSpec.DoubleValue AUTONOMOUS_SPIDER_SKILL1_RADIUS;
    public static final ForgeConfigSpec.IntValue AUTONOMOUS_SPIDER_SKILL1_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue AUTONOMOUS_SPIDER_SKILL1_WARMUP_TICKS;
    public static final ForgeConfigSpec.IntValue AUTONOMOUS_SPIDER_SKILL1_SHOCKED_TICKS;
    public static final ForgeConfigSpec.DoubleValue SHOCKED_FOV_SCALE;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("entities");
        ENABLE_PAMA_DETECTOR = builder
                .comment("Enable Detector, the basic unit of the first-stage PAMA boss system.")
                .define("enablePamaDetector", true);
        PAMA_DETECTOR_HEALTH = builder
                .comment("Base max health for Detector.")
                .defineInRange("pamaDetectorHealth", 20.0D, 1.0D, 1024.0D);
        PAMA_DETECTOR_FOLLOW_RANGE = builder
                .comment("Detection range for Detector. Vanilla skeletons commonly use 16 blocks.")
                .defineInRange("pamaDetectorFollowRange", 16.0D, 1.0D, 128.0D);
        PAMA_DETECTOR_IDLE_SPEED = builder
                .comment("Idle hovering movement speed for Detector.")
                .defineInRange("pamaDetectorIdleSpeed", 0.45D, 0.05D, 4.0D);
        PAMA_DETECTOR_LOCK_SPEED = builder
                .comment("Movement speed while Detector is locking and orbiting a target.")
                .defineInRange("pamaDetectorLockSpeed", 1.15D, 0.05D, 8.0D);
        PAMA_DETECTOR_LOCK_RADIUS = builder
                .comment("Orbit radius around a locked player.")
                .defineInRange("pamaDetectorLockRadius", 4.0D, 1.0D, 16.0D);
        PAMA_DETECTOR_LOCK_COOLDOWN_TICKS = builder
                .comment("Cooldown before Detector applies glowing to its locked player. 80 ticks = 4 seconds.")
                .defineInRange("pamaDetectorLockCooldownTicks", 80, 1, 20 * 60 * 10);
        PAMA_DETECTOR_GLOWING_TICKS = builder
                .comment("Glowing duration applied by Detector. 40 ticks = 2 seconds.")
                .defineInRange("pamaDetectorGlowingTicks", 40, 1, 20 * 60 * 10);
        ENABLE_ZOMBIE_CHIPPED = builder
                .comment("Enable Zombie Chipped, a redstone-machine controlled zombie variant.")
                .define("enableZombieChipped", true);
        ZOMBIE_CHIPPED_FOLLOW_RANGE = builder
                .comment("Follow range for Zombie Chipped. Default is half of a normal zombie's common 35-block follow range.")
                .defineInRange("zombieChippedFollowRange", 17.5D, 1.0D, 128.0D);
        ZOMBIE_CHIPPED_CHIP_DROP_CHANCE = builder
                .comment("Drop chance for Damaged Chip from Zombie Chipped on death or water conversion.")
                .defineInRange("zombieChippedChipDropChance", 0.5D, 0.0D, 1.0D);
        ENABLE_SKELETON_CHIPPED = builder
                .comment("Enable Skeleton Chipped, a redstone-machine controlled skeleton variant.")
                .define("enableSkeletonChipped", true);
        SKELETON_CHIPPED_CHIP_DROP_CHANCE = builder
                .comment("Drop chance for Damaged Chip from Skeleton Chipped on death or water conversion.")
                .defineInRange("skeletonChippedChipDropChance", 0.5D, 0.0D, 1.0D);
        ENABLE_AUTONOMOUS_SPIDER = builder
                .comment("Enable Autonomous Spider, an elite redstone-machine spider unit.")
                .define("enableAutonomousSpider", true);
        AUTONOMOUS_SPIDER_HEALTH = builder
                .comment("Base max health for Autonomous Spider.")
                .defineInRange("autonomousSpiderHealth", 24.0D, 1.0D, 1024.0D);
        AUTONOMOUS_SPIDER_ARMOR = builder
                .comment("Base armor for Autonomous Spider.")
                .defineInRange("autonomousSpiderArmor", 10.0D, 0.0D, 1024.0D);
        AUTONOMOUS_SPIDER_SKILL1_RADIUS = builder
                .comment("Pulse radius for Autonomous Spider skill 1.")
                .defineInRange("autonomousSpiderSkill1Radius", 5.0D, 1.0D, 32.0D);
        AUTONOMOUS_SPIDER_SKILL1_COOLDOWN_TICKS = builder
                .comment("Cooldown for Autonomous Spider skill 1. 200 ticks = 10 seconds.")
                .defineInRange("autonomousSpiderSkill1CooldownTicks", 200, 1, 20 * 60 * 10);
        AUTONOMOUS_SPIDER_SKILL1_WARMUP_TICKS = builder
                .comment("Warmup before Autonomous Spider releases its pulse.")
                .defineInRange("autonomousSpiderSkill1WarmupTicks", 12, 0, 20 * 10);
        AUTONOMOUS_SPIDER_SKILL1_SHOCKED_TICKS = builder
                .comment("Duration of the Shocked effect from Autonomous Spider skill 1. 60 ticks = 3 seconds.")
                .defineInRange("autonomousSpiderSkill1ShockedTicks", 60, 1, 20 * 60 * 10);
        SHOCKED_FOV_SCALE = builder
                .comment("FOV multiplier applied while a player is Shocked.")
                .defineInRange("shockedFovScale", 0.7D, 0.1D, 1.0D);
        builder.pop();

        builder.push("debug");
        DEBUG_LOGS = builder
                .comment("Enable extra debug logging for Limen systems.")
                .define("debugLogs", false);
        builder.pop();

        SPEC = builder.build();
    }

    private LimenCommonConfig() {
    }
}
