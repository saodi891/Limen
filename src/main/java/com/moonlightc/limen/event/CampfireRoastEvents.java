package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.inventory.CampfireHubMenu;
import com.moonlightc.limen.inventory.CampfireMenu;
import com.moonlightc.limen.inventory.CampfireScene;
import com.moonlightc.limen.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Opens the campfire options hub when a player right-clicks a lit campfire while holding a marshmallow
 * in the main hand, drives the peace scene (nearby vanilla hostiles flee), and closes the hub when the
 * player is hurt or the campfire is destroyed.
 *
 * <p>The interaction has to be intercepted here rather than in a block override: the campfire is a
 * vanilla block, and vanilla {@code CampfireBlock.use} would claim the click first for any held stack
 * that has a {@code campfire_cooking} recipe. {@code RightClickBlock} fires ahead of {@code use}.
 *
 * <p>Each marshmallow is bound to a realm: the plain stick roasts only over an Overworld fire, the blaze
 * rod over any Nether fire or an Overworld soul campfire (hot enough to stand in for the Nether). The wrong
 * pairing shows a flavour message instead of opening. In the Overworld the hub also keeps the
 * night/no-thunderstorm gate ("not the time to roast marshmallows"); the Nether has no real day-night cycle
 * or weather, so its fire is always available. The night check reads the day-time window directly so it
 * stays correct during a daytime thunderstorm (which darkens the sky and would fool a brightness check).
 */
@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class CampfireRoastEvents {
    /** Vanilla bosses and elite mobs that ignore the campfire peace scene. Data-driven so it can be tuned. */
    private static final TagKey<net.minecraft.world.entity.EntityType<?>> SCENE_IMMUNE =
            TagKey.create(Registries.ENTITY_TYPE, Limen.id("campfire_scene_immune"));
    private static final int FLEE_RADIUS = 15;
    private static final double FLEE_SPEED = 1.35D;
    private static final double FLEE_TARGET_DISTANCE = 16.0D;
    /** Day-time window (ticks within a 24000-tick day) during which the campfire hub may be opened. */
    static final long NIGHT_START = 12542L;
    static final long NIGHT_END = 23459L;

    private CampfireRoastEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack held = event.getItemStack();
        if (CampfireMenu.roastResult(held) == null) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!CampfireBlock.isLitCampfire(state)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide()));

        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            tryOpen(serverPlayer, level, pos, state, held);
        }
    }

    private static void tryOpen(ServerPlayer player, Level level, BlockPos pos, BlockState state, ItemStack held) {
        boolean overworld = level.dimension() == Level.OVERWORLD;
        boolean nether = level.dimension() == Level.NETHER;
        boolean soulCampfire = state.is(Blocks.SOUL_CAMPFIRE);

        // The plain stick roasts only over an Overworld fire; anywhere else just scolds the player.
        if (held.is(ModItems.MARSHMALLOW_ONASTICK.get()) && !overworld) {
            player.displayClientMessage(Component.translatable("message.limen.marshmallow_too_hot"), true);
            return;
        }
        // The blaze rod roasts over any Nether fire, or over an Overworld soul campfire (hot enough) — a plain
        // Overworld campfire or any other realm is too weak.
        if (held.is(ModItems.MARSHMALLOW_ONABLAZEROD.get()) && !nether && !(overworld && soulCampfire)) {
            player.displayClientMessage(Component.translatable("message.limen.marshmallow_too_cold"), true);
            return;
        }

        // The night/no-thunderstorm gate is Overworld-only; the Nether has no real day-night cycle or
        // weather, so the blaze-rod fire is always available there.
        if (overworld) {
            long time = level.getDayTime() % 24000L;
            boolean night = time >= NIGHT_START && time <= NIGHT_END;
            if (!night || level.isThundering()) {
                player.displayClientMessage(Component.translatable("message.limen.not_marshmallow_time"), true);
                return;
            }
        }

        Component title = Component.translatable(state.is(Blocks.SOUL_CAMPFIRE)
                ? "container.limen.soul_campfire"
                : "container.limen.campfire");
        ContainerLevelAccess access = ContainerLevelAccess.create(level, pos);

        NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                (containerId, inventory, ignored) -> new CampfireHubMenu(containerId, inventory, access, pos),
                title), buffer -> buffer.writeBlockPos(pos));

        if (level instanceof ServerLevel serverLevel) {
            driveFlee(serverLevel, pos);
        }
    }

    /**
     * Clears aggro and pushes every eligible mob away from the campfire. Eligible = a vanilla hostile
     * ({@link Enemy}) within {@value #FLEE_RADIUS}-block Chebyshev distance that is not tagged
     * {@code limen:campfire_scene_immune} (bosses and elites are). Run every tick while the roasting
     * screen is open so mobs cannot re-aggro or wander back in.
     */
    private static void driveFlee(ServerLevel level, BlockPos campfire) {
        double cx = campfire.getX() + 0.5D;
        double cy = campfire.getY() + 0.5D;
        double cz = campfire.getZ() + 0.5D;
        AABB box = new AABB(campfire).inflate(FLEE_RADIUS);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, CampfireRoastEvents::shouldFlee)) {
            double dx = Math.abs(mob.getX() - cx);
            double dy = Math.abs(mob.getY() - cy);
            double dz = Math.abs(mob.getZ() - cz);
            if (Math.max(dx, Math.max(dy, dz)) > FLEE_RADIUS) {
                continue;
            }

            mob.setTarget(null);
            if (mob instanceof NeutralMob neutral) {
                neutral.stopBeingAngry();
            }

            if (mob.getNavigation().isDone()) {
                double ox = mob.getX() - cx;
                double oz = mob.getZ() - cz;
                double horiz = Math.sqrt(ox * ox + oz * oz);
                double dirX;
                double dirZ;
                if (horiz < 1.0E-3D) {
                    double angle = mob.getRandom().nextDouble() * Math.PI * 2.0D;
                    dirX = Math.cos(angle);
                    dirZ = Math.sin(angle);
                } else {
                    dirX = ox / horiz;
                    dirZ = oz / horiz;
                }
                mob.getNavigation().moveTo(mob.getX() + dirX * FLEE_TARGET_DISTANCE,
                        mob.getY(), mob.getZ() + dirZ * FLEE_TARGET_DISTANCE, FLEE_SPEED);
            }
        }
    }

    private static boolean shouldFlee(Mob mob) {
        if (!mob.isAlive() || !(mob instanceof Enemy)) {
            return false;
        }
        net.minecraft.resources.ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (key == null || !"minecraft".equals(key.getNamespace())) {
            return false;
        }
        return !mob.getType().builtInRegistryHolder().is(SCENE_IMMUNE);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (player.containerMenu instanceof CampfireScene scene) {
            BlockPos campfire = scene.getCampfirePos();
            if (!CampfireBlock.isLitCampfire(serverLevel.getBlockState(campfire))) {
                player.closeContainer();
                return;
            }
            driveFlee(serverLevel, campfire);
        }
    }

    /** Being hurt breaks the peace: close the hub the moment the player takes any damage. */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.containerMenu instanceof CampfireScene) {
            player.closeContainer();
        }
    }

    /** Each server tick, let the rest manager skip the night once enough players are resting or asleep. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
            CampfireRestManager.tick(serverLevel);
        }
    }
}
