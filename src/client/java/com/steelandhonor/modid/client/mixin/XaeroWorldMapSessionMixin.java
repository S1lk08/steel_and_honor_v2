package com.steelandhonor.modid.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xaero.map.WorldMapSession;
import xaero.map.highlight.HighlighterRegistry;

@Mixin(value = WorldMapSession.class, remap = false)
public abstract class XaeroWorldMapSessionMixin {

    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lxaero/map/highlight/HighlighterRegistry;end()V"))
    private void steel_and_honor$registerCityOverlays(HighlighterRegistry registry) {
        registry.end();
    }
}
