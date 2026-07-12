package com.azeluxclient.mixin;

import com.azeluxclient.module.render.Fullbright;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * True fullbright for 1.21.11 — two-layered approach for Sodium compatibility.
 *
 * LAYER 1 — gamma hack (Sodium-compatible):
 *   Every tick we pin gamma to 25.0 (2500 %).  Sodium computes its own
 *   lightmap from this option, so even though our @Redirect below is a
 *   no-op when Sodium replaces LightmapTextureManager.update(), Sodium's
 *   own path will use the inflated gamma and produce a fully bright lightmap.
 *
 * LAYER 2 — getBrightness redirect (vanilla fallback):
 *   When vanilla's update() is in use, we intercept every getBrightness
 *   call and return 1.0f so every sky×block combination maps to maximum
 *   brightness.  require=0 means a missing injection point is a silent
 *   no-op, not a crash.
 *
 * The user's original gamma is saved/restored by Fullbright.onEnable/onDisable.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {

    @Shadow private boolean dirty;

    @Shadow private static float getBrightness(float ambientLight, int light) { return 0; }

    /**
     * Pin gamma to 25.0 (2500%) every tick while Fullbright is on.
     * Sodium reads mc.options.getGamma() for its own lightmap, so this
     * gives true fullbright regardless of which lightmap backend is active.
     */
    @Inject(method = "update", at = @At("HEAD"))
    private void onUpdate(float tickProgress, CallbackInfo ci) {
        Fullbright fb = Fullbright.getInstance();
        if (fb == null || !fb.isEnabled()) return;
        dirty = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) {
            // 25.0 = 2500 % — enough to saturate any lightmap calculation.
            // Sodium's GammaSliderOption clamps its slider to 100 % but the
            // underlying SimpleOption accepts arbitrary doubles when set
            // programmatically, and Sodium's shader path uses the raw value.
            mc.options.getGamma().setValue(25.0);
        }
    }

    /**
     * Vanilla fullbright: make every getBrightness() call return 1.0f.
     * No-ops silently when Sodium replaces the vanilla update() body.
     */
    @Redirect(
        method = "update",
        require = 0,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/LightmapTextureManager;getBrightness(FI)F"
        )
    )
    private float redirectGetBrightness(float ambientLight, int light) {
        Fullbright fb = Fullbright.getInstance();
        if (fb != null && fb.isEnabled()) return 1.0f;
        return getBrightness(ambientLight, light);
    }
}
