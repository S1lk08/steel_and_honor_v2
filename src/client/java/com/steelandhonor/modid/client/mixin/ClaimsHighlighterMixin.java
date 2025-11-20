package com.steelandhonor.modid.client.mixin;

import com.steelandhonor.modid.client.city.ClientCityData;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.minimap.highlight.ChunkHighlighter;
import xaero.common.minimap.info.render.compile.InfoDisplayCompiler;
import xaero.common.mods.pac.highlight.ClaimsHighlighter;
import xaero.pac.client.claims.api.IClientClaimsManagerAPI;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;

@Mixin(value = ClaimsHighlighter.class, remap = false)
public abstract class ClaimsHighlighterMixin extends ChunkHighlighter {
    protected ClaimsHighlighterMixin() {
        super(true);
    }

    @Shadow
    @Final
    private IClientClaimsManagerAPI claimsManager;

    @Inject(method = "chunkIsHighlit", at = @At("RETURN"), cancellable = true)
    private void steel$includeWilderness(RegistryKey<World> dimension, int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        if (ClientCityData.INSTANCE.cityAt(dimension.getValue(), chunkX, chunkZ) != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "regionHasHighlights", at = @At("RETURN"), cancellable = true)
    private void steel$regionHighlights(RegistryKey<World> dimension, int regionX, int regionZ, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        int minChunkX = regionX << 5;
        int minChunkZ = regionZ << 5;
        int maxChunkX = minChunkX + 31;
        int maxChunkZ = minChunkZ + 31;
        boolean intersects = ClientCityData.INSTANCE.citiesFor(dimension.getValue()).stream().anyMatch(city ->
            city.getMinChunkX() <= maxChunkX && city.getMaxChunkX() >= minChunkX &&
                city.getMinChunkZ() <= maxChunkZ && city.getMaxChunkZ() >= minChunkZ);
        if (intersects) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "addChunkHighlightTooltips", at = @At("HEAD"), cancellable = true)
    private void steel$kingdomTooltips(InfoDisplayCompiler compiler, RegistryKey<World> dimension, int chunkX, int chunkZ, int width, CallbackInfo ci) {
        ClientCityData.City city = ClientCityData.INSTANCE.cityAt(dimension.getValue(), chunkX, chunkZ);
        if (city == null) {
            return;
        }
        IPlayerChunkClaimAPI claim = claimsManager.get(dimension.getValue(), chunkX, chunkZ);
        MutableText header = Text.literal(city.getKingdomName()).formatted(Formatting.GOLD, Formatting.BOLD);
        compiler.addLine(header);
        if (claim != null) {
            compiler.addLine(Text.translatable("text.steel_and_honor.claim.claimed").formatted(Formatting.GREEN));
        }
        compiler.addLine(Text.translatable("text.steel_and_honor.claim.coords", chunkX, chunkZ).formatted(Formatting.DARK_GRAY));
        ci.cancel();
    }

    @Inject(method = "getColors", at = @At("RETURN"), cancellable = true)
    private void steel$tintColors(RegistryKey<World> dimension, int chunkX, int chunkZ, CallbackInfoReturnable<int[]> cir) {
        ClientCityData.City city = ClientCityData.INSTANCE.cityAt(dimension.getValue(), chunkX, chunkZ);
        if (city == null) {
            return;
        }
        int[] colors = cir.getReturnValue();
        if (colors == null || colors.length == 0) {
            return;
        }
        int tint = toColor(city.getKingdomColor());
        for (int i = 0; i < colors.length; i++) {
            int alpha = colors[i] & 0xFF000000;
            colors[i] = alpha | (tint & 0x00FFFFFF);
        }
        cir.setReturnValue(colors);
    }

    private static int toColor(float[] components) {
        int r = toChannel(components, 0);
        int g = toChannel(components, 1);
        int b = toChannel(components, 2);
        return (r << 16) | (g << 8) | b;
    }

    private static int toChannel(float[] values, int index) {
        float value = (index >= 0 && index < values.length) ? values[index] : 1F;
        value = Math.min(1F, Math.max(0F, value));
        return Math.min(255, Math.max(0, Math.round(value * 255F)));
    }
}
