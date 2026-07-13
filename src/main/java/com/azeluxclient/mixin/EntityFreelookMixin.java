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
 * Intercepts Entity.turn(deltaYaw, deltaPitch) for the local player.
 *
 * When Freelook is active:
 *  - The raw mouse deltas (already sensitivity-adjusted by Mouse.updateMouse)
 *    are applied to Freelook.lookYaw / lookPitch with the same 0.15 factor
 *    that vanilla Entity.turn() uses.
 *  - The actual call is cancelled so the entity's yaw/pitch stay unchanged,
 *    keeping movement direction locked.
 */
@Mixin(Entity.class)
public class EntityFreelookMixin {

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void onTurn(double deltaYaw, double deltaPitch, CallbackInfo ci) {
        // Only intercept the local player, not every entity in the world
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || (Object) this != mc.player) return;
        if (!Freelook.isActive()) return;

        // Mirror the scaling vanilla Entity.turn() applies
        Freelook.lookYaw   += (float) (deltaYaw   * 0.15);
        Freelook.lookPitch  = MathHelper.clamp(
            Freelook.lookPitch - (float) (deltaPitch * 0.15), -90f, 90f);

        // Cancel so the entity yaw/pitch — and therefore movement direction — are unchanged
        ci.cancel();
    }
}
