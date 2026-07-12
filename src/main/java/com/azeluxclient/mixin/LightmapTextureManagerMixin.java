package com.azeluxclient.mixin;

import com.azeluxclient.module.render.Fullbright;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces the lightmap to maximum brightness when Fullbright is enabled.
 * This works even with Sodium 0.8.x which clamps gamma via options.getGamma().
 * By overwriting the actual lightmap texture pixels after every update we
 * bypass the gamma pipeline entirely.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {

    @Shadow private NativeImageBackedTexture texture;

    @Inject(method = "update", at = @At("RETURN"))
    private void onUpdate(float tickDelta, CallbackInfo ci) {
        Fullbright fb = Fullbright.getInstance();
        if (fb == null || !fb.isEnabled()) return;

        NativeImage image = texture.getImage();
        if (image == null) return;

        // Overwrite every lightmap pixel with full-white (ARGB 0xFFFFFFFF).
        // The lightmap is a 16×16 texture (sky×block light).
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setColorArgb(x, y, 0xFFFFFFFF);
            }
        }
        texture.upload();
    }
}
