package com.moonlightc.limen.hotbar;

import com.moonlightc.limen.Limen;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Attaches {@link ArtisanHotbars} to every player and carries the rows across respawn and dimension
 * changes.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class ArtisanHotbarsCapability {
    public static final Capability<ArtisanHotbars> ARTISAN_HOTBARS =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    public static final ResourceLocation ID = Limen.id("artisan_hotbars");

    private ArtisanHotbarsCapability() {
    }

    /** The player's artisan rows, or empty if the capability is somehow missing. */
    public static Optional<ArtisanHotbars> get(@Nullable Player player) {
        if (player == null) {
            return Optional.empty();
        }
        return player.getCapability(ARTISAN_HOTBARS).resolve();
    }

    @Mod.EventBusSubscriber(modid = Limen.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
            event.register(ArtisanHotbars.class);
        }
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(ID, new Provider());
        }
    }

    /**
     * Copies the rows onto the new player instance. Respawn builds a fresh {@code ServerPlayer}, so
     * without this the rows would be lost on death or a dimension change.
     */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        // The original is being discarded; its capabilities are still readable but must be
        // reviveCaps'd first, since Forge invalidates them when the entity is removed.
        original.reviveCaps();
        get(original).ifPresent(from -> get(event.getEntity()).ifPresent(to -> to.copyFrom(from)));
        original.invalidateCaps();
    }

    /** Keeps the rows attached through a dimension change, which also swaps the player instance. */
    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        get(event.getEntity()).ifPresent(ArtisanHotbars::setChanged);
    }

    /** Marks the rows for a resync whenever the player's entity is rebuilt. */
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        get(event.getEntity()).ifPresent(ArtisanHotbars::setChanged);
    }

    /** Sends the rows once on login, before the player can open anything. */
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        get(event.getEntity()).ifPresent(ArtisanHotbars::setChanged);
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final ArtisanHotbars hotbars = new ArtisanHotbars();
        private final LazyOptional<ArtisanHotbars> optional = LazyOptional.of(() -> this.hotbars);

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
            return ARTISAN_HOTBARS.orEmpty(capability, this.optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return this.hotbars.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            this.hotbars.deserializeNBT(tag);
        }
    }
}
