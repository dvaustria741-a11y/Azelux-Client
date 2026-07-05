package com.azeluxclient.module.hud;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;

public class MovingStatus extends Module {
    public MovingStatus() {
        super("MovingStatus", "Shows your movement state: walking, sprinting, sneaking, jumping", Category.HUD);
    }

    public String getStatus(MinecraftClient client) {
        if (client.player == null) return "Idle";
        var p = client.player;
        if (!p.isOnGround()) return "Jumping";
        if (p.isSneaking()) return "Sneaking";
        if (p.isSprinting()) return "Sprinting";
        var vel = p.getVelocity();
        if (Math.abs(vel.x) > 0.01 || Math.abs(vel.z) > 0.01) return "Walking";
        return "Standing";
    }
}
