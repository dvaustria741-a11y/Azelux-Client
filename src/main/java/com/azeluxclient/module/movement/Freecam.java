package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Freecam / Spectate — detaches you from physics and lets you fly freely.
 * Your real position is saved when enabled and restored when disabled,
 * so the server sees you return to where you started.
 *
 * Controls:
 *   WASD  — move (pitch-aware, looks up/down while moving forward)
 *   Space — move up
 *   Shift — move down
 */
public class Freecam extends Module {
    private double savedX, savedY, savedZ;
    private float  savedYaw, savedPitch;
    private boolean hasSaved = false;

    public Freecam() {
        super("Freecam", "Fly around freely without moving your real position.", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = mc();
        if (mc.player == null) { toggle(); return; }
        savedX     = mc.player.getX();
        savedY     = mc.player.getY();
        savedZ     = mc.player.getZ();
        savedYaw   = mc.player.getYaw();
        savedPitch = mc.player.getPitch();
        hasSaved   = true;
        mc.player.noClip    = true;
        mc.player.fallDistance = 0f;
        mc.player.setVelocity(Vec3d.ZERO);
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = mc();
        if (mc.player == null || !hasSaved) return;
        mc.player.noClip = false;
        mc.player.setPosition(savedX, savedY, savedZ);
        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.fallDistance = 0f;
        hasSaved = false;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;

        // Keep noClip active every tick in case something resets it
        mc.player.noClip    = true;
        mc.player.fallDistance = 0f;

        double spd   = 0.3;
        double yaw   = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());

        double vx = 0, vy = 0, vz = 0;

        var input = mc.player.input;
        if (input != null) {
            var move  = input.getMovementInput();
            double fwd    = move.y;
            double strafe = move.x;

            // Pitch-aware: looking up while pressing W moves you upward
            vx += (-Math.sin(yaw) * Math.cos(pitch) * fwd - Math.cos(yaw) * strafe) * spd;
            vy += (-Math.sin(pitch) * fwd) * spd;
            vz += ( Math.cos(yaw) * Math.cos(pitch) * fwd - Math.sin(yaw) * strafe) * spd;
        }

        if (mc.options.jumpKey.isPressed())  vy += spd;
        if (mc.options.sneakKey.isPressed()) vy -= spd;

        mc.player.setVelocity(vx, vy, vz);
    }
}
