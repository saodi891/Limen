package com.moonlightc.limen.inventory;

import net.minecraft.core.BlockPos;

/**
 * Marks a menu that drives the campfire "peace scene": while it is the open container the overhead
 * camera arms itself and nearby vanilla hostiles are pushed away. Both the roasting station and the
 * options hub expose the campfire they are tied to through this interface so the client camera and the
 * server-side flee/close logic can react to either without caring which menu is open.
 */
public interface CampfireScene {
    BlockPos getCampfirePos();
}
