package com.azeluxclient.mixin;

import com.azeluxclient.module.movement.Freelook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts Entity.changeLookDirection(deltaYaw, deltaPitch) for the local player.
 * (Was Entity.turn() in < 1.21.x — renamed in 1.21.x Yarn mappings.)
 *
 * When Freelook is active:
 *  - The raw mouse deltas are applied to Freelook.lookYaw / lookPitch.
 *  - The actual call is cancelled so the entity yaw/pitch stay unchanged,
 *    keeping movement direction locked while the camera rotates freely.
 */
@Mixin(Entity.class)
public class EntityFreelookMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void onChangeLookDirection(double deltaYaw, double deltaPitch, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || (Object) this != mc.player) return;
        if (!Freelook.isActive()) return;

        Freelook.lookYaw   += (float) (deltaYaw   * 0.15);
        Freelook.lookPitch  = MathHelper.clamp(
            Freelook.lookPitch - (float) (deltaPitch * 0.15), -90f, 90f);

        ci.cancel();
    }
}
