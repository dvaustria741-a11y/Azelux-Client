package com.azeluxclient.mixin;

import com.azeluxclient.module.movement.Freelook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the camera orbit around the player using freelook angles.
 *
 * ── Why HEAD + RETURN, not RETURN only ──────────────────────────────────────
 * Camera.update() does two things:
 *   1. Computes the CAMERA POSITION (third-person offset behind the player).
 *   2. Computes the CAMERA LOOK DIRECTION (rotation quaternion).
 * Both steps read `entity.getYaw()` / `entity.getPitch()`.
 *
 * The old approach (RETURN inject, overriding `this.yaw/pitch` and manually
 * recomputing the quaternion) only fixed the LOOK DIRECTION. The camera
 * position was already baked from the entity's real yaw — so the camera
 * stayed behind the entity facing but just rotated the view, not actually
 * orbiting.
 *
 * Fix:
 *   HEAD  → override entity yaw/pitch with freelook values BEFORE update().
 *           Camera.update() reads these and computes BOTH position and
 *           rotation correctly for the freelook angles.
 *   RETURN → restore entity yaw/pitch so the player's actual facing is
 *            unaffected (important for AutoPvP's silent-aim).
 *
 * Only active in third-person views — freelook in first-person would just
 * be a broken camera that doesn't orbit.
 */
@Mixin(Camera.class)
public class CameraFreelookMixin {

    @Shadow private Entity focusedEntity;

    @Inject(method = "update", at = @At("HEAD"))
    private void onUpdateHead(CallbackInfo ci) {
        if (!Freelook.isActive()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        // Third-person only — freelook doesn't make sense in first-person
        if (mc.options.getPerspective() == Perspective.FIRST_PERSON) return;
        if (focusedEntity == null) return;

        // Save real entity angles and replace with freelook angles.
        // Camera.update() will now position and orient the camera using
        // our freelook yaw/pitch, giving a proper orbital third-person view.
        Freelook.savedEntityYaw   = focusedEntity.getYaw();
        Freelook.savedEntityPitch = focusedEntity.getPitch();
        focusedEntity.setYaw(Freelook.lookYaw);
        focusedEntity.setPitch(Freelook.lookPitch);
        Freelook.entityOverrideActive = true;
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void onUpdateReturn(CallbackInfo ci) {
        if (!Freelook.entityOverrideActive) return;
        // Restore so the player entity keeps its real yaw/pitch for movement,
        // silent-aim packet routing, and all other game logic.
        if (focusedEntity != null) {
            focusedEntity.setYaw(Freelook.savedEntityYaw);
            focusedEntity.setPitch(Freelook.savedEntityPitch);
        }
        Freelook.entityOverrideActive = false;
    }
}
