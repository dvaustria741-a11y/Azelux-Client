package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;

public class AirJump extends Module {
    private boolean prevJump = false;

    public AirJump() {
        super("AirJump", "Allows you to jump while in mid-air.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        boolean jumping = client.options.jumpKey.isPressed();

        // Jump on key press edge (not held) while airborne
        if (jumping && !prevJump && !client.player.isOnGround()) {
            client.player.jump();
        }
        prevJump = jumping;
    }

    @Override public void onDisable() { prevJump = false; }
}
