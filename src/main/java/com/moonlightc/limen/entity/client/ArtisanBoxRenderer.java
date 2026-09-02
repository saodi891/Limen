package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.block.entity.ArtisanBoxBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Renders the artisan box from its Blockbench model, with the lid animation played by GeckoLib.
 * The box has no facing state — the lid always opens upward — so no rotation is applied here and
 * the model is drawn exactly as authored.
 */
public class ArtisanBoxRenderer extends GeoBlockRenderer<ArtisanBoxBlockEntity> {
    public ArtisanBoxRenderer() {
        super(new ArtisanBoxModel());
    }
}
