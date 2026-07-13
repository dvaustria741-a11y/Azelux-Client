package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

public class Freelook extends Module {

    public static float   lookYaw   = 0f;
    public static float   lookPitch = 0f;
    private static boolean active   = false;

    // Used by CameraFreelookMixin HEAD/RETURN to temporarily override entity yaw
    public static float   savedEntityYaw   = 0f;
    public static float   savedEntityPitch = 0f;
    public static boolean entityOverrideActive = false;

    public Freelook() {
        super("Freelook", "Look around freely in third-person without rotating your body.", Category.MOVEMENT);
    }

    public static boolean isActive() { return active; }

    public static void setActive(boolean state, MinecraftClient mc) {
        if (state == active) return;
        active = state;
        if (state && mc != null && mc.player != null) {
            lookYaw   = mc.player.getYaw();
            lookPitch = mc.player.getPitch();
        }
    }

    @Override public void onEnable() {
        MinecraftClient mc = mc();
        if (mc != null && mc.player != null) {
            lookYaw   = mc.player.getYaw();
            lookPitch = mc.player.getPitch();
        }
        active = true;
    }

    @Override public void onDisable() {
        active = false;
        entityOverrideActive = false;
    }

    @Override public void onTick(MinecraftClient mc) { /* handled by mixins */ }
}
