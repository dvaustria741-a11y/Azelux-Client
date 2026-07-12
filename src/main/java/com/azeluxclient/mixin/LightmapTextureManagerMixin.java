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
 * True fullbright for 1.21.11.
 *
 * In 1.21.11 the lightmap backend changed from NativeImageBackedTexture to
 * GpuTexture + MappableRingBuffer. Direct pixel overwriting is no longer
 * possible. Instead we redirect the private-static getBrightness(float, int)
 * calls that update() makes for each of the 256 lightmap entries and return
 * 1.0f for all of them when Fullbright is enabled — identical end-result to
 * the old pixel overwrite.
 *
 * require=0 on the Redirect means: if getBrightness(FI)F is not called from
 * update in this build, the redirect is silently skipped (no crash). The
 * HEAD inject (dirty=true + gamma=1.0) stays active as a near-fullbright
 * fallback so the module is never completely non-functional.
 *
 * The user's original gamma is saved/restored by Fullbright.onEnable/onDisable.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {

    @Shadow private boolean dirty;

    /** Shadows the private-static helper so we can call it in the redirect fallback. */
    @Shadow private static float getBrightness(float ambientLight, int light) { return 0; }

    /** Baseline: force lightmap recalc and pin gamma to max every tick. */
    @Inject(method = "update", at = @At("HEAD"))
    private void onUpdate(float tickProgress, CallbackInfo ci) {
        Fullbright fb = Fullbright.getInstance();
        if (fb == null || !fb.isEnabled()) return;
        dirty = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) {
            mc.options.getGamma().setValue(1.0);
        }
    }

    /**
     * True fullbright: intercept every getBrightness(float, int) call inside
     * update and return 1.0f. This covers all 256 sky×block combinations so
     * every lightmap entry maps to maximum brightness regardless of light level.
     * require=0 so a missing injection point is a silent no-op, not a crash.
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
