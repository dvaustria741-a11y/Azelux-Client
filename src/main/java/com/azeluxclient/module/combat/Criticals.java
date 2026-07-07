package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class Criticals extends Module {

    public Criticals() {
        super("Criticals", "Makes every melee attack a critical hit.", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || !client.player.isOnGround()) return;
        Vec3d vel = client.player.getVelocity();
        // Apply a micro-hop so the player is always in a falling state for crits
        if (Math.abs(vel.x) > 0.01 || Math.abs(vel.z) > 0.01) {
            client.player.setVelocity(vel.x, 0.11, vel.z);
        }
    }
}
