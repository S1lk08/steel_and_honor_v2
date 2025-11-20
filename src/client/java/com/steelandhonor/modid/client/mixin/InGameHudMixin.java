package com.steelandhonor.modid.client.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    private static final String CLAIM_KEY = "gui.xaero_pac_title_entered_claim";
    private static final String[] CLAIM_MATCHES = new String[] {
        "'s claim",
        " claim",
        "claimed territory",
        "claim border",
        "server claim",
        "expired claim"
    };

    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
    private Text steel$swapClaimTitle(Text original) {
        Text replacement = computeReplacement(original);
        return replacement != null ? replacement : original;
    }

    @ModifyVariable(method = "setSubtitle", at = @At("HEAD"), argsOnly = true)
    private Text steel$swapClaimSubtitle(Text original) {
        Text replacement = computeReplacement(original);
        return replacement != null ? replacement : original;
    }

    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private Text steel$swapClaimOverlay(Text original) {
        Text replacement = computeReplacement(original);
        return replacement != null ? replacement : original;
    }

    private Text computeReplacement(Text original) {
        if (original == null || original.getString().isEmpty()) {
            return null;
        }
        
        if (original.getContent() instanceof TranslatableTextContent content) {
            if (CLAIM_KEY.equals(content.getKey())) {
                return Text.empty();
            }
        }
        
        // Check literal text for claim-related patterns
        if (matchesLiteralClaim(original)) {
            return Text.empty();
        }
        
        return null;
    }

    private boolean matchesLiteralClaim(Text original) {
        String plain = original.getString().toLowerCase();
        for (String match : CLAIM_MATCHES) {
            if (plain.contains(match)) {
                return true;
            }
        }
        return false;
    }
}
