package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class NoSlow extends Module {

    public NoSlow() {
        super("NoSlow", "Prevents slowdown while using items like bows or food.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || !client.player.isUsingItem()) return;
        // Counteract the 15% movement penalty from item use
        Vec3d vel = client.player.getVelocity();
        double hSpd = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (hSpd > 0.005 && hSpd < 0.15) {
            double factor = 0.22 / hSpd; // target ~walk speed
            client.player.setVelocity(vel.x * factor, vel.y, vel.z * factor);
        }
        // Allow sprinting while using item
        if (client.player.input != null && client.player.input.getMovementInput().y > 0) {
            client.player.setSprinting(true);
        }
    }
}
