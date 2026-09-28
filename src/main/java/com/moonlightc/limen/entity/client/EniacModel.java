package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.monster.EniacEntity;
import com.moonlightc.limen.registry.ModItems;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.data.EntityModelData;

public class EniacModel extends AbstractLimenGeoModel<EniacEntity> {
    public EniacModel() {
        super(
                "geo/entity/eniac.geo.json",
                "textures/entity/eniac.png",
                "animations/entity/eniac.animation.json"
        );
    }

    @Override
    public void setCustomAnimations(EniacEntity animatable, long instanceId, AnimationState<EniacEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        updateHeldWeapon(animatable);

        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (entityData == null) {
            return;
        }
        float headYaw = entityData.netHeadYaw() * Mth.DEG_TO_RAD;
        float headPitch = entityData.headPitch() * Mth.DEG_TO_RAD;
        this.getBone("Head").ifPresent(bone -> {
            bone.setRotY(bone.getInitialSnapshot().getRotY() + headYaw);
            bone.setRotX(bone.getInitialSnapshot().getRotX() + headPitch);
        });
    }

    // The weapons are modelled into the rig as bones (children of itemRight/itemLeft) rather than rendered as
    // held ItemStacks. ENIAC still equips the real items to drive melee damage; here we just show the bone
    // matching its state so two weapons are never visible at once.
    private void updateHeldWeapon(EniacEntity animatable) {
        boolean skillC = animatable.isSkillCRunning();
        Item held = animatable.getMainHandItem().getItem();
        // During skillC the main hand always shows the powered polearm, even while the real main-hand item
        // briefly swaps to an axe to drive the shield-break damage.
        setWeaponVisible("pama_sword", !skillC && held == ModItems.PAMA_SWORD.get());
        setWeaponVisible("pama_axe", !skillC && held == ModItems.PAMA_AXE.get());
        setWeaponVisible("power_polearm_3d", skillC || held == ModItems.POWERED_POLEARM.get());
        // Off-hand break axe: only flashes during the skillC shield-break beat, which is the one moment the
        // main-hand item is swapped to the axe while skillC is running.
        setWeaponVisible("pama_axe2", skillC && held == ModItems.PAMA_AXE.get());
    }

    private void setWeaponVisible(String boneName, boolean visible) {
        this.getBone(boneName).ifPresent(bone -> {
            bone.setHidden(!visible);
            bone.setChildrenHidden(!visible);
        });
    }
}
