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
 * In 1.21.11 the lightmap backend changed from NativeImageBackedTexture to
 * GpuTexture + MappableRingBuffer (GPU-side UBO). Direct pixel overwriting is
 * no longer possible. We force gamma = 1.0 before every lightmap recalculation
 * so vanilla outputs the brightest values it can compute. The user's original
 * gamma is saved/restored by Fullbright.onEnable() / onDisable().
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
