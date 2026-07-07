package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class HighJump extends Module {
    private final SliderSetting multiplier = register(new SliderSetting("Multiplier", 2.5, 1.5, 10.0));
    private boolean wasOnGround = true;

    public HighJump() {
        super("HighJump", "Multiply your jump height.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        boolean onGround = client.player.isOnGround();
        // Detect the exact tick the player leaves the ground (jump moment)
        if (wasOnGround && !onGround) {
            Vec3d vel = client.player.getVelocity();
            if (vel.y > 0.1) {
                client.player.setVelocity(vel.x, vel.y * multiplier.getValue(), vel.z);
            }
        }
        wasOnGround = onGround;
    }

    @Override
    public void onDisable() { wasOnGround = true; }
}
