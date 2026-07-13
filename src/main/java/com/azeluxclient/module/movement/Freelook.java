package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Freelook — look around freely without rotating your player body.
 *
 * Design (two-mixin):
 *   EntityFreelookMixin : cancels Entity.turn() for the local player when
 *                         active, redirecting mouse deltas into lookYaw/lookPitch.
 *   CameraFreelookMixin : after Camera.update() computes entity-based angles,
 *                         overrides yaw/pitch with our freelook angles.
 *
 * Can also be activated programmatically by other modules (e.g. AutoPvP)
 * via Freelook.setActive(true/false).
 */
public class Freelook extends Module {

    public static float   lookYaw    = 0f;
    public static float   lookPitch  = 0f;
    private static boolean active    = false;

    public Freelook() {
        super("Freelook", "Look around freely without rotating your body.", Category.MOVEMENT);
    }

    public static boolean isActive() { return active; }

    /** Called by AutoPvP (and other modules) to enable freelook programmatically. */
    public static void setActive(boolean state, MinecraftClient mc) {
        if (state == active) return;
        active = state;
        if (state && mc.player != null) {
            lookYaw   = mc.player.getYaw();
            lookPitch = mc.player.getPitch();
        }
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = mc();
        if (mc != null && mc.player != null) {
            lookYaw   = mc.player.getYaw();
            lookPitch = mc.player.getPitch();
        }
        active = true;
    }

    @Override
    public void onDisable() {
        active = false;
        MinecraftClient mc = mc();
        if (mc != null && mc.player != null) {
            lookYaw   = mc.player.getYaw();
            lookPitch = mc.player.getPitch();
        }
    }

    @Override
    public void onTick(MinecraftClient mc) {
        // All work is done in the mixins
    }
}
