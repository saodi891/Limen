package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.item.ArtisanBoxItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Draws the artisan box item as its 3D Blockbench model in inventories, hands and item frames. */
public class ArtisanBoxItemRenderer extends GeoItemRenderer<ArtisanBoxItem> {
    public ArtisanBoxItemRenderer() {
        super(new ArtisanBoxItemModel());
    }
}
