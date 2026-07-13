package com.azeluxclient.mixin;

import com.azeluxclient.module.movement.Freelook;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides the camera's yaw/pitch after Camera.update() computes them from
 * the entity, replacing them with the Freelook look angles when active.
 *
 * This is the only change needed to redirect the rendered view — the game
 * reads camera.getYaw() / camera.getPitch() for both first-person and
 * third-person rendering after update() returns.
 */
@Mixin(Camera.class)
public class CameraFreelookMixin {

    @Shadow private float yaw;
    @Shadow private float pitch;

    @Inject(method = "update", at = @At("RETURN"))
    private void onUpdate(CallbackInfo ci) {
        if (!Freelook.isActive()) return;
        this.yaw   = Freelook.lookYaw;
        this.pitch = Freelook.lookPitch;
    }
}
