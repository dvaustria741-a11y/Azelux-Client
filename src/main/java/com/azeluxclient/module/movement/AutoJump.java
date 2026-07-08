package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;

public class AutoJump extends Module {
    public AutoJump() {
        super("AutoJump", "Automatically jumps while on the ground.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (client.player.isOnGround() && !client.player.isSneaking()) {
            client.player.jump();
        }
    }
}
