package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;

public class NoFall extends Module {
    public NoFall() {
        super("NoFall", "Prevents fall damage by zeroing fall distance.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        client.player.fallDistance = 0.0f;
    }
}
