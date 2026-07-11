package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Freecam / Spectate — detaches camera from physics, lets you fly freely
 * and clip through walls. Your real position is saved on enable and
 * restored on disable so the server sees you return to your start point.
 *
 * Controls:
 *   WASD  — move (pitch-aware)
 *   Space — move up
 *   Shift — move down
 */
public class Freecam extends Module {
    private double savedX, savedY, savedZ;
    private float  savedYaw, savedPitch;
    private boolean hasSaved = false;

    public Freecam() {
        super("Freecam", "Fly around freely and clip through walls without moving your real position.", Category.MOVEMENT);
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
        mc.player.noClip = true;
        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.fallDistance = 0f;
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = mc();
        if (mc.player == null || !hasSaved) return;
        mc.player.noClip = false;
        // Teleport back to real position
        mc.player.setPosition(savedX, savedY, savedZ);
        mc.player.setYaw(savedYaw);
        mc.player.setPitch(savedPitch);
        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.fallDistance = 0f;
        hasSaved = false;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;

        // Keep noClip alive every tick — Lithium and other mods may reset it
        mc.player.noClip = true;
        mc.player.fallDistance = 0f;
        // Kill velocity so the vanilla move() call does nothing
        mc.player.setVelocity(Vec3d.ZERO);

        double spd   = 0.3;
        double yaw   = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());

        double vx = 0, vy = 0, vz = 0;

        var input = mc.player.input;
        if (input != null) {
            var move  = input.getMovementInput();
            double fwd    = move.y;
            double strafe = move.x;

            vx += (-Math.sin(yaw) * Math.cos(pitch) * fwd
                  - Math.cos(yaw) * strafe) * spd;
            vy += (-Math.sin(pitch) * fwd) * spd;
            vz += ( Math.cos(yaw) * Math.cos(pitch) * fwd
                  - Math.sin(yaw) * strafe) * spd;
        }

        if (mc.options.jumpKey.isPressed())  vy += spd;
        if (mc.options.sneakKey.isPressed()) vy -= spd;

        // Use setPosition() directly — bypasses ALL collision detection,
        // including Lithium-patched move(). This is what makes wall-clipping work.
        if (vx != 0 || vy != 0 || vz != 0) {
            mc.player.setPosition(
                mc.player.getX() + vx,
                mc.player.getY() + vy,
                mc.player.getZ() + vz
            );
        }
    }
}
