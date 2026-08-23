package com.moonlightc.limen.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public final class LimenPlayerSkills {
    private static final String LIMEN_SKILLS_TAG = "LimenSkills";
    private static final String POLEARM_COMBAT_TECHNIQUES = "PolearmCombatTechniques";

    private LimenPlayerSkills() {
    }

    public static boolean hasPolearmCombatTechniques(Player player) {
        return getLimenSkillsTag(player).getBoolean(POLEARM_COMBAT_TECHNIQUES);
    }

    public static void unlockPolearmCombatTechniques(Player player) {
        getLimenSkillsTag(player).putBoolean(POLEARM_COMBAT_TECHNIQUES, true);
    }

    private static CompoundTag getLimenSkillsTag(Player player) {
        CompoundTag persistedData = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!player.getPersistentData().contains(Player.PERSISTED_NBT_TAG)) {
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistedData);
        }

        CompoundTag limenSkills = persistedData.getCompound(LIMEN_SKILLS_TAG);
        if (!persistedData.contains(LIMEN_SKILLS_TAG)) {
            persistedData.put(LIMEN_SKILLS_TAG, limenSkills);
        }

        return limenSkills;
    }
}
