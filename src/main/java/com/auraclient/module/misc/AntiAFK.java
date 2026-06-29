package com.auraclient.module.misc;

import com.auraclient.module.Module;
import net.minecraft.client.MinecraftClient;

public class AntiAFK extends Module {
    private int ticks = 0;

    public AntiAFK() {
        super("AntiAFK", "Prevents AFK kicks by jumping periodically.", Category.MISC);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (++ticks >= 200) {
            client.player.jump();
            ticks = 0;
        }
    }

    @Override
    public void onDisable() { ticks = 0; }
}
