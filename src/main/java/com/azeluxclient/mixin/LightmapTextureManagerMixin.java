package com.azeluxclient.mixin;

import com.azeluxclient.module.render.Fullbright;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright for 1.21.11.
 *
 * The old NativeImageBackedTexture pixel-overwrite and the getBrightness
 * redirect both no longer work in 1.21.11 (GPU lightmap pipeline via
 * GpuTexture + MappableRingBuffer). We pin gamma to 1.0 and force a
 * lightmap recalc every tick while Fullbright is on. Gamma save/restore
 * is handled by Fullbright.onEnable() / onDisable().
 */
@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {

    @Shadow private boolean dirty;

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
}
