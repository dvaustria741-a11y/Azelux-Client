package com.azeluxclient.mixin;

import com.azeluxclient.module.movement.Freelook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redirects mouse deltas into Freelook.lookYaw/lookPitch instead of rotating
 * the player entity when freelook is active in third-person.
 *
 * Cancelling changeLookDirection keeps the entity's facing direction frozen
 * so that movement keys and AutoPvP silent-aim work relative to the player's
 * actual combat direction — not the camera orbit angle.
 *
 * Third-person guard: in first-person, freelook is disabled, so we let
 * changeLookDirection run normally.
 */
@Mixin(Entity.class)
public class EntityFreelookMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void onChangeLookDirection(double deltaYaw, double deltaPitch, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        if ((Object) this != mc.player) return;
        if (!Freelook.isActive()) return;
        // Only intercept in third-person; first-person = freelook not active
        if (mc.options.getPerspective() == Perspective.FIRST_PERSON) return;

        // Accumulate mouse deltas into the freelook orbit angles.
        // changeLookDirection receives already-sensitivity-scaled deltas;
        // the 0.15 factor is the same one MC applies internally.
        Freelook.lookYaw  += (float) (deltaYaw   * 0.15);
        Freelook.lookPitch = MathHelper.clamp(
            Freelook.lookPitch - (float) (deltaPitch * 0.15), -90f, 90f);

        ci.cancel(); // don't let the entity actually rotate
    }
}
