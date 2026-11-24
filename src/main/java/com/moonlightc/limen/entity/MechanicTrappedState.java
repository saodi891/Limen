package com.moonlightc.limen.entity;

import com.moonlightc.limen.network.MechanicTrappedPacket;
import com.moonlightc.limen.network.ModNetworking;
import com.moonlightc.limen.registry.ModVillagers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.network.PacketDistributor;

/** Persistent, server-authoritative state; it never replaces velocity or disables gravity. */
public final class MechanicTrappedState {
    public static final String POSE_ID = "animation.mechanic.trapped";
    private static final String INITIALIZED = "LimenMechanicInitialized";
    private static final String TRAPPED = "LimenMechanicTrapped";
    private static final String FACING = "LimenMechanicTrappedYaw";

    private MechanicTrappedState() {
    }

    public static boolean isMechanic(Villager villager) {
        return villager.getVillagerData().getProfession() == ModVillagers.MECHANIC.get();
    }

    public static boolean isInitialized(Villager villager) {
        return villager.getPersistentData().getBoolean(INITIALIZED);
    }

    public static boolean isTrapped(Mob mob) {
        return mob instanceof Villager villager && isMechanic(villager)
                && villager.getPersistentData().getBoolean(TRAPPED);
    }

    /** Existing saved villagers are not retroactively trapped; new structure/egg villagers are. */
    public static void initialize(Villager villager, boolean trapped) {
        if (isInitialized(villager)) {
            return;
        }
        applySyncedState(villager, trapped, villager.getYRot());
    }

    public static void applySyncedState(Villager villager, boolean trapped, float yaw) {
        CompoundTag data = villager.getPersistentData();
        data.putBoolean(INITIALIZED, true);
        data.putBoolean(TRAPPED, trapped);
        data.putFloat(FACING, yaw);
        if (trapped) {
            villager.setTradingPlayer(null);
            holdFacing(villager);
        }
    }

    /** Cancels only autonomous inputs. Pushes, knockback, falling and fluid movement stay vanilla. */
    public static void stopAutonomousMovement(Mob mob) {
        mob.getNavigation().stop();
        mob.setXxa(0.0F);
        mob.setYya(0.0F);
        mob.setZza(0.0F);
        mob.setJumping(false);
        mob.setSpeed(0.0F);
    }

    public static void holdFacing(Villager villager) {
        float yaw = villager.getPersistentData().getFloat(FACING);
        villager.setYRot(yaw);
        villager.yRotO = yaw;
        villager.setYHeadRot(yaw);
        villager.yHeadRotO = yaw;
        villager.setYBodyRot(yaw);
        villager.yBodyRotO = yaw;
        villager.setXRot(0.0F);
        villager.xRotO = 0.0F;
    }

    public static void rescue(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || !isTrapped(villager)) {
            return;
        }
        applySyncedState(villager, false, villager.getYRot());
        // Vanilla ResetProfession only resets a novice with zero trading XP.
        villager.setVillagerXp(Math.max(1, villager.getVillagerXp()));
        villager.refreshBrain(level);
        sync(villager);
        level.playSound(null, villager.blockPosition(), SoundEvents.CHAIN_BREAK,
                SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    public static MechanicTrappedPacket packet(Villager villager) {
        return new MechanicTrappedPacket(villager.getId(), villager.getUUID(), isTrapped(villager),
                villager.getPersistentData().getFloat(FACING));
    }

    public static void sync(Villager villager) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> villager), packet(villager));
    }
}
