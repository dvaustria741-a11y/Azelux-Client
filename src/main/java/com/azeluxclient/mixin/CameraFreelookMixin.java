package com.azeluxclient.mixin;

import com.azeluxclient.module.movement.Freelook;
import net.minecraft.client.render.Camera;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides the camera direction when Freelook is active.
 *
 * Root cause of the "camera stuck" bug:
 *   Camera.update() computes `this.rotation` (the quaternion that actually
 *   steers the rendered view) from entity yaw/pitch DURING execution.
 *   The old mixin injected at RETURN and wrote `this.yaw` and `this.pitch`,
 *   but by that point the rotation quaternion was already computed from the
 *   entity's values — so the visual never changed.
 *
 * Fix:
 *   After overriding yaw/pitch at RETURN we ALSO recompute `this.rotation`
 *   using the same formula Camera uses internally:
 *     rotation = identity · rotateY(-yaw_rad) · rotateX(pitch_rad)
 *   and refresh the three camera plane vectors that depend on it.
 */
@Mixin(Camera.class)
public class CameraFreelookMixin {

    @Shadow private float yaw;
    @Shadow private float pitch;
    // The rotation quaternion is what the renderer actually reads for the view matrix.
    @Shadow private Quaternionf rotation;            // private final — @Shadow gives access
    @Shadow private Vector3f horizontalPlane;        // forward direction
    @Shadow private Vector3f verticalPlane;          // up direction
    @Shadow private Vector3f diagonalPlane;          // normalized(forward + up)

    @Inject(method = "update", at = @At("RETURN"))
    private void onUpdate(CallbackInfo ci) {
        if (!Freelook.isActive()) return;

        // 1. Override the stored yaw/pitch values
        this.yaw   = Freelook.lookYaw;
        this.pitch = Freelook.lookPitch;

        // 2. Recompute the rotation quaternion from the new yaw/pitch.
        //    MC convention: yaw=0 → south (+Z), positive pitch → looking down.
        //    rotateY(-yaw_rad) sweeps the forward vector around the Y axis.
        //    rotateX( pitch_rad) tilts it up/down.
        this.rotation.identity()
            .rotateY((float) Math.toRadians(-this.yaw))
            .rotateX((float) Math.toRadians(this.pitch));

        // 3. Update the three camera plane vectors that other systems read.
        this.horizontalPlane.set(0f, 0f, 1f).rotate(this.rotation);
        this.verticalPlane.set(0f, 1f, 0f).rotate(this.rotation);
        this.diagonalPlane.set(this.horizontalPlane).add(this.verticalPlane).normalize();
    }
}
