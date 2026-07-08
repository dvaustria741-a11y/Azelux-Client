package com.azeluxclient.module.player;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;

public class AutoRespawn extends Module {
    public AutoRespawn() {
        super("AutoRespawn", "Automatically respawns after death.", Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.currentScreen instanceof DeathScreen && client.player != null) {
            client.player.requestRespawn();
        }
    }
}
